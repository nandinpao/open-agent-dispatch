package com.opensocket.aievent.core.security;

import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable HTTP route to Atomic Permission mapping generated for R3. */
public final class R3HttpPermissionRule {
    public static final String CONTROLLER_SCOPED = "@CONTROLLER";
    private final String method;
    private final String routeTemplate;
    private final String permission;
    private final ScopeType scopeType;
    private final String scopeVariable;
    private final String resourceType;
    private final String resourceVariable;
    private final boolean highRisk;
    private final Set<String> legacyRoles;
    private final Set<String> internalRoles;
    private final List<Segment> segments;
    private final int specificity;

    public R3HttpPermissionRule(
            String method,
            String routeTemplate,
            String permission,
            ScopeType scopeType,
            String scopeVariable,
            String resourceType,
            String resourceVariable,
            boolean highRisk,
            Set<String> legacyRoles,
            Set<String> internalRoles) {
        this.method = required(method, "method").toUpperCase(Locale.ROOT);
        this.routeTemplate = normalizePath(routeTemplate);
        this.permission = required(permission, "permission");
        this.scopeType = Objects.requireNonNull(scopeType, "scopeType");
        this.scopeVariable = normalize(scopeVariable);
        this.resourceType = required(resourceType, "resourceType");
        this.resourceVariable = normalize(resourceVariable);
        this.highRisk = highRisk;
        this.legacyRoles = normalizedSet(legacyRoles);
        this.internalRoles = normalizedSet(internalRoles);
        this.segments = parseTemplate(this.routeTemplate);
        this.specificity = calculateSpecificity(this.segments);
        if ((scopeType == ScopeType.DEPARTMENT || scopeType == ScopeType.DEPARTMENT_SUBTREE || scopeType == ScopeType.GROUP)
                && this.scopeVariable.isBlank()) {
            throw new IllegalArgumentException("scopeVariable is required for " + scopeType);
        }
        if (CONTROLLER_SCOPED.equals(this.scopeVariable) && scopeType != ScopeType.TENANT) {
            throw new IllegalArgumentException("controller-scoped admission must use TENANT route projection");
        }
    }

    public String method() { return method; }
    public String routeTemplate() { return routeTemplate; }
    public String permission() { return permission; }
    public ScopeType scopeType() { return scopeType; }
    public boolean controllerScoped() { return CONTROLLER_SCOPED.equals(scopeVariable); }
    public String resourceType() { return resourceType; }
    public boolean highRisk() { return highRisk; }
    public Set<String> legacyRoles() { return legacyRoles; }
    public Set<String> internalRoles() { return internalRoles; }
    public int specificity() { return specificity; }

    public Optional<Resolved> resolve(String requestMethod, String requestPath) {
        String candidateMethod = normalize(requestMethod).toUpperCase(Locale.ROOT);
        if (!method.equals("ANY") && !method.equals(candidateMethod)) return Optional.empty();
        List<String> actual = splitPath(normalizePath(requestPath));
        if (actual.size() != segments.size()) return Optional.empty();
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment expected = segments.get(i);
            String value = actual.get(i);
            if (expected.variable()) {
                if (value.isBlank()) return Optional.empty();
                variables.put(expected.value(), value);
            } else if (!expected.value().equals(value)) {
                return Optional.empty();
            }
        }
        String scopeId = switch (scopeType) {
            case INSTANCE -> "INSTANCE";
            case TENANT -> "";
            case DEPARTMENT, DEPARTMENT_SUBTREE, GROUP -> variables.getOrDefault(scopeVariable, "");
        };
        if ((scopeType == ScopeType.DEPARTMENT || scopeType == ScopeType.DEPARTMENT_SUBTREE || scopeType == ScopeType.GROUP) && scopeId.isBlank()) {
            return Optional.empty();
        }
        String resourceId = resourceVariable.isBlank()
                ? routeTemplate
                : variables.getOrDefault(resourceVariable, routeTemplate);
        return Optional.of(new Resolved(this, Map.copyOf(variables), scopeId, resourceId));
    }

    private static List<Segment> parseTemplate(String template) {
        List<Segment> parsed = new ArrayList<>();
        for (String raw : splitPath(template)) {
            if (raw.startsWith("{") && raw.endsWith("}")) {
                String name = raw.substring(1, raw.length() - 1);
                if (name.startsWith("*")) name = name.substring(1);
                int colon = name.indexOf(':');
                if (colon >= 0) name = name.substring(0, colon);
                parsed.add(new Segment(true, required(name, "path variable")));
            } else {
                parsed.add(new Segment(false, raw));
            }
        }
        return List.copyOf(parsed);
    }

    private static int calculateSpecificity(List<Segment> values) {
        int score = 0;
        for (Segment segment : values) score += segment.variable() ? 1 : 10;
        return score;
    }

    private static List<String> splitPath(String path) {
        if (path.equals("/")) return List.of();
        String value = path.startsWith("/") ? path.substring(1) : path;
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isBlank()) return List.of();
        return List.of(value.split("/", -1));
    }

    private static String normalizePath(String value) {
        String path = required(value, "routeTemplate");
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (!path.startsWith("/")) path = "/" + path;
        while (path.contains("//")) path = path.replace("//", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static Set<String> normalizedSet(Set<String> source) {
        if (source == null || source.isEmpty()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String value : source) {
            String normalized = normalize(value).toUpperCase(Locale.ROOT);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return Collections.unmodifiableSet(result);
    }

    private static String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record Segment(boolean variable, String value) { }

    public record Resolved(
            R3HttpPermissionRule rule,
            Map<String, String> pathVariables,
            String scopeId,
            String resourceId) { }
}
