package com.air.memory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableConfigurationProperties({
  ,
})
@EnableDiscoveryClient
@SpringBootApplication
@EnableAsync
public class SharedMemoryServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharedMemoryServicesApplication.class, args);
    }

}
