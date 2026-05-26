package com.air.commander;

import com.air.commander.config.ChatModelApiKeyConfig;
import com.air.commander.config.ChatModelRoutingConfig;
import com.air.commander.config.CommanderMetaConfig;
import com.air.commander.config.LoggingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableConfigurationProperties({
        ChatModelApiKeyConfig.class,
        ChatModelRoutingConfig.class,
        CommanderMetaConfig.class,
        LoggingConfig.class,
})
@EnableDiscoveryClient
@SpringBootApplication
@EnableAsync
public class CommanderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommanderAgentApplication.class);
    }
}