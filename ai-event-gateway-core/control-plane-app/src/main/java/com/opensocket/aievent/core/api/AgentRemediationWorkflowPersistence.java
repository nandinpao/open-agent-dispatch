package com.opensocket.aievent.core.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowActionExecutionRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowHistoryRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationActionView;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowActionExecutionResponse;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowHistoryEntry;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowResponse;

/**
 * Persistence and representation boundary for Agent remediation workflows.
 *
 * <p>This helper deliberately remains package-private and is constructed by the
 * REST authority. It owns database record mapping, JSON representation,
 * idempotency fingerprints and optimistic workflow-state transitions without
 * becoming a second Spring-managed workflow authority.</p>
 */
final class AgentRemediationWorkflowPersistence {
    private static final TypeReference<List<AgentRemediationActionView>> ACTION_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentRemediationWorkflowStore store;
    private final ObjectMapper objectMapper;

    AgentRemediationWorkflowPersistence(AgentRemediationWorkflowStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    List<AgentRemediationWorkflowResponse> listByAgentId(String agentId, int limit) {
        return store.findWorkflowsByAgentId(agentId, limit).stream().map(this::hydrate).toList();
    }

    AgentRemediationWorkflowResponse require(String agentId, String workflowId) {
        AgentRemediationWorkflowRecord workflow = store.findWorkflowById(workflowId);
        if (workflow == null || !agentId.equals(workflow.getAgentId())) {
            throw new IllegalArgumentException("Remediation workflow not found: " + workflowId);
        }
        return hydrate(workflow);
    }

    void insert(AgentRemediationWorkflowResponse workflow) {
        store.insertWorkflow(toWorkflowRecord(workflow));
        for (AgentRemediationWorkflowHistoryEntry entry : workflow.history()) {
            store.insertHistory(toHistoryRecord(workflow.workflowId(), workflow.agentId(), entry));
        }
    }

    void ensureActionExecutionRows(AgentRemediationWorkflowResponse workflow) {
        if (workflow == null || workflow.actions() == null) return;
        for (AgentRemediationActionView action : workflow.actions()) {
            if (action != null) store.insertActionExecutionIfAbsent(toActionExecutionRecord(workflow, action));
        }
    }

    AgentRemediationWorkflowResponse updateStatus(
            AgentRemediationWorkflowResponse current,
            String status,
            String operatorId,
            AgentRemediationWorkflowHistoryEntry entry) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updatedRows = store.updateWorkflowStatusIfCurrent(
                current.workflowId(), current.status(), status, operatorId, now);
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Remediation workflow transition was rejected because another Core instance changed the workflow state.");
        }
        store.insertHistory(toHistoryRecord(current.workflowId(), current.agentId(), entry));
        return require(current.agentId(), current.workflowId());
    }

    AgentRemediationWorkflowHistoryRecord toHistoryRecord(
            String workflowId, String agentId, AgentRemediationWorkflowHistoryEntry entry) {
        AgentRemediationWorkflowHistoryRecord record = new AgentRemediationWorkflowHistoryRecord();
        record.setHistoryId(entry.historyId());
        record.setWorkflowId(workflowId);
        record.setAgentId(agentId);
        record.setEventType(entry.eventType());
        record.setOperatorId(entry.operatorId());
        record.setReason(entry.reason());
        record.setMetadataJson(writeJson(entry.metadata()));
        record.setOccurredAt(entry.occurredAt());
        return record;
    }

    AgentRemediationWorkflowActionExecutionResponse toActionExecutionResponse(
            AgentRemediationWorkflowActionExecutionRecord record) {
        return new AgentRemediationWorkflowActionExecutionResponse(
                record.getActionExecutionId(),
                record.getWorkflowId(),
                record.getAgentId(),
                record.getActionId(),
                record.getActionType(),
                record.getIdempotencyKey(),
                record.getStatus(),
                record.getAttemptCount(),
                record.getLastOperatorId(),
                record.getLastReason(),
                readJson(record.getLastResultJson(), MAP_TYPE, Map.of()),
                record.getLastError(),
                record.getFirstAttemptAt(),
                record.getLastAttemptAt(),
                record.getCompletedAt(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize remediation workflow JSON payload.", ex);
        }
    }

    private AgentRemediationWorkflowResponse hydrate(AgentRemediationWorkflowRecord record) {
        List<AgentRemediationActionView> actions = readJson(record.getActionsJson(), ACTION_LIST_TYPE, List.of());
        List<String> rollbackSuggestions = readJson(record.getRollbackSuggestionsJson(), STRING_LIST_TYPE, List.of());
        Map<String, Object> metadata = readJson(record.getMetadataJson(), MAP_TYPE, Map.of());
        List<AgentRemediationWorkflowHistoryEntry> history = store.findHistoryByWorkflowId(record.getWorkflowId()).stream()
                .map(this::toHistoryEntry)
                .toList();
        return new AgentRemediationWorkflowResponse(
                record.getWorkflowId(),
                record.getProposalId(),
                record.getAgentId(),
                record.getStatus(),
                record.getSeverity(),
                Boolean.TRUE.equals(record.getApprovalRequired()),
                actions,
                rollbackSuggestions,
                history,
                store.findActionExecutionsByWorkflowId(record.getWorkflowId()).stream()
                        .map(this::toActionExecutionResponse)
                        .toList(),
                record.getCreatedBy(),
                record.getLastOperatorId(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                metadata,
                record.getExecutionLeaseOwner(),
                record.getExecutionLeaseAcquiredAt(),
                record.getExecutionLeaseExpiresAt(),
                AgentRemediationWorkflowExecutionPolicy.executionLeaseRemainingSeconds(record),
                AgentRemediationWorkflowExecutionPolicy.executionLeaseActive(record));
    }

    private AgentRemediationWorkflowRecord toWorkflowRecord(AgentRemediationWorkflowResponse workflow) {
        AgentRemediationWorkflowRecord record = new AgentRemediationWorkflowRecord();
        record.setWorkflowId(workflow.workflowId());
        record.setProposalId(workflow.proposalId());
        record.setAgentId(workflow.agentId());
        record.setStatus(workflow.status());
        record.setSeverity(workflow.severity());
        record.setApprovalRequired(workflow.approvalRequired());
        record.setCreatedBy(workflow.createdBy());
        record.setLastOperatorId(workflow.lastOperatorId());
        record.setRollbackSuggestionsJson(writeJson(workflow.rollbackSuggestions()));
        record.setActionsJson(writeJson(workflow.actions()));
        record.setMetadataJson(writeJson(workflow.metadata()));
        record.setVersion(0L);
        record.setCreatedAt(workflow.createdAt());
        record.setUpdatedAt(workflow.updatedAt());
        return record;
    }

    private AgentRemediationWorkflowHistoryEntry toHistoryEntry(AgentRemediationWorkflowHistoryRecord record) {
        return new AgentRemediationWorkflowHistoryEntry(
                record.getHistoryId(),
                record.getEventType(),
                record.getOperatorId(),
                record.getReason(),
                readJson(record.getMetadataJson(), MAP_TYPE, Map.of()),
                record.getOccurredAt());
    }

    private AgentRemediationWorkflowActionExecutionRecord toActionExecutionRecord(
            AgentRemediationWorkflowResponse workflow, AgentRemediationActionView action) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actionId = firstNonBlank(action.actionId(), action.actionType(), "UNKNOWN_ACTION");
        String idempotencyKey = actionIdempotencyKey(
                workflow.workflowId(), workflow.agentId(), actionId, action.actionType(), action.commandHint());
        AgentRemediationWorkflowActionExecutionRecord record = new AgentRemediationWorkflowActionExecutionRecord();
        record.setActionExecutionId("agent-remediation-action-" + idempotencyKey.substring(Math.max(0, idempotencyKey.length() - 32)));
        record.setWorkflowId(workflow.workflowId());
        record.setAgentId(workflow.agentId());
        record.setActionId(actionId);
        record.setActionType(firstNonBlank(action.actionType(), "UNKNOWN"));
        record.setIdempotencyKey(idempotencyKey);
        record.setStatus(action.executable() ? "PENDING" : "SKIPPED");
        record.setAttemptCount(0);
        record.setLastResultJson(writeJson(Map.of("createdBy", "P9_ACTION_IDEMPOTENCY", "executable", action.executable())));
        record.setCompletedAt(action.executable() ? null : now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private String actionIdempotencyKey(
            String workflowId,
            String agentId,
            String actionId,
            String actionType,
            Map<String, Object> commandHint) {
        String raw = String.join("|",
                safe(workflowId),
                safe(agentId),
                safe(actionId),
                safe(actionType),
                safe(writeJson(commandHint == null ? Map.of() : commandHint)));
        return "remediation-action:v1:" + sha256(raw);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(safe(raw).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize remediation workflow JSON payload.", ex);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
