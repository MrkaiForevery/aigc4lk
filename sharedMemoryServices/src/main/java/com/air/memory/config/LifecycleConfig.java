package com.air.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "memory.lifecycle")
public class LifecycleConfig {
    /** 行为记忆保留天数（默认90天） */
    private int behaviorRetentionDays = 90;
    /** 冷数据归档月数（默认3个月） */
    private int coldArchiveMonths = 3;
    /** 是否启用自动清理 */
    private boolean autoCleanupEnabled = true;
}