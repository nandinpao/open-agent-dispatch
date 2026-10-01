package com.opensocket.aievent.core.configuration.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;

@Configuration(proxyBeanMethods = false)
public class RuntimeConfigurationConsumerConfiguration {
    @Bean
    @ConditionalOnMissingBean(RuntimeConfigurationLocalSnapshotRegistry.class)
    RuntimeConfigurationLocalSnapshotRegistry runtimeConfigurationLocalSnapshotRegistry() {
        return new RuntimeConfigurationLocalSnapshotRegistry();
    }
}
