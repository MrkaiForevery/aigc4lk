# aigc4lk 架构设计说明书

> 文档版本: v1.0
> 生成日期: 2026-07-02
> 项目版本: 0.0.1-SNAPSHOT
> 基础技术栈: Java 17 / Spring Boot 3.5.0 / Spring Cloud Alibaba 2025.0.0.0 / Spring AI 1.1.2 / Spring AI Alibaba 1.1.2.2 / Nacos 3.2 / Seata 2.x / PostgreSQL / Redis / ChromaDB / Resilience4j

---

## 1. 项目概述

**aigc4lk** 是一个企业级多 Agent 编排平台，采用 "Commander（编排中心） + Sub-Agents（子 Agent 集群） + Shared Memory（共享记忆）" 的三层架构。平台通过 `CommanderAgent` 枢纽协调由 LLM 驱动的子 Agent，实现完整的意图识别 → 计划生成 → 计划校验 → 多模式执行 → 质量评估 → 记忆自进化的闭环。

### 1.1 设计目标

| 目标 | 说明 |
|---|---|
| **混合编排** | 支持预定义模板（已知场景）与 LLM 动态生成（未知场景）两种计划模式 |
| **多执行模式** | 顺序、并行、条件分支、循环纠正、竞争选拔共 5 种 |
| **弹性与容错** | Resilience4j 全链路防护（断路器 + 限流 + 隔离舱 + 超时），支持降级 |
| **分布式事务** | Seata AT 全局事务包裹整个编排流程，支持挂起/恢复/回滚 |
| **分层记忆** | 短期（Redis）、长期（PostgreSQL）、向量（ChromaDB）三层 |
| **自进化闭环** | 高质量执行案例存回 Chroma，供意图识别与候选计划 few-shot 检索 |
| **多模态融合** | 文本/视觉/语音/视频四模态跨平台输入 |

### 1.2 技术选型

| 领域 | 选型 |
|---|---|
| 基础框架 | Spring Boot 3.5.0 |
| 微服务 | Spring Cloud Alibaba 2025.0.0.0 |
| AI 框架 | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.2 |
| 注册/配置/发现 | Nacos 3.2 |
| 分布式事务 | Seata 2.x (AT 模式) |
| 长期存储 | PostgreSQL + MyBatis-Plus 3.5.12 |
| 短期缓存 | Redis + Redisson 3.47.0 |
| 向量数据库 | ChromaDB |
| 弹性组件 | Resilience4j |
| 服务调用 | OpenFeign |
| MCP 客户端 | Spring AI MCP Client (WebFlux) |
| 构建工具 | Maven 3.x (Java 17) |

---

## 2. 模块拓扑

### 2.1 模块依赖图

```
                        ┌──────────────────────────┐
                        │       Nacos 3.2          │
                        │  命名空间: 0f18537a-...  │
                        └───────────┬──────────────┘
                  ┌─────────────────┼─────────────────┐
                  │ Discovery       │ Config          │ A2A Registry
                  ▼                 ▼                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   commanderAgent (port 10010)                         │
│  HybridOrchestratorManager → IntentClassifier → CompetitionEngine     │
│  GraphExecutorEngine → StepUnitExecutor → BaseNacosA2ARouter          │
│  MemoryContextBuilder / MemoryUpdatePipeline / QualityAssessor        │
└───────┬────────────────────────────┬───────────────────────┬────────┘
        │ Feign                     │ Feign                 │ A2A/HTTP
        │                          │                       │
        ▼                          ▼                       ▼
┌──────────────────────┐  ┌────────────────────────────────────────────┐
│ sharedMemoryServices │  │         Sub-Agents (5 个)                  │
│ (port 10086)         │  ├────────────────────────────────────────────┤
│ PostgreSQL + Redis   │  │ documentAgent (12100)                      │
│ + ChromaDB           │  │ dataAnalysisAgent (12200)                  │
│                      │  │ codeReviewAgent (12300)                    │
│ 短期: Redis          │  │ customerServiceAgent (12400)               │
│ 长期: PostgreSQL     │  │ imageAnalysisAgent (12410)                 │
│ 向量: ChromaDB       │  └────────────────────────────────────────────┘
└──────────────────────┘
        ▲
        │ 公共依赖
┌───────┴─────────────────────────────────┐
│  platformCommon │ sharedMemoryServiceApi │
└─────────────────────────────────────────┘
```

### 2.2 模块清单

| 模块 | 类型 | 端口 | 职责 |
|---|---|---|---|
| `platformCommon` | 基础库（无 main） | — | A2A 协议、Agent 基类、多模态、MCP 抽象、模型路由 |
| `sharedMemoryServiceApi` | API 契约库（无 main） | — | 6 个 FeignClient 接口 + DTO |
| `commanderAgent` | Spring Boot 应用 | 10010 | 编排中心（意图、计划、执行、恢复、质量评估） |
| `sharedMemoryServices` | Spring Boot 应用 | 10086 | 记忆持久化（PostgreSQL/Redis/Chroma） |
| `documentAgent` | Spring Boot 应用 | 12100 | 文档处理子 Agent |
| `dataAnalysisAgent` | Spring Boot 应用 | 12200 | 数据分析子 Agent |
| `codeReviewAgent` | Spring Boot 应用 | 12300 | 代码审查子 Agent |
| `customerServiceAgent` | Spring Boot 应用 | 12400 | 客服子 Agent |
| `imageAnalysisAgent` | Spring Boot 应用 | 12410 | 图像分析子 Agent |

### 2.3 依赖矩阵

| 消费者 → 提供者 | platformCommon | sharedMemoryServiceApi | commanderAgent | sharedMemoryServices |
|---|---|---|---|---|
| commanderAgent | ✅ | ✅ | — | (via Feign) |
| sharedMemoryServices | ✅ | ✅ | — | — |
| documentAgent | ✅ | ✅ | — | (via Feign) |
| dataAnalysisAgent | ✅ | ✅ | — | (via Feign) |
| codeReviewAgent | ✅ | ✅ | — | (via Feign) |
| customerServiceAgent | ✅ | ✅ | — | (via Feign) |
| imageAnalysisAgent | ✅ | ✅ | — | (via Feign) |

> 除 `platformCommon` 和 `sharedMemoryServiceApi` 两个基础库外，**其余所有可执行模块均依赖这两个库**。

---

## 3. 核心架构：Commander（编排中心）

### 3.1 核心流程

```
用户请求 (HTTP/JSON)
   │
   ▼
┌─────────────────────────────────────────────────────────────────┐
│                   OrchestrationController                       │
│   POST /execute          POST /resume          GET /...         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │  HybridOrchestratorManager   │ ◀ @GlobalTransactional
              │  (编排总入口，30分钟事务)     │
              └──┬─────────┬─────────┬───────┘
                 │         │         │
      ┌──────────▼──┐  ┌───▼────┐  ┌─▼──────────────┐
      │MemoryContext│  │Intent  │  │ Plan           │
      │Builder      │  │Classif.│  │ Generation     │
      │(记忆上下文) │  │(意图)  │  │(模板|竞争)     │
      └─────────────┘  └────────┘  └──┬─────────────┘
                                      │
                           ┌──────────▼──────────┐
                           │  GraphExecutorEngine │
                           │  (按 ExecutionMode   │
                           │   路由到 5 种执行器)  │
                           └──┬─────┬─────┬──────┘
                              │     │     │
               ┌──────────────▼┐ ┌──▼───┐ ┌▼────────────────┐
               │SequentialExec.│ │Par.  │ │Cond/Iter/Comp   │
               │ParallelExec.  │ │Exec. │ │三种高级模式     │
               └──────┬────────┘ └──────┘ └────────┬────────┘
                      │                            │
                      └────────────┬───────────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │ StepUnitExecutor    │
                        │ (原子步骤执行)      │
                        │ A2A/LLM_CALL/INTERRUPT│
                        └─────────┬───────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
                    ▼             ▼             ▼
            BaseNacosA2ARouter  ChatClient   InterruptHandler
            (调用子Agent)        (LLM调用)    (中断/恢复/回滚)
                                   │
                                   ▼
                         ┌──────────────────────┐
                         │ QualityAssessor      │
                         │ (规则评分+LLM评分)   │
                         │ 高质量案例→Chroma    │
                         └──────────────────────┘
```

### 3.2 核心组件详表

| 组件类别 | 类名 | 路径 | 职责 |
|---|---|---|---|
| **总控** | `HybridOrchestratorManager` | `commander/...` | 编排总入口，`@GlobalTransactional(timeoutMills=1800000)` |
| **意图** | `IntentClassifier` | `intent/` | 通过硬规则/LLM/动态模式分类场景、复杂度、风险 |
| **竞争编排** | `CompetitionOrchestratorEngine` | `orchestrator/compet/` | 多 LLM 候选生成 + 互评估 |
| **候选生成** | `CandidateGenerator` | `orchestrator/compet/` | 调用不同 LLM 生成候选 `OrchestrationPlan` |
| **候选评估** | `PlanEvaluator` | `orchestrator/compet/` | LLM 评估候选方案得分 |
| **动态编排** | `DynamicOrchestrator` | `orchestrator/dynamic/` | 单一 LLM 生成动态计划 |
| **模板编排** | `TemplatePlanGenerator` | `orchestrator/template/` | 从 Nacos `graph-templates` 加载模板 |
| **计划校验** | `PlanValidator` | `validator/` | 拓扑、Agent 白名单、变量引用检查 |
| **图执行** | `GraphExecutorEngine` | `graph/` | 按 `ExecutionMode` 路由 |
| **步骤执行** | `StepUnitExecutor` | `graph/` | 原子执行单元，分发 A2A/LLM/INTERRUPT |
| **A2A 路由** | `BaseNacosA2ARouter` | `a2a/` | 通过 Nacos Discovery 发现 Agent 并 JSON-RPC 调用 |
| **记忆上下文** | `MemoryContextBuilder` | `memory/` | 构建用户画像/会话/案例记忆 |
| **记忆更新** | `MemoryUpdatePipeline` | `memory/` | 异步持久化会话历史 |
| **案例库** | `CaseLibraryClient` | `memory/` | 与 Chroma 交互存取案例 |
| **弹性管理** | `ResilienceManager` | `resilience/` | CB + 限流 + 隔离舱 + 超时 + 降级 |
| **质量评估** | `QualityAssessor` | `quality/` | 当前空壳，预留规则+LLM 双通道评分 |
| **图构建** | `GraphBuilder` | `graph/` | 拓扑排序：顺序列表 / 并行分组 |
| **步骤后处理** | `GraphCommonDataProcessor` | `graph/common/` | 输出注册、中断探测、失败策略处理 |
| **数据契约** | `DataContractEngine` | `conversation/contract/` | 变量占位替换、输出注册、失败策略查询 |
| **提示词** | `PromptManagerBuilder` | `Prompt/` | 所有 LLM 提示词模板集中管理 |
| **模型路由** | `ChatClientSelector` | `chat/` | 按名称从容器取对应 ChatClient |
| **配置加载** | `RemoteConfigLoader` | `configloader/` | 从 Nacos 加载各类远程配置 |
| **中断处理** | `InterruptHandler` | `interrupt/` | 挂起/恢复/回滚 |
| **中断门面** | `InterruptActionFaced` | `interrupt/` | 外部中断恢复的 REST 调用入口 |

### 3.3 五种执行模式

| 执行模式 | 枚举值 | 执行器 | 说明 |
|---|---|---|---|
| 顺序执行 | `SEQUENTIAL` | `SequentialExecutor` | 拓扑排序后串行 |
| 并行执行 | `PARALLEL` | `ParallelExecutor` | 拓扑分层，层内并行 |
| 条件分支 | `CONDITIONAL` | `ConditionalExecutor` | SPEL 或 LLM 判断分支 |
| 循环纠正 | `ITERATIVE_CORRECTION` | `IterativeCorrectionExecutor` | 评估→纠正→再评估 |
| 竞争选拔 | `COMPETITIVE` | `CompetitiveExecutor` | 多竞争者并行 → Selector 选优 |

每种模式都依赖 `GraphBuilder` 提供的拓扑排序/分组两种视图。

### 3.4 中断与分布式事务

```
正常流程:
  execute() @GlobalTransactional → 步骤N → ... → commit

中断流程:
  StepN 生成 REQUEST_CREDENTIAL / REQUEST_CONFIRM
       │
       ▼
  GraphCommonDataProcessor 探测 Command
       │
       ▼
  InterruptHandler.suspend():
    - runtimeContext 写入 stepId.interrupted
    - Redis 写入 "interrupt:"+xid (TTL 60 分钟)
    - RootContext.unbind (Seata 解绑)

恢复流程 (用户审批):
  POST /resume { xid, approved: true/false }
       │
       ▼
  InterruptActionFaced.doRespond(xid, body):
    - approved=true  → InterruptHandler.resume → HybridOrchestratorManager.resumeExecution
    - approved=false → InterruptHandler.rollback → GlobalTransactionContext.reload(xid).rollback()
```

### 3.5 弹性配置

Resilience4j 装饰顺序（由外到内）：

```
RateLimiter → Bulkhead → CircuitBreaker → TimeLimiter → Fallback
```

| 名称 | 用途 |
|---|---|
| `a2a-call` | 子 Agent A2A 调用 |
| `llm-fast-model` | 快速 LLM 推理 |
| `llm-reasoning-model` | 推理型 LLM |
| `redis-session` | Redis 会话操作 |
| `memory-service` | Memory Feign 调用 |
| `chroma-case` | 案例库向量检索 |
| `llm-step-call` | 步骤内部 LLM 调用 |

降级策略：LLM 失败返回默认 JSON；Agent 调用失败返回跳过标记；Memory/Chroma 失败返回空集合。

---

## 4. A2A 通信协议

### 4.1 协议栈

```
┌──────────────────────────────────────────┐
│          A2AMessage / A2AResponse        │ 应用消息层
├──────────────────────────────────────────┤
│     A2AChannel / CommanderChannel        │ 任务通道层
├──────────────────────────────────────────┤
│  A2ARouter / BaseNacosA2ARouter          │ 路由+传输层
├──────────────────────────────────────────┤
│     DiscoveryClient + RestClient         │ Nacos + HTTP
└──────────────────────────────────────────┘
```

### 4.2 关键文件

| 文件 | 路径 | 类型 |
|---|---|---|
| `A2ARouter` | `platformCommon/.../a2a/A2ARouter.java` | 接口 |
| `BaseNacosA2ARouter` | `commanderAgent/.../a2a/BaseNacosA2ARouter.java` | 实现 |
| `A2AChannel` | `platformCommon/.../a2a/A2AChannel.java` | 通道 |
| `CommanderChannel` | `commanderAgent/.../a2a/CommanderChannel.java` | 编排侧通道 |
| `A2AMessage` | `platformCommon/.../a2a/A2AMessage.java` | 消息 |
| `A2AMessageType` | `platformCommon/.../a2a/A2AMessageType.java` | 24 种消息类型枚举 |
| `A2AResponse` | `platformCommon/.../a2a/A2AResponse.java` | 响应 |
| `AgentCardWrapper` | (Spring AI Alibaba) | Agent 元数据 |
| `NacosAgentCardProvider` | `commanderAgent/...` | Agent 卡片提供者 |

### 4.3 Agent 生命周期

interface `AgentLifecycle`（`AgentLifecycle.java`）定义 12 个回调：

```
onRegister / onDeregister / onBind / onUnbind / onError / onRecover /
onHeartbeat / onModelBind / onModelUnbind / onCommanderConnect /
onCommanderDisconnect / onStateChanged
```

`BaseAgent` 提供 5 个核心实现：`onRegister / onModelBind / onDeregister / onError / onRecover`。

`DualChannelAgent` 扩展为双通道（Commander + A2A），分别处理 `onCommanderTask` 和 `onPeerRequest`。

### 4.4 消息类型全集（A2AMessageType）

基础通信 / 任务协作 / 能力管理 / 上下文同步 / 协商辩论 / 多模态协作 / 异常处理 共 7 大类 24 种，支持 `correlationId`、`priority`、`ttl`、`requiresAck`、`retryCount`。

---

## 5. 记忆系统

### 5.1 三层记忆架构

```
┌────────────────────────────────────────────────────────┐
│                   记忆系统分层                          │
├──────────────┬─────────────────┬───────────────────────┤
│   短期记忆    │   长期记忆       │     向量记忆          │
│   Redis      │   PostgreSQL    │     ChromaDB          │
│  会话消息池   │  用户画像        │    知识/案例库        │
│  30 分钟 TTL │  持久化存储      │    相似度检索         │
└──────┬───────┴────────┬────────┴───────────┬───────────┘
       │                │                    │
       ▼                ▼                    ▼
 SessionMemoryRepo  StructuredMemRepo   KnowledgeRepository
 (Redisson)        (MyBatis-Plus)       (ChromaVectorStore)
```

### 5.2 存储清单

| 记忆类型 | 存储 | 路径/表 | 关键类 |
|---|---|---|---|
| 会话消息 | Redis | `memory:session:{sessionId}` | `SessionMemoryRepository` |
| 用户身份 | PostgreSQL | `memory_identity` | `IdentityMemory` + Mapper |
| 用户画像 | PostgreSQL | `memory_profile` | `ProfileMemory` + Mapper |
| 用户偏好 | PostgreSQL | `memory_preference` | `PreferenceMemory` + Mapper |
| 行为记录 | PostgreSQL | `memory_behavior` | `BehaviorRecord` + Mapper |
| 决策记录 | PostgreSQL | `memory_decision` | `DecisionRecord` + Mapper |
| 关系记录 | PostgreSQL | `memory_relationship` | `RelationshipRecord` + Mapper |
| 会话历史 | PostgreSQL | `conversation_history` | `ConversationHistoryManagerService` |
| 计划历史 | PostgreSQL | `plan_history` (jsonb) | `PlanHistory` |
| 执行结果历史 | PostgreSQL | `execution_result_history` (jsonb) | `ExecutionResultHistory` |
| 知识索引 | PostgreSQL + Chroma | `knowledge_index` + `knowledge_base` 集合 | `KnowledgeRepository` |

### 5.3 客户端 API（sharedMemoryServiceApi）

6 个 FeignClient 全部指向服务 `aigc4lk-shared-memory-service`：

| Feign 接口 | HTTP 路径 | 用途 |
|---|---|---|
| `MemoryIdentityFeign` | `/memory/identity` | 用户身份 CRUD |
| `MemoryConversationHistoryFeign` | `/memory/conversation/history` | 会话历史保存 |
| `MemorySessionFeign` | `/memory/session` | 会话摘要 |
| `MemoryPreferenceFeign` | `/memory/preference` | 用户偏好读取 |
| `MemoryProfileFeign` | `/memory/profile` | 用户画像读取 |
| `MemoryKnowledgeFeign` | `/memory/Knowledge` | 向量知识检索 |

### 5.4 记忆生命周期

| 组件 | 职责 |
|---|---|
| `RuleBasedCleaner` | 长度检查、重复压缩、截断（同步，置信度 0.8） |
| `LLMBasedCleaner` | LLM 提取摘要/知识/标签（异步，置信度 0.95） |
| `MemoryCleanerOrchestrator` | 协调规则→LLM 两阶段清洗 |
| `MemoryDeduplicator` | 向量相似度去重（默认阈值 0.92） |
| `MemoryDesensitizer` | 手机号/邮箱/身份证/银行卡/IP 脱敏 |
| `MemoryPrioritySorter` | 按类型权重 → 时间 → 频率 → 相似度排序 |
| `MemoryLifecycleManager` | 定时任务（凌晨清理/归档/低频降级） |
| `MemoryManagerService` | 上下文聚合 + 会话摘要 |

定时调度：

- 每天 02:00 降级 3 个月未访问知识
- 每天 03:00 清理 90 天前行为记忆
- 每周日 04:00 归档 3 个月前冷数据

### 5.5 基础设施配置（端口 10086）

| 组件 | 配置 |
|---|---|
| PostgreSQL | `jdbc:postgresql://localhost:5432/aigc4lk_memory_db` |
| Redis | `redis://localhost:6379` (Redisson 单节点) |
| ChromaDB | `http://localhost:8000` (collection: `knowledge_base`) |
| Embedding | DashScope `text-embedding-v2` / `text-embedding-ada-002` |
| Nacos | namespace `0f18537a-...`，group `MEMORY_GROUP` |

---

## 6. 子 Agent 集群

### 6.1 通用结构（5 个 Agent 一致）

```
*AgentApplication.java                      ← 启动类
configuration/*AgentConfiguration.java      ← 3 @Bean (dashScopeApi, chatModel, reactAgent)
tools/*MemoryTools.java                     ← 4 个 Feign 记忆方法
tools/*AbilityTools.java                    ← 领域工具
mcp/RemoteMcpToolProvider.java              ← MCP 客户端供给器 (stdio + streamable-http)
config/ChatModelApiKeyConfig.java           ← 前缀 customer.model
config/McpServersProperties.java            ← 前缀 mcp
application.yml                             ← Nacos + A2A card 注册
pom.xml
```

### 6.2 业务差异

| Agent | 端口 | 核心工具 | 领域实体 | 特殊之处 |
|---|---|---|---|---|
| `documentAgent` | 12100 | `DocumentMemoryTools` | — | 唯一使用 DynamicAgentCardProvider + AgentSkillsProperties；双技能源；`is_relation_agent` 标志 |
| `dataAnalysisAgent` | 12200 | `DataAnalysisAbilityTools` (`multiDimensionAnalysis/trendForecast/statisticalSummary`) | `MultiDimensionResult, StatisticalSummary, TrendForecastResult` | 结构化结果多 |
| `codeReviewAgent` | 12300 | `CodeAnalysisAbilityTools` + `GitDiffTools` | `CodeQualityReport, SecurityReport, SecurityIssue, CodingStandardReport` | 实体类最多；用 `git diff` 命令行 |
| `customerServiceAgent` | 12400 | `OrderQueryTools + TicketQueryTools + RoutePolicyTools` | — | 业务工具丰富；mock 风格 |
| `imageAnalysisAgent` | 12410 | `ImageRecognitionTool + OCRTools + SceneUnderstandingTool` | — | **唯一多模态**（qwen-vl-max 双模型 + Base64 图像） |

### 6.3 端口分配

| Agent | 端口 |
|---|---|
| documentAgent | 12100 |
| dataAnalysisAgent | 12200 |
| codeReviewAgent | 12300 |
| customerServiceAgent | 12400 |
| imageAnalysisAgent | 12410 |

---

## 7. 平台公共层（platformCommon）

### 7.1 包结构

```
com/air/platform/common
├── a2a/           A2A 协议（A2ARouter/A2AMessage/A2AResponse/A2AChannel）
├── agent/         BaseAgent / AgentLifecycle / 多模态 Agent（图像/语音/视频）
├── architecture/  AgentArchitecture/Sequential/Parallel + ArchitectureRegistry
├── enums/         ModalityType / ModelType / ArchitectureType
├── mcp/           McpTool / McpToolParam 注解
├── model/         ModelDefinition + Vision/Speech/Video + ArchitectureDefinition + ScenarioBinding
├── multimodal/    MultimodalFusionEngine + 输入输出 VO
└── tool/          McpClientManager / RemoteMcpToolProvider
```

### 7.2 关键抽象

- `A2ARouter` — 链路契约（`route / discover / register / heartbeat`）
- `BaseAgent` — 抽象 Agent，含 12 个生命周期回调
- `DualChannelAgent` — 双通道（Commander + A2A）
- `MultimodalFusionEngine` — 文本/视觉/语音/视频融合理解 + 跨模态生成
- `McpClientManager` — MCP 连接池与工具缓存（支持 stdio + streamable-http）
- `ModelDefinition` — 模型路由定义（3 种 ModelType + 针对视觉/语音/视频的专用子类）

---

## 8. REST API 设计

### 8.1 Commander 对外端点

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/execute` | 主编排入口（模板/动态） |
| `POST` | `/resume` | 中断恢复（审批通过/拒绝） |
| `GET`  | `/status/{xid}` | 事务/计划状态查询 |
| `POST` | `/interrupt/callback` | 用户审批回调（由 `InterruptActionFaced` 暴露） |

### 8.2 Memory API

| 方法 | Controller | 用途 |
|---|---|---|
| `GET/POST/DELETE` | `IdentityController` | 用户身份 |
| `POST` | `ConversationHistoryController` | 会话历史保存 |
| `POST` | `SessionController/{sessionId}/summarize` | 会话摘要 |
| `GET` | `PreferenceController/{userId}` | 偏好获取 |
| `GET` | `ProfileController/{userId}` | 画像获取 |
| `POST` | `KnowledgeController/search` | 向量检索 |

---

## 9. 配置中心（Nacos 3.2）

### 9.1 公共配置

- **命名空间**: `0f18537a-2027-45f8-9a55-e837fa9fdd2a`
- **注册中心**: `127.0.0.1:8848`
- **权限**: `aigc4lk / 123456`

### 9.2 按模块配置集

| Group | 模块 | 关键配置 Data-id |
|---|---|---|
| `COMMANDER_GROUP` | commanderAgent | `graph-templates-none.yaml`, `intent-rules-none.yaml`, `comander-resilience4j.yaml`, `commander-chroma-vectorstore-config.yaml`, `spring-ai-mcp-config.yaml` |
| `DOCUMENT_GROUP` | documentAgent | `document-agent-remote-mcp.yaml`, `document-agent-skill-properties.yaml` |
| `DATA_ANALYSIS_GROUP` | dataAnalysisAgent | `data-analysis-agent-remote-mcp.yaml` |
| `CODE_REVIEW_GROUP` | codeReviewAgent | `code-review-agent-remote-mcp.yaml` |
| `CUSTOMER_SERVICE_GROUP` | customerServiceAgent | `customer-service-agent-remote-mcp.yaml` |
| `IMAGE_ANALYSIS_GROUP` | imageAnalysisAgent | `image-analysis-agent-remote-mcp.yaml` |
| `MEMORY_GROUP` | sharedMemoryServices | `memory-general-enable.yaml`, `memory-chroma-vectorstore-config.yaml` |
| `DEFAULT_GROUP` | 所有 | `common-chat-model-api-keys.properties`, `common--desensitize-enable.yaml`, `staic-application-metrics.yaml` |

### 9.3 关键配置类

| 配置类 | 前缀 | 用途 |
|---|---|---|
| `GraphTemplatesProperties` | `commander.graph-templates` | 图模板加载（`@RefreshScope`） |
| `IntentRulesProperties` | `commander.intent-rules` | 意图匹配规则 |
| `ChatModelApiKeyProperties` | `customer.model` | API Key 集合（DashScope/OpenAI） |
| `ChromaDbClientConfig` | `spring.ai.vectorstore.chroma` | Chroma 连接配置 |
| `McpServersProperties` | `mcp` | MCP 服务器清单 |
| `DesensitizeEnableConfig` | `services.memory.desensitize` | 脱敏开关 |
| `DedupEnableConfig` | `memory.dedup` | 去重开关 |
| `LifecycleConfig` | `memory.lifecycle` | 生命周期策略 |

---

## 10. 数据模型

### 10.1 核心领域模型

| 类 | 路径 | 关键字段 |
|---|---|---|
| `OrchestrationPlan` | `commander/.../model/` | planId / mode(TEMPLATE\|DYNAMIC) / executionMode / steps / correctionConfig / competitiveConfig |
| `Step` | `commander/.../model/` | id / type(A2A_DELEGATE\|INTERRUPT\|LLM_CALL) / agent / task / input / dependsOn / checkpoint / dataContract |
| `ExecutionResult` | `commander/.../model/` | stepId / success / executionStatus(SUSPEND\|DONE\|FAILURE) / output / command / durationMs |
| `ExecutionPlan` | `commander/.../model/` | mode / planId / results / interrupted / xid |
| `InterruptContext` | `commander/.../model/` | xid / planId / currentStepId / planJson / commandType / runtimeContext（Serializable） |
| `StepDataContract` | `commander/.../model/` | inputFields / outputField / onFailure(ROLLBACK_AND_STOP\|SKIP_AND_CONTINUE\|MARK_AS_FAILED) |
| `MemoryContext` | `commander/.../model/` | userQuery / recentMessages / userProfile / preferences / similarCases / knowledgeChunks |
| `IterationState` | `commander/.../model/` | iteration / phase ("EVALUATE"/"CORRECT") |
| `CompetitiveState` | `commander/.../model/` | groupIndex / completedCompetitors |
| `IntentResult` | `commander/.../model/` | isTemplate / scenario / templateId / complexity / highRisk |
| `ValidationResult` | `commander/.../model/` | isValid / errors |
| `PlanEvaluationResult` | `commander/.../model()` | winner / scores / reason |

### 10.2 关键枚举

| 枚举 | 值 |
|---|---|
| `ExecutionMode` | SEQUENTIAL / PARALLEL / CONDITIONAL / ITERATIVE_CORRECTION / COMPETITIVE |
| `StepType` | A2A_DELEGATE / INTERRUPT / LLM_CALL |
| `ExecutionStatus` | SUSPEND / DONE / FAILURE |
| `ModeType` | TEMPLATE / DYNAMIC |
| `CheckpointType` | CREDENTIAL / CONFIRM |
| `FailurePolicy` | ROLLBACK_AND_STOP / SKIP_AND_CONTINUE / MARK_AS_FAILED |
| `EvaluationMethod` | SPEL / LLM_JUDGE |

---

## 11. 构建与运行

### 11.1 构建

```bash
# 构建整个项目
mvn clean install -DskipTests

# 构建指定模块（含依赖）
mvn clean install -pl commanderAgent -am -DskipTests
```

### 11.2 启动顺序

依赖前置服务：

1. **Nacos 3.2** (`localhost:8848`)
2. **Seata TC** (`localhost:8091`)
3. **Redis** (`localhost:6379`)
4. **PostgreSQL** (`aigc4lk_memory_db`)
5. **ChromaDB** (`localhost:8000`)

启动应用：

```bash
# 共享记忆服务（先于 commander）
mvn spring-boot:run -pl sharedMemoryServices
# 编排中心
mvn spring-boot:run -pl commanderAgent
# 子 Agent（可任选启动）
mvn spring-boot:run -pl documentAgent
mvn spring-boot:run -pl dataAnalysisAgent
mvn spring-boot:run -pl codeReviewAgent
mvn spring-boot:run -pl customerServiceAgent
mvn spring-boot:run -pl imageAnalysisAgent
```

### 11.3 前置配置

需在 Nacos 命名空间 `0f18537a-2027-45f8-9a55-e837fa9fdd2a` 中配置：

- `common-chat-model-api-keys.properties`：DashScope/OpenAI 等 LLM API Key
- `graph-templates-*`：图模板配置
- `intent-rules-*`：意图识别规则
- `*-chroma-vectorstore-config.yaml`：Chroma 连接信息
- `*-remote-mcp.yaml`：MCP 服务器清单

---

## 12. 已知问题与技术债

| 范围 | 问题 | 建议 |
|---|---|---|
| 全局 | `ChatModelApiKeyConfig` 前缀 `customer.model` 在 5 个 Agent 中复用，命名与来源不一致 | 重命名前缀为 `chat.model.api-key` |
| 全局 | `MemoryTools` 四个 Feign 方法在 5 个 Agent 中高度重复 | 抽取到 `platformCommon` 或 `sharedMemoryServiceApi` |
| 全局 | `spring.ai.mcp.client.enabled=false` 被标为临时 todo，与 `RemoteMcpToolProvider` 并存 | 确定是否移除 native MCP 自动配置 |
| documentAgent | `SkillDocumentLoader` 找不到文件时静默返回空串 | 增加告警/异常 |
| documentAgent | 程序化 card + 声明式 yaml 技能双源潜在冲突 | 统一注册策略 |
| customerServiceAgent | `@ComponentScan("com.customerServiceAgent")` 与实际包 `com.air.customerServiceAgent` 不一致 | 统一为实际包名 |
| dataAnalysisAgent / codeReviewAgent / customerServiceAgent / imageAnalysisAgent | `maven-compiler-plugin` source/target 写死为 `15`，与项目 Java 17 版本不符 | 移除写死或改为 17 |
| imageAnalysisAgent | `ImageRecognitionTool.detectObjects` 硬编码模拟返回 | 对接真实视觉服务 |
| commanderAgent | `QualityAssessor` 当前为空壳类，未实现评分逻辑 | 按计划落规则评分 + LLM 评分 |

---

## 13. 附录：关键文件索引

### 13.1 平台公共层

- `platformCommon/src/main/java/com/air/platform/common/a2a/A2ARouter.java`
- `platformCommon/src/main/java/com/air/platform/common/a2a/A2AMessage.java`
- `platformCommon/src/main/java/com/air/platform/common/a2a/A2AResponse.java`
- `platformCommon/src/main/java/com/air/platform/common/a2a/A2AChannel.java`
- `platformCommon/src/main/java/com/air/platform/common/agent/BaseAgent.java`
- `platformCommon/src/main/java/com/air/platform/common/agent/AgentLifecycle.java`
- `platformCommon/src/main/java/com/air/platform/common/multimodal/MultimodalFusionEngine.java`
- `platformCommon/src/main/java/com/air/platform/common/model/ModelDefinition.java`
- `platformCommon/src/main/java/com/air/platform/common/mcp/McpTool.java`
- `platformCommon/src/main/java/com/air/platform/common/tool/RemoteMcpToolProvider.java`

### 13.2 Commander

- `commanderAgent/src/main/java/com/air/commander/CommanderAgentApplication.java`
- `commanderAgent/src/main/java/com/air/commander/orchestrator/HybridOrchestratorManager.java`
- `commanderAgent/src/main/java/com/air/commander/intent/IntentClassifier.java`
- `commanderAgent/src/main/java/com/air/commander/orchestrator/compet/CandidateGenerator.java`
- `commanderAgent/src/main/java/com/air/commander/orchestrator/compet/PlanEvaluator.java`
- `commanderAgent/src/main/java/com/air/commander/orchestrator/compet/CompetitionOrchestratorEngine.java`
- `commanderAgent/src/main/java/com/air/commander/orchestrator/dynamic/DynamicOrchestrator.java`
- `commanderAgent/src/main/java/com/air/commander/orchestrator/template/TemplatePlanGenerator.java`
- `commanderAgent/src/main/java/com/air/commander/validator/PlanValidator.java`
- `commanderAgent/src/main/java/com/air/commander/graph/GraphExecutorEngine.java`
- `commanderAgent/src/main/java/com/air/commander/graph/StepUnitExecutor.java`
- `commanderAgent/src/main/java/com/air/commander/graph/GraphBuilder.java`
- `commanderAgent/src/main/java/com/air/commander/graph/common/GraphCommonDataProcessor.java`
- `commanderAgent/src/main/java/com/air/commander/conversation/contract/DataContractEngine.java`
- `commanderAgent/src/main/java/com/air/commander/a2a/BaseNacosA2ARouter.java`
- `commanderAgent/src/main/java/com/air/commander/memory/MemoryContextBuilder.java`
- `commanderAgent/src/main/java/com/air/commander/memory/MemoryUpdatePipeline.java`
- `commanderAgent/src/main/java/com/air/commander/memory/CaseLibraryClient.java`
- `commanderAgent/src/main/java/com/air/commander/quality/QualityAssessor.java`
- `commanderAgent/src/main/java/com/air/commander/resilience/ResilienceManager.java`
- `commanderAgent/src/main/java/com/air/commander/interrupt/InterruptHandler.java`
- `commanderAgent/src/main/java/com/air/commander/interrupt/InterruptActionFaced.java`
- `commanderAgent/src/main/java/com/air/commander/Prompt/PromptManagerBuilder.java`
- `commanderAgent/src/main/java/com/air/commander/chat/ChatClientSelector.java`
- `commanderAgent/src/main/java/com/air/commander/configloader/loader/RemoteConfigLoader.java`
- `commanderAgent/src/main/java/com/air/commander/controller/OrchestrationController.java`

### 13.3 记忆服务

- `sharedMemoryServiceApi/src/main/java/com/air/api/feignClient/*Feign.java`  (6 个)
- `sharedMemoryServices/src/main/java/com/air/memory/SharedMemoryServicesApplication.java`
- `sharedMemoryServices/src/main/java/com/air/memory/service/MemoryManagerService.java`
- `sharedMemoryServices/src/main/java/com/air/memory/repository/structured/StructuredMemoryRepository.java`
- `sharedMemoryServices/src/main/java/com/air/memory/repository/unstructured/SessionMemoryRepository.java`
- `sharedMemoryServices/src/main/java/com/air/memory/repository/vectorized/KnowledgeRepository.java`
- `sharedMemoryServices/src/main/java/com/air/memory/cleaner/RuleBasedCleaner.java`
- `sharedMemoryServices/src/main/java/com/air/memory/cleaner/LLMBasedCleaner.java`
- `sharedMemoryServices/src/main/java/com/air/memory/deduplicator/MemoryDeduplicator.java`
- `sharedMemoryServices/src/main/java/com/air/memory/desensitizer/MemoryDesensitizer.java`
- `sharedMemoryServices/src/main/java/com/air/memory/priority/MemoryPrioritySorter.java`
- `sharedMemoryServices/src/main/java/com/air/memory/lifecycle/MemoryLifecycleManager.java`

### 13.4 子 Agent 配置类

| Agent | 配置文件 |
|---|---|
| documentAgent | `documentAgent/src/main/java/com/air/document/configuration/DocumentAgentConfiguration.java` |
| dataAnalysisAgent | `dataAnalysisAgent/src/main/java/com/air/dataAnalysis/configuration/DataAnalysisAgentConfiguration.java` |
| codeReviewAgent | `codeReviewAgent/src/main/java/com/air/codeReview/configuration/CodeReviewAgentConfiguration.java` |
| customerServiceAgent | `customerServiceAgent/src/main/java/com/air/customerService/configuration/CustomerServiceAgentConfiguration.java` |
| imageAnalysisAgent | `imageAnalysisAgent/src/main/java/com/air/imageAnalysis/configuration/ImageAnalysisAgentConfiguration.java` |