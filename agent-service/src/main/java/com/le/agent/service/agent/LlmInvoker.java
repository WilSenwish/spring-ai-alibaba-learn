package com.le.agent.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.le.agent.common.api.JsonUtils;
import com.le.agent.common.exception.LlmInvokeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通用 LLM 调用工具.
 *
 * <p>封装 ChatClient 调用与 JSON 解析，提供 question/answer/review 三类结构化输出.</p>
 */
@Slf4j
@Component
public class LlmInvoker {

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;

	public LlmInvoker(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
		this.chatClient = chatClientBuilder.build();
		this.objectMapper = objectMapper;
	}

	/**
	 * 简单文本调用.
	 */
	public String callText(String systemPrompt, String userPrompt) {
		try {
			return chatClient.prompt()
					.system(systemPrompt)
					.user(userPrompt)
					.call()
					.content();
		} catch (Exception e) {
			log.error("[LlmInvoker] callText failed: {}", e.getMessage(), e);
			throw new LlmInvokeException("LLM 调用失败：" + e.getMessage(), e);
		}
	}

	/**
	 * 结构化 JSON 调用 - 解析为 Map.
	 */
	public Map<String, Object> callJson(String systemPrompt, String userPrompt) {
		String text = callText(systemPrompt, userPrompt);
		return parseJsonMap(text);
	}

	/**
	 * 解析 JSON 字符串为 Map.
	 */
	public Map<String, Object> parseJsonMap(String text) {
		if (text == null) {
			return Map.of();
		}
		String cleaned = stripCodeFence(text);
		Map<String, Object> map = JsonUtils.fromJson(cleaned, new TypeReference<Map<String, Object>>() {
		});
		if (map == null) {
			log.warn("[LlmInvoker] parseJsonMap empty, raw={}", text);
			return Map.of();
		}
		return map;
	}

	/**
	 * 去掉 Markdown ```json ... ``` 包装.
	 */
	public String stripCodeFence(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			if (firstNewline > 0) {
				trimmed = trimmed.substring(firstNewline + 1);
			}
			if (trimmed.endsWith("```")) {
				trimmed = trimmed.substring(0, trimmed.length() - 3);
			}
		}
		return trimmed.trim();
	}

	/**
	 * 直接拿 AssistantMessage（备用，目前未用）.
	 */
	@SuppressWarnings("unused")
	public AssistantMessage callMessage(String systemPrompt, String userPrompt) {
		try {
			return chatClient.prompt()
					.system(systemPrompt)
					.user(userPrompt)
					.call()
					.chatResponse()
					.getResult()
					.getOutput();
		} catch (Exception e) {
			throw new LlmInvokeException("LLM 调用失败：" + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unused")
	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (Exception e) {
			return String.valueOf(obj);
		}
	}
}
