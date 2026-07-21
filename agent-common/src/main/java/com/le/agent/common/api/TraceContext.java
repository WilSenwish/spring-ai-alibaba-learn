package com.le.agent.common.api;

/**
 * 链路追踪上下文，基于 ThreadLocal 透传 traceId.
 *
 * <p>由 agent-service 中的 TraceIdFilter 写入，由 ApiResponse 读取.</p>
 */
public final class TraceContext {

	private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

	private TraceContext() {
	}

	public static void set(String traceId) {
		TRACE_ID.set(traceId);
	}

	public static String get() {
		return TRACE_ID.get();
	}

	public static void clear() {
		TRACE_ID.remove();
	}
}
