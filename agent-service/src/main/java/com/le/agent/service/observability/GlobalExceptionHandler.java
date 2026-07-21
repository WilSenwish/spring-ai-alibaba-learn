package com.le.agent.service.observability;

import com.le.agent.common.api.ApiResponse;
import com.le.agent.common.api.ResultCode;
import com.le.agent.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 业务异常.
	 */
	@ExceptionHandler(BizException.class)
	public ApiResponse<Void> handleBizException(BizException ex) {
		log.warn("[BizException] code={}, message={}", ex.getCode(), ex.getMessage());
		if (log.isDebugEnabled()) {
			log.debug("[BizException] stack:", ex);
		}
		return ApiResponse.fail(ex.getCode(), ex.getMessage());
	}

	/**
	 * 参数校验异常.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("[IllegalArgumentException] {}", ex.getMessage());
		return ApiResponse.fail(ResultCode.BAD_REQUEST.getCode(), ex.getMessage());
	}

	/**
	 * 兜底异常.
	 */
	@ExceptionHandler(Throwable.class)
	public ApiResponse<Void> handleUnknown(Throwable ex) {
		log.error("[UnknownException] ", ex);
		return ApiResponse.fail(ResultCode.INTERNAL_ERROR);
	}
}
