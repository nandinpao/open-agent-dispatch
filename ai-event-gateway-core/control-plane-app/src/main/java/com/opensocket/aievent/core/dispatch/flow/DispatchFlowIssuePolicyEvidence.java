package com.opensocket.aievent.core.dispatch.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evidence-only snapshot of Issue policy mutation input before normalization mutates the aggregate.
 *
 * <p>No business decision is made here. The class exists specifically to distinguish an omitted
 * JSON field from the model's historical OPTIONAL initializer and to retain Rule field-presence
 * before normalization converts blank values into inheritance.</p>
 */
final class DispatchFlowIssuePolicyEvidence {
    static final String ABSENT = "<ABSENT>";
    static final String NULL = "<NULL>";

    private DispatchFlowIssuePolicyEvidence() {
    }

    static Snapshot capture(DispatchFlowView request) {
        if (request == null) {
            return new Snapshot(false, null, "UNKNOWN", List.of());
        }
        List<RuleInput> rules = new ArrayList<>();
        List<DispatchFlowRuleView> sourceRules = request.getRules() == null ? List.of() : request.getRules();
        for (int index = 0; index < sourceRules.size(); index++) {
            DispatchFlowRuleView rule = sourceRules.get(index);
            rules.add(new RuleInput(
                    index,
                    rule == null ? null : rule.getRuleId(),
                    rule == null ? null : rule.getRuleCode(),
                    rule != null && rule.issueSyncPolicyWasProvided(),
                    rule == null ? null : rule.getIssueSyncPolicy()));
        }
        return new Snapshot(
                request.defaultIssueSyncPolicyWasProvided(),
                request.getDefaultIssueSyncPolicy(),
                mutationSource(request),
                List.copyOf(rules));
    }

    static String mutationSource(DispatchFlowView request) {
        if (request == null) return "UNKNOWN";
        String explicit = normalizeSource(request.evidenceMutationSource());
        if (explicit != null) return explicit;
        Map<String, Object> metadata = request.getMetadata();
        if (metadata != null) {
            if (Boolean.TRUE.equals(metadata.get("enterpriseA2aDemo"))) return "A2A_ENTERPRISE_BOOTSTRAP_INFERRED";
            if (metadata.get("adminUiEditor") != null) return "ADMIN_UI_INFERRED";
            if (metadata.get("certificationGate") != null || metadata.get("gate") != null) return "RUNTIME_CERTIFICATION_INFERRED";
        }
        return "API_OR_INTERNAL_UNSPECIFIED";
    }

    static String display(boolean provided, String value) {
        if (!provided) return ABSENT;
        return value == null ? NULL : value;
    }

    private static String normalizeSource(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-]", "_");
    }

    record Snapshot(
            boolean flowPolicyProvided,
            String flowPolicyValue,
            String mutationSource,
            List<RuleInput> rules) {
    }

    record RuleInput(
            int index,
            String ruleId,
            String ruleCode,
            boolean policyProvided,
            String policyValue) {
    }
}
