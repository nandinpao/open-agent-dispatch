package com.opensocket.aievent.core.action.executor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityService;
import com.opensocket.aievent.core.integration.identity.IntegrationOperation;
import com.opensocket.aievent.core.integration.identity.MappingResolutionRequest;
import com.opensocket.aievent.core.integration.identity.ProjectMappingGovernanceService;
import com.opensocket.aievent.core.integration.identity.ProjectMappingPreviewRequest;
import com.opensocket.aievent.core.integration.identity.ProviderFieldMetadata;
import com.opensocket.aievent.core.integration.identity.ProviderMetadataSnapshot;
import com.opensocket.aievent.core.integration.identity.ScopedIntegrationExecutionContext;

/**
 * Canonical operational read/preflight authority for Issue Tracking recovery.
 *
 * <p>Failure chronology comes from adapter_executor_audit, which is ordered by
 * created_at DESC in both database and in-memory repositories. Recovery never
 * edits provider configuration; Integration Configuration remains the only
 * configuration authority. Preflight re-resolves the current Integration
 * Identity context and refreshes provider metadata before a manual retry.</p>
 */
@Service
public class IntegrationRecoveryService {
    private final AdapterActionFacade actions;
    private final IntegrationIdentityService identity;
    private final ProjectMappingGovernanceService mappings;
    private final AdapterExecutorCircuitBreaker circuitBreaker;

    public IntegrationRecoveryService(AdapterActionFacade actions,
                                      IntegrationIdentityService identity,
                                      ProjectMappingGovernanceService mappings,
                                      AdapterExecutorCircuitBreaker circuitBreaker) {
        this.actions = actions;
        this.identity = identity;
        this.mappings = mappings;
        this.circuitBreaker = circuitBreaker;
    }

    public IntegrationRecoverySnapshot snapshot(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        List<AdapterAction> issueActions = actions.recent(Math.max(limit, 500)).stream()
                .filter(value -> value.getAdapterType() == AdapterType.ISSUE_TRACKING)
                .toList();
        Map<String, AdapterAction> actionById = new LinkedHashMap<>();
        issueActions.forEach(action -> actionById.put(action.getActionId(), action));

        List<AdapterExecutorAuditRecord> issueAudits = actions.recentExecutorAudit(Math.min(1000, Math.max(limit * 3, 300))).stream()
                .filter(this::issueAudit)
                .sorted(Comparator.comparing(AdapterExecutorAuditRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<IntegrationRecoveryFailure> failures = issueAudits.stream()
                .filter(this::failureAudit)
                .limit(limit)
                .map(audit -> failure(audit, actionById.get(audit.getActionId())))
                .toList();

        int pending = count(issueActions, AdapterActionStatus.PENDING, AdapterActionStatus.CLAIMED, AdapterActionStatus.EXECUTING);
        int retryWaiting = count(issueActions, AdapterActionStatus.RETRY_WAITING, AdapterActionStatus.EXECUTOR_UNAVAILABLE);
        int failed = count(issueActions, AdapterActionStatus.FAILED);
        int completed = count(issueActions, AdapterActionStatus.COMPLETED);
        int uncertain = (int) failures.stream()
                .filter(IntegrationRecoveryFailure::current)
                .filter(value -> "OUTCOME_UNCERTAIN".equals(value.category()))
                .map(IntegrationRecoveryFailure::actionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        int needsAttention = (int) failures.stream()
                .filter(IntegrationRecoveryFailure::current)
                .filter(value -> !"RATE_LIMIT".equals(value.category()))
                .map(IntegrationRecoveryFailure::actionId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        OffsetDateTime nextRetryAt = issueActions.stream()
                .map(AdapterAction::getNextAttemptAt)
                .filter(Objects::nonNull)
                .filter(value -> value.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                .min(Comparator.naturalOrder())
                .orElse(null);

        String providerState = providerState(issueAudits);
        return new IntegrationRecoverySnapshot(
                pending,
                retryWaiting,
                failed,
                completed,
                needsAttention,
                uncertain,
                providerState,
                nextRetryAt,
                failures.isEmpty() ? null : failures.get(0),
                failures);
    }

    public IntegrationRecoveryPreflight preflight(String actionId) {
        AdapterAction action = requireIssueAction(actionId);
        AdapterExecutorAuditRecord failure = latestFailure(actionId);
        String category = classify(failure, action);
        String failureCode = failure == null ? null : clean(failure.getProviderFailureCode());
        List<String> checks = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        String nextAction = "REVIEW";
        String mappingId = first(
                failure == null ? null : failure.getProjectMappingId(),
                text(action.getPayload(), "projectMappingId", "integrationProjectMappingId"));
        String tenantId = first(
                failure == null ? null : failure.getTenantId(),
                text(action.getPayload(), "tenantId"));
        String connectionId = failure == null ? null : failure.getConnectionId();
        String metadataSnapshotId = null;
        OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);

        if (!retryState(action.getStatus())) {
            blockers.add("AdapterAction is not in a recoverable state: " + action.getStatus());
        }
        if ("OUTCOME_UNCERTAIN".equals(category)) {
            blockers.add("Provider outcome is uncertain. Reconciliation is required before retrying a mutating operation.");
            nextAction = "RECONCILE_PROVIDER_OUTCOME";
            return new IntegrationRecoveryPreflight(actionId, false, category, failureCode, tenantId, connectionId,
                    mappingId, metadataSnapshotId, checkedAt, checks, blockers, nextAction);
        }
        if (action.getNextAttemptAt() != null && action.getNextAttemptAt().isAfter(checkedAt)
                && "RATE_LIMIT".equals(category)) {
            blockers.add("Provider retry window has not opened yet: " + action.getNextAttemptAt());
            nextAction = "WAIT_FOR_RETRY_WINDOW";
        }
        if (blank(tenantId)) blockers.add("Tenant evidence is missing from the action/audit record.");

        if (blockers.isEmpty()) {
            try {
                ScopedIntegrationExecutionContext context = resolveContext(action, tenantId, mappingId);
                connectionId = context.connection().connectionId();
                mappingId = context.mapping().mappingId();
                checks.add("Current Integration Identity context resolved.");
                checks.add("Project Mapping is runtime-resolvable: " + mappingId + ".");
                checks.add("Technical Service Account and active credential resolved.");

                ProviderMetadataSnapshot metadata = mappings.probe(
                        tenantId,
                        mappingId,
                        context.principal().principalId(),
                        true,
                        first(failure == null ? null : failure.getCorrelationId(), text(action.getPayload(), "correlationId"), "integration-recovery-" + actionId));
                metadataSnapshotId = metadata.snapshotId();
                checks.add("Provider metadata probe succeeded: " + metadataSnapshotId + ".");

                var preview = mappings.preview(tenantId, mappingId, new ProjectMappingPreviewRequest(action.getPayload()));
                if (!preview.missingRequiredFields().isEmpty()) {
                    blockers.add("Required mapped fields are still missing: " + String.join(", ", preview.missingRequiredFields()));
                } else {
                    checks.add("Governed mapping preview has no missing required fields.");
                }

                if (priorityFailure(failure)) {
                    ProviderFieldMetadata priority = metadata.fields().stream()
                            .filter(field -> "priority_id".equalsIgnoreCase(first(field.fieldId(), field.fieldKey())))
                            .findFirst().orElse(null);
                    if (priority == null || priority.allowedValues().isEmpty()) {
                        blockers.add("Redmine Priority metadata has no resolvable provider value/default.");
                    } else {
                        checks.add("Redmine Priority metadata is resolvable before CREATE.");
                    }
                }
            } catch (RuntimeException exception) {
                blockers.add("Current integration authority is not ready: " + safeMessage(exception));
            }
        }

        boolean allowed = blockers.isEmpty();
        if (allowed) nextAction = "RETRY_AFTER_PREFLIGHT";
        else if (!"WAIT_FOR_RETRY_WINDOW".equals(nextAction)) nextAction = "REPAIR_IN_INTEGRATION_CONFIGURATION";
        return new IntegrationRecoveryPreflight(actionId, allowed, category, failureCode, tenantId, connectionId,
                mappingId, metadataSnapshotId, checkedAt, checks, blockers, nextAction);
    }

    public AdapterAction governedRetry(String actionId, String reason) {
        if (reason == null || reason.trim().length() < 12) {
            throw new IllegalArgumentException("Recovery retry reason must contain at least 12 characters.");
        }
        IntegrationRecoveryPreflight preflight = preflight(actionId);
        if (!preflight.retryAllowed()) {
            throw new IllegalStateException("INTEGRATION_RECOVERY_PREFLIGHT_BLOCKED: " + String.join("; ", preflight.blockers()));
        }
        return actions.retryForWorker(actionId,
                "Integration Recovery preflight passed: " + reason.trim(), false);
    }

    private ScopedIntegrationExecutionContext resolveContext(AdapterAction action, String tenantId, String mappingId) {
        IntegrationOperation operation = operation(action.getActionType());
        if (!blank(mappingId)) return identity.resolveExecutionContext(tenantId, mappingId, operation);
        Map<String, Object> payload = action.getPayload();
        return identity.resolveExecutionContext(new MappingResolutionRequest(
                tenantId,
                text(payload, "connectionId"),
                text(payload, "ownerDepartmentId", "departmentId"),
                text(payload, "ownerGroupId", "groupId"),
                text(payload, "executorDomainId", "serviceDomainId"),
                text(payload, "sourceSystemId", "sourceSystem"),
                text(payload, "taskType")), operation);
    }

    private IntegrationOperation operation(AdapterActionType type) {
        if (type == null) return IntegrationOperation.CREATE;
        return switch (type) {
            case ISSUE_READ -> IntegrationOperation.READ;
            case ISSUE_COMMENT, ISSUE_UPDATE_COMMENT -> IntegrationOperation.COMMENT;
            case ISSUE_UPDATE -> IntegrationOperation.UPDATE;
            case ISSUE_CREATE -> IntegrationOperation.CREATE;
            default -> IntegrationOperation.CREATE;
        };
    }

    private AdapterAction requireIssueAction(String actionId) {
        AdapterAction action = actions.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
        if (action.getAdapterType() != AdapterType.ISSUE_TRACKING) {
            throw new IllegalArgumentException("Integration Recovery only accepts ISSUE_TRACKING AdapterActions.");
        }
        return action;
    }

    private AdapterExecutorAuditRecord latestFailure(String actionId) {
        return actions.auditByAction(actionId, 100).stream()
                .filter(this::failureAudit)
                .sorted(Comparator.comparing(AdapterExecutorAuditRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst().orElse(null);
    }

    private IntegrationRecoveryFailure failure(AdapterExecutorAuditRecord audit, AdapterAction action) {
        String category = classify(audit, action);
        String retryPolicy = switch (category) {
            case "OUTCOME_UNCERTAIN" -> "RECONCILE";
            case "REQUEST_VALIDATION", "AUTHENTICATION", "PERMISSION", "RESOURCE_NOT_FOUND", "CONFLICT" -> "REPAIR_AND_PREFLIGHT";
            case "RATE_LIMIT" -> "WAIT_AND_RETRY";
            case "PROVIDER_UNAVAILABLE", "EXECUTOR_UNAVAILABLE" -> "PREFLIGHT_AND_RETRY";
            default -> "MANUAL_REVIEW";
        };
        String health = first(audit.getProviderHealthImpact(), category.equals("REQUEST_VALIDATION") ? "HEALTHY" : null, "UNKNOWN");
        boolean current = action != null && switch (action.getStatus()) {
            case FAILED, EXECUTOR_UNAVAILABLE, RETRY_WAITING, CANCELLED -> true;
            default -> false;
        };
        return new IntegrationRecoveryFailure(
                audit.getAuditId(), audit.getActionId(), audit.getTaskId(), audit.getTenantId(),
                first(audit.getSourceSystemId(), action == null ? null : text(action.getPayload(), "sourceSystemId", "sourceSystem")),
                action == null ? null : text(action.getPayload(), "taskType"),
                audit.getConnectionId(), audit.getProjectMappingId(), audit.getExternalProjectId(),
                audit.getProviderStatusCode(), audit.getProviderFailureCode(), health,
                audit.getProviderOutcomeCertainty(), category, retryPolicy,
                audit.getMessage(), audit.getCorrelationId(), audit.getAttemptCount(), audit.getCreatedAt(),
                action == null || action.getStatus() == null ? null : action.getStatus().name(),
                action == null ? null : action.getNextAttemptAt(), current);
    }

    private String providerState(List<AdapterExecutorAuditRecord> audits) {
        boolean circuitOpen = circuitBreaker.snapshot().values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(value -> String.valueOf(value.getOrDefault("openUntil", "")))
                .anyMatch(value -> !value.isBlank());
        if (circuitOpen) return "OPEN_CIRCUIT";
        if (audits.isEmpty()) return "UNKNOWN";
        String impact = upper(audits.get(0).getProviderHealthImpact());
        if ("THROTTLED".equals(impact)) return "THROTTLED";
        if ("DEGRADED".equals(impact)) return "DEGRADED";
        if ("HEALTHY".equals(impact) || "NONE".equals(impact)) return "HEALTHY";
        return failureAudit(audits.get(0)) ? "UNKNOWN" : "HEALTHY";
    }

    private String classify(AdapterExecutorAuditRecord audit, AdapterAction action) {
        String code = upper(audit == null ? null : audit.getProviderFailureCode());
        String certainty = upper(audit == null ? null : audit.getProviderOutcomeCertainty());
        String message = upper(first(audit == null ? null : audit.getMessage(), action == null ? null : action.getLastError()));
        if ("UNCERTAIN".equals(certainty) || contains(code, "OUTCOME_UNCERTAIN") || contains(message, "OUTCOME_UNCERTAIN")) return "OUTCOME_UNCERTAIN";
        if (contains(code, "REQUIRED_FIELD_UNMAPPED") || contains(code, "VALIDATION_FAILED") || contains(message, "422")) return "REQUEST_VALIDATION";
        if (contains(code, "AUTHENTICATION") || contains(message, "401")) return "AUTHENTICATION";
        if (contains(code, "PERMISSION") || contains(message, "403")) return "PERMISSION";
        if (contains(code, "RATE_LIMIT") || contains(message, "429")) return "RATE_LIMIT";
        if (contains(code, "RESOURCE_NOT_FOUND") || contains(message, "404")) return "RESOURCE_NOT_FOUND";
        if (contains(code, "CONFLICT") || contains(message, "409")) return "CONFLICT";
        if (contains(code, "UNAVAILABLE") || contains(code, "TIMEOUT") || contains(message, "503") || contains(message, "502") || contains(message, "504")) return "PROVIDER_UNAVAILABLE";
        if (action != null && action.getStatus() == AdapterActionStatus.EXECUTOR_UNAVAILABLE) return "EXECUTOR_UNAVAILABLE";
        return "UNKNOWN";
    }

    private boolean priorityFailure(AdapterExecutorAuditRecord failure) {
        if (failure == null) return false;
        String code = upper(failure.getProviderFailureCode());
        String message = upper(failure.getMessage());
        return contains(code, "REQUIRED_FIELD_UNMAPPED") && contains(message, "PRIORITY")
                || contains(message, "PRIORITY")
                || (failure.getMessage() != null && failure.getMessage().contains("優先權"));
    }

    private boolean issueAudit(AdapterExecutorAuditRecord audit) {
        return audit != null && "ISSUE_TRACKING".equalsIgnoreCase(audit.getAdapterType());
    }

    private boolean failureAudit(AdapterExecutorAuditRecord audit) {
        if (audit == null) return false;
        if (!blank(audit.getProviderFailureCode())) return true;
        if ("FAILED".equalsIgnoreCase(audit.getAfterStatus()) || "EXECUTOR_UNAVAILABLE".equalsIgnoreCase(audit.getAfterStatus())) return true;
        String outcome = upper(audit.getOutcome());
        return "PERMANENT_FAILURE".equals(outcome) || "RETRYABLE_FAILURE".equals(outcome) || "OUTCOME_UNCERTAIN".equals(outcome);
    }

    private int count(List<AdapterAction> values, AdapterActionStatus... statuses) {
        List<AdapterActionStatus> allowed = List.of(statuses);
        return (int) values.stream().filter(value -> allowed.contains(value.getStatus())).count();
    }

    private boolean retryState(AdapterActionStatus status) {
        return status == AdapterActionStatus.FAILED
                || status == AdapterActionStatus.CANCELLED
                || status == AdapterActionStatus.EXECUTOR_UNAVAILABLE
                || status == AdapterActionStatus.RETRY_WAITING;
    }

    private String text(Map<String, Object> payload, String... keys) {
        if (payload == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private boolean contains(String value, String token) {
        return value != null && token != null && value.contains(token);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String first(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value.trim();
        return null;
    }

    public record IntegrationRecoverySnapshot(
            int pending,
            int retryWaiting,
            int failed,
            int completed,
            int needsAttention,
            int uncertain,
            String providerState,
            OffsetDateTime nextRetryAt,
            IntegrationRecoveryFailure latestFailure,
            List<IntegrationRecoveryFailure> failures) {
        public IntegrationRecoverySnapshot {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }
    }

    public record IntegrationRecoveryFailure(
            String auditId,
            String actionId,
            String taskId,
            String tenantId,
            String sourceSystemId,
            String taskType,
            String connectionId,
            String projectMappingId,
            String externalProjectId,
            Integer providerStatusCode,
            String providerFailureCode,
            String providerHealthImpact,
            String providerOutcomeCertainty,
            String category,
            String retryPolicy,
            String message,
            String correlationId,
            int attemptCount,
            OffsetDateTime occurredAt,
            String actionStatus,
            OffsetDateTime nextAttemptAt,
            boolean current) {}

    public record IntegrationRecoveryPreflight(
            String actionId,
            boolean retryAllowed,
            String category,
            String failureCode,
            String tenantId,
            String connectionId,
            String projectMappingId,
            String metadataSnapshotId,
            OffsetDateTime checkedAt,
            List<String> checks,
            List<String> blockers,
            String nextAction) {
        public IntegrationRecoveryPreflight {
            checks = checks == null ? List.of() : List.copyOf(checks);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
