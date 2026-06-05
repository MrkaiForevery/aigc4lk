package com.air.commander.log;

import com.air.commander.configloader.properties.LoggingProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 监听Nacos上的配置更新同步激活Logback日志级别更新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingLevelRefresher {

    private final LoggingSystem loggingSystem;
    private final LoggingProperties loggingProperties;

    @PostConstruct
    public void init() {
        refreshLoggingLevels();
    }

    private void refreshLoggingLevels() {
        Map<String, String> levels = loggingProperties.getLevel();
        if (levels != null) {
            levels.forEach((packageName, level) -> {
                try {
                    LogLevel logLevel = LogLevel.valueOf(level.toUpperCase());
                    loggingSystem.setLogLevel(packageName, logLevel);
                    log.info("📊 Log level: {} = {}", packageName, logLevel);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid log level '{}' for package '{}'", level, packageName);
                }
            });
        }
    }
}