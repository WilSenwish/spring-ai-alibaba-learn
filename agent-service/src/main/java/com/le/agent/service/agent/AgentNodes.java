package com.le.agent.service.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.le.agent.common.enums.LevelEnum;
import com.le.agent.service.agent.prompt.SystemPrompts;
import com.le.agent.service.observability.LoggerHook;
import com.le.agent.service.observability.ModelLoggingInterceptor;
import com.le.agent.service.observability.ToolLoggingInterceptor;
import com.le.agent.service.tool.WebSearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Agent 节点工厂 - 提供 ReactAgent 构建方法.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentNodes {

	private final ChatClient.Builder chatClientBuilder;
	private final WebSearchTool webSearchTool;

	public ReactAgent buildQuestionAgent(LevelEnum level, String topic) {
		return ReactAgent.builder()
				.name("QUESTION_AGENT_" + level.name())
				.systemPrompt(String.format(SystemPrompts.questionPrompt(level), topic))
				.chatClient(chatClientBuilder.build())
				.outputKey("question")
				.hooks(Arrays.asList(new LoggerHook()))
				.interceptors(Arrays.asList(new ModelLoggingInterceptor()))
				.build();
	}

	public ReactAgent buildAnswerAgent(LevelEnum level, String question, String feedback) {
		return ReactAgent.builder()
				.name("ANSWER_AGENT_" + level.name())
				.systemPrompt(String.format(SystemPrompts.answerPrompt(level), question, SystemPrompts.feedbackSection(feedback)))
				.chatClient(chatClientBuilder.build())
				.methodTools(webSearchTool)
				.outputKey("answer")
				.hooks(Arrays.asList(new LoggerHook()))
				.interceptors(Arrays.asList(new ModelLoggingInterceptor(), new ToolLoggingInterceptor()))
				.build();
	}

	public ReactAgent buildReviewAgent(LevelEnum level, String question, String answer) {
		return ReactAgent.builder()
				.name("REVIEW_AGENT_" + level.name())
				.systemPrompt(String.format(SystemPrompts.reviewPrompt(level), question, answer))
				.chatClient(chatClientBuilder.build())
				.outputKey("review")
				.hooks(Arrays.asList(new LoggerHook()))
				.interceptors(Arrays.asList(new ModelLoggingInterceptor()))
				.build();
	}
}
