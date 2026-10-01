package com.opensocket.aievent.core.outbox;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2L typed runtime view for durable module-event outbox delivery tuning. */
@Component
public final class OutboxRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String BATCH_SIZE = "core.outbox.batch-size";
    public static final String CLAIM_LEASE = "core.outbox.claim-lease";
    public static final String INITIAL_BACKOFF = "core.outbox.initial-backoff";
    public static final String MAX_ATTEMPTS = "core.outbox.max-attempts";
    public static final String MAX_BACKOFF = "core.outbox.max-backoff";
    public static final String SCAN_INTERVAL_MS = "core.outbox.scan-interval-ms";
    public static final String WORKER_ID = "core.outbox.worker-id";
    public static final Set<String> ALL = Set.of(BATCH_SIZE, CLAIM_LEASE, INITIAL_BACKOFF, MAX_ATTEMPTS, MAX_BACKOFF, SCAN_INTERVAL_MS, WORKER_ID);

    private static final Pattern WORKER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final OutboxProperties startup;

    public OutboxRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                           RuntimeConfigurationAuthorityRegistry authority,
                                           OutboxProperties startup) {
        this.values = values; this.authority = authority; this.startup = startup;
    }

    public boolean runtimeBacked(){ return values.hasSnapshot(SET_KEY); }
    public String revisionId(){ return values.revisionId(SET_KEY).orElse(null); }
    public int batchSize(){ int v=intValue(BATCH_SIZE,startup.getBatchSize()); if(v<1||v>1000)throw invalid(BATCH_SIZE); return v; }
    public Duration claimLease(){ return boundedDuration(CLAIM_LEASE,startup.getClaimLease(),Duration.ofSeconds(1),Duration.ofHours(1)); }
    public Duration initialBackoff(){ return boundedDuration(INITIAL_BACKOFF,startup.getInitialBackoff(),Duration.ofMillis(100),Duration.ofHours(1)); }
    public int maxAttempts(){ int v=intValue(MAX_ATTEMPTS,startup.getMaxAttempts()); if(v<1||v>100)throw invalid(MAX_ATTEMPTS); return v; }
    public Duration maxBackoff(){ Duration v=boundedDuration(MAX_BACKOFF,startup.getMaxBackoff(),Duration.ofMillis(100),Duration.ofDays(1)); if(v.compareTo(initialBackoff())<0)throw invalid(MAX_BACKOFF); return v; }
    public Duration scanInterval(){ long v=longValue(SCAN_INTERVAL_MS,startup.getScanIntervalMs()); if(v<250||v>3_600_000)throw invalid(SCAN_INTERVAL_MS); return Duration.ofMillis(v); }
    public String workerId(){ String v=textValue(WORKER_ID,startup.getWorkerId()).trim(); if(!WORKER.matcher(v).matches())throw invalid(WORKER_ID); return v; }

    private boolean required(String key){ return authority!=null && authority.isRuntimeAuthoritative(key); }
    private void require(String key){ if(!values.hasSnapshot(SET_KEY))throw incomplete(key); values.requireKeys(SET_KEY,ALL); }
    private int intValue(String key,int fallback){ if(required(key)){require(key);return values.integerValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.integerValue(SET_KEY,key).orElse(fallback); }
    private long longValue(String key,long fallback){ if(required(key)){require(key);return values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.longValue(SET_KEY,key).orElse(fallback); }
    private String textValue(String key,String fallback){ if(required(key)){require(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fallback); }
    private Duration durationValue(String key,Duration fallback){ if(required(key)){require(key);return values.durationValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.durationValue(SET_KEY,key).orElse(fallback); }
    private Duration boundedDuration(String key,Duration fallback,Duration min,Duration max){ Duration v=durationValue(key,fallback); if(v==null||v.compareTo(min)<0||v.compareTo(max)>0)throw invalid(key); return v; }
    private static IllegalStateException invalid(String key){ return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key); }
    private static IllegalStateException incomplete(String key){ return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key); }
}
