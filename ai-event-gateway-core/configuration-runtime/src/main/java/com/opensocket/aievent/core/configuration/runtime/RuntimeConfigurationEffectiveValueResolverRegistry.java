package com.opensocket.aievent.core.configuration.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for domain-owned Runtime Configuration effective-value resolvers.
 *
 * <p>Duplicate ownership is rejected during construction. Control-plane consumers can also assert
 * that every migration-authorized key has exactly one resolver before serving operator traffic.</p>
 */
@Component
public final class RuntimeConfigurationEffectiveValueResolverRegistry {
    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigurationEffectiveValueResolverRegistry.class);
    private final Map<String, RuntimeConfigurationEffectiveValueResolver> byKey;
    private final Map<String, String> ownerByKey;

    public RuntimeConfigurationEffectiveValueResolverRegistry(List<RuntimeConfigurationEffectiveValueResolver> resolvers) {
        Map<String, RuntimeConfigurationEffectiveValueResolver> index = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (RuntimeConfigurationEffectiveValueResolver resolver : resolvers == null ? List.<RuntimeConfigurationEffectiveValueResolver>of() : resolvers) {
            if (resolver == null) continue;
            String owner = requiredOwner(resolver.owner());
            Set<String> keys = resolver.supportedKeys();
            if (keys == null || keys.isEmpty())
                throw new IllegalStateException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_EMPTY owner=" + owner);
            for (String rawKey : keys) {
                String key = requiredKey(rawKey, owner);
                RuntimeConfigurationEffectiveValueResolver previous = index.putIfAbsent(key, resolver);
                if (previous != null) {
                    duplicates.add(key + " owners=" + previous.owner() + "," + owner);
                } else {
                    owners.put(key, owner);
                }
            }
        }
        if (!duplicates.isEmpty()) {
            log.error("runtime_config_resolver_registry_duplicate duplicateCount={} duplicates={}", duplicates.size(), duplicates);
            throw new IllegalStateException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_DUPLICATE " + String.join("; ", duplicates));
        }
        this.byKey = Collections.unmodifiableMap(index);
        this.ownerByKey = Collections.unmodifiableMap(owners);
        log.info("runtime_config_resolver_registry_initialized resolverCount={} keyCount={} owners={}",
                resolvers == null ? 0 : resolvers.size(), index.size(), new TreeSet<>(owners.values()));
    }

    public Object resolve(String key) {
        RuntimeConfigurationEffectiveValueResolver resolver = byKey.get(key);
        if (resolver == null) {
            log.warn("runtime_config_value_resolution_missing key={}", key);
            throw new IllegalArgumentException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_MISSING key=" + key);
        }
        try {
            Object resolved = resolver.resolve(key);
            log.debug("runtime_config_value_resolved key={} resolverOwner={} valueType={}", key, resolver.owner(),
                    resolved == null ? "null" : resolved.getClass().getSimpleName());
            return resolved;
        } catch (RuntimeException ex) {
            log.warn("runtime_config_value_resolution_failed key={} resolverOwner={} errorType={}",
                    key, resolver.owner(), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    public String ownerFor(String key) {
        String owner = ownerByKey.get(key);
        if (owner == null)
            throw new IllegalArgumentException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_MISSING key=" + key);
        return owner;
    }

    public Set<String> supportedKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(byKey.keySet()));
    }

    /** Whether this process owns a typed startup/runtime resolver for the key. */
    public boolean supports(String key) {
        return key != null && byKey.containsKey(key);
    }

    /**
     * Fail closed when a migration-authorized definition has neither a local typed resolver nor
     * a distributed Runtime Configuration snapshot path. This is the multi-process projection
     * contract: Core-owned settings resolve through their typed view; Gateway/Worker-owned
     * settings resolve from the authenticated local copy of their published snapshot instead of
     * creating illegal compile/runtime dependencies on those applications' startup properties.
     */
    public void requireProjectionCoverage(Set<String> migrationAuthorizedKeys, Set<String> snapshotBackedKeys) {
        Set<String> required = new TreeSet<>(migrationAuthorizedKeys == null ? Set.of() : migrationAuthorizedKeys);
        Set<String> snapshot = snapshotBackedKeys == null ? Set.of() : snapshotBackedKeys;
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(byKey.keySet());
        missing.removeAll(snapshot);
        if (!missing.isEmpty()) {
            log.error("runtime_config_projection_coverage_incomplete missingCount={} missingKeys={}", missing.size(), missing);
            throw new IllegalStateException("RUNTIME_CONFIG_EFFECTIVE_VALUE_PROJECTION_COVERAGE_INCOMPLETE missing=" + missing);
        }
        Set<String> local = new TreeSet<>(required);
        local.retainAll(byKey.keySet());
        Set<String> distributed = new TreeSet<>(required);
        distributed.removeAll(local);
        log.info("runtime_config_projection_coverage_verified authorizedKeyCount={} localResolverKeyCount={} distributedSnapshotKeyCount={}",
                required.size(), local.size(), distributed.size());
    }

    /** Fail closed when a caller explicitly requires local typed resolver ownership. */
    public void requireCoverage(Set<String> migrationAuthorizedKeys) {
        Set<String> missing = new TreeSet<>(migrationAuthorizedKeys == null ? Set.of() : migrationAuthorizedKeys);
        missing.removeAll(byKey.keySet());
        if (!missing.isEmpty()) {
            log.error("runtime_config_resolver_coverage_incomplete missingCount={} missingKeys={}", missing.size(), missing);
            throw new IllegalStateException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_COVERAGE_INCOMPLETE missing=" + missing);
        }
        log.info("runtime_config_resolver_coverage_verified authorizedKeyCount={} resolverKeyCount={}",
                migrationAuthorizedKeys == null ? 0 : migrationAuthorizedKeys.size(), byKey.size());
    }

    private static String requiredOwner(String owner) {
        if (owner == null || owner.isBlank())
            throw new IllegalStateException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_OWNER_REQUIRED");
        return owner.trim();
    }

    private static String requiredKey(String key, String owner) {
        if (key == null || key.isBlank())
            throw new IllegalStateException("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_KEY_REQUIRED owner=" + owner);
        return key.trim();
    }
}
