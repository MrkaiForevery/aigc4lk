package com.air.memory;

import com.air.memory.config.ChatModelApiKeyConfig;
import com.air.memory.config.ChromaDbClientConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableConfigurationProperties({
        ChatModelApiKeyConfig.class,
        ChromaDbClientConfig.class,
})
@EnableDiscoveryClient
@SpringBootApplication
@EnableAsync
@MapperScan("com.air.memory.mapper")
public class SharedMemoryServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharedMemoryServicesApplication.class, args);
    }

}
