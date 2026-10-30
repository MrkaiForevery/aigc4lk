-- CREATE SCHEMA IF NOT EXISTS auth_model;
--- 权限角色定义表
DROP TABLE IF EXISTS auth_role_def;
CREATE TABLE auth_role_def(
                              id SERIAL,
                              role_code VARCHAR NOT NULL,
                              role_name VARCHAR NOT NULL,
                              description VARCHAR,
                              del_logic BIT NOT NULL,
                              created_by VARCHAR,
                              created_time TIMESTAMP,
                              update_by VARCHAR,
                              update_time TIMESTAMP,
                              PRIMARY KEY (id)
);
COMMENT ON COLUMN auth_role_def.id IS '主键id';
COMMENT ON COLUMN auth_role_def.role_code IS '角色编号';
COMMENT ON COLUMN auth_role_def.role_name IS '角色名称';
COMMENT ON COLUMN auth_role_def.description IS '角色描述';
COMMENT ON COLUMN auth_role_def.del_logic IS '逻辑删除标识';
COMMENT ON COLUMN auth_role_def.created_by IS '创建者';
COMMENT ON COLUMN auth_role_def.created_time IS '创建时间';
COMMENT ON COLUMN auth_role_def.update_by IS '修改者';
COMMENT ON COLUMN auth_role_def.update_time IS '修改时间';
COMMENT ON TABLE auth_role_def IS 'auth_role_def;权限角色定义表';

CREATE  UNIQUE INDEX UNQ_ROLE_CODE ON auth_role_def ( role_code );

--- 权限域定义表(树型结构)
DROP TABLE IF EXISTS auth_domain_def;
CREATE TABLE auth_domain_def(
                                id SERIAL,
                                domain_level INT2 NOT NULL,
                                father_domain_code VARCHAR NOT NULL,
                                domain_code VARCHAR,
                                domain_name VARCHAR,
                                description VARCHAR,
                                del_logic BIT NOT NULL,
                                created_by VARCHAR,
                                created_time TIMESTAMP,
                                update_by VARCHAR,
                                update_time TIMESTAMP,
                                PRIMARY KEY (id)
);
COMMENT ON COLUMN auth_domain_def.id IS '主键id';
COMMENT ON COLUMN auth_domain_def.domain_level IS '域级别';
COMMENT ON COLUMN auth_domain_def.father_domain_code IS '父域编码';
COMMENT ON COLUMN auth_domain_def.domain_code IS '域编码';
COMMENT ON COLUMN auth_domain_def.domain_name IS '域名称';
COMMENT ON COLUMN auth_domain_def.description IS '域描述';
COMMENT ON COLUMN auth_domain_def.del_logic IS '逻辑删除标识';
COMMENT ON COLUMN auth_domain_def.created_by IS '创建者';
COMMENT ON COLUMN auth_domain_def.created_time IS '创建时间';
COMMENT ON COLUMN auth_domain_def.update_by IS '修改者';
COMMENT ON COLUMN auth_domain_def.update_time IS '修改时间';
COMMENT ON TABLE auth_domain_def IS 'auth_domain_def;权限域定义表(树型结构)';

CREATE  UNIQUE INDEX UNQ_DOMAIN_CODE ON auth_domain_def (domain_code DESC);


--- 权限控制元素定义表
DROP TABLE IF EXISTS auth_element_def;
CREATE TABLE auth_element_def(
                                 id SERIAL4,
                                 element_type VARCHAR NOT NULL,
                                 sensitivity_type VARCHAR NOT NULL,
                                 element_code VARCHAR NOT NULL,
                                 element_name VARCHAR NOT NULL,
                                 relation_domain_code VARCHAR NOT NULL,
                                 description VARCHAR,
                                 del_logic BIT NOT NULL,
                                 created_by VARCHAR,
                                 created_time TIMESTAMP,
                                 update_by VARCHAR,
                                 update_time TIMESTAMP,
                                 PRIMARY KEY (id)
);
COMMENT ON COLUMN auth_element_def.id IS '主键id';
COMMENT ON COLUMN auth_element_def.element_type IS '元素类型(tool/resource/prompt)';
COMMENT ON COLUMN auth_element_def.sensitivity_type IS '元素敏感度类型(read/write/...)';
COMMENT ON COLUMN auth_element_def.element_code IS '元素编码';
COMMENT ON COLUMN auth_element_def.element_name IS '元素名称';
COMMENT ON COLUMN auth_element_def.relation_domain_code IS '元素所在域编码';
COMMENT ON COLUMN auth_element_def.description IS '元素描述';
COMMENT ON COLUMN auth_element_def.del_logic IS '逻辑删除标识';
COMMENT ON COLUMN auth_element_def.created_by IS '创建者';
COMMENT ON COLUMN auth_element_def.created_time IS '创建时间';
COMMENT ON COLUMN auth_element_def.update_by IS '修改者';
COMMENT ON COLUMN auth_element_def.update_time IS '修改时间';
COMMENT ON TABLE auth_element_def IS 'auth_element_def;权限控制元素定义表';

CREATE  UNIQUE INDEX UNQ_ELEMENT_CODE ON auth_element_def ( element_code DESC);


--- 权限角色绑定权限元素策略表
DROP TABLE IF EXISTS role_bind_element_policy;
CREATE TABLE role_bind_element_policy(
                                         id SERIAL,
                                         role_code VARCHAR NOT NULL,
                                         element_code VARCHAR NOT NULL,
                                         description VARCHAR,
                                         del_logic BIT NOT NULL,
                                         created_by VARCHAR,
                                         created_time TIMESTAMP,
                                         update_by VARCHAR,
                                         update_time TIMESTAMP,
                                         PRIMARY KEY (id)
);
COMMENT ON COLUMN role_bind_element_policy.id IS '主键id';
COMMENT ON COLUMN role_bind_element_policy.role_code IS '角色编号';
COMMENT ON COLUMN role_bind_element_policy.element_code IS '元素编码';
COMMENT ON COLUMN role_bind_element_policy.description IS '策略描述';
COMMENT ON COLUMN role_bind_element_policy.del_logic IS '逻辑删除标识';
COMMENT ON COLUMN role_bind_element_policy.created_by IS '创建者';
COMMENT ON COLUMN role_bind_element_policy.created_time IS '创建时间';
COMMENT ON COLUMN role_bind_element_policy.update_by IS '修改者';
COMMENT ON COLUMN role_bind_element_policy.update_time IS '修改时间';
COMMENT ON TABLE role_bind_element_policy IS 'role_bind_element_policy;权限角色绑定权限元素策略表';


--- 客户端key 绑定 角色 策略表
DROP TABLE IF EXISTS client_key_bind_role_policy;
CREATE TABLE client_key_bind_role_policy(
                                            id SERIAL,
                                            client_type VARCHAR NOT NULL,
                                            client_mapping_key_json JSON NOT NULL,
                                            bind_role_code VARCHAR NOT NULL,
                                            description VARCHAR,
                                            del_logic BIT NOT NULL,
                                            created_by VARCHAR,
                                            created_time TIMESTAMP,
                                            update_by VARCHAR,
                                            update_time TIMESTAMP,
                                            PRIMARY KEY (id)
);
COMMENT ON COLUMN client_key_bind_role_policy.id IS '主键id';
COMMENT ON COLUMN client_key_bind_role_policy.client_type IS '客户端类型(如dify/codex/....)';
COMMENT ON COLUMN client_key_bind_role_policy.client_mapping_key_json IS '客户端映射key(json类)';
COMMENT ON COLUMN client_key_bind_role_policy.bind_role_code IS '绑定的角色编码';
COMMENT ON COLUMN client_key_bind_role_policy.description IS '策略描述';
COMMENT ON COLUMN client_key_bind_role_policy.del_logic IS '逻辑删除标识';
COMMENT ON COLUMN client_key_bind_role_policy.created_by IS '创建者';
COMMENT ON COLUMN client_key_bind_role_policy.created_time IS '创建时间';
COMMENT ON COLUMN client_key_bind_role_policy.update_by IS '修改者';
COMMENT ON COLUMN client_key_bind_role_policy.update_time IS '修改时间';
COMMENT ON TABLE client_key_bind_role_policy IS 'client_key_bind_role_policy;客户端key 绑定 角色 策略表';

--- 权限黑名单映射表
DROP TABLE IF EXISTS auth_black_list_mapping;
CREATE TABLE auth_black_list_mapping(
                                        id SERIAL,
                                        client_type VARCHAR NOT NULL,
                                        black_mapping_key_json JSON NOT NULL,
                                        deny_target_element_codes VARCHAR NOT NULL,
                                        effect_flag BIT NOT NULL,
                                        description VARCHAR,
                                        del_logic BIT NOT NULL,
                                        created_by VARCHAR,
                                        created_time TIMESTAMP,
                                        update_by VARCHAR,
                                        update_time TIMESTAMP,
                                        PRIMARY KEY (id)
);
COMMENT ON COLUMN auth_black_list_mapping.id IS '主键id';
COMMENT ON COLUMN auth_black_list_mapping.client_type IS '客户端类型(如dify/codex/....)';
COMMENT ON COLUMN auth_black_list_mapping.black_mapping_key_json IS '客户端映射key(json类)';
COMMENT ON COLUMN auth_black_list_mapping.deny_target_element_codes IS '拒绝使用的权限元素';
COMMENT ON COLUMN auth_black_list_mapping.effect_flag IS '是否生效';
COMMENT ON COLUMN auth_black_list_mapping.description IS '描述';
COMMENT ON COLUMN auth_black_list_mapping.del_logic IS '逻辑删除标识';
COMMENT ON COLUMN auth_black_list_mapping.created_by IS '创建者';
COMMENT ON COLUMN auth_black_list_mapping.created_time IS '创建时间';
COMMENT ON COLUMN auth_black_list_mapping.update_by IS '修改者';
COMMENT ON COLUMN auth_black_list_mapping.update_time IS '修改时间';
COMMENT ON TABLE auth_black_list_mapping IS 'auth_black_list_mapping;权限黑名单映射表';

ALTER TABLE public.client_key_bind_role_policy ALTER COLUMN client_mapping_key_json TYPE jsonb USING client_mapping_key_json::jsonb;
ALTER TABLE public.auth_black_list_mapping ALTER COLUMN black_mapping_key_json TYPE jsonb USING black_mapping_key_json::jsonb;
