package com.opensocket.aievent.core.processing;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2M typed runtime view for event deduplication decision tuning. */
@Component
public final class EventDecisionRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String DEDUP_WINDOW = "core.decision.dedup-window";
    public static final String DEDUP_TTL = "core.decision.dedup-ttl";
    public static final Set<String> ALL = Set.of(DEDUP_WINDOW, DEDUP_TTL);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final EventProcessingProperties startup;

    @Autowired
    public EventDecisionRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                                 RuntimeConfigurationAuthorityRegistry authority,
                                                 EventProcessingProperties startup) {
        this.values = values;
        this.authority = authority;
        this.startup = startup;
    }

    /** Compatibility constructor for focused/unit tests that intentionally run without Runtime Configuration. */
    public EventDecisionRuntimeConfigurationView(EventProcessingProperties startup) {
        this(null, null, startup == null ? new EventProcessingProperties() : startup);
    }

    public boolean runtimeBacked() { return values != null && values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values == null ? null : values.revisionId(SET_KEY).orElse(null); }

    public Duration dedupWindow() {
        return boundedDuration(DEDUP_WINDOW, startup.getDedupWindow(), Duration.ofSeconds(1), Duration.ofHours(24));
    }

    public Duration dedupTtl() {
        Duration value = boundedDuration(DEDUP_TTL, startup.getDedupTtl(), Duration.ofSeconds(1), Duration.ofDays(7));
        if (value.compareTo(dedupWindow()) < 0) throw invalid(DEDUP_TTL);
        return value;
    }

    private boolean required(String key) { return authority != null && authority.isRuntimeAuthoritative(key); }
    private void require(String key) {
        if (values == null || !values.hasSnapshot(SET_KEY)) throw incomplete(key);
        values.requireKeys(SET_KEY, ALL);
    }
    private Duration durationValue(String key, Duration fallback) {
        if (values == null) return fallback;
        if (required(key)) {
            require(key);
            return values.durationValue(SET_KEY, key).orElseThrow(() -> incomplete(key));
        }
        return values.durationValue(SET_KEY, key).orElse(fallback);
    }
    private Duration boundedDuration(String key, Duration fallback, Duration min, Duration max) {
        Duration value = durationValue(key, fallback);
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) throw invalid(key);
        return value;
    }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key=" + key); }
    private static IllegalStateException incomplete(String key) { return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " key=" + key); }
}
