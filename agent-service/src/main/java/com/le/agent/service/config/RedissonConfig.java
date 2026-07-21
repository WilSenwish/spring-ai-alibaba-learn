package com.le.agent.service.config;

import com.alibaba.cloud.ai.graph.store.stores.RedisStore;
import lombok.Getter;
import lombok.Setter;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置.
 *
 * <p>从 application.yml 读取 Redis 配置，创建 RedissonClient Bean 和 RedisStore Bean.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Getter
@Setter
public class RedissonConfig {

	private String host = "127.0.0.1";
	private int port = 6379;
	private String password = "";
	private int database = 0;

	@Bean
	public RedissonClient redissonClient() {
		Config config = new Config();
		String address = String.format("redis://%s:%d", host, port);
		config.useSingleServer()
				.setAddress(address)
				.setDatabase(database);
		if (!password.isEmpty()) {
			config.useSingleServer().setPassword(password);
		}
		return Redisson.create(config);
	}

	@Bean
	public RedisStore redisStore() {
		return new RedisStore();
	}
}
