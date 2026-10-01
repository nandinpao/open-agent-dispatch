package com.opensocket.aievent.core.operational;

import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2N typed runtime view for operational backlog telemetry cadence and SLO thresholds. */
@Component
public final class OperationalRuntimeConfigurationView {
 public static final String SET_KEY="RUNTIME/OPENDISPATCH/SYSTEM";
 public static final String REFRESH_MS="opendispatch.operational.metrics.refresh-ms";
 public static final String DISPATCH_SLO_SECONDS="opendispatch.operational.slo.dispatch-oldest-age-seconds";
 public static final String RECONCILIATION_SLO_SECONDS="opendispatch.operational.slo.reconciliation-oldest-age-seconds";
 public static final Set<String> ALL=Set.of(REFRESH_MS,DISPATCH_SLO_SECONDS,RECONCILIATION_SLO_SECONDS);
 private final RuntimeConfigurationSnapshotValues values; private final RuntimeConfigurationAuthorityRegistry authority;
 private final long refreshMs,dispatchSlo,reconciliationSlo;
 @Autowired public OperationalRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,RuntimeConfigurationAuthorityRegistry authority,
   @Value("${opendispatch.operational.metrics.refresh-ms:5000}") long refreshMs,
   @Value("${opendispatch.operational.slo.dispatch-oldest-age-seconds:30}") long dispatchSlo,
   @Value("${opendispatch.operational.slo.reconciliation-oldest-age-seconds:120}") long reconciliationSlo){this.values=values;this.authority=authority;this.refreshMs=refreshMs;this.dispatchSlo=dispatchSlo;this.reconciliationSlo=reconciliationSlo;}
 public OperationalRuntimeConfigurationView(long refreshMs,long dispatchSlo,long reconciliationSlo){this(null,null,refreshMs,dispatchSlo,reconciliationSlo);}
 public long refreshMs(){return bounded(REFRESH_MS,read(REFRESH_MS,refreshMs),250L,300_000L);} public Duration refreshInterval(){return Duration.ofMillis(refreshMs());}
 public long dispatchSloSeconds(){return bounded(DISPATCH_SLO_SECONDS,read(DISPATCH_SLO_SECONDS,dispatchSlo),1L,86_400L);}
 public long reconciliationSloSeconds(){return bounded(RECONCILIATION_SLO_SECONDS,read(RECONCILIATION_SLO_SECONDS,reconciliationSlo),1L,86_400L);}
 private long read(String key,long fallback){if(values==null)return fallback;if(authority!=null&&authority.isRuntimeAuthoritative(key)){if(!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);return values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.longValue(SET_KEY,key).orElse(fallback);}
 private static long bounded(String key,long value,long min,long max){if(value<min||value>max)throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key+" value="+value);return value;}
 private static IllegalStateException incomplete(String key){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key);}
}
