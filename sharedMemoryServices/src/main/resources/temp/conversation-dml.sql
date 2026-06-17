
DROP TABLE IF EXISTS conversation_history;
CREATE TABLE conversation_history(
                                     id SERIAL NOT NULL,
                                     thread_id VARCHAR NOT NULL,
                                     user_id VARCHAR NOT NULL,
                                     status VARCHAR NOT NULL,
                                     created_by VARCHAR NOT NULL,
                                     created_at TIMESTAMP NOT NULL,
                                     update_by VARCHAR,
                                     update_at TIMESTAMP,
                                     PRIMARY KEY (id)
);
COMMENT ON COLUMN conversation_history.id IS '主键id';
COMMENT ON COLUMN conversation_history.thread_id IS '会话id';
COMMENT ON COLUMN conversation_history.user_id IS '用户id';
COMMENT ON COLUMN conversation_history.status IS '会话状态;IN_PROGRESS / INTERRUPTED / COMPLETED / FAILED';
COMMENT ON COLUMN conversation_history.created_by IS '创建人';
COMMENT ON COLUMN conversation_history.created_at IS '创建时间';
COMMENT ON COLUMN conversation_history.update_by IS '修改人';
COMMENT ON COLUMN conversation_history.update_at IS '修改时间';
COMMENT ON TABLE conversation_history IS 'conversation_history;会话历史记录表';

CREATE INDEX index_user_id_thread_id ON conversation_history (
                                                              user_id ,
                                                              thread_id
    );
COMMENT ON INDEX index_user_id_thread_id IS 'index_user_id_thread_id;用户id+会话id 索引';

DROP TABLE IF EXISTS plan_history;
CREATE TABLE plan_history(
                             id SERIAL NOT NULL,
                             thread_id VARCHAR NOT NULL,
                             relation_request_id VARCHAR NOT NULL,
                             relation_template_id VARCHAR,
                             user_input TEXT NOT NULL,
                             plan_id VARCHAR NOT NULL,
                             plan_content_jsonb JSONB NOT NULL,
                             execute_step_id VARCHAR NOT NULL,
                             created_by VARCHAR NOT NULL,
                             created_time TIMESTAMP NOT NULL,
                             update_by VARCHAR,
                             update_time TIMESTAMP,
                             PRIMARY KEY (id)
);
COMMENT ON COLUMN plan_history.id IS '主键id';
COMMENT ON COLUMN plan_history.thread_id IS '会话id';
COMMENT ON COLUMN plan_history.relation_request_id IS '关联的请求id';
COMMENT ON COLUMN plan_history.relation_template_id IS '关联的严格场景的模板id';
COMMENT ON COLUMN plan_history.user_input IS '用户的原始输入';
COMMENT ON COLUMN plan_history.plan_id IS '编排计划id';
COMMENT ON COLUMN plan_history.plan_content_jsonb IS '编排计划的jsonb对象';
COMMENT ON COLUMN plan_history.execute_step_id IS '执行到哪一步的step_id';
COMMENT ON COLUMN plan_history.created_by IS '创建人';
COMMENT ON COLUMN plan_history.created_time IS '创建时间';
COMMENT ON COLUMN plan_history.update_by IS '修改人';
COMMENT ON COLUMN plan_history.update_time IS '修改时间';
COMMENT ON TABLE plan_history IS 'plan_history;生成的编排计划历史记录表';

CREATE INDEX index_request_id_plan_id_thread_id ON plan_history (
                                                                 relation_request_id ,
                                                                 thread_id ,
                                                                 plan_id
    );
COMMENT ON INDEX index_request_id_plan_id_thread_id IS 'index_request_id_plan_id_thread_id;会话id+请i求id+编排计划id';

DROP TABLE IF EXISTS execution_result_history;
CREATE TABLE execution_result_history(
                                         id SERIAL NOT NULL,
                                         relation_plan_id VARCHAR NOT NULL,
                                         step_id VARCHAR NOT NULL,
                                         result_content_jsonb JSONB NOT NULL,
                                         execute_status VARCHAR NOT NULL,
                                         execute_time INT8 NOT NULL,
                                         created_by VARCHAR NOT NULL,
                                         created_time TIMESTAMP NOT NULL,
                                         update_by VARCHAR,
                                         update_time TIMESTAMP,
                                         PRIMARY KEY (id)
);
COMMENT ON COLUMN execution_result_history.id IS '主键id';
COMMENT ON COLUMN execution_result_history.relation_plan_id IS '关联的编排计划id';
COMMENT ON COLUMN execution_result_history.step_id IS '步骤id';
COMMENT ON COLUMN execution_result_history.result_content_jsonb IS '步骤结果jsonb对象';
COMMENT ON COLUMN execution_result_history.execute_status IS '执行状态;成功，失败，阻塞';
COMMENT ON COLUMN execution_result_history.execute_time IS '执行耗时';
COMMENT ON COLUMN execution_result_history.created_by IS '创建人';
COMMENT ON COLUMN execution_result_history.created_time IS '创建时间';
COMMENT ON COLUMN execution_result_history.update_by IS '修改人';
COMMENT ON COLUMN execution_result_history.update_time IS '修改时间';
COMMENT ON TABLE execution_result_history IS 'execution_result_history;执行结果历史记录表';

