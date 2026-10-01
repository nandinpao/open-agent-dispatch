package com.opensocket.aievent.gateway.netty.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
public class GatewayRuntimeConfigurationBeans {
    @Bean
    GatewayRuntimeConfigurationLocalRegistry gatewayRuntimeConfigurationLocalRegistry() {
        return new GatewayRuntimeConfigurationLocalRegistry();
    }

    @Bean
    GatewayRuntimeConfigurationRecoveryTracker gatewayRuntimeConfigurationRecoveryTracker() {
        return new GatewayRuntimeConfigurationRecoveryTracker();
    }

    @Bean
    GatewayRuntimeConfigurationLkgStore gatewayRuntimeConfigurationLkgStore(
            GatewayRuntimeConfigurationProperties properties,
            ObjectMapper objectMapper) {
        return new GatewayRuntimeConfigurationLkgStore(properties.lkgDirectory(), objectMapper);
    }
}
