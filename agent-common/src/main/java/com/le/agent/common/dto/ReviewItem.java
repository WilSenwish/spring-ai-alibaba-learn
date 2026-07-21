package com.le.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 复核 DTO - 由 Review Agent 产出.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewItem implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 是否通过 */
	private Boolean approved;

	/** 复核评语 */
	private String comments;

	/** 修订建议（approved=false 时填写） */
	private String revisionHint;
}
