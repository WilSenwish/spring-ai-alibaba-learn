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
}
