package com.le.agent.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Jedis 连接池配置.
 */
@Slf4j
@Configuration
public class JedisConfig {

	@Value("${spring.data.redis.host:127.0.0.1}")
	private String host;

	@Value("${spring.data.redis.port:6379}")
	private int port;

	@Value("${spring.data.redis.password:}")
	private String password;

	@Value("${spring.data.redis.database:0}")
	private int database;

	@Value("${spring.data.redis.timeout:5s}")
	private java.time.Duration timeout;

	@Bean(destroyMethod = "close")
	public JedisPool jedisPool() {
		JedisPoolConfig config = new JedisPoolConfig();
		config.setMaxTotal(64);
		config.setMaxIdle(16);
		config.setMinIdle(4);
		config.setTestOnBorrow(true);
		if (password == null || password.isBlank()) {
			return new JedisPool(config, host, port, (int) timeout.toMillis(), null, database);
		}
		return new JedisPool(config, host, port, (int) timeout.toMillis(), password, database);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
