package com.le.agent.service.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.RedisStore;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.le.agent.common.api.TraceContext;
import com.le.agent.common.dto.AnswerItem;
import com.le.agent.common.dto.QaContent;
import com.le.agent.common.dto.QuestionItem;
import com.le.agent.common.dto.ReviewItem;
import com.le.agent.common.dto.StartSessionResponse;
import com.le.agent.common.enums.DecisionEnum;
import com.le.agent.common.enums.LevelEnum;
import com.le.agent.common.exception.BizException;
import com.le.agent.common.api.ResultCode;
import com.le.agent.service.session.SessionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Graph 执行服务.
 *
 * <p>负责驱动 InterviewGraph 的生命周期：启动(start)、人工决策(human)、继续会话(continueSession)、查询历史(history)。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GraphExecutorService {

	private final CompiledGraph compiledGraph;
	private final ObjectMapper objectMapper;
	private final RedisStore redisStore;
	private final SessionHistoryService sessionHistoryService;

	@Value("${agent.orchestrator.max-revise-count:5}")
	private int maxReviseCount;

	@Value("${agent.orchestrator.run-prefix:agent:run:}")
	private String runPrefix;

	/**
	 * 启动新会话并驱动 Graph 执行到第一个中断点或结束.
	 */
	public StartSessionResponse start(String sessionId, String topic, LevelEnum level,
									  String emailTo, Boolean emailRequired) {
		String runId = UUID.randomUUID().toString();
		Map<String, Object> initialData = new HashMap<>();
		initialData.put("sessionId", sessionId);
		initialData.put("topic", topic);
		initialData.put("level", level);
		initialData.put("emailTo", emailTo);
		initialData.put("emailRequired", emailRequired);
		initialData.put("reviseCount", 0);
		initialData.put("traceId", TraceContext.get());
		initialData.put("runId", runId);
		OverAllState state = new OverAllState(initialData);

		RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
		Optional<OverAllState> result = compiledGraph.invoke(state, config);

		StateSnapshot snapshot = compiledGraph.getState(config);
		if (snapshot != null && "HUMAN_CHECKPOINT".equals(snapshot.next())) {
			log.info("[GraphExecutorService] Graph interrupted at HUMAN_CHECKPOINT, runId={}, sessionId={}", runId, sessionId);
			saveSnapshot(result.orElse(state), runId);
			return buildResponse(result.orElse(state), true);
		} else {
			log.info("[GraphExecutorService] Graph completed, runId={}, sessionId={}", runId, sessionId);
			deleteSnapshot(runId);
			return buildResponse(result.orElse(state), false);
		}
	}

	/**
	 * 人工决策：APPROVE 进入 FINALIZE，REVISE 回到 ANSWER_AGENT（受 maxReviseCount 限制）.
	 */
	public StartSessionResponse human(String sessionId, String runId,
									  DecisionEnum decision, String feedback) {
		RunnableConfig loadConfig = RunnableConfig.builder().threadId(runId).build();

		OverAllState state = loadSnapshot(runId).orElseGet(() -> {
			StateSnapshot snapshot = compiledGraph.getState(loadConfig);
			if (snapshot != null && snapshot.state() != null) {
				return snapshot.state();
			}
			return null;
		});

		if (state == null) {
			throw new BizException(ResultCode.SESSION_NOT_FOUND);
		}

		String storedSessionId = state.value("sessionId", String.class).orElse(null);
		if (!sessionId.equals(storedSessionId)) {
			throw new BizException(ResultCode.BAD_REQUEST, "sessionId 不匹配");
		}

		Number revNum = state.value("reviseCount", Number.class).orElse(0);
		int reviseCount = revNum.intValue();
		if (decision == DecisionEnum.REVISE) {
			if (reviseCount >= maxReviseCount) {
				throw new BizException(ResultCode.MAX_REVISE_EXCEEDED);
			}
			reviseCount++;
		}

		Map<String, Object> updates = new HashMap<>();
		updates.put("decision", decision.name());
		updates.put("feedback", feedback);
		updates.put("reviseCount", reviseCount);

		Optional<OverAllState> result;
		RunnableConfig config;
		try {
			config = RunnableConfig.builder()
					.threadId(runId)
					.resume()
					.addStateUpdate(updates)
					.build();
			result = compiledGraph.invoke(state, config);
		} catch (Exception e) {
			log.error("[GraphExecutorService] resume invoke failed, runId={}, fallback", runId, e);
			config = RunnableConfig.builder()
					.threadId(runId)
					.nextNode("HUMAN_CHECKPOINT")
					.addStateUpdate(updates)
					.build();
			result = compiledGraph.invoke(state, config);
		}

		StateSnapshot snapshot = compiledGraph.getState(config);
		if (snapshot == null) {
			log.warn("[GraphExecutorService] checkpoint lost after invoke, fallback to nextNode, runId={}", runId);
			config = RunnableConfig.builder()
					.threadId(runId)
					.nextNode("HUMAN_CHECKPOINT")
					.addStateUpdate(updates)
					.build();
			result = compiledGraph.invoke(state, config);
		}

		boolean awaitingHuman = false;
		snapshot = compiledGraph.getState(config);
		if (snapshot != null && "HUMAN_CHECKPOINT".equals(snapshot.next())) {
			awaitingHuman = true;
			saveSnapshot(result.orElse(state), runId);
		} else {
			deleteSnapshot(runId);
		}

		return buildResponse(result.orElse(state), awaitingHuman);
	}

	/**
	 * 基于历史复用 level，启动新主题会话.
	 */
	public StartSessionResponse continueSession(String sessionId, String newTopic) {
		List<QaContent> hist = sessionHistoryService.get(sessionId);
		if (hist.isEmpty()) {
			throw new BizException(ResultCode.SESSION_NOT_FOUND, "无历史记录，无法复用 level");
		}
		LevelEnum level = hist.get(hist.size() - 1).getLevel();
		return start(sessionId, newTopic, level, null, false);
	}

	/**
	 * 查询会话历史.
	 */
	public List<QaContent> history(String sessionId) {
		return sessionHistoryService.get(sessionId);
	}

	/**
	 * 流式启动新会话，返回每个节点的执行结果.
	 */
	public Flux<NodeOutput> streamStart(String sessionId, String topic, LevelEnum level,
									   String emailTo, Boolean emailRequired) {
		String runId = UUID.randomUUID().toString();
		Map<String, Object> initialData = new HashMap<>();
		initialData.put("sessionId", sessionId);
		initialData.put("topic", topic);
		initialData.put("level", level);
		initialData.put("emailTo", emailTo);
		initialData.put("emailRequired", emailRequired);
		initialData.put("reviseCount", 0);
		initialData.put("traceId", TraceContext.get());
		initialData.put("runId", runId);

		RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
		return compiledGraph.stream(initialData, config);
	}

	/**
	 * 流式执行人工决策后的流程.
	 */
	public Flux<NodeOutput> streamHuman(String sessionId, String runId,
									   DecisionEnum decision, String feedback) {
		OverAllState state = loadSnapshot(runId).orElseGet(() -> {
			RunnableConfig loadConfig = RunnableConfig.builder().threadId(runId).build();
			StateSnapshot snapshot = compiledGraph.getState(loadConfig);
			if (snapshot != null && snapshot.state() != null) {
				return snapshot.state();
			}
			return null;
		});

		if (state == null) {
			return Flux.error(new BizException(ResultCode.SESSION_NOT_FOUND));
		}

		String storedSessionId = state.value("sessionId", String.class).orElse(null);
		if (!sessionId.equals(storedSessionId)) {
			return Flux.error(new BizException(ResultCode.BAD_REQUEST, "sessionId 不匹配"));
		}

		Number revNum = state.value("reviseCount", Number.class).orElse(0);
		int reviseCount = revNum.intValue();
		if (decision == DecisionEnum.REVISE) {
			if (reviseCount >= maxReviseCount) {
				return Flux.error(new BizException(ResultCode.MAX_REVISE_EXCEEDED));
			}
			reviseCount++;
		}

		Map<String, Object> updates = new HashMap<>();
		updates.put("decision", decision.name());
		updates.put("feedback", feedback);
		updates.put("reviseCount", reviseCount);

		RunnableConfig config = RunnableConfig.builder()
				.threadId(runId)
				.resume()
				.addStateUpdate(updates)
				.build();

		return compiledGraph.graphResponseStream(state, config)
				.map(response -> (NodeOutput) response.getOutput().join());
	}

	private String runKey(String runId) {
		return runPrefix + runId;
	}

	private void saveSnapshot(OverAllState state, String runId) {
		try {
			StoreItem item = StoreItem.of(Arrays.asList("agent", "snapshot"), runId, state.data());
			redisStore.putItem(item);
			log.info("[GraphExecutorService] snapshot saved, runId={}", runId);
		} catch (Exception e) {
			log.error("[GraphExecutorService] saveSnapshot failed, runId={}", runId, e);
		}
	}

	private Optional<OverAllState> loadSnapshot(String runId) {
		try {
			Optional<StoreItem> item = redisStore.getItem(Arrays.asList("agent", "snapshot"), runId);
			if (item.isEmpty()) {
				return Optional.empty();
			}
			Map<String, Object> data = item.get().getValue();
			return Optional.of(new OverAllState(data));
		} catch (Exception e) {
			log.error("[GraphExecutorService] loadSnapshot failed, runId={}", runId, e);
			return Optional.empty();
		}
	}

	private void deleteSnapshot(String runId) {
		try {
			redisStore.deleteItem(Arrays.asList("agent", "snapshot"), runId);
		} catch (Exception e) {
			log.error("[GraphExecutorService] deleteSnapshot failed, runId={}", runId, e);
		}
	}

	private StartSessionResponse buildResponse(OverAllState state, boolean awaitingHuman) {
		String sessionId = state.value("sessionId", String.class).orElse(null);
		String runId = state.value("runId", String.class).orElse(null);
		String topic = state.value("topic", String.class).orElse(null);
		LevelEnum level = LevelEnum.of(state.value("level", String.class).orElse(null));
		Number revNum = state.value("reviseCount", Number.class).orElse(0);
		int reviseCount = revNum.intValue();

		QuestionItem question = state.value("question", Map.class)
				.map(m -> objectMapper.convertValue(m, QuestionItem.class))
				.orElse(null);
		AnswerItem answer = state.value("answer", Map.class)
				.map(m -> objectMapper.convertValue(m, AnswerItem.class))
				.orElse(null);
		ReviewItem review = state.value("review", Map.class)
				.map(m -> objectMapper.convertValue(m, ReviewItem.class))
				.orElse(null);

		return StartSessionResponse.builder()
				.sessionId(sessionId)
				.runId(runId)
				.question(question != null ? question.getContent() : null)
				.answer(answer != null ? answer.getContent() : null)
				.keyPoints(answer != null ? answer.getKeyPoints() : null)
				.level(level != null ? level.name() : null)
				.revision(reviseCount)
				.approved(review != null ? review.getApproved() : null)
				.reviewComments(review != null ? review.getComments() : null)
				.revisionHint(review != null ? review.getRevisionHint() : null)
				.awaitingHuman(awaitingHuman)
				.build();
	}
}
