package com.opensocket.aievent.core.dispatch;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;

/** TODO 15-D: applies temporary runtime cooldown to repeatedly failing agents. */
@Service
public class AgentRuntimeBackoffService {
    private final AgentDirectoryFacade agentDirectory;
    private final DispatchProperties properties;

    public AgentRuntimeBackoffService(AgentDirectoryFacade agentDirectory, DispatchProperties properties) {
        this.agentDirectory = agentDirectory;
        this.properties = properties == null ? new DispatchProperties() : properties;
    }

    public OffsetDateTime applyCooldown(String agentId, int failureCount, String reason, OffsetDateTime now) {
        OffsetDateTime at = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        Duration delay = cooldownForFailure(failureCount);
        OffsetDateTime until = at.plus(delay);
        if (agentDirectory != null && agentId != null && !agentId.isBlank()) {
            agentDirectory.applyRuntimeBackoff(agentId, until, reason);
        }
        return until;
    }

    public Duration cooldownForFailure(int failureCount) {
        int count = Math.max(1, failureCount);
        long multiplier = 1L << Math.max(0, Math.min(count - 1, 10));
        Duration initial = properties.getFailureRequeue().getRuntimeInitialBackoff();
        Duration max = properties.getFailureRequeue().getRuntimeMaxBackoff();
        Duration candidate = initial.multipliedBy(multiplier);
        return candidate.compareTo(max) > 0 ? max : candidate;
    }
}
