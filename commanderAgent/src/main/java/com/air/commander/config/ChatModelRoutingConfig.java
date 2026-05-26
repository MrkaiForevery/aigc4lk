package com.air.commander.config;

import com.air.commander.entity.CommanderModelDefinition;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型切换配置数据，从Nacos配置中心platform-model-routing-config中读取
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "platform.air")
public class ChatModelRoutingConfig {

    private List<CommanderModelDefinition> models = new ArrayList<>();

}