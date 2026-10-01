package com.opensocket.aievent.core.agent.runtime;

import java.time.Duration;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;

/** Typed runtime view for Agent Directory operational maintenance settings. */
@Component
public final class AgentDirectoryRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.AGENT_DIRECTORY_SYSTEM;
    public static final String LEASE_REAPER_FIXED_DELAY = "agent-directory.lease-reaper.fixed-delay";
    public static final Set<String> ALL = Set.of(LEASE_REAPER_FIXED_DELAY);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final long startupFixedDelayMs;

    public AgentDirectoryRuntimeConfigurationView(
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority,
            Environment environment) {
        this.values = values;
        this.authority = authority;
        this.startupFixedDelayMs = environment == null
                ? 10_000L
                : environment.getProperty("agent-directory.lease-reaper.fixed-delay", Long.class, 10_000L);
    }

    public Duration leaseReaperFixedDelay() {
        if (runtimeRequired()) {
            requireSnapshot();
            long value = values.longValue(SET_KEY, LEASE_REAPER_FIXED_DELAY)
                    .orElseThrow(() -> incomplete(LEASE_REAPER_FIXED_DELAY));
            return validate(value);
        }
        long value = values.longValue(SET_KEY, LEASE_REAPER_FIXED_DELAY).orElse(startupFixedDelayMs);
        return validate(value);
    }

    private boolean runtimeRequired() {
        return authority != null && authority.isRuntimeAuthoritative(LEASE_REAPER_FIXED_DELAY);
    }

    private void requireSnapshot() {
        if (!values.hasSnapshot(SET_KEY)) throw incomplete(LEASE_REAPER_FIXED_DELAY);
        values.requireKeys(SET_KEY, ALL);
    }

    private static Duration validate(long millis) {
        if (millis < 1_000L || millis > 3_600_000L) {
            throw new IllegalStateException(
                    "RUNTIME_CONFIG_VALIDATION_FAILED key=" + LEASE_REAPER_FIXED_DELAY + " value=" + millis);
        }
        return Duration.ofMillis(millis);
    }

    private static IllegalStateException incomplete(String key) {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " key=" + key);
    }
}
