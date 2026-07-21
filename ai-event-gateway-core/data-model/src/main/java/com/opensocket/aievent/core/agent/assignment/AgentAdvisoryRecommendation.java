package com.opensocket.aievent.core.agent.assignment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Phase 9C Advisory Recommendation.
 *
 * This model is intentionally advisory-only. It records suggestion evidence and
 * operator decisions, but accepting a recommendation must not directly mutate
 * Source Flow, Agent Pool, Pool Member, Runtime Eligibility, or Selection Strategy state.
 */
@Getter
@Setter
public class AgentAdvisoryRecommendation {
    private String tenantId;
    private String recommendationId;
    private String recommendationType;
    private String status = "OPEN";
    private String targetPoolId;
    private String targetAgentId;
    private String capabilityCode;
    private String evidenceWindow = "24h";
    private BigDecimal confidence = BigDecimal.ZERO;
    private String reason;
    private Map<String, Object> suggestedChange = new LinkedHashMap<>();
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private Map<String, Object> decisionAudit = new LinkedHashMap<>();
    private Boolean advisoryOnly = Boolean.TRUE;
    private Boolean autoApply = Boolean.FALSE;
    private String routingImpact = "NONE";
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String acceptedBy;
    private OffsetDateTime acceptedAt;
    private String rejectedBy;
    private OffsetDateTime rejectedAt;
    private String decisionReason;

    public void setSuggestedChange(Map<String, Object> suggestedChange) {
        this.suggestedChange = suggestedChange == null ? new LinkedHashMap<>() : new LinkedHashMap<>(suggestedChange);
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
    }

    public void setDecisionAudit(Map<String, Object> decisionAudit) {
        this.decisionAudit = decisionAudit == null ? new LinkedHashMap<>() : new LinkedHashMap<>(decisionAudit);
    }
}
