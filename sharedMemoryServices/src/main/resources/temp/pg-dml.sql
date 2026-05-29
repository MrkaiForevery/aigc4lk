-- 1. 创建新用户
CREATE USER aigc4lk WITH PASSWORD '123456';
-- 2. 创建新数据库，并将所有者设为该用户
CREATE DATABASE aigc4lk_memory_db OWNER aigc4lk;

-- ============================================================
-- Memory Service 数据库表结构
-- 数据库: aigc4lk_memory_db
-- 适用: PostgreSQL 15+
-- ============================================================

-- 1. 身份记忆表（用户基础信息，精确查询）
CREATE TABLE IF NOT EXISTS memory_identity (
                                               user_id         VARCHAR(64) PRIMARY KEY,          -- 用户唯一标识
    username        VARCHAR(128),                     -- 用户名
    phone           VARCHAR(20),                      -- 手机号
    email           VARCHAR(256),                     -- 邮箱
    member_level    VARCHAR(32),                      -- 会员等级 (VIP1, VIP2...)
    role            VARCHAR(32) DEFAULT 'user',       -- 角色 (user/admin)
    created_at      TIMESTAMP DEFAULT NOW(),          -- 创建时间
    updated_at      TIMESTAMP DEFAULT NOW()           -- 更新时间
    );
COMMENT ON TABLE memory_identity IS '用户身份记忆表';
COMMENT ON COLUMN memory_identity.user_id IS '用户唯一标识';
COMMENT ON COLUMN memory_identity.username IS '用户名';
COMMENT ON COLUMN memory_identity.phone IS '手机号';
COMMENT ON COLUMN memory_identity.email IS '电子邮箱';
COMMENT ON COLUMN memory_identity.member_level IS '会员等级';
COMMENT ON COLUMN memory_identity.role IS '角色';
COMMENT ON COLUMN memory_identity.created_at IS '创建时间';
COMMENT ON COLUMN memory_identity.updated_at IS '更新时间';

-- 2. 画像记忆表（用户画像，动态进化）
CREATE TABLE IF NOT EXISTS memory_profile (
                                              user_id                 VARCHAR(64) PRIMARY KEY,   -- 用户唯一标识
    preferred_model         VARCHAR(64),               -- 偏好的大模型 (qwen-max, deepseek-v3...)
    preferred_architecture  VARCHAR(64),               -- 偏好的执行架构 (sequential-pipeline...)
    technical_level         VARCHAR(32),               -- 技术水平 (beginner/intermediate/advanced)
    topics_of_interest      JSONB DEFAULT '[]',        -- 感兴趣的话题 (JSON数组)
    communication_style     VARCHAR(32),               -- 沟通风格 (formal/casual/detailed)
    extra_attrs             JSONB DEFAULT '{}',        -- 扩展属性 (任意键值对)
    updated_at              TIMESTAMP DEFAULT NOW()    -- 更新时间
    );
COMMENT ON TABLE memory_profile IS '用户画像记忆表';
COMMENT ON COLUMN memory_profile.user_id IS '用户唯一标识';
COMMENT ON COLUMN memory_profile.preferred_model IS '偏好的大模型';
COMMENT ON COLUMN memory_profile.preferred_architecture IS '偏好的执行架构';
COMMENT ON COLUMN memory_profile.technical_level IS '技术水平';
COMMENT ON COLUMN memory_profile.topics_of_interest IS '感兴趣的话题(JSON数组)';
COMMENT ON COLUMN memory_profile.communication_style IS '沟通风格';
COMMENT ON COLUMN memory_profile.extra_attrs IS '扩展属性(JSON)';
COMMENT ON COLUMN memory_profile.updated_at IS '更新时间';

-- 3. 行为记忆表（用户行为记录，按时间查询）
CREATE TABLE IF NOT EXISTS memory_behavior (
                                               id              BIGSERIAL PRIMARY KEY,             -- 自增主键
                                               user_id         VARCHAR(64) NOT NULL,              -- 用户唯一标识
    session_id      VARCHAR(64),                      -- 会话ID
    action_type     VARCHAR(32),                      -- 行为类型 (query/intent/result/click/feedback)
    content         TEXT,                             -- 行为内容
    metadata        JSONB DEFAULT '{}',               -- 附加元数据
    created_at      TIMESTAMP DEFAULT NOW()            -- 创建时间
    );
CREATE INDEX IF NOT EXISTS idx_behavior_user_time ON memory_behavior(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_session ON memory_behavior(session_id);
COMMENT ON TABLE memory_behavior IS '用户行为记忆表';
COMMENT ON COLUMN memory_behavior.id IS '自增主键';
COMMENT ON COLUMN memory_behavior.user_id IS '用户唯一标识';
COMMENT ON COLUMN memory_behavior.session_id IS '会话ID';
COMMENT ON COLUMN memory_behavior.action_type IS '行为类型';
COMMENT ON COLUMN memory_behavior.content IS '行为内容';
COMMENT ON COLUMN memory_behavior.metadata IS '附加元数据(JSON)';
COMMENT ON COLUMN memory_behavior.created_at IS '创建时间';

-- 4. 偏好记忆表（用户显式配置）
CREATE TABLE IF NOT EXISTS memory_preference (
                                                 user_id             VARCHAR(64) PRIMARY KEY,       -- 用户唯一标识
    output_style        VARCHAR(32) DEFAULT 'detailed',-- 输出风格 (concise/detailed)
    show_intermediate   BOOLEAN DEFAULT true,          -- 是否展示中间步骤
    preferred_models    JSONB DEFAULT '[]',            -- 按场景的模型偏好 (JSON对象)
    auto_fallback       BOOLEAN DEFAULT true,          -- 是否自动降级
    updated_at          TIMESTAMP DEFAULT NOW()        -- 更新时间
    );
COMMENT ON TABLE memory_preference IS '用户偏好记忆表';
COMMENT ON COLUMN memory_preference.user_id IS '用户唯一标识';
COMMENT ON COLUMN memory_preference.output_style IS '输出风格';
COMMENT ON COLUMN memory_preference.show_intermediate IS '是否展示中间步骤';
COMMENT ON COLUMN memory_preference.preferred_models IS '按场景的模型偏好(JSON)';
COMMENT ON COLUMN memory_preference.auto_fallback IS '是否自动降级';
COMMENT ON COLUMN memory_preference.updated_at IS '更新时间';

-- 5. 关系记忆表（跨用户关系）
CREATE TABLE IF NOT EXISTS memory_relationship (
                                                   id              BIGSERIAL PRIMARY KEY,             -- 自增主键
                                                   user_id         VARCHAR(64) NOT NULL,              -- 主体用户
    related_user    VARCHAR(64) NOT NULL,              -- 关联用户
    relation_type   VARCHAR(32),                      -- 关系类型 (teammate/superior/client)
    project_id      VARCHAR(64),                      -- 关联的项目ID
    created_at      TIMESTAMP DEFAULT NOW()            -- 创建时间
    );
CREATE INDEX IF NOT EXISTS idx_relationship_user ON memory_relationship(user_id);
CREATE INDEX IF NOT EXISTS idx_relationship_related ON memory_relationship(related_user);
COMMENT ON TABLE memory_relationship IS '用户关系记忆表';
COMMENT ON COLUMN memory_relationship.id IS '自增主键';
COMMENT ON COLUMN memory_relationship.user_id IS '主体用户';
COMMENT ON COLUMN memory_relationship.related_user IS '关联用户';
COMMENT ON COLUMN memory_relationship.relation_type IS '关系类型';
COMMENT ON COLUMN memory_relationship.project_id IS '关联的项目ID';
COMMENT ON COLUMN memory_relationship.created_at IS '创建时间';

-- 6. 决策记忆表（记录Commander的架构/模型选择决策）
CREATE TABLE IF NOT EXISTS memory_decision (
                                               id                      BIGSERIAL PRIMARY KEY,     -- 自增主键
                                               user_id                 VARCHAR(64),               -- 用户ID
    session_id              VARCHAR(64),               -- 会话ID
    intent_scenario         VARCHAR(64),               -- 意图场景 (DOCUMENT_GENERATION...)
    selected_architecture   VARCHAR(64),               -- 选择的架构
    selected_model          VARCHAR(64),               -- 选择的模型
    selection_reason        TEXT,                      -- 选择原因
    execution_time_ms       BIGINT,                    -- 执行耗时(毫秒)
    success                 BOOLEAN,                   -- 是否成功
    created_at              TIMESTAMP DEFAULT NOW()    -- 创建时间
    );
CREATE INDEX IF NOT EXISTS idx_decision_user_time ON memory_decision(user_id, created_at DESC);
COMMENT ON TABLE memory_decision IS '决策记忆表';
COMMENT ON COLUMN memory_decision.id IS '自增主键';
COMMENT ON COLUMN memory_decision.user_id IS '用户ID';
COMMENT ON COLUMN memory_decision.session_id IS '会话ID';
COMMENT ON COLUMN memory_decision.intent_scenario IS '意图场景';
COMMENT ON COLUMN memory_decision.selected_architecture IS '选择的架构';
COMMENT ON COLUMN memory_decision.selected_model IS '选择的模型';
COMMENT ON COLUMN memory_decision.selection_reason IS '选择原因';
COMMENT ON COLUMN memory_decision.execution_time_ms IS '执行耗时(毫秒)';
COMMENT ON COLUMN memory_decision.success IS '是否成功';
COMMENT ON COLUMN memory_decision.created_at IS '创建时间';

-- 冷热数据分离
-- 归档表（结构与主表一致，但索引精简）
CREATE TABLE memory_behavior_archive (
   LIKE memory_behavior INCLUDING ALL
);

-- 归档表只需要 user_id 和 created_at 索引（偶尔按用户查询历史）
CREATE INDEX idx_archive_user_time ON memory_behavior_archive(user_id, created_at DESC);


-- 降级的索引表
CREATE TABLE knowledge_index (
                                 doc_id        VARCHAR(128) PRIMARY KEY,          -- Chroma 文档 ID
                                 user_id       VARCHAR(64),
                                 source        VARCHAR(32),
                                 created_at    TIMESTAMP DEFAULT NOW(),
                                 last_access   TIMESTAMP DEFAULT NOW(),
                                 status        VARCHAR(16) DEFAULT 'active'       -- active / archived
);
CREATE INDEX idx_ki_status_access ON knowledge_index(status, last_access);