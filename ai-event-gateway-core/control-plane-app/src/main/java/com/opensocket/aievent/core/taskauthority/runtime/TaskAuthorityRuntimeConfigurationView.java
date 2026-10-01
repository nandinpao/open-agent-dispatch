package com.opensocket.aievent.core.taskauthority.runtime;

import java.time.Duration;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** C3R2H typed runtime view for Task condition/finalization operational controls. */
@Component
public final class TaskAuthorityRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.TASK_AUTHORITY_SYSTEM;
    public static final String CONDITIONS_ENABLED="opendispatch.a0-r2.conditions.enabled";
    public static final String CONDITIONS_BATCH_SIZE="opendispatch.a0-r2.conditions.batch-size";
    public static final String CONDITIONS_POLL_MS="opendispatch.a0-r2.conditions.poll-ms";
    public static final String FINALIZATION_ENABLED="opendispatch.a0-r2.finalization.enabled";
    public static final String FINALIZATION_BATCH_SIZE="opendispatch.a0-r2.finalization.batch-size";
    public static final String FINALIZATION_CLAIM_SECONDS="opendispatch.a0-r2.finalization.claim-seconds";
    public static final String FINALIZATION_MAX_ATTEMPTS="opendispatch.a0-r2.finalization.max-attempts";
    public static final String FINALIZATION_POLL_MS="opendispatch.a0-r2.finalization.poll-ms";
    public static final String PROJECTION_ENABLED="opendispatch.a0-r2.finalization.projection-enabled";
    public static final String PROJECTION_BATCH_SIZE="opendispatch.a0-r2.finalization.projection-batch-size";
    public static final String PROJECTION_CLAIM_SECONDS="opendispatch.a0-r2.finalization.projection-claim-seconds";
    public static final String PROJECTION_MAX_ATTEMPTS="opendispatch.a0-r2.finalization.projection-max-attempts";
    public static final String PROJECTION_POLL_MS="opendispatch.a0-r2.finalization.projection-poll-ms";
    public static final Set<String> ALL=Set.of(CONDITIONS_ENABLED,CONDITIONS_BATCH_SIZE,CONDITIONS_POLL_MS,FINALIZATION_ENABLED,FINALIZATION_BATCH_SIZE,FINALIZATION_CLAIM_SECONDS,FINALIZATION_MAX_ATTEMPTS,FINALIZATION_POLL_MS,PROJECTION_ENABLED,PROJECTION_BATCH_SIZE,PROJECTION_CLAIM_SECONDS,PROJECTION_MAX_ATTEMPTS,PROJECTION_POLL_MS);
    private final RuntimeConfigurationSnapshotValues values; private final RuntimeConfigurationAuthorityRegistry authority; private final Environment startup;
    public TaskAuthorityRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,RuntimeConfigurationAuthorityRegistry authority,Environment startup){this.values=values;this.authority=authority;this.startup=startup;}
    public boolean conditionsEnabled(){return bool(CONDITIONS_ENABLED,true);} public int conditionsBatchSize(){return integer(CONDITIONS_BATCH_SIZE,100,1,500);} public Duration conditionsPollDelay(){return millis(CONDITIONS_POLL_MS,5000,100,3600000);}
    public boolean finalizationEnabled(){return bool(FINALIZATION_ENABLED,true);} public int finalizationBatchSize(){return integer(FINALIZATION_BATCH_SIZE,20,1,100);} public int finalizationClaimSeconds(){return integer(FINALIZATION_CLAIM_SECONDS,60,15,300);} public int finalizationMaxAttempts(){return integer(FINALIZATION_MAX_ATTEMPTS,8,1,50);} public Duration finalizationPollDelay(){return millis(FINALIZATION_POLL_MS,2000,100,3600000);}
    public boolean projectionEnabled(){return bool(PROJECTION_ENABLED,true);} public int projectionBatchSize(){return integer(PROJECTION_BATCH_SIZE,50,1,250);} public int projectionClaimSeconds(){return integer(PROJECTION_CLAIM_SECONDS,60,15,300);} public int projectionMaxAttempts(){return integer(PROJECTION_MAX_ATTEMPTS,20,1,100);} public Duration projectionPollDelay(){return millis(PROJECTION_POLL_MS,3000,100,3600000);}
    private boolean bool(String key,boolean fallback){if(required(key)){requireSnapshot();return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.booleanValue(SET_KEY,key).orElse(startup.getProperty(key,Boolean.class,fallback));}
    private int integer(String key,int fallback,int min,int max){long v;if(required(key)){requireSnapshot();v=values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}else v=values.longValue(SET_KEY,key).orElse(startup.getProperty(key,Long.class,(long)fallback));if(v<min||v>max)throw invalid(key,v);return Math.toIntExact(v);}
    private Duration millis(String key,long fallback,long min,long max){long v;if(required(key)){requireSnapshot();v=values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}else v=values.longValue(SET_KEY,key).orElse(startup.getProperty(key,Long.class,fallback));if(v<min||v>max)throw invalid(key,v);return Duration.ofMillis(v);}
    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);} private void requireSnapshot(){if(!values.hasSnapshot(SET_KEY))throw incomplete("*");values.requireKeys(SET_KEY,ALL);} private static IllegalStateException invalid(String k,Object v){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+k+" value="+v);} private static IllegalStateException incomplete(String k){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+k);}
}
