package com.le.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 多 Agent 面试学习框架启动类.
 *
 * <p>启动入口位于 agent-controller 模块，扫描 service/common 包下所有组件.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.le.agent"})
@EnableAsync
public class AgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentApplication.class, args);
	}
}
