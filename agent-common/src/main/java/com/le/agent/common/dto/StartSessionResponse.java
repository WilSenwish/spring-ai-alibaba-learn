package com.le.agent.common.dto;

import com.le.agent.common.enums.EmailSendStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 启动会话响应 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartSessionResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 会话 ID */
	private String sessionId;

	/** Graph 运行 ID（用于后续 human 决策） */
	private String runId;

	/** 当前生成的问题 */
	private String question;

	/** 当前生成的答案 */
	private String answer;

	/** 关键要点 */
	private List<String> keyPoints;

	/** 难度等级 */
	private String level;

	/** 修订轮次 */
	private Integer revision;

	/** 是否已通过（true = 进入 Finalize；false = 等待人工决策） */
	private Boolean approved;

	/** 复核评语 */
	private String reviewComments;

	/** 修订建议 */
	private String revisionHint;

	/** 是否需要人工确认（=true 时调用 /human 接口） */
	private Boolean awaitingHuman;

	/** 邮件发送状态 */
	private EmailSendStatusEnum emailStatus;
}
