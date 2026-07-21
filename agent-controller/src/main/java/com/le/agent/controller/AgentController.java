package com.le.agent.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.le.agent.common.api.ApiResponse;
import com.le.agent.common.dto.HumanDecisionRequest;
import com.le.agent.common.dto.QaContent;
import com.le.agent.common.dto.StartSessionRequest;
import com.le.agent.common.dto.StartSessionResponse;
import com.le.agent.common.enums.LevelEnum;
import com.le.agent.service.email.EmailSendEvent;
import com.le.agent.service.graph.GraphExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 框架 REST 接口.
 *
 * <p>所有方法均返回 {@link ApiResponse}，不写业务逻辑，仅调用 {@link GraphExecutorService}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

	private final GraphExecutorService graphExecutorService;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 发起新会话.
	 */
	@PostMapping("/start")
	public ApiResponse<StartSessionResponse> start(@RequestBody StartSessionRequest request) {
		log.info("[AgentController] /start topic={}, level={}", request.getTopic(), request.getLevel());
		StartSessionResponse resp = graphExecutorService.start(
				request.getSessionId(),
				request.getTopic(),
				request.getLevel(),
				request.getEmailTo(),
				request.getEmailRequired());
		return ApiResponse.success(resp);
	}

	/**
	 * 人工决策（通过/修订）.
	 */
	@PostMapping("/human")
	public ApiResponse<StartSessionResponse> human(@RequestBody HumanDecisionRequest request) {
		log.info("[AgentController] /human sessionId={}, runId={}, decision={}",
				request.getSessionId(), request.getRunId(), request.getDecision());
		StartSessionResponse resp = graphExecutorService.human(
				request.getSessionId(),
				request.getRunId(),
				request.getDecision(),
				request.getFeedback());
		return ApiResponse.success(resp);
	}

	/**
	 * 续接会话：同 sessionId 继续问新问题.
	 */
	@PostMapping("/continue")
	public ApiResponse<StartSessionResponse> continueSession(@RequestBody StartSessionRequest request) {
		log.info("[AgentController] /continue sessionId={}, topic={}", request.getSessionId(), request.getTopic());
		StartSessionResponse resp = graphExecutorService.continueSession(request.getSessionId(), request.getTopic());
		return ApiResponse.success(resp);
	}

	/**
	 * 查询历史.
	 */
	@GetMapping("/history/{sessionId}")
	public ApiResponse<List<QaContent>> history(@PathVariable String sessionId) {
		return ApiResponse.success(graphExecutorService.history(sessionId));
	}

	/**
	 * 邮件重试 - 取最后一次 Q&A 重新发布 EmailSendEvent.
	 */
	@PostMapping("/email/retry/{sessionId}")
	public ApiResponse<String> retryEmail(@PathVariable String sessionId) {
		List<QaContent> history = graphExecutorService.history(sessionId);
		if (history.isEmpty()) {
			return ApiResponse.fail(4003, "未找到该 session 的历史 Q&A，无法重试邮件");
		}
		QaContent latest = history.get(history.size() - 1);
		eventPublisher.publishEvent(new EmailSendEvent(
				this, sessionId, com.le.agent.common.api.TraceContext.get(), latest, latest.getSessionId()));
		log.info("[AgentController] /email/retry sessionId={}, qaId={}", sessionId, latest.getId());
		return ApiResponse.success("邮件已重新加入发送队列");
	}

	/**
	 * 流式启动新会话 - SSE 推送每个节点的执行结果.
	 */
	@PostMapping(value = "/stream/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<NodeOutput> streamStart(@RequestBody StartSessionRequest request) {
		log.info("[AgentController] /stream/start topic={}, level={}", request.getTopic(), request.getLevel());
		return graphExecutorService.streamStart(
				request.getSessionId(),
				request.getTopic(),
				request.getLevel(),
				request.getEmailTo(),
				request.getEmailRequired());
	}

	/**
	 * 流式人工决策 - SSE 推送人工决策后的执行结果.
	 */
	@PostMapping(value = "/stream/human", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<NodeOutput> streamHuman(@RequestBody HumanDecisionRequest request) {
		log.info("[AgentController] /stream/human sessionId={}, runId={}, decision={}",
				request.getSessionId(), request.getRunId(), request.getDecision());
		return graphExecutorService.streamHuman(
				request.getSessionId(),
				request.getRunId(),
				request.getDecision(),
				request.getFeedback());
	}
}
