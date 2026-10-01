package com.opensocket.aievent.core.gateway.runtime;

import java.time.Duration;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** C3R2I typed runtime view for Gateway-node directory maintenance. */
@Component
public final class GatewayNodeRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.GATEWAY_NODES_SYSTEM;
    public static final String LEASE_REAPER_FIXED_DELAY = "gateway-nodes.lease-reaper.fixed-delay";
    public static final Set<String> ALL = Set.of(LEASE_REAPER_FIXED_DELAY);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final long startupDelayMs;

    public GatewayNodeRuntimeConfigurationView(
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority,
            Environment environment) {
        this.values = values;
        this.authority = authority;
        this.startupDelayMs = environment == null ? 10_000L
                : environment.getProperty(LEASE_REAPER_FIXED_DELAY, Long.class, 10_000L);
    }

    public Duration leaseReaperDelay() {
        if (authority != null && authority.isRuntimeAuthoritative(LEASE_REAPER_FIXED_DELAY)) {
            if (!values.hasSnapshot(SET_KEY)) throw incomplete();
            values.requireKeys(SET_KEY, ALL);
            return validate(values.longValue(SET_KEY, LEASE_REAPER_FIXED_DELAY).orElseThrow(this::incomplete));
        }
        return validate(values.longValue(SET_KEY, LEASE_REAPER_FIXED_DELAY).orElse(startupDelayMs));
    }

    private static Duration validate(long value) {
        if (value < 1_000L || value > 3_600_000L) {
            throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + LEASE_REAPER_FIXED_DELAY + " value=" + value);
        }
        return Duration.ofMillis(value);
    }

    private IllegalStateException incomplete() {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " key=" + LEASE_REAPER_FIXED_DELAY);
    }
}
