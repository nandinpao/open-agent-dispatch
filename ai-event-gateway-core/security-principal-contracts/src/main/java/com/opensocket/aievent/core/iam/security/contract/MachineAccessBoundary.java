package com.opensocket.aievent.core.iam.security.contract;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Credential-level upper bounds for a machine caller. These values are not an authorization
 * decision: RBAC/resource authorization must still evaluate the request. Empty collections are
 * fail-closed and grant nothing.
 */
public record MachineAccessBoundary(
        Set<String> permissionBounds,
        Set<String> scopes,
        Set<String> audiences,
        Set<String> sourceSystems,
        Set<String> apiPrefixes,
        Set<String> cidrs,
        Map<String, String> resourceRestrictions
) {
    public MachineAccessBoundary {
        permissionBounds = clean(permissionBounds, "permissionBound");
        scopes = clean(scopes, "scope");
        audiences = clean(audiences, "audience");
        sourceSystems = clean(sourceSystems, "sourceSystem");
        apiPrefixes = cleanApiPrefixes(apiPrefixes);
        cidrs = clean(cidrs, "cidr");
        resourceRestrictions = cleanMap(resourceRestrictions);
    }

    public static MachineAccessBoundary denyAll() {
        return new MachineAccessBoundary(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
    }

    public boolean permitsPermissionBound(String permission) {
        return permission != null && permissionBounds.contains(permission.trim());
    }

    public boolean permitsScope(String scope) {
        return scope != null && scopes.contains(scope.trim());
    }

    public boolean permitsAudience(String audience) {
        return audience != null && audiences.contains(audience.trim());
    }

    public boolean permitsSourceSystem(String sourceSystem) {
        return sourceSystem != null && sourceSystems.contains(sourceSystem.trim());
    }

    public boolean permitsApiPath(String apiPath) {
        if (apiPath == null || apiPath.isBlank()) return false;
        String normalized = apiPath.trim();
        return apiPrefixes.stream().anyMatch(normalized::startsWith);
    }

    private static Set<String> clean(Collection<String> values, String field) {
        if (values == null || values.isEmpty()) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
            out.add(value.trim());
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> cleanApiPrefixes(Collection<String> values) {
        Set<String> out = clean(values, "apiPrefix");
        for (String prefix : out) {
            if (!prefix.startsWith("/")) throw new IllegalArgumentException("apiPrefix must start with /");
        }
        return out;
    }

    private static Map<String, String> cleanMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        TreeMap<String, String> out = new TreeMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("resource restriction key must not be blank");
            if (value == null || value.isBlank()) throw new IllegalArgumentException("resource restriction value must not be blank");
            out.put(key.trim(), value.trim());
        });
        return Collections.unmodifiableMap(out);
    }
}
