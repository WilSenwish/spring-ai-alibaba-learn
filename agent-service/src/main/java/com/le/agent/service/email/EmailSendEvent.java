package com.le.agent.service.email;

import com.le.agent.common.dto.QaContent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 邮件发送事件.
 *
 * <p>由业务层在人工通过 + emailRequired=true 时发布，由 EmailSendListener 异步消费.
 * 当前通过 Spring 事件总线传递，未来可无缝替换为 RocketMQ 等 MQ.</p>
 */
@Getter
public class EmailSendEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private final String sessionId;
	private final String traceId;
	private final QaContent qaContent;
	private final String emailTo;

	public EmailSendEvent(Object source, String sessionId, String traceId, QaContent qaContent, String emailTo) {
		super(source);
		this.sessionId = sessionId;
		this.traceId = traceId;
		this.qaContent = qaContent;
		this.emailTo = emailTo;
	}
}
