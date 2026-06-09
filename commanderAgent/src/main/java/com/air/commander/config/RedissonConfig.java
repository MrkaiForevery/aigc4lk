package com.air.commander.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;

@Configuration
public class RedissonConfig {

    /**
     * 自定义 Redisson 的编码器，确保使用注册了 JavaTimeModule 的 ObjectMapper
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer(ObjectMapper springObjectMapper) {
        return config -> {
            // 确保 Spring 的 ObjectMapper 支持 Java 8 时间类型
            springObjectMapper.registerModule(new JavaTimeModule());
            // 使用自定义的 ObjectMapper 创建 JSON 编码器
            config.setCodec(new JsonJacksonCodec(springObjectMapper));
        };
    }

    /**
     * 可选：确保全局 Jackson 配置也包含 JavaTimeModule（非必须，但建议保留）
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.modules(new JavaTimeModule());
    }
}