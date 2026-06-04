# aigc4lk-Agent智能平台架构设计文档 v10.0

> **版本**: v10.0.0 (生产就绪完整版)  
> **日期**: 2026-06-04  
> **技术栈**: Spring Boot 3.5.0 + Spring AI Alibaba 1.1.2.2 + Nacos 3.2 + Seata 2.x + PostgreSQL + Redis + Chroma + Resilience4j + Micrometer + Zipkin  
> **架构模式**: 混合编排 + 动态编排 + 自进化闭环 + 分层记忆 + 中断与分布式事务 + 多模态融合 + 确定性保障 + 流量防护 + 故障降级 + 多租户隔离  
> **核心特性**: 本架构是一份完整的、可直接落地的企业级多Agent系统设计，覆盖从编排层、服务层、数据层到运维层的全维度，包含冷启动、自进化、安全合规、灾备演练等全套方案。

---

## 一、设计理念与目标


### 1.1 设计哲学

本平台旨在构建一个**可进化、可审计、可降级、人机协同**的多智能体系统。核心设计哲学如下：

**五大支柱原则**：

1.  **以用户为中心，保留人类决策权**：关键时刻（授权、风控、策略选择）中断AI执行，等待人类确认。
2.  **确定性优先于探索性**：动态编排生成的计划，必须经过静态校验（拓扑、白名单、变量引用），不盲信LLM输出。
3.  **自愈优于中断**：系统具备故障自感知与降级能力（Chroma不可用降级纯LLM、Seata不可用降级无事务），单点故障不阻断核心主流程。
4.  **事件驱动 + 最终一致**：跨Agent长事务采用Saga与TCC混合模式，不依赖全局锁；事务悬挂支持主动恢复。
5.  **闭环覆盖全生命周期**：执行有闭环（自进化案例库），故障恢复有闭环（TC状态查询），流量防护有闭环（信号量+断路器+排队），冷启动有闭环（种子案例注入）。

### 1.2 关键指标

| 指标                | 目标值 | 说明                                 |
| ------------------- | ------ | ------------------------------------ |
| 子Agent数量         | 16+    | 可水平扩展，支持多租户独立部署       |
| 模板模式P99延迟     | <200ms | 不含子Agent内部耗时                  |
| 动态编排首次执行P99 | <8s    | 含LLM规划+案例检索                   |
| 案例库检索P99       | <100ms | 向量相似度+过滤；超200ms自动降级     |
| 分布式事务超时      | 30min  | 可配置（用户授权场景）               |
| 系统可用性          | 99.95% | 核心服务集群化 + 降级预案 + 灾备演练 |
| 案例命中率          | >70%   | 动态编排时检索到有效案例的比例       |
| 动态计划校验拦截率  | >95%   | 拦截死循环、幻觉Agent、无效变量引用  |
| 中断事务恢复成功率  | >99.9% | 含Commander崩溃后通过TC恢复          |

---

## 二、系统全景架构

### 2.1 逻辑架构总图

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   外部依赖与基础设施                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │  Nacos   │ │ Seata TC │ │PostgreSQL│ │  Redis   │ │  Chroma  │ │DashScope │        │
│  │(注册/配  │ │(事务协调 │ │(关系数据)│ │(缓存/状  │ │(向量存储)│ │(LLM API) │        │
│  │ 置/A2A)  │ │ 器集群)  │ │          │ │ 态/队列) │ │          │ │          │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
           ┌──────────────────────────────────┼──────────────────────────────────┐
           │                                  │                                  │
           ▼                                  ▼                                  ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                  网关层 (API Gateway)                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐                       │
│  │   JWT 鉴权       │  │   限流 (令牌桶)   │  │   租户识别       │                       │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   编排层 (Commander)                                      │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐           │
│  │ Intent         │ │ TaskFeature    │ │ Hybrid         │ │ Dynamic        │           │
│  │ Classifier     │ │ Analyzer       │ │ Orchestrator   │ │ Orchestrator   │           │
│  │ (意图识别)      │ │ (特征分析)     │ │ (混合编排器)    │ │ (动态编排器)    │           │
│  └────────────────┘ └────────────────┘ └────────────────┘ └───────┬────────┘           │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐         │                    │
│  │ Graph          │ │ Plan           │ │ ChatModel      │         ▼                    │
│  │ Builder        │ │ Validator      │ │ Router         │ ┌────────────────┐           │
│  │ (图构建器)     │ │ (计划校验器)    │ │ (模型路由)     │ │ Resilience     │           │
│  └────────────────┘ └────────────────┘ └────────────────┘ │ Manager        │           │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐ │ (弹性管理器)    │           │
│  │ Quality        │ │ Adversarial    │ │ Conversation   │ └────────────────┘           │
│  │ Assessor       │ │ Assessor       │ │ Manager        │ ┌────────────────┐           │
│  │ (质量评估)     │ │ (对抗评估)     │ │ (会话管理)     │ │ A2A Router     │           │
│  └────────────────┘ └────────────────┘ └────────────────┘ │ (A2A路由)      │           │
│                                                           └────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
           │                                  │                                  │
           ▼                                  ▼                                  ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   服务层 (独立微服务)                                      │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐            │
│  │ Memory     │ │ Case       │ │ Credential │ │ Document   │ │ Data       │            │
│  │ Service    │ │ Library    │ │ Service    │ │ Agent      │ │ Analysis   │            │
│  │ (记忆服务) │ │ (案例库)    │ │ (凭证服务) │ │ (文档Agent)│ │ Agent      │ ...        │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘ └────────────┘            │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐            │
│  │ Image      │ │ Code       │ │ Knowledge  │ │ Email      │ │ Calendar   │            │
│  │ Analysis   │ │ Generation │ │ Retrieval  │ │ Agent      │ │ Agent      │ ...        │
│  │ Agent      │ │ Agent      │ │ Agent      │ │            │ │            │            │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘ └────────────┘            │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   数据层                                                  │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐           │
│  │ PostgreSQL           │  │ Redis                │  │ Chroma               │           │
│  │ 主库+只读副本        │  │ 哨兵模式             │  │ 向量存储             │           │
│  └──────────────────────┘  └──────────────────────┘  └──────────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 完整数据流（动态编排为例）

```
1. 用户请求 → API Gateway
   ├── JWT 鉴权，提取 userId, tenantId
   ├── 租户限流检查
   └── 转发至 Commander

2. Commander 接收请求
   ├── ConversationManager.addMessage(threadId, "user", content)  // 写入短期记忆
   └── HybridOrchestrator.execute(request)

3. 意图识别 (IntentClassifier)
   ├── 构造 Prompt，调用 fastModel (qwen-turbo)
   ├── 输出：{ scenario: "FINANCIAL_ANALYSIS", complexity: "HIGH", modality: "TEXT" }
   └── 若 complexity == HIGH 且 scenario 不匹配任何硬规则 → 进入动态模式

4. 任务特征分析 (TaskFeatureAnalyzer)
   ├── 匹配硬规则：财务审计 → 模板模式 (本场景无)
   └── 决策：动态模式 (complexity=HIGH)

5. 动态编排 (DynamicOrchestrator)
   ├── 检索相似案例 (Case Library)
   │   ├── 调用 Case Library Feign 接口
   │   ├── Chroma 向量检索 (cosine 相似度)
   │   ├── 超时 200ms 或 断路器打开 → 降级：跳过检索，日志记录
   │   └── 返回 Top-3 相似案例
   ├── 构建 Few-shot Prompt (含相似案例)
   ├── 调用 reasoningModel (qwen-max) 生成编排计划 JSON
   │   └── 计划包含 steps 列表、依赖关系、可选 INTERRUPT 节点
   └── 输出：OrchestrationPlan (JSON)

6. 计划校验 (PlanValidator) ← v10.0 核心新增
   ├── 拓扑校验：检测步骤依赖是否存在环 (DFS)
   ├── 白名单校验：每个 step.agent 是否在 Nacos AgentCard 列表中
   ├── 变量引用校验：{stepX.output} 引用是否存在
   ├── 校验失败 → 重试最多1次 → 仍失败则 INTERRUPT 人工干预
   └── 校验通过 → 进入 Graph 构建

7. Graph 执行 (GraphBuilder + StateGraph)
   ├── 解析计划，动态构建 StateGraph
   ├── 遍历节点：
   │   ├── A2A_DELEGATE → A2ARouter 调用子 Agent (断路器保护)
   │   ├── LLM_CALL → ChatModelRouter 调用本地模型 (限流保护)
   │   └── INTERRUPT → 挂起事务，推送授权请求 (见步骤8)
   └── 每步执行结果存入 StateGraph context

8. 中断处理（若有 INTERRUPT 节点）
   ├── Commander 挂起 Seata 全局事务 (GlobalTransaction.suspend())
   ├── 将 XID、userId、stepId、command 存入 Redis (interrupt:{XID}, TTL=30min)
   ├── 向用户推送授权请求 (WebSocket 或 轮询)
   ├── 用户响应后恢复：
   │   ├── Redis Key 存在 → 恢复事务，注入凭证，继续执行
   │   └── Redis Key 不存在 → 查询 Seata TC 全局事务状态
   │       ├── 事务活跃 (Begin/CommitRetrying) → 重建 Redis Key，恢复执行
   │       └── 事务已结束 (Rollbacked/Finished) → 返回错误"操作已结束"
   └── 用户拒绝 → 恢复事务 → GlobalTransaction.rollback() → 清理 Redis

9. 质量评估 (QualityAssessor)
   ├── 规则评估 (超时/异常/格式) → R 得分
   ├── LLM 评估 (正确性/完整性/可用性) → L 得分
   └── 若 (R+L)/2 ≥ 85 → 进入对抗评估

10. 对抗评估 (Adversarial Assessor) ← v10.0 新增
    ├── 以挑剔视角检查步骤序列是否冗余
    ├── 检查是否违背已知业务规则
    ├── 若未通过 → 质量分乘以惩罚系数 0.8
    └── 最终得分 Q

11. 案例入库 (Case Library)
    ├── Q ≥ 85 且 置信度 ≥ 0.8 → 保存 execution_case + step_detail
    ├── 生成向量 embedding(user_input + scenario) → 存入 Chroma
    └── 记录 diversity_tags (对抗评估标签)

12. 流式输出 (SSE)
    ├── 最终结果分字符推送给用户
    └── 写入 ConversationManager (短期记忆 + 异步归档)
```

### 2.3 模板模式数据流

```
1. 用户请求 → API Gateway → Commander
2. 意图识别 → TaskFeatureAnalyzer 匹配硬规则 → 模板模式
3. GraphTemplateSelector 从 Nacos 加载模板 (commander-graph-templates.yaml)
4. GraphBuilder 构建 StateGraph
5. 严格按模板顺序执行
   ├── 每步执行前检查校验规则 (not_null, format_check)
   ├── INTERRUPT 节点触发中断 (同动态模式)
   └── 异常时触发 rollback (检查点保存)
6. 流式输出结果
```

---

## 三、核心模块详解

### 3.1 Commander（编排层）

#### 3.1.1 完整组件列表

| 组件                    | 类型     | 职责                                         | 关键依赖                                   | 版本       |
| ----------------------- | -------- | -------------------------------------------- | ------------------------------------------ | ---------- |
| `IntentClassifier`      | 核心     | 调用 LLM 识别用户意图（场景、复杂度、模态）  | ChatModel (qwen-turbo)                     | v9.0       |
| `TaskFeatureAnalyzer`   | 核心     | 规则+轻量分析，决定执行模式（模板/动态）     | Nacos 硬规则配置                           | v9.0       |
| `GraphTemplateSelector` | 模板     | 根据意图选择预定义模板                       | Nacos 模板配置                             | v9.0       |
| `DynamicOrchestrator`   | 核心     | 调用 LLM 生成编排计划，检索案例，含降级逻辑  | Case Library, ChatModel, ResilienceManager | v10.0 升级 |
| `PlanValidator`         | **新增** | 校验 LLM 生成的计划（拓扑、白名单、变量）    | Nacos AgentCard 列表                       | v10.0      |
| `GraphBuilder`          | 核心     | 将计划/模板转换为 StateGraph，支持 INTERRUPT | Spring AI Graph Core                       | v9.0       |
| `ChatModelRouter`       | 路由     | 根据复杂度选择模型实例（加权轮询）           | 多 ChatModel Bean                          | v9.0       |
| `A2ARouter`             | 路由     | 通过 Nacos 服务发现调用子 Agent，含断路器    | Nacos A2A, Resilience4j                    | v10.0 升级 |
| `QualityAssessor`       | 评估     | 执行后评估结果质量，触发案例入库             | LLM, Case Library                          | v9.0       |
| `Adversarial Assessor`  | **新增** | 案例入库前的多样性/健康度审查                | LLM                                        | v10.0      |
| `ResilienceManager`     | **新增** | 管理降级策略、断路器、信号量                 | Resilience4j, Redis                        | v10.0      |
| `ConversationManager`   | 记忆     | 管理会话短期记忆（Redis）与历史存档（PG）    | Redis, PostgreSQL                          | v9.0       |
| `HybridOrchestrator`    | 编排     | 统一入口，根据模式调用模板或动态执行器       | 以上所有                                   | v9.0       |

#### 3.1.2 Commander 内部类图

```
┌──────────────────────────────────────────────────────┐
│                  CommanderAgent                      │
├──────────────────────────────────────────────────────┤
│ - hybridOrchestrator: HybridOrchestrator             │
│ - conversationManager: ConversationManager           │
│ - resilienceManager: ResilienceManager               │
│ + executeStream(request): Flux<ServerSentEvent>      │
│ + handleInterruptResume(xid, userResponse): void     │
└────────────────────────┬─────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│                HybridOrchestrator                    │
├──────────────────────────────────────────────────────┤
│ - taskAnalyzer: TaskFeatureAnalyzer                  │
│ - templateExecutor: TemplateExecutor                 │
│ - dynamicOrchestrator: DynamicOrchestrator           │
│ - planValidator: PlanValidator                       │
│ + execute(request): Flux<Response>                   │
│ + decideMode(intent): ExecutionMode                  │
└────────────────────────┬─────────────────────────────┘
                         │
          ┌──────────────┴──────────────┐
          ▼                              ▼
┌─────────────────────┐    ┌──────────────────────────┐
│  TemplateExecutor   │    │  DynamicOrchestrator     │
├─────────────────────┤    ├──────────────────────────┤
│ + execute(template) │    │ - caseLibrary: Feign     │
│ + validate(step)    │    │ - chatModel: ChatModel   │
└─────────────────────┘    │ - resilienceMgr          │
                           │ + execute(input): Plan   │
                           │ + retrieveCases(input)   │
                           │ + generatePlan(input)    │
                           └────────────┬─────────────┘
                                        │
                                        ▼
                           ┌──────────────────────────┐
                           │     PlanValidator        │
                           ├──────────────────────────┤
                           │ - nacosRegistry          │
                           │ + validate(plan): Result │
                           │ + hasCycle(steps): bool  │
                           │ + checkWhitelist(steps)  │
                           │ + checkRefs(steps)       │
                           └──────────────────────────┘
```

#### 3.1.3 `PlanValidator` 完整实现

```java
@Service
public class PlanValidator {

    private final NacosA2aRegistry registry;
    private final ResilienceManager resilienceManager;
    private final MetricsService metricsService;

    /**
     * 对LLM生成的编排计划进行三重校验
     */
    public ValidationResult validate(OrchestrationPlan plan) {
        List<String> errors = new ArrayList<>();
        
        // 校验1: 拓扑排序检测循环依赖
        if (hasCycle(plan.getSteps())) {
            errors.add("PLAN_CYCLE_DETECTED: 编排计划存在循环依赖，拓扑排序失败");
            metricsService.increment("plan.validation.cycle.detected");
        }
        
        // 校验2: Agent白名单校验
        Set<String> availableAgents = registry.getAllAgentNames();
        for (Step step : plan.getSteps()) {
            if (step.getType() == StepType.A2A_DELEGATE) {
                if (!availableAgents.contains(step.getAgent())) {
                    errors.add("AGENT_NOT_FOUND: 步骤" + step.getId() + 
                               "引用的Agent '" + step.getAgent() + "' 不在注册中心");
                    metricsService.increment("plan.validation.agent.not_found");
                }
            }
        }
        
        // 校验3: 变量引用校验
        Set<String> validRefs = plan.getSteps().stream()
            .map(s -> s.getId() + ".output")
            .collect(Collectors.toSet());
        for (Step step : plan.getSteps()) {
            List<String> refs = extractReferences(step.getInput());
            refs.removeAll(validRefs);
            if (!refs.isEmpty()) {
                errors.add("INVALID_REF: 步骤" + step.getId() + 
                           "引用了不存在的输出: " + refs);
                metricsService.increment("plan.validation.invalid.ref");
            }
        }
        
        // 校验失败处理
        if (!errors.isEmpty()) {
            ValidationResult failed = ValidationResult.failed(errors);
            // 记录失败事件，用于分析LLM行为模式
            resilienceManager.recordPlanValidationFailure(plan, errors);
            log.warn("编排计划校验失败: xid={}, errors={}", 
                     RootContext.getXID(), errors);
            return failed;
        }
        
        metricsService.increment("plan.validation.success");
        return ValidationResult.success();
    }
    
    /**
     * 使用DFS检测步骤依赖图中的环
     */
    private boolean hasCycle(List<Step> steps) {
        Map<String, List<String>> graph = new HashMap<>();
        for (Step step : steps) {
            graph.putIfAbsent(step.getId(), new ArrayList<>());
            for (String dep : step.getDependsOn()) {
                graph.get(step.getId()).add(dep);
            }
        }
        
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (dfsHasCycle(node, graph, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean dfsHasCycle(String node, Map<String, List<String>> graph,
                                 Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) return true;
        if (visited.contains(node)) return false;
        
        visited.add(node);
        recursionStack.add(node);
        
        for (String neighbor : graph.getOrDefault(node, List.of())) {
            if (dfsHasCycle(neighbor, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
}
```

#### 3.1.4 `ResilienceManager` 完整实现

```java
@Component
public class ResilienceManager {
    
    // 动态编排并发信号量 (从Nacos动态加载)
    @Value("${resilience.orchestration.max-concurrent:50}")
    private int maxConcurrent;
    private Semaphore dynamicOrchSemaphore;
    
    // 子Agent断路器缓存
    private final Map<String, CircuitBreaker> agentBreakers = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry breakerRegistry;
    
    // Chroma专用断路器
    private final CircuitBreaker chromaBreaker;
    
    // LLM API限流器
    private final RateLimiter llmRateLimiter;
    
    @PostConstruct
    public void init() {
        this.dynamicOrchSemaphore = new Semaphore(maxConcurrent);
    }
    
    /**
     * 动态编排入口：信号量控制 + 排队
     */
    public boolean tryAcquireOrchestrationSlot(long timeoutMs) {
        try {
            return dynamicOrchSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    public void releaseOrchestrationSlot() {
        dynamicOrchSemaphore.release();
    }
    
    /**
     * 带降级的执行器：主逻辑 + 降级逻辑 + 断路器保护
     */
    public <T> T executeWithCircuitBreaker(
            Supplier<T> primaryAction,
            Supplier<T> fallbackAction,
            String breakerName) {
        
        CircuitBreaker breaker = agentBreakers.computeIfAbsent(
            breakerName, 
            name -> breakerRegistry.circuitBreaker(name)
        );
        
        return breaker.decorateSupplier(primaryAction)
            .recover(throwable -> {
                log.warn("断路器触发降级: breaker={}, reason={}", 
                         breakerName, throwable.getMessage());
                return fallbackAction.get();
            }).get();
    }
    
    /**
     * LLM API 限流调用
     */
    public <T> T executeWithRateLimit(Supplier<T> action) {
        return RateLimiter.decorateSupplier(llmRateLimiter, action).get();
    }
    
    /**
     * 记录编排计划校验失败事件 (用于分析LLM行为)
     */
    public void recordPlanValidationFailure(OrchestrationPlan plan, List<String> errors) {
        // 存入Redis，供后续分析
        String key = "plan:validation:failure:" + System.currentTimeMillis();
        Map<String, Object> event = Map.of(
            "plan", plan,
            "errors", errors,
            "timestamp", Instant.now().toString()
        );
        redisTemplate.opsForValue().set(key, event, Duration.ofHours(24));
    }
}
```

#### 3.1.5 `Adversarial Assessor` 完整实现

```java
@Component
public class AdversarialAssessor {
    
    private final ChatModel criticModel; // 使用 reasoningModel
    
    /**
     * 对高质量案例进行对抗评估，防止"回音壁效应"
     */
    public AdversarialResult assess(ExecutionCase caseRecord) {
        
        String prompt = """
            你是一个严格的系统审查员。请以挑剔的眼光审查以下AI编排执行案例：
            
            用户需求：%s
            执行步骤：%s
            执行结果：%s
            
            请回答：
            1. 步骤序列是否存在冗余？(是否有可以合并或跳过的步骤)
            2. 是否存在逻辑矛盾？(步骤顺序是否符合业务常识)
            3. 是否存在幻觉性操作？(引用了不存在的资源或Agent)
            4. 整体健康度评分 (1-100)
            
            以JSON格式返回：{"redundant": bool, "contradiction": bool, 
            "hallucination": bool, "health_score": int, "comment": "..."}
            """.formatted(
                caseRecord.getUserInput(),
                caseRecord.getOrchestrationPlan().toString(),
                caseRecord.getResultSummary()
            );
        
        String response = criticModel.call(new Prompt(prompt))
            .getResult().getOutput().getText();
        
        AdversarialResult result = parseResponse(response);
        
        // 如果健康度低于60，标记为"毒案例"
        if (result.getHealthScore() < 60) {
            result.setToxic(true);
            log.warn("检测到潜在毒案例: caseId={}, healthScore={}", 
                     caseRecord.getCaseId(), result.getHealthScore());
        }
        
        return result;
    }
}
```

---

### 3.2 子 Agent 通用设计

#### 3.2.1 子 Agent 标准架构

每个子 Agent 遵循以下标准架构：

```
┌─────────────────────────────────────────────────────────┐
│                    子 Agent 微服务                       │
├─────────────────────────────────────────────────────────┤
│  启动类：@SpringBootApplication + @EnableA2aAgent        │
├─────────────────────────────────────────────────────────┤
│  配置层                                                  │
│  ├── application.yml (本地配置)                          │
│  ├── Nacos 远程配置 (MCP配置、模型配置)                   │
│  └── Seata 客户端配置                                    │
├─────────────────────────────────────────────────────────┤
│  A2A 接口层                                              │
│  ├── AgentCard 注册 (名称、技能、示例)                    │
│  ├── /api/a2a/message/stream (流式调用)                  │
│  ├── /api/a2a/rollback (补偿接口)                        │
│  └── /api/a2a/health (健康检查)                          │
├─────────────────────────────────────────────────────────┤
│  业务逻辑层                                              │
│  ├── 工具类 (OCR、数据分析、文档生成等)                   │
│  ├── MCP 工具 (通过 RemoteMcpToolProvider 集成)          │
│  └── Command 返回 (REQUEST_CREDENTIAL, REQUEST_CONFIRM)   │
├─────────────────────────────────────────────────────────┤
│  数据访问层                                              │
│  ├── 本地 PostgreSQL (可选)                              │
│  ├── Redis 缓存                                          │
│  └── Memory Service Feign Client                        │
└─────────────────────────────────────────────────────────┘
```

#### 3.2.2 子 Agent 生命周期

```
1. 初始化 (Init)
   ├── 加载本地配置 (application.yml)
   ├── 连接 Nacos (注册服务、拉取远程配置)
   ├── 连接 Redis
   ├── 连接 Seata TC (注册 RM)
   ├── 初始化 MCP Client (SafeSyncMcpToolCallbackProvider 懒加载)
   └── 注册 AgentCard 到 Nacos A2A

2. 运行中 (Running)
   ├── 接收 A2A 请求 (解析 threadId, xid, 输入参数)
   ├── 提取上下文 (从 metadata 获取 recentMessages, userProfile, tokens)
   ├── 执行本地工具
   │   ├── 需要用户授权 → 返回 Command (状态码200，内容含中断标记)
   │   ├── 执行成功 → 返回结果
   │   └── 执行失败 → 抛异常触发 Seata 回滚
   └── 上报健康状态 (/actuator/health)

3. 补偿 (Compensation)
   ├── 接收 /api/a2a/rollback (含 XID, branchId)
   ├── 基于 XID + branchId 幂等清理
   └── 返回 Success

4. 关闭 (Shutdown)
   ├── 注销 AgentCard
   ├── 关闭 MCP Client
   └── 关闭数据库连接
```

#### 3.2.3 子 Agent 标准代码模板

```java
@SpringBootApplication
@EnableA2aAgent
@EnableConfigurationProperties(AgentConfig.class)
@EnableFeignClients(basePackages = "com.enterprise.memory")
public class DocumentAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentAgentApplication.class, args);
    }
}

// ============ A2A 接口 ============
@RestController
@RequestMapping("/api/a2a")
public class AgentA2AController {
    
    @PostMapping("/message/stream")
    @GlobalTransactional(timeoutMills = 1800000)
    public Flux<ServerSentEvent> handleMessage(@RequestBody A2ARequest request) {
        // 1. 提取上下文
        String threadId = request.getMetadata().getThreadId();
        String xid = request.getMetadata().getXid();
        List<Message> recentMessages = request.getMetadata().getRecentMessages();
        Map<String, String> tokens = request.getMetadata().getCredentialTokens();
        
        // 2. 执行业务逻辑
        return agentService.execute(request.getMessage(), tokens)
            .map(result -> ServerSentEvent.builder()
                .data(result)
                .build());
    }
    
    @PostMapping("/rollback")
    public ResponseEntity<String> rollback(@RequestBody RollbackRequest request) {
        // 幂等性：基于 xid + branchId 去重
        boolean success = compensationService.compensate(
            request.getXid(), 
            request.getBranchId()
        );
        return success ? ResponseEntity.ok("Success") 
                       : ResponseEntity.ok("Success"); // 幂等：已回滚也返回成功
    }
}

// ============ 工具类 ============
@Component
public class DocumentGenerationTools {
    
    private final ChatModel reasoningModel;
    
    public DocumentGenerationTools(@Qualifier("reasoningModel") ChatModel model) {
        this.reasoningModel = model;
    }
    
    @Tool(description = "生成正式文档")
    public String generateDocument(
        @ToolParam(description = "文档标题") String title,
        @ToolParam(description = "文档内容大纲") String outline,
        @ToolParam(description = "参考数据") String referenceData) {
        
        // 如果需要用户确认
        if (requiresUserApproval(title)) {
            return Command.builder()
                .type("REQUEST_CREDENTIAL")
                .scope("document_generation")
                .message("请确认是否生成文档：" + title)
                .build().toJson();
        }
        
        // 正常生成文档
        String prompt = "生成文档：" + title + "\n大纲：" + outline + "\n数据：" + referenceData;
        return reasoningModel.call(new Prompt(prompt))
            .getResult().getOutput().getText();
    }
}
```

---

### 3.3 Memory Service（独立服务）

#### 3.3.1 API 设计

```java
@FeignClient(name = "memory-service", path = "/api/memory")
public interface MemoryServiceClient {
    
    // 用户画像
    @GetMapping("/profile/{userId}")
    ProfileDTO getProfile(@PathVariable String userId);
    
    @PutMapping("/profile/{userId}")
    void updateProfile(@PathVariable String userId, @RequestBody ProfileDTO dto);
    
    // 行为记录
    @PostMapping("/behavior")
    void recordBehavior(@RequestBody BehaviorDTO dto);
    
    @GetMapping("/behavior/{userId}")
    List<BehaviorDTO> getBehaviors(
        @PathVariable String userId,
        @RequestParam String startTime,
        @RequestParam String endTime
    );
    
    // 知识检索 (RAG)
    @PostMapping("/knowledge/search")
    List<KnowledgeChunk> searchKnowledge(@RequestBody SearchRequest req);
    
    @PostMapping("/knowledge/ingest")
    void ingestKnowledge(@RequestBody KnowledgeDocument doc);
    
    // 用户偏好
    @GetMapping("/preference/{userId}")
    Map<String, String> getPreferences(@PathVariable String userId);
    
    @PutMapping("/preference/{userId}")
    void updatePreference(@PathVariable String userId, @RequestBody Map<String, String> prefs);
}
```

#### 3.3.2 数据模型

```sql
-- 用户画像 (JSONB 灵活存储)
CREATE TABLE user_profile (
    user_id VARCHAR(64) PRIMARY KEY,
    profile_data JSONB NOT NULL DEFAULT '{}',
    -- profile_data 示例:
    -- {
    --   "industry": "finance",
    --   "role": "analyst",
    --   "expertise": ["stock", "bond"],
    --   "preferred_language": "zh-CN",
    --   "risk_tolerance": "medium",
    --   "membership": "vip"
    -- }
    embedding VECTOR(1536),  -- pgvector 扩展，用于相似用户检索
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 用户行为事件流 (分区表，按日分区)
CREATE TABLE user_behavior (
    event_id BIGSERIAL,
    user_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,   -- click, invoke, feedback, view
    event_data JSONB NOT NULL,
    session_id VARCHAR(128),
    created_at TIMESTAMP DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- 知识库 (Chroma 存储向量，PG 存储元数据)
CREATE TABLE knowledge_base (
    doc_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    title VARCHAR(255),
    content TEXT,
    source VARCHAR(128),
    chroma_collection VARCHAR(64),
    chroma_id VARCHAR(64),
    tags TEXT[],
    created_at TIMESTAMP DEFAULT NOW()
);

-- 用户偏好 (键值对)
CREATE TABLE user_preference (
    user_id VARCHAR(64) NOT NULL,
    pref_key VARCHAR(128) NOT NULL,
    pref_value TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, pref_key)
);
```

---

### 3.4 Case Library（独立服务）

#### 3.4.1 API 设计

```java
@RestController
@RequestMapping("/api/case")
public class CaseController {
    
    // 检索相似案例 (向量相似度 + 质量分过滤)
    @PostMapping("/search")
    public List<CaseDTO> search(@RequestBody SearchRequest req) {
        // req: { query: "...", scenario: "...", topK: 3, minQuality: 70 }
    }
    
    // 保存案例 (含步骤明细)
    @PostMapping("/save")
    public SaveResult save(@RequestBody CaseSaveRequest req) {
        // req: { case: {...}, steps: [...], dataFlows: [...] }
    }
    
    // 获取案例详情
    @GetMapping("/{caseId}")
    public CaseDetailDTO getDetail(@PathVariable String caseId);
    
    // 获取案例质量评估
    @GetMapping("/{caseId}/quality")
    public QualityAssessment getQuality(@PathVariable String caseId);
    
    // 用户反馈
    @PostMapping("/{caseId}/feedback")
    public void feedback(@PathVariable String caseId, @RequestBody FeedbackDTO fb);
    
    // 案例老化处理 (内部Job调用)
    @PostMapping("/internal/aging")
    public void performAging();
    
    // 种子案例注入 (运维接口)
    @PostMapping("/internal/seed")
    public void injectSeedCases(@RequestBody List<CaseSeedDTO> seeds);
}
```

#### 3.4.2 案例质量评分算法 (完整版)

```
算法输入: ExecutionResult (每个步骤输出、耗时、状态)
算法输出: 最终得分 Q + 质量标签

步骤1: 规则评估 (Rule Assessment)
    score_R = 100
    score_R -= 10  if any step timeout
    score_R -= 20  if any step exception
    score_R -= 5   if any step output format mismatch
    score_R -= 15  if total_duration > expected_duration * 2
    return max(0, score_R)

步骤2: LLM 评估 (LLM Assessment)
    Prompt: "评估以下AI任务执行的质量，从以下维度评分：
             - 任务完成度 (0-30)
             - 结果正确性 (0-30)
             - 输出完整性 (0-20)
             - 执行效率 (0-20)
             用户需求: {user_input}
             执行结果: {result_summary}"
    score_L = sum of 4 dimensions
    confidence = LLM返回的置信度

步骤3: 对抗评估 (Adversarial Assessment) ← v10.0新增
    仅当 (score_R + score_L) / 2 ≥ 85 时触发
    调用 AdversarialAssessor.assess(case)
    penalty = adversarial_result.isToxic() ? 0.8 : 1.0

步骤4: 人工评估 (Human Assessment, 可选)
    触发条件: score_R < 30 或 confidence < 0.7
    或 score_R + score_L 在 40-75 之间
    score_H = 人工评分 (0-100)

步骤5: 最终得分计算
    if human_score exists:
        Q = 0.4 * score_R + 0.3 * score_L + 0.2 * score_H + 0.1 * health_score
    else:
        Q = 0.4 * score_R + 0.4 * score_L + 0.2 * health_score
    Q = Q * penalty  (对抗评估惩罚)

步骤6: 质量标签
    Q >= 90  → GOLD (黄金案例, 永不删除, 权重1.5x)
    Q >= 85  → HIGH
    Q >= 70  → MEDIUM
    Q < 70   → LOW (定期清理)
    Q < 40   → TOXIC (立即标记, 不参与检索)
```

---

### 3.5 Credential Service（独立服务）

#### 3.5.1 完整数据模型

```sql
-- 凭证主表
CREATE TABLE credential (
    credential_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(128) NOT NULL,          -- 权限范围
    -- scope 示例: financial_read, financial_write, knowledge_create, 
    --           data_export, user_management, system_config
    granted BOOLEAN DEFAULT FALSE,
    token VARCHAR(512),                   -- JWT 凭证令牌
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    granted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    INDEX idx_user_scope (user_id, scope)
);

-- 授权审批记录
CREATE TABLE credential_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    credential_id VARCHAR(64) REFERENCES credential(credential_id),
    user_id VARCHAR(64) NOT NULL,
    action VARCHAR(20) NOT NULL,          -- REQUEST, APPROVE, REJECT, REVOKE
    action_data JSONB,                    -- 审批时的附加上下文
    ip_address VARCHAR(45),
    user_agent TEXT,
    xid VARCHAR(128),                     -- 关联的全局事务ID
    created_at TIMESTAMP DEFAULT NOW()
);

-- 凭证与Agent的授权映射
CREATE TABLE credential_agent_allowlist (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(128) NOT NULL,
    agent_name VARCHAR(255) NOT NULL,     -- 允许使用此凭证的Agent名称
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (scope, agent_name)
);
```

#### 3.5.2 与中断机制的集成流程

```
1. 子Agent返回 REQUEST_CREDENTIAL Command
   Command = {
     type: "REQUEST_CREDENTIAL",
     scope: "financial_read",
     reason: "需要读取财务数据进行分析",
     required_scopes: ["financial_read", "financial_write"]  // 可批量申请
   }

2. Commander 收到 Command
   ├── 提取所有 required_scopes
   ├── 调用 CredentialService.check(userId, scopes)
   │   ├── 全部已授权 → 直接注入Token，继续执行
   │   └── 有未授权 → 进入中断流程

3. 中断流程
   ├── Commander 挂起 Seata 全局事务
   ├── 保存中断上下文到 Redis (interrupt:{XID})
   ├── 生成统一授权页面URL，推送给用户
   │   URL: /auth/approve?scopes=financial_read,financial_write&xid=xxx

4. 用户授权
   ├── 用户进入授权页面，查看权限说明
   ├── 用户勾选批准的权限 (可部分批准)
   ├── 提交 → CredentialService.approve(userId, scopes, xid)

5. Credential Service 处理
   ├── 为每个批准的scope生成JWT Token
   ├── Token中包含: { userId, scope, agent_whitelist, exp }
   ├── 记录审计日志 (credential_audit)
   └── 返回 Token 列表给 Commander

6. Commander 恢复执行
   ├── 恢复 Seata 全局事务
   ├── 将Token注入到后续步骤的metadata中
   ├── Token注入时检查Agent白名单:
   │   └── 如果某个Token的白名单不包含当前调用的Agent → 不注入此Token
   └── 继续执行后续步骤
```

---

### 3.6 ConversationManager 完整实现

```java
@Service
public class ConversationManager {
    
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    
    @Value("${conversation.short-term.max-messages:20}")
    private int maxMessages;
    
    @Value("${conversation.short-term.ttl-minutes:30}")
    private int shortTermTtl;
    
    /**
     * 添加消息到短期记忆 (Redis List)
     */
    public void addMessage(String threadId, String role, String content) {
        String key = "conv:" + threadId;
        Map<String, String> message = Map.of(
            "role", role,
            "content", truncate(content, 4000),  // 限制单条消息长度
            "timestamp", Instant.now().toString()
        );
        
        // 写入Redis List (右侧追加)
        redisTemplate.opsForList().rightPush(key, 
            objectMapper.writeValueAsString(message));
        
        // 裁剪：只保留最近N条
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > maxMessages) {
            redisTemplate.opsForList().trim(key, -maxMessages, -1);
        }
        
        // 刷新TTL
        redisTemplate.expire(key, Duration.ofMinutes(shortTermTtl));
        
        // 异步归档到 PostgreSQL
        eventPublisher.publishEvent(new ConversationArchiveEvent(
            threadId, role, content
        ));
    }
    
    /**
     * 获取最近N条消息 (用于A2A上下文)
     */
    public List<Map<String, String>> getRecentMessages(String threadId, int n) {
        String key = "conv:" + threadId;
        List<String> list = redisTemplate.opsForList()
            .range(key, -n, -1);
        
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        
        return list.stream()
            .map(json -> {
                try {
                    return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
                } catch (Exception e) {
                    return Map.of("role", "system", "content", "[解析失败]");
                }
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 归档对话到 PostgreSQL (异步监听器)
     */
    @Async
    @EventListener
    public void handleArchiveEvent(ConversationArchiveEvent event) {
        jdbcTemplate.update(
            "INSERT INTO conversation_history (thread_id, role, content) VALUES (?, ?, ?)",
            event.getThreadId(), event.getRole(), event.getContent()
        );
    }
    
    private String truncate(String content, int maxLength) {
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...[truncated]";
    }
}
```

---

## 四、混合执行模式

### 4.1 模式决策树（完整版）

```
用户输入 → API Gateway → Commander
    │
    ▼
Step 1: 租户级别强制规则检查
    ├── 租户配置了"强制模板模式" → 模板模式
    └── 继续
    │
    ▼
Step 2: 匹配硬规则 (从Nacos加载)
    ├── 场景匹配: 财务审计、合规审查、合同生成、医疗诊断 → 模板模式
    └── 继续
    │
    ▼
Step 3: 复杂度预评估
    ├── 关键词分析: 包含"分析"、"比较"、"优化"、"建议" → 复杂度+2
    ├── 句子结构: 多层级问题、条件句 → 复杂度+1
    ├── 历史数据: 用户历史交互复杂度均值 > 5 → 复杂度+1
    └── 复杂度 > 5 → 动态模式
    │
    ▼
Step 4: LLM 轻量分类 (qwen-turbo)
    ├── Prompt: "判断用户需求是否属于: 创意生成、开放研究、复杂推理、多步骤规划"
    ├── 是 → 动态模式
    └── 否 → 模板模式 (默认)
    │
    ▼
Step 5: 最终决策
    ├── 模板模式 → GraphTemplateSelector 加载模板 → TemplateExecutor
    └── 动态模式 → DynamicOrchestrator → PlanValidator → GraphBuilder
```

### 4.2 模板模式详细设计

#### 4.2.1 Graph 模板存储（Nacos YAML）

```yaml
# commander-graph-templates.yaml
templates:
  - template-id: financial-audit
    name: "财务审计"
    mode: STRICT
    trigger:
      scenarios: ["FINANCIAL_AUDIT", "COMPLIANCE_CHECK"]
      keywords: ["审计", "合规", "财务检查"]
    steps:
      - id: step1_fetch
        type: A2A_DELEGATE
        agent: data-analysis-agent
        task: FETCH_FINANCIAL_DATA
        mandatory: true
        validation:
          not_null: ["data"]
          format: "json"
        timeout_ms: 30000
        retry: 2
        
      - id: step2_review
        type: INTERRUPT
        question: "数据已获取，是否继续审计分析？"
        options: ["同意继续", "拒绝并终止"]
        on_agree: step3_analyze
        on_reject: rollback
        
      - id: step3_analyze
        type: A2A_DELEGATE
        agent: data-analysis-agent
        task: AUDIT_ANALYSIS
        input:
          data: "{step1_fetch.output.data}"
          rules: "standard_compliance_v2"
        mandatory: true
        timeout_ms: 60000
        
      - id: step4_report
        type: A2A_DELEGATE
        agent: document-agent
        task: GENERATE_AUDIT_REPORT
        input:
          analysis: "{step3_analyze.output}"
          template: "audit_report_template_v1"
        mandatory: true
        
    rollback:
      enabled: true
      savepoint: step1_fetch_end
      
    quality:
      expected_duration_ms: 120000
      min_acceptable_score: 70

  - template-id: quick-qa
    name: "快速问答"
    mode: FAST
    trigger:
      scenarios: ["QA", "INFORMATION_LOOKUP"]
      keywords: ["什么是", "如何", "解释"]
    steps:
      - id: step1_search
        type: LLM_CALL
        model: fast-model
        task: ANSWER_QUESTION
        timeout_ms: 5000
    rollback:
      enabled: false
```

#### 4.2.2 TemplateExecutor 实现

```java
@Component
public class TemplateExecutor {
    
    private final GraphBuilder graphBuilder;
    private final A2ARouter a2aRouter;
    private final ChatModelRouter modelRouter;
    
    public Flux<StepResult> execute(GraphTemplate template, UserRequest request) {
        
        // 1. 构建 StateGraph
        StateGraph graph = graphBuilder.buildFromTemplate(template);
        
        // 2. 按顺序执行步骤
        return Flux.fromIterable(template.getSteps())
            .flatMap(step -> {
                // 2.1 执行前校验
                if (step.getValidation() != null) {
                    validateInput(step, graph.getState());
                }
                
                // 2.2 根据类型执行
                return switch (step.getType()) {
                    case A2A_DELEGATE -> executeA2AStep(step, graph);
                    case INTERRUPT -> executeInterruptStep(step, graph);
                    case LLM_CALL -> executeLLMStep(step, graph);
                };
            })
            .doOnComplete(() -> {
                // 3. 保存检查点
                graph.saveCheckpoint();
            });
    }
    
    private Flux<StepResult> executeA2AStep(Step step, StateGraph graph) {
        return a2aRouter.callWithRetry(
            step.getAgent(),
            step.getTask(),
            step.resolveInput(graph.getState()),
            step.getTimeoutMs(),
            step.getRetry()
        );
    }
}
```

### 4.3 动态模式详细设计

#### 4.3.1 LLM 编排 Prompt 模板（完整版）

```java
public class OrchestrationPromptBuilder {
    
    public String buildPrompt(String userInput, List<Case> similarCases, 
                              Set<String> availableAgents) {
        
        StringBuilder sb = new StringBuilder();
        
        // 系统角色
        sb.append("""
            你是一个智能任务编排专家。给定用户需求，你需要生成一个JSON格式的执行计划。
            
            约束条件：
            1. 每个步骤必须指定id、type、agent(如适用)、task、input
            2. 步骤间通过 {step_id.output} 引用前序步骤的输出
            3. 如果需要用户授权，插入 type: "interrupt" 的步骤
            4. 不要引用不存在的步骤输出
            5. 不要使用不在可用列表中的Agent
            """);
        
        // 可用Agent列表
        sb.append("\n\n可用Agent列表：\n");
        for (String agent : availableAgents) {
            sb.append("- ").append(agent).append("\n");
        }
        
        // 相似成功案例 (Few-shot)
        if (similarCases != null && !similarCases.isEmpty()) {
            sb.append("\n\n参考成功案例：\n");
            for (int i = 0; i < similarCases.size(); i++) {
                Case c = similarCases.get(i);
                sb.append("案例").append(i+1).append(": ")
                  .append(c.getOrchestrationPlan().toString())
                  .append("\n");
            }
        } else {
            // 降级：无案例时的提示
            sb.append("\n\n注意：当前无相似案例参考，请根据最佳实践自行规划。\n");
        }
        
        // 用户需求
        sb.append("\n\n用户需求：").append(userInput);
        
        // 输出格式要求
        sb.append("""
            
            输出JSON格式：
            {
              "plan_id": "唯一标识",
              "description": "计划简述",
              "steps": [
                {
                  "id": "step1",
                  "type": "A2A_DELEGATE | INTERRUPT | LLM_CALL",
                  "agent": "agent-name (type=A2A_DELEGATE时需要)",
                  "task": "任务类型",
                  "input": {"key": "value或{stepX.output}"},
                  "depends_on": ["step_id"],  // 依赖的前置步骤
                  "mandatory": true
                }
              ],
              "on_reject": {"action": "rollback | skip | ask_user"}
            }
            """);
        
        return sb.toString();
    }
}
```

#### 4.3.2 降级编排策略

```java
@Component
public class DynamicOrchestrator {
    
    private final CaseLibraryClient caseLibrary;
    private final ChatModel chatModel;
    private final ResilienceManager resilienceManager;
    
    /**
     * 动态编排执行 (含降级逻辑)
     */
    public OrchestrationPlan orchestrate(UserRequest request) {
        
        // 1. 获取可用Agent列表 (从Nacos实时获取)
        Set<String> availableAgents = getAvailableAgents();
        
        // 2. 检索相似案例 (含降级)
        List<Case> similarCases = retrieveCasesWithFallback(request);
        
        // 3. 构建Prompt并生成计划
        String prompt = promptBuilder.build(
            request.getContent(), similarCases, availableAgents
        );
        
        // 4. 调用LLM (含限流)
        String llmResponse = resilienceManager.executeWithRateLimit(() -> 
            chatModel.call(new Prompt(prompt))
                .getResult().getOutput().getText()
        );
        
        // 5. 解析JSON
        OrchestrationPlan plan = parsePlan(llmResponse);
        
        // 6. 如果降级了，在plan中标记
        if (similarCases.isEmpty()) {
            plan.getMetadata().put("fallback", "case_library_unavailable");
        }
        
        return plan;
    }
    
    /**
     * 检索案例 (含超时降级)
     */
    private List<Case> retrieveCasesWithFallback(UserRequest request) {
        return resilienceManager.executeWithCircuitBreaker(
            // 主逻辑：调用Case Library
            () -> caseLibrary.search(new SearchRequest(
                request.getContent(), 
                request.getScenario(), 
                3,  // topK
                70  // minQuality
            )),
            // 降级逻辑：返回空列表
            () -> {
                log.warn("Case Library 不可用，降级为纯LLM编排");
                return List.of();
            },
            "case-library"
        );
    }
}
```

---

## 五、动态编排与自进化闭环

### 5.1 自进化闭环完整流程

```
┌─────────────────────────────────────────────────────────────────┐
│                      自进化闭环 (Self-Evolution Loop)             │
│                                                                   │
│  ┌──────────────┐                                                │
│  │  用户请求    │                                                │
│  └──────┬───────┘                                                │
│         ▼                                                        │
│  ┌──────────────┐    ┌──────────────────┐                       │
│  │  动态编排    │◄───│ 案例检索 (Few-shot)│                      │
│  └──────┬───────┘    └────────┬─────────┘                       │
│         │                     │                                   │
│         ▼                     │                                   │
│  ┌──────────────┐             │                                   │
│  │  计划校验    │             │                                   │
│  └──────┬───────┘             │                                   │
│         │                     │                                   │
│         ▼                     │                                   │
│  ┌──────────────┐             │                                   │
│  │  Graph 执行  │             │                                   │
│  └──────┬───────┘             │                                   │
│         │                     │                                   │
│         ▼                     │                                   │
│  ┌──────────────┐             │                                   │
│  │  质量评估    │             │                                   │
│  │  R + L + A   │             │                                   │
│  └──────┬───────┘             │                                   │
│         │                     │                                   │
│         ▼                     │                                   │
│  ┌──────────────┐    Q ≥ 85   │                                   │
│  │  案例入库    │─────────────┘                                   │
│  │  +向量化     │                                                 │
│  └──────────────┘                                                 │
│                                                                   │
│  定期学习任务 (每日凌晨3:00):                                     │
│  ┌──────────────────────────────────────────────────┐            │
│  │ 1. 扫描高质案例 (Q ≥ 90)                         │            │
│  │ 2. 提取编排模式 (聚类分析)                       │            │
│  │ 3. 更新 Few-shot 示例库                         │            │
│  │ 4. 通知编排模型有新的最佳实践                    │            │
│  └──────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 案例检索与匹配算法

```java
@Component
public class CaseMatcher {
    
    private final ChromaClient chromaClient;
    private final EmbeddingModel embeddingModel;
    
    /**
     * 检索最相似的案例
     */
    public List<Case> findSimilar(String userInput, String scenario, int topK, int minQuality) {
        
        // 1. 将用户输入向量化
        float[] queryEmbedding = embeddingModel.embed(userInput + " " + scenario);
        
        // 2. Chroma 向量检索
        QueryResult result = chromaClient.query(
            "case_vectors",
            queryEmbedding,
            topK * 3,  // 多取一些，用于后续过滤
            Map.of("quality_score", Map.of("$gte", minQuality))
        );
        
        // 3. 计算综合得分并排序
        List<CaseWithScore> scored = result.getResults().stream()
            .map(r -> {
                double similarity = cosineSimilarity(queryEmbedding, r.getEmbedding());
                double qualityNorm = r.getMetadata().get("quality_score") / 100.0;
                double score = similarity * 0.7 + qualityNorm * 0.3;
                return new CaseWithScore(r.getCase(), score);
            })
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(topK)
            .collect(Collectors.toList());
        
        return scored.stream()
            .map(CaseWithScore::getCase)
            .collect(Collectors.toList());
    }
}
```

### 5.3 案例库维护策略

#### 5.3.1 老化策略

```java
@Component
public class CaseAgingJob {
    
    /**
     * 每日凌晨执行案例老化
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void performAging() {
        
        // 1. 质量分衰减：超过6个月未使用的案例，质量分 * 0.95
        jdbcTemplate.update("""
            UPDATE execution_case 
            SET overall_score = overall_score * 0.95,
                active = CASE WHEN overall_score < 40 THEN FALSE ELSE active END
            WHERE last_matched_at < NOW() - INTERVAL '6 months'
            AND source != 'SEED'  -- 种子案例不过期
            AND quality_tag != 'GOLD'  -- 黄金案例不过期
        """);
        
        // 2. 清理低质量案例
        jdbcTemplate.update("""
            UPDATE execution_case 
            SET active = FALSE, deleted_at = NOW()
            WHERE overall_score < 40 
            AND match_count = 0 
            AND created_at < NOW() - INTERVAL '30 days'
            AND source != 'SEED'
        """);
        
        log.info("案例老化完成: 处理时间={}", Instant.now());
    }
}
```

#### 5.3.2 去重策略

```java
@Component
public class CaseDeduplicator {
    
    /**
     * 入库前检查是否有更高质量的重复案例
     */
    public DedupResult checkDuplicate(Case newCase) {
        
        // 1. 检索相似度 > 0.95 的已有案例
        List<Case> duplicates = chromaClient.query(
            "case_vectors",
            newCase.getEmbedding(),
            5,
            Map.of("quality_score", Map.of("$gte", 70))
        ).getResults().stream()
            .filter(r -> cosineSimilarity(newCase.getEmbedding(), r.getEmbedding()) > 0.95)
            .map(r -> r.getCase())
            .collect(Collectors.toList());
        
        // 2. 如果已有更高质量的案例，标记当前案例为被取代
        for (Case existing : duplicates) {
            if (existing.getOverallScore() >= newCase.getOverallScore()) {
                return DedupResult.duplicate(existing.getCaseId());
            } else {
                // 新案例质量更高，软删除旧案例
                jdbcTemplate.update(
                    "UPDATE execution_case SET active=FALSE, superseded_by=? WHERE case_id=?",
                    newCase.getCaseId(), existing.getCaseId()
                );
            }
        }
        
        return DedupResult.unique();
    }
}
```

---

## 六、分层记忆系统

### 6.1 三层架构详解

| 层级 | 名称     | 存储引擎            | 数据结构                                                     | 生命周期           | 管理者              | 访问模式             | 典型用途           |
| ---- | -------- | ------------------- | ------------------------------------------------------------ | ------------------ | ------------------- | -------------------- | ------------------ |
| L0   | 短期会话 | Redis List          | `conv:{threadId}` 存储最近20条消息                           | 30分钟 TTL         | ConversationManager | 按时间顺序读取       | 多轮对话上下文     |
| L1   | 对话存档 | PostgreSQL          | `conversation_history` 表                                    | 90天后归档到冷存储 | ConversationManager | 按 threadId 分页查询 | 审计、用户行为分析 |
| L1   | 用户记忆 | PostgreSQL + Chroma | `user_profile`, `user_behavior`, `user_preference`; Chroma 知识库 | 永久 (可手动删除)  | Memory Service      | 精确查询、向量检索   | 个性化、RAG        |
| L2   | 经验案例 | Chroma + PostgreSQL | `execution_case` 表 + 向量索引                               | 永久 (可清理低质)  | Case Library        | 相似检索 + 过滤      | 编排参考、自进化   |

### 6.2 子Agent获取上下文的方式

Commander 在调用子 Agent 的 A2A 请求中，将以下上下文放入 `metadata`：

```json
{
  "method": "message/stream",
  "params": {
    "metadata": {
      "threadId": "user123::session456",
      "xid": "192.168.1.1:8091:2034567890",
      "tenantId": "tenant_001",
      
      "recentMessages": [
        {"role": "user", "content": "帮我分析最近的财务数据"},
        {"role": "assistant", "content": "好的，我需要您授权访问财务系统"}
      ],
      
      "userProfile": {
        "industry": "finance",
        "role": "financial_analyst",
        "expertise": ["stock", "bond", "forex"],
        "preferred_language": "zh-CN",
        "risk_tolerance": "medium"
      },
      
      "credentialTokens": {
        "financial_read": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "data_export": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
      },
      
      "taskContext": {
        "scenario": "FINANCIAL_ANALYSIS",
        "complexity": "HIGH",
        "parentRequestId": "req_abc123"
      }
    },
    "message": {
      "role": "user",
      "content": "请分析Q3财务报表并生成报告"
    }
  }
}
```

**关键原则**：子 Agent 从 `metadata` 中提取所有需要的上下文，**不需要自行查询任何记忆服务**。这保证了上下文的完整性和一致性，避免了子Agent间的隐式依赖。

---

## 七、中断与分布式事务机制

### 7.1 Seata 集成架构

```
┌──────────────────────────────────────────────────────────────┐
│                    Seata 分布式事务架构                        │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────┐                                      │
│  │  Seata TC 集群       │  (3节点，Nacos注册)                 │
│  │  - 协调全局事务      │                                      │
│  │  - 存储模式: db      │  (PostgreSQL)                       │
│  │  - 端口: 8091        │                                      │
│  └──────────┬──────────┘                                      │
│             │                                                  │
│    ┌────────┴────────┐                                        │
│    │                  │                                        │
│    ▼                  ▼                                        │
│  ┌──────────────┐  ┌──────────────────────┐                   │
│  │  TM          │  │  RM (多个)            │                   │
│  │  (Commander) │  │  (子Agent微服务)      │                   │
│  │              │  │                      │                   │
│  │ @GlobalTrans │  │ @GlobalTransactional │                   │
│  │ actional     │  │ @TwoPhaseBusinessAction                 │
│  └──────────────┘  └──────────────────────┘                   │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 事务传播与分支注册

```java
// Commander 中 (TM)
@Service
public class OrchestrationExecutor {
    
    @GlobalTransactional(
        timeoutMills = 1800000,  // 30分钟
        name = "multi-step-orchestration"
    )
    public OrchestrationResult executeWithTransaction(
            String xid, 
            OrchestrationPlan plan,
            Map<String, String> credentialTokens) {
        
        // 设置当前线程的XID
        RootContext.bind(xid);
        
        try {
            StateGraph graph = graphBuilder.build(plan);
            
            for (Step step : plan.getSteps()) {
                StepResult result;
                
                if (step.getType() == StepType.A2A_DELEGATE) {
                    // Seata 会自动将XID传播到子Agent的调用中
                    result = a2aRouter.call(
                        step.getAgent(),
                        buildA2ARequest(step, graph.getState(), credentialTokens)
                    );
                    
                    // 检查是否需要中断
                    if (result.isCommand() && 
                        result.getCommand().getType().equals("REQUEST_CREDENTIAL")) {
                        // 挂起事务，等待用户授权
                        return suspendAndWait(plan, step, result.getCommand());
                    }
                }
                
                graph.updateState(step.getId(), result);
            }
            
            return OrchestrationResult.success(graph.getState());
            
        } finally {
            RootContext.unbind();
        }
    }
}
```

### 7.3 TCC 模式实现

```java
// 子 Agent 中 (RM) - 财务扣减示例
@Service
public class BalanceService {
    
    @TwoPhaseBusinessAction(
        name = "deductBalance",
        commitMethod = "commitDeduct",
        rollbackMethod = "rollbackDeduct"
    )
    public boolean tryDeduct(
            @BusinessActionContextParameter(paramName = "userId") String userId,
            @BusinessActionContextParameter(paramName = "amount") BigDecimal amount) {
        
        // TCC Try阶段：冻结资金
        int rows = jdbcTemplate.update(
            "UPDATE account SET frozen_balance = frozen_balance + ? " +
            "WHERE user_id = ? AND available_balance >= ?",
            amount, userId, amount
        );
        
        if (rows == 0) {
            throw new InsufficientBalanceException("余额不足");
        }
        
        // 记录冻结日志 (用于幂等)
        jdbcTemplate.update(
            "INSERT INTO freeze_log (xid, branch_id, user_id, amount, status) " +
            "VALUES (?, ?, ?, ?, 'FROZEN') " +
            "ON CONFLICT (xid, branch_id) DO NOTHING",  // 幂等保证
            RootContext.getXID(), 
            RootContext.getBranchId(),
            userId, 
            amount
        );
        
        return true;
    }
    
    public boolean commitDeduct(BusinessActionContext context) {
        String xid = context.getXid();
        long branchId = context.getBranchId();
        String userId = (String) context.getActionContext("userId");
        BigDecimal amount = new BigDecimal((String) context.getActionContext("amount"));
        
        // TCC Commit阶段：确认扣减
        // 幂等检查
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM freeze_log WHERE xid = ? AND branch_id = ?",
            String.class, xid, branchId
        );
        
        if ("CONFIRMED".equals(status)) {
            return true;  // 已确认，幂等返回
        }
        
        // 执行扣减
        jdbcTemplate.update(
            "UPDATE account SET available_balance = available_balance - ?, " +
            "frozen_balance = frozen_balance - ? " +
            "WHERE user_id = ?",
            amount, amount, userId
        );
        
        // 更新冻结日志状态
        jdbcTemplate.update(
            "UPDATE freeze_log SET status = 'CONFIRMED' " +
            "WHERE xid = ? AND branch_id = ?",
            xid, branchId
        );
        
        return true;
    }
    
    public boolean rollbackDeduct(BusinessActionContext context) {
        String xid = context.getXid();
        long branchId = context.getBranchId();
        String userId = (String) context.getActionContext("userId");
        BigDecimal amount = new BigDecimal((String) context.getActionContext("amount"));
        
        // TCC Rollback阶段：解冻资金
        // 幂等检查
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM freeze_log WHERE xid = ? AND branch_id = ?",
            String.class, xid, branchId
        );
        
        if ("ROLLED_BACK".equals(status) || status == null) {
            return true;  // 已回滚或未冻结，幂等返回
        }
        
        // 执行解冻
        jdbcTemplate.update(
            "UPDATE account SET frozen_balance = frozen_balance - ? " +
            "WHERE user_id = ?",
            amount, userId
        );
        
        // 更新冻结日志状态
        jdbcTemplate.update(
            "UPDATE freeze_log SET status = 'ROLLED_BACK' " +
            "WHERE xid = ? AND branch_id = ?",
            xid, branchId
        );
        
        return true;
    }
}
```

### 7.4 中断恢复完整流程

```java
@Service
public class InterruptHandler {
    
    private final StringRedisTemplate redisTemplate;
    private final SeataTCClient seataTCClient;
    
    /**
     * 挂起事务并等待用户响应
     */
    public InterruptResult suspendAndWait(String xid, Step step, Command command) {
        
        // 1. 挂起全局事务
        GlobalTransaction globalTx = GlobalTransactionContext.getCurrent();
        globalTx.suspend();
        
        // 2. 保存中断上下文到 Redis
        String key = "interrupt:" + xid;
        Map<String, String> context = Map.of(
            "xid", xid,
            "userId", getCurrentUserId(),
            "stepId", step.getId(),
            "command", command.toJson(),
            "status", "WAITING_USER",
            "suspendedAt", Instant.now().toString()
        );
        redisTemplate.opsForHash().putAll(key, context);
        redisTemplate.expire(key, Duration.ofMinutes(30));
        
        // 3. 推送授权请求给用户
        notificationService.pushAuthorizationRequest(
            getCurrentUserId(),
            command.getScopes(),
            xid
        );
        
        // 4. 启动定时刷新TTL任务 (防止用户长时间不响应)
        startTtlRefreshTask(xid);
        
        return InterruptResult.suspended(xid);
    }
    
    /**
     * 用户响应后恢复事务
     */
    public ResumeResult resumeAfterUserResponse(String xid, boolean approved, 
                                                  List<String> approvedScopes) {
        
        String key = "interrupt:" + xid;
        Map<Object, Object> context = redisTemplate.opsForHash().entries(key);
        
        if (context.isEmpty()) {
            // Redis中没有，查询Seata TC状态
            return resumeFromTCState(xid, approved, approvedScopes);
        }
        
        if (approved) {
            // 用户同意
            // 1. 获取凭证
            Map<String, String> tokens = credentialService.approve(
                context.get("userId"), approvedScopes, xid
            );
            
            // 2. 恢复全局事务
            GlobalTransaction globalTx = GlobalTransactionContext.reload(xid);
            globalTx.resume();
            
            // 3. 更新中断状态
            redisTemplate.opsForHash().put(key, "status", "RESUMED");
            
            // 4. 注入凭证，继续执行后续步骤
            return ResumeResult.resumed(tokens, context.get("stepId"));
            
        } else {
            // 用户拒绝
            // 1. 恢复事务并回滚
            GlobalTransaction globalTx = GlobalTransactionContext.reload(xid);
            globalTx.resume();
            globalTx.rollback();
            
            // 2. 清理Redis
            redisTemplate.delete(key);
            
            return ResumeResult.rolledBack();
        }
    }
    
    /**
     * Commander崩溃恢复：从Seata TC查询事务状态
     */
    private ResumeResult resumeFromTCState(String xid, boolean approved, 
                                            List<String> approvedScopes) {
        
        // 查询Seata TC
        GlobalStatus status = seataTCClient.getGlobalTransactionStatus(xid);
        
        switch (status) {
            case Begin:
            case CommitRetrying:
                // 事务仍在活跃，重建Redis Key并恢复
                rebuildInterruptContext(xid);
                return resumeAfterUserResponse(xid, approved, approvedScopes);
                
            case Rollbacked:
            case TimeoutRollbacked:
            case Finished:
                // 事务已结束
                return ResumeResult.alreadyFinished("操作已超时或已被取消，请重新发起请求");
                
            default:
                return ResumeResult.error("无法确定事务状态: " + status);
        }
    }
}
```

---

## 八、多模态与多模型配置

### 8.1 多 ChatModel 配置

```java
@Configuration
public class ChatModelConfiguration {
    
    @Value("${dashscope.api-key}")
    private String apiKey;
    
    // 视觉语言模型 (默认)
    @Bean("vlModel")
    @Primary
    public ChatModel vlChatModel() {
        DashScopeApi api = DashScopeApi.builder()
            .apiKey(apiKey)
            .modelName("qwen-vl-max")
            .build();
        return DashScopeChatModel.builder()
            .dashScopeApi(api)
            .defaultOptions(DashScopeChatOptions.builder()
                .withTemperature(0.7)
                .withMaxTokens(2000)
                .build())
            .build();
    }
    
    // 推理模型 (复杂任务)
    @Bean("reasoningModel")
    public ChatModel reasoningModel() {
        DashScopeApi api = DashScopeApi.builder()
            .apiKey(apiKey)
            .modelName("qwen-max")
            .build();
        return DashScopeChatModel.builder()
            .dashScopeApi(api)
            .defaultOptions(DashScopeChatOptions.builder()
                .withTemperature(0.3)  // 低温度，更确定性
                .withMaxTokens(4000)
                .build())
            .build();
    }
    
    // 快速模型 (简单任务)
    @Bean("fastModel")
    public ChatModel fastModel() {
        DashScopeApi api = DashScopeApi.builder()
            .apiKey(apiKey)
            .modelName("qwen-turbo")
            .build();
        return DashScopeChatModel.builder()
            .dashScopeApi(api)
            .defaultOptions(DashScopeChatOptions.builder()
                .withTemperature(0.5)
                .withMaxTokens(1000)
                .build())
            .build();
    }
    
    // 代码模型
    @Bean("codeModel")
    public ChatModel codeModel() {
        DashScopeApi api = DashScopeApi.builder()
            .apiKey(apiKey)
            .modelName("qwen-coder-plus")
            .build();
        return DashScopeChatModel.builder()
            .dashScopeApi(api)
            .build();
    }
}
```

### 8.2 模型路由策略

```java
@Component
public class ChatModelRouter {
    
    private final ChatModel fastModel;
    private final ChatModel vlModel;
    private final ChatModel reasoningModel;
    private final ChatModel codeModel;
    
    /**
     * 根据复杂度和场景选择模型
     */
    public ChatModel route(Complexity complexity, Scenario scenario) {
        return switch (complexity) {
            case LOW -> fastModel;
            case MEDIUM -> {
                if (scenario == Scenario.CODE_GENERATION) {
                    yield codeModel;
                } else if (scenario == Scenario.IMAGE_ANALYSIS) {
                    yield vlModel;
                } else {
                    yield reasoningModel;
                }
            }
            case HIGH -> reasoningModel;
            case CRITICAL -> reasoningModel;  // 关键任务使用最强模型
        };
    }
    
    /**
     * 加权轮询 (当同一复杂度有多个模型时)
     */
    public ChatModel weightedRoundRobin(Complexity complexity) {
        // 从Nacos加载模型权重配置
        // platform-model-routing.yaml:
        //   complexity.HIGH:
        //     - model: qwen-max, weight: 70
        //     - model: qwen-plus, weight: 30
        return loadBalancer.select(complexity);
    }
}
```

### 8.3 多模态消息构造

```java
@Component
public class MultimodalMessageBuilder {
    
    /**
     * 构造图文混合消息
     */
    public UserMessage buildImageTextMessage(String text, byte[] imageBytes, String mimeType) {
        Resource imageResource = new ByteArrayResource(imageBytes);
        Media media = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType(mimeType))
            .data(imageResource)
            .build();
        
        return UserMessage.builder()
            .text(text)
            .media(media)
            .build();
    }
    
    /**
     * 构造多图消息
     */
    public UserMessage buildMultiImageMessage(String text, List<ImageData> images) {
        UserMessage.Builder builder = UserMessage.builder().text(text);
        
        for (ImageData img : images) {
            Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(img.getMimeType()))
                .data(new ByteArrayResource(img.getBytes()))
                .build();
            builder.media(media);
        }
        
        return builder.build();
    }
    
    /**
     * 安全护栏：扫描多模态内容
     */
    public boolean scanMultimodalContent(UserMessage message) {
        // 1. 检查文本中的注入攻击
        if (ContentSafetyScanner.detectPromptInjection(message.getText())) {
            log.warn("检测到可能的提示注入攻击");
            return false;
        }
        
        // 2. 检查图片中的隐藏文本 (OCR + 安全扫描)
        for (Media media : message.getMedia()) {
            if (media.getMimeType().getType().equals("image")) {
                String ocrText = performOCR(media.getData());
                if (ContentSafetyScanner.detectPromptInjection(ocrText)) {
                    log.warn("检测到图片中的隐藏注入文本");
                    return false;
                }
            }
        }
        
        return true;
    }
}
```

---

## 九、MCP动态配置

### 9.1 配置结构与动态刷新

```yaml
# Nacos 配置: document-agent-mcp.yaml
mcp:
  servers:
    - name: baidu-map
      type: stdio
      command: npx.cmd  # Windows 使用 .cmd
      args: ["-y", "@baidumap/mcp-server-baidu-map"]
      env:
        BAIDU_MAP_API_KEY: ${BAIDU_MAP_API_KEY}
      enabled: false
      timeout_ms: 20000
      
    - name: weather-service
      type: streamable-http
      url: http://weather-service:8080/mcp
      enabled: true
      timeout_ms: 10000
      retry: 2
      
    - name: pdf-reader
      type: stdio
      command: python
      args: ["-m", "mcp_pdf_reader"]
      env:
        PDF_OCR_LANG: "chi_sim+eng"
      enabled: true
      timeout_ms: 30000
```

### 9.2 安全懒加载实现

```java
@Component
@RefreshScope
public class SafeMcpToolProvider {
    
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    
    @EventListener(ApplicationReadyEvent.class)
    public void lazyInit() {
        // 延迟初始化：避免启动时因MCP超时导致应用启动失败
        scheduler.schedule(() -> {
            try {
                initClients();
                initialized = true;
            } catch (Exception e) {
                log.error("MCP Client 初始化失败，将在首次调用时重试", e);
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 获取工具回调 (懒加载 + 重试)
     */
    @Override
    public ToolCallback[] getToolCallbacks() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    initClients();
                    initialized = true;
                }
            }
        }
        
        return clients.values().stream()
            .flatMap(client -> {
                try {
                    return client.listTools().stream();
                } catch (Exception e) {
                    log.error("获取MCP工具列表失败: {}", e.getMessage());
                    return Stream.empty();
                }
            })
            .map(tool -> ToolCallback.from(tool))
            .toArray(ToolCallback[]::new);
    }
}
```

---

## 十、数据存储设计

### 10.1 PostgreSQL 核心表结构

```sql
-- ========================================
-- 案例库
-- ========================================

CREATE TABLE execution_case (
    case_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_input TEXT NOT NULL,
    user_input_embedding VECTOR(1536),  -- pgvector 存储向量副本
    scenario VARCHAR(64),
    orchestration_plan JSONB NOT NULL,
    overall_score INT NOT NULL,
    rule_score INT,
    llm_score INT,
    human_score INT,
    adversarial_score INT,
    quality_tag VARCHAR(20),  -- GOLD, HIGH, MEDIUM, LOW, TOXIC
    confidence DECIMAL(3,2),
    total_duration_ms BIGINT,
    status VARCHAR(20),
    source VARCHAR(20) DEFAULT 'AUTO',  -- AUTO, SEED, MANUAL
    match_count INT DEFAULT 0,
    last_matched_at TIMESTAMP,
    active BOOLEAN DEFAULT TRUE,
    superseded_by VARCHAR(64),
    diversity_tags TEXT[],
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_case_tenant ON execution_case(tenant_id);
CREATE INDEX idx_case_scenario ON execution_case(scenario);
CREATE INDEX idx_case_quality ON execution_case(overall_score);
CREATE INDEX idx_case_active ON execution_case(active) WHERE active = TRUE;

-- ========================================
-- 步骤明细
-- ========================================

CREATE TABLE execution_step_detail (
    detail_id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(64) REFERENCES execution_case(case_id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    step_id VARCHAR(64),
    type VARCHAR(20),  -- A2A_DELEGATE, INTERRUPT, LLM_CALL
    agent_name VARCHAR(255),
    task_name VARCHAR(128),
    step_score INT,
    duration_ms BIGINT,
    input_summary TEXT,
    output_summary TEXT,
    issues JSONB,  -- [{"type": "timeout", "message": "..."}]
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_step_case ON execution_step_detail(case_id);

-- ========================================
-- 对话历史
-- ========================================

CREATE TABLE conversation_history (
    id BIGSERIAL,
    tenant_id VARCHAR(64) NOT NULL,
    thread_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,  -- user, assistant, system
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (id, created_at)  -- 复合主键，支持分区
) PARTITION BY RANGE (created_at);

-- 创建月度分区
CREATE TABLE conversation_history_2026_06 PARTITION OF conversation_history
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE conversation_history_2026_07 PARTITION OF conversation_history
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE INDEX idx_conv_thread ON conversation_history(thread_id);

-- ========================================
-- 凭证管理
-- ========================================

CREATE TABLE credential (
    credential_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(128) NOT NULL,
    granted BOOLEAN DEFAULT FALSE,
    token VARCHAR(512),
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    granted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    UNIQUE (user_id, scope)
);

CREATE INDEX idx_cred_user ON credential(user_id);

-- ========================================
-- 凭证审计
-- ========================================

CREATE TABLE credential_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    credential_id VARCHAR(64),
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    action VARCHAR(20) NOT NULL,  -- REQUEST, APPROVE, REJECT, REVOKE
    scopes TEXT[],
    xid VARCHAR(128),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- ========================================
-- 系统审计日志
-- ========================================

CREATE TABLE audit_log (
    log_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(128),
    detail JSONB,
    xid VARCHAR(128),
    trace_id VARCHAR(128),
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_time ON audit_log(created_at DESC);

-- ========================================
-- 用户画像
-- ========================================

CREATE TABLE user_profile (
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    profile_data JSONB NOT NULL DEFAULT '{}',
    embedding VECTOR(1536),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, tenant_id)
);

-- ========================================
-- 用户行为事件 (分区表)
-- ========================================

CREATE TABLE user_behavior (
    event_id BIGSERIAL,
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128),
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- 按日分区 (自动创建)
-- 运维Job定期创建未来分区
```

### 10.2 Redis 数据结构

| Key 模式                    | 类型   | 内容                  | TTL               | 说明              |
| --------------------------- | ------ | --------------------- | ----------------- | ----------------- |
| `conv:{threadId}`           | List   | 最近20条消息 (JSON)   | 30分钟            | 短期会话          |
| `interrupt:{XID}`           | Hash   | 挂起任务信息          | 30分钟 (定时刷新) | 中断上下文        |
| `case:hot:{scenario}`       | Set    | 热场景的 case_id 集合 | 永久              | 快速检索缓存      |
| `queue:dynamic_orch`        | List   | 动态编排排队请求      | N/A               | 过载保护队列      |
| `rate:llm:api`              | String | LLM API 调用计数      | 1秒               | 限流计数          |
| `breaker:{agentName}`       | String | 断路器状态            | N/A               | Resilience4j 状态 |
| `plan:validation:failure:*` | String | 计划校验失败事件      | 24小时            | LLM行为分析       |
| `session:{userId}`          | Hash   | 用户当前会话信息      | 60分钟            | 会话管理          |

### 10.3 Chroma 集合设计

| 集合名称         | 维度 | 元数据字段                                          | 索引                     | 说明         |
| ---------------- | ---- | --------------------------------------------------- | ------------------------ | ------------ |
| `case_vectors`   | 1536 | `case_id`, `quality_score`, `scenario`, `tenant_id` | quality_score, tenant_id | 案例向量     |
| `knowledge_base` | 1536 | `doc_id`, `user_id`, `title`, `source`              | user_id                  | 用户知识库   |
| `user_profiles`  | 1536 | `user_id`, `tenant_id`, `industry`                  | tenant_id                | 用户画像向量 |

---

## 十一、流量防护与弹性设计

### 11.1 分层防护策略

```
┌─────────────────────────────────────────────────────────────┐
│                    流量防护分层架构                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  第一层: API Gateway                                         │
│  ├── 全局限流 (令牌桶: 10000 req/s)                          │
│  ├── 租户级别限流 (每租户 100 req/s)                         │
│  ├── IP 黑名单                                               │
│  └── JWT 鉴权                                                │
│                                                              │
│  第二层: Commander 编排层                                    │
│  ├── 动态编排信号量 (50 并发)                                │
│  ├── 排队机制 (Redis 优先级队列, 最大等待60s)               │
│  └── 请求超时控制 (全局 90s)                                 │
│                                                              │
│  第三层: LLM API 调用                                        │
│  ├── 限流器 (100 req/s)                                      │
│  ├── 超时 (30s)                                              │
│  └── 重试 (最多2次, 指数退避)                                │
│                                                              │
│  第四层: 子Agent调用 (A2A)                                  │
│  ├── 断路器 (5次失败 → Open, 30s → Half-Open)               │
│  ├── 舱壁隔离 (每个Agent独立线程池)                          │
│  ├── 超时 (各Agent可配置, 默认30s)                           │
│  └── 重试 (最多2次, 仅幂等操作)                              │
│                                                              │
│  第五层: 数据库/缓存                                         │
│  ├── 连接池限流 (HikariCP max-pool-size)                     │
│  ├── Redis 连接池限流                                        │
│  └── SQL 超时 (10s)                                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 11.2 Resilience4j 配置

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30000
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
    instances:
      chroma-service:
        base-config: default
        failure-rate-threshold: 30
      data-analysis-agent:
        base-config: default
      document-agent:
        base-config: default
      credential-service:
        base-config: default
        failure-rate-threshold: 20  # 凭证服务更敏感
  
  bulkhead:
    configs:
      default:
        max-concurrent-calls: 10
        max-wait-duration: 5000ms
    instances:
      data-analysis-agent:
        max-concurrent-calls: 5  # 计算密集型Agent限制更严
  
  ratelimiter:
    configs:
      llm-api:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 5s
  
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
```

### 11.3 排队与过载保护

```java
@Component
public class OverloadProtectionManager {
    
    private final StringRedisTemplate redisTemplate;
    private final Semaphore dynamicOrchSemaphore;
    
    /**
     * 尝试获取编排槽位，失败则排队
     */
    public Mono<Boolean> tryAcquireOrQueue(UserRequest request) {
        
        // 1. 尝试获取信号量
        if (dynamicOrchSemaphore.tryAcquire()) {
            return Mono.just(true);
        }
        
        // 2. 信号量满，加入排队队列
        String queueKey = "queue:dynamic_orch:" + request.getPriority();
        redisTemplate.opsForList().rightPush(queueKey, request.toJson());
        
        // 3. 设置排队超时
        return Mono.delay(Duration.ofSeconds(60))
            .flatMap(tick -> {
                // 检查是否还在队列中
                Long rank = redisTemplate.opsForList()
                    .indexOf(queueKey, request.toJson());
                if (rank != null) {
                    // 超时，从队列中移除
                    redisTemplate.opsForList()
                        .remove(queueKey, 1, request.toJson());
                    return Mono.just(false);
                }
                return Mono.just(true);
            });
    }
    
    /**
     * 后台任务：定期检查信号量，从队列中取出请求
     */
    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        if (dynamicOrchSemaphore.availablePermits() > 0) {
            String requestJson = redisTemplate.opsForList()
                .leftPop("queue:dynamic_orch:HIGH");
            if (requestJson == null) {
                requestJson = redisTemplate.opsForList()
                    .leftPop("queue:dynamic_orch:NORMAL");
            }
            
            if (requestJson != null) {
                UserRequest request = UserRequest.fromJson(requestJson);
                // 异步处理
                orchestrationExecutor.executeAsync(request);
            }
        }
    }
}
```

---

## 十二、故障降级与灾备预案

### 12.1 降级策略矩阵

| 故障场景                    | 影响范围                 | 检测方式                  | 降级动作                                                     | 恢复动作             | 用户感知               | 告警级别 |
| --------------------------- | ------------------------ | ------------------------- | ------------------------------------------------------------ | -------------------- | ---------------------- | -------- |
| **Chroma 向量库不可用**     | 动态编排质量下降         | 断路器打开 / 健康检查失败 | 跳过案例检索，使用纯LLM编排                                  | 断路器关闭后自动恢复 | 响应可能稍慢，质量略降 | P2       |
| **Seata TC 集群故障**       | 无法开启全局事务         | TC 健康检查失败           | 禁止跨Agent事务操作，降级为无事务顺序调用；高风险操作(扣款)直接拒绝 | TC恢复后自动恢复     | 部分功能不可用         | P1       |
| **LLM API 不可用**          | 动态编排完全阻塞         | 连续3次调用失败           | 动态模式返回503，引导用户使用模板功能；模板模式继续正常      | API恢复后自动恢复    | 动态功能暂停           | P1       |
| **LLM API 超额限流**        | 响应变慢                 | RateLimiter触发           | 请求排队，返回429+Retry-After；模板模式优先处理              | 下一时间窗口自动恢复 | 可能等待               | P2       |
| **Case Library 服务故障**   | 案例存取失败             | 断路器打开                | 读降级：跳过检索；写降级：暂存Redis，定时同步                | 服务恢复后自动恢复   | 无感(读)               | P2       |
| **Memory Service 故障**     | 用户画像/偏好不可用      | 断路器打开                | 使用默认画像，会话记忆仅用Redis短期记忆                      | 服务恢复后恢复       | 个性化减弱             | P3       |
| **Credential Service 故障** | 无法授权                 | 健康检查失败              | 已有Token继续使用；新授权请求返回503，提示稍后重试           | 服务恢复后恢复       | 新授权不可用           | P2       |
| **PostgreSQL 主库故障**     | 所有写操作失败           | 连接失败                  | 读操作切换只读副本；写操作暂存Redis(关键操作)或拒绝          | 主从切换完成         | 数据延迟               | P0       |
| **Redis 故障**              | 短期记忆、中断上下文丢失 | 连接失败                  | 中断事务无法恢复(引导用户重新发起)；短期记忆用请求体携带     | 哨兵自动切换         | 可能丢失上下文         | P1       |
| **Nacos 故障**              | 服务发现、配置更新失败   | 服务列表不更新            | 使用本地缓存的服务列表和配置；不支持新Agent注册              | Nacos恢复后自动同步  | 无感(短期)             | P1       |

### 12.2 降级实现示例

```java
@Component
public class DegradationManager {
    
    /**
     * Chroma 不可用降级
     */
    public List<Case> retrieveCasesWithChromaFallback(SearchRequest request) {
        return resilienceManager.executeWithCircuitBreaker(
            // 主逻辑
            () -> caseLibrary.search(request),
            // 降级逻辑
            () -> {
                log.warn("Chroma不可用，降级为纯LLM编排: userId={}", getCurrentUserId());
                
                // 记录降级事件
                metricsService.increment("degradation.chroma.unavailable");
                
                // 返回空列表
                return List.of();
            },
            "chroma-service"
        );
    }
    
    /**
     * Seata TC 不可用降级
     */
    public TransactionMode determineTransactionMode() {
        if (seataHealthIndicator.isHealthy()) {
            return TransactionMode.FULL_ACID;
        }
        
        log.warn("Seata TC不可用，降级为无事务模式");
        metricsService.increment("degradation.seata.unavailable");
        
        return TransactionMode.NO_TRANSACTION;
    }
    
    /**
     * LLM API 不可用降级
     */
    public OrchestrationPlan generatePlanWithLLMFallback(String userInput) {
        try {
            return callLLMForPlan(userInput);
        } catch (Exception e) {
            log.error("LLM API不可用", e);
            
            // 检查是否有预制模板可用
            Optional<GraphTemplate> template = templateSelector
                .findBestMatch(userInput);
            
            if (template.isPresent()) {
                // 降级为模板模式
                metricsService.increment("degradation.llm.template_fallback");
                return templateToPlan(template.get());
            } else {
                // 完全无法处理
                throw new ServiceUnavailableException(
                    "AI服务暂时不可用，请稍后重试或联系管理员"
                );
            }
        }
    }
}
```

### 12.3 灾备演练计划

| 演练项目            | 频率   | 参与人员       | 演练内容           | 预期结果                                 | 回滚方案       |
| ------------------- | ------ | -------------- | ------------------ | ---------------------------------------- | -------------- |
| Chroma 不可用       | 每月   | 运维+后端      | 手动停止Chroma服务 | 系统自动降级为纯LLM编排，响应时间增加<2s | 重启Chroma服务 |
| Seata TC 单节点故障 | 每月   | 运维+后端      | 停止1个TC节点      | 事务自动切换其他节点，无数据不一致       | 重启故障节点   |
| Seata TC 全集群故障 | 每季度 | 运维+后端+架构 | 停止所有TC节点     | 系统拒绝高风险操作，允许只读操作         | 按序重启TC集群 |
| LLM API 超额        | 每月   | 后端           | 模拟API返回429     | 系统自动排队，超限请求返回503            | 恢复正常限流   |
| PostgreSQL 主从切换 | 每季度 | DBA+运维       | 手动触发主从切换   | 切换时间<30s，数据零丢失                 | 切换回原主库   |
| Redis 哨兵切换      | 每季度 | 运维           | 停止Redis主节点    | 自动切换<10s，中断上下文丢失可接受       | 恢复原主节点   |
| 全链路故障演练      | 每半年 | 全部技术团队   | 同时触发多个故障   | 系统降级后核心功能可用，P0告警触发       | 按预案逐项恢复 |

---

## 十三、多租户与数据隔离

### 13.1 多租户架构

```
┌─────────────────────────────────────────────────────────────┐
│                    多租户隔离架构                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  租户A                    租户B                    租户C      │
│  ├── 用户组A1             ├── 用户组B1             ├── ...   │
│  ├── Agent实例组A         ├── Agent实例组B         ├── ...   │
│  ├── 配置组A              ├── 配置组B              ├── ...   │
│  └── 数据A                └── 数据B                └── ...   │
│                                                              │
│  隔离级别:                                                    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ 数据库层: 共享数据库 + tenant_id 字段隔离             │    │
│  │ 缓存层:   Redis key 前缀: {tenantId}:*               │    │
│  │ 向量库:   Chroma 集合按租户分 collection             │    │
│  │ 配置层:   Nacos namespace 隔离                       │    │
│  │ 服务层:   Agent 共享，通过 tenantId 区分上下文        │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 13.2 隔离实现

```java
@Component
public class TenantContext {
    
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }
    
    public static String get() {
        return CURRENT_TENANT.get();
    }
    
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

// API Gateway 过滤器
@Component
public class TenantFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String tenantId = exchange.getRequest()
            .getHeaders().getFirst("X-Tenant-Id");
        
        if (tenantId == null) {
            tenantId = extractFromJWT(exchange);
        }
        
        TenantContext.set(tenantId);
        
        return chain.filter(exchange)
            .doFinally(signal -> TenantContext.clear());
    }
}

// Redis Key 隔离
@Component
public class TenantAwareRedisTemplate {
    
    private final StringRedisTemplate redisTemplate;
    
    public void set(String key, String value) {
        String tenantKey = TenantContext.get() + ":" + key;
        redisTemplate.opsForValue().set(tenantKey, value);
    }
    
    public String get(String key) {
        String tenantKey = TenantContext.get() + ":" + key;
        return redisTemplate.opsForValue().get(tenantKey);
    }
}
```

---

## 十四、部署与配置

### 14.1 服务端口规划

| 服务                      | 端口  | 实例数    | 资源分配         | 备注       |
| ------------------------- | ----- | --------- | ---------------- | ---------- |
| Nacos                     | 8848  | 3         | 4C8G             | 集群       |
| Seata TC                  | 8091  | 3         | 2C4G             | 集群       |
| PostgreSQL                | 5432  | 主从      | 16C64G, SSD      | 高可用     |
| Redis                     | 6379  | 哨兵3节点 | 8C32G            | 高可用     |
| Chroma                    | 8000  | 2+        | 8C32G, GPU(可选) | 向量存储   |
| API Gateway               | 8080  | 2+        | 4C8G             | 网关       |
| Commander                 | 10010 | 3+        | 8C16G            | 水平扩展   |
| Memory Service            | 10086 | 2+        | 4C8G             |            |
| Case Library              | 10100 | 2+        | 4C8G             |            |
| Credential Service        | 10110 | 2+        | 2C4G             |            |
| Document Agent            | 12100 | 2+        | 4C8G             |            |
| DataAnalysis Agent        | 12110 | 2+        | 8C16G            | 计算密集型 |
| Image Analysis Agent      | 12120 | 2+        | 4C8G+GPU         |            |
| Code Generation Agent     | 12130 | 2+        | 4C8G             |            |
| Knowledge Retrieval Agent | 12140 | 2+        | 4C8G             |            |
| Email Agent               | 12150 | 1+        | 2C4G             |            |
| Calendar Agent            | 12160 | 1+        | 2C4G             |            |

### 14.2 启动顺序

```
Phase 1: 基础中间件 (30分钟)
  1. PostgreSQL 主从 → 验证复制
  2. Redis 哨兵 → 验证主从
  3. Chroma → 验证集合
  4. Nacos 集群 → 验证注册
  5. Seata TC 集群 → 验证协调

Phase 2: 核心服务 (10分钟)
  6. Memory Service
  7. Case Library (加载种子案例)
  8. Credential Service

Phase 3: 业务Agent (15分钟)
  9. Document Agent
  10. DataAnalysis Agent
  11. Image Analysis Agent
  12. 其他Agent...

Phase 4: 编排层 (5分钟)
  13. Commander
  14. API Gateway

总启动时间: ~60分钟
```

### 14.3 Nacos 配置文件清单

| Data ID                                 | Group               | 说明                        |
| --------------------------------------- | ------------------- | --------------------------- |
| `commander-config.yaml`                 | COMMANDER_GROUP     | Commander 核心配置          |
| `commander-graph-templates.yaml`        | COMMANDER_GROUP     | 预定义 Graph 模板           |
| `task-classification-rules.yaml`        | COMMANDER_GROUP     | 硬规则 + 复杂度阈值         |
| `platform-model-routing.yaml`           | COMMANDER_GROUP     | 模型映射与加权              |
| `resilience-config.yaml`                | COMMANDER_GROUP     | 断路器/限流/信号量配置      |
| `degradation-rules.yaml`                | COMMANDER_GROUP     | 降级策略配置                |
| `seata-config.yaml`                     | SEATA_GROUP         | Seata 客户端配置            |
| `document-agent-mcp.yaml`               | DOCUMENT_GROUP      | Document Agent MCP 配置     |
| `data-analysis-agent-mcp.yaml`          | DATA_ANALYSIS_GROUP | DataAnalysis Agent MCP 配置 |
| `seed-cases.yaml`                       | CASE_LIBRARY_GROUP  | 种子案例数据                |
| `tenant-config.yaml`                    | TENANT_GROUP        | 多租户配置                  |
| `common-chat-model-api-keys.properties` | COMMON_GROUP        | API Key 统一管理            |

---

## 十五、可观测性与监控

### 15.1 日志规范

```java
// 结构化日志格式 (JSON)
@Slf4j
public class LoggingAspect {
    
    @Around("@annotation(GlobalTransactional)")
    public Object logTransaction(ProceedingJoinPoint pjp) throws Throwable {
        String xid = RootContext.getXID();
        String traceId = MDC.get("traceId");
        String userId = MDC.get("userId");
        
        log.info("{}", Map.of(
            "event", "TX_BEGIN",
            "xid", xid,
            "traceId", traceId,
            "userId", userId,
            "method", pjp.getSignature().getName(),
            "timestamp", Instant.now().toString()
        ));
        
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("{}", Map.of(
                "event", "TX_END",
                "xid", xid,
                "traceId", traceId,
                "duration_ms", System.currentTimeMillis() - start,
                "status", "SUCCESS"
            ));
            return result;
        } catch (Exception e) {
            log.error("{}", Map.of(
                "event", "TX_ERROR",
                "xid", xid,
                "traceId", traceId,
                "error", e.getMessage(),
                "duration_ms", System.currentTimeMillis() - start
            ));
            throw e;
        }
    }
}
```

### 15.2 指标采集 (Micrometer + Prometheus)

| 指标名                         | 类型      | 标签                   | 说明             |
| ------------------------------ | --------- | ---------------------- | ---------------- |
| `commander.execution.duration` | Histogram | mode, status, scenario | 编排执行耗时     |
| `commander.execution.total`    | Counter   | mode, status           | 编排执行计数     |
| `commander.interrupt.count`    | Counter   | type                   | 中断次数         |
| `commander.interrupt.duration` | Histogram | type                   | 中断等待时长     |
| `commander.case.hit`           | Counter   | scenario               | 案例命中次数     |
| `commander.case.miss`          | Counter   | scenario               | 案例未命中次数   |
| `agent.a2a.call.duration`      | Histogram | agent, task            | 子Agent调用耗时  |
| `agent.a2a.call.total`         | Counter   | agent, status          | 子Agent调用计数  |
| `seata.transaction.duration`   | Histogram | status                 | 全局事务耗时     |
| `seata.transaction.rollback`   | Counter   | reason                 | 事务回滚次数     |
| `llm.api.call.duration`        | Histogram | model                  | LLM API调用耗时  |
| `llm.api.call.total`           | Counter   | model, status          | LLM API调用计数  |
| `degradation.event`            | Counter   | service, reason        | 降级事件计数     |
| `plan.validation.failure`      | Counter   | reason                 | 计划校验失败计数 |

### 15.3 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: agent-platform
    rules:
      # P0 告警：立即处理
      - alert: SeataTCClusterDown
        expr: up{job="seata-tc"} == 0
        for: 1m
        labels:
          severity: P0
        annotations:
          summary: "Seata TC 集群全部不可用"
          
      - alert: PostgreSQLMasterDown
        expr: pg_up{instance="postgresql-master"} == 0
        for: 30s
        labels:
          severity: P0
        annotations:
          summary: "PostgreSQL 主库不可用"
          
      # P1 告警：30分钟内处理
      - alert: LLMAPIDown
        expr: rate(llm_api_call_total{status="error"}[5m]) > 0.5
        for: 5m
        labels:
          severity: P1
        annotations:
          summary: "LLM API 错误率超过50%"
          
      - alert: HighRollbackRate
        expr: rate(seata_transaction_rollback_total[10m]) > 0.2
        for: 10m
        labels:
          severity: P1
        annotations:
          summary: "Seata 事务回滚率超过20%"
          
      # P2 告警：1小时内处理
      - alert: HighExecutionDuration
        expr: histogram_quantile(0.99, commander_execution_duration_seconds) > 8
        for: 15m
        labels:
          severity: P2
        annotations:
          summary: "P99编排耗时超过8秒"
          
      - alert: ChromaDegradation
        expr: rate(degradation_event_total{service="chroma"}[30m]) > 0
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "Chroma 降级事件发生"
```

### 15.4 链路追踪

```yaml
# 链路追踪配置
spring:
  sleuth:
    sampler:
      probability: 0.1  # 10%采样率
    baggage:
      remote-fields:
        - xid
        - tenant-id
        - user-id
  zipkin:
    base-url: http://zipkin:9411
    sender:
      type: web
```

---

## 十六、安全与合规

### 16.1 认证授权体系

```
┌─────────────────────────────────────────────────────────────┐
│                    安全防护体系                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  外部用户 → API Gateway                                      │
│  ├── JWT 鉴权 (token校验 + 过期检查)                         │
│  ├── 租户识别 (X-Tenant-Id)                                  │
│  ├── RBAC 权限检查                                           │
│  ├── 限流 (租户级别)                                         │
│  └── CORS 防护                                               │
│                                                              │
│  内部服务间 → Nacos A2A 服务认证                             │
│  ├── 服务名 + 密钥认证                                       │
│  └── X-INTERNAL-TOKEN 头                                    │
│                                                              │
│  Feign 调用 → 服务间 Token                                  │
│  ├── X-INTERNAL-TOKEN                                       │
│  └── 来源IP白名单                                            │
│                                                              │
│  数据访问 → 凭证服务统一管理                                  │
│  ├── 细粒度 scope 控制                                       │
│  ├── Agent 白名单 (Token 仅注入授权Agent)                    │
│  └── 审计日志全记录                                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 16.2 数据安全

```java
@Component
public class DataSecurityManager {
    
    /**
     * 敏感数据加密 (AES-256)
     */
    public String encrypt(String plainText, String keyId) {
        SecretKey key = keyManager.getKey(keyId);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
    
    /**
     * 用户输入脱敏
     */
    public String sanitize(String userInput) {
        // 移除身份证号
        userInput = userInput.replaceAll(
            "\\d{17}[\\dXx]", "[身份证号已脱敏]"
        );
        // 移除手机号
        userInput = userInput.replaceAll(
            "1[3-9]\\d{9}", "[手机号已脱敏]"
        );
        // 移除邮箱
        userInput = userInput.replaceAll(
            "[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}", "[邮箱已脱敏]"
        );
        return userInput;
    }
    
    /**
     * 多模态内容安全扫描
     */
    public boolean scanMultimodalContent(UserMessage message) {
        // 1. 文本注入检测
        if (ContentSafetyScanner.detectPromptInjection(message.getText())) {
            log.warn("检测到提示注入攻击: userId={}", getCurrentUserId());
            auditLog.record("PROMPT_INJECTION_DETECTED", message.getText());
            return false;
        }
        
        // 2. 图片内容扫描
        for (Media media : message.getMedia()) {
            if (media.getMimeType().getType().equals("image")) {
                // OCR提取图片文字
                String ocrText = performOCR(media.getData());
                if (ContentSafetyScanner.detectPromptInjection(ocrText)) {
                    log.warn("检测到图片中的隐藏注入文本");
                    return false;
                }
            }
        }
        
        return true;
    }
}
```

### 16.3 审计日志

```java
@Aspect
@Component
public class AuditAspect {
    
    @Around("@annotation(Auditable)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String userId = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        String tenantId = TenantContext.get();
        String action = pjp.getSignature().getName();
        String traceId = MDC.get("traceId");
        String xid = RootContext.getXID();
        String ipAddress = getClientIp();
        
        AuditLog log = AuditLog.builder()
            .userId(userId)
            .tenantId(tenantId)
            .action(action)
            .traceId(traceId)
            .xid(xid)
            .ipAddress(ipAddress)
            .detail(Map.of(
                "method", pjp.getSignature().toShortString(),
                "args", sanitizeArgs(pjp.getArgs())
            ))
            .build();
        
        try {
            Object result = pjp.proceed();
            log.setStatus("SUCCESS");
            return result;
        } catch (Exception e) {
            log.setStatus("FAILURE");
            log.setError(e.getMessage());
            throw e;
        } finally {
            auditLogService.save(log);
        }
    }
}
```

---

## 十七、容量规划与扩展

### 17.1 估算模型（100万DAU）

| 维度              | 估算值      | 计算依据                              |
| ----------------- | ----------- | ------------------------------------- |
| 日活跃用户        | 100万       | 峰值1.5倍系数                         |
| 每日请求量        | 300万       | 每用户3次交互 (含多轮)                |
| 模板模式占比      | 60% (180万) | P99 < 200ms                           |
| 动态编排占比      | 40% (120万) | P99 < 8s                              |
| 峰值QPS           | 500         | (300万 / 86400) * 峰值系数10          |
| 动态编排峰值并发  | 100         | 120万请求 * 平均4s / 86400 * 峰值系数 |
| 每日子Agent调用量 | 900万次     | 300万 * 平均3步                       |
| 案例库日增案例    | 3000条      | 120万 * 高质量率0.25%                 |
| PostgreSQL 日增量 | 5GB         | 对话历史 + 审计日志 + 案例            |
| Redis 峰值内存    | 30GB        | 短期会话 + 中断上下文 + 缓存          |
| Chroma 日增量     | 15万向量    | 新案例 + 新知识                       |
| LLM API 日调用量  | 150万次     | 120万编排 * 1 + 意图识别 + 评估       |

### 17.2 水平扩展策略

| 服务               | 扩展策略 | 触发条件                    |
| ------------------ | -------- | --------------------------- |
| Commander          | 线性扩展 | CPU > 70% 或 活跃请求 > 200 |
| Memory Service     | 线性扩展 | CPU > 60%                   |
| Case Library       | 线性扩展 | 请求延迟 > 200ms            |
| DataAnalysis Agent | 线性扩展 | 队列深度 > 50               |
| Chroma             | 分片扩展 | 集合大小 > 100万向量        |
| PostgreSQL         | 读写分离 | 主库CPU > 80%               |
| Redis              | 集群扩展 | 内存使用 > 80%              |

---

## 十八、冷启动与种子数据

### 18.1 种子案例注入

```yaml
# seed-cases.yaml
seed_cases:
  - case_id: "SEED_001"
    source: "SEED"
    scenario: "FINANCIAL_ANALYSIS"
    user_input: "分析Q3财务报表并生成审计报告"
    orchestration_plan:
      steps:
        - id: "step1"
          type: "A2A_DELEGATE"
          agent: "data-analysis-agent"
          task: "FETCH_FINANCIAL_DATA"
        - id: "step2"
          type: "INTERRUPT"
          question: "数据已获取，是否继续审计分析？"
        - id: "step3"
          type: "A2A_DELEGATE"
          agent: "data-analysis-agent"
          task: "AUDIT_ANALYSIS"
        - id: "step4"
          type: "A2A_DELEGATE"
          agent: "document-agent"
          task: "GENERATE_AUDIT_REPORT"
    overall_score: 95
    quality_tag: "GOLD"
    diversity_tags: ["finance", "audit", "multi-step"]

  - case_id: "SEED_002"
    source: "SEED"
    scenario: "DATA_ANALYSIS"
    user_input: "分析销售趋势并预测下季度收入"
    orchestration_plan:
      steps:
        - id: "step1"
          type: "A2A_DELEGATE"
          agent: "data-analysis-agent"
          task: "TREND_ANALYSIS"
        - id: "step2"
          type: "A2A_DELEGATE"
          agent: "data-analysis-agent"
          task: "FORECAST"
        - id: "step3"
          type: "LLM_CALL"
          task: "SUMMARIZE_INSIGHTS"
    overall_score: 92
    quality_tag: "GOLD"
    diversity_tags: ["data", "forecast", "trend"]

  # ... 共100个种子案例，覆盖所有核心场景
```

### 18.2 种子数据加载器

```java
@Component
public class SeedDataLoader implements ApplicationRunner {
    
    @Override
    public void run(ApplicationArguments args) {
        if (caseRepository.countBySource("SEED") == 0) {
            log.info("开始加载种子案例...");
            
            List<CaseSeedDTO> seeds = loadSeedsFromYaml("seed-cases.yaml");
            
            for (CaseSeedDTO seed : seeds) {
                ExecutionCase caseRecord = convertToEntity(seed);
                caseRecord.setOverallScore(seed.getOverallScore());
                caseRecord.setQualityTag(seed.getQualityTag());
                caseRecord.setSource("SEED");
                
                // 生成向量
                float[] embedding = embeddingModel.embed(
                    seed.getUserInput() + " " + seed.getScenario()
                );
                caseRecord.setEmbedding(embedding);
                
                // 保存到PG和Chroma
                caseRepository.save(caseRecord);
                chromaClient.add("case_vectors", caseRecord.getCaseId(), 
                                  embedding, buildMetadata(caseRecord));
            }
            
            log.info("种子案例加载完成: 共{}条", seeds.size());
        }
    }
}
```

---

## 十九、运维手册与故障演练

### 19.1 常见运维操作

```bash
# ==========================================
# 服务启动/停止
# ==========================================

# 启动所有服务 (按顺序)
docker-compose -f docker-compose-infra.yml up -d  # 中间件
sleep 60
docker-compose -f docker-compose-core.yml up -d    # 核心服务
sleep 30
docker-compose -f docker-compose-agents.yml up -d   # Agent
sleep 15
docker-compose -f docker-compose-orchestrator.yml up -d  # 编排层

# 优雅停止单个Agent
curl -X POST http://document-agent:12100/actuator/shutdown

# 查看服务健康状态
curl http://commander:10010/actuator/health

# ==========================================
# 配置热更新
# ==========================================

# 通过 Nacos 控制台或 API 更新配置
curl -X POST "http://nacos:8848/nacos/v1/cs/configs" \
  -d "dataId=resilience-config.yaml&group=COMMANDER_GROUP&content=..."

# 刷新配置 (无需重启)
curl -X POST http://commander:10010/actuator/refresh

# ==========================================
# 数据维护
# ==========================================

# 手动触发案例老化
curl -X POST http://case-library:10100/api/case/internal/aging

# 手动触发种子案例重载
curl -X POST http://case-library:10100/api/case/internal/seed/reload

# 清理过期Redis Key
redis-cli --scan --pattern "interrupt:*" | xargs -L 1 redis-cli TTL | \
  awk '{if ($1 < 0) print}' | xargs -L 1 redis-cli DEL

# ==========================================
# 故障排查
# ==========================================

# 查看Seata全局事务列表
curl http://seata-tc:7091/api/v1/global/status

# 查询特定XID的事务状态
curl "http://seata-tc:7091/api/v1/global/status?xid=192.168.1.1:8091:2034567890"

# 查看Chroma集合信息
curl http://chroma:8000/api/v1/collections/case_vectors

# 查看断路器状态
curl http://commander:10010/actuator/circuitbreakers
curl http://commander:10010/actuator/circuitbreakerevents
```

### 19.2 故障演练脚本

```bash
#!/bin/bash
# ==========================================
# 故障演练: Chroma 不可用
# ==========================================

echo "=== Chroma 故障演练开始 ==="

# 1. 记录演练前状态
echo "演练前断路器状态:"
curl -s http://commander:10010/actuator/circuitbreakers | jq '.circuitBreakers.chroma-service'

# 2. 停止 Chroma 服务
echo "停止 Chroma 服务..."
docker stop chroma-service

# 3. 等待断路器触发
echo "等待断路器触发..."
sleep 15

# 4. 验证降级
echo "发送测试请求..."
curl -s -X POST http://commander:10010/api/v1/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TEST_TOKEN" \
  -d '{"message": "分析最近的销售数据"}' | jq '.metadata.fallback'

# 5. 检查断路器状态
echo "断路器状态:"
curl -s http://commander:10010/actuator/circuitbreakers | jq '.circuitBreakers.chroma-service'

# 6. 恢复 Chroma
echo "恢复 Chroma 服务..."
docker start chroma-service

# 7. 等待断路器半开
echo "等待断路器半开..."
sleep 35

# 8. 验证恢复
echo "断路器状态:"
curl -s http://commander:10010/actuator/circuitbreakers | jq '.circuitBreakers.chroma-service'

echo "=== 演练完成 ==="
```

---

## 二十、常见问题与踩坑记录

### 20.1 循环依赖
**现象**: `BeanCurrentlyInCreationException`  
**原因**: 配置类通过字段注入工具类，工具类又注入配置类定义的 ChatModel Bean  
**解决**: 移除配置类中的工具类字段，改为在 `@Bean` 方法参数中注入

### 20.2 MCP Client 初始化超时
**现象**: `Client failed to initialize listing tools`，超时20s  
**原因**: stdio 模式启动 npx 进程慢，或环境变量缺失  
**解决**: Windows 使用 `npx.cmd`；设置环境变量；使用 `SafeSyncMcpToolCallbackProvider` 懒加载

### 20.3 A2A 服务注册失败
**现象**: Commander 找不到子 Agent  
**解决**: 子Agent启动类添加 `@EnableA2aAgent`，确保 `spring.ai.alibaba.a2a.nacos.registry.enabled=true`

### 20.4 @ConfigurationProperties 绑定为空
**解决**: 启动类添加 `@EnableConfigurationProperties(XxxConfig.class)`

### 20.5 Seata 事务恢复后子 Agent 重复执行
**解决**: Commander 恢复调用时确保同一 `XID` 和 `branchId`；子Agent TCC 接口基于 `XID + branchId` 去重

### 20.6 案例库向量检索延迟过高
**解决**: 增加 Chroma 内存；使用 GPU 加速；建立元数据过滤索引；限制检索数量

### 20.7 动态编排生成死循环计划 (v10.0 已修复)
**现象**: 编排执行超过预期时间，日志显示步骤循环  
**修复**: 通过 `PlanValidator` 进行拓扑排序检查，检测到环立即拦截

### 20.8 事务悬挂导致用户授权后无法恢复 (v10.0 已修复)
**现象**: 用户长时间未授权，Commander 重启后无法恢复  
**修复**: 实现主动查询 Seata TC 全局事务状态能力，只要 TC 中事务活跃即可重建中断上下文

### 20.9 多模态提示注入 (v10.0 已修复)
**现象**: 用户上传含隐藏文字的图片进行注入攻击  
**修复**: 对多模态内容进行OCR提取 + 安全扫描，检测到注入立即拒绝

---

## 二十一、演进路线图

| 版本  | 时间           | 核心功能                                                     |
| ----- | -------------- | ------------------------------------------------------------ |
| v9.0  | 2026-06        | 混合编排 + 动态编排原型 + 自进化闭环 + 中断机制              |
| v10.0 | 2026-06 (当前) | 生产就绪完整版：确定性保障、故障降级、流量防护、种子案例、事务恢复闭环、多租户隔离 |
| v10.1 | 2026-07        | 强化学习优化编排策略 (基于案例库训练编排决策模型)            |
| v10.2 | 2026-08        | 联邦学习：跨租户案例共享 (差分隐私保护)                      |
| v10.3 | 2026-09        | Agent 市场：支持第三方开发自定义Agent并注册到平台            |
| v10.4 | 2026-10        | 多模态大模型原生编排 (视频流、音频流实时分析Agent)           |
| v11.0 | 2026-Q4        | 平台化：可视化编排设计器、低代码Agent构建、运营仪表盘        |

---

> **文档维护**: 本文档随系统迭代持续更新，最新版本请查阅 `docs/architecture_v10.0.md`  
> **架构决策记录 (ADR)**: 所有关键架构决策记录在 `docs/adr/` 目录  
> **最后更新**: 2026-06-04  
> **负责人**: 架构组  

**附录**:
- A. [术语表](docs/glossary.md)
- B. [API 接口文档](docs/api-reference.md)
- C. [部署运维手册](docs/operations.md)
- D. [安全白皮书](docs/security.md)
- E. [ADR 索引](docs/adr/README.md)

