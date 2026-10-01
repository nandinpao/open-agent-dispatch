package com.opensocket.aievent.core.integration;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2L typed runtime view for external integration-event projection and delivery. */
@Component
public final class IntegrationEventsRuntimeConfigurationView {
    public enum SinkType { NONE, HTTP }
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String BATCH_SIZE="core.integration-events.batch-size";
    public static final String CLAIM_LEASE="core.integration-events.claim-lease";
    public static final String ENDPOINT_URL="core.integration-events.endpoint-url";
    public static final String EXPORTED_EVENT_TYPES="core.integration-events.exported-event-types";
    public static final String INITIAL_BACKOFF="core.integration-events.initial-backoff";
    public static final String MAX_ATTEMPTS="core.integration-events.max-attempts";
    public static final String MAX_BACKOFF="core.integration-events.max-backoff";
    public static final String REQUEST_TIMEOUT="core.integration-events.request-timeout";
    public static final String SCAN_INTERVAL_MS="core.integration-events.scan-interval-ms";
    public static final String SINK="core.integration-events.sink";
    public static final String SOURCE="core.integration-events.source";
    public static final String WORKER_ID="core.integration-events.worker-id";
    public static final Set<String> ALL=Set.of(BATCH_SIZE,CLAIM_LEASE,ENDPOINT_URL,EXPORTED_EVENT_TYPES,INITIAL_BACKOFF,MAX_ATTEMPTS,MAX_BACKOFF,REQUEST_TIMEOUT,SCAN_INTERVAL_MS,SINK,SOURCE,WORKER_ID);
    public static final Set<String> ALLOWED_EVENT_TYPES=Set.of("incident.escalated.v1","task.terminal.v1","adapter-action.requested.v1","dispatch.dead-lettered.v1");
    private static final Pattern ID=Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final IntegrationEventProperties startup;

    public IntegrationEventsRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                                      RuntimeConfigurationAuthorityRegistry authority,
                                                      IntegrationEventProperties startup){
        this.values=values; this.authority=authority; this.startup=startup;
    }
    public boolean runtimeBacked(){return values.hasSnapshot(SET_KEY);} public String revisionId(){return values.revisionId(SET_KEY).orElse(null);}
    public int batchSize(){int v=intValue(BATCH_SIZE,startup.getBatchSize());if(v<1||v>1000)throw invalid(BATCH_SIZE);return v;}
    public Duration claimLease(){return boundedDuration(CLAIM_LEASE,startup.getClaimLease(),Duration.ofSeconds(1),Duration.ofHours(1));}
    public Duration initialBackoff(){return boundedDuration(INITIAL_BACKOFF,startup.getInitialBackoff(),Duration.ofMillis(100),Duration.ofHours(1));}
    public int maxAttempts(){int v=intValue(MAX_ATTEMPTS,startup.getMaxAttempts());if(v<1||v>100)throw invalid(MAX_ATTEMPTS);return v;}
    public Duration maxBackoff(){Duration v=boundedDuration(MAX_BACKOFF,startup.getMaxBackoff(),Duration.ofMillis(100),Duration.ofDays(1));if(v.compareTo(initialBackoff())<0)throw invalid(MAX_BACKOFF);return v;}
    public Duration requestTimeout(){return boundedDuration(REQUEST_TIMEOUT,startup.getRequestTimeout(),Duration.ofMillis(100),Duration.ofMinutes(5));}
    public Duration scanInterval(){long v=longValue(SCAN_INTERVAL_MS,startup.getScanIntervalMs());if(v<250||v>3_600_000)throw invalid(SCAN_INTERVAL_MS);return Duration.ofMillis(v);}
    public SinkType sink(){String raw=textValue(SINK,startup.getSink());try{return SinkType.valueOf(raw.trim().toUpperCase());}catch(RuntimeException ex){throw invalid(SINK);}}
    public String source(){String v=textValue(SOURCE,startup.getSource()).trim();if(!ID.matcher(v).matches())throw invalid(SOURCE);return v;}
    public String workerId(){String v=textValue(WORKER_ID,startup.getWorkerId()).trim();if(!ID.matcher(v).matches())throw invalid(WORKER_ID);return v;}
    public String endpointUrl(){String v=textValue(ENDPOINT_URL,startup.getEndpointUrl()).trim();if(v.isBlank()){if(sink()==SinkType.HTTP)throw invalid(ENDPOINT_URL);return "";}try{URI uri=URI.create(v);String scheme=uri.getScheme();if(scheme==null||(!scheme.equalsIgnoreCase("http")&&!scheme.equalsIgnoreCase("https")))throw invalid(ENDPOINT_URL);}catch(RuntimeException ex){if(ex instanceof IllegalStateException ise)throw ise;throw invalid(ENDPOINT_URL);}return v;}
    public Set<String> exportedEventTypes(){String fallback=String.join(",",startup.getExportedEventTypes());String raw=textValue(EXPORTED_EVENT_TYPES,fallback);LinkedHashSet<String> result=new LinkedHashSet<>();Arrays.stream(raw.split(",")).map(String::trim).filter(v->!v.isBlank()).forEach(result::add);if(result.isEmpty()||!ALLOWED_EVENT_TYPES.containsAll(result))throw invalid(EXPORTED_EVENT_TYPES);return Set.copyOf(result);}

    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);} private void require(String key){if(!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);}
    private int intValue(String key,int fallback){if(required(key)){require(key);return values.integerValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.integerValue(SET_KEY,key).orElse(fallback);}
    private long longValue(String key,long fallback){if(required(key)){require(key);return values.longValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.longValue(SET_KEY,key).orElse(fallback);}
    private String textValue(String key,String fallback){if(required(key)){require(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fallback);}
    private Duration durationValue(String key,Duration fallback){if(required(key)){require(key);return values.durationValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.durationValue(SET_KEY,key).orElse(fallback);}
    private Duration boundedDuration(String key,Duration fallback,Duration min,Duration max){Duration v=durationValue(key,fallback);if(v==null||v.compareTo(min)<0||v.compareTo(max)>0)throw invalid(key);return v;}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key);} private static IllegalStateException incomplete(String key){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key);}
}
