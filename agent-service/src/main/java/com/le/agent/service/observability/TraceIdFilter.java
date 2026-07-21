package com.le.agent.service.observability;

import com.le.agent.common.api.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 过滤器 - 在请求入口生成 traceId 并写入 MDC 与响应头.
 *
 * <p>traceId 优先从请求头 X-Trace-Id 读取，便于跨服务调用串联；缺省时生成 UUID.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";
	public static final String MDC_TRACE_ID_KEY = "traceId";

	@Override
	protected void doFilterInternal(HttpServletRequest request,
									HttpServletResponse response,
									FilterChain filterChain) throws ServletException, IOException {
		String traceId = request.getHeader(TRACE_ID_HEADER);
		if (!StringUtils.hasText(traceId)) {
			traceId = UUID.randomUUID().toString().replace("-", "");
		}
		try {
			MDC.put(MDC_TRACE_ID_KEY, traceId);
			TraceContext.set(traceId);
			response.setHeader(TRACE_ID_HEADER, traceId);
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_TRACE_ID_KEY);
			TraceContext.clear();
		}
	}
}
