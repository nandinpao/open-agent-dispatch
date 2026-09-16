package com.opensocket.aievent.core.security;

import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fail-closed registry for every Human Admin HTTP entry point. */
public final class R3HumanApiPermissionRegistry {
    public static final String RESOURCE = "iam/r3-human-api-authorization-policy.tsv";
    private final List<R3HttpPermissionRule> rules;

    public R3HumanApiPermissionRegistry() {
        this(Thread.currentThread().getContextClassLoader());
    }

    R3HumanApiPermissionRegistry(ClassLoader classLoader) {
        this.rules = load(classLoader == null ? R3HumanApiPermissionRegistry.class.getClassLoader() : classLoader);
        if (rules.isEmpty()) throw new IllegalStateException("R3_AUTHORIZATION_POLICY_EMPTY");
    }

    public Optional<R3HttpPermissionRule.Resolved> resolve(String method, String path) {
        String canonicalPath = canonicalRoutePath(path);
        for (R3HttpPermissionRule rule : rules) {
            Optional<R3HttpPermissionRule.Resolved> resolved = rule.resolve(method, canonicalPath);
            if (resolved.isPresent()) return resolved;
        }
        return Optional.empty();
    }

    /**
     * Servlet/proxy layers are allowed to percent-encode ':' inside a path segment.
     * The generated R3 policy inventory stores operation suffixes such as ':batch'
     * in canonical decoded form, so normalize only that safe pchar here. Do not
     * generically URL-decode '/' or other structural delimiters.
     */
    static String canonicalRoutePath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.replaceAll("(?i)%3a", ":");
    }

    public int size() { return rules.size(); }
    public Set<String> permissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        rules.forEach(rule -> codes.add(rule.permission()));
        return Set.copyOf(codes);
    }

    private static List<R3HttpPermissionRule> load(ClassLoader classLoader) {
        InputStream input = classLoader.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("R3_AUTHORIZATION_POLICY_MISSING: " + RESOURCE);
        List<R3HttpPermissionRule> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 || line.isBlank() || line.startsWith("#")) continue;
                String[] values = line.split("\\t", -1);
                if (values.length != 10) {
                    throw new IllegalStateException("R3_AUTHORIZATION_POLICY_INVALID line=" + lineNumber);
                }
                loaded.add(new R3HttpPermissionRule(
                        values[0], values[1], values[2], ScopeType.valueOf(values[3]), values[4],
                        values[5], values[6], Boolean.parseBoolean(values[7]), csv(values[8]), csv(values[9])));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("R3_AUTHORIZATION_POLICY_READ_FAILED", ex);
        }
        loaded.sort(Comparator.comparingInt(R3HttpPermissionRule::specificity).reversed()
                .thenComparing(R3HttpPermissionRule::routeTemplate)
                .thenComparing(R3HttpPermissionRule::method));
        return List.copyOf(loaded);
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .forEach(result::add);
        return Set.copyOf(result);
    }
}
