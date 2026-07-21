package com.le.agent.service.tool;

import lombok.Data;

import java.util.List;

/**
 * WebSearch 结果 DTO.
 */
@Data
public class WebSearchResult {

	private boolean success;
	private String errorMessage;
	private String summary;
	private List<Item> results;

	/**
	 * 结果来源引擎，用于追踪本次搜索由哪个引擎返回.
	 */
	private String sourceEngine;

	public static WebSearchResult fail(String message) {
		WebSearchResult r = new WebSearchResult();
		r.setSuccess(false);
		r.setErrorMessage(message);
		return r;
	}

	@Data
	public static class Item {
		private String title;
		private String snippet;
		private String url;

		/**
		 * 来源媒体/网站名称.
		 */
		private String media;

		/**
		 * 发布日期，格式 yyyy-MM-dd.
		 */
		private String publishDate;

		/**
		 * 网站图标链接.
		 */
		private String icon;

		public Item() {
		}

		public Item(String title, String snippet, String url) {
			this.title = title;
			this.snippet = snippet;
			this.url = url;
		}
	}
}
