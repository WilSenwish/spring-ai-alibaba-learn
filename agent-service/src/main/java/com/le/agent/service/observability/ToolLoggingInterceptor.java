package com.le.agent.service.observability;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.le.agent.common.api.TraceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具调用日志拦截器.
 *
 * <p>拦截工具调用请求和响应，记录日志和耗时.</p>
 */
@Slf4j
public class ToolLoggingInterceptor extends ToolInterceptor {

	@Override
	public String getName() {
		return "ToolLoggingInterceptor";
	}

	@Override
	public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
		long startTime = System.currentTimeMillis();
		String sessionId = request.getContext() != null ? String.valueOf(request.getContext().get("sessionId")) : null;
		log.info("[tool-call] traceId={}, sessionId={}, toolName={}, toolCallId={}, arguments={}",
				TraceContext.get(), sessionId,
				request.getToolName(),
				request.getToolCallId(),
				request.getArguments() != null ? request.getArguments().length() + " chars" : "null");

		try {
			ToolCallResponse response = handler.call(request);
			long costMs = System.currentTimeMillis() - startTime;
			log.info("[tool-response] traceId={}, sessionId={}, toolName={}, toolCallId={}, costMs={}, status={}, isError={}",
					TraceContext.get(), sessionId,
					response.getToolName(),
					response.getToolCallId(),
					costMs,
					response.getStatus(),
					response.isError());
			return response;
		} catch (Exception e) {
			long costMs = System.currentTimeMillis() - startTime;
			log.error("[tool-error] traceId={}, sessionId={}, toolName={}, toolCallId={}, costMs={}, error={}",
					TraceContext.get(), sessionId,
					request.getToolName(),
					request.getToolCallId(),
					costMs, e.getMessage(), e);
			throw e;
		}
	}
}
