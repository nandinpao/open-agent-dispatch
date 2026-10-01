package com.opensocket.aievent.core.api;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3B1 typed local-snapshot view for stale remediation workflow lease recovery. */
@Component
public class AgentRemediationWorkflowRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.AGENT_REMEDIATION_SYSTEM;
    public static final String ENABLED = "agent-remediation.workflow.stale-lease-reaper.enabled";
    public static final String FIXED_DELAY_MS = "agent-remediation.workflow.stale-lease-reaper.fixed-delay-ms";
    public static final String INITIAL_DELAY_MS = "agent-remediation.workflow.stale-lease-reaper.initial-delay-ms";
    public static final String LIMIT = "agent-remediation.workflow.stale-lease-reaper.limit";
    public static final Set<String> ALL = Set.of(ENABLED, FIXED_DELAY_MS, INITIAL_DELAY_MS, LIMIT);

    private final AgentRemediationWorkflowRuntimeProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public AgentRemediationWorkflowRuntimeConfigurationView(
            AgentRemediationWorkflowRuntimeProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }
    public boolean enabled() { return booleanValue(ENABLED, startup.isEnabled()); }

    public Duration fixedDelay() {
        long value = longValue(FIXED_DELAY_MS, startup.getFixedDelayMs());
        if (value < 1000 || value > 86_400_000) throw invalid(FIXED_DELAY_MS);
        return Duration.ofMillis(value);
    }

    public Duration initialDelay() {
        long value = longValue(INITIAL_DELAY_MS, startup.getInitialDelayMs());
        if (value < 1000 || value > 86_400_000) throw invalid(INITIAL_DELAY_MS);
        return Duration.ofMillis(value);
    }

    public int limit() {
        int value = integerValue(LIMIT, startup.getLimit());
        if (value < 1 || value > 5000) throw invalid(LIMIT);
        return value;
    }

    private boolean booleanValue(String key, boolean fallback) {
        if (authority.isRuntimeAuthoritative(key)) {
            requireSnapshot(key);
            return values.booleanValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.booleanValue(SET_KEY, key).orElse(fallback);
    }

    private long longValue(String key, long fallback) {
        if (authority.isRuntimeAuthoritative(key)) {
            requireSnapshot(key);
            return values.longValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.longValue(SET_KEY, key).orElse(fallback);
    }

    private int integerValue(String key, int fallback) {
        if (authority.isRuntimeAuthoritative(key)) {
            requireSnapshot(key);
            return values.integerValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.integerValue(SET_KEY, key).orElse(fallback);
    }

    private void requireSnapshot(String key) {
        if (!values.hasSnapshot(SET_KEY)) throw incomplete(key + ": authenticated local snapshot missing");
    }

    private static IllegalStateException invalid(String key) {
        return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED " + key);
    }

    private static IllegalStateException incomplete(String detail) {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " detail=" + detail);
    }
}
