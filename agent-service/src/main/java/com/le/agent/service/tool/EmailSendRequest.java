package com.le.agent.service.tool;

import lombok.Data;

/**
 * 邮件发送请求 DTO.
 */
@Data
public class EmailSendRequest {

	/**
	 * 收件人邮箱.
	 */
	private String to;

	/**
	 * 邮件主题.
	 */
	private String subject;

	/**
	 * 邮件内容（HTML）.
	 */
	private String content;
}
