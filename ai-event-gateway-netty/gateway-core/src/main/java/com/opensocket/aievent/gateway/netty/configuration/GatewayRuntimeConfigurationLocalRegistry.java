package com.opensocket.aievent.gateway.netty.configuration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Gateway local immutable snapshot hot path. No Redis/DB network read occurs on business access. */
public final class GatewayRuntimeConfigurationLocalRegistry {
    private final Map<String,AtomicReference<GatewayRuntimeConfigurationSnapshot>> byId=new ConcurrentHashMap<>();
    private final Map<String,AtomicReference<GatewayRuntimeConfigurationSnapshot>> bySetKey=new ConcurrentHashMap<>();
    public Optional<GatewayRuntimeConfigurationSnapshot> current(String configSetId){AtomicReference<GatewayRuntimeConfigurationSnapshot> r=byId.get(configSetId);return r==null?Optional.empty():Optional.ofNullable(r.get());}
    public Optional<GatewayRuntimeConfigurationSnapshot> currentBySetKey(String setKey){AtomicReference<GatewayRuntimeConfigurationSnapshot> r=bySetKey.get(setKey);return r==null?Optional.empty():Optional.ofNullable(r.get());}
    public java.util.Set<String> currentConfigSetIds(){return java.util.Set.copyOf(byId.keySet());}
    public java.util.Map<String,GatewayRuntimeConfigurationSnapshot> snapshots(){java.util.LinkedHashMap<String,GatewayRuntimeConfigurationSnapshot> out=new java.util.LinkedHashMap<>();byId.forEach((id,ref)->{GatewayRuntimeConfigurationSnapshot s=ref.get();if(s!=null)out.put(id,s);});return java.util.Map.copyOf(out);}
    public GatewayRuntimeConfigurationSnapshot atomicSwap(GatewayRuntimeConfigurationSnapshot next){
        if(next==null||next.configSetId()==null||next.configSetId().isBlank()||next.setKey()==null||next.setKey().isBlank()) throw new IllegalArgumentException("snapshot configSetId/setKey is required");
        AtomicReference<GatewayRuntimeConfigurationSnapshot> ref=byId.computeIfAbsent(next.configSetId(),ignored->new AtomicReference<>());
        while(true){GatewayRuntimeConfigurationSnapshot current=ref.get();if(current!=null&&current.sequenceNo()>next.sequenceNo())return current;if(ref.compareAndSet(current,next)){bySetKey.put(next.setKey(),ref);return next;}}
    }
}
