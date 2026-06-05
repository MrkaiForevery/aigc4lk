package com.air.commander.config;

import org.redisson.codec.JsonJacksonCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer() {
        return config -> {
            // 全局使用 JSON 序列化，避免乱码或二进制数据
            config.setCodec(new JsonJacksonCodec());
        };
    }
}