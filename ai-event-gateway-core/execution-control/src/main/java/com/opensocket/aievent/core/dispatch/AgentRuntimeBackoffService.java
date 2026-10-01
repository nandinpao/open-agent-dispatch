package com.opensocket.aievent.core.dispatch;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;

/** TODO 15-D: applies temporary runtime cooldown to repeatedly failing agents. */
@Service
public class AgentRuntimeBackoffService {
    private final AgentDirectoryFacade agentDirectory;
    private final DispatchProperties properties;
    private final DispatchRuntimeConfigurationView runtimeConfiguration;

    @Autowired
    public AgentRuntimeBackoffService(
            AgentDirectoryFacade agentDirectory,
            DispatchProperties properties,
            DispatchRuntimeConfigurationView runtimeConfiguration) {
        this.agentDirectory = agentDirectory;
        this.properties = properties == null ? new DispatchProperties() : properties;
        this.runtimeConfiguration = runtimeConfiguration;
    }

    /** Compatibility constructor for focused tests that do not bootstrap Runtime Configuration. */
    public AgentRuntimeBackoffService(AgentDirectoryFacade agentDirectory, DispatchProperties properties) {
        this(agentDirectory, properties, null);
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
        Duration initial = runtimeConfiguration == null
                ? properties.getFailureRequeue().getRuntimeInitialBackoff()
                : runtimeConfiguration.runtimeInitialBackoff();
        Duration max = runtimeConfiguration == null
                ? properties.getFailureRequeue().getRuntimeMaxBackoff()
                : runtimeConfiguration.runtimeMaxBackoff();
        Duration candidate = initial.multipliedBy(multiplier);
        return candidate.compareTo(max) > 0 ? max : candidate;
    }
}
