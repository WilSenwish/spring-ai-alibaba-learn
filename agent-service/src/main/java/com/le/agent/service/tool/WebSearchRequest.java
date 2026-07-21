package com.le.agent.service.tool;

import lombok.Data;

/**
 * WebSearch 请求 DTO.
 */
@Data
public class WebSearchRequest {

	/**
	 * 搜索关键词.
	 */
	private String query;

	/**
	 * 搜索引擎，可选值：
	 * <ul>
	 *     <li>zhipu-search-pro（默认）</li>
	 *     <li>dashscope-web-search</li>
	 * </ul>
	 * 为空时使用系统默认配置.
	 */
	private String searchEngine;
}
