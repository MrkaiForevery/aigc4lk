package com.air.imageAnalysis;

import com.air.imageAnalysis.config.ChatModelApiKeyConfig;
import com.air.imageAnalysis.config.McpServersProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = {
        "com.air.imageAnalysis",          // 扫描 document 自己的包
        "com.air.platform.common"     // 扫描公共模块的包（包含 NacosA2ARouter）
})
@EnableFeignClients(basePackages = "com.air.api.feignClient")
@EnableConfigurationProperties({
        ChatModelApiKeyConfig.class,
        McpServersProperties.class,
})
@EnableDiscoveryClient
@SpringBootApplication
public class ImageAnalysisAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageAnalysisAgentApplication.class, args);
    }

}
