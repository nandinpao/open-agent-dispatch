package com.opensocket.aievent.core.action.executor.issue.scoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;
import com.opensocket.aievent.core.action.executor.issue.IssueVendor;
import com.opensocket.aievent.core.integration.identity.IntegrationAuthType;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityService;
import com.opensocket.aievent.core.integration.identity.IntegrationOperation;
import com.opensocket.aievent.core.integration.identity.IntegrationProviderType;
import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import com.opensocket.aievent.core.integration.identity.ScopedIntegrationExecutionContext;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangeProviderGateway;
import com.opensocket.aievent.core.issuetracking.change.ProviderCommentSyncCommand;
import com.opensocket.aievent.core.issuetracking.change.ProviderRelationSyncCommand;
import com.opensocket.aievent.core.issuetracking.change.ProviderSyncResult;
import com.opensocket.aievent.core.issuetracking.identity.ProviderExecutionAuthorizationRef;

import tools.jackson.databind.ObjectMapper;

/** Scoped Jira/Redmine comment and relation execution for Phase 3G. */
@Component
@Primary
public class ScopedExternalChangeProviderGateway implements ExternalChangeProviderGateway {
    private final IntegrationIdentityService identities;
    private final IntegrationSecretResolver secrets;
    private final ScopedIssueExecutionService execution;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ScopedExternalChangeProviderGateway(IntegrationIdentityService identities,
            IntegrationSecretResolver secrets,
            ScopedIssueExecutionService execution,
            ObjectMapper json) {
        this.identities = identities;
        this.secrets = secrets;
        this.execution = execution;
        this.json = json;
    }

    @Override
    public ProviderSyncResult appendComment(ProviderCommentSyncCommand command) {
        try {
            var mapping = identities.mapping(command.tenantId(), command.projectMappingId());
            var connection = identities.connection(command.tenantId(), mapping.connectionId());
            IssueVendor vendor = vendor(connection.providerType());
            AdapterAction action = new AdapterAction();
            action.setActionId("external-comment-" + UUID.randomUUID());
            action.setIdempotencyKey(command.idempotencyKey());
            action.setTaskId(command.sourceCommentId());
            action.setAdapterType(AdapterType.ISSUE_TRACKING);
            action.setActionType(AdapterActionType.ISSUE_UPDATE_COMMENT);
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", command.tenantId());
            payload.put("projectMappingId", command.projectMappingId());
            payload.put("linkedIssueId", command.externalIssueId());
            payload.put("issueComment", command.body());
            payload.put("sourceMarker", command.sourceMarker());
            payload.put("correlationId", command.correlationId());
            addExecutionAuthorization(payload, command.executionAuthorization());
            action.setPayload(payload);
            AdapterExecutionResult result = execution.execute(action, vendor);
            if (result.isSuccess()) {
                return ProviderSyncResult.success(200, result.getResponseRef(), result.getResponseRef());
            }
            return ProviderSyncResult.failure(result.isRetryable(), null,
                    "EXTERNAL_COMMENT_PROVIDER_FAILURE", result.getError());
        } catch (RuntimeException ex) {
            return ProviderSyncResult.failure(true, null,
                    "EXTERNAL_COMMENT_PROVIDER_FAILURE", safe(ex.getMessage()));
        }
    }

    @Override
    public ProviderSyncResult createRelation(ProviderRelationSyncCommand command) {
        try {
            ScopedIntegrationExecutionContext context = identities.resolveExecutionContext(
                    command.tenantId(), command.projectMappingId(), IntegrationOperation.RELATION);
            verifyExecutionAuthorization(context, command.executionAuthorization());
            try (var secret = secrets.resolve(context.credential())) {
                HttpResponse<String> response = switch (context.connection().providerType()) {
                    case JIRA -> jiraRelation(context, secret.reveal(), command);
                    case REDMINE -> redmineRelation(context, secret.reveal(), command);
                    default -> null;
                };
                if (response == null) {
                    return ProviderSyncResult.failure(false, null,
                            "PROVIDER_RELATION_UNSUPPORTED", "Provider does not support governed relation synchronization.");
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String evidence = context.connection().providerType().name().toLowerCase()
                            + ":relation:" + command.sourceExternalIssueId() + ":" + command.targetExternalIssueId();
                    return ProviderSyncResult.success(response.statusCode(), evidence, evidence);
                }
                boolean retryable = response.statusCode() == 408 || response.statusCode() == 429
                        || response.statusCode() >= 500;
                return ProviderSyncResult.failure(retryable, response.statusCode(),
                        "EXTERNAL_RELATION_PROVIDER_FAILURE", "Provider relation returned " + response.statusCode() + ".");
            }
        } catch (Exception ex) {
            return ProviderSyncResult.failure(true, null,
                    "EXTERNAL_RELATION_PROVIDER_FAILURE", safe(ex.getMessage()));
        }
    }


    private void addExecutionAuthorization(Map<String,Object> payload, ProviderExecutionAuthorizationRef authorization) {
        if (authorization == null) return;
        payload.put("providerAttributionId", authorization.attributionId());
        payload.put("expectedProviderPolicy", authorization.policy().name());
        payload.put("expectedConnectionId", authorization.connectionId());
        payload.put("expectedMappingId", authorization.mappingId());
        payload.put("expectedIntegrationPrincipalId", authorization.integrationPrincipalId());
        payload.put("expectedCredentialId", authorization.credentialId());
        payload.put("expectedCredentialVersion", authorization.credentialVersion());
        payload.put("expectedProviderActorId", authorization.providerActorId());
    }

    private void verifyExecutionAuthorization(ScopedIntegrationExecutionContext context,
            ProviderExecutionAuthorizationRef authorization) {
        if (authorization == null) return;
        if (!authorization.connectionId().equals(context.connection().connectionId())
                || !authorization.mappingId().equals(context.mapping().mappingId())
                || !authorization.integrationPrincipalId().equals(context.principal().principalId())
                || !authorization.credentialId().equals(context.credential().credentialId())
                || !authorization.credentialVersion().equals(context.credential().secretVersion())
                || authorization.policy() != context.mapping().providerWriteIdentityPolicy()) {
            throw new IllegalStateException("PROVIDER_EXECUTION_AUTHORIZATION_CONTEXT_MISMATCH");
        }
        if (!authorization.providerActorId().isBlank()
                && !authorization.providerActorId().equals(context.principal().externalPrincipalIdentifier())) {
            throw new IllegalStateException("PROVIDER_EXECUTION_ACTOR_MISMATCH");
        }
    }

    private HttpResponse<String> jiraRelation(ScopedIntegrationExecutionContext context, String secret,
            ProviderRelationSyncCommand command) throws Exception {
        Map<String,Object> body = Map.of(
                "type", Map.of("name", jiraRelationName(command.relationType())),
                "inwardIssue", Map.of("key", command.sourceExternalIssueId()),
                "outwardIssue", Map.of("key", command.targetExternalIssueId()),
                "comment", Map.of("body", command.sourceMarker()));
        return post(context, secret, "/rest/api/3/issueLink", json.writeValueAsString(body));
    }

    private HttpResponse<String> redmineRelation(ScopedIntegrationExecutionContext context, String secret,
            ProviderRelationSyncCommand command) throws Exception {
        Map<String,Object> body = Map.of("relation", Map.of(
                "issue_id", command.sourceExternalIssueId(),
                "issue_to_id", command.targetExternalIssueId(),
                "relation_type", redmineRelationType(command.relationType())));
        return post(context, secret, "/issue_relations.json", json.writeValueAsString(body));
    }

    private HttpResponse<String> post(ScopedIntegrationExecutionContext context, String secret,
            String path, String body) throws Exception {
        String base = context.connection().baseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofMillis(Math.max(1000, context.connection().timeoutMs())))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-OpenDispatch-Source", "phase3g");
        auth(context, secret).forEach(builder::header);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Map<String,String> auth(ScopedIntegrationExecutionContext context, String secret) {
        Map<String,String> headers = new LinkedHashMap<>();
        var credential = context.credential();
        var principal = context.principal();
        if ((credential.authType() == IntegrationAuthType.API_TOKEN
                || credential.authType() == IntegrationAuthType.BASIC_PASSWORD)
                && principal.externalPrincipalIdentifier() != null
                && !principal.externalPrincipalIdentifier().isBlank()) {
            headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (principal.externalPrincipalIdentifier() + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        } else {
            headers.put("Authorization", "Bearer " + secret);
        }
        if (context.connection().providerType() == IntegrationProviderType.REDMINE
                && credential.authType() == IntegrationAuthType.API_TOKEN) {
            headers.put("X-Redmine-API-Key", secret);
        }
        return headers;
    }

    private IssueVendor vendor(IntegrationProviderType type) {
        return switch (type) {
            case JIRA -> IssueVendor.JIRA;
            case REDMINE -> IssueVendor.REDMINE;
            case GITLAB_ISSUES -> IssueVendor.GITLAB;
        };
    }

    private String jiraRelationName(String relationType) {
        String value = relationType == null ? "" : relationType.trim().toUpperCase();
        return switch (value) {
            case "BLOCKS", "BLOCKED_BY" -> "Blocks";
            case "DUPLICATES", "DUPLICATED_BY" -> "Duplicate";
            default -> "Relates";
        };
    }

    private String redmineRelationType(String relationType) {
        String value = relationType == null ? "" : relationType.trim().toUpperCase();
        return switch (value) {
            case "BLOCKS" -> "blocks";
            case "BLOCKED_BY" -> "blocked";
            case "DUPLICATES" -> "duplicates";
            case "DUPLICATED_BY" -> "duplicated";
            default -> "relates";
        };
    }

    private String safe(String message) {
        if (message == null || message.isBlank()) return "Provider synchronization failed.";
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    @Override public String mode() { return "SCOPED_JIRA_REDMINE"; }
}
