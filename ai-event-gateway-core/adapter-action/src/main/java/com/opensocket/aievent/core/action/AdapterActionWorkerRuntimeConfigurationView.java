package com.opensocket.aievent.core.action;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** Typed local-snapshot view for Adapter Action external-worker operational tuning. */
@Component
public class AdapterActionWorkerRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.ADAPTER_ACTION_SYSTEM;
    public static final String RETRY_ENABLED = "adapter-actions.worker.retry-enabled";
    public static final String MAX_ATTEMPTS = "adapter-actions.worker.max-attempts";
    public static final String INITIAL_BACKOFF = "adapter-actions.worker.initial-backoff";
    public static final String MAX_BACKOFF = "adapter-actions.worker.max-backoff";
    public static final String EXPIRED_LEASE_SCAN_BATCH_SIZE = "adapter-actions.worker.expired-lease-scan-batch-size";
    public static final String EXPIRED_LEASE_SCAN_INTERVAL_MS = "adapter-actions.worker.expired-lease-scan-interval-ms";
    public static final Set<String> ALL = Set.of(RETRY_ENABLED, MAX_ATTEMPTS, INITIAL_BACKOFF, MAX_BACKOFF,
            EXPIRED_LEASE_SCAN_BATCH_SIZE, EXPIRED_LEASE_SCAN_INTERVAL_MS);

    private final AdapterActionProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    @Autowired
    public AdapterActionWorkerRuntimeConfigurationView(AdapterActionProperties startup,
            RuntimeConfigurationSnapshotValues values, RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup; this.values = values; this.authority = authority;
    }
    AdapterActionWorkerRuntimeConfigurationView(AdapterActionProperties startup, RuntimeConfigurationSnapshotValues values) {
        this(startup, values, new RuntimeConfigurationAuthorityRegistry());
    }

    public boolean runtimeBacked(){return values != null && values.hasSnapshot(SET_KEY);}
    public String revisionId(){return values == null ? null : values.revisionId(SET_KEY).orElse(null);}
    public boolean retryEnabled(){return booleanValue(RETRY_ENABLED, startup.getWorker().isRetryEnabled());}
    public int maxAttempts(){int v=integerValue(MAX_ATTEMPTS,startup.getWorker().getMaxAttempts());if(v<1||v>20)throw invalid(MAX_ATTEMPTS);return v;}
    public Duration initialBackoff(){return positive(durationValue(INITIAL_BACKOFF,startup.getWorker().getInitialBackoff()),INITIAL_BACKOFF);}
    public Duration maxBackoff(){Duration initial=initialBackoff();Duration d=positive(durationValue(MAX_BACKOFF,startup.getWorker().getMaxBackoff()),MAX_BACKOFF);if(d.compareTo(initial)<0)throw invalid(MAX_BACKOFF);return d;}
    public int expiredLeaseScanBatchSize(){int v=integerValue(EXPIRED_LEASE_SCAN_BATCH_SIZE,startup.getWorker().getExpiredLeaseScanBatchSize());if(v<1||v>1000)throw invalid(EXPIRED_LEASE_SCAN_BATCH_SIZE);return v;}
    public Duration expiredLeaseScanInterval(){long ms=longValue(EXPIRED_LEASE_SCAN_INTERVAL_MS,startup.getWorker().getExpiredLeaseScanIntervalMs());if(ms<250||ms>3_600_000)throw invalid(EXPIRED_LEASE_SCAN_INTERVAL_MS);return Duration.ofMillis(ms);}

    private boolean booleanValue(String key, boolean fallback){if(runtimeRequired(key)){requireKey(key);return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values==null?fallback:values.booleanValue(SET_KEY,key).orElse(fallback);}
    private int integerValue(String key,int fallback){if(runtimeRequired(key)){requireKey(key);return values.integerValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values==null?fallback:values.integerValue(SET_KEY,key).orElse(fallback);}
    private long longValue(String key,long fallback){if(runtimeRequired(key)){requireKey(key);return values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values==null?fallback:values.longValue(SET_KEY,key).orElse(fallback);}
    private Duration durationValue(String key,Duration fallback){if(runtimeRequired(key)){requireKey(key);return values.durationValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values==null?fallback:values.durationValue(SET_KEY,key).orElse(fallback);}
    private boolean runtimeRequired(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);}
    private void requireKey(String key){if(values==null||!values.hasSnapshot(SET_KEY))throw incomplete(key+": snapshot missing");if(!values.keys(SET_KEY).contains(key))throw incomplete(key+": key missing");}
    private static Duration positive(Duration d,String key){if(d==null||d.isZero()||d.isNegative())throw invalid(key);return d;}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED "+key);}
    private static IllegalStateException incomplete(String detail){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" detail="+detail);}
}
