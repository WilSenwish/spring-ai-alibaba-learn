package com.le.agent.common.exception;

import com.le.agent.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常基类.
 *
 * <p>所有业务侧可预期的异常都应抛出此异常，由 GlobalExceptionHandler 统一捕获.</p>
 */
@Getter
public class BizException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** 业务码 */
	private final int code;

	/** 业务消息 */
	private final String message;

	public BizException(ResultCode resultCode) {
		super(resultCode.getMessage());
		this.code = resultCode.getCode();
		this.message = resultCode.getMessage();
	}

	public BizException(ResultCode resultCode, String message) {
		super(message);
		this.code = resultCode.getCode();
		this.message = message;
	}

	public BizException(int code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}

	public BizException(ResultCode resultCode, Throwable cause) {
		super(resultCode.getMessage(), cause);
		this.code = resultCode.getCode();
		this.message = resultCode.getMessage();
	}
}
