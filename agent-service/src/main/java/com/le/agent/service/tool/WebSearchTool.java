package com.le.agent.service.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * WebSearch 统一入口工具 - 门面类.
 *
 * <p>供 Answer Agent 在知识不足时自主调用。实际搜索逻辑委托给配置或请求指定的具体引擎实现。</p>
 */
@Slf4j
@Component
public class WebSearchTool {

	public static final String ENGINE_ZHIPU_SEARCH_PRO = "zhipu-search-pro";
	public static final String ENGINE_DASHSCOPE_WEB_SEARCH = "dashscope-web-search";

	private final ZhipuWebSearchTool zhipuWebSearchTool;
	private final DashScopeWebSearchTool dashScopeWebSearchTool;
	private final SearchEngineProperties searchEngineProperties;

	public WebSearchTool(ZhipuWebSearchTool zhipuWebSearchTool,
						 DashScopeWebSearchTool dashScopeWebSearchTool,
						 SearchEngineProperties searchEngineProperties) {
		this.zhipuWebSearchTool = zhipuWebSearchTool;
		this.dashScopeWebSearchTool = dashScopeWebSearchTool;
		this.searchEngineProperties = searchEngineProperties;
	}

	/**
	 * 执行网络搜索，根据请求或配置选择具体引擎.
	 *
	 * @param request 搜索请求
	 * @return 搜索结果
	 */
	@Tool(name = "web_search", description = "当现有知识不足以覆盖用户问题时，调用此工具进行网络搜索获取补充信息")
	public WebSearchResult search(WebSearchRequest request) {
		if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
			return WebSearchResult.fail("搜索关键词不能为空");
		}

		String engine = resolveEngine(request.getSearchEngine());
		log.info("[WebSearchTool] query={}, engine={}", request.getQuery(), engine);

		WebSearchResult result;
		if (ENGINE_DASHSCOPE_WEB_SEARCH.equals(engine)) {
			result = dashScopeWebSearchTool.search(request);
		} else {
			// 默认使用智谱 search_pro
			result = zhipuWebSearchTool.search(request);
		}
		result.setSourceEngine(engine);
		return result;
	}

	private String resolveEngine(String requestEngine) {
		if (requestEngine != null && !requestEngine.isBlank()) {
			return normalizeEngine(requestEngine);
		}
		return normalizeEngine(searchEngineProperties.getEngine());
	}

	private String normalizeEngine(String engine) {
		if (ENGINE_DASHSCOPE_WEB_SEARCH.equalsIgnoreCase(engine) || "dashscope".equalsIgnoreCase(engine)) {
			return ENGINE_DASHSCOPE_WEB_SEARCH;
		}
		return ENGINE_ZHIPU_SEARCH_PRO;
	}
}
