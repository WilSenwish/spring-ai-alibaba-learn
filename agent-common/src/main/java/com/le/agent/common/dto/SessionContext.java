package com.le.agent.common.dto;

import com.le.agent.common.enums.EmailSendStatusEnum;
import com.le.agent.common.enums.LevelEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话上下文 DTO.
 *
 * <p>贯穿整个 Graph 流程的会话状态载体，按 sessionId 隔离.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 会话 ID（业务侧传入） */
	private String sessionId;

	/** 链路追踪 ID（系统侧生成） */
	private String traceId;

	/** 用户输入的原始文本（主题或问题） */
	private String rawInput;

	/** 抽取后的主题 */
	private String topic;

	/** 难度等级 */
	private LevelEnum level;

	/** 修订累计次数 */
	private Integer reviseCount;

	/** 邮件相关 */
	private String emailTo;
	private Boolean emailRequired;

	/** 邮件发送状态 */
	private EmailSendStatusEnum emailStatus;

	/** Graph 唯一运行标识（用于 Human Checkpoint 恢复） */
	private String runId;

	/** 流程状态：RUNNING / AWAITING_HUMAN / COMPLETED */
	private String flowStatus;

	/** 当前问题 */
	private QuestionItem question;

	/** 当前答案 */
	private AnswerItem answer;

	/** 复核结果 */
	private ReviewItem review;

	/** 当前反馈（修订时填写） */
	private String feedback;

	/** 最终 Q&A（Finalize 节点产出） */
	private QaContent finalQa;
}
