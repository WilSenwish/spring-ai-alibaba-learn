package com.le.agent.service.config;

import com.le.agent.service.memory.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置.
 *
 * <p>基于 MessageWindowChatMemory 包装 Redis 持久化 ChatMemoryRepository,
 * 同一 sessionId 在多次调用间共享上下文.</p>
 */
@Slf4j
@Configuration
public class ChatMemoryConfig {

	@Value("${agent.memory.max-history-size:50}")
	private int maxMessages;

	@Bean
	public ChatMemory chatMemory(ChatMemoryRepository repository) {
		log.info("[ChatMemory] init with maxMessages={}, repo={}", maxMessages, repository.getClass().getSimpleName());
		return MessageWindowChatMemory.builder()
				.maxMessages(maxMessages)
				.chatMemoryRepository(repository)
				.build();
	}
}
