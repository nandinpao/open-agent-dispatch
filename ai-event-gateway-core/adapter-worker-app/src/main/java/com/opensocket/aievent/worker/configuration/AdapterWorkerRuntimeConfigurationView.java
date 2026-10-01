package com.opensocket.aievent.worker.configuration;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.opensocket.aievent.worker.AdapterWorkerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Local-only typed read path for the separately deployed Adapter Worker. */
@Component
public final class AdapterWorkerRuntimeConfigurationView {
    public static final String SET_KEY="RUNTIME/ADAPTER_WORKER/SYSTEM";
    public static final String ENABLED="adapter-worker.enabled";
    public static final String ADAPTER_TYPES="adapter-worker.adapter-types";
    public static final String LEASE_SECONDS="adapter-worker.lease-seconds";
    public static final String POLL_INTERVAL_MS="adapter-worker.poll-interval-ms";
    public static final String REQUEST_TIMEOUT="adapter-worker.request-timeout";
    public static final Set<String> ALL=Set.of(ENABLED,ADAPTER_TYPES,LEASE_SECONDS,POLL_INTERVAL_MS,REQUEST_TIMEOUT);

    private final AdapterWorkerProperties startup;
    private final WorkerRuntimeConfigurationLocalRegistry registry;
    private final ObjectMapper objectMapper;
    private final boolean failClosedOnMissingSnapshot;
    private final boolean lkgEnabled;
    private final long maxStaleMs;
    private final Map<String,CachedSnapshot> cache=new ConcurrentHashMap<>();

    @Autowired
    public AdapterWorkerRuntimeConfigurationView(AdapterWorkerProperties startup,
            WorkerRuntimeConfigurationLocalRegistry registry,ObjectMapper objectMapper,
            WorkerRuntimeConfigurationProperties runtimeProperties){
        this(startup,registry,objectMapper,new AccessPolicy(runtimeProperties!=null&&runtimeProperties.enabled()&&runtimeProperties.failClosedOnColdStart(),runtimeProperties==null||runtimeProperties.lkgEnabled(),runtimeProperties==null?300000:runtimeProperties.maxStaleMs()));
    }

    /** Focused-test / startup-only compatibility constructor. */
    public AdapterWorkerRuntimeConfigurationView(AdapterWorkerProperties startup,
            WorkerRuntimeConfigurationLocalRegistry registry,ObjectMapper objectMapper){
        this(startup,registry,objectMapper,new AccessPolicy(false,true,300000));
    }

    AdapterWorkerRuntimeConfigurationView(AdapterWorkerProperties startup,
            WorkerRuntimeConfigurationLocalRegistry registry,ObjectMapper objectMapper,
            boolean failClosedOnMissingSnapshot){
        this(startup,registry,objectMapper,new AccessPolicy(failClosedOnMissingSnapshot,true,300000));
    }

    private AdapterWorkerRuntimeConfigurationView(AdapterWorkerProperties startup,
            WorkerRuntimeConfigurationLocalRegistry registry,ObjectMapper objectMapper,AccessPolicy policy){
        this.startup=startup;this.registry=registry;this.objectMapper=objectMapper;this.failClosedOnMissingSnapshot=policy.failClosedOnMissingSnapshot();this.lkgEnabled=policy.lkgEnabled();this.maxStaleMs=policy.maxStaleMs();
    }

    public boolean runtimeBacked(){return registry.currentBySetKey(SET_KEY).isPresent();}
    public String revisionId(){return registry.currentBySetKey(SET_KEY).map(WorkerRuntimeConfigurationSnapshot::revisionId).orElse(null);}
    public boolean enabled(){return booleanValue(ENABLED,startup.isEnabled());}
    public Set<String> adapterTypes(){
        String fallback=String.join(",",startup.getAdapterTypes());
        String raw=textValue(ADAPTER_TYPES,fallback);
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String token:raw.split(",")){String value=token.trim().toUpperCase();if(!value.isBlank())result.add(value);}
        if(result.isEmpty())throw invalid(ADAPTER_TYPES);
        if(result.stream().anyMatch(v->!"MCP".equals(v)))throw new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED "+ADAPTER_TYPES+" unsupported="+result);
        return Set.copyOf(result);
    }
    public long leaseSeconds(){long v=longValue(LEASE_SECONDS,startup.getLeaseSeconds());if(v<10||v>3600)throw invalid(LEASE_SECONDS);return v;}
    public Duration pollInterval(){long ms=longValue(POLL_INTERVAL_MS,startup.getPollIntervalMs());if(ms<250||ms>3_600_000)throw invalid(POLL_INTERVAL_MS);return Duration.ofMillis(ms);}
    public Duration requestTimeout(){Duration d=durationValue(REQUEST_TIMEOUT,startup.getRequestTimeout());if(d==null||d.isZero()||d.isNegative()||d.compareTo(Duration.ofMinutes(5))>0)throw invalid(REQUEST_TIMEOUT);return d;}

    public boolean isConfigured(String adapterType){
        if(startup.isMockSuccessEnabled())return true;
        if("MCP".equalsIgnoreCase(adapterType))return !startup.getMcpEndpointUrl().isBlank();
        return false;
    }

    private boolean booleanValue(String key,boolean fallback){JsonNode n=value(key);if(n==null)return fallback;if(!n.isBoolean())throw type(key,"BOOLEAN");return n.booleanValue();}
    private long longValue(String key,long fallback){JsonNode n=value(key);if(n==null)return fallback;if(!n.isIntegralNumber())throw type(key,"LONG");return n.longValue();}
    private String textValue(String key,String fallback){JsonNode n=value(key);if(n==null)return fallback;if(!n.isTextual())throw type(key,"STRING");return n.textValue();}
    private Duration durationValue(String key,Duration fallback){String text=textValue(key,null);if(text==null)return fallback;try{return Duration.parse(text);}catch(RuntimeException ex){throw new IllegalStateException("RUNTIME_CONFIG_VALUE_INVALID_DURATION setKey="+SET_KEY+" key="+key,ex);}}
    private JsonNode value(String key){
        var current=registry.currentBySetKey(SET_KEY).orElse(null);
        if(current==null){
            if(failClosedOnMissingSnapshot)throw new IllegalStateException("RUNTIME_CONFIGURATION_SNAPSHOT_MISSING setKey="+SET_KEY+" key="+key);
            return null;
        }
        WorkerRuntimeConfigurationSnapshotState state=WorkerRuntimeConfigurationSnapshotFreshness.state(current,lkgEnabled?maxStaleMs:0);
        if(state==WorkerRuntimeConfigurationSnapshotState.EXPIRED||state==WorkerRuntimeConfigurationSnapshotState.INVALID){
            if(current.runtimeOnly()||failClosedOnMissingSnapshot)throw new IllegalStateException("RUNTIME_CONFIGURATION_SNAPSHOT_EXPIRED setKey="+SET_KEY+" key="+key);
            return null;
        }
        JsonNode node=parsed(current).get(key);
        if(node==null&&current.required(key))throw new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key);
        return node;
    }
    private Map<String,JsonNode> parsed(WorkerRuntimeConfigurationSnapshot current){
        CachedSnapshot c=cache.get(SET_KEY);if(c!=null&&c.revisionId.equals(current.revisionId())&&c.payloadHash.equalsIgnoreCase(current.payloadHash()))return c.values;
        try{JsonNode root=objectMapper.readTree(current.payloadJson());if(root==null||!root.isObject())throw new IllegalStateException("Runtime configuration payload must be a JSON object");java.util.LinkedHashMap<String,JsonNode> values=new java.util.LinkedHashMap<>();root.properties().forEach(e->values.put(e.getKey(),e.getValue()));CachedSnapshot next=new CachedSnapshot(current.revisionId(),current.payloadHash(),Map.copyOf(values));cache.put(SET_KEY,next);return next.values;}catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Unable to parse worker runtime configuration payload",ex);}
    }
    private static IllegalStateException type(String key,String expected){return new IllegalStateException("RUNTIME_CONFIG_VALUE_TYPE_MISMATCH setKey="+SET_KEY+" key="+key+" expected="+expected);}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED "+key);}
    private record CachedSnapshot(String revisionId,String payloadHash,Map<String,JsonNode> values){}
    private record AccessPolicy(boolean failClosedOnMissingSnapshot,boolean lkgEnabled,long maxStaleMs){}
}
