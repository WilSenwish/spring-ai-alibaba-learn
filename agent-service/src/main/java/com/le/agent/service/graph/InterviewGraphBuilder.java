package com.le.agent.service.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.le.agent.common.constants.BizConstants;
import com.le.agent.common.dto.AnswerItem;
import com.le.agent.common.dto.QaContent;
import com.le.agent.common.dto.QuestionItem;
import com.le.agent.common.dto.ReviewItem;
import com.le.agent.common.enums.LevelEnum;
import com.le.agent.common.exception.BizException;
import com.le.agent.common.api.ResultCode;
import com.le.agent.service.agent.AgentNodes;
import com.le.agent.service.agent.LlmInvoker;
import com.le.agent.service.session.SessionHistoryService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Interview StateGraph 编排构建器.
 *
 * <p>构建包含 INTAKE -> QUESTION_AGENT -> ANSWER_AGENT -> REVIEW_AGENT -> HUMAN_CHECKPOINT -> FINALIZE
 * 的完整 Graph，支持按 LevelEnum 分级的 Agent 路由与人工复核中断.</p>
 */
@Component
@RequiredArgsConstructor
public class InterviewGraphBuilder {

	private final AgentNodes agentNodes;
	private final ObjectMapper objectMapper;
	private final LlmInvoker llmInvoker;
	private final SessionHistoryService sessionHistoryService;
	private final RedissonClient redissonClient;

	@Bean
	public CompiledGraph interviewGraph() throws GraphStateException {
		StateGraph graph = new StateGraph();

		// ==================== 节点定义 ====================

		// INTAKE：校验 topic，初始化 sessionId / reviseCount / flowStatus
		graph.addNode("INTAKE", AsyncNodeAction.node_async(state -> {
			String topic = state.value("topic", String.class).orElse(null);
			if (topic == null || topic.isBlank()) {
				throw new BizException(ResultCode.EMPTY_INPUT);
			}
			String sessionId = state.value("sessionId", String.class).orElse(null);
			if (sessionId == null || sessionId.isBlank()) {
				sessionId = UUID.randomUUID().toString();
			}
			Map<String, Object> updates = new HashMap<>();
			updates.put("sessionId", sessionId);
			updates.put("reviseCount", 0);
			updates.put("flowStatus", BizConstants.FlowStatus.RUNNING);
			return updates;
		}));

		// QUESTION_AGENT_*：按 level 生成面试题
		for (LevelEnum level : LevelEnum.values()) {
			String nodeId = "QUESTION_AGENT_" + level.name();
			graph.addNode(nodeId, AsyncNodeAction.node_async(state -> {
				String topic = state.value("topic", String.class).orElse("");
				LevelEnum lvl = LevelEnum.of(state.value("level", String.class).orElse("JUNIOR"));
				if (lvl == null) {
					lvl = LevelEnum.JUNIOR;
				}
				var agent = agentNodes.buildQuestionAgent(lvl, topic);
				AssistantMessage msg = agent.call("主题：" + topic);
				Map<String, Object> map = llmInvoker.parseJsonMap(msg.getText());
				QuestionItem item = objectMapper.convertValue(map, QuestionItem.class);
				Map<String, Object> updates = new HashMap<>();
				updates.put("question", objectMapper.convertValue(item, Map.class));
				return updates;
			}));
		}

		// ANSWER_AGENT_*：按 level 生成答案
		for (LevelEnum level : LevelEnum.values()) {
			String nodeId = "ANSWER_AGENT_" + level.name();
			graph.addNode(nodeId, AsyncNodeAction.node_async(state -> {
				QuestionItem question = state.value("question", Map.class)
						.map(m -> objectMapper.convertValue(m, QuestionItem.class))
						.orElse(null);
				String feedback = state.value("feedback", String.class).orElse(null);
				Number revNum = state.value("reviseCount", Number.class).orElse(0);
				int reviseCount = revNum.intValue();
				LevelEnum lvl = LevelEnum.of(state.value("level", String.class).orElse("JUNIOR"));
				if (lvl == null) {
					lvl = LevelEnum.JUNIOR;
				}
				var agent = agentNodes.buildAnswerAgent(lvl,
						question != null ? question.getContent() : "", feedback);
				AssistantMessage msg = agent.call("问题：" + (question != null ? question.getContent() : ""));
				Map<String, Object> map = llmInvoker.parseJsonMap(msg.getText());
				AnswerItem item = objectMapper.convertValue(map, AnswerItem.class);
				item.setRevision(reviseCount);
				Map<String, Object> updates = new HashMap<>();
				updates.put("answer", objectMapper.convertValue(item, Map.class));
				return updates;
			}));
		}

		// REVIEW_AGENT_*：按 level 复核答案
		for (LevelEnum level : LevelEnum.values()) {
			String nodeId = "REVIEW_AGENT_" + level.name();
			graph.addNode(nodeId, AsyncNodeAction.node_async(state -> {
				QuestionItem question = state.value("question", Map.class)
						.map(m -> objectMapper.convertValue(m, QuestionItem.class))
						.orElse(null);
				AnswerItem answer = state.value("answer", Map.class)
						.map(m -> objectMapper.convertValue(m, AnswerItem.class))
						.orElse(null);
				LevelEnum lvl = LevelEnum.of(state.value("level", String.class).orElse("JUNIOR"));
				if (lvl == null) {
					lvl = LevelEnum.JUNIOR;
				}
				var agent = agentNodes.buildReviewAgent(lvl,
						question != null ? question.getContent() : "",
						answer != null ? answer.getContent() : "");
				AssistantMessage msg = agent.call("请复核。");
				Map<String, Object> map = llmInvoker.parseJsonMap(msg.getText());
				ReviewItem item = objectMapper.convertValue(map, ReviewItem.class);
				Map<String, Object> updates = new HashMap<>();
				updates.put("review", objectMapper.convertValue(item, Map.class));
				return updates;
			}));
		}

		// HUMAN_CHECKPOINT：设置等待人工确认状态
		graph.addNode("HUMAN_CHECKPOINT", AsyncNodeAction.node_async(state -> {
			Map<String, Object> updates = new HashMap<>();
			updates.put("flowStatus", BizConstants.FlowStatus.AWAITING_HUMAN);
			return updates;
		}));

		// FINALIZE：构造 QaContent 并持久化历史
		graph.addNode("FINALIZE", AsyncNodeAction.node_async(state -> {
			String sessionId = state.value("sessionId", String.class).orElse("");
			String topic = state.value("topic", String.class).orElse("");
			LevelEnum level = LevelEnum.of(state.value("level", String.class).orElse(null));
			QuestionItem question = state.value("question", Map.class)
					.map(m -> objectMapper.convertValue(m, QuestionItem.class))
					.orElse(null);
			AnswerItem answer = state.value("answer", Map.class)
					.map(m -> objectMapper.convertValue(m, AnswerItem.class))
					.orElse(null);
			QaContent qaContent = QaContent.builder()
					.id(UUID.randomUUID().toString())
					.sessionId(sessionId)
					.topic(topic)
					.level(level)
					.question(question != null ? question.getContent() : null)
					.answer(answer != null ? answer.getContent() : null)
					.keyPoints(answer != null ? answer.getKeyPoints() : null)
					.sampleCode(answer != null ? answer.getSampleCode() : null)
					.revision(answer != null ? answer.getRevision() : null)
					.createdAt(LocalDateTime.now())
					.build();
			sessionHistoryService.append(sessionId, qaContent);
			Map<String, Object> updates = new HashMap<>();
			updates.put("flowStatus", BizConstants.FlowStatus.COMPLETED);
			updates.put("finalQa", objectMapper.convertValue(qaContent, Map.class));
			return updates;
		}));

		// ==================== 边定义 ====================

		graph.addEdge(StateGraph.START, "INTAKE");

		// INTAKE -> 按 level 路由到对应 QUESTION_AGENT
		graph.addConditionalEdges("INTAKE", AsyncEdgeAction.edge_async(state -> {
			LevelEnum level = LevelEnum.of(state.value("level", String.class).orElse("JUNIOR"));
			if (level == null) {
				level = LevelEnum.JUNIOR;
			}
			return "QUESTION_AGENT_" + level.name();
		}), Map.of(
				"QUESTION_AGENT_JUNIOR", "QUESTION_AGENT_JUNIOR",
				"QUESTION_AGENT_MID", "QUESTION_AGENT_MID",
				"QUESTION_AGENT_SENIOR", "QUESTION_AGENT_SENIOR"
		));

		// 各级别内部链路
		for (LevelEnum level : LevelEnum.values()) {
			String qNode = "QUESTION_AGENT_" + level.name();
			String aNode = "ANSWER_AGENT_" + level.name();
			String rNode = "REVIEW_AGENT_" + level.name();
			graph.addEdge(qNode, aNode);
			graph.addEdge(aNode, rNode);
			graph.addEdge(rNode, "HUMAN_CHECKPOINT");
		}

		// HUMAN_CHECKPOINT -> 根据 decision 路由
		graph.addConditionalEdges("HUMAN_CHECKPOINT", AsyncEdgeAction.edge_async(state -> {
			String decision = state.value("decision", String.class).orElse(null);
			if ("APPROVE".equals(decision)) {
				return "FINALIZE";
			} else if ("REVISE".equals(decision)) {
				LevelEnum level = LevelEnum.of(state.value("level", String.class).orElse("JUNIOR"));
				if (level == null) {
					level = LevelEnum.JUNIOR;
				}
				return "ANSWER_AGENT_" + level.name();
			} else {
				return StateGraph.END;
			}
		}), Map.of(
				"FINALIZE", "FINALIZE",
				"ANSWER_AGENT_JUNIOR", "ANSWER_AGENT_JUNIOR",
				"ANSWER_AGENT_MID", "ANSWER_AGENT_MID",
				"ANSWER_AGENT_SENIOR", "ANSWER_AGENT_SENIOR",
				StateGraph.END, StateGraph.END
		));

		RedisSaver redisSaver = RedisSaver.builder()
				.redisson(redissonClient)
				.build();
		CompileConfig compileConfig = CompileConfig.builder()
				.interruptAfter("REVIEW_AGENT_JUNIOR", "REVIEW_AGENT_MID", "REVIEW_AGENT_SENIOR")
				.saverConfig(SaverConfig.builder().register(redisSaver).build())
				.build();

		return graph.compile(compileConfig);
	}
}
