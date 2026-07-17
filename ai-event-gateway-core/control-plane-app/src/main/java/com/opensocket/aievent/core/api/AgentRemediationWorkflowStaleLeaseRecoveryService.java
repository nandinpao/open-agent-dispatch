package com.opensocket.aievent.core.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.observability.AgentRemediationWorkflowMetricsService;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowHistoryRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowRecord;

/**
 * P11 active stale execution-lease recovery for Agent remediation workflows.
 *
 * <p>P10 allows stale lease takeover on the next execute call. P11 adds an active reaper that clears expired
 * workflow-level execution leases and appends recovery history so HA control-plane deployments do not depend on
 * a future operator action to make a stuck workflow visible and retryable.</p>
 */
@Service
public class AgentRemediationWorkflowStaleLeaseRecoveryService {
    public static final String STALE_LEASE_RECOVERED_EVENT = "EXECUTION_LEASE_STALE_RECOVERED";
    public static final String STALE_LEASE_RECOVERY_RACE_EVENT = "EXECUTION_LEASE_STALE_RECOVERY_RACE";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentRemediationWorkflowStore remediationWorkflowDao;
    private final AgentRemediationWorkflowMetricsService remediationWorkflowMetrics;
    private final ObjectMapper objectMapper;

    public AgentRemediationWorkflowStaleLeaseRecoveryService(AgentRemediationWorkflowStore remediationWorkflowDao,
                                                             AgentRemediationWorkflowMetricsService remediationWorkflowMetrics,
                                                             ObjectMapper objectMapper) {
        this.remediationWorkflowDao = remediationWorkflowDao;
        this.remediationWorkflowMetrics = remediationWorkflowMetrics;
        this.objectMapper = objectMapper;
    }

    public List<StaleWorkflowExecutionLeaseView> listStaleLeases(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return remediationWorkflowDao.findExpiredWorkflowExecutionLeases(now, clampLimit(limit)).stream()
                .map(po -> toStaleLeaseView(po, now))
                .toList();
    }

    public List<RecoveredWorkflowExecutionLeaseView> listRecentRecoveredLeases(int limit) {
        return remediationWorkflowDao.findHistoryByEventType(STALE_LEASE_RECOVERED_EVENT, clampLimit(limit)).stream()
                .map(this::toRecoveredLeaseView)
                .toList();
    }

    @Transactional
    public StaleLeaseRecoveryRun recoverExpiredLeases(int limit, String operatorId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String safeOperatorId = firstNonBlank(operatorId, "p11-stale-lease-reaper");
        String safeReason = firstNonBlank(reason, "P11 active stale workflow execution lease recovery.");
        List<AgentRemediationWorkflowRecord> candidates = remediationWorkflowDao.findExpiredWorkflowExecutionLeases(now, clampLimit(limit));
        List<StaleWorkflowExecutionLeaseView> recovered = new ArrayList<>();
        List<StaleWorkflowExecutionLeaseView> races = new ArrayList<>();
        for (AgentRemediationWorkflowRecord candidate : candidates) {
            String leaseOwner = candidate.getExecutionLeaseOwner();
            if (leaseOwner == null || leaseOwner.isBlank()) {
                continue;
            }
            int cleared = remediationWorkflowDao.clearExpiredWorkflowExecutionLeaseForOwner(
                    candidate.getWorkflowId(), leaseOwner, now);
            StaleWorkflowExecutionLeaseView view = toStaleLeaseView(candidate, now);
            if (cleared == 1) {
                remediationWorkflowDao.insertHistory(history(candidate, STALE_LEASE_RECOVERED_EVENT,
                        safeOperatorId,
                        safeReason,
                        staleLeaseMetadata(candidate, now, "RECOVERED")));
                recovered.add(view);
            } else {
                remediationWorkflowDao.insertHistory(history(candidate, STALE_LEASE_RECOVERY_RACE_EVENT,
                        safeOperatorId,
                        safeReason,
                        staleLeaseMetadata(candidate, now, "RACE_LOST")));
                races.add(view);
            }
        }
        boolean scheduled = safeOperatorId.toLowerCase(java.util.Locale.ROOT).contains("reaper")
                || safeOperatorId.toLowerCase(java.util.Locale.ROOT).contains("scheduler");
        remediationWorkflowMetrics.recordStaleLeaseRecoveryRun(candidates.size(), recovered.size(), races.size(), scheduled);
        return new StaleLeaseRecoveryRun(now, candidates.size(), recovered.size(), races.size(), recovered, races);
    }

    private StaleWorkflowExecutionLeaseView toStaleLeaseView(AgentRemediationWorkflowRecord po, OffsetDateTime now) {
        Long expiredSeconds = null;
        if (po.getExecutionLeaseExpiresAt() != null) {
            expiredSeconds = Math.max(0, Duration.between(po.getExecutionLeaseExpiresAt(), now).toSeconds());
        }
        return new StaleWorkflowExecutionLeaseView(
                po.getWorkflowId(),
                po.getAgentId(),
                po.getStatus(),
                po.getSeverity(),
                po.getExecutionLeaseOwner(),
                po.getExecutionLeaseAcquiredAt(),
                po.getExecutionLeaseExpiresAt(),
                expiredSeconds,
                po.getUpdatedAt());
    }

    private RecoveredWorkflowExecutionLeaseView toRecoveredLeaseView(AgentRemediationWorkflowHistoryRecord po) {
        return new RecoveredWorkflowExecutionLeaseView(
                po.getWorkflowId(),
                po.getAgentId(),
                po.getOperatorId(),
                po.getReason(),
                readMetadata(po.getMetadataJson()),
                po.getOccurredAt());
    }

    private AgentRemediationWorkflowHistoryRecord history(AgentRemediationWorkflowRecord workflow,
                                                      String eventType,
                                                      String operatorId,
                                                      String reason,
                                                      Map<String, Object> metadata) {
        AgentRemediationWorkflowHistoryRecord po = new AgentRemediationWorkflowHistoryRecord();
        po.setHistoryId("agent-remediation-history-" + UUID.randomUUID());
        po.setWorkflowId(workflow.getWorkflowId());
        po.setAgentId(workflow.getAgentId());
        po.setEventType(eventType);
        po.setOperatorId(operatorId);
        po.setReason(reason);
        po.setMetadataJson(writeJson(metadata));
        po.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        return po;
    }

    private Map<String, Object> staleLeaseMetadata(AgentRemediationWorkflowRecord workflow,
                                                   OffsetDateTime recoveryAt,
                                                   String result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("recoveryMode", "P11_ACTIVE_STALE_LEASE_REAPER");
        metadata.put("result", result);
        metadata.put("workflowId", workflow.getWorkflowId());
        metadata.put("agentId", workflow.getAgentId());
        metadata.put("workflowStatus", workflow.getStatus());
        metadata.put("leaseOwner", workflow.getExecutionLeaseOwner());
        metadata.put("leaseAcquiredAt", workflow.getExecutionLeaseAcquiredAt());
        metadata.put("leaseExpiresAt", workflow.getExecutionLeaseExpiresAt());
        metadata.put("leaseExpiredSeconds", workflow.getExecutionLeaseExpiresAt() == null
                ? null
                : Math.max(0, Duration.between(workflow.getExecutionLeaseExpiresAt(), recoveryAt).toSeconds()));
        metadata.put("recoveryAt", recoveryAt);
        return metadata;
    }

    private String writeJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize stale lease recovery metadata.", e);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", json);
            fallback.put("parseError", e.getMessage());
            return fallback;
        }
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 500);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record StaleWorkflowExecutionLeaseView(
            String workflowId,
            String agentId,
            String status,
            String severity,
            String leaseOwner,
            OffsetDateTime leaseAcquiredAt,
            OffsetDateTime leaseExpiresAt,
            Long leaseExpiredSeconds,
            OffsetDateTime updatedAt) {
    }

    public record RecoveredWorkflowExecutionLeaseView(
            String workflowId,
            String agentId,
            String operatorId,
            String reason,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt) {
    }

    public record StaleLeaseRecoveryRun(
            OffsetDateTime recoveredAt,
            int scannedCount,
            int recoveredCount,
            int raceLostCount,
            List<StaleWorkflowExecutionLeaseView> recovered,
            List<StaleWorkflowExecutionLeaseView> races) {
    }
}
