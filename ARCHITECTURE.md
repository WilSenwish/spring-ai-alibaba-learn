# 多 Agent 面试知识学习框架 - 架构说明

## 1. 项目概述

本项目基于 **Spring AI Alibaba 1.1.2** 框架构建多 Agent 面试知识学习系统，支持分级出题、答题、复核、人工确认等完整流程，并提供可选的邮件发送功能。

## 2. 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户请求层                                       │
│  ┌─────────────────────┐    ┌─────────────────────┐                         │
│  │   REST API          │    │   SSE Stream        │                         │
│  │  /api/v1/agent/*    │    │  /stream/start      │                         │
│  └─────────────────────┘    └─────────────────────┘                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Controller 层                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  AgentController                                                     │    │
│  │  - start()        启动新会话                                         │    │
│  │  - human()        人工决策                                           │    │
│  │  - continue()     继续会话                                           │    │
│  │  - history()      查询历史                                           │    │
│  │  - streamStart()  流式启动                                           │    │
│  │  - streamHuman()  流式决策                                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Service 层                                         │
│  ┌───────────────────────┐    ┌───────────────────────┐                      │
│  │  GraphExecutorService │    │  SessionHistoryService│                      │
│  │  - 驱动 Graph 执行    │    │  - 会话历史管理       │                      │
│  │  - 状态持久化         │    │  - Q&A 记录           │                      │
│  └───────────────────────┘    └───────────────────────┘                      │
│  ┌───────────────────────┐    ┌───────────────────────┐                      │
│  │  InterviewGraphBuilder│    │  AgentNodes           │                      │
│  │  - StateGraph 编排    │    │  - ReactAgent 构建    │                      │
│  │  - 节点定义           │    │  - Hook/Interceptor   │                      │
│  └───────────────────────┘    └───────────────────────┘                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Agent Framework 层                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  StateGraph (Graph 编排)                                             │    │
│  │  ┌─────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │    │
│  │  │  INTAKE │→ │ QUESTION_   │→ │ ANSWER_     │→ │ REVIEW_     │      │    │
│  │  │         │  │ AGENT       │  │ AGENT       │  │ AGENT       │      │    │
│  │  └─────────┘  └─────────────┘  └─────────────┘  └─────────────┘      │    │
│  │        ↓                                                               │    │
│  │  ┌─────────────────┐  ┌─────────────┐  ┌─────────────┐               │    │
│  │  │ HUMAN_CHECKPOINT│→ │   FINALIZE  │→ │     END     │               │    │
│  │  └─────────────────┘  └─────────────┘  └─────────────┘               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  ReactAgent (智能体节点)                                              │    │
│  │  - QUESTION_AGENT_JUNIOR/MID/SENIOR                                  │    │
│  │  - ANSWER_AGENT_JUNIOR/MID/SENIOR                                    │    │
│  │  - REVIEW_AGENT_JUNIOR/MID/SENIOR                                    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          基础设施层                                           │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐        │
│  │  RedisSaver       │  │  RedissonStore    │  │  SessionHistory   │        │
│  │  (Checkpoint)     │  │  (State Store)    │  │  (Q&A History)    │        │
│  │  持久化到 Redis   │  │  持久化到 Redis   │  │  持久化到 Redis   │        │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘        │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐        │
│  │  LoggerHook       │  │  ModelInterceptor │  │  ToolInterceptor  │        │
│  │  (生命周期日志)   │  │  (模型调用拦截)   │  │  (工具调用拦截)   │        │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘        │
│  ┌───────────────────┐  ┌───────────────────┐                               │
│  │  WebSearchTool    │  │  EmailSendTool    │                               │
│  │  (DashScope搜索)  │  │  (邮件发送)       │                               │
│  └───────────────────┘  └───────────────────┘                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           外部服务                                            │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐        │
│  │  Redis            │  │  DashScope LLM    │  │  SMTP Server      │        │
│  │  (状态存储)       │  │  (智谱AI)         │  │  (邮件发送)       │        │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. 模块职责说明

### 3.1 agent-common
公共模块，包含：
- DTO 定义：`StartSessionRequest`、`StartSessionResponse`、`QaContent` 等
- 枚举定义：`LevelEnum`（初级/中级/高级）、`DecisionEnum`（通过/修订）
- 异常定义：`BizException`、`ResultCode`
- 工具类：`TraceContext`（分布式追踪）、`JsonUtils`

### 3.2 agent-service
业务逻辑模块，包含：
- Graph 编排：`InterviewGraphBuilder`、`GraphExecutorService`
- Agent 节点：`AgentNodes`、`LlmInvoker`
- 可观测性：`LoggerHook`、`ModelLoggingInterceptor`、`ToolLoggingInterceptor`
- 存储：`RedissonStore`（自定义生产级 Store）
- 工具：`WebSearchTool`、`EmailSendTool`

### 3.3 agent-controller
接口模块，包含：
- REST API：`AgentController`
- SSE 流式接口：`/stream/start`、`/stream/human`

## 4. 技术选型说明

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Spring Boot | 3.5.16 | 基础框架 |
| Agent | Spring AI Alibaba | 1.1.2.0 | Agent Framework + Graph Core |
| LLM | DashScope (智谱AI) | - | 默认 LLM 提供商 |
| Redis | Redisson | 3.22.0 | 分布式 Redis 客户端 |
| 构建 | Maven | - | 多模块项目 |

## 5. 核心组件说明

### 5.1 StateGraph & CompiledGraph
- `StateGraph`：定义节点和边的有向图
- `CompiledGraph`：编译后的可执行图，支持 `invoke()` 和 `stream()` 方法

### 5.2 ReactAgent
- 智能体节点，支持 Tool Calling
- 通过 `Builder` 配置名称、系统提示、工具、Hook、Interceptor

### 5.3 RedisSaver
- Checkpoint 持久化到 Redis
- 支持 Graph 中断和恢复

### 5.4 RedissonStore（自定义）
- 生产级 Store 实现
- 使用 Redisson 的 `RBucket` 持久化会话状态
- Key 格式：`agent:store:{namespace}:{itemId}`

### 5.5 Hook 机制
- `LoggerHook`：在 `BEFORE_AGENT` 和 `AFTER_AGENT` 位置输出生命周期日志

### 5.6 Interceptor 机制
- `ModelLoggingInterceptor`：拦截 LLM 调用，记录日志和耗时
- `ToolLoggingInterceptor`：拦截工具调用，记录日志和耗时

## 6. 数据流向说明

```
1. 用户输入主题 → POST /api/v1/agent/start
2. GraphExecutorService.start() 驱动 Graph 执行
3. INTAKE 节点校验输入，初始化状态
4. 路由到对应级别的 QUESTION_AGENT 生成面试题
5. 路由到对应级别的 ANSWER_AGENT 生成答案（可能调用 WebSearchTool）
6. 路由到对应级别的 REVIEW_AGENT 复核答案
7. HUMAN_CHECKPOINT 节点等待人工确认（Graph 中断）
8. 用户确认 → POST /api/v1/agent/human
9. GraphExecutorService.human() 恢复 Graph 执行
10. FINALIZE 节点构造最终 Q&A 并持久化
11. 返回结果给用户
```

## 7. 状态管理

- `OverAllState`：Graph 运行时状态
- `RedisSaver`：Checkpoint 持久化（用于 Graph 中断/恢复）
- `RedissonStore`：会话快照存储
- `SessionHistoryService`：Q&A 历史记录