package com.le.agent.common.constants;

/**
 * Agent 相关常量 - 节点 ID 等.
 */
public final class AgentConstants {

	private AgentConstants() {
	}

	/** 节点 ID */
	public static final class NodeId {

		public static final String INTAKE = "intake";
		public static final String LEVEL_ROUTER = "level_router";

		public static final String QUESTION_JUNIOR = "question_junior";
		public static final String QUESTION_MID = "question_mid";
		public static final String QUESTION_SENIOR = "question_senior";

		public static final String ANSWER_JUNIOR = "answer_junior";
		public static final String ANSWER_MID = "answer_mid";
		public static final String ANSWER_SENIOR = "answer_senior";

		public static final String REVIEW_JUNIOR = "review_junior";
		public static final String REVIEW_MID = "review_mid";
		public static final String REVIEW_SENIOR = "review_senior";

		public static final String HUMAN_CHECKPOINT = "human_checkpoint";
		public static final String FINALIZE = "finalize";

		private NodeId() {
		}
	}
}
