package com.le.agent.common.dto;

import com.le.agent.common.enums.LevelEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 启动会话请求 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartSessionRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 会话 ID（业务侧传入；为空时由系统生成） */
	private String sessionId;

	/** 主题或问题（必填） */
	private String topic;

	/** 难度等级（可选；缺省 MID） */
	private LevelEnum level;

	/** 邮件收件人 */
	private String emailTo;

	/** 是否需要邮件发送 */
	private Boolean emailRequired;
}
