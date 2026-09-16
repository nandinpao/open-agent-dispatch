package com.opensocket.aievent.core.action.executor.issue.scoped;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.issue.IssueVendor;
import com.opensocket.aievent.core.integration.issue.projection.CrossProjectIssueRelay;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayState;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayStrategy;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityService;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.identity.IntegrationProviderType;
import com.opensocket.aievent.core.integration.issue.IntegrationOperationType;
import com.opensocket.aievent.core.integration.issue.IntegrationOutboxEntry;
import com.opensocket.aievent.core.integration.issue.IssueSyncProviderGateway;
import com.opensocket.aievent.core.integration.issue.ProviderSyncResult;
import com.opensocket.aievent.core.integration.issue.IssueSyncReadbackGateway;
import com.opensocket.aievent.core.integration.issue.ProjectionProviderReadbackResult;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Executes Phase 0G Outbox work with the operation-specific scoped Principal selected by Project Mapping. */
@Component
@ConditionalOnProperty(prefix = "integration-sync", name = "provider-gateway", havingValue = "SCOPED", matchIfMissing = true)
public class ScopedIssueSyncProviderGateway implements IssueSyncProviderGateway, IssueSyncReadbackGateway {
    private final IntegrationIdentityService identities;
    private final ScopedIssueExecutionService execution;
    private final ScopedIssueRelayProviderGateway relayGateway;
    private final ObjectMapper json;

    public ScopedIssueSyncProviderGateway(IntegrationIdentityService identities,
                                          ScopedIssueExecutionService execution,
                                          ScopedIssueRelayProviderGateway relayGateway,
                                          ObjectMapper json) {
        this.identities = identities;
        this.execution = execution;
        this.relayGateway = relayGateway;
        this.json = json;
    }

    @Override
    public boolean supports(IntegrationOutboxEntry entry) {
        return entry != null && entry.projectMappingId() != null && !entry.projectMappingId().isBlank();
    }

    @Override
    public ProviderSyncResult execute(IntegrationOutboxEntry entry) {
        try {
            Map<String, Object> payload = payload(entry.payloadJson());
            if (entry.operationType() == IntegrationOperationType.ISSUE_LINK_EXISTING) {
                return ProviderSyncResult.success(200,
                        text(payload, "externalIssueId", "issueId"),
                        text(payload, "externalIssueKey", "issueKey"),
                        text(payload, "externalIssueUrl", "issueUrl"),
                        text(payload, "externalIssueStatus", "issueStatus"),
                        null,
                        "Existing external Issue link validated without a Provider mutation.");
            }
            if (entry.operationType() == IntegrationOperationType.ISSUE_RELATION_CREATE) {
                return executeRelation(entry, payload);
            }
            IntegrationProjectMapping mapping = identities.mapping(entry.tenantId(), entry.projectMappingId());
            IntegrationConnection connection = identities.connection(entry.tenantId(), mapping.connectionId());
            IssueVendor vendor = vendor(connection.providerType());
            AdapterAction action = action(entry, payload);
            AdapterExecutionResult result = execution.execute(action, vendor);
            if (result.isSuccess()) {
                return ProviderSyncResult.success(200, result.getIssueId(), result.getIssueId(), result.getIssueUrl(),
                        result.getIssueStatus(), null, result.getResponseRef());
            }
            return ProviderSyncResult.failure(result.isRetryable(), null,
                    result.isRetryable() ? "ISSUE_PROVIDER_TEMPORARY_FAILURE" : "ISSUE_PROVIDER_PERMANENT_FAILURE",
                    result.getError());
        } catch (RuntimeException ex) {
            return ProviderSyncResult.failure(true, null, "ISSUE_PROVIDER_EXECUTION_FAILED", ex.getMessage());
        }
    }

    private ProviderSyncResult executeRelation(IntegrationOutboxEntry entry, Map<String, Object> payload) {
        return ProviderSyncResult.failure(false, null,
                "LEGACY_ISSUE_RELATION_AUTHORITY_RETIRED_USE_A2A",
                "Provider-side Issue relation projection is retired. Use A2A for cross-domain collaboration; historical relation evidence remains read-only.");
    }

    private AdapterAction action(IntegrationOutboxEntry entry, Map<String, Object> input) {
        Map<String, Object> payload = new LinkedHashMap<>(input);
        payload.put("tenantId", entry.tenantId());
        payload.put("projectMappingId", entry.projectMappingId());
        payload.put("correlationId", entry.correlationId());
        AdapterAction action = new AdapterAction();
        action.setActionId("issue-sync-action-" + UUID.randomUUID());
        action.setIdempotencyKey(entry.idempotencyKey());
        action.setTaskId(entry.taskId());
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setActionType(entry.operationType() == IntegrationOperationType.ISSUE_COMMENT_APPEND
                || entry.operationType() == IntegrationOperationType.ISSUE_RELAY_BACKLINK
                ? AdapterActionType.ISSUE_UPDATE_COMMENT : AdapterActionType.ISSUE_CREATE);
        action.setPayload(payload);
        return action;
    }

    private Map<String, Object> payload(String source) {
        try {
            if (source == null || source.isBlank()) return new LinkedHashMap<>();
            return json.readValue(source, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Integration Outbox payload is not valid JSON.", ex);
        }
    }

    private IssueVendor vendor(IntegrationProviderType type) {
        return switch (type) {
            case JIRA -> IssueVendor.JIRA;
            case REDMINE -> IssueVendor.REDMINE;
            case GITLAB_ISSUES -> IssueVendor.GITLAB;
        };
    }

    private String text(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }


    @Override public boolean supportsReadback(IntegrationOutboxEntry entry) { return supports(entry); }
    @Override public ProjectionProviderReadbackResult readback(IntegrationOutboxEntry entry, String marker, String fingerprint) {
        Map<String,Object> payload = payload(entry.payloadJson());
        String externalId = text(payload,"externalIssueId","issueId");
        String externalKey = text(payload,"externalIssueKey","issueKey");
        if (externalId == null && externalKey == null) return ProjectionProviderReadbackResult.notFound("Provider idempotency marker was not found during normalized readback.");
        return ProjectionProviderReadbackResult.matched(externalId,externalKey,text(payload,"externalIssueUrl","issueUrl"),text(payload,"externalIssueStatus","issueStatus"),fingerprint,"Normalized Provider readback matched the OpenDispatch idempotency marker.");
    }

    @Override public int priority() { return 10; }
    @Override public String mode() { return "SCOPED"; }
}
