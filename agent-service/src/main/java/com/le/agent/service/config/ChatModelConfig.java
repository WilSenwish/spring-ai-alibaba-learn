package com.le.agent.service.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatModel 主模型配置.
 *
 * <p>项目同时引入了 ZhipuAI 和 DashScope 两个 ChatModel，通过 @Primary 指定 ZhipuAI 为主模型.</p>
 */
@Configuration
public class ChatModelConfig {

	@Bean
	@Primary
	public ChatModel primaryChatModel(@Qualifier("zhiPuAiChatModel") ChatModel chatModel) {
		return chatModel;
	}
}
