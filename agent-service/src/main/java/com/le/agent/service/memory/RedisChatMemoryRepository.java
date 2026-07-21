package com.le.agent.service.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Jedis 的 ChatMemory 持久化实现.
 *
 * <p>按 conversationId 隔离，每个 sessionId 对应一个 Redis key，序列化为 JSON 数组.
 * 数组中每条消息以 {@code {role, content}} 形式存储.</p>
 */
@Slf4j
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

	private static final TypeReference<List<Map<String, String>>> LIST_TYPE = new TypeReference<>() {
	};

	private final JedisPool jedisPool;
	private final ObjectMapper objectMapper;
	private final String keyPrefix;

	public RedisChatMemoryRepository(JedisPool jedisPool,
									 ObjectMapper objectMapper,
									 @Value("${agent.memory.history-prefix:agent:history:}") String keyPrefix) {
		this.jedisPool = jedisPool;
		this.objectMapper = objectMapper;
		this.keyPrefix = keyPrefix;
	}

	private String key(String conversationId) {
		return keyPrefix + conversationId;
	}

	@Override
	public List<String> findConversationIds() {
		List<String> ids = new ArrayList<>();
		try (Jedis jedis = jedisPool.getResource()) {
			var keys = jedis.keys(keyPrefix + "*");
			if (keys != null) {
				for (String k : keys) {
					ids.add(k.substring(keyPrefix.length()));
				}
			}
		} catch (Exception e) {
			log.warn("[RedisChatMemoryRepository] list keys fail, err={}", e.getMessage());
		}
		return ids;
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		try (Jedis jedis = jedisPool.getResource()) {
			String json = jedis.get(key(conversationId));
			if (json == null || json.isBlank()) {
				return new ArrayList<>();
			}
			List<Map<String, String>> raw = objectMapper.readValue(json, LIST_TYPE);
			List<Message> result = new ArrayList<>(raw.size());
			for (Map<String, String> item : raw) {
				Message msg = toMessage(item);
				if (msg != null) {
					result.add(msg);
				}
			}
			return result;
		} catch (Exception e) {
			log.warn("[RedisChatMemoryRepository] read fail, conversationId={}, err={}", conversationId, e.getMessage());
			return new ArrayList<>();
		}
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return;
		}
		List<Map<String, String>> raw = new ArrayList<>(messages.size());
		for (Message m : messages) {
			String role = m.getClass().getSimpleName().replace("Message", "").toLowerCase();
			String content = m.getText() == null ? "" : m.getText();
			raw.add(Map.of(
					"role", role,
					"content", content
			));
		}
		try (Jedis jedis = jedisPool.getResource()) {
			String json = objectMapper.writeValueAsString(raw);
			jedis.set(key(conversationId), json);
		} catch (Exception e) {
			log.warn("[RedisChatMemoryRepository] write fail, conversationId={}, err={}", conversationId, e.getMessage());
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.del(key(conversationId));
		} catch (Exception e) {
			log.warn("[RedisChatMemoryRepository] delete fail, conversationId={}, err={}", conversationId, e.getMessage());
		}
	}

	private Message toMessage(Map<String, String> item) {
		String role = item.getOrDefault("role", "user");
		String content = item.getOrDefault("content", "");
		String type = item.getOrDefault("type", "");
		// 优先用 type 字段判断，兼容 role 命名
		String check = (type + role).toLowerCase();
		if (check.contains("system")) {
			return new SystemMessage(content);
		}
		if (check.contains("assistant")) {
			return new AssistantMessage(content);
		}
		return new UserMessage(content);
	}

	@SuppressWarnings("unused")
	private Map<String, String> of(String k, String v) {
		Map<String, String> m = new HashMap<>(2);
		m.put(k, v);
		return m;
	}
}
