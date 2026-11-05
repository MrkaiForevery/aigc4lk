INSERT INTO public.auth_black_list_mapping (client_type,black_mapping_key_json,deny_target_element_codes,effect_flag,description,del_logic,created_by,created_time,update_by,update_time) VALUES
	 ('codex_agent_client_type','{"operator_list": ["60329", "60327"]}','111000000_1001','1','codex黑名单','0','admin','2026-10-30 09:08:26.497693',NULL,NULL),
	 ('dify_agent_client_type','{"user_id_list": ["10086", "10087"]}','112000000_1001','1','dify黑名单','0','admin','2026-10-30 09:08:26.497693',NULL,NULL);
