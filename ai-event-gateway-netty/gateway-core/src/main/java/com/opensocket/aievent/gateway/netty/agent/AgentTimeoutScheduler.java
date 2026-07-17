package com.opensocket.aievent.gateway.netty.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent domain component for Agent Timeout Scheduler. It manages the lifecycle, status,
 * connection identity, and query model used by the Admin UI and command delivery diagnostics.
 */
@Component
public class AgentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentTimeoutScheduler.class);

    private final AgentLifecycleService agentLifecycleService;

    public AgentTimeoutScheduler(AgentLifecycleService agentLifecycleService) {
        this.agentLifecycleService = agentLifecycleService;
    }

    @Scheduled(fixedDelayString = "${agent.timeout-scan-interval-ms:5000}")
    public void scanTimeoutAgents() {
        var timeoutCount = agentLifecycleService.markTimeoutAgents();
        if (timeoutCount > 0) {
            log.warn("Agent timeout scan marked {} agent(s) as TIMEOUT", timeoutCount);
        }
    }
}
