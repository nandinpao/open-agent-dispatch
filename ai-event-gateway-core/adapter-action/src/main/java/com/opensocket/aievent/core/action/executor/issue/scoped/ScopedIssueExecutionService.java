package com.opensocket.aievent.core.action.executor.issue.scoped;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.issue.IssueExecutorRequest;
import com.opensocket.aievent.core.action.executor.issue.IssueExecutorResponse;
import com.opensocket.aievent.core.action.executor.issue.IssueVendor;
import com.opensocket.aievent.core.action.executor.issue.RedmineIssueVendorExecutor;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityService;
import com.opensocket.aievent.core.integration.identity.IntegrationOperation;
import com.opensocket.aievent.core.integration.identity.IntegrationProviderType;
import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import com.opensocket.aievent.core.integration.identity.MappingResolutionRequest;
import com.opensocket.aievent.core.integration.identity.ScopedIntegrationExecutionContext;

import tools.jackson.databind.ObjectMapper;

@Service
public class ScopedIssueExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ScopedIssueExecutionService.class);

    private final IntegrationIdentityService identity;
    private final IntegrationSecretResolver secrets;
    private final ObjectMapper json;

    public ScopedIssueExecutionService(IntegrationIdentityService identity,
                                       IntegrationSecretResolver secrets,
                                       ObjectMapper json) {
        this.identity = identity;
        this.secrets = secrets;
        this.json = json;
    }

    public AdapterExecutionResult execute(AdapterAction action, IssueVendor vendor) {
        Map<String,Object> payload = action.getPayload();
        String tenant = text(payload, "tenantId");
        String mappingId = text(payload, "projectMappingId", "integrationProjectMappingId");
        String correlationId = correlation(action);
        IntegrationOperation operation = legacyOperation(action.getActionType());
        log.info("issue_provider_execution_started actionId={} taskId={} provider={} operation={} tenantId={} mappingId={} correlationId={}",
                action.getActionId(), action.getTaskId(), vendor, operation, tenant, mappingId, correlationId);

        if (tenant == null) {
            AdapterExecutionResult result = AdapterExecutionResult.permanentFailure("scoped-issue-executor", "tenantId is required for scoped Issue execution.");
            log.warn("issue_provider_execution_failed actionId={} taskId={} provider={} operation={} stage={} retryable={} error={}",
                    action.getActionId(), action.getTaskId(), vendor, operation, "TENANT_RESOLUTION", result.isRetryable(), result.getError());
            return result;
        }

        ScopedIntegrationExecutionContext context;
        try {
            if (mappingId != null) {
                context = identity.resolveExecutionContext(tenant, mappingId, operation);
            } else {
                context = identity.resolveExecutionContext(new MappingResolutionRequest(
                        tenant,
                        text(payload, "connectionId"),
                        text(payload, "ownerDepartmentId", "departmentId"),
                        text(payload, "ownerGroupId", "groupId"),
                        text(payload, "executorDomainId", "serviceDomainId"),
                        text(payload, "sourceSystemId", "sourceSystem"),
                        text(payload, "taskType")), operation);
            }
        } catch (RuntimeException ex) {
            AdapterExecutionResult result = AdapterExecutionResult.permanentFailure("scoped-issue-executor", ex.getMessage());
            log.warn("issue_provider_execution_failed actionId={} taskId={} provider={} operation={} stage={} exceptionClass={} retryable={} error={}",
                    action.getActionId(), action.getTaskId(), vendor, operation, "EXECUTION_CONTEXT", ex.getClass().getName(), result.isRetryable(), result.getError());
            return result;
        }

        log.info("issue_provider_execution_context_resolved actionId={} taskId={} provider={} operation={} connectionId={} mappingId={} externalProjectId={} principalId={} credentialId={} credentialVersion={}",
                action.getActionId(), action.getTaskId(), vendor, operation,
                context.connection().connectionId(), context.mapping().mappingId(), context.mapping().externalProjectId(),
                context.principal().principalId(), context.credential().credentialId(), context.credential().secretVersion());

        if (!matches(vendor, context.connection().providerType())) {
            AdapterExecutionResult result = AdapterExecutionResult.permanentFailure("scoped-issue-executor", "Resolved Project Mapping provider does not match requested Issue vendor.");
            enrich(result, action, context, null, vendor);
            log.warn("issue_provider_execution_failed actionId={} taskId={} provider={} operation={} stage={} retryable={} error={}",
                    action.getActionId(), action.getTaskId(), vendor, operation, "PROVIDER_MATCH", result.isRetryable(), result.getError());
            return result;
        }

        String authorizationMismatch = executionAuthorizationMismatch(payload, context);
        if (authorizationMismatch != null) {
            AdapterExecutionResult result = AdapterExecutionResult.permanentFailure("scoped-issue-executor", authorizationMismatch);
            enrich(result, action, context, null, vendor);
            log.warn("issue_provider_execution_failed actionId={} taskId={} provider={} operation={} stage={} retryable={} error={}",
                    action.getActionId(), action.getTaskId(), vendor, operation, "AUTHORITY_CONTEXT", result.isRetryable(), result.getError());
            return result;
        }

        IssueExecutorResponse response;
        try (var resolved = secrets.resolve(context.credential())) {
            String secret = resolved.reveal();
            IssueExecutorRequest request = IssueExecutorRequest.from(action, vendor);
            if (vendor == IssueVendor.REDMINE) {
                var properties = new AdapterActionExecutionProperties.Redmine();
                properties.setEnabled(true);
                properties.setBaseUrl(context.connection().baseUrl());
                properties.setApiKey(secret);
                properties.setProjectId(context.mapping().externalProjectId());
                properties.setTrackerId(context.mapping().externalTrackerId());
                response = new RedmineIssueVendorExecutor(
                        properties,
                        "redmine-scoped-principal-executor",
                        json,
                        java.time.Duration.ofMillis(context.connection().timeoutMs())).execute(request);
            } else if (vendor == IssueVendor.JIRA) {
                response = new ScopedJiraIssueVendorExecutor(json, context, secret).execute(request);
            } else {
                AdapterExecutionResult result = AdapterExecutionResult.permanentFailure(
                        "scoped-issue-executor",
                        "Scoped production execution supports Jira and Redmine in Phase 0E-1.");
                enrich(result, action, context, null, vendor);
                log.warn("issue_provider_execution_failed actionId={} taskId={} provider={} operation={} stage={} retryable={} error={}",
                        action.getActionId(), action.getTaskId(), vendor, operation, "PROVIDER_UNSUPPORTED", result.isRetryable(), result.getError());
                return result;
            }
        } catch (RuntimeException ex) {
            AdapterExecutionResult result = AdapterExecutionResult.retryableFailure(
                    "scoped-issue-executor",
                    "Scoped provider execution failed. Review resolver and provider evidence.");
            enrich(result, action, context, null, vendor);
            log.warn("issue_provider_execution_failed actionId={} taskId={} provider={} operation={} stage={} exceptionClass={} retryable={} error={}",
                    action.getActionId(), action.getTaskId(), vendor, operation, "PROVIDER_INVOCATION", ex.getClass().getName(), result.isRetryable(), result.getError());
            return result;
        }

        if (response.getStatusCode() != null && (response.getStatusCode() == 401 || response.getStatusCode() == 403)) {
            identity.recordAuthorizationFailure(tenant, context.mapping().mappingId(), operation, response.getStatusCode(), correlationId);
        }

        if (response.isSuccess()) {
            AdapterExecutionResult result = AdapterExecutionResult.success("scoped-" + vendor.name().toLowerCase() + "-executor", response.getResponseRef());
            result.setIssueVendor(vendor.name());
            result.setIssueId(response.getIssueId());
            result.setIssueUrl(response.getIssueUrl());
            result.setIssueStatus(response.getIssueStatus());
            enrich(result, action, context, response, vendor);
            log.info("issue_provider_execution_completed actionId={} taskId={} provider={} operation={} success=true providerStatusCode={} issueId={} issueUrl={} connectionId={} mappingId={} correlationId={}",
                    action.getActionId(), action.getTaskId(), vendor, operation, response.getStatusCode(), response.getIssueId(), response.getIssueUrl(),
                    context.connection().connectionId(), context.mapping().mappingId(), correlationId);
            return result;
        }

        AdapterExecutionResult result = response.isRetryable()
                ? AdapterExecutionResult.retryableFailure("scoped-" + vendor.name().toLowerCase() + "-executor", response.getError())
                : AdapterExecutionResult.permanentFailure("scoped-" + vendor.name().toLowerCase() + "-executor", response.getError());
        enrich(result, action, context, response, vendor);
        log.warn("issue_provider_execution_completed actionId={} taskId={} provider={} operation={} success=false providerStatusCode={} retryable={} error={} connectionId={} mappingId={} correlationId={}",
                action.getActionId(), action.getTaskId(), vendor, operation, response.getStatusCode(), result.isRetryable(), response.getError(),
                context.connection().connectionId(), context.mapping().mappingId(), correlationId);
        return result;
    }

    private void enrich(AdapterExecutionResult result,
                        AdapterAction action,
                        ScopedIntegrationExecutionContext context,
                        IssueExecutorResponse response,
                        IssueVendor vendor) {
        result.setTenantId(context.connection().tenantId());
        result.setCorrelationId(correlation(action));
        result.setConnectionId(context.connection().connectionId());
        result.setProjectMappingId(context.mapping().mappingId());
        result.setExternalProjectId(context.mapping().externalProjectId());
        result.setTechnicalPrincipalId(context.principal().principalId());
        result.setCredentialId(context.credential().credentialId());
        result.setCredentialVersion(context.credential().secretVersion());
        result.setIssueVendor(vendor == null ? null : vendor.name());
        result.setIdempotencyKey(action.getIdempotencyKey());
        if (response != null) result.setProviderStatusCode(response.getStatusCode());
    }

    private IntegrationOperation legacyOperation(AdapterActionType type) {
        if (type == null) return IntegrationOperation.CREATE;
        return switch (type) {
            case ISSUE_READ -> IntegrationOperation.READ;
            case ISSUE_COMMENT, ISSUE_UPDATE_COMMENT -> IntegrationOperation.COMMENT;
            case ISSUE_UPDATE -> IntegrationOperation.UPDATE;
            case ISSUE_CREATE -> IntegrationOperation.CREATE;
            default -> throw new IllegalArgumentException("Unsupported legacy scoped Issue operation: " + type);
        };
    }

    private String executionAuthorizationMismatch(Map<String,Object> payload, ScopedIntegrationExecutionContext context) {
        String attribution = text(payload, "providerAttributionId");
        if (attribution == null) return null;
        String expectedPolicy = text(payload, "expectedProviderPolicy");
        String expectedConnection = text(payload, "expectedConnectionId");
        String expectedMapping = text(payload, "expectedMappingId");
        String expectedPrincipal = text(payload, "expectedIntegrationPrincipalId");
        String expectedCredential = text(payload, "expectedCredentialId");
        String expectedVersion = text(payload, "expectedCredentialVersion");
        String expectedActor = text(payload, "expectedProviderActorId");
        if (expectedPolicy == null || expectedConnection == null || expectedMapping == null || expectedPrincipal == null
                || expectedCredential == null || expectedVersion == null) {
            return "PROVIDER_EXECUTION_AUTHORIZATION_CONTEXT_INCOMPLETE";
        }
        if (!expectedConnection.equals(context.connection().connectionId())
                || !expectedMapping.equals(context.mapping().mappingId())
                || !expectedPrincipal.equals(context.principal().principalId())
                || !expectedCredential.equals(context.credential().credentialId())
                || !expectedVersion.equals(context.credential().secretVersion())
                || !expectedPolicy.equals(context.mapping().providerWriteIdentityPolicy().name())) {
            return "PROVIDER_EXECUTION_AUTHORIZATION_CONTEXT_MISMATCH";
        }
        if (expectedActor != null && !expectedActor.isBlank()
                && !expectedActor.equals(context.principal().externalPrincipalIdentifier())) {
            return "PROVIDER_EXECUTION_ACTOR_MISMATCH";
        }
        return null;
    }

    private boolean matches(IssueVendor vendor, IntegrationProviderType provider) {
        return (vendor == IssueVendor.REDMINE && provider == IntegrationProviderType.REDMINE)
                || (vendor == IssueVendor.JIRA && provider == IntegrationProviderType.JIRA)
                || (vendor == IssueVendor.GITLAB && provider == IntegrationProviderType.GITLAB_ISSUES);
    }

    private String text(Map<String,Object> payload, String... keys) {
        if (payload == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private String correlation(AdapterAction action) {
        String value = text(action.getPayload(), "correlationId");
        return value == null ? action.getActionId() : value;
    }
}
