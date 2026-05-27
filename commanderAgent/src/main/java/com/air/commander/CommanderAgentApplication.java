package com.air.commander;

import com.air.commander.config.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableFeignClients(basePackages = "com.air.api.feignClient")
@EnableConfigurationProperties({
        ChatModelApiKeyConfig.class,
        ChatModelRoutingConfig.class,
        CommanderMetaConfig.class,
        LoggingConfig.class,
        ChromaDbClientConfig.class,
})
@EnableDiscoveryClient
@SpringBootApplication
@EnableAsync
public class CommanderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommanderAgentApplication.class);
    }
}