package com.opensocket.aievent.core.agent.assignment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advisory-only recommendation application component.
 *
 * <p>It calculates recommendations from quality and capability evidence,
 * retains the current in-memory recommendation projection, and records
 * operator accept/reject intent. It never mutates Source Flow, Agent Pool,
 * Pool membership, runtime eligibility or selection strategy.</p>
 */
final class AgentAdvisoryRecommendationCoordinator {
    private final AgentAssignmentPersistence persistence;
    private final Map<String, AgentAdvisoryRecommendation> advisoryRecommendations = new ConcurrentHashMap<>();

    AgentAdvisoryRecommendationCoordinator(AgentAssignmentPersistence persistence) {
        this.persistence = persistence;
    }

    List<AgentAdvisoryRecommendation> search(String tenantId,
                                                                           String targetPoolId,
                                                                           String agentId,
                                                                           String status,
                                                                           String evidenceWindow,
                                                                           int limit) {
        String resolvedTenant = defaultTenant(tenantId);
        String resolvedStatus = normalizeOptional(status);
        String resolvedPool = trimOptional(targetPoolId);
        String resolvedAgent = trimOptional(agentId);
        String resolvedWindow = firstNonBlank(evidenceWindow, "24h");
        List<AgentAdvisoryRecommendation> generated = generate(resolvedTenant, resolvedPool, resolvedAgent, resolvedWindow, limit);
        List<AgentAdvisoryRecommendation> existing = advisoryRecommendations.values().stream()
                .filter(item -> resolvedTenant.equals(item.getTenantId()))
                .filter(item -> blank(resolvedPool) || resolvedPool.equals(item.getTargetPoolId()))
                .filter(item -> blank(resolvedAgent) || resolvedAgent.equals(item.getTargetAgentId()))
                .filter(item -> blank(resolvedStatus) || resolvedStatus.equals(item.getStatus()))
                .sorted(Comparator.comparing(AgentAdvisoryRecommendation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<String, AgentAdvisoryRecommendation> merged = new LinkedHashMap<>();
        for (AgentAdvisoryRecommendation item : existing) merged.put(item.getRecommendationId(), item);
        for (AgentAdvisoryRecommendation item : generated) merged.putIfAbsent(item.getRecommendationId(), item);
        return merged.values().stream()
                .filter(item -> blank(resolvedStatus) || resolvedStatus.equals(item.getStatus()))
                .limit(normalizeLimit(limit))
                .toList();
    }

    List<AgentAdvisoryRecommendation> generate(String tenantId,
                                                                             String targetPoolId,
                                                                             String agentId,
                                                                             String evidenceWindow,
                                                                             int limit) {
        String resolvedTenant = defaultTenant(tenantId);
        String resolvedPool = trimOptional(targetPoolId);
        String resolvedAgent = trimOptional(agentId);
        String resolvedWindow = firstNonBlank(evidenceWindow, "24h");
        List<AgentAdvisoryRecommendation> results = new ArrayList<>();
        List<SupplyProfileQualitySnapshot> snapshots = persistence.searchSupplyProfileQualitySnapshots(resolvedTenant, resolvedAgent, null, resolvedWindow, normalizeLimit(limit));
        for (SupplyProfileQualitySnapshot snapshot : snapshots) {
            String candidateAgentId = firstNonBlank(snapshot.getAgentId(), resolvedAgent);
            if (blank(candidateAgentId)) continue;
            BigDecimal score = firstNonNull(snapshot.getScore(), BigDecimal.ZERO);
            BigDecimal successRate = firstNonNull(snapshot.getSuccessRate(), BigDecimal.ZERO);
            BigDecimal timeoutRate = firstNonNull(snapshot.getTimeoutRate(), BigDecimal.ZERO);
            BigDecimal failureRate = firstNonNull(snapshot.getFailureRate(), BigDecimal.ZERO);
            if (isHighQuality(score, successRate)) {
                results.add(registerRecommendation(buildRecommendation(
                        resolvedTenant,
                        "ADJUST_AGENT_WEIGHT",
                        resolvedPool,
                        candidateAgentId,
                        null,
                        resolvedWindow,
                        confidenceFrom(score, successRate, BigDecimal.valueOf(0.72)),
                        "Agent quality observation is strong. Review whether member weight should be increased.",
                        Map.of("action", "REVIEW_WEIGHT_INCREASE", "agentId", candidateAgentId, "targetPoolId", nullToBlank(resolvedPool), "suggestedWeightDirection", "INCREASE"),
                        Map.of("score", score, "successRate", successRate, "sampleSize", snapshot.getSampleSize(), "qualityGrade", firstNonBlank(snapshot.getQualityGrade(), "UNKNOWN"))
                )));
                if (!blank(resolvedPool)) {
                    results.add(registerRecommendation(buildRecommendation(
                            resolvedTenant,
                            "ADD_AGENT_TO_POOL",
                            resolvedPool,
                            candidateAgentId,
                            null,
                            resolvedWindow,
                            confidenceFrom(score, successRate, BigDecimal.valueOf(0.68)),
                            "Agent has positive quality evidence. Review whether it should be added to the target Pool.",
                            Map.of("action", "REVIEW_ADD_POOL_MEMBER", "agentId", candidateAgentId, "targetPoolId", resolvedPool, "memberStatus", "ACTIVE"),
                            Map.of("score", score, "successRate", successRate, "sampleSize", snapshot.getSampleSize(), "observationOnly", true)
                    )));
                }
            }
            if (isCapacityRisk(timeoutRate, failureRate, snapshot.getRecentFailureCount())) {
                results.add(registerRecommendation(buildRecommendation(
                        resolvedTenant,
                        "POOL_CAPACITY_RISK",
                        resolvedPool,
                        candidateAgentId,
                        null,
                        resolvedWindow,
                        BigDecimal.valueOf(0.78),
                        "Quality observation shows timeout/failure pressure. Review Pool capacity or add more eligible Agents.",
                        Map.of("action", "REVIEW_POOL_CAPACITY", "targetPoolId", nullToBlank(resolvedPool), "agentId", candidateAgentId, "capacityRisk", true),
                        Map.of("timeoutRate", timeoutRate, "failureRate", failureRate, "recentFailureCount", snapshot.getRecentFailureCount(), "sampleSize", snapshot.getSampleSize())
                )));
                if (!blank(resolvedPool)) {
                    results.add(registerRecommendation(buildRecommendation(
                            resolvedTenant,
                            "INCREASE_POOL_CAPACITY",
                            resolvedPool,
                            candidateAgentId,
                            null,
                            resolvedWindow,
                            BigDecimal.valueOf(0.70),
                            "Pool-level capacity pressure is visible in quality observation. Review whether Pool capacity should be increased.",
                            Map.of("action", "REVIEW_CAPACITY_INCREASE", "targetPoolId", resolvedPool, "agentId", candidateAgentId, "requiresCapacityReview", true),
                            Map.of("timeoutRate", timeoutRate, "failureRate", failureRate, "recentFailureCount", snapshot.getRecentFailureCount(), "sampleSize", snapshot.getSampleSize())
                    )));
                }
            }
        }
        if (!blank(resolvedAgent)) {
            List<AgentCapabilityAssignment> capabilities = persistence.findAgentCapabilityAssignmentsByAgent(resolvedAgent).stream()
                    .filter(item -> "APPROVED".equalsIgnoreCase(String.valueOf(item.getStatus())))
                    .toList();
            for (AgentCapabilityAssignment capability : capabilities) {
                if (!blank(resolvedPool)) {
                    results.add(registerRecommendation(buildRecommendation(
                            resolvedTenant,
                            "ADD_AGENT_TO_POOL",
                            resolvedPool,
                            resolvedAgent,
                            firstNonBlank(normalizeCode(capability.getCapabilityCode()), "UNKNOWN"),
                            resolvedWindow,
                            BigDecimal.valueOf(0.55),
                            "Agent has approved Capability Registry evidence. Review as a Pool candidate; this does not create a routing gate.",
                            Map.of("action", "REVIEW_ADD_POOL_MEMBER", "agentId", resolvedAgent, "targetPoolId", resolvedPool, "capabilityCode", firstNonBlank(normalizeCode(capability.getCapabilityCode()), "UNKNOWN")),
                            Map.of("capabilityCode", firstNonBlank(normalizeCode(capability.getCapabilityCode()), "UNKNOWN"), "capabilityStatus", String.valueOf(capability.getStatus()), "capabilityReferenceOnly", true)
                    )));
                }
            }
        }
        return results.stream().limit(normalizeLimit(limit)).toList();
    }

    AgentAdvisoryRecommendation accept(String tenantId, String recommendationId, AgentAdvisoryRecommendationDecisionCommand command) {
        return decideAdvisoryRecommendation(tenantId, recommendationId, command, true);
    }

    AgentAdvisoryRecommendation reject(String tenantId, String recommendationId, AgentAdvisoryRecommendationDecisionCommand command) {
        return decideAdvisoryRecommendation(tenantId, recommendationId, command, false);
    }

    private AgentAdvisoryRecommendation decideAdvisoryRecommendation(String tenantId,
                                                                     String recommendationId,
                                                                     AgentAdvisoryRecommendationDecisionCommand command,
                                                                     boolean accepted) {
        if (blank(recommendationId)) throw new IllegalArgumentException("recommendationId is required");
        AgentAdvisoryRecommendation recommendation = advisoryRecommendations.get(recommendationId);
        if (recommendation == null || !defaultTenant(tenantId).equals(recommendation.getTenantId())) {
            throw new IllegalArgumentException("Advisory recommendation not found: " + recommendationId);
        }
        AgentAdvisoryRecommendationDecisionCommand body = command == null ? new AgentAdvisoryRecommendationDecisionCommand() : command;
        OffsetDateTime now = now();
        Map<String, Object> audit = new LinkedHashMap<>(recommendation.getDecisionAudit());
        audit.put("operatorId", firstNonBlank(body.getOperatorId(), "operator"));
        audit.put("reason", firstNonBlank(body.getReason(), accepted ? "Accepted advisory recommendation for configuration review." : "Rejected advisory recommendation."));
        audit.put("decision", accepted ? "ACCEPTED" : "REJECTED");
        audit.put("decidedAt", now.toString());
        audit.put("advisoryOnly", true);
        audit.put("autoApply", false);
        audit.put("routingImpact", "NONE");
        audit.put("metadata", body.getMetadata());
        recommendation.setDecisionAudit(audit);
        recommendation.setDecisionReason(String.valueOf(audit.get("reason")));
        recommendation.setUpdatedAt(now);
        if (accepted) {
            recommendation.setStatus("ACCEPTED");
            recommendation.setAcceptedBy(String.valueOf(audit.get("operatorId")));
            recommendation.setAcceptedAt(now);
            Map<String, Object> suggestedChange = new LinkedHashMap<>(recommendation.getSuggestedChange());
            suggestedChange.put("configurationIntent", "REVIEW_REQUIRED");
            suggestedChange.put("requiresExplicitAdminApply", true);
            recommendation.setSuggestedChange(suggestedChange);
        } else {
            recommendation.setStatus("REJECTED");
            recommendation.setRejectedBy(String.valueOf(audit.get("operatorId")));
            recommendation.setRejectedAt(now);
        }
        advisoryRecommendations.put(recommendation.getRecommendationId(), recommendation);
        return recommendation;
    }

    private AgentAdvisoryRecommendation buildRecommendation(String tenantId,
                                                            String type,
                                                            String targetPoolId,
                                                            String targetAgentId,
                                                            String capabilityCode,
                                                            String evidenceWindow,
                                                            BigDecimal confidence,
                                                            String reason,
                                                            Map<String, Object> suggestedChange,
                                                            Map<String, Object> evidence) {
        OffsetDateTime now = now();
        AgentAdvisoryRecommendation recommendation = new AgentAdvisoryRecommendation();
        recommendation.setTenantId(defaultTenant(tenantId));
        recommendation.setRecommendationType(type);
        recommendation.setRecommendationId(recommendationId(defaultTenant(tenantId), type, targetPoolId, targetAgentId, capabilityCode, evidenceWindow));
        recommendation.setTargetPoolId(trimOptional(targetPoolId));
        recommendation.setTargetAgentId(trimOptional(targetAgentId));
        recommendation.setCapabilityCode(trimOptional(capabilityCode));
        recommendation.setEvidenceWindow(firstNonBlank(evidenceWindow, "24h"));
        recommendation.setConfidence(confidence == null ? BigDecimal.ZERO : confidence);
        recommendation.setReason(reason);
        recommendation.setSuggestedChange(suggestedChange);
        recommendation.setEvidence(evidence);
        recommendation.setAdvisoryOnly(true);
        recommendation.setAutoApply(false);
        recommendation.setRoutingImpact("NONE");
        recommendation.setCreatedBy("ADVISORY_RECOMMENDATION_ENGINE");
        recommendation.setCreatedAt(now);
        recommendation.setUpdatedAt(now);
        return recommendation;
    }

    private AgentAdvisoryRecommendation registerRecommendation(AgentAdvisoryRecommendation candidate) {
        AgentAdvisoryRecommendation existing = advisoryRecommendations.get(candidate.getRecommendationId());
        if (existing != null && !"OPEN".equals(existing.getStatus())) return existing;
        advisoryRecommendations.put(candidate.getRecommendationId(), candidate);
        return candidate;
    }

    private String recommendationId(String tenantId, String type, String targetPoolId, String targetAgentId, String capabilityCode, String evidenceWindow) {
        String raw = String.join("|", defaultTenant(tenantId), firstNonBlank(type, "UNKNOWN"), nullToBlank(targetPoolId), nullToBlank(targetAgentId), nullToBlank(capabilityCode), firstNonBlank(evidenceWindow, "24h"));
        return "advisory-" + Integer.toHexString(raw.hashCode());
    }

    private boolean isHighQuality(BigDecimal score, BigDecimal successRate) {
        return score.compareTo(BigDecimal.valueOf(80)) >= 0 || successRate.compareTo(BigDecimal.valueOf(0.95)) >= 0 || successRate.compareTo(BigDecimal.valueOf(95)) >= 0;
    }

    private boolean isCapacityRisk(BigDecimal timeoutRate, BigDecimal failureRate, int recentFailureCount) {
        return normalizeRate(timeoutRate).compareTo(BigDecimal.valueOf(0.15)) >= 0
                || normalizeRate(failureRate).compareTo(BigDecimal.valueOf(0.20)) >= 0
                || recentFailureCount >= 5;
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.compareTo(BigDecimal.ONE) > 0 ? value.divide(BigDecimal.valueOf(100)) : value;
    }

    private BigDecimal confidenceFrom(BigDecimal score, BigDecimal successRate, BigDecimal fallback) {
        BigDecimal scoreConfidence = score == null ? BigDecimal.ZERO : score.divide(BigDecimal.valueOf(100));
        BigDecimal successConfidence = normalizeRate(successRate);
        BigDecimal max = scoreConfidence.max(successConfidence).max(fallback == null ? BigDecimal.ZERO : fallback);
        return max.min(BigDecimal.valueOf(0.95));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
    private String normalizeOptional(String value) { return blank(value) ? null : value.trim().toUpperCase(); }
    private String trimOptional(String value) { return blank(value) ? null : value.trim(); }
    private String normalizeCode(String value) { return blank(value) ? null : value.trim().toUpperCase(); }
    private int normalizeLimit(int limit) { return limit <= 0 ? 500 : Math.min(limit, 5000); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private String defaultTenant(String tenantId) {
        if (blank(tenantId)) throw new IllegalArgumentException("tenantId is required");
        return tenantId.trim();
    }
    private <T> T firstNonNull(T value, T fallback) { return value == null ? fallback : value; }
    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
