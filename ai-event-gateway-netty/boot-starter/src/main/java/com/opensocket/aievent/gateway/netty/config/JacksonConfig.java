package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provides the JSON mapper used by TCP, WebSocket, UDP discovery, and task dispatch code.
 *
 * <p>Spring Boot 4 uses Jackson 3 by default. Jackson 3 moved the core packages from
 * {@code com.fasterxml.jackson.*} to {@code tools.jackson.*}. The explicit bean below keeps the
 * gateway code independent from whether the mapper is provided by Boot auto-configuration or by a
 * minimal test context.</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * Creates a Jackson 3 JsonMapper. JsonMapper extends ObjectMapper and is the recommended mapper
     * for JSON-only workloads in Spring Boot 4 / Spring Framework 7.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }
}
