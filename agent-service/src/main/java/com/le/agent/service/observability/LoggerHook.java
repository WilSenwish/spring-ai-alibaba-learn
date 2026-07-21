package com.le.agent.service.observability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.le.agent.common.api.TraceContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Agent 生命周期日志 Hook.
 *
 * <p>替代 NodeLogger.wrap()，在 BEFORE_AGENT 和 AFTER_AGENT 位置自动输出 [node-enter]/[node-exit] 日志.</p>
 */
@Slf4j
public class LoggerHook extends ModelHook {

	private final Map<String, Long> startTimeMap = new ConcurrentHashMap<>();

	@Override
	public String getName() {
		return "LoggerHook";
	}

	@Override
	public HookPosition[] getHookPositions() {
		return new HookPosition[]{HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT};
	}

	@Override
	public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
		String agentName = getAgentName();
		String sessionId = state.value("sessionId", String.class).orElse("");
		long startTime = System.currentTimeMillis();
		startTimeMap.put(agentName, startTime);
		log.info("[node-enter] traceId={}, sessionId={}, node={}", TraceContext.get(), sessionId, agentName);
		return CompletableFuture.completedFuture(new HashMap<>());
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
		String agentName = getAgentName();
		String sessionId = state.value("sessionId", String.class).orElse("");
		long startTime = startTimeMap.remove(agentName);
		long costMs = System.currentTimeMillis() - startTime;
		log.info("[node-exit] traceId={}, sessionId={}, node={}, costMs={}, status=ok",
				TraceContext.get(), sessionId, agentName, costMs);
		return CompletableFuture.completedFuture(new HashMap<>());
	}
}
