package com.le.agent.common.enums;

import lombok.Getter;

/**
 * Agent 流程节点枚举（用于日志埋点与可观测）.
 */
@Getter
public enum NodeEnum {

	INTAKE("INTAKE", "Intake 节点"),
	LEVEL_ROUTER("LEVEL_ROUTER", "Level Router 节点"),
	QUESTION_JUNIOR("QUESTION_JUNIOR", "Junior Question 节点"),
	QUESTION_MID("QUESTION_MID", "Mid Question 节点"),
	QUESTION_SENIOR("QUESTION_SENIOR", "Senior Question 节点"),
	ANSWER_JUNIOR("ANSWER_JUNIOR", "Junior Answer 节点"),
	ANSWER_MID("ANSWER_MID", "Mid Answer 节点"),
	ANSWER_SENIOR("ANSWER_SENIOR", "Senior Answer 节点"),
	REVIEW_JUNIOR("REVIEW_JUNIOR", "Junior Review 节点"),
	REVIEW_MID("REVIEW_MID", "Mid Review 节点"),
	REVIEW_SENIOR("REVIEW_SENIOR", "Senior Review 节点"),
	HUMAN_CHECKPOINT("HUMAN_CHECKPOINT", "人工确认节点"),
	FINALIZE("FINALIZE", "最终拼装节点");

	private final String code;
	private final String label;

	NodeEnum(String code, String label) {
		this.code = code;
		this.label = label;
	}
}
