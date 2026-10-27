--- 表设计
domain_definition
biz_domian_definition
id,mcp_type,domian_type,domian_level,from_source_id,from_source_name,domian_id,domian_name,


CREATE SCHEMA IF NOT EXISTS permission_domain_namespace;

-- 权限策略表
CREATE TABLE IF NOT EXISTS permission_domain_namespace.role_policy_definition (
                                                                     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                                                     policy_role VARCHAR(64) NOT NULL,
    domain      VARCHAR(64) NOT NULL,
    sensitivity VARCHAR(32) NOT NULL,
    effect      VARCHAR(8)  NOT NULL,
    CONSTRAINT uk_role_domain_sens UNIQUE (policy_role, domain, sensitivity)
    );

COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.policy_role IS '策略角色名';
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.domain      IS '业务域，*表示通配';
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.sensitivity IS '敏感度(read/write/delete/export)，*表示通配';
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.effect      IS 'allow 或 deny';

ALTER TABLE permission_domain_namespace.role_policy_definition ADD domain_id varchar(64) NOT NULL;
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.domain_id IS '主域id，*表示通配';
ALTER TABLE permission_domain_namespace.role_policy_definition ADD biz_domian varchar(258) NULL;
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.biz_domian IS '业务域';
ALTER TABLE permission_domain_namespace.role_policy_definition ALTER COLUMN "domain" TYPE varchar(258) USING "domain"::varchar;
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition."domain" IS '主域';
ALTER TABLE permission_domain_namespace.role_policy_definition ADD biz_domian_id varchar(64) NULL;
COMMENT ON COLUMN permission_domain_namespace.role_policy_definition.biz_domian_id IS '业务域id，*表示通配';
ALTER TABLE permission_domain_namespace.role_policy_definition ALTER COLUMN effect TYPE varchar(32) USING effect::varchar;


-- 用户角色映射表
CREATE TABLE IF NOT EXISTS permission_domain_namespace.agent_user_role_mapping (
                                                                      id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                                                      agent_id    VARCHAR(64) NOT NULL,
    user_role   VARCHAR(64) NOT NULL,
    policy_role VARCHAR(64) NOT NULL,
    CONSTRAINT uk_agent_user_role UNIQUE (agent_id, user_role, policy_role)
    );

COMMENT ON COLUMN permission_domain_namespace.agent_user_role_mapping.agent_id    IS '例如 agent-sales';
COMMENT ON COLUMN permission_domain_namespace.agent_user_role_mapping.user_role   IS 'SSO Token 携带的用户角色';
COMMENT ON COLUMN permission_domain_namespace.agent_user_role_mapping.policy_role IS '映射到的策略角色';

-- 用户覆盖映射表
CREATE TABLE IF NOT EXISTS permission_domain_namespace.user_override_role_mapping (
                                                 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                                 agent_id  VARCHAR(64)  NOT NULL,
    user_id   VARCHAR(64)  NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    effect    VARCHAR(8)   NOT NULL,
    CONSTRAINT uk_agent_user_tool UNIQUE (agent_id, user_id, tool_name)
    );

COMMENT ON COLUMN permission_domain_namespace.user_override_role_mapping.effect IS 'allow 或 deny';