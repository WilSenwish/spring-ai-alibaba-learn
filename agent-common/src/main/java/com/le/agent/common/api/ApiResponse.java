package com.le.agent.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一接口返回体.
 *
 * <p>所有 controller 接口的返回结构都必须是 ApiResponse<T>，包含 code/message/data/traceId.</p>
 *
 * @param <T> data 字段类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 业务码（0 表示成功） */
	private int code;

	/** 业务消息 */
	private String message;

	/** 返回数据 */
	private T data;

	/** 链路追踪 ID */
	private String traceId;

	/** 时间戳（毫秒） */
	private Long timestamp;

	/**
	 * 成功响应（无 data）.
	 */
	public static <T> ApiResponse<T> success() {
		return success(null);
	}

	/**
	 * 成功响应.
	 */
	public static <T> ApiResponse<T> success(T data) {
		return ApiResponse.<T>builder()
				.code(ResultCode.SUCCESS.getCode())
				.message(ResultCode.SUCCESS.getMessage())
				.data(data)
				.traceId(TraceContext.get())
				.timestamp(System.currentTimeMillis())
				.build();
	}

	/**
	 * 失败响应（自定义业务码 + 消息）.
	 */
	public static <T> ApiResponse<T> fail(ResultCode resultCode) {
		return fail(resultCode.getCode(), resultCode.getMessage());
	}

	/**
	 * 失败响应.
	 */
	public static <T> ApiResponse<T> fail(int code, String message) {
		return ApiResponse.<T>builder()
				.code(code)
				.message(message)
				.traceId(TraceContext.get())
				.timestamp(System.currentTimeMillis())
				.build();
	}
}
