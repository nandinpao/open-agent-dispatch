package com.opensocket.aievent.core.incident;

import java.time.Duration;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** C3R2K typed runtime view shared by incident lifecycle scanning and recurring-incident reopen policy. */
@Component
public final class IncidentRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.INCIDENT_SYSTEM;
    public static final String SCAN_INTERVAL_MS="core.lifecycle.incident.scan-interval-ms";
    public static final String INACTIVE_THRESHOLD="core.lifecycle.incident.inactive-threshold";
    public static final String MAX_BATCH_SIZE="core.lifecycle.incident.max-batch-size";
    public static final String REOPEN_POLICY="core.lifecycle.incident.reopen-policy";
    public static final String REOPEN_WINDOW="core.lifecycle.incident.reopen-window";
    public static final Set<String> ALL=Set.of(SCAN_INTERVAL_MS,INACTIVE_THRESHOLD,MAX_BATCH_SIZE,REOPEN_POLICY,REOPEN_WINDOW);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final Environment startup;

    public IncidentRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                            RuntimeConfigurationAuthorityRegistry authority,
                                            Environment startup) {
        this.values=values; this.authority=authority; this.startup=startup;
    }
    public boolean runtimeBacked(){return values.hasSnapshot(SET_KEY);} public String revisionId(){return values.revisionId(SET_KEY).orElse(null);}
    public Duration scanInterval(){long v=longValue(SCAN_INTERVAL_MS,startup.getProperty(SCAN_INTERVAL_MS,Long.class,60000L)); if(v<1000||v>3600000)throw invalid(SCAN_INTERVAL_MS); return Duration.ofMillis(v);}
    public Duration inactiveThreshold(){return boundedDuration(INACTIVE_THRESHOLD,durationFallback(INACTIVE_THRESHOLD,Duration.ofHours(12)),Duration.ofMinutes(1),Duration.ofDays(30));}
    public int maxBatchSize(){int v=intValue(MAX_BATCH_SIZE,startup.getProperty(MAX_BATCH_SIZE,Integer.class,100));if(v<1||v>5000)throw invalid(MAX_BATCH_SIZE);return v;}
    public IncidentModuleProperties.ReopenPolicy reopenPolicy(){String raw=textValue(REOPEN_POLICY,startup.getProperty(REOPEN_POLICY,"REOPEN_RECENT"));try{return IncidentModuleProperties.ReopenPolicy.valueOf(raw.trim().toUpperCase());}catch(RuntimeException ex){throw invalid(REOPEN_POLICY);}}
    public Duration reopenWindow(){return boundedDuration(REOPEN_WINDOW,durationFallback(REOPEN_WINDOW,Duration.ofHours(24)),Duration.ofMinutes(1),Duration.ofDays(30));}
    private Duration durationFallback(String key,Duration fallback){String v=startup.getProperty(key);if(v==null||v.isBlank())return fallback;try{return org.springframework.boot.convert.DurationStyle.detectAndParse(v);}catch(RuntimeException ex){return fallback;}}
    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);} private void require(String key){if(!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);}
    private long longValue(String key,long fallback){if(required(key)){require(key);return values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.longValue(SET_KEY,key).orElse(fallback);}
    private int intValue(String key,int fallback){if(required(key)){require(key);return values.integerValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.integerValue(SET_KEY,key).orElse(fallback);}
    private String textValue(String key,String fallback){if(required(key)){require(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fallback);}
    private Duration durationValue(String key,Duration fallback){if(required(key)){require(key);return values.durationValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.durationValue(SET_KEY,key).orElse(fallback);}
    private Duration boundedDuration(String key,Duration fallback,Duration min,Duration max){Duration v=durationValue(key,fallback);if(v==null||v.compareTo(min)<0||v.compareTo(max)>0)throw invalid(key);return v;}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key);} private static IllegalStateException incomplete(String key){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key);}
}
