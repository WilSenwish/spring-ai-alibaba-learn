package com.le.agent.common.dto;

import com.le.agent.common.enums.LevelEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 问题 DTO - 由 Question Agent 产出.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionItem implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 问题正文 */
	private String content;

	/** 难度等级（=当前 level） */
	private LevelEnum difficulty;

	/** 标签（如 "持久化"、"高并发"） */
	private List<String> tags;
}
