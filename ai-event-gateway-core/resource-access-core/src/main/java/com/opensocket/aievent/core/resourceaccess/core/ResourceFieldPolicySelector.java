package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.VisibilityFieldRule;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityPolicyRecord;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Selects the most specific field rule before applying administrative priority. */
public final class ResourceFieldPolicySelector {
    private ResourceFieldPolicySelector() {}

    public static Optional<VisibilityFieldRule> select(VisibilityPolicyRecord policy, String fieldPath) {
        Objects.requireNonNull(policy, "policy");
        String field = required(fieldPath, "fieldPath");
        return policy.fieldRules().stream()
                .filter(rule -> matches(rule.fieldPath(), field))
                .sorted(Comparator
                        .comparingInt((VisibilityFieldRule rule) -> specificity(rule.fieldPath(), field))
                        .reversed()
                        .thenComparingInt(VisibilityFieldRule::priority)
                        .thenComparing(VisibilityFieldRule::fieldRuleId))
                .findFirst();
    }

    static int specificity(String rulePath, String fieldPath) {
        if (rulePath.equals(fieldPath)) return 3;
        if (rulePath.endsWith(".*") && fieldPath.startsWith(rulePath.substring(0, rulePath.length() - 1))) return 2;
        return rulePath.equals("*") ? 1 : 0;
    }

    private static boolean matches(String rulePath, String fieldPath) {
        return specificity(rulePath, fieldPath) > 0;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
