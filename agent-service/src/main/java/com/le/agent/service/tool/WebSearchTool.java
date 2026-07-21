package com.le.agent.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * WebSearch 工具 - 封装 DashScope WebSearch API.
 *
 * <p>供 Answer Agent 在知识不足时自主调用.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool {

	private final ObjectMapper objectMapper;
	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${spring.ai.dashscope.api-key:}")
	private String apiKey;

	@Value("${spring.ai.dashscope.base-url:https://dashscope.aliyuncs.com/api/v1}")
	private String baseUrl;

	/**
	 * 执行网络搜索.
	 *
	 * @param request 搜索请求
	 * @return 搜索结果
	 */
	@Tool(name = "web_search", description = "当现有知识不足以覆盖用户问题时，调用此工具进行网络搜索获取补充信息")
	public WebSearchResult search(WebSearchRequest request) {
		if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
			return WebSearchResult.fail("搜索关键词不能为空");
		}
		log.info("[WebSearchTool] query={}", request.getQuery());
		try {
			// DashScope 通用搜索 API（简化实现：调用兼容 OpenAI 的 completions 接口并启用 search）
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "Bearer " + apiKey);
			headers.set("Content-Type", "application/json");

			Map<String, Object> body = Map.of(
					"model", "qwen-plus",
					"messages", List.of(Map.of(
							"role", "user",
							"content", "请搜索并总结以下内容：" + request.getQuery()
					)),
					"tools", List.of(Map.of("type", "web_search")),
					"stream", false
			);

			HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
			ResponseEntity<String> resp = restTemplate.exchange(
					baseUrl + "/chat/completions",
					HttpMethod.POST,
					entity,
					String.class);

			if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
				return WebSearchResult.fail("搜索服务返回异常: " + resp.getStatusCode());
			}

			// 简化：直接返回原始响应中的 assistant 消息作为摘要
			Map<String, Object> map = objectMapper.readValue(resp.getBody(), Map.class);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
			if (choices == null || choices.isEmpty()) {
				return WebSearchResult.fail("搜索无结果");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
			String content = message == null ? "" : String.valueOf(message.get("content"));

			WebSearchResult result = new WebSearchResult();
			result.setSuccess(true);
			result.setSummary(content);
			result.setResults(List.of(new WebSearchResult.Item("搜索结果", content, "")));
			log.info("[WebSearchTool] success, summary length={}", content.length());
			return result;
		} catch (Exception e) {
			log.warn("[WebSearchTool] fail: {}", e.getMessage());
			return WebSearchResult.fail("搜索调用失败: " + e.getMessage());
		}
	}
}
