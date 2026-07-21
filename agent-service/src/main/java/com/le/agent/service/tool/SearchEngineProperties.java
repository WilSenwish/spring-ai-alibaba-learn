package com.le.agent.service.tool;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 搜索引擎配置属性.
 *
 * <p>默认使用智谱 search_pro，可选 dashscope-web-search。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "agent.search")
@Getter
@Setter
public class SearchEngineProperties {

	/**
	 * 默认搜索引擎，可选值：
	 * <ul>
	 *     <li>zhipu-search-pro（默认）</li>
	 *     <li>dashscope-web-search</li>
	 * </ul>
	 */
	private String engine = WebSearchTool.ENGINE_ZHIPU_SEARCH_PRO;
}
