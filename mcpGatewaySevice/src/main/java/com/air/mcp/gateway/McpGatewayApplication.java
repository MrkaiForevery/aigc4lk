package com.air.mcp.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {
        "com.air.mcp.gateway",          // 扫描 gateway 自己的包
        "com.air.platform.common"     // 扫描公共模块的包（包含 NacosA2ARouter）
})
@EnableFeignClients(basePackages = "com.air.api.feignClient")
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.air.mcp.gateway.mapper")
public class McpGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }

}
