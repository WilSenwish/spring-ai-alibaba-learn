package com.le.agent.service.agent;

import com.le.agent.common.dto.AnswerItem;
import com.le.agent.common.dto.QuestionItem;
import com.le.agent.common.dto.ReviewItem;
import com.le.agent.common.enums.LevelEnum;
import com.le.agent.service.agent.prompt.SystemPrompts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 节点执行器 - 实现 9 个分级节点的核心调用.
 *
 * <p>每个方法封装一次 LLM 调用 + 解析；orchestrator 在 Graph 中按 level 路由到对应方法.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentNodes {

	private final LlmInvoker llmInvoker;

	// ================== Question 节点 ==================

	public QuestionItem doQuestionJunior(String topic) {
		return doQuestion(LevelEnum.JUNIOR, topic);
	}

	public QuestionItem doQuestionMid(String topic) {
		return doQuestion(LevelEnum.MID, topic);
	}

	public QuestionItem doQuestionSenior(String topic) {
		return doQuestion(LevelEnum.SENIOR, topic);
	}

	private QuestionItem doQuestion(LevelEnum level, String topic) {
		String prompt = String.format(SystemPrompts.questionPrompt(level), topic);
		Map<String, Object> map = llmInvoker.callJson("你是一位结构化输出的助手，请仅输出 JSON。", prompt);
		String content = stringOrDefault(map, "content", "请介绍一下 " + topic);
		@SuppressWarnings("unchecked")
		List<Object> rawTags = (List<Object>) map.getOrDefault("tags", Collections.emptyList());
		List<String> tags = rawTags.stream().map(String::valueOf).collect(Collectors.toList());
		return QuestionItem.builder()
				.content(content)
				.difficulty(level)
				.tags(tags)
				.build();
	}

	// ================== Answer 节点 ==================

	public AnswerItem doAnswerJunior(String question, String feedback, int revision) {
		return doAnswer(LevelEnum.JUNIOR, question, feedback, revision);
	}

	public AnswerItem doAnswerMid(String question, String feedback, int revision) {
		return doAnswer(LevelEnum.MID, question, feedback, revision);
	}

	public AnswerItem doAnswerSenior(String question, String feedback, int revision) {
		return doAnswer(LevelEnum.SENIOR, question, feedback, revision);
	}

	private AnswerItem doAnswer(LevelEnum level, String question, String feedback, int revision) {
		String prompt = String.format(
				SystemPrompts.answerPrompt(level),
				question,
				SystemPrompts.feedbackSection(feedback));
		Map<String, Object> map = llmInvoker.callJson("你是一位结构化输出的助手，请仅输出 JSON。", prompt);
		String content = stringOrDefault(map, "content", "（AI 未给出答案）");
		@SuppressWarnings("unchecked")
		List<Object> rawPoints = (List<Object>) map.getOrDefault("keyPoints", Collections.emptyList());
		List<String> keyPoints = rawPoints.stream().map(String::valueOf).collect(Collectors.toList());
		String sampleCode = stringOrDefault(map, "sampleCode", "");
		return AnswerItem.builder()
				.content(content)
				.keyPoints(keyPoints)
				.sampleCode(sampleCode)
				.revision(revision)
				.build();
	}

	// ================== Review 节点 ==================

	public ReviewItem doReviewJunior(String question, String answer) {
		return doReview(LevelEnum.JUNIOR, question, answer);
	}

	public ReviewItem doReviewMid(String question, String answer) {
		return doReview(LevelEnum.MID, question, answer);
	}

	public ReviewItem doReviewSenior(String question, String answer) {
		return doReview(LevelEnum.SENIOR, question, answer);
	}

	private ReviewItem doReview(LevelEnum level, String question, String answer) {
		String prompt = String.format(SystemPrompts.reviewPrompt(level), question, answer);
		Map<String, Object> map = llmInvoker.callJson("你是一位结构化输出的助手，请仅输出 JSON。", prompt);
		boolean approved = Boolean.TRUE.equals(map.get("approved"));
		String comments = stringOrDefault(map, "comments", "");
		String revisionHint = stringOrDefault(map, "revisionHint", "");
		return ReviewItem.builder()
				.approved(approved)
				.comments(comments)
				.revisionHint(revisionHint)
				.build();
	}

	private String stringOrDefault(Map<String, Object> map, String key, String def) {
		Object v = map.get(key);
		return v == null ? def : v.toString();
	}
}
