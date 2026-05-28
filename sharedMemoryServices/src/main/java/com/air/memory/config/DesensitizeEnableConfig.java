package com.air.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 脱敏开关配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "services.memory.desensitize")
public class DesensitizeEnableConfig {
    private boolean enabled = true;
    private boolean phone = true;
    private boolean email = true;
    private boolean idCard = true;
    private boolean bankCard = true;
    private boolean ip = true;
}