package com.air.commander.config;

import org.redisson.codec.JsonJackson3Codec;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedissonConfig {

    /**
     * 自定义 Redisson 的编码器，确保使用注册了 JavaTimeModule 的 ObjectMapper
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer(JsonMapper springJsonMapper) {
        return config -> {
            // Jackson 3 已自动支持 JavaTimeModule，无需手动注册
            config.setCodec(new JsonJackson3Codec(springJsonMapper));
        };
    }

    /**
     * 可选：确保全局 Jackson 配置也包含 JavaTimeModule（非必须，但建议保留）
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 此处可以根据需要添加 Jackson 3 的配置，例如：
            builder.disable(JsonReadFeature.ALLOW_JAVA_COMMENTS);
        };
    }
}