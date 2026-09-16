package com.opensocket.aievent.core.action.executor.issue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.integration.identity.ConnectorIntegrationExecutionContext;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityService;
import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import com.opensocket.aievent.core.integration.identity.MappingResolutionRequest;
import com.opensocket.aievent.core.integration.identity.ResolvedIntegrationSecret;
import com.opensocket.aievent.core.issuetracking.connector.IssueCommentCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueConnectorResult;
import com.opensocket.aievent.core.issuetracking.connector.IssueCreateCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueReadCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderFailureCode;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderHealthImpact;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderOutcomeCertainty;
import com.opensocket.aievent.core.issuetracking.application.recovery.ProviderHealthService;
import com.opensocket.aievent.core.issuetracking.connector.IssueUpdateCommand;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskIssueBindingContext;

import tools.jackson.databind.ObjectMapper;

/**
 * Canonical I0-C/I0-D Issue runtime.
 *
 * <p>The runtime reloads the persisted Task through the read-only Task query
 * boundary, resolves Source/Domain-to-Project routing server-side and then uses
 * the typed Redmine connector. It never accepts provider, mapping, principal,
 * credential or secret authority from the AdapterAction payload.</p>
 */
@Service
public class RedmineConnectorRuntimeService {
    private static final Logger log = LoggerFactory.getLogger(RedmineConnectorRuntimeService.class);
    private final IntegrationIdentityService identities;
    private final IntegrationSecretResolver secrets;
    private final TaskOperationalQuery tasks;
    private final ProviderHealthService providerHealth;
    private final ObjectMapper json;

    public RedmineConnectorRuntimeService(IntegrationIdentityService identities,
                                          IntegrationSecretResolver secrets,
                                          TaskOperationalQuery tasks,
                                          ProviderHealthService providerHealth,
                                          ObjectMapper json) {
        this.identities = identities;
        this.secrets = secrets;
        this.tasks = tasks;
        this.providerHealth = providerHealth;
        this.json = json;
    }

    public AdapterExecutionResult execute(AdapterAction action) {
        if (action == null || action.getTaskId() == null || action.getTaskId().isBlank()) {
            return AdapterExecutionResult.permanentFailure(name(), "ISSUE_CONNECTOR_TASK_CONTEXT_REQUIRED");
        }
        TaskRecord task = tasks.findTask(action.getTaskId()).orElse(null);
        if (task == null) {
            return AdapterExecutionResult.permanentFailure(name(), "ISSUE_CONNECTOR_TASK_NOT_FOUND");
        }
        if (task.getTenantId() == null || task.getTenantId().isBlank()) {
            return AdapterExecutionResult.permanentFailure(name(), "ISSUE_CONNECTOR_TASK_TENANT_REQUIRED");
        }

        TaskRecord issueAuthorityTask = issueAuthorityTask(task);
        ConnectorIntegrationExecutionContext context;
        try {
            context = identities.resolveConnectorExecutionContext(mappingRequest(issueAuthorityTask));
        } catch (RuntimeException ex) {
            return preflightFailure(action, task, "ISSUE_CONNECTOR_EXECUTION_CONTEXT_UNAVAILABLE", safe(ex), null);
        }

        String routeBFenceViolation = routeBFenceViolation(action, task, context);
        if (routeBFenceViolation != null) {
            return preflightFailure(action, task, "ISSUE_CONNECTOR_ROUTE_B_CONTEXT_STALE", routeBFenceViolation, context);
        }

        ResolvedIntegrationSecret resolved;
        try {
            resolved = secrets.resolve(context.credential());
        } catch (RuntimeException ex) {
            String errorCode = credentialMaterialErrorCode(ex);
            log.warn("issue_credential_material_unavailable taskId={} actionId={} connectionId={} mappingId={} credentialId={} credentialVersion={} secretScheme={} resolverMode={} errorCode={}",
                    task.getTaskId(), action.getActionId(), context.connection().connectionId(), context.mapping().mappingId(),
                    context.credential().credentialId(), context.credential().secretVersion(), secretScheme(context.credential().secretRef()),
                    secrets.mode(), errorCode);
            return preflightFailure(action, task, errorCode, safe(ex), context);
        }

        try (resolved) {
            var p = new AdapterActionExecutionProperties.Redmine();
            p.setEnabled(true);
            p.setBaseUrl(context.connection().baseUrl());
            p.setApiKey(resolved.reveal());
            p.setProjectId(context.mapping().externalProjectId());
            p.setTrackerId(context.mapping().externalTrackerId());
            RedmineIssueVendorExecutor connector = new RedmineIssueVendorExecutor(
                    p, name(), json, Duration.ofMillis(context.connection().timeoutMs()));
            String idempotencyKey = idempotencyKey(action);
            String operationFingerprint = operationFingerprint(action, task, context, idempotencyKey);
            log.info("issue_provider_execution_started taskId={} issueOwnerTaskId={} actionId={} provider=REDMINE connectionId={} mappingId={} credentialId={} operation={}",
                    task.getTaskId(), issueAuthorityTask.getTaskId(), action.getActionId(), context.connection().connectionId(), context.mapping().mappingId(),
                    context.credential().credentialId(), action.getActionType());
            IssueConnectorResult result = executeTyped(connector, action, task, idempotencyKey);
            recordProviderHealth(task, action, context, result);
            recordCredentialUse(task, context);
            AdapterExecutionResult executionResult = toAdapterResult(result);
            enrichExecutionEvidence(executionResult, action, task, context, idempotencyKey, operationFingerprint, result);
            return executionResult;
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException && safe(ex).contains("ISSUE_PROVIDER_REQUIRED_FIELD_UNMAPPED")) {
                AdapterExecutionResult failure = AdapterExecutionResult.permanentFailure(name(), safe(ex));
                failure.setErrorCode("ISSUE_PROVIDER_REQUIRED_FIELD_UNMAPPED");
                failure.setProviderHealthImpact("NONE");
                failure.setProviderOutcomeCertainty("CONFIRMED");
                enrichExecutionEvidence(failure, action, task, context, idempotencyKey(action),
                        operationFingerprint(action, task, context, idempotencyKey(action)), null);
                return failure;
            }
            AdapterExecutionResult failure = AdapterExecutionResult.retryableFailure(name(), "ISSUE_CONNECTOR_EXECUTION_FAILED: " + safe(ex));
            failure.setErrorCode("ISSUE_PROVIDER_EXECUTION_FAILED");
            failure.setProviderHealthImpact("DEGRADED");
            failure.setProviderOutcomeCertainty("CONFIRMED");
            enrichExecutionEvidence(failure, action, task, context, idempotencyKey(action),
                    operationFingerprint(action, task, context, idempotencyKey(action)), null);
            return failure;
        }
    }

    private IssueConnectorResult executeTyped(RedmineIssueVendorExecutor connector,
                                              AdapterAction action,
                                              TaskRecord task,
                                              String idem) {
        AdapterActionType type = action.getActionType();
        if (type == null) throw new IllegalArgumentException("ISSUE_CONNECTOR_OPERATION_REQUIRED");
        Map<String, Object> payload = action.getPayload();
        return switch (type) {
            case ISSUE_READ -> connector.read(new IssueReadCommand(requiredIssueId(payload)));
            case ISSUE_CREATE -> {
                Map<String,Object> providerFields = providerFields(payload);
                String priorityId = firstNonBlank(
                        text(payload, "priorityId", "priority_id"),
                        text(providerFields, "priority_id", "priorityId"),
                        connector.resolveDefaultPriorityId());
                if (priorityId == null) {
                    throw new IllegalArgumentException("ISSUE_PROVIDER_REQUIRED_FIELD_UNMAPPED:priority_id");
                }
                yield connector.create(new IssueCreateCommand(
                        firstNonBlank(text(payload, "issueTitle", "title", "subject"), task.getTitle(), "OpenDispatch Task " + task.getTaskId()),
                        firstNonBlank(text(payload, "issueDescription", "description"), text(payload, "agentSummary", "summary"), task.getDescription()),
                        priorityId,
                        idem,
                        providerFields));
            }
            case ISSUE_COMMENT, ISSUE_UPDATE_COMMENT -> connector.comment(new IssueCommentCommand(
                    requiredIssueId(payload),
                    requiredComment(payload, task),
                    idem));
            case ISSUE_UPDATE -> connector.update(new IssueUpdateCommand(
                    requiredIssueId(payload),
                    text(payload, "subject", "issueTitle", "title"),
                    text(payload, "description", "issueDescription"),
                    text(payload, "statusId", "status_id"),
                    text(payload, "priorityId", "priority_id"),
                    text(payload, "assigneeId", "assignedToId", "assigned_to_id"),
                    idem));
            default -> throw new IllegalArgumentException("ISSUE_CONNECTOR_OPERATION_UNSUPPORTED: " + type);
        };
    }


    @SuppressWarnings("unchecked")
    private Map<String,Object> providerFields(Map<String,Object> payload) {
        if (payload == null) return Map.of();
        Object direct = payload.get("providerFields");
        if (direct instanceof Map<?,?> m) {
            Map<String,Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k,v) -> out.put(String.valueOf(k), v));
            return Map.copyOf(out);
        }
        Object approved = payload.get("approvedContext");
        if (approved instanceof Map<?,?> a) {
            Object nested = a.get("providerFields");
            if (nested instanceof Map<?,?> m) {
                Map<String,Object> out = new java.util.LinkedHashMap<>();
                m.forEach((k,v) -> out.put(String.valueOf(k), v));
                return Map.copyOf(out);
            }
        }
        return Map.of();
    }

    private TaskRecord issueAuthorityTask(TaskRecord task) {
        if (task == null) return null;
        String mode = firstNonBlank(task.getIssueSyncPolicyInheritanceMode(), "NONE");
        if ("NONE".equalsIgnoreCase(mode)) return task;
        String ownerId = firstNonBlank(task.getRootTaskId(), task.getIssueSyncPolicyInheritedFromTaskId(), task.getParentTaskId());
        if (ownerId == null || ownerId.equals(task.getTaskId())) return task;
        return tasks.findTask(task.getTenantId(), ownerId)
                .orElseThrow(() -> new IllegalStateException("ISSUE_OWNER_TASK_NOT_FOUND:" + ownerId));
    }

    private MappingResolutionRequest mappingRequest(TaskRecord task) {
        TaskIssueBindingContext context = TaskIssueBindingContext.from(task);
        return new MappingResolutionRequest(
                context.tenantId(), null, context.departmentId(), context.groupId(),
                context.serviceDomainId(), context.sourceSystemId(), context.taskType());
    }

    /**
     * A2A child detection is retained for execution evidence only. Binding authority
     * is resolved through {@link TaskIssueBindingContext}; this helper must not be
     * used to choose a Project Mapping.
     */
    private boolean isA2AChild(TaskRecord task) {
        return task != null
                && (task.getParentTaskId() != null
                    || "A2A".equalsIgnoreCase(task.getEventStage()));
    }

    private String requiredIssueId(Map<String, Object> payload) {
        String raw = text(payload, "linkedIssueId", "issueId", "externalIssueId", "iid", "key");
        if (raw == null) throw new IllegalArgumentException("ISSUE_CONNECTOR_ISSUE_ID_REQUIRED");
        int colon = raw.indexOf(':');
        if (colon >= 0 && colon < raw.length() - 1) raw = raw.substring(colon + 1);
        if (raw.contains("#")) raw = raw.substring(raw.lastIndexOf('#') + 1);
        raw = raw.trim();
        if (raw.isBlank()) throw new IllegalArgumentException("ISSUE_CONNECTOR_ISSUE_ID_REQUIRED");
        return raw;
    }

    private String requiredComment(Map<String, Object> payload, TaskRecord task) {
        String comment = firstNonBlank(
                text(payload, "issueComment", "comment", "notes", "body"),
                text(payload, "agentSummary", "summary", "resultSummary"),
                task.getDescription());
        if (comment == null) throw new IllegalArgumentException("ISSUE_CONNECTOR_COMMENT_REQUIRED");
        return comment;
    }

    private AdapterExecutionResult toAdapterResult(IssueConnectorResult result) {
        AdapterExecutionResult out;
        if (result.success()) {
            out = AdapterExecutionResult.success(name(), result.responseRef());
            out.setIssueVendor(result.provider());
            out.setIssueId(result.issueId());
            out.setIssueUrl(result.issueUrl());
            out.setIssueStatus(result.issueStatus());
        } else if (result.failureCode() == IssueProviderFailureCode.ISSUE_PROVIDER_OUTCOME_UNCERTAIN
                || result.outcomeCertainty() == IssueProviderOutcomeCertainty.UNCERTAIN) {
            out = AdapterExecutionResult.outcomeUncertain(name(), result.errorMessage());
        } else if (result.failureCode() == IssueProviderFailureCode.ISSUE_PROVIDER_TIMEOUT) {
            out = AdapterExecutionResult.timeout(name(), result.errorMessage());
        } else if (result.retryable()) {
            out = AdapterExecutionResult.retryableFailure(name(), result.errorMessage());
        } else {
            out = AdapterExecutionResult.permanentFailure(name(), result.errorMessage());
        }
        out.setProviderStatusCode(result.providerStatusCode());
        out.setProviderHealthImpact(result.healthImpact() == null ? null : result.healthImpact().name());
        out.setErrorCode(result.failureCode() == null ? null : result.failureCode().name());
        out.setProviderOutcomeCertainty(result.outcomeCertainty() == null ? null : result.outcomeCertainty().name());
        return out;
    }

    private void enrichExecutionEvidence(AdapterExecutionResult out,
                                         AdapterAction action,
                                         TaskRecord task,
                                         ConnectorIntegrationExecutionContext context,
                                         String idempotencyKey,
                                         String operationFingerprint,
                                         IssueConnectorResult result) {
        out.setTenantId(task.getTenantId());
        out.setCorrelationId(firstNonBlank(task.getCorrelationId(), task.getOriginCorrelationId(), action.getDispatchRequestId()));
        out.setA2aRequestId(isA2AChild(task) ? task.getRequestId() : null);
        out.setSourceSystemId(firstNonBlank(task.getExecutorDomainId(), task.getSourceSystem(), task.getOriginSourceSystem()));
        out.setConnectionId(context.connection().connectionId());
        out.setProjectMappingId(context.mapping().mappingId());
        out.setExternalProjectId(context.mapping().externalProjectId());
        out.setTechnicalPrincipalId(context.principal().principalId());
        out.setCredentialId(context.credential().credentialId());
        out.setCredentialVersion(context.credential().secretVersion());
        out.setIdempotencyKey(idempotencyKey);
        out.setOperationFingerprint(operationFingerprint);
        if (result != null) {
            out.setProviderOutcomeCertainty(result.outcomeCertainty() == null ? null : result.outcomeCertainty().name());
        }
    }

    /**
     * Route B binds the automation decision to one governed provider context. The
     * AdapterAction payload is evidence only; provider authority is still reloaded
     * server-side. If the current binding has changed since Task finalization, fail
     * closed before any network write rather than silently executing against a new
     * Project/Connection/schema.
     */
    private String routeBFenceViolation(AdapterAction action,
                                        TaskRecord task,
                                        ConnectorIntegrationExecutionContext context) {
        Map<String, Object> payload = action.getPayload();
        if (!"TASK_POLICY_ROUTE_B".equals(text(payload, "issueAutomationAuthority"))) return null;
        String expectedTenant = text(payload, "tenantId");
        String expectedConnection = text(payload, "connectionId");
        String expectedMapping = text(payload, "projectMappingId");
        String expectedVersion = text(payload, "projectMappingVersion");
        String expectedSchema = text(payload, "projectMappingSchemaHash");
        if (expectedTenant == null || expectedConnection == null || expectedMapping == null
                || expectedVersion == null || expectedSchema == null) {
            return "ISSUE_CONNECTOR_ROUTE_B_FENCE_INCOMPLETE: Route B action is missing tenant/connection/mapping/version/schema evidence.";
        }
        if (!expectedTenant.equals(task.getTenantId())) {
            return "ISSUE_CONNECTOR_ROUTE_B_TENANT_MISMATCH";
        }
        if (!expectedConnection.equals(context.connection().connectionId())) {
            return "ISSUE_CONNECTOR_ROUTE_B_CONNECTION_CHANGED: expected=" + expectedConnection
                    + ", current=" + context.connection().connectionId();
        }
        if (!expectedMapping.equals(context.mapping().mappingId())) {
            return "ISSUE_CONNECTOR_ROUTE_B_MAPPING_CHANGED: expected=" + expectedMapping
                    + ", current=" + context.mapping().mappingId();
        }
        if (!expectedVersion.equals(String.valueOf(context.mapping().mappingVersion()))) {
            return "ISSUE_CONNECTOR_ROUTE_B_MAPPING_VERSION_CHANGED: expected=" + expectedVersion
                    + ", current=" + context.mapping().mappingVersion();
        }
        String currentSchema = normalized(context.mapping().metadataSchemaHash());
        if (currentSchema == null || !expectedSchema.equals(currentSchema)) {
            return "ISSUE_CONNECTOR_ROUTE_B_MAPPING_SCHEMA_CHANGED: expected=" + expectedSchema
                    + ", current=" + firstNonBlank(currentSchema, "MISSING");
        }
        return null;
    }

    private String credentialMaterialErrorCode(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (message == null || message.isBlank()) return "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE";
        if (message.contains("SECRET_REFERENCE_REQUIRED")) return "ISSUE_CREDENTIAL_SECRET_REFERENCE_REQUIRED";
        if (message.contains("SECRET_REFERENCE_EMPTY")) return "ISSUE_CREDENTIAL_SECRET_REFERENCE_EMPTY";
        if (message.contains("SECRET_REFERENCE_SCHEME_NOT_SUPPORTED")) return "ISSUE_CREDENTIAL_SECRET_REFERENCE_UNSUPPORTED";
        return "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE";
    }

    private String secretScheme(String reference) {
        if (reference == null || reference.isBlank()) return "MISSING";
        int marker = reference.indexOf("://");
        return marker <= 0 ? "UNKNOWN" : reference.substring(0, marker).trim().toUpperCase(java.util.Locale.ROOT);
    }

    private AdapterExecutionResult preflightFailure(AdapterAction action,
                                                    TaskRecord task,
                                                    String errorCode,
                                                    String message,
                                                    ConnectorIntegrationExecutionContext context) {
        AdapterExecutionResult out = AdapterExecutionResult.permanentFailure(name(), errorCode + ": " + message);
        out.setErrorCode(errorCode);
        out.setProviderHealthImpact("NONE");
        out.setProviderOutcomeCertainty("CONFIRMED");
        out.setTenantId(task == null ? null : task.getTenantId());
        out.setCorrelationId(task == null ? null : firstNonBlank(task.getCorrelationId(), task.getOriginCorrelationId(), action.getDispatchRequestId()));
        out.setSourceSystemId(task == null ? null : firstNonBlank(task.getExecutorDomainId(), task.getSourceSystem(), task.getOriginSourceSystem()));
        out.setIdempotencyKey(idempotencyKey(action));
        if (context != null) {
            out.setConnectionId(context.connection().connectionId());
            out.setProjectMappingId(context.mapping().mappingId());
            out.setExternalProjectId(context.mapping().externalProjectId());
            out.setTechnicalPrincipalId(context.principal().principalId());
            out.setCredentialId(context.credential().credentialId());
            out.setCredentialVersion(context.credential().secretVersion());
            out.setOperationFingerprint(operationFingerprint(action, task, context, idempotencyKey(action)));
        }
        return out;
    }

    private String idempotencyKey(AdapterAction action) {
        if (action.getActionType() == AdapterActionType.ISSUE_READ) return null;
        Map<String, Object> payload = action.getPayload();
        return firstNonBlank(action.getIdempotencyKey(), text(payload, "issueActionIdempotencyKey", "idempotencyKey"),
                action.getActionId() == null ? null : "issue-action:" + action.getActionId());
    }

    private String operationFingerprint(AdapterAction action, TaskRecord task, ConnectorIntegrationExecutionContext context, String idempotencyKey) {
        try {
            String payload = action.getPayload() == null ? "{}" : json.writeValueAsString(new TreeMap<>(action.getPayload()));
            String canonical = String.join("|",
                    action.getActionType() == null ? "" : action.getActionType().name(),
                    firstNonBlank(task.getTaskId(), ""),
                    firstNonBlank(task.getExecutorDomainId(), task.getSourceSystem(), ""),
                    context.connection().connectionId(),
                    context.mapping().mappingId(),
                    firstNonBlank(context.mapping().externalProjectId(), ""),
                    firstNonBlank(idempotencyKey, ""), payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("ISSUE_CONNECTOR_FINGERPRINT_FAILED", ex);
        }
    }

    private void recordCredentialUse(TaskRecord task, ConnectorIntegrationExecutionContext context) {
        try {
            identities.recordCredentialUse(task.getTenantId(), context.credential().credentialId(), OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException ignored) {
            // Usage telemetry must never rewrite an already-observed provider result.
        }
    }

    private void recordProviderHealth(TaskRecord task,
                                      AdapterAction action,
                                      ConnectorIntegrationExecutionContext context,
                                      IssueConnectorResult result) {
        IssueProviderHealthImpact impact = result.healthImpact();
        if (impact == null || impact == IssueProviderHealthImpact.NONE) return;
        boolean healthyObservation = impact == IssueProviderHealthImpact.HEALTHY;
        boolean rateLimited = impact == IssueProviderHealthImpact.THROTTLED;
        String failureCode = healthyObservation || result.failureCode() == null ? null : result.failureCode().name();
        try {
            providerHealth.record(
                    task.getTenantId(),
                    context.connection().connectionId(),
                    context.mapping().mappingId(),
                    healthyObservation,
                    rateLimited,
                    failureCode,
                    0,
                    100,
                    "ISSUE_CONNECTOR",
                    firstNonBlank(action.getDispatchRequestId(), action.getActionId(), task.getTaskId()));
        } catch (RuntimeException ignored) {
            // Provider health evidence must not rewrite the provider operation result.
        }
    }

    private String text(Map<String, Object> payload, String... keys) {
        if (payload == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String s && !s.isBlank()) return s.trim();
            if (value instanceof Number n) return String.valueOf(n);
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private String normalized(String value) {
        String v = normalizedUnassigned(value);
        return v == null || "UNASSIGNED".equalsIgnoreCase(v) ? null : v;
    }

    private String normalizedUnassigned(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safe(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private String name() { return "redmine-connector-runtime"; }
}
