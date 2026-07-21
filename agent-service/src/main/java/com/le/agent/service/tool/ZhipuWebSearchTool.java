package com.le.agent.service.tool;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.web_search.WebSearchDTO;
import ai.z.openapi.service.web_search.WebSearchRequest;
import ai.z.openapi.service.web_search.WebSearchResponse;
import ai.z.openapi.service.web_search.WebSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 智谱 WebSearch 实现 - 使用智谱官方 Web Search API.
 *
 * <p>默认使用 search_pro 引擎，返回结构化搜索结果。</p>
 */
@Slf4j
@Component
public class ZhipuWebSearchTool {

	public static final String SEARCH_PRO = "search_pro";

	@Value("${spring.ai.dashscope.api-key:}")
	private String apiKey;

	@Value("${agent.search.zhipu.count:10}")
	private int count;

	@Value("${agent.search.zhipu.content-size:medium}")
	private String contentSize;

	@Value("${agent.search.zhipu.recency-filter:noLimit}")
	private String recencyFilter;

	/**
	 * 执行智谱网络搜索.
	 *
	 * @param request 搜索请求
	 * @return 搜索结果
	 */
	public WebSearchResult search(com.le.agent.service.tool.WebSearchRequest request) {
		if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
			return WebSearchResult.fail("搜索关键词不能为空");
		}
		log.info("[ZhipuWebSearchTool] query={}", request.getQuery());

		try {
			ZhipuAiClient client = ZhipuAiClient.builder().apiKey(apiKey).build();
			WebSearchService webSearchService = client.webSearch();

			WebSearchRequest zhipuRequest = WebSearchRequest.builder()
					.searchEngine(SEARCH_PRO)
					.searchQuery(request.getQuery())
					.count(clampCount(count))
					.contentSize(contentSize)
					.searchRecencyFilter(recencyFilter)
					.build();

			WebSearchResponse response = webSearchService.createWebSearch(zhipuRequest);
			if (response == null || !response.isSuccess() || response.getData() == null) {
				String msg = response == null ? "响应为空" : response.getMsg();
				return WebSearchResult.fail("智谱搜索返回异常: " + msg);
			}

			WebSearchDTO data = response.getData();
			List<WebSearchResult.Item> items = Optional.ofNullable(data.getWebSearchResp())
					.orElse(Collections.emptyList())
					.stream()
					.map(r -> {
						WebSearchResult.Item item = new WebSearchResult.Item();
						item.setTitle(r.getTitle());
						item.setSnippet(r.getContent());
						item.setUrl(r.getLink());
						item.setMedia(r.getMedia());
						item.setPublishDate(r.getPublishDate());
						item.setIcon(r.getIcon());
						return item;
					})
					.collect(Collectors.toList());

			String summary = items.stream()
					.map(i -> i.getTitle() + "：" + i.getSnippet())
					.collect(Collectors.joining("\n"));

			WebSearchResult result = new WebSearchResult();
			result.setSuccess(true);
			result.setSummary(summary);
			result.setResults(items);
			log.info("[ZhipuWebSearchTool] success, result count={}", items.size());
			return result;
		} catch (Exception e) {
			log.warn("[ZhipuWebSearchTool] fail: {}", e.getMessage());
			return WebSearchResult.fail("智谱搜索调用失败: " + e.getMessage());
		}
	}

	private int clampCount(int value) {
		if (value < 1) {
			return 1;
		}
		return Math.min(value, 50);
	}
}
