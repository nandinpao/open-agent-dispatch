package com.opensocket.aievent.worker.configuration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Worker lock-free local snapshot read path. */
public final class WorkerRuntimeConfigurationLocalRegistry {
    private final Map<String,AtomicReference<WorkerRuntimeConfigurationSnapshot>> byId=new ConcurrentHashMap<>();
    private final Map<String,AtomicReference<WorkerRuntimeConfigurationSnapshot>> bySetKey=new ConcurrentHashMap<>();
    public Optional<WorkerRuntimeConfigurationSnapshot> current(String configSetId){var r=byId.get(configSetId);return r==null?Optional.empty():Optional.ofNullable(r.get());}
    public Optional<WorkerRuntimeConfigurationSnapshot> currentBySetKey(String setKey){var r=bySetKey.get(setKey);return r==null?Optional.empty():Optional.ofNullable(r.get());}
    public java.util.Set<String> currentConfigSetIds(){return java.util.Set.copyOf(byId.keySet());}
    public java.util.Map<String,WorkerRuntimeConfigurationSnapshot> snapshots(){java.util.LinkedHashMap<String,WorkerRuntimeConfigurationSnapshot> out=new java.util.LinkedHashMap<>();byId.forEach((id,ref)->{WorkerRuntimeConfigurationSnapshot s=ref.get();if(s!=null)out.put(id,s);});return java.util.Map.copyOf(out);}
    public WorkerRuntimeConfigurationSnapshot atomicSwap(WorkerRuntimeConfigurationSnapshot next){
        if(next==null||blank(next.configSetId())||blank(next.setKey()))throw new IllegalArgumentException("snapshot configSetId/setKey is required");
        var ref=byId.computeIfAbsent(next.configSetId(),ignored->new AtomicReference<WorkerRuntimeConfigurationSnapshot>());
        while(true){var current=ref.get();if(current!=null&&current.sequenceNo()>next.sequenceNo())return current;if(ref.compareAndSet(current,next)){bySetKey.put(next.setKey(),ref);return next;}}
    }
    private static boolean blank(String v){return v==null||v.isBlank();}
}
