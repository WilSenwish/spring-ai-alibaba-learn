package com.le.agent.common.api;

/**
 * 业务码枚举.
 *
 * <p>采用 HTTP 风格 + 自定义业务码混合编码：4xxx 为业务异常，5xxx 为系统异常.</p>
 */
public enum ResultCode {

	/** 成功 */
	SUCCESS(0, "success"),

	/** 通用业务异常 */
	BAD_REQUEST(4000, "请求参数不合法"),

	/** 入参为空（Intake 校验） */
	EMPTY_INPUT(4001, "用户输入不能为空"),

	/** 超过最大修订次数 */
	MAX_REVISE_EXCEEDED(4002, "已超过最大修订次数，请人工接管"),

	/** 会话不存在 */
	SESSION_NOT_FOUND(4003, "会话不存在"),

	/** 节点流转异常 */
	GRAPH_INVOKE_ERROR(4004, "Agent 图执行异常"),

	/** LLM 调用异常 */
	LLM_INVOKE_ERROR(5001, "大模型调用失败"),

	/** 邮件发送异常 */
	MAIL_SEND_ERROR(5002, "邮件发送失败"),

	/** 系统异常 */
	INTERNAL_ERROR(5999, "系统繁忙，请稍后再试");

	private final int code;
	private final String message;

	ResultCode(int code, String message) {
		this.code = code;
		this.message = message;
	}

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}
}
