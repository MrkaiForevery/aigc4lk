INSERT INTO public.client_key_bind_role_policy (client_type,client_mapping_key_json,bind_role_code,description,del_logic,created_by,created_time,update_by,update_time) VALUES
	 ('dify_agent_client_type','{"agent_id": "code-agent-id-950925", "user_role": "code_admin_role"}','1200000','dify平台agent访问code系统','0','admin','2026-10-29 17:55:43.717901',NULL,NULL),
	 ('dify_agent_client_type','{"agent_id": "identity-agent-id-950000", "user_role": "identity_admin_role"}','1100000','dify平台agent访问identity系统','0','admin','2026-10-29 17:51:16.923583',NULL,NULL),
	 ('codex_agent_client_type','{"agent_id": "identity-agent-id-84832", "global_auth": "false", "operator_auth": "identity_admin_role"}','1100000','codex平台agent访问identity系统','0','admin','2026-10-30 08:55:34.985535',NULL,NULL),
	 ('codex_agent_client_type','{"agent_id": "code-agent-id-950925", "global_auth": "false", "operator_auth": "code_admin_role"}','1200000','codex平台agent访问code系统','0','admin','2026-10-30 08:55:34.985535',NULL,NULL);
