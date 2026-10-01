package com.opensocket.aievent.core.configuration.runtime;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

/**
 * Local-only typed access to authenticated runtime configuration snapshots.
 * The payload is parsed once per setKey/effective snapshot fingerprint and never performs DB/Redis/network I/O.
 * Same-revision emergency overrides therefore invalidate this cache when payloadHash changes.
 */
@Component
public class RuntimeConfigurationSnapshotValues {
    private final RuntimeConfigurationLocalSnapshotRegistry registry;
    private final ObjectMapper objectMapper;
    private final Map<String,CachedSnapshot> cache = new ConcurrentHashMap<>();

    public RuntimeConfigurationSnapshotValues(RuntimeConfigurationLocalSnapshotRegistry registry,ObjectMapper objectMapper) {
        this.registry=registry; this.objectMapper=objectMapper;
    }

    public boolean hasSnapshot(String setKey){return registry.currentBySetKey(setKey).isPresent();}
    public Optional<String> revisionId(String setKey){return registry.currentBySetKey(setKey).map(RuntimeConfigurationSnapshotEnvelope::revisionId);}
    public Optional<String> payloadHash(String setKey){return registry.currentBySetKey(setKey).map(RuntimeConfigurationSnapshotEnvelope::payloadHash);}
    public Set<String> keys(String setKey){return parsed(setKey).map(v->v.values().keySet()).orElse(Set.of());}
    public Optional<String> authorityMode(String setKey){return registry.currentBySetKey(setKey).map(RuntimeConfigurationSnapshotEnvelope::authorityMode);}
    public Set<String> requiredKeys(String setKey){return registry.currentBySetKey(setKey).map(RuntimeConfigurationSnapshotEnvelope::requiredKeys).orElse(Set.of());}
    public boolean isRequired(String setKey,String key){return registry.currentBySetKey(setKey).map(v->v.required(key)).orElse(false);}

    /** Typed JSON node for domain views that own structured JSON runtime configuration. */
    public Optional<JsonNode> jsonValue(String setKey,String key) { return value(setKey,key); }

    /** Canonical JSON for one value, used by control-plane projection for remote-process settings. */
    public Optional<String> canonicalJsonValue(String setKey,String key) {
        return value(setKey,key).map(JsonNode::toString);
    }

    public void requireKeys(String setKey,Set<String> required) {
        if(!hasSnapshot(setKey)) return;
        Set<String> actual=keys(setKey);
        if(!actual.containsAll(required)) {
            Set<String> missing=new java.util.LinkedHashSet<>(required); missing.removeAll(actual);
            throw new IllegalStateException("RUNTIME_CONFIG_SNAPSHOT_INCOMPLETE setKey="+setKey+" missing="+missing);
        }
    }

    public Optional<Boolean> booleanValue(String setKey,String key) {
        Optional<JsonNode> value=value(setKey,key); if(value.isEmpty()) return Optional.empty();
        JsonNode node=value.get(); if(!node.isBoolean()) throw type(setKey,key,"BOOLEAN");
        return Optional.of(node.booleanValue());
    }
    public OptionalInt integerValue(String setKey,String key) {
        Optional<JsonNode> value=value(setKey,key); if(value.isEmpty()) return OptionalInt.empty();
        JsonNode node=value.get(); if(!node.isIntegralNumber()) throw type(setKey,key,"INTEGER");
        return OptionalInt.of(node.intValue());
    }
    public OptionalLong longValue(String setKey,String key) {
        Optional<JsonNode> value=value(setKey,key); if(value.isEmpty()) return OptionalLong.empty();
        JsonNode node=value.get(); if(!node.isIntegralNumber()) throw type(setKey,key,"LONG");
        return OptionalLong.of(node.longValue());
    }
    public Optional<String> textValue(String setKey,String key) {
        Optional<JsonNode> value=value(setKey,key); if(value.isEmpty()) return Optional.empty();
        JsonNode node=value.get(); if(!node.isTextual()) throw type(setKey,key,"STRING");
        return Optional.of(node.textValue());
    }
    public Optional<Duration> durationValue(String setKey,String key) {
        Optional<String> text=textValue(setKey,key); if(text.isEmpty()) return Optional.empty();
        try { Duration d=Duration.parse(text.get()); if(d.isZero()||d.isNegative()) throw new IllegalArgumentException(); return Optional.of(d); }
        catch(RuntimeException ex){throw new IllegalStateException("RUNTIME_CONFIG_VALUE_INVALID_DURATION setKey="+setKey+" key="+key,ex);}
    }

    private Optional<JsonNode> value(String setKey,String key){
        Optional<CachedSnapshot> parsed=parsed(setKey); if(parsed.isEmpty()) return Optional.empty();
        JsonNode node=parsed.get().values().get(key);
        if(node==null && isRequired(setKey,key)) throw new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+setKey+" key="+key);
        return Optional.ofNullable(node);
    }
    private Optional<CachedSnapshot> parsed(String setKey) {
        Optional<RuntimeConfigurationSnapshotEnvelope> envelope=registry.currentBySetKey(setKey);
        if(envelope.isEmpty()) return Optional.empty();
        RuntimeConfigurationSnapshotEnvelope current=envelope.get();
        CachedSnapshot existing=cache.get(setKey);
        if(existing!=null && existing.revisionId().equals(current.revisionId()) && existing.payloadHash().equalsIgnoreCase(current.payloadHash())) return Optional.of(existing);
        try {
            JsonNode root=objectMapper.readTree(current.payloadJson());
            if(root==null||!root.isObject()) throw new IllegalStateException("Runtime configuration payload must be a JSON object");
            java.util.LinkedHashMap<String,JsonNode> values=new java.util.LinkedHashMap<>();
            root.properties().forEach(e->values.put(e.getKey(),e.getValue()));
            CachedSnapshot next=new CachedSnapshot(current.revisionId(),current.payloadHash(),Map.copyOf(values)); cache.put(setKey,next); return Optional.of(next);
        } catch(RuntimeException ex){throw ex;} catch(Exception ex){throw new IllegalStateException("Unable to parse runtime configuration payload for "+setKey,ex);}
    }
    private static IllegalStateException type(String setKey,String key,String expected){return new IllegalStateException("RUNTIME_CONFIG_VALUE_TYPE_MISMATCH setKey="+setKey+" key="+key+" expected="+expected);}
    private record CachedSnapshot(String revisionId,String payloadHash,Map<String,JsonNode> values){}
}
