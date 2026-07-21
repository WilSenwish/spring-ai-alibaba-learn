package com.le.agent.common.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类.
 */
@Slf4j
public final class JsonUtils {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private JsonUtils() {
	}

	/**
	 * 对象转 JSON 字符串.
	 */
	public static String toJson(Object obj) {
		if (obj == null) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			log.warn("[JsonUtils] toJson failed: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * JSON 字符串转对象.
	 */
	public static <T> T fromJson(String json, Class<T> clazz) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readValue(json, clazz);
		} catch (Exception e) {
			log.warn("[JsonUtils] fromJson failed: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * JSON 字符串转复杂类型.
	 */
	public static <T> T fromJson(String json, TypeReference<T> typeReference) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readValue(json, typeReference);
		} catch (Exception e) {
			log.warn("[JsonUtils] fromJson(TypeReference) failed: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Map 转 List.
	 */
	@SuppressWarnings("unchecked")
	public static List<Map<String, Object>> toList(Object obj) {
		if (obj == null) {
			return Collections.emptyList();
		}
		try {
			return MAPPER.convertValue(obj, new TypeReference<List<Map<String, Object>>>() {
			});
		} catch (Exception e) {
			log.warn("[JsonUtils] toList failed: {}", e.getMessage());
			return Collections.emptyList();
		}
	}
}
