package com.opensocket.aievent.core.routing.governance.eligibility;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;

/**
 * V38 canonical Capability eligibility gate.
 *
 * <p>Flow membership determines where to look. Task requiredCapabilities determines who is
 * qualified inside that candidate set. Only active Core-approved Agent Capability assignments
 * satisfy the requirement. Runtime readiness and capacity are evaluated by their own gates.</p>
 */
@Component
public class CapabilityEligibilityEvaluator implements DispatchEligibilityShadowEvaluator {
    public static final String CODE = "CAPABILITY";

    @Override public String code() { return CODE; }

    @Override
    public AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context) {
        if (context == null || context.getRequirement() == null) {
            return block("REQUIREMENT_EVIDENCE_MISSING", "Requirement evidence is unavailable.");
        }
        Set<String> required = normalized(context.getRequirement().getRequiredCapabilities());
        if (required.isEmpty()) {
            return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.NOT_APPLICABLE,
                    "NO_REQUIRED_CAPABILITY",
                    "The Task has no required Capability; Flow membership and the remaining runtime gates determine eligibility.")
                    .withDetail("capabilityAuthority", "TASK_REQUIRED_CAPABILITY");
        }

        OffsetDateTime now = context.getEvaluatedAt() == null ? OffsetDateTime.now() : context.getEvaluatedAt();
        Set<String> approved = new LinkedHashSet<>();
        for (AgentCapabilityAssignment assignment : context.getCapabilityAssignments()) {
            if (assignment == null || assignment.getStatus() != AgentCapabilityAssignmentStatus.APPROVED) continue;
            if (assignment.getExpiresAt() != null && !assignment.getExpiresAt().isAfter(now)) continue;
            if (assignment.getTenantId() != null && context.getRequirement().getTenantId() != null
                    && !tenantKey(context.getRequirement().getTenantId()).equals(tenantKey(assignment.getTenantId()))) continue;
            approved.add(normalize(assignment.getCapabilityCode()));
        }

        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(approved);
        if (!missing.isEmpty()) {
            return block("REQUIRED_CAPABILITY_NOT_APPROVED",
                    "Agent does not have every Core-approved Capability required by this Task.")
                    .withDetail("matchMode", "ALL")
                    .withDetail("requiredCapabilities", required)
                    .withDetail("approvedCapabilities", approved)
                    .withDetail("missingCapabilities", missing)
                    .withDetail("capabilityAuthority", "TASK_REQUIRED_CAPABILITY");
        }
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.PASS,
                "REQUIRED_CAPABILITY_APPROVED",
                "The Agent has every Core-approved Capability required by this Task.")
                .withDetail("matchMode", "ALL")
                .withDetail("matchedCapabilities", required)
                .withDetail("capabilityAuthority", "TASK_REQUIRED_CAPABILITY");
    }

    private AgentEligibilityShadowCheck block(String reason, String message) {
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.BLOCK, reason, message);
    }

    private static Set<String> normalized(Iterable<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) for (String value : values) if (!normalize(value).isBlank()) result.add(normalize(value));
        return result;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static String tenantKey(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
