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

		public Item() {
		}

		public Item(String title, String snippet, String url) {
			this.title = title;
			this.snippet = snippet;
			this.url = url;
		}
	}
}
