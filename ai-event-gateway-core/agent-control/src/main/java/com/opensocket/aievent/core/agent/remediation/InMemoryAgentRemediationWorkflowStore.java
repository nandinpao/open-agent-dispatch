package com.opensocket.aievent.core.agent.remediation;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "agent-remediation.workflow", name = "store", havingValue = "MEMORY")
public class InMemoryAgentRemediationWorkflowStore implements AgentRemediationWorkflowStore {
    private final ConcurrentHashMap<String, AgentRemediationWorkflowRecord> workflows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentRemediationWorkflowHistoryRecord> history = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentRemediationWorkflowActionExecutionRecord> actionExecutions = new ConcurrentHashMap<>();

    @Override
    public int insertWorkflow(AgentRemediationWorkflowRecord record) {
        if (record == null || record.getWorkflowId() == null) return 0;
        workflows.put(record.getWorkflowId(), copy(record));
        return 1;
    }

    @Override
    public AgentRemediationWorkflowRecord findWorkflowById(String workflowId) {
        return copy(workflows.get(workflowId));
    }

    @Override
    public List<AgentRemediationWorkflowRecord> findWorkflowsByAgentId(String agentId, int limit) {
        return workflows.values().stream()
                .filter(record -> agentId != null && agentId.equals(record.getAgentId()))
                .sorted(Comparator.comparing(AgentRemediationWorkflowRecord::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, limit))
                .map(this::copy)
                .toList();
    }

    @Override
    public int updateWorkflowStatusIfCurrent(String workflowId, String expectedStatus, String nextStatus, String lastOperatorId, OffsetDateTime updatedAt) {
        boolean[] updated = {false};
        workflows.computeIfPresent(workflowId, (id, existing) -> {
            if (!safeEquals(existing.getStatus(), expectedStatus)) return existing;
            AgentRemediationWorkflowRecord next = copy(existing);
            next.setStatus(nextStatus);
            next.setLastOperatorId(lastOperatorId);
            next.setUpdatedAt(updatedAt);
            next.setVersion((next.getVersion() == null ? 0L : next.getVersion()) + 1L);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    @Override
    public int acquireWorkflowExecutionLease(String workflowId, String expectedStatus, String leaseOwner, String lastOperatorId, OffsetDateTime acquiredAt, OffsetDateTime expiresAt) {
        boolean[] updated = {false};
        workflows.computeIfPresent(workflowId, (id, existing) -> {
            if (!safeEquals(existing.getStatus(), expectedStatus)) return existing;
            if (existing.getExecutionLeaseOwner() != null
                    && existing.getExecutionLeaseExpiresAt() != null
                    && existing.getExecutionLeaseExpiresAt().isAfter(acquiredAt)) {
                return existing;
            }
            AgentRemediationWorkflowRecord next = copy(existing);
            next.setExecutionLeaseOwner(leaseOwner);
            next.setExecutionLeaseAcquiredAt(acquiredAt);
            next.setExecutionLeaseExpiresAt(expiresAt);
            next.setExecutionLeaseVersion((next.getExecutionLeaseVersion() == null ? 0L : next.getExecutionLeaseVersion()) + 1L);
            next.setLastOperatorId(lastOperatorId);
            next.setUpdatedAt(acquiredAt);
            next.setVersion((next.getVersion() == null ? 0L : next.getVersion()) + 1L);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    @Override
    public int releaseWorkflowExecutionLease(String workflowId, String leaseOwner, OffsetDateTime updatedAt) {
        boolean[] updated = {false};
        workflows.computeIfPresent(workflowId, (id, existing) -> {
            if (!safeEquals(existing.getExecutionLeaseOwner(), leaseOwner)) return existing;
            AgentRemediationWorkflowRecord next = copy(existing);
            next.setExecutionLeaseOwner(null);
            next.setExecutionLeaseAcquiredAt(null);
            next.setExecutionLeaseExpiresAt(null);
            next.setUpdatedAt(updatedAt);
            next.setVersion((next.getVersion() == null ? 0L : next.getVersion()) + 1L);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    @Override
    public int clearExpiredWorkflowExecutionLease(String workflowId, OffsetDateTime now) {
        boolean[] updated = {false};
        workflows.computeIfPresent(workflowId, (id, existing) -> {
            if (existing.getExecutionLeaseExpiresAt() == null || existing.getExecutionLeaseExpiresAt().isAfter(now)) return existing;
            AgentRemediationWorkflowRecord next = copy(existing);
            next.setExecutionLeaseOwner(null);
            next.setExecutionLeaseAcquiredAt(null);
            next.setExecutionLeaseExpiresAt(null);
            next.setUpdatedAt(now);
            next.setVersion((next.getVersion() == null ? 0L : next.getVersion()) + 1L);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    @Override
    public int clearExpiredWorkflowExecutionLeaseForOwner(String workflowId, String leaseOwner, OffsetDateTime now) {
        boolean[] updated = {false};
        workflows.computeIfPresent(workflowId, (id, existing) -> {
            if (!safeEquals(existing.getExecutionLeaseOwner(), leaseOwner)
                    || existing.getExecutionLeaseExpiresAt() == null
                    || existing.getExecutionLeaseExpiresAt().isAfter(now)) return existing;
            AgentRemediationWorkflowRecord next = copy(existing);
            next.setExecutionLeaseOwner(null);
            next.setExecutionLeaseAcquiredAt(null);
            next.setExecutionLeaseExpiresAt(null);
            next.setUpdatedAt(now);
            next.setVersion((next.getVersion() == null ? 0L : next.getVersion()) + 1L);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    @Override
    public List<AgentRemediationWorkflowRecord> findExpiredWorkflowExecutionLeases(OffsetDateTime now, int limit) {
        return workflows.values().stream()
                .filter(record -> record.getExecutionLeaseOwner() != null)
                .filter(record -> record.getExecutionLeaseExpiresAt() != null)
                .filter(record -> !record.getExecutionLeaseExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(AgentRemediationWorkflowRecord::getExecutionLeaseExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(1, limit))
                .map(this::copy)
                .toList();
    }

    @Override
    public int insertHistory(AgentRemediationWorkflowHistoryRecord record) {
        if (record == null || record.getHistoryId() == null) return 0;
        history.put(record.getHistoryId(), copy(record));
        return 1;
    }

    @Override
    public List<AgentRemediationWorkflowHistoryRecord> findHistoryByWorkflowId(String workflowId) {
        return history.values().stream()
                .filter(record -> workflowId != null && workflowId.equals(record.getWorkflowId()))
                .sorted(Comparator.comparing(AgentRemediationWorkflowHistoryRecord::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<AgentRemediationWorkflowHistoryRecord> findHistoryByEventType(String eventType, int limit) {
        return history.values().stream()
                .filter(record -> eventType != null && eventType.equals(record.getEventType()))
                .sorted(Comparator.comparing(AgentRemediationWorkflowHistoryRecord::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, limit))
                .map(this::copy)
                .toList();
    }

    @Override
    public int insertActionExecutionIfAbsent(AgentRemediationWorkflowActionExecutionRecord record) {
        if (record == null || record.getActionExecutionId() == null) return 0;
        String existingKey = record.getWorkflowId() + "|" + record.getActionId();
        boolean exists = actionExecutions.values().stream().anyMatch(existing -> existingKey.equals(existing.getWorkflowId() + "|" + existing.getActionId()));
        if (exists) return 0;
        actionExecutions.put(record.getActionExecutionId(), copy(record));
        return 1;
    }

    @Override
    public List<AgentRemediationWorkflowActionExecutionRecord> findActionExecutionsByWorkflowId(String workflowId) {
        return actionExecutions.values().stream()
                .filter(record -> workflowId != null && workflowId.equals(record.getWorkflowId()))
                .sorted(Comparator.comparing(AgentRemediationWorkflowActionExecutionRecord::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copy)
                .toList();
    }

    @Override
    public AgentRemediationWorkflowActionExecutionRecord findActionExecutionById(String actionExecutionId) {
        return copy(actionExecutions.get(actionExecutionId));
    }

    @Override
    public int claimActionExecutionForRun(String actionExecutionId, String lastOperatorId, String lastReason, OffsetDateTime updatedAt) {
        boolean[] updated = {false};
        actionExecutions.computeIfPresent(actionExecutionId, (id, existing) -> {
            if (!"PENDING".equals(existing.getStatus()) && !"FAILED".equals(existing.getStatus())) return existing;
            AgentRemediationWorkflowActionExecutionRecord next = copy(existing);
            next.setStatus("RUNNING");
            next.setAttemptCount((next.getAttemptCount() == null ? 0 : next.getAttemptCount()) + 1);
            next.setLastOperatorId(lastOperatorId);
            next.setLastReason(lastReason);
            if (next.getFirstAttemptAt() == null) next.setFirstAttemptAt(updatedAt);
            next.setLastAttemptAt(updatedAt);
            next.setCompletedAt(null);
            next.setUpdatedAt(updatedAt);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    @Override
    public int completeActionExecutionIfRunning(String actionExecutionId, String nextStatus, String lastResultJson, String lastError, OffsetDateTime updatedAt) {
        boolean[] updated = {false};
        actionExecutions.computeIfPresent(actionExecutionId, (id, existing) -> {
            if (!"RUNNING".equals(existing.getStatus())) return existing;
            AgentRemediationWorkflowActionExecutionRecord next = copy(existing);
            next.setStatus(nextStatus);
            next.setLastResultJson(lastResultJson);
            next.setLastError(lastError);
            if ("SUCCEEDED".equals(nextStatus) || "SKIPPED".equals(nextStatus) || "FAILED".equals(nextStatus)) {
                next.setCompletedAt(updatedAt);
            }
            next.setUpdatedAt(updatedAt);
            updated[0] = true;
            return next;
        });
        return updated[0] ? 1 : 0;
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private AgentRemediationWorkflowRecord copy(AgentRemediationWorkflowRecord source) {
        if (source == null) return null;
        AgentRemediationWorkflowRecord target = new AgentRemediationWorkflowRecord();
        target.setWorkflowId(source.getWorkflowId());
        target.setProposalId(source.getProposalId());
        target.setAgentId(source.getAgentId());
        target.setStatus(source.getStatus());
        target.setSeverity(source.getSeverity());
        target.setApprovalRequired(source.getApprovalRequired());
        target.setCreatedBy(source.getCreatedBy());
        target.setLastOperatorId(source.getLastOperatorId());
        target.setRollbackSuggestionsJson(source.getRollbackSuggestionsJson());
        target.setActionsJson(source.getActionsJson());
        target.setMetadataJson(source.getMetadataJson());
        target.setExecutionLeaseOwner(source.getExecutionLeaseOwner());
        target.setExecutionLeaseAcquiredAt(source.getExecutionLeaseAcquiredAt());
        target.setExecutionLeaseExpiresAt(source.getExecutionLeaseExpiresAt());
        target.setExecutionLeaseVersion(source.getExecutionLeaseVersion());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private AgentRemediationWorkflowHistoryRecord copy(AgentRemediationWorkflowHistoryRecord source) {
        if (source == null) return null;
        AgentRemediationWorkflowHistoryRecord target = new AgentRemediationWorkflowHistoryRecord();
        target.setHistoryId(source.getHistoryId());
        target.setWorkflowId(source.getWorkflowId());
        target.setAgentId(source.getAgentId());
        target.setEventType(source.getEventType());
        target.setOperatorId(source.getOperatorId());
        target.setReason(source.getReason());
        target.setMetadataJson(source.getMetadataJson());
        target.setOccurredAt(source.getOccurredAt());
        return target;
    }

    private AgentRemediationWorkflowActionExecutionRecord copy(AgentRemediationWorkflowActionExecutionRecord source) {
        if (source == null) return null;
        AgentRemediationWorkflowActionExecutionRecord target = new AgentRemediationWorkflowActionExecutionRecord();
        target.setActionExecutionId(source.getActionExecutionId());
        target.setWorkflowId(source.getWorkflowId());
        target.setAgentId(source.getAgentId());
        target.setActionId(source.getActionId());
        target.setActionType(source.getActionType());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setStatus(source.getStatus());
        target.setAttemptCount(source.getAttemptCount());
        target.setLastOperatorId(source.getLastOperatorId());
        target.setLastReason(source.getLastReason());
        target.setLastResultJson(source.getLastResultJson());
        target.setLastError(source.getLastError());
        target.setFirstAttemptAt(source.getFirstAttemptAt());
        target.setLastAttemptAt(source.getLastAttemptAt());
        target.setCompletedAt(source.getCompletedAt());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}
