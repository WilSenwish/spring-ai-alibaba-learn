package com.le.agent.service.tool;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮件发送工具 - 封装 JavaMailSender.
 *
 * <p>供 Finalize 节点或 Agent 自主调用发送邮件.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendTool {

	private final JavaMailSender mailSender;

	@Value("${agent.email.from:no-reply@example.com}")
	private String from;

	/**
	 * 发送邮件.
	 *
	 * @param request 邮件请求
	 * @return 发送结果
	 */
	@Tool(name = "send_email", description = "发送面试学习结果邮件到指定收件人")
	public EmailSendResult send(EmailSendRequest request) {
		if (request == null || !StringUtils.hasText(request.getTo())) {
			return EmailSendResult.fail("收件人不能为空");
		}
		log.info("[EmailSendTool] to={}, subject={}", request.getTo(), request.getSubject());
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(request.getTo());
			helper.setSubject(request.getSubject());
			helper.setText(request.getContent(), true);
			mailSender.send(message);
			log.info("[EmailSendTool] success, to={}", request.getTo());
			return EmailSendResult.ok("邮件发送成功");
		} catch (MessagingException e) {
			log.error("[EmailSendTool] fail: {}", e.getMessage(), e);
			return EmailSendResult.fail("邮件发送失败: " + e.getMessage());
		}
	}
}
