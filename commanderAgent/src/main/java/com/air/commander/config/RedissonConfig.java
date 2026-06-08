package com.air.commander.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
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

    //Redisson 默认使用 Jackson 进行序列化，但 Jackson 需要额外注册 JavaTimeModule 才能处理 Java 8 时间类型。
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.modules(new JavaTimeModule());
    }
}