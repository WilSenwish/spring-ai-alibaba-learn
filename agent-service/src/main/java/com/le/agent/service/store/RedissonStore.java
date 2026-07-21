package com.le.agent.service.store;

import com.alibaba.cloud.ai.graph.store.NamespaceListRequest;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.StoreSearchRequest;
import com.alibaba.cloud.ai.graph.store.StoreSearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于 Redisson 的生产级 Store 实现.
 *
 * <p>实现 Store 接口，使用 Redisson 真正持久化到 Redis.</p>
 * <p>Key 格式: {namespace}:{itemId}</p>
 */
@Slf4j
public class RedissonStore implements Store {

	private static final String KEY_SEPARATOR = ":";
	private static final String KEY_PREFIX = "agent:store:";
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	public RedissonStore(RedissonClient redissonClient) {
		this.redissonClient = redissonClient;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public void putItem(StoreItem item) {
		String key = buildKey(item.getNamespace(), item.getKey());
		try {
			Map<String, Object> value = new HashMap<>();
			value.put("namespace", item.getNamespace());
			value.put("key", item.getKey());
			value.put("value", item.getValue());
			value.put("createdAt", item.getCreatedAt());
			value.put("updatedAt", item.getUpdatedAt());
			String json = objectMapper.writeValueAsString(value);
			RBucket<String> bucket = redissonClient.getBucket(key);
			bucket.set(json);
			log.debug("[RedissonStore] putItem success, key={}", key);
		} catch (JsonProcessingException e) {
			log.error("[RedissonStore] putItem failed, key={}, error={}", key, e.getMessage());
			throw new RuntimeException("Failed to serialize store item", e);
		}
	}

	@Override
	public Optional<StoreItem> getItem(List<String> namespace, String itemId) {
		String key = buildKey(namespace, itemId);
		try {
			RBucket<String> bucket = redissonClient.getBucket(key);
			String json = bucket.get();
			if (json == null || json.isEmpty()) {
				return Optional.empty();
			}
			Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);
			StoreItem item = new StoreItem(
					convertToList(map.get("namespace")),
					String.valueOf(map.get("key")),
					convertToMap(map.get("value")),
					map.get("createdAt") != null ? ((Number) map.get("createdAt")).longValue() : 0L,
					map.get("updatedAt") != null ? ((Number) map.get("updatedAt")).longValue() : 0L
			);
			return Optional.of(item);
		} catch (Exception e) {
			log.error("[RedissonStore] getItem failed, key={}, error={}", key, e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public boolean deleteItem(List<String> namespace, String itemId) {
		String key = buildKey(namespace, itemId);
		try {
			RBucket<String> bucket = redissonClient.getBucket(key);
			boolean deleted = bucket.delete();
			log.debug("[RedissonStore] deleteItem result={}, key={}", deleted, key);
			return deleted;
		} catch (Exception e) {
			log.error("[RedissonStore] deleteItem failed, key={}, error={}", key, e.getMessage());
			return false;
		}
	}

	@Override
	public StoreSearchResult searchItems(StoreSearchRequest request) {
		try {
			List<String> namespace = request.getNamespace();
			String namespacePrefix = buildNamespacePrefix(namespace);
			RKeys keys = redissonClient.getKeys();
			Iterable<String> keyIterable = keys.getKeysByPattern(namespacePrefix + "*");
			
			List<StoreItem> items = new ArrayList<>();
			for (String key : keyIterable) {
				Optional<StoreItem> item = getItemByKey(key);
				item.ifPresent(items::add);
			}

			int limit = request.getLimit() > 0 ? request.getLimit() : 100;
			int offset = request.getOffset();
			long total = items.size();
			List<StoreItem> pagedItems = items.stream()
					.skip(offset)
					.limit(limit)
					.collect(Collectors.toList());

			return new StoreSearchResult(pagedItems, total, offset, limit);
		} catch (Exception e) {
			log.error("[RedissonStore] searchItems failed, error={}", e.getMessage());
			return StoreSearchResult.empty();
		}
	}

	@Override
	public List<String> listNamespaces(NamespaceListRequest request) {
		try {
			List<String> namespace = request.getNamespace();
			String prefix = namespace != null && !namespace.isEmpty() 
					? String.join(KEY_SEPARATOR, namespace) + KEY_SEPARATOR 
					: "";
			String pattern = KEY_PREFIX + prefix + "*";
			RKeys keys = redissonClient.getKeys();
			
			List<String> namespaces = new ArrayList<>();
			for (String key : keys.getKeysByPattern(pattern)) {
				String ns = extractNamespace(key);
				if (ns != null && !namespaces.contains(ns)) {
					namespaces.add(ns);
				}
			}
			return namespaces;
		} catch (Exception e) {
			log.error("[RedissonStore] listNamespaces failed, error={}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public void clear() {
		try {
			RKeys keys = redissonClient.getKeys();
			for (String key : keys.getKeysByPattern(KEY_PREFIX + "*")) {
				redissonClient.getBucket(key).delete();
			}
			log.info("[RedissonStore] clear completed");
		} catch (Exception e) {
			log.error("[RedissonStore] clear failed, error={}", e.getMessage());
		}
	}

	@Override
	public long size() {
		try {
			RKeys keys = redissonClient.getKeys();
			long count = 0;
			for (String ignored : keys.getKeysByPattern(KEY_PREFIX + "*")) {
				count++;
			}
			return count;
		} catch (Exception e) {
			log.error("[RedissonStore] size failed, error={}", e.getMessage());
			return 0;
		}
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	private String buildKey(List<String> namespace, String itemId) {
		String ns = namespace != null ? String.join(KEY_SEPARATOR, namespace) : "";
		return KEY_PREFIX + ns + KEY_SEPARATOR + itemId;
	}

	private String buildNamespacePrefix(List<String> namespace) {
		if (namespace == null || namespace.isEmpty()) {
			return KEY_PREFIX;
		}
		return KEY_PREFIX + String.join(KEY_SEPARATOR, namespace) + KEY_SEPARATOR;
	}

	private Optional<StoreItem> getItemByKey(String key) {
		try {
			RBucket<String> bucket = redissonClient.getBucket(key);
			String json = bucket.get();
			if (json == null || json.isEmpty()) {
				return Optional.empty();
			}
			Map<String, Object> map = objectMapper.readValue(json, MAP_TYPE);
			StoreItem item = new StoreItem(
					convertToList(map.get("namespace")),
					String.valueOf(map.get("key")),
					convertToMap(map.get("value")),
					map.get("createdAt") != null ? ((Number) map.get("createdAt")).longValue() : 0L,
					map.get("updatedAt") != null ? ((Number) map.get("updatedAt")).longValue() : 0L
			);
			return Optional.of(item);
		} catch (Exception e) {
			log.warn("[RedissonStore] getItemByKey failed, key={}, error={}", key, e.getMessage());
			return Optional.empty();
		}
	}

	private String extractNamespace(String key) {
		if (!key.startsWith(KEY_PREFIX)) {
			return null;
		}
		String suffix = key.substring(KEY_PREFIX.length());
		int lastSep = suffix.lastIndexOf(KEY_SEPARATOR);
		return lastSep > 0 ? suffix.substring(0, lastSep) : null;
	}

	@SuppressWarnings("unchecked")
	private List<String> convertToList(Object obj) {
		if (obj == null) {
			return Collections.emptyList();
		}
		if (obj instanceof List) {
			return (List<String>) obj;
		}
		return Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> convertToMap(Object obj) {
		if (obj == null) {
			return Collections.emptyMap();
		}
		if (obj instanceof Map) {
			return (Map<String, Object>) obj;
		}
		return Collections.emptyMap();
	}
}