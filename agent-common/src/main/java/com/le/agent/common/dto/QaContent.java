package com.le.agent.common.dto;

import com.le.agent.common.enums.LevelEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 最终问答内容 DTO - 由 Finalize 节点产出.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaContent implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 唯一标识 */
	private String id;

	/** 会话 ID */
	private String sessionId;

	/** 主题 */
	private String topic;

	/** 难度等级 */
	private LevelEnum level;

	/** 问题 */
	private String question;

	/** 答案（Markdown） */
	private String answer;

	/** 关键要点 */
	private java.util.List<String> keyPoints;

	/** 示例代码 */
	private String sampleCode;

	/** 修订轮次 */
	private Integer revision;

	/** 生成时间 */
	private LocalDateTime createdAt;
}
