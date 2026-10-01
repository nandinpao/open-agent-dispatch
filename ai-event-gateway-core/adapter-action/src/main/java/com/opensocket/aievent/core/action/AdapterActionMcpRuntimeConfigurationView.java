package com.opensocket.aievent.core.action;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** Typed local-snapshot view for active Adapter Action MCP orchestration settings. */
@Component
public class AdapterActionMcpRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.ADAPTER_ACTION_SYSTEM;
    public static final String ENABLED = "adapter-actions.mcp.enabled";
    public static final String RUN_ON_COMPLETED = "adapter-actions.mcp.run-on-completed-task";
    public static final String RUN_ON_FAILED = "adapter-actions.mcp.run-on-failed-task";
    public static final String ONE_PER_TASK = "adapter-actions.mcp.one-per-task";
    public static final String ADAPTER_NAME = "adapter-actions.mcp.adapter-name";
    public static final Set<String> ALL = Set.of(ENABLED, RUN_ON_COMPLETED, RUN_ON_FAILED, ONE_PER_TASK, ADAPTER_NAME);

    private final AdapterActionProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    @Autowired
    public AdapterActionMcpRuntimeConfigurationView(AdapterActionProperties startup,
            RuntimeConfigurationSnapshotValues values, RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup;
        this.values = values;
        this.authority = authority;
    }

    AdapterActionMcpRuntimeConfigurationView(AdapterActionProperties startup, RuntimeConfigurationSnapshotValues values) {
        this(startup, values, new RuntimeConfigurationAuthorityRegistry());
    }

    public boolean runtimeBacked() { return values != null && values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values == null ? null : values.revisionId(SET_KEY).orElse(null); }
    public boolean enabled() { return booleanValue(ENABLED, startup.getMcp().isEnabled()); }
    public boolean runOnCompletedTask() { return booleanValue(RUN_ON_COMPLETED, startup.getMcp().isRunOnCompletedTask()); }
    public boolean runOnFailedTask() { return booleanValue(RUN_ON_FAILED, startup.getMcp().isRunOnFailedTask()); }
    public boolean onePerTask() { return booleanValue(ONE_PER_TASK, startup.getMcp().isOnePerTask()); }
    public String adapterName() {
        String value = textValue(ADAPTER_NAME, startup.getMcp().getAdapterName()).trim();
        if (value.isBlank() || value.length() > 128) throw invalid(ADAPTER_NAME);
        return value;
    }

    private boolean booleanValue(String key, boolean fallback) {
        if (runtimeRequired(key)) { requireKey(key); return values.booleanValue(SET_KEY, key).orElseThrow(() -> incomplete(key)); }
        return values == null ? fallback : values.booleanValue(SET_KEY, key).orElse(fallback);
    }
    private String textValue(String key, String fallback) {
        if (runtimeRequired(key)) { requireKey(key); return values.textValue(SET_KEY, key).orElseThrow(() -> incomplete(key)); }
        return values == null ? fallback : values.textValue(SET_KEY, key).orElse(fallback);
    }
    private boolean runtimeRequired(String key) { return authority != null && authority.isRuntimeAuthoritative(key); }
    private void requireKey(String key) {
        if (values == null || !values.hasSnapshot(SET_KEY)) throw incomplete(key + ": snapshot missing");
        if (!values.keys(SET_KEY).contains(key)) throw incomplete(key + ": key missing");
    }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED " + key); }
    private static IllegalStateException incomplete(String detail) { return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " detail=" + detail); }
}
