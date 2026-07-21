package com.le.agent.service.email;

import com.le.agent.common.dto.QaContent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 邮件发送监听器.
 *
 * <p>异步消费 EmailSendEvent，调用 JavaMailSender 发送.
 * 未来若引入 RocketMQ，可在此处替换为 {@code @RocketMQMessageListener} 注解，行为保持一致.</p>
 */
@Slf4j
@Component
public class EmailSendListener {

	private final JavaMailSender mailSender;
	private final String from;
	private final String subjectPrefix;

	public EmailSendListener(JavaMailSender mailSender,
							@Value("${agent.email.from:no-reply@example.com}") String from,
							@Value("${agent.email.subject-prefix:[面试学习]}") String subjectPrefix) {
		this.mailSender = mailSender;
		this.from = from;
		this.subjectPrefix = subjectPrefix;
	}

	@Async
	@EventListener
	public void onEvent(EmailSendEvent event) {
		QaContent qa = event.getQaContent();
		String to = StringUtils.hasText(event.getEmailTo()) ? event.getEmailTo() : qa.getSessionId();
		log.info("[EmailSendListener] start send, traceId={}, sessionId={}, to={}",
				event.getTraceId(), event.getSessionId(), to);
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject(subjectPrefix + " " + qa.getTopic());
			helper.setText(buildHtml(qa), true);
			// TODO log or write2file
			mailSender.send(message);
			log.info("[EmailSendListener] send success, sessionId={}, to={}", event.getSessionId(), to);
		} catch (MessagingException ex) {
			log.error("[EmailSendListener] send fail, sessionId={}, to={}, err={}",
					event.getSessionId(), to, ex.getMessage(), ex);
		} catch (Exception ex) {
			log.error("[EmailSendListener] unexpected error, sessionId={}, err={}",
					event.getSessionId(), ex.getMessage(), ex);
		}
	}

	private String buildHtml(QaContent qa) {
		StringBuilder sb = new StringBuilder();
		sb.append("<h2>").append(qa.getTopic()).append("</h2>");
		sb.append("<p><b>难度：</b>").append(qa.getLevel()).append("</p>");
		sb.append("<h3>问题</h3><p>").append(safe(qa.getQuestion())).append("</p>");
		sb.append("<h3>答案</h3><pre style=\"white-space:pre-wrap;\">")
				.append(safe(qa.getAnswer()))
				.append("</pre>");
		if (qa.getKeyPoints() != null && !qa.getKeyPoints().isEmpty()) {
			sb.append("<h3>关键要点</h3><ul>");
			for (String point : qa.getKeyPoints()) {
				sb.append("<li>").append(safe(point)).append("</li>");
			}
			sb.append("</ul>");
		}
		if (StringUtils.hasText(qa.getSampleCode())) {
			sb.append("<h3>示例代码</h3><pre><code>")
					.append(safe(qa.getSampleCode()))
					.append("</code></pre>");
		}
		return sb.toString();
	}

	private String safe(String s) {
		return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
	}
}
