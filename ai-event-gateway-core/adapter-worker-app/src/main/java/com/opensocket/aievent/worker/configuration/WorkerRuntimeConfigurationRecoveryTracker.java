package com.opensocket.aievent.worker.configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkerRuntimeConfigurationRecoveryTracker {
    private final Map<String,Entry> entries=new ConcurrentHashMap<>();
    public void mark(String configSetId,String setKey,String revisionId,WorkerRuntimeConfigurationSnapshotState state,String source,String error){String id=configSetId==null||configSetId.isBlank()?"UNKNOWN":configSetId;entries.put(id,new Entry(id,setKey,revisionId,state,source,error,OffsetDateTime.now(ZoneOffset.UTC)));}
    public Map<String,Entry> snapshot(){return Map.copyOf(new LinkedHashMap<>(entries));}
    public record Entry(String configSetId,String setKey,String revisionId,WorkerRuntimeConfigurationSnapshotState state,String source,String error,OffsetDateTime observedAt){}
}
