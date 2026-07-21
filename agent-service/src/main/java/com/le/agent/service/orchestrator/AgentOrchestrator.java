package com.le.agent.service.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.le.agent.common.api.JsonUtils;
import com.le.agent.common.constants.BizConstants;
import com.le.agent.common.dto.AnswerItem;
import com.le.agent.common.dto.QaContent;
import com.le.agent.common.dto.QuestionItem;
import com.le.agent.common.dto.ReviewItem;
import com.le.agent.common.dto.SessionContext;
import com.le.agent.common.dto.StartSessionResponse;
import com.le.agent.common.enums.DecisionEnum;
import com.le.agent.common.enums.EmailSendStatusEnum;
import com.le.agent.common.enums.LevelEnum;
import com.le.agent.common.exception.BizException;
import com.le.agent.service.agent.AgentNodes;
import com.le.agent.service.email.EmailSendEvent;
import com.le.agent.service.observability.NodeLogger;
import com.le.agent.service.session.SessionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 编排服务.
 *
 * <p>驱动完整流程：Intake → LevelRouter → Question → Answer → Review → Human Checkpoint → Finalize.
 * 所有跨调用状态（中间产物、修订计数）持久化在 Redis 中，runId 作为 Key.
 * Human-in-the-Loop 通过 runId + Redis state 实现：start 后挂起，human 决策时再恢复.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

	private static final TypeReference<SessionContext> CTX_TYPE = new TypeReference<>() {
	};

	private final AgentNodes agentNodes;
	private final SessionHistoryService sessionHistoryService;
	private final JedisPool jedisPool;
	private final ObjectMapper objectMapper;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${agent.orchestrator.max-revise-count:5}")
	private int maxReviseCount;

	@Value("${agent.orchestrator.run-prefix:agent:run:}")
	private String runPrefix;

	// ============== 对外接口 ==============

	/**
	 * 启动一次会话：Intake → Router → Question → Answer → Review，然后挂起等待人工.
	 */
	public StartSessionResponse start(String sessionId, String topic, LevelEnum level,
									  String emailTo, Boolean emailRequired) {
		String runId = UUID.randomUUID().toString().replace("-", "");
		SessionContext initial = SessionContext.builder()
				.sessionId(sessionId)
				.traceId(com.le.agent.common.api.TraceContext.get())
				.rawInput(topic)
				.topic(topic)
				.level(level == null ? LevelEnum.MID : level)
				.reviseCount(0)
				.emailTo(emailTo)
				.emailRequired(Boolean.TRUE.equals(emailRequired))
				.runId(runId)
				.build();

		// 1) Intake
		SessionContext afterIntake = NodeLogger.wrap("INTAKE", sessionId, () -> doIntake(initial));
		// 2) Level Router
		SessionContext afterRouter = NodeLogger.wrap("LEVEL_ROUTER", sessionId, () -> doLevelRouter(afterIntake));

		// 3) Question + Answer + Review
		SessionContext ctx = runQuestionAnswerReview(afterRouter);

		// 4) 持久化快照，等待人工
		saveSnapshot(ctx);

		return buildResponse(ctx, true);
	}

	/**
	 * 人工决策：APPROVE → Finalize；REVISE → Answer+Review 再次执行（保持原 level）.
	 */
	public StartSessionResponse human(String sessionId, String runId, DecisionEnum decision, String feedback) {
		SessionContext ctx = loadSnapshot(runId)
				.orElseThrow(() -> new BizException(com.le.agent.common.api.ResultCode.SESSION_NOT_FOUND,
						"未找到 runId 对应的挂起会话：" + runId));

		if (!ctx.getSessionId().equals(sessionId)) {
			throw new BizException(com.le.agent.common.api.ResultCode.BAD_REQUEST, "sessionId 与 runId 不匹配");
		}

		if (decision == DecisionEnum.REVISE) {
			return doRevise(ctx, feedback);
		}
		return doApprove(ctx, runId);
	}

	/**
	 * 续接会话：使用同一 sessionId 继续问新问题.
	 */
	public StartSessionResponse continueSession(String sessionId, String newTopic) {
		List<QaContent> history = sessionHistoryService.get(sessionId);
		if (history.isEmpty()) {
			throw new BizException(com.le.agent.common.api.ResultCode.SESSION_NOT_FOUND,
					"未找到该 sessionId 的历史 Q&A，请使用 /start 发起新会话");
		}
		// 复用上一次 level
		LevelEnum level = history.get(history.size() - 1).getLevel();
		return start(sessionId, newTopic, level, null, false);
	}

	/**
	 * 查询历史.
	 */
	public List<QaContent> history(String sessionId) {
		return sessionHistoryService.get(sessionId);
	}

	// ============== 决策处理 ==============

	private StartSessionResponse doRevise(SessionContext ctx, String feedback) {
		String sessionId = ctx.getSessionId();
		int count = ctx.getReviseCount() == null ? 0 : ctx.getReviseCount();
		if (count >= maxReviseCount) {
			throw new BizException(com.le.agent.common.api.ResultCode.MAX_REVISE_EXCEEDED,
					"已超过最大修订次数 " + maxReviseCount);
		}
		ctx.setReviseCount(count + 1);
		ctx.setFeedback(feedback);
		String levelName = ctx.getLevel().name();

		// Answer：使用 feedback 重新生成
		final SessionContext beforeAnswer = ctx;
		ctx = NodeLogger.wrap("ANSWER_" + levelName, sessionId, () -> doAnswer(beforeAnswer, feedback));
		// Review
		final SessionContext beforeReview = ctx;
		ctx = NodeLogger.wrap("REVIEW_" + levelName, sessionId, () -> doReview(beforeReview));

		saveSnapshot(ctx);
		return buildResponse(ctx, true);
	}

	private StartSessionResponse doApprove(SessionContext ctx, String runId) {
		String sessionId = ctx.getSessionId();
		final SessionContext before = ctx;
		ctx = NodeLogger.wrap("FINALIZE", sessionId, () -> doFinalize(before));
		deleteSnapshot(runId);

		// 邮件事件
		if (Boolean.TRUE.equals(ctx.getEmailRequired())) {
			try {
				eventPublisher.publishEvent(new EmailSendEvent(this, ctx.getSessionId(), ctx.getTraceId(),
						ctx.getFinalQa(), ctx.getEmailTo()));
				ctx.setEmailStatus(EmailSendStatusEnum.PENDING);
			} catch (Exception ex) {
				log.warn("[AgentOrchestrator] publish email event fail: {}", ex.getMessage());
			}
		}

		return buildResponse(ctx, false);
	}

	// ============== 流程编排 ==============

	private SessionContext runQuestionAnswerReview(SessionContext ctx) {
		String sessionId = ctx.getSessionId();
		String levelName = ctx.getLevel().name();

		// Question
		final SessionContext beforeQ = ctx;
		ctx = NodeLogger.wrap("QUESTION_" + levelName, sessionId, () -> doQuestion(beforeQ));
		// Answer
		final SessionContext beforeA = ctx;
		ctx = NodeLogger.wrap("ANSWER_" + levelName, sessionId, () -> doAnswer(beforeA, null));
		// Review
		final SessionContext beforeR = ctx;
		ctx = NodeLogger.wrap("REVIEW_" + levelName, sessionId, () -> doReview(beforeR));
		return ctx;
	}

	// ============== 节点实现 ==============

	private SessionContext doIntake(SessionContext ctx) {
		if (ctx.getRawInput() == null || ctx.getRawInput().isBlank()) {
			throw new BizException(com.le.agent.common.api.ResultCode.EMPTY_INPUT, "用户输入不能为空");
		}
		if (ctx.getSessionId() == null || ctx.getSessionId().isBlank()) {
			ctx.setSessionId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
		}
		log.info("[Intake] sessionId={}, topic={}", ctx.getSessionId(), ctx.getTopic());
		return ctx;
	}

	private SessionContext doLevelRouter(SessionContext ctx) {
		if (ctx.getLevel() == null) {
			ctx.setLevel(LevelEnum.MID);
			log.info("[LevelRouter] 未指定 level，默认 MID");
		} else {
			log.info("[LevelRouter] 指定 level={}", ctx.getLevel());
		}
		return ctx;
	}

	private SessionContext doQuestion(SessionContext ctx) {
		QuestionItem q = switch (ctx.getLevel()) {
			case JUNIOR -> agentNodes.doQuestionJunior(ctx.getTopic());
			case MID -> agentNodes.doQuestionMid(ctx.getTopic());
			case SENIOR -> agentNodes.doQuestionSenior(ctx.getTopic());
		};
		ctx.setQuestion(q);
		log.info("[Question] level={}, content={}", q.getDifficulty(),
				q.getContent() == null ? "" : q.getContent().substring(0, Math.min(60, q.getContent().length())));
		return ctx;
	}

	private SessionContext doAnswer(SessionContext ctx, String feedback) {
		QuestionItem q = ctx.getQuestion();
		if (q == null || q.getContent() == null) {
			throw new BizException(com.le.agent.common.api.ResultCode.GRAPH_INVOKE_ERROR, "未找到问题，无法作答");
		}
		int revision = ctx.getReviseCount() == null ? 0 : ctx.getReviseCount();
		AnswerItem a = switch (ctx.getLevel()) {
			case JUNIOR -> agentNodes.doAnswerJunior(q.getContent(), feedback, revision);
			case MID -> agentNodes.doAnswerMid(q.getContent(), feedback, revision);
			case SENIOR -> agentNodes.doAnswerSenior(q.getContent(), feedback, revision);
		};
		ctx.setAnswer(a);
		log.info("[Answer] revision={}, keyPoints size={}", revision,
				a.getKeyPoints() == null ? 0 : a.getKeyPoints().size());
		return ctx;
	}

	private SessionContext doReview(SessionContext ctx) {
		QuestionItem q = ctx.getQuestion();
		AnswerItem a = ctx.getAnswer();
		ReviewItem r = switch (ctx.getLevel()) {
			case JUNIOR -> agentNodes.doReviewJunior(q.getContent(), a.getContent());
			case MID -> agentNodes.doReviewMid(q.getContent(), a.getContent());
			case SENIOR -> agentNodes.doReviewSenior(q.getContent(), a.getContent());
		};
		ctx.setReview(r);
		log.info("[Review] approved={}, comments={}", r.getApproved(), r.getComments());
		return ctx;
	}

	private SessionContext doFinalize(SessionContext ctx) {
		QuestionItem q = ctx.getQuestion();
		AnswerItem a = ctx.getAnswer();
		QaContent qa = QaContent.builder()
				.id(UUID.randomUUID().toString().replace("-", ""))
				.sessionId(ctx.getSessionId())
				.topic(ctx.getTopic())
				.level(ctx.getLevel())
				.question(q.getContent())
				.answer(a.getContent())
				.keyPoints(a.getKeyPoints())
				.sampleCode(a.getSampleCode())
				.revision(a.getRevision())
				.createdAt(LocalDateTime.now())
				.build();
		ctx.setFinalQa(qa);
		sessionHistoryService.append(ctx.getSessionId(), qa);
		ctx.setFlowStatus(BizConstants.FlowStatus.COMPLETED);
		log.info("[Finalize] qa.id={}", qa.getId());
		return ctx;
	}

	// ============== 持久化 ==============

	private String runKey(String runId) {
		return runPrefix + runId;
	}

	private void saveSnapshot(SessionContext ctx) {
		ctx.setFlowStatus(BizConstants.FlowStatus.AWAITING_HUMAN);
		try (Jedis jedis = jedisPool.getResource()) {
			String json = objectMapper.writeValueAsString(ctx);
			jedis.setex(runKey(ctx.getRunId()), 24 * 3600, json);
		} catch (Exception e) {
			log.error("[AgentOrchestrator] saveSnapshot fail: {}", e.getMessage(), e);
		}
	}

	private Optional<SessionContext> loadSnapshot(String runId) {
		try (Jedis jedis = jedisPool.getResource()) {
			String json = jedis.get(runKey(runId));
			if (json == null) {
				return Optional.empty();
			}
			SessionContext ctx = JsonUtils.fromJson(json, CTX_TYPE);
			return Optional.ofNullable(ctx);
		} catch (Exception e) {
			log.error("[AgentOrchestrator] loadSnapshot fail: {}", e.getMessage(), e);
			return Optional.empty();
		}
	}

	private void deleteSnapshot(String runId) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.del(runKey(runId));
		} catch (Exception e) {
			log.warn("[AgentOrchestrator] deleteSnapshot fail: {}", e.getMessage());
		}
	}

	// ============== DTO 转换 ==============

	private StartSessionResponse buildResponse(SessionContext ctx, boolean awaitingHuman) {
		AnswerItem a = ctx.getAnswer();
		ReviewItem r = ctx.getReview();
		QuestionItem q = ctx.getQuestion();
		List<String> points = a == null || a.getKeyPoints() == null ? new ArrayList<>() : a.getKeyPoints();

		return StartSessionResponse.builder()
				.sessionId(ctx.getSessionId())
				.runId(ctx.getRunId())
				.question(q == null ? null : q.getContent())
				.answer(a == null ? null : a.getContent())
				.keyPoints(points)
				.level(ctx.getLevel() == null ? null : ctx.getLevel().name())
				.revision(a == null ? 0 : a.getRevision())
				.approved(r == null ? null : r.getApproved())
				.reviewComments(r == null ? null : r.getComments())
				.revisionHint(r == null ? null : r.getRevisionHint())
				.awaitingHuman(awaitingHuman)
				.emailStatus(ctx.getEmailStatus())
				.build();
	}
}
