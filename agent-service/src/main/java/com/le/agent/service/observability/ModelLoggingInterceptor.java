package com.le.agent.service.observability;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.le.agent.common.api.TraceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 模型调用日志拦截器.
 *
 * <p>拦截 LLM 调用请求和响应，记录日志和耗时.</p>
 */
@Slf4j
public class ModelLoggingInterceptor extends ModelInterceptor {

	@Override
	public String getName() {
		return "ModelLoggingInterceptor";
	}

	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		long startTime = System.currentTimeMillis();
		String sessionId = request.getContext() != null ? String.valueOf(request.getContext().get("sessionId")) : null;
		log.info("[model-call] traceId={}, sessionId={}, systemPrompt={}, messages={}, tools={}",
				TraceContext.get(), sessionId,
				request.getSystemMessage() != null ? request.getSystemMessage().getText().length() + " chars" : "null",
				request.getMessages() != null ? request.getMessages().size() : 0,
				request.getTools() != null ? request.getTools().size() : 0);

		try {
			ModelResponse response = handler.call(request);
			long costMs = System.currentTimeMillis() - startTime;
			log.info("[model-response] traceId={}, sessionId={}, costMs={}, responseType={}",
					TraceContext.get(), sessionId, costMs,
					response.getMessage() != null ? response.getMessage().getClass().getSimpleName() : "null");
			return response;
		} catch (Exception e) {
			long costMs = System.currentTimeMillis() - startTime;
			log.error("[model-error] traceId={}, sessionId={}, costMs={}, error={}",
					TraceContext.get(), sessionId, costMs, e.getMessage(), e);
			throw e;
		}
	}
}
