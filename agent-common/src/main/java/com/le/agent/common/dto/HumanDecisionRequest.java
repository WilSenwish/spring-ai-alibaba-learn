package com.le.agent.common.dto;

import com.le.agent.common.enums.DecisionEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 人工决策请求 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanDecisionRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 会话 ID */
	private String sessionId;

	/** Graph 运行 ID（由 start 接口返回） */
	private String runId;

	/** 决策：APPROVE/REVISE */
	private DecisionEnum decision;

	/** 修订反馈（REVISE 时必填） */
	private String feedback;
}
