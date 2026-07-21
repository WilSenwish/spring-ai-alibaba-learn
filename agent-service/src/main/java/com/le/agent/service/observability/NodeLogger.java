package com.le.agent.service.observability;

import com.le.agent.common.api.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.Callable;

/**
 * 节点日志工具.
 *
 * <p>封装 [node-enter]/[node-exit] 结构化日志，自动读取 traceId.</p>
 */
@Slf4j
public final class NodeLogger {

	private static final String MDC_NODE_KEY = "nodeId";

	private NodeLogger() {
	}

	/**
	 * 节点进入日志.
	 */
	public static void enter(String nodeId, String sessionId) {
		MDC.put(MDC_NODE_KEY, nodeId);
		log.info("[node-enter] traceId={}, sessionId={}, node={}", TraceContext.get(), sessionId, nodeId);
	}

	/**
	 * 节点退出日志（成功）.
	 */
	public static void exit(String nodeId, String sessionId, long costMs) {
		log.info("[node-exit] traceId={}, sessionId={}, node={}, costMs={}, status=ok",
				TraceContext.get(), sessionId, nodeId, costMs);
		MDC.remove(MDC_NODE_KEY);
	}

	/**
	 * 节点退出日志（失败）.
	 */
	public static void exitWithError(String nodeId, String sessionId, long costMs, Throwable t) {
		log.error("[node-exit] traceId={}, sessionId={}, node={}, costMs={}, status=error, err={}",
				TraceContext.get(), sessionId, nodeId, costMs, t.getMessage(), t);
		MDC.remove(MDC_NODE_KEY);
	}

	/**
	 * 包裹节点执行：进入/退出日志 + 耗时统计.
	 */
	public static <T> T wrap(String nodeId, String sessionId, Callable<T> action) {
		long start = System.currentTimeMillis();
		enter(nodeId, sessionId);
		try {
			T result = action.call();
			exit(nodeId, sessionId, System.currentTimeMillis() - start);
			return result;
		} catch (RuntimeException re) {
			exitWithError(nodeId, sessionId, System.currentTimeMillis() - start, re);
			throw re;
		} catch (Exception e) {
			exitWithError(nodeId, sessionId, System.currentTimeMillis() - start, e);
			throw new RuntimeException(e);
		}
	}
}
