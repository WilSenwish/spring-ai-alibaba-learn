package com.le.agent.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.le.agent.common.api.JsonUtils;
import com.le.agent.common.dto.QaContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	@Value("${agent.memory.history-prefix:agent:history:}")
	private String keyPrefix;

	@Value("${agent.memory.max-history-size:50}")
	private int maxSize;

	private String key(String sessionId) {
		return keyPrefix + sessionId;
	}

	public void append(String sessionId, QaContent qa) {
		List<QaContent> list = get(sessionId);
		list.add(qa);
		if (list.size() > maxSize) {
			list = new ArrayList<>(list.subList(list.size() - maxSize, list.size()));
		}
		try {
			RBucket<List<QaContent>> bucket = redissonClient.getBucket(key(sessionId));
			bucket.set(list);
		} catch (RuntimeException e) {
			log.warn("[SessionHistoryService] append fail, sessionId={}, err={}", sessionId, e.getMessage());
		}
	}

	public List<QaContent> get(String sessionId) {
		try {
			RBucket<List<QaContent>> bucket = redissonClient.getBucket(key(sessionId));
			List<QaContent> list = bucket.get();
			return list == null ? new ArrayList<>() : list;
		} catch (RuntimeException e) {
			log.warn("[SessionHistoryService] get fail, sessionId={}, err={}", sessionId, e.getMessage());
			return new ArrayList<>();
		}
	}

	public void clear(String sessionId) {
		try {
			RBucket<List<QaContent>> bucket = redissonClient.getBucket(key(sessionId));
			bucket.delete();
		} catch (RuntimeException e) {
			log.warn("[SessionHistoryService] clear fail, sessionId={}, err={}", sessionId, e.getMessage());
		}
	}
}
