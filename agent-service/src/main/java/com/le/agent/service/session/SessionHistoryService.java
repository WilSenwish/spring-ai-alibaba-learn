package com.le.agent.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.le.agent.common.api.JsonUtils;
import com.le.agent.common.dto.QaContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史服务 - 持久化同一 session 下累积的多组 Q&A.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionHistoryService {

	private static final TypeReference<List<QaContent>> LIST_TYPE = new TypeReference<>() {
	};

	private final JedisPool jedisPool;
	private final ObjectMapper objectMapper;

	@Value("${agent.memory.history-prefix:agent:history:}")
	private String keyPrefix;

	@Value("${agent.memory.max-history-size:50}")
	private int maxSize;

	private String key(String sessionId) {
		return keyPrefix + sessionId;
	}

	/**
	 * 追加一条 Q&A.
	 */
	public void append(String sessionId, QaContent qa) {
		List<QaContent> list = get(sessionId);
		list.add(qa);
		// 截断
		if (list.size() > maxSize) {
			list = new ArrayList<>(list.subList(list.size() - maxSize, list.size()));
		}
		try (Jedis jedis = jedisPool.getResource()) {
			String json = objectMapper.writeValueAsString(list);
			jedis.set(key(sessionId), json);
		} catch (RuntimeException | java.io.IOException e) {
			log.warn("[SessionHistoryService] append fail, sessionId={}, err={}", sessionId, e.getMessage());
		}
	}

	/**
	 * 获取历史.
	 */
	public List<QaContent> get(String sessionId) {
		try (Jedis jedis = jedisPool.getResource()) {
			String json = jedis.get(key(sessionId));
			if (json == null || json.isBlank()) {
				return new ArrayList<>();
			}
			List<QaContent> list = JsonUtils.fromJson(json, LIST_TYPE);
			return list == null ? new ArrayList<>() : list;
		} catch (RuntimeException e) {
			log.warn("[SessionHistoryService] get fail, sessionId={}, err={}", sessionId, e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * 清空历史.
	 */
	public void clear(String sessionId) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.del(key(sessionId));
		} catch (JedisException e) {
			log.warn("[SessionHistoryService] clear fail, sessionId={}, err={}", sessionId, e.getMessage());
		}
	}
}
