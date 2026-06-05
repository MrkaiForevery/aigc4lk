package com.air.commander;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan(basePackages = {
        "com.air.commander",          // 扫描 Commander 自己的包
        "com.air.platform.common"     // 扫描公共模块的包（包含 NacosA2ARouter）
})
@EnableFeignClients(basePackages = "com.air.api.feignClient") // 扫描FeignClient 的包
@EnableDiscoveryClient
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CommanderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommanderAgentApplication.class);
    }
}