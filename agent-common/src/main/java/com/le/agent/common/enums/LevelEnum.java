package com.le.agent.common.enums;

import lombok.Getter;

/**
 * 难度等级枚举.
 *
 * <p>Level Router 节点会选定一个 level 标签，贯穿整条 Question/Answer/Review 链路.</p>
 */
@Getter
public enum LevelEnum {

	/** 初级 */
	JUNIOR("JUNIOR", "初级", "基础概念、定义、入门示例"),

	/** 中级 */
	MID("MID", "中级", "原理、对比、常见坑、源码片段"),

	/** 高级 */
	SENIOR("SENIOR", "高级", "架构设计、底层源码、极限场景、分布式权衡");

	private final String code;
	private final String label;
	private final String description;

	LevelEnum(String code, String label, String description) {
		this.code = code;
		this.label = label;
		this.description = description;
	}

	public static LevelEnum of(String code) {
		if (code == null) {
			return null;
		}
		for (LevelEnum value : values()) {
			if (value.code.equalsIgnoreCase(code)) {
				return value;
			}
		}
		return null;
	}
}
