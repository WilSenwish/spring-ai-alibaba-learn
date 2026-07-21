package com.le.agent.common.enums;

import lombok.Getter;

/**
 * 邮件发送状态.
 */
@Getter
public enum EmailSendStatusEnum {

	PENDING("PENDING", "待发送"),
	SUCCESS("SUCCESS", "发送成功"),
	FAILED("FAILED", "发送失败");

	private final String code;
	private final String label;

	EmailSendStatusEnum(String code, String label) {
		this.code = code;
		this.label = label;
	}
}
