# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目概述

**aigc4lk** — 基于 Spring Boot 3.5.0 + Spring AI Alibaba 1.1.2.2 构建的企业级多Agent编排平台。平台通过 **Commander** 枢纽协调LLM驱动的子Agent，实现意图识别、执行计划生成（模板模式或LLM动态生成）、计划校验以及多种执行模式（顺序、并行、条件、循环纠正、竞争）下的计划执行。

**技术栈**: Java 17, Spring Boot 3.5.0, Spring Cloud Alibaba 2025.0.0.0, Spring AI 1.1.2, Nacos 3.2（注册/配置/A2A）, Seata 2.x（分布式事务）, PostgreSQL, Redis, ChromaDB（向量存储）, Resilience4j。

## 构建与运行

```bash
# 构建整个项目（父POM包含所有模块）
mvn clean install -DskipTests

# 构建指定Agent模块
mvn clean install -pl commanderAgent -am -DskipTests

# 运行各服务（每个都是独立的Spring Boot应用）：
# Commander（编排中心，端口 10010）
mvn spring-boot:run -pl commanderAgent
# 共享记忆服务（记忆/持久化层）
mvn spring-boot:run -pl sharedMemoryServices
# 子Agent（每个都通过A2A AgentCard注册到Nacos）：
mvn spring-boot:run -pl documentAgent
mvn spring-boot:run -pl dataAnalysisAgent
mvn spring-boot:run -pl codeReviewAgent
mvn spring-boot:run -pl customerServiceAgent
mvn spring-boot:run -pl imageAnalysisAgent
```

**前置依赖**: Nacos 3.2（localhost:8848）, Seata TC（localhost:8091）, Redis（localhost:6379）, ChromaDB, DashScope/OpenAI API密钥（通过Nacos配置加载）。各Agent的 `application.yml` 从Nacos加载配置——确保Nacos命名空间 `0f18537a-2027-45f8-9a55-e837fa9fdd2a` 可用且包含所需配置。

## 架构

### 模块布局（Maven多模块）

```
aigc4lk（父POM）
├── platformCommon          — 共享A2A协议、Agent基类、多模态工具、MCP抽象层
├── commanderAgent          — 编排中心（意图识别、计划生成、图执行）
├── sharedMemoryServiceApi  — 记忆服务的Feign客户端接口（DTO + API契约）
├── sharedMemoryServices    — 记忆持久化（PostgreSQL/Redis/Chroma）、对话历史、用户画像
├── documentAgent           — 文档处理子Agent（A2A注册）
├── dataAnalysisAgent       — 数据分析子Agent（A2A注册）
├── codeReviewAgent         — 代码审查子Agent（A2A注册）
├── customerServiceAgent    — 客服子Agent（A2A注册，含订单/票务/路由工具）
└── imageAnalysisAgent      — 图像分析子Agent（A2A注册，含OCR/场景识别工具）
```

### 核心架构模式

1. **混合编排** — Commander支持两种执行模式：
   - **模板模式**: 从Nacos加载预定义图模板（`commander-graph-templates.yaml`），适用于已知场景
   - **动态模式**: LLM（qwen-max）结合ChromaDB的少样本案例检索即时生成计划；经 `PlanValidator` 校验并自动重试

2. **A2A Agent通信** — 子Agent通过A2A `AgentCard` 元数据注册到Nacos。Commander通过 `NacosAgentCardProvider` 发现它们，并通过HTTP JSON-RPC调用（`BaseNacosA2ARouter`）。每个Agent动态暴露其能力/技能。

3. **竞争引擎** — `CompetitionOrchestratorEngine` 使用不同LLM生成多个候选计划，再用另一个LLM评估选出最佳方案（`CandidateGenerator` → `PlanEvaluator`）。

4. **五种执行模式**（`OrchestrationPlan.ExecutionMode`）：
   - `SEQUENTIAL` — 拓扑排序，按依赖顺序执行步骤
   - `PARALLEL` — 将独立步骤分组并行执行
   - `CONDITIONAL` — 根据步骤条件分支执行（SPEL表达式或LLM判断）
   - `ITERATIVE_CORRECTION` — 带质量阈值和纠正步骤的循环
   - `COMPETITIVE` — 多个竞争者执行同一任务，由选择器选出最优结果

5. **分布式事务** — Seata `@GlobalTransactional` 包裹整个编排过程。`INTERRUPT` 步骤可挂起事务等待用户审批（存入Redis并设TTL），然后通过中断处理器恢复或回滚。

6. **弹性与容错**（`ResilienceManager`）：
   - 每个Agent调用、Chroma查询、LLM API均配置断路器（Resilience4j）
   - LLM调用的限流器
   - 基于信号量的并发编排槽位控制
   - 降级策略: Chroma不可用→跳过向量检索；Agent不可用→跳过步骤；LLM不可用→降级计划

7. **记忆系统** — `sharedMemoryServices` 提供分层记忆：
   - **短期记忆**: Redis中的会话消息（通过 `ConversationManager`）
   - **长期记忆**: PostgreSQL中的用户画像、行为记录、决策记录、关系记录
   - **向量记忆**: 知识在ChromaDB中索引，支持相似度搜索
   - **记忆生命周期**: 基于LLM的摘要、去重、脱敏、优先级排序

8. **质量评估** — 执行后 `QualityAssessor` 评估结果（规则评分 + LLM评分）。高质量执行结果作为案例存入ChromaDB，供未来少样本检索使用（自进化闭环）。

### Commander流程（动态模式）

```
用户请求 → HybridOrchestratorManager
  → MemoryContextBuilder（从用户画像/行为/知识构建上下文）
  → IntentClassifier（LLM: 场景 + 复杂度 + 模态）
  → CompetitionOrchestratorEngine
      → DynamicOrchestrator（LLM以可用Agent卡片为上下文生成计划）
      → PlanValidator（拓扑检查 + Agent白名单 + 变量引用检查）
  → GraphExecutorEngine（路由到SEQUENTIAL/PARALLEL等执行器）
      → StepUnitExecutor → 通过BaseNacosA2ARouter调用A2A | 通过ChatClient调LLM | INTERRUPT
  → QualityAssessor（评分，高质量案例入库）
  → MemoryUpdatePipeline（异步: 归档对话、更新画像）
```

### 重要模型类

- `OrchestrationPlan` — 包含步骤、执行模式、纠正/竞争配置的执行计划
- `Step` — 原子执行单元，包含类型（A2A_DELEGATE / LLM_CALL / INTERRUPT）、依赖关系、输入/输出契约、检查点配置
- `ExecutionResult` — 每步执行结果，包含状态、输出、错误、耗时
- `InterruptContext` — 用户审批流程中的挂起事务状态
- `AgentCardWrapper` — 来自Spring AI Alibaba的A2A标准Agent描述符

### 子Agent通用结构

每个子Agent遵循相同的结构：
1. `*AgentApplication.java` — Spring Boot入口
2. `configuration/*AgentConfiguration.java` — Bean定义（工具、MCP提供者、聊天模型）
3. `config/ChatModelApiKeyConfig.java` — 从Nacos加载API密钥
4. `config/McpServersProperties.java` — MCP工具服务器配置
5. `mcp/RemoteMcpToolProvider.java` — 远程MCP工具提供者
6. `tools/*Tools.java` — Agent专用工具/函数
7. 基于技能的专用能力工具（如 DocumentAgent 中的 `AgentSkillsProperties` + `DynamicAgentCardProvider`）
8. `application.yaml` — 配置（A2A注册需要Nacos服务发现；设置 `is_relation_agent: true`）

### 平台公共层核心抽象（`platformCommon`）

- `A2ARouter` — Agent发现与通信的接口（通过 `BaseNacosA2ARouter` 基于Nacos实现）
- `A2AMessage`/`A2AResponse` — A2A协议消息格式
- `BaseAgent` — 抽象Agent，含生命周期（注册、绑定、注销、错误、恢复）和建造者模式
- `AgentLifecycle` — Nacos注册回调的生命周期接口
- `MultimodalFusionEngine` — 多模态输入融合（文本/视觉/语音/视频）
- `McpTool`/`McpToolParam` — MCP工具定义注解
- `ModelDefinition`/`ArchitectureDefinition` — 模型路由和架构配置