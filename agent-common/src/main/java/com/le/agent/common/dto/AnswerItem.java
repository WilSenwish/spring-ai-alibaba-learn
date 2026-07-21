package com.le.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 答案 DTO - 由 Answer Agent 产出.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerItem implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 答案正文（Markdown） */
	private String content;

	/** 关键要点 */
	private List<String> keyPoints;

	/** 示例代码块 */
	private String sampleCode;

	/** 修订轮次（第 1 次 = 0） */
	private Integer revision;
}
