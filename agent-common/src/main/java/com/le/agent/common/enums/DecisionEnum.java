package com.le.agent.common.enums;

import lombok.Getter;

/**
 * 人工决策枚举.
 */
@Getter
public enum DecisionEnum {

	/** 通过，进入 Finalize */
	APPROVE("APPROVE", "通过"),

	/** 修订，携带 feedback 回到 Answer 节点 */
	REVISE("REVISE", "修订");

	private final String code;
	private final String label;

	DecisionEnum(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public static DecisionEnum of(String code) {
		if (code == null) {
			return null;
		}
		for (DecisionEnum value : values()) {
			if (value.code.equalsIgnoreCase(code)) {
				return value;
			}
		}
		return null;
	}
}
