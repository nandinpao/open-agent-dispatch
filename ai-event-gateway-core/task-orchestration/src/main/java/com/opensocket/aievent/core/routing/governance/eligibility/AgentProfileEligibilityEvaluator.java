package com.opensocket.aievent.core.routing.governance.eligibility;

import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.agent.governance.AgentCredentialStatus;
import com.opensocket.aievent.core.agent.governance.AgentProfile;

/** Direct-dispatch Agent profile, approval, risk, and credential check. */
@Component
public class AgentProfileEligibilityEvaluator implements DispatchEligibilityShadowEvaluator {
    public static final String CODE = "AGENT_PROFILE";

    @Override public String code() { return CODE; }

    @Override
    public AgentEligibilityShadowCheck evaluate(DispatchEligibilityShadowContext context) {
        AgentProfile profile = context == null ? null : context.getAgentProfile();
        if (profile == null) return block("AGENT_PROFILE_NOT_FOUND", "Agent profile is missing.");
        if (context.getRequirement() != null && profile.getTenantId() != null
                && !tenantKey(context.getRequirement().getTenantId()).equals(tenantKey(profile.getTenantId()))) {
            return block("AGENT_TENANT_MISMATCH", "Agent profile belongs to another Tenant.")
                    .withDetail("agentTenantId", profile.getTenantId());
        }
        if (profile.getApprovalStatus() == null
                || !profile.getApprovalStatus().isApproved()
                || !profile.isEnabled()) {
            return block("AGENT_NOT_APPROVED", "Agent is not APPROVED and enabled.")
                    .withDetail("approvalStatus", profile.getApprovalStatus())
                    .withDetail("enabled", profile.isEnabled());
        }
        if (profile.getRiskStatus() == null || !profile.getRiskStatus().allowsAssignment()) {
            return block("AGENT_RISK_STATUS_BLOCKED", "Agent risk status does not allow assignment.")
                    .withDetail("riskStatus", profile.getRiskStatus());
        }
        if (profile.getCredential() == null || profile.getCredential().getCredentialStatus() != AgentCredentialStatus.ACTIVE) {
            return block("AGENT_CREDENTIAL_NOT_ACTIVE", "Agent credential is missing or not ACTIVE.");
        }
        OffsetDateTime now = context.getEvaluatedAt() == null ? OffsetDateTime.now() : context.getEvaluatedAt();
        if (profile.getCredential().getExpiresAt() != null && !profile.getCredential().getExpiresAt().isAfter(now)) {
            return block("AGENT_CREDENTIAL_EXPIRED", "Agent credential has expired.");
        }
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.PASS,
                "AGENT_PROFILE_APPROVED", "Agent profile, risk, and credential checks passed.");
    }

    private AgentEligibilityShadowCheck block(String reason, String message) {
        return AgentEligibilityShadowCheck.of(CODE, EligibilityShadowCheckOutcome.BLOCK, reason, message);
    }

    private static String tenantKey(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
