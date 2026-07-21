package com.le.agent.service.tool;

import lombok.Data;

/**
 * 邮件发送结果 DTO.
 */
@Data
public class EmailSendResult {

	private boolean success;
	private String message;

	public static EmailSendResult ok(String message) {
		EmailSendResult r = new EmailSendResult();
		r.setSuccess(true);
		r.setMessage(message);
		return r;
	}

	public static EmailSendResult fail(String message) {
		EmailSendResult r = new EmailSendResult();
		r.setSuccess(false);
		r.setMessage(message);
		return r;
	}
}
