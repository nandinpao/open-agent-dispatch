package com.opensocket.aievent.worker.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
public class WorkerRuntimeConfigurationBeans {
    @Bean WorkerRuntimeConfigurationLocalRegistry workerRuntimeConfigurationLocalRegistry(){return new WorkerRuntimeConfigurationLocalRegistry();}
    @Bean WorkerRuntimeConfigurationRecoveryTracker workerRuntimeConfigurationRecoveryTracker(){return new WorkerRuntimeConfigurationRecoveryTracker();}
    @Bean WorkerRuntimeConfigurationLkgStore workerRuntimeConfigurationLkgStore(WorkerRuntimeConfigurationProperties properties,ObjectMapper objectMapper){return new WorkerRuntimeConfigurationLkgStore(properties.lkgDirectory(),objectMapper);}
}
