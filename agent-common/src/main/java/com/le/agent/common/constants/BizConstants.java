package com.le.agent.common.constants;

/**
 * 业务侧常量.
 */
public final class BizConstants {

	private BizConstants() {
	}

	/** Graph OverAllState 中的 key 常量 */
	public static final class StateKeys {

		/** 会话上下文 */
		public static final String SESSION = "session";

		/** 问题 */
		public static final String QUESTION = "question";

		/** 答案 */
		public static final String ANSWER = "answer";

		/** 答案关键点 */
		public static final String KEY_POINTS = "keyPoints";

		/** 示例代码 */
		public static final String SAMPLE_CODE = "sampleCode";

		/** 复核结论 */
		public static final String REVIEW = "review";

		/** 复核是否通过 */
		public static final String APPROVED = "approved";

		/** 修订反馈 */
		public static final String FEEDBACK = "feedback";

		/** 修订建议 */
		public static final String REVISION_HINT = "revisionHint";

		/** 修订轮次 */
		public static final String REVISION = "revision";

		/** 最终 Q&A */
		public static final String QA_CONTENT = "qaContent";

		/** 流程结果（运行中/挂起/完成） */
		public static final String FLOW_STATUS = "flowStatus";

		private StateKeys() {
		}
	}

	/** 流程状态 */
	public static final class FlowStatus {

		/** 运行中 */
		public static final String RUNNING = "RUNNING";

		/** 等待人工确认 */
		public static final String AWAITING_HUMAN = "AWAITING_HUMAN";

		/** 完成 */
		public static final String COMPLETED = "COMPLETED";

		private FlowStatus() {
		}
	}

	/** 默认修订上限 */
	public static final int DEFAULT_MAX_REVISE = 5;

	/** 默认重试次数 */
	public static final int DEFAULT_RETRY_TIMES = 3;
}
