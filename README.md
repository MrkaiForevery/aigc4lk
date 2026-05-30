

# aigc4lk-Agent智能平台架构设计文档

> **文档版本**: v4.0.0
> **设计日期**: 2026-05-27
> **技术栈**: Spring Boot 3.5.0 + Spring AI Alibaba 1.1.2 + Nacos 3.2 + PostgreSQL + Redis + Chroma
> **架构模式**: Commander编排 + A2A自治 + Memory Service + 多模态融合
> **核心变更**: 新增 Memory Service 独立服务，完善记忆分类与持久化方案
>
> [TOC]
>
>

## 一、系统全景架构

### 1.1 系统拓扑图

text

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         Nacos 3.2 Service Mesh                                │
│                        (原生 A2A / MCP / 多模态治理)                           │
│                                                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                      Commander Service (编排层)                           │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │  │
│  │  │ 意图识别      │  │ 架构选择      │  │ 模型路由      │                   │  │
│  │  │ IntentClassifier│ │ArchitectureSelector│ │ChatModelRouter │            │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘                   │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐   │  │
│  │  │                    ChatClientConfiguration (动态模型工厂)          │   │  │
│  │  │   DashScopeChatModel | OpenAiChatModel | modelCache              │   │  │
│  │  └──────────────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────┬─────────────────────────────────────────┘  │
│                                  │ 编排调度                                    │
│       ┌──────────────────────────┼──────────────────────────┐                  │
│       │                          │                          │                  │
│  ┌────▼────────┐         ┌───────▼────────┐        ┌───────▼────────┐         │
│  │ Sequential  │         │   Parallel     │        │   Custom       │         │
│  │ Pipeline    │         │   Analysis     │        │   StateGraph   │         │
│  └─────┬───────┘         └───────┬────────┘        └───────┬────────┘         │
│        │                         │                         │                   │
│  ┌─────┴─────────────────────────┴─────────────────────────┴──────┐            │
│  │                    A2A Channel (Nacos 3.2 原生发现)              │            │
│  │  ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐  │            │
│  │  │ Agent A │◄───►│ Agent B │◄───►│ Agent C │◄───►│ Agent D │  │            │
│  │  └────┬────┘     └────┬────┘     └────┬────┘     └────┬────┘  │            │
│  └───────┼───────────────┼───────────────┼───────────────┼────────┘            │
│          │               │               │               │                     │
│  ┌───────┴───────────────┴───────────────┴───────────────┴────────┐            │
│  │                 MCP Protocol Layer (Nacos 3.2 治理)              │            │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │            │
│  │  │Text MCP  │ │Vision MCP│ │Speech MCP│ │Video MCP         │  │            │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │            │
│  └────────────────────────────────────────────────────────────────┘            │
│                                                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                    Memory Service (共享记忆层)                             │  │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐  │  │
│  │  │ 身份记忆   │ │ 画像记忆   │ │ 行为记忆   │ │ 长期知识   │ │ 偏好记忆   │  │  │
│  │  │ PostgreSQL│ │ PostgreSQL│ │ PostgreSQL│ │ Chroma    │ │ PostgreSQL│  │  │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘  │  │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌────────────────────────┐    │  │
│  │  │ 关系记忆   │ │ 决策记忆   │ │ 短期热记忆 │ │        清洗引擎        │    │  │
│  │  │ PostgreSQL│ │ PostgreSQL│ │  Redis    │ │   RuleBased + LLM     │    │  │
│  │  └───────────┘ └───────────┘ └───────────┘ └────────────────────────┘    │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```



### 1.2 服务间调用关系

text

```
外部请求 (HTTP)
    │
    ▼
Commander Service (port 10010)
    │
    ├──→ Memory Service (port 8081)           // 记忆存取
    │    ├── PostgreSQL (核心持久化)
    │    ├── Redis (热缓存)
    │    └── Chroma (向量知识库)
    │
    ├──→ Nacos 3.2 (服务发现 / 配置中心)
    │    ├── agent-platform-config.yaml
    │    ├── platform-model-routing-config.yaml
    │    ├── chat-model-api-keys.properties
    │    └── ...其他配置
    │
    ├──→ DashScope API (通义系列模型)
    │    ├── ChatModel (qwen-turbo / qwen-max / qwen-plus)
    │    └── EmbeddingModel (text-embedding-v2)
    │
    ├──→ OpenAI Compatible API (DeepSeek 等)
    │
    └──→ 子 Agent (通过 A2A 协议)
         ├── Document Agent
         ├── Analysis Agent
         ├── Speech Agent
         └── Vision Agent
```



------

## 二、核心技术栈与版本矩阵

### 2.1 精确版本依赖





| 组件                 | 版本       | 用途                         | 备注                          |
| :------------------- | :--------- | :--------------------------- | :---------------------------- |
| Spring Boot          | 3.5.0      | 基础框架                     |                               |
| Spring AI            | 1.1.2      | AI 抽象层                    | MCP Client/Server 支持        |
| Spring AI Alibaba    | 1.1.2.2    | Agent Framework + Graph Core | 核心编排框架                  |
| Spring Cloud Alibaba | 2025.0.0.0 | 微服务治理                   |                               |
| **Nacos Server**     | **3.2.0**  | **注册中心 + 配置中心**      | **⚠️ 必须 3.2+，原生 A2A/MCP** |
| Nacos Client         | 3.2.0      | Nacos 客户端                 |                               |
| DashScope SDK        | latest     | 通义系列模型接入             | 文本/语音/视觉/视频           |
| PostgreSQL           | 15+        | 核心持久化存储               | 身份/画像/行为/偏好/关系/决策 |
| Redis                | 7.x        | 热缓存 + 会话记忆            | 短期热记忆                    |
| Chroma               | latest     | 向量知识库                   | 长期知识记忆                  |
| Resilience4j         | 2.2.0      | 熔断/重试                    |                               |
| Micrometer           | 1.15.0     | 指标收集                     | Prometheus 导出               |

### 2.2 Maven 依赖清单

xml

```
<properties>
    <java.version>17</java.version>
    <spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-cloud-alibaba.version>2025.0.0.0</spring-cloud-alibaba.version>
    <nacos-client.version>3.2.0</nacos-client.version>
</properties>

<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Spring AI Alibaba -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-agent-framework</artifactId>
        <version>${spring-ai-alibaba.version}</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
        <version>${spring-ai-alibaba.version}</version>
        <exclusions>
            <exclusion>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <!-- Spring AI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>

    <!-- Nacos -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        <version>${spring-cloud-alibaba.version}</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        <version>${spring-cloud-alibaba.version}</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-a2a-nacos</artifactId>
        <version>${spring-ai-alibaba.version}</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-client</artifactId>
        <version>${nacos-client.version}</version>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>

    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>2.2.0</version>
    </dependency>

    <!-- Micrometer -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```



------

## 三、模块划分与项目结构

### 3.1 顶级项目结构

text

```
aigc4lk/
├── pom.xml                                 # 父 POM
├── platformCommon/                         # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/air/platform/common/
│       ├── a2a/                            # A2A 协议核心
│       │   ├── protocol/                   # 消息体定义
│       │   ├── router/                     # NacosA2ARouter
│       │   └── channel/                    # CommanderChannel / A2AChannel
│       ├── model/                          # 通用数据模型
│       │   └── ModelDefinition.java
│       └── enums/                          # 枚举定义
│           └── ModelType.java
│
├── commanderAgent/                         # Commander 编排服务
│   ├── pom.xml
│   └── src/main/java/com/air/commander/
│       ├── CommanderApplication.java       # 启动类
│       ├── agent/                          # CommanderAgent
│       │   └── CommanderAgent.java
│       ├── intent/                         # 意图识别
│       │   ├── IntentClassifier.java
│       │   └── IntentAnalysis.java
│       ├── architecture/                   # 架构选择
│       │   ├── ArchitectureSelector.java
│       │   └── ArchitectureSelection.java
│       ├── model/                          # 模型路由
│       │   ├── ChatModelRouter.java
│       │   └── ModelSelection.java
│       ├── config/                         # 配置类
│       │   ├── CommanderMetaConfig.java
│       │   ├── ChatModelRoutingConfig.java
│       │   ├── ChatModelApiKeyConfig.java
│       │   ├── ChatClientConfiguration.java
│       │   ├── ChromaProperties.java
│       │   └── ModelRoutingConfig.java
│       ├── controller/                     # API 控制器
│       │   └── CommanderController.java
│       └── entity/                         # 实体类
│           ├── CommanderRequest.java
│           ├── CommanderResponse.java
│           └── ExecutionRecord.java
│
├── memory-service/                         # 记忆服务
│   ├── pom.xml
│   └── src/main/java/com/example/memory/
│       ├── MemoryServiceApplication.java
│       ├── controller/                     # API 控制器
│       │   ├── IdentityController.java
│       │   ├── SessionController.java
│       │   ├── ProfileController.java
│       │   ├── BehaviorController.java
│       │   ├── KnowledgeController.java
│       │   ├── PreferenceController.java
│       │   ├── RelationshipController.java
│       │   └── DecisionController.java
│       ├── service/                        # 业务逻辑
│       │   ├── MemoryManager.java
│       │   └── handlers/                   # 各类记忆处理器
│       ├── model/                          # 数据模型
│       │   ├── IdentityMemory.java
│       │   ├── ProfileMemory.java
│       │   ├── BehaviorRecord.java
│       │   ├── KnowledgeResult.java
│       │   ├── PreferenceMemory.java
│       │   ├── RelationshipRecord.java
│       │   └── DecisionRecord.java
│       ├── repository/                     # 数据访问层
│       │   ├── IdentityRepository.java
│       │   ├── ProfileRepository.java
│       │   ├── BehaviorRepository.java
│       │   ├── PreferenceRepository.java
│       │   ├── RelationshipRepository.java
│       │   └── DecisionRepository.java
│       ├── cleaner/                        # 记忆清洗
│       │   ├── MemoryCleaner.java
│       │   ├── RuleBasedCleaner.java
│       │   └── LLMBasedCleaner.java
│       └── config/                         # 配置类
│           ├── RedisConfig.java
│           ├── ChromaConfig.java
│           └── AsyncConfig.java
│
├── agent-services/                         # 子 Agent 服务集群（待实现）
│   ├── document-agent/
│   ├── analysis-agent/
│   ├── investment-agent/
│   └── customer-service-agent/
│
├── multimodal-services/                    # 多模态服务集群（待实现）
│   ├── speech-recognition-service/
│   ├── text-to-speech-service/
│   ├── image-analysis-service/
│   ├── image-generation-service/
│   ├── video-analysis-service/
│   └── video-generation-service/
│
└── mcp-services/                           # MCP 服务集群
    ├── local-filesystem-mcp/
    ├── local-calculator-mcp/
    ├── remote-database-mcp/
    └── remote-search-mcp/
```



------

## 四、通信架构设计

### 4.1 六通道通信矩阵





| 通道                   | 协议           | 模式      | 超时 | 用途                                |
| :--------------------- | :------------- | :-------- | :--- | :---------------------------------- |
| **Commander Channel**  | HTTP/REST      | 同步      | 300s | 全局任务编排、结果汇报              |
| **A2A Channel**        | Nacos 3.2 gRPC | 异步+同步 | 30s  | Agent 间直接协作、能力发现          |
| **MCP Channel**        | STDIO/HTTP/SSE | 同步+流式 | 60s  | 工具调用、资源访问                  |
| **Memory Channel**     | HTTP/REST      | 同步      | 10s  | Commander → Memory Service 记忆存取 |
| **Multimodal Channel** | HTTP/WebSocket | 流式      | 120s | 音视频流处理、图像传输              |
| **Config Channel**     | Nacos 3.2 gRPC | 长连接    | -    | 配置热更新、服务发现                |

### 4.2 通信层级图

text

```
┌─────────────────────────────────────────────────────────────┐
│  用户请求                                                    │
│    │                                                        │
│    ▼                                                        │
│  Commander Controller (HTTP API)                            │
│    │                                                        │
│    ├──────────┬──────────┬──────────┬──────────┐            │
│    │          │          │          │          │            │
│    ▼          ▼          ▼          ▼          ▼            │
│  意图识别   架构选择   模型路由   记忆加载   任务执行         │
│    │          │          │          │          │            │
│    │          │          │    ┌─────┘          │            │
│    │          │          │    │                │            │
│    │          │          ▼    ▼                │            │
│    │          │       Memory Service           │            │
│    │          │       (port 8081)              │            │
│    │          │                                │            │
│    │          ▼                                │            │
│    │       A2A Channel                         │            │
│    │       (子 Agent 发现与调用)                 │            │
│    │                                           │            │
│    ▼                                           │            │
│  ChatModel (DashScope / OpenAI)                │            │
│  执行具体任务                                    │            │
│                                                │            │
│    ▼                                           │            │
│  CommanderResponse (返回结果)                    │            │
└─────────────────────────────────────────────────────────────┘
```



------

## 五、Commander Agent 设计

### 5.1 核心职责

Commander Agent 是**元级编排器**，不通过 A2A 暴露自己的能力，只负责调用其他业务 Agent。

text

```
Commander Agent 能力矩阵:
┌─────────────────────────────────────────────────────┐
│ 1. 意图识别 (IntentClassifier)                       │
│    • 快速模型分析用户意图                              │
│    • 场景分类 + 复杂度评估                             │
├─────────────────────────────────────────────────────┤
│ 2. 架构选择 (ArchitectureSelector)                   │
│    • Nacos 配置绑定 (规则驱动)                        │
│    • 复杂度匹配 (智能推荐)                             │
│    • 场景-架构映射 (可动态切换)                         │
├─────────────────────────────────────────────────────┤
│ 3. 模型路由 (ChatModelRouter)                        │
│    • 多模型动态切换                                   │
│    • 加权负载均衡                                    │
│    • 能力匹配选择                                    │
├─────────────────────────────────────────────────────┤
│ 4. 记忆集成 (MemoryServiceClient)                    │
│    • 会话记忆加载                                    │
│    • 用户画像获取                                    │
│    • 知识搜索                                       │
│    • 异步写回                                       │
├─────────────────────────────────────────────────────┤
│ 5. 故障恢复 (Fallback)                               │
│    • 架构降级 (复杂→简单)                             │
│    • 模型降级 (高级→稳定)                             │
│    • 执行重试                                       │
└─────────────────────────────────────────────────────┘
```



### 5.2 核心方法





| 方法                          | 返回类型                               | 用途         |
| :---------------------------- | :------------------------------------- | :----------- |
| `execute(request)`            | `CommanderResponse`                    | 同步执行     |
| `executeAsync(request)`       | `CompletableFuture<CommanderResponse>` | 异步执行     |
| `executeStream(request)`      | `Flux<CommanderResponse>`              | 阶段级流式   |
| `executeTokenStream(request)` | `Flux<String>`                         | Token 级流式 |
| `fallbackExecute(request, t)` | `CommanderResponse`                    | 降级执行     |

------

## 六、A2A 通信协议设计

### 6.1 消息协议

java

```
@Data
@Builder
public class A2AMessage implements Serializable {
    private String messageId;           // 消息唯一 ID (UUID v7)
    private String senderAgentId;       // 发送方 Agent ID
    private String receiverAgentId;     // 接收方 Agent ID (null=广播)
    private A2AMessageType messageType; // 消息类型
    private Map<String, String> headers;// 元数据头
    private Object payload;             // 消息体
    private long timestamp;             // 时间戳
    private String correlationId;       // 关联 ID
    private int priority;               // 优先级 (1-10)
}
```



### 6.2 消息类型

java

```
public enum A2AMessageType {
    // 基础通信
    DIRECT_REQUEST, DIRECT_RESPONSE, BROADCAST, HEARTBEAT,
    // 任务协作
    TASK_DELEGATION, TASK_RESULT, TASK_PROGRESS,
    // 能力管理
    CAPABILITY_QUERY, CAPABILITY_RESPONSE, AGENT_DISCOVERY,
    // 上下文同步
    CONTEXT_UPDATE, MEMORY_SYNC, KNOWLEDGE_QUERY,
    // 协商与辩论
    OPINION_SHARE, REBUTTAL, CONSENSUS_CHECK, CONSENSUS_RESULT,
    // 多模态协作
    MULTIMODAL_INPUT, MULTIMODAL_OUTPUT, MODALITY_CONVERSION,
    // 异常处理
    ERROR_NOTIFICATION, AGENT_STATUS_CHANGE, INTERVENTION_REQUEST
}
```



------

## 七、MCP 协议集成设计

### 7.1 MCP 服务清单





| MCP 服务                | 类型  | 用途         |
| :---------------------- | :---- | :----------- |
| `local-filesystem`      | STDIO | 文件读写     |
| `local-calculator`      | STDIO | 数学计算     |
| `remote-database`       | HTTP  | 数据库查询   |
| `remote-search`         | SSE   | 网络搜索     |
| `remote-vision-ocr`     | HTTP  | 图像识别/OCR |
| `remote-speech-asr`     | HTTP  | 语音识别     |
| `remote-speech-tts`     | HTTP  | 语音合成     |
| `remote-video-analysis` | HTTP  | 视频分析     |

### 7.2 MCP 配置

yaml

```
spring:
  ai:
    mcp:
      client:
        type: async
        request-timeout: 60s
        toolcallback:
          enabled: true
        sse:
          connections:
            local-filesystem:
              url: http://localhost:8088
            remote-database:
              url: http://database-mcp-service:8081/mcp
            remote-search:
              url: https://api.searchmcp.com/v1

```



------

## 八、多模型动态路由设计

### 8.1 模型配置（Nacos）

yaml

```
# Nacos: platform-model-routing-config.yaml
platform:
  air:
    models:
      - model-id: qwen-turbo
        type: DASHSCOPE
        provider: alibaba
        model-name: qwen-turbo
        capabilities: [FAST_RESPONSE, INTENT_CLASSIFICATION]
        weight: 5
        enabled: true
      - model-id: qwen-max
        type: DASHSCOPE
        provider: alibaba
        model-name: qwen-max
        capabilities: [REASONING, CODING, ANALYSIS, LONG_CONTEXT]
        weight: 10
        enabled: true
      - model-id: deepseek-v3
        type: OPENAI_COMPATIBLE
        provider: deepseek
        model-name: deepseek-chat
        capabilities: [REASONING, CODING, LONG_CONTEXT]
        weight: 9
        enabled: true

```



### 8.2 路由流程

text

```
用户请求
    │
    ▼
IntentClassifier.analyzeIntent()  →  scenario + complexity + requiredCapabilities
    │
    ▼
ArchitectureSelector.selectArchitecture()  →  architectureId
    │
    ▼
ChatModelRouter.selectModel()  →  modelId
    │   ├─ 配置绑定：优先使用 scenario-architecture-mapping
    │   ├─ 复杂度匹配：HIGH → qwen-max, MEDIUM → qwen-plus, LOW → qwen-turbo
    │   └─ 加权轮询：weighted-round-robin
    │
    ▼
ChatClientConfiguration.createModel(modelId)  →  ChatModel 实例
    │   ├─ DASHSCOPE  → DashScopeChatModel
    │   └─ OPENAI_COMPATIBLE → OpenAiChatModel
    │
    ▼
CommanderAgent.executeTask()  →  CommanderResponse

```



------

## 九、Memory Service 设计

### 9.1 定位

Memory Service 是**独立的数据服务**，不是 Agent。它只负责记忆的存取和清洗，不参与推理、决策或工具调用。

### 9.2 八种记忆类型





| #    | 类型               | 存储              | 特点             | 检索方式     | 有效期    |
| :--- | :----------------- | :---------------- | :--------------- | :----------- | :-------- |
| 1    | **短期热记忆**     | Redis             | 当前会话上下文   | 会话 Key     | TTL 30min |
| 2    | **结构化精确记忆** | PostgreSQL        | 身份信息，不可变 | Key 精确查询 | 永久      |
| 3    | **画像记忆**       | PostgreSQL JSONB  | 动态进化         | Key 精确查询 | 长期      |
| 4    | **行为记忆**       | PostgreSQL 分区表 | 时序数据         | 时间范围查询 | 90天      |
| 5    | **长期知识记忆**   | Chroma            | 语义搜索         | 向量相似度   | 永久      |
| 6    | **偏好记忆**       | PostgreSQL JSONB  | 用户显式配置     | Key 精确查询 | 长期      |
| 7    | **关系记忆**       | PostgreSQL        | 跨用户关系       | 双向查询     | 长期      |
| 8    | **决策记忆**       | PostgreSQL        | 路由决策记录     | 统计分析     | 永久      |

### 9.3 API 接口设计





| 记忆类型 | GET                      | POST/PUT               | DELETE                 | 说明          |
| :------- | :----------------------- | :--------------------- | :--------------------- | :------------ |
| 身份记忆 | `/identity/{userId}`     | `/identity`            | `/identity/{userId}`   | 用户身份信息  |
| 会话记忆 | `/session/{sessionId}`   | `/session/{sessionId}` | `/session/{sessionId}` | 会话消息列表  |
| 画像记忆 | `/profile/{userId}`      | `/profile/{userId}`    | -                      | 用户画像      |
| 行为记忆 | `/behavior/{userId}`     | `/behavior`            | -                      | 操作日志      |
| 长期知识 | `/knowledge/search`      | `/knowledge`           | -                      | 语义搜索/保存 |
| 偏好记忆 | `/preference/{userId}`   | `/preference/{userId}` | -                      | 用户偏好配置  |
| 关系记忆 | `/relationship/{userId}` | `/relationship`        | -                      | 跨用户关系    |
| 决策记忆 | `/decision/analysis`     | `/decision`            | -                      | 路由决策分析  |

### 9.4 记忆清洗

text

```
原始记忆
    │
    ▼
第一层：规则清洗（无需 LLM）
  ├─ 去重：相同信息合并
  ├─ 去噪：过滤无效信息
  └─ 格式化：统一字段格式
    │
    ▼
第二层：LLM 智能清洗（异步）
  ├─ 摘要提取：长文本压缩
  ├─ 情感过滤：过滤情绪宣泄
  └─ 知识提取：提取结构化知识

```



------

## 十、多模态 Agent 体系设计

### 10.1 能力矩阵

text

```
┌─────────────────────────────────────────────────────────────────────┐
│                     多模态 Agent 能力矩阵                            │
│                                                                      │
│  输入模态 (识别/理解)                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐       │
│  │ 语音识别  │  │ 图像识别  │  │ 视频分析  │  │ 文档 OCR     │       │
│  │ ASR      │  │ CV       │  │ Video    │  │ Document    │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘       │
│                                                                      │
│  输出模态 (生成/合成)                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐       │
│  │ 语音合成  │  │ 图像生成  │  │ 视频生成  │  │ 多模态融合    │       │
│  │ TTS      │  │ GenAI    │  │ Video    │  │ Multimodal  │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────────────┘

```



### 10.2 多模态 Agent 清单





| Agent                  | 类型   | 能力               | MCP 工具              |
| :--------------------- | :----- | :----------------- | :-------------------- |
| SpeechRecognitionAgent | INPUT  | 语音→文本          | remote-speech-asr     |
| TextToSpeechAgent      | OUTPUT | 文本→语音          | remote-speech-tts     |
| ImageAnalysisAgent     | INPUT  | 图像→文本/OCR/检测 | remote-vision-ocr     |
| ImageGenerationAgent   | OUTPUT | 文本→图像/编辑     | -                     |
| VideoAnalysisAgent     | INPUT  | 视频→文本/摘要     | remote-video-analysis |
| VideoGenerationAgent   | OUTPUT | 文本/图像→视频     | -                     |
| MultimodalFusionAgent  | BOTH   | 跨模态问答/转换    | 全部                  |

------

## 十一、数据流与状态管理

### 11.1 Commander 执行流程

text

```
CommanderRequest
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  阶段1: 意图识别 (IntentClassifier)                           │
│  → IntentAnalysis { scenario, complexity, modality }         │
├─────────────────────────────────────────────────────────────┤
│  阶段2: 架构选择 (ArchitectureSelector)                       │
│  → ArchitectureSelection { architectureId, architectureType }│
├─────────────────────────────────────────────────────────────┤
│  阶段3: 模型路由 (ChatModelRouter)                            │
│  → ModelSelection { modelId, modelName, provider }           │
├─────────────────────────────────────────────────────────────┤
│  阶段4: 记忆加载 (MemoryServiceClient)                        │
│  → Identity + Profile + Session + Knowledge                 │
├─────────────────────────────────────────────────────────────┤
│  阶段5: 构建增强上下文                                         │
│  → enhancedInput = userInput + memory + metadata             │
├─────────────────────────────────────────────────────────────┤
│  阶段6: 执行任务 (executeSequential/Parallel/LlmRouting...)   │
│  → Map<String, Object> result                                │
├─────────────────────────────────────────────────────────────┤
│  阶段7: 异步写回 (CompletableFuture)                          │
│  → 保存行为 / 更新画像 / 记录决策                              │
├─────────────────────────────────────────────────────────────┤
│  阶段8: 返回结果 (CommanderResponse)                          │
└─────────────────────────────────────────────────────────────┘

```



### 11.2 流式执行流程

text

```
executeStream(request)
    │
    ├─ 推送进度: INTENT_ANALYSIS
    ├─ 意图识别完成
    ├─ 推送进度: ARCHITECTURE_SELECTION
    ├─ 架构选择完成
    ├─ 推送进度: MODEL_SELECTION
    ├─ 模型选择完成
    ├─ 推送进度: TASK_EXECUTION
    ├─ 任务执行中...
    ├─ 推送进度: MEMORY_SAVE
    ├─ 记忆保存中...
    └─ 推送最终结果: CommanderResponse (完整)

```



------

## 十二、部署架构设计

### 12.1 Docker Compose

yaml

```
version: '3.8'

services:
  # ==================== 基础设施 ====================
  nacos:
    image: nacos/nacos-server:v3.2.0
    container_name: nacos
    environment:
      - MODE=standalone
      - NACOS_AUTH_ENABLE=true
    ports:
      - "8848:8848"
      - "9848:9848"

  postgres:
    image: postgres:15-alpine
    container_name: postgres
    environment:
      - POSTGRES_DB=memory
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"

  chroma:
    image: chromadb/chroma:latest
    container_name: chroma
    ports:
      - "8000:8000"

  # ==================== 核心服务 ====================
  memory-service:
    build: ./memory-service
    container_name: memory-service
    ports:
      - "8081:8081"
    environment:
      - POSTGRES_URL=jdbc:postgresql://postgres:5432/memory
      - REDIS_HOST=redis
      - CHROMA_HOST=chroma
    depends_on:
      - postgres
      - redis
      - chroma

  commander-service:
    build: ./commanderAgent
    container_name: commander-service
    ports:
      - "10010:10010"
    environment:
      - NACOS_SERVER_ADDR=nacos:8848
      - DASHSCOPE_API_KEY=${DASHSCOPE_API_KEY}
      - MEMORY_SERVICE_URL=http://memory-service:8081
    depends_on:
      - nacos
      - memory-service

```



### 12.2 环境变量清单





| 变量名               | 用途                | 示例值                                   |
| :------------------- | :------------------ | :--------------------------------------- |
| `DASHSCOPE_API_KEY`  | DashScope API 密钥  | `sk-xxx`                                 |
| `DEEPSEEK_API_KEY`   | DeepSeek API 密钥   | `sk-xxx`                                 |
| `OPENAI_API_KEY`     | OpenAI API 密钥     | `sk-xxx`                                 |
| `NACOS_SERVER_ADDR`  | Nacos 服务地址      | `127.0.0.1:8848`                         |
| `MEMORY_SERVICE_URL` | Memory Service 地址 | `http://memory-service:8081`             |
| `POSTGRES_URL`       | PostgreSQL 连接地址 | `jdbc:postgresql://postgres:5432/memory` |
| `REDIS_HOST`         | Redis 地址          | `redis`                                  |
| `CHROMA_HOST`        | Chroma 地址         | `chroma`                                 |

------

## 十三、监控与可观测性设计

### 13.1 指标收集





| 指标类型  | 指标名称                       | 说明         |
| :-------- | :----------------------------- | :----------- |
| Commander | `commander.execution.duration` | 执行耗时     |
| Commander | `commander.execution.count`    | 执行次数     |
| Model     | `model.tokens.total`           | Token 用量   |
| Model     | `model.latency`                | 模型延迟     |
| A2A       | `a2a.messages.total`           | A2A 消息量   |
| A2A       | `a2a.message.latency`          | A2A 延迟     |
| MCP       | `mcp.tool.duration`            | MCP 工具耗时 |
| Memory    | `memory.operation.duration`    | 记忆操作耗时 |
| Memory    | `memory.cache.hit.ratio`       | 缓存命中率   |

### 13.2 监控端点

yaml

```
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info,circuitbreakers
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: commander-service
      version: 4.0.0

```



------

## 十四、配置管理设计

### 14.1 Nacos 配置文件清单





| Data ID                                    | Group             | 用途               |
| :----------------------------------------- | :---------------- | :----------------- |
| `platform-commander-config.yaml`           | `COMMANDER_GROUP` | Commander 策略配置 |
| `platform-model-routing-config.yaml`       | `COMMANDER_GROUP` | 模型定义与路由     |
| `chat-model-api-keys.properties`           | `COMMANDER_GROUP` | 模型 API Key       |
| `comander-resilience4j.yaml`               | `COMMANDER_GROUP` | 熔断重试配置       |
| `spring-ai-chroma-vectorstore-config.yaml` | `COMMANDER_GROUP` | Chroma 连接信息    |
| `spring-ai-mcp-config.yaml`                | `COMMANDER_GROUP` | MCP 客户端配置     |
| `staic-application-metrics.yaml`           | `DEFAULT_GROUP`   | 监控指标静态配置   |

### 14.2 本地配置清单





| 配置项                                             | 说明               |
| :------------------------------------------------- | :----------------- |
| `spring.application.name`                          | 服务标识           |
| `spring.cloud.nacos.*`                             | Nacos 连接引导配置 |
| `spring.ai.alibaba.a2a.nacos.*`                    | A2A 连接引导配置   |
| `spring.ai.dashscope.chat.options.model`           | 兜底默认模型       |
| `logging.pattern.console`                          | 兜底日志格式       |
| `management.endpoints.web.exposure.include=health` | 健康检查兜底       |
| `resilience4j.*`                                   | 容错兜底           |

------

## 附录

### A. 核心 API 接口清单





| 方法   | 路径                                      | 说明                       |
| :----- | :---------------------------------------- | :------------------------- |
| `POST` | `/api/v1/commander/execute`               | Commander 同步执行         |
| `POST` | `/api/v1/commander/execute/async`         | Commander 异步执行         |
| `POST` | `/api/v1/commander/execute/stream`        | Commander 流式执行 (SSE)   |
| `POST` | `/api/v1/commander/execute/stream/token`  | Commander Token 流式 (SSE) |
| `GET`  | `/api/v1/commander/history/{executionId}` | 查询执行历史               |
| `GET`  | `/api/v1/commander/architectures`         | 可用架构列表               |
| `GET`  | `/api/v1/commander/models`                | 可用模型列表               |
| `GET`  | `/api/v1/commander/health`                | Commander 健康检查         |
| `GET`  | `/api/v1/memory/identity/{userId}`        | 获取用户身份               |
| `GET`  | `/api/v1/memory/session/{sessionId}`      | 获取会话记忆               |
| `GET`  | `/api/v1/memory/profile/{userId}`         | 获取用户画像               |
| `POST` | `/api/v1/memory/knowledge/search`         | 语义搜索知识               |
| `GET`  | `/api/v1/memory/preference/{userId}`      | 获取用户偏好               |

### B. 错误码定义





| 错误码    | HTTP 状态 | 说明                 |
| :-------- | :-------- | :------------------- |
| `CMD-001` | 500       | 架构选择失败         |
| `CMD-002` | 500       | 模型路由失败         |
| `CMD-003` | 504       | Commander 执行超时   |
| `CMD-004` | 200       | 降级执行（正常降级） |
| `A2A-001` | 404       | 目标 Agent 未找到    |
| `A2A-002` | 504       | A2A 通信超时         |
| `A2A-003` | 503       | A2A 熔断开启         |
| `MCP-001` | 404       | MCP 工具未找到       |
| `MCP-002` | 500       | MCP 工具执行异常     |
| `MM-001`  | 400       | 不支持的模态类型     |
| `MEM-001` | 404       | 记忆记录不存在       |
| `MEM-002` | 500       | 记忆存储异常         |
| `MEM-003` | 429       | 记忆服务限流         |

### C. 版本历史





| 版本       | 日期           | 变更                                                         |
| :--------- | :------------- | :----------------------------------------------------------- |
| v1.0.0     | 2026-05-20     | 初始版本，基础文本 Agent 架构                                |
| v2.0.0     | 2026-05-24     | 增加 Commander、A2A、MCP 集成                                |
| v2.1.0     | 2026-05-24     | 修正 Nacos 版本为 3.2.0                                      |
| v3.0.0     | 2026-05-24     | 增加完整多模态 Agent 体系                                    |
| **v4.0.0** | **2026-05-27** | **新增 Memory Service 独立服务，完善 8 种记忆分类与持久化方案** |