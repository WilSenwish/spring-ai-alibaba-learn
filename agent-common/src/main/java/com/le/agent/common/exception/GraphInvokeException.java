package com.le.agent.common.exception;

import com.le.agent.common.api.ResultCode;

/**
 * Graph 编排相关异常.
 */
public class GraphInvokeException extends BizException {

	public GraphInvokeException(String message) {
		super(ResultCode.GRAPH_INVOKE_ERROR, message);
	}

	public GraphInvokeException(String message, Throwable cause) {
		super(ResultCode.GRAPH_INVOKE_ERROR, message);
		initCause(cause);
	}
}
