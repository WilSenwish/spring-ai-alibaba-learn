# 多 Agent 面试知识学习框架 - 使用文档

## 1. 环境要求

| 组件 | 版本要求 |
|------|---------|
| Java | 17+ |
| Redis | 6.0+ |
| Maven | 3.6+ |

## 2. 配置说明

### 2.1 application.yml 配置

```yaml
# LLM 配置（智谱AI）
spring:
  ai:
    dashscope:
      api-key: ${ZHIPU_API_KEY:your-api-key}
      chat:
        options:
          model: glm-4-plus

# Redis 配置
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}

# Agent 配置
agent:
  orchestrator:
    max-revise-count: 5      # 最大修订次数
  memory:
    history-prefix: agent:history:  # 历史记录 key 前缀
    max-history-size: 50     # 最大历史记录数
  search:
    engine: zhipu-search-pro  # 默认搜索引擎，可选：dashscope-web-search
    zhipu:
      count: 10               # 返回结果条数，范围 1-50
      content-size: medium    # 摘要长度：low/medium/high
      recency-filter: noLimit # 时间范围过滤

# 邮件配置（可选）
  mail:
    host: ${SMTP_HOST:smtp.example.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

### 2.2 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `ZHIPU_API_KEY` | 智谱AI API Key | 是 |
| `REDIS_HOST` | Redis 主机地址 | 是 |
| `REDIS_PORT` | Redis 端口 | 是 |
| `REDIS_PASSWORD` | Redis 密码 | 否 |
| `REDIS_DATABASE` | Redis 数据库索引 | 否 |
| `SMTP_HOST` | SMTP 服务器地址 | 否 |
| `SMTP_PORT` | SMTP 端口 | 否 |
| `SMTP_USERNAME` | SMTP 用户名 | 否 |
| `SMTP_PASSWORD` | SMTP 密码 | 否 |

## 3. 启动方式

### 3.1 本地开发环境

```bash
# 设置 Java 17
export JAVA_HOME=/path/to/java17

# 编译
mvn clean install -DskipTests

# 启动
java -jar agent-controller/target/agent-controller-0.0.1-SNAPSHOT.jar
```

### 3.2 Docker 部署

```bash
# 构建镜像
docker build -t interview-agent:latest .

# 运行容器
docker run -d \
  --name interview-agent \
  -p 8080:8080 \
  -e ZHIPU_API_KEY=your-api-key \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  interview-agent:latest
```

## 4. API 接口说明

### 4.1 启动新会话

**POST** `/api/v1/agent/start`

请求体：
```json
{
  "sessionId": "optional-session-id",
  "topic": "Java 并发编程",
  "level": "MID",
  "emailTo": "user@example.com",
  "emailRequired": false
}
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "session-uuid",
    "runId": "run-uuid",
    "question": "请解释 Java 中的 volatile 关键字...",
    "answer": "volatile 是 Java 中的轻量级同步机制...",
    "keyPoints": ["可见性", "禁止指令重排序", "不保证原子性"],
    "level": "MID",
    "revision": 0,
    "approved": false,
    "reviewComments": "答案基本正确，建议补充...",
    "revisionHint": "可以补充 volatile 与 synchronized 的对比",
    "awaitingHuman": true
  }
}
```

### 4.2 人工决策

**POST** `/api/v1/agent/human`

请求体：
```json
{
  "sessionId": "session-uuid",
  "runId": "run-uuid",
  "decision": "APPROVE",
  "feedback": ""
}
```

决策类型：
- `APPROVE`：通过，进入 FINALIZE
- `REVISE`：修订，重新生成答案

### 4.3 继续会话

**POST** `/api/v1/agent/continue`

请求体：
```json
{
  "sessionId": "session-uuid",
  "topic": "MySQL 索引优化"
}
```

### 4.4 查询历史

**GET** `/api/v1/agent/history/{sessionId}`

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "qa-uuid",
      "sessionId": "session-uuid",
      "topic": "Java 并发编程",
      "level": "MID",
      "question": "请解释 Java 中的 volatile 关键字...",
      "answer": "volatile 是 Java 中的轻量级同步机制...",
      "keyPoints": ["可见性", "禁止指令重排序"],
      "createdAt": "2026-07-21T10:00:00"
    }
  ]
}
```

### 4.5 流式启动

**POST** `/api/v1/agent/stream/start`

- Content-Type: `text/event-stream`
- 返回每个节点执行结果的实时推送

### 4.6 流式决策

**POST** `/api/v1/agent/stream/human`

- Content-Type: `text/event-stream`
- 返回人工决策后执行结果的实时推送

## 5. 使用示例

### 5.1 基本流程

```bash
# 1. 启动新会话
curl -X POST http://localhost:8080/api/v1/agent/start \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "Spring Boot 自动配置原理",
    "level": "SENIOR"
  }'

# 2. 查看返回结果，如果 awaitingHuman=true，进行人工决策
curl -X POST http://localhost:8080/api/v1/agent/human \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "returned-session-id",
    "runId": "returned-run-id",
    "decision": "APPROVE"
  }'

# 3. 查询历史
curl http://localhost:8080/api/v1/agent/history/returned-session-id
```

### 5.2 修订流程

```bash
# 1. 启动新会话
curl -X POST http://localhost:8080/api/v1/agent/start \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "Redis 分布式锁实现",
    "level": "MID"
  }'

# 2. 请求修订答案
curl -X POST http://localhost:8080/api/v1/agent/human \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "returned-session-id",
    "runId": "returned-run-id",
    "decision": "REVISE",
    "feedback": "请补充 Redlock 算法的详细说明"
  }'

# 3. 再次确认
curl -X POST http://localhost:8080/api/v1/agent/human \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "returned-session-id",
    "runId": "returned-run-id",
    "decision": "APPROVE"
  }'
```

### 5.3 流式调用

```bash
# 流式启动
curl -X POST http://localhost:8080/api/v1/agent/stream/start \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "topic": "Kafka 消息可靠性保证",
    "level": "SENIOR"
  }'

# 流式决策
curl -X POST http://localhost:8080/api/v1/agent/stream/human \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "sessionId": "session-id",
    "runId": "run-id",
    "decision": "APPROVE"
  }'
```

## 6. 日志说明

### 6.1 节点生命周期日志

```
[node-enter] traceId=xxx, sessionId=xxx, node=QUESTION_AGENT_MID
[node-exit] traceId=xxx, sessionId=xxx, node=QUESTION_AGENT_MID, costMs=1234, status=ok
```

### 6.2 模型调用日志

```
[model-call] traceId=xxx, sessionId=xxx, systemPrompt=500 chars, messages=1, tools=1
[model-response] traceId=xxx, sessionId=xxx, costMs=2345, responseType=AssistantMessage
```

### 6.3 工具调用日志

```
[tool-call] traceId=xxx, sessionId=xxx, toolName=webSearch, toolCallId=xxx, arguments=50 chars
[tool-response] traceId=xxx, sessionId=xxx, toolName=webSearch, toolCallId=xxx, costMs=500, status=success, isError=false
```

## 6. 搜索引擎说明

项目支持两种网络搜索引擎，可通过 `agent.search.engine` 配置切换：

| 引擎 | 配置值 | 说明 |
|------|--------|------|
| 智谱 search_pro | `zhipu-search-pro` | 默认，使用智谱官方 Web Search API，返回结构化结果（标题/链接/摘要/来源/发布日期） |
| DashScope Web Search | `dashscope-web-search` | 使用 DashScope completions 接口启用 web_search 工具，返回 LLM 总结内容 |

### 6.1 切换搜索引擎示例

```yaml
agent:
  search:
    engine: dashscope-web-search
```

### 6.2 智谱搜索高级配置

```yaml
agent:
  search:
    engine: zhipu-search-pro
    zhipu:
      count: 15
      content-size: high
      recency-filter: oneWeek
```

`recency-filter` 可选值：
- `noLimit`：不限时间
- `oneDay`：一天内
- `oneWeek`：一周内
- `oneMonth`：一月内
- `oneYear`：一年内

## 7. 故障排查

### 7.1 启动失败

- 检查 Java 版本是否为 17+
- 检查 Redis 是否正常运行
- 检查 `ZHIPU_API_KEY` 是否配置正确

### 7.2 会话状态丢失

- 检查 Redis 连接是否正常
- 检查 Redisson 配置是否正确
- 查看 `RedissonStore` 日志

### 7.3 LLM 调用失败

- 检查 API Key 是否有效
- 检查网络连接
- 查看 `ModelLoggingInterceptor` 日志