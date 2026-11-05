INSERT INTO public.auth_element_def (element_type,sensitivity_type,element_code,element_name,relation_domain_code,description,del_logic,created_by,created_time,update_by,update_time) VALUES
	 ('mcp_tools_type','read','query_mobile_user_base_info','queryMobileUserBaseInfo','111000000','查询移动用户的基本信息','0','admin','2026-10-29 16:48:19.546891',NULL,NULL),
	 ('mcp_prompt_type','read','fill_code_check_prompt','fillCodeCheckPrompt','211100000','提供检查logserver服务代码的检查提示词','0','admin','2026-10-29 17:01:25.569968',NULL,NULL),
	 ('mcp_tools_type','read','query_code_check_result_base_info','queryCodeCheckResultBaseInfo','112000000','查询代码审查结果','0','admin','2026-10-29 16:50:11.774536',NULL,NULL),
	 ('mcp_resources_type','read','db://identity/machine/schema','readMachineTableSchemaInfo','120000000','提供机器列表信息','0','admin','2026-10-29 16:56:35.202942',NULL,NULL);
