package com.opensocket.aievent.core.dedup;

import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2N typed Runtime Configuration view for Redisson dedup lock tuning. */
@Component
public final class EventDedupOperationalRuntimeConfigurationView {
    public static final String SET_KEY = "RUNTIME/EVENT/SYSTEM";
    public static final String LOCK_WAIT_SECONDS = "event.dedup.redis.lock-wait-seconds";
    public static final String LOCK_LEASE_SECONDS = "event.dedup.redis.lock-lease-seconds";
    public static final Set<String> ALL = Set.of(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final EventDedupRedisProperties startup;

    @Autowired
    public EventDedupOperationalRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                                          RuntimeConfigurationAuthorityRegistry authority,
                                                          EventDedupRedisProperties startup) {
        this.values=values; this.authority=authority; this.startup=startup;
    }
    public EventDedupOperationalRuntimeConfigurationView(EventDedupRedisProperties startup) { this(null,null,startup); }

    public long lockWaitSeconds() { return bounded(LOCK_WAIT_SECONDS, startup.getLockWaitSeconds(), 0L, 60L); }
    public long lockLeaseSeconds() { return bounded(LOCK_LEASE_SECONDS, startup.getLockLeaseSeconds(), 1L, 300L); }

    private long bounded(String key,long fallback,long min,long max) {
        long value=read(key,fallback);
        if(value<min||value>max) throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key+" value="+value);
        return value;
    }
    private long read(String key,long fallback) {
        if(values==null) return fallback;
        if(authority!=null&&authority.isRuntimeAuthoritative(key)) {
            if(!values.hasSnapshot(SET_KEY)) throw incomplete(key);
            values.requireKeys(SET_KEY,ALL);
            return values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));
        }
        return values.longValue(SET_KEY,key).orElse(fallback);
    }
    private static IllegalStateException incomplete(String key) { return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key); }
}
