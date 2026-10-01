package com.opensocket.aievent.gateway.netty.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.gateway.netty.configuration.GatewayDynamicFixedDelayTask;

/**
 * Runtime-configurable Agent timeout scanner. The next wait is resolved after every completed
 * cycle so {@code agent.timeout-scan-interval-ms} follows HOT_NEXT_CYCLE semantics.
 */
@Component
public class AgentTimeoutScheduler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AgentTimeoutScheduler.class);

    private final AgentLifecycleService agentLifecycleService;
    private final AgentRuntimeConfigurationView runtimeConfiguration;
    private final GatewayDynamicFixedDelayTask dynamicTask;

    public AgentTimeoutScheduler(
            AgentLifecycleService agentLifecycleService,
            AgentRuntimeConfigurationView runtimeConfiguration,
            TaskScheduler taskScheduler) {
        this.agentLifecycleService = agentLifecycleService;
        this.runtimeConfiguration = runtimeConfiguration;
        this.dynamicTask = new GatewayDynamicFixedDelayTask(
                taskScheduler,
                "agent-timeout-scan",
                this::scanTimeoutAgents,
                runtimeConfiguration::timeoutScanInterval);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() {
        dynamicTask.start();
    }

    @Override
    public void destroy() {
        dynamicTask.stop();
    }

    public void scanTimeoutAgents() {
        var timeoutCount = agentLifecycleService.markTimeoutAgents();
        if (timeoutCount > 0) {
            log.warn("Agent timeout scan marked {} agent(s) as TIMEOUT; heartbeatTimeout={} nextScan={}",
                    timeoutCount, runtimeConfiguration.heartbeatTimeout(), runtimeConfiguration.timeoutScanInterval());
        }
    }
}
