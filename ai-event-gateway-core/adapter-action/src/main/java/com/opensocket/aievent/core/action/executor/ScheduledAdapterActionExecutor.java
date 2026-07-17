package com.opensocket.aievent.core.action.executor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledAdapterActionExecutor {
    private final AdapterActionExecutionService service;
    private final AdapterActionExecutionProperties properties;

    public ScheduledAdapterActionExecutor(AdapterActionExecutionService service, AdapterActionExecutionProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${adapter-executor.auto-execute-interval-ms:10000}")
    public void executePending() {
        if (!properties.isEnabled() || !properties.isAutoExecutePending()) {
            return;
        }
        service.executePending(properties.getBatchSize());
    }
}
