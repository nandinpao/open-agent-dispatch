package com.opensocket.aievent.gateway.netty.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Local-only typed access to authenticated Gateway runtime-configuration snapshots.
 *
 * <p>No DB/Redis/network access occurs on a business read. The reconciler owns remote I/O and
 * atomically swaps verified snapshots into {@link GatewayRuntimeConfigurationLocalRegistry}.</p>
 *
 * <p>HF2 authority rule: startup fallback is permitted only while the remote runtime-configuration
 * plane is not configured as strict. Once reconcile is enabled with cold-start fail-closed, a
 * missing snapshot is an authority failure rather than an implicit request to reactivate YAML/ENV.
 * Missing keys listed in the signed authority contract are always fail-closed.</p>
 */
@Component
public final class GatewayRuntimeConfigurationSnapshotValues {
    private final GatewayRuntimeConfigurationLocalRegistry registry;
    private final ObjectMapper objectMapper;
    private final boolean failClosedOnMissingSnapshot;
    private final boolean lkgEnabled;
    private final long maxStaleMs;
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    @Autowired
    public GatewayRuntimeConfigurationSnapshotValues(
            GatewayRuntimeConfigurationLocalRegistry registry,
            ObjectMapper objectMapper,
            ObjectProvider<GatewayRuntimeConfigurationProperties> propertiesProvider) {
        this(registry, objectMapper, policy(propertiesProvider == null ? null : propertiesProvider.getIfAvailable()));
    }

    /** Focused-test / startup-only compatibility constructor. */
    public GatewayRuntimeConfigurationSnapshotValues(
            GatewayRuntimeConfigurationLocalRegistry registry,
            ObjectMapper objectMapper) {
        this(registry, objectMapper, new AccessPolicy(false, true, 300000));
    }

    GatewayRuntimeConfigurationSnapshotValues(
            GatewayRuntimeConfigurationLocalRegistry registry,
            ObjectMapper objectMapper,
            boolean failClosedOnMissingSnapshot) {
        this(registry, objectMapper, new AccessPolicy(failClosedOnMissingSnapshot, true, 300000));
    }

    private GatewayRuntimeConfigurationSnapshotValues(
            GatewayRuntimeConfigurationLocalRegistry registry,
            ObjectMapper objectMapper,
            AccessPolicy policy) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.failClosedOnMissingSnapshot = policy.failClosedOnMissingSnapshot();
        this.lkgEnabled = policy.lkgEnabled();
        this.maxStaleMs = policy.maxStaleMs();
    }

    public boolean hasSnapshot(String setKey) {
        return registry.currentBySetKey(setKey).isPresent();
    }

    public Set<String> keys(String setKey) {
        return parsed(setKey).map(v -> v.values().keySet()).orElse(Set.of());
    }

    public Optional<String> authorityMode(String setKey) {
        return registry.currentBySetKey(setKey).map(GatewayRuntimeConfigurationSnapshot::authorityMode);
    }

    public Set<String> requiredKeys(String setKey) {
        return registry.currentBySetKey(setKey).map(GatewayRuntimeConfigurationSnapshot::requiredKeys).orElse(Set.of());
    }

    public boolean runtimeAuthoritative(String setKey, String key) {
        return registry.currentBySetKey(setKey).map(snapshot -> snapshot.required(key)).orElse(false);
    }

    /**
     * Central fallback policy for Gateway consumers. booleanValue() validates signed requiredKeys
     * before this method may return the startup value.
     */
    public boolean booleanValueOrStartup(String setKey, String key, boolean startupValue) {
        return booleanValue(setKey, key).orElse(startupValue);
    }

    /** Central fallback policy for integral Gateway runtime values. */
    public long longValueOrStartup(String setKey, String key, long startupValue) {
        return longValue(setKey, key).orElse(startupValue);
    }

    public Optional<Boolean> booleanValue(String setKey, String key) {
        Optional<JsonNode> value = value(setKey, key);
        if (value.isEmpty()) return Optional.empty();
        JsonNode node = value.get();
        if (!node.isBoolean()) throw type(setKey, key, "BOOLEAN");
        return Optional.of(node.booleanValue());
    }

    public OptionalLong longValue(String setKey, String key) {
        Optional<JsonNode> value = value(setKey, key);
        if (value.isEmpty()) return OptionalLong.empty();
        JsonNode node = value.get();
        if (!node.isIntegralNumber()) throw type(setKey, key, "LONG");
        return OptionalLong.of(node.longValue());
    }

    private Optional<JsonNode> value(String setKey, String key) {
        Optional<GatewayRuntimeConfigurationSnapshot> envelope = registry.currentBySetKey(setKey);
        if (envelope.isEmpty()) {
            if (failClosedOnMissingSnapshot) {
                throw new IllegalStateException(
                        "RUNTIME_CONFIGURATION_SNAPSHOT_MISSING setKey=" + setKey + " key=" + key);
            }
            return Optional.empty();
        }
        GatewayRuntimeConfigurationSnapshot snapshot = envelope.get();
        GatewayRuntimeConfigurationSnapshotState state = GatewayRuntimeConfigurationSnapshotFreshness.state(
                snapshot, lkgEnabled ? maxStaleMs : 0);
        if (state == GatewayRuntimeConfigurationSnapshotState.EXPIRED
                || state == GatewayRuntimeConfigurationSnapshotState.INVALID) {
            if (snapshot.runtimeOnly() || failClosedOnMissingSnapshot) {
                throw new IllegalStateException(
                        "RUNTIME_CONFIGURATION_SNAPSHOT_EXPIRED setKey=" + setKey + " key=" + key);
            }
            return Optional.empty();
        }
        Optional<CachedSnapshot> parsed = parsed(snapshot);
        JsonNode node = parsed.orElseThrow().values().get(key);
        if (node == null && snapshot.required(key)) {
            throw new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + setKey + " key=" + key);
        }
        return Optional.ofNullable(node);
    }

    private Optional<CachedSnapshot> parsed(String setKey) {
        Optional<GatewayRuntimeConfigurationSnapshot> envelope = registry.currentBySetKey(setKey);
        if (envelope.isEmpty()) return Optional.empty();
        return parsed(envelope.get());
    }

    private Optional<CachedSnapshot> parsed(GatewayRuntimeConfigurationSnapshot current) {
        String setKey = current.setKey();
        CachedSnapshot existing = cache.get(setKey);
        if (existing != null
                && existing.revisionId().equals(current.revisionId())
                && existing.payloadHash().equalsIgnoreCase(current.payloadHash())) {
            return Optional.of(existing);
        }
        try {
            JsonNode root = objectMapper.readTree(current.payloadJson());
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Gateway runtime configuration payload must be a JSON object");
            }
            LinkedHashMap<String, JsonNode> values = new LinkedHashMap<>();
            root.properties().forEach(e -> values.put(e.getKey(), e.getValue()));
            CachedSnapshot next = new CachedSnapshot(current.revisionId(), current.payloadHash(), Map.copyOf(values));
            cache.put(setKey, next);
            return Optional.of(next);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse Gateway runtime configuration payload for " + setKey, ex);
        }
    }

    private static AccessPolicy policy(GatewayRuntimeConfigurationProperties properties) {
        if (properties == null) return new AccessPolicy(false, true, 300000);
        return new AccessPolicy(properties.enabled() && properties.failClosedOnColdStart(),
                properties.lkgEnabled(), properties.maxStaleMs());
    }

    private static IllegalStateException type(String setKey, String key, String expected) {
        return new IllegalStateException(
                "RUNTIME_CONFIG_VALUE_TYPE_MISMATCH setKey=" + setKey + " key=" + key + " expected=" + expected);
    }

    private record CachedSnapshot(String revisionId, String payloadHash, Map<String, JsonNode> values) {}
    private record AccessPolicy(boolean failClosedOnMissingSnapshot, boolean lkgEnabled, long maxStaleMs) {}
}
