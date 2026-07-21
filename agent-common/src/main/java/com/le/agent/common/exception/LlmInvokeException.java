package com.le.agent.common.exception;

import com.le.agent.common.api.ResultCode;

/**
 * LLM 调用异常.
 */
public class LlmInvokeException extends BizException {

	public LlmInvokeException(String message) {
		super(ResultCode.LLM_INVOKE_ERROR, message);
	}

	public LlmInvokeException(String message, Throwable cause) {
		super(ResultCode.LLM_INVOKE_ERROR, message);
		initCause(cause);
	}
}
