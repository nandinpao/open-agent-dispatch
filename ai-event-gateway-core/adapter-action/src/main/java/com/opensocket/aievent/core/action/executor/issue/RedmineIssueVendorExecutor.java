package com.opensocket.aievent.core.action.executor.issue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;
import com.opensocket.aievent.core.issuetracking.connector.IssueCommentCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueConnector;
import com.opensocket.aievent.core.issuetracking.connector.IssueConnectorOperation;
import com.opensocket.aievent.core.issuetracking.connector.IssueConnectorResult;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderFailureCode;
import com.opensocket.aievent.core.issuetracking.connector.IssueProviderHealthImpact;
import com.opensocket.aievent.core.issuetracking.connector.IssueCreateCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueReadCommand;
import com.opensocket.aievent.core.issuetracking.connector.IssueUpdateCommand;
import tools.jackson.databind.ObjectMapper;

/**
 * Redmine-only thin Issue connector.
 *
 * <p>The typed {@link IssueConnector} methods are the I0-B contract. The legacy
 * {@link #execute(IssueExecutorRequest)} entry point is retained as a compatibility bridge while
 * later batches cut the runtime over from operation-principal authorization.</p>
 */
public class RedmineIssueVendorExecutor extends AbstractHttpIssueVendorExecutor implements IssueConnector {
    private static final Logger log = LoggerFactory.getLogger(RedmineIssueVendorExecutor.class);
    private static final Set<String> FORBIDDEN_PROXY_KEYS = Set.of(
            "method", "httpmethod", "path", "url", "endpoint", "headers",
            "apikey", "api_key", "token", "password", "credential", "credentialid",
            "secret", "secretref", "principal", "principalid");

    private final AdapterActionExecutionProperties.Redmine properties;
    private final Duration timeout;

    public RedmineIssueVendorExecutor(AdapterActionExecutionProperties.Redmine properties,
                                      String executorName,
                                      ObjectMapper mapper,
                                      Duration timeout) {
        super(executorName, mapper, timeout);
        this.properties = properties;
        this.timeout = AdapterSecretRedactor.safeHttpTimeout(timeout, Duration.ofSeconds(30));
    }

    @Override
    public IssueVendor vendor() { return IssueVendor.REDMINE; }

    @Override
    public String provider() { return IssueVendor.REDMINE.name(); }

    public boolean enabled() { return properties != null && properties.isEnabled(); }

    /**
     * Compatibility bridge for existing AdapterAction execution. New I0 runtime code should use the
     * typed IssueConnector methods and must never accept raw HTTP/provider proxy fields.
     */
    @Override
    public IssueExecutorResponse execute(IssueExecutorRequest request) {
        if (!enabled()) return failed("Redmine connector is disabled");
        if (properties.getBaseUrl().isBlank()) return failed("Redmine base URL is required by the resolved Integration Connection");
        if (properties.getApiKey().isBlank()) return failed("Redmine credential is required by the resolved Integration Connection");
        String proxyViolation = forbiddenProxyField(request == null ? null : request.getPayload());
        if (proxyViolation != null) return failed("Generic provider proxy field is not allowed: " + proxyViolation);
        if (request == null || request.getActionType() == null || request.getActionType().isBlank()) {
            return failed("Issue connector actionType is required");
        }
        try {
            return switch (request.getActionType().trim().toUpperCase(Locale.ROOT)) {
                case "ISSUE_READ" -> toLegacy(read(new IssueReadCommand(requiredIssueId(request))));
                case "ISSUE_CREATE" -> toLegacy(create(toCreateCommand(request)));
                case "ISSUE_COMMENT", "ISSUE_UPDATE_COMMENT" -> toLegacy(comment(new IssueCommentCommand(
                        requiredIssueId(request), comment(request), idempotencyKey(request))));
                case "ISSUE_UPDATE" -> toLegacy(update(toUpdateCommand(request)));
                default -> failed("Unsupported Redmine Issue connector operation: " + request.getActionType());
            };
        } catch (IllegalArgumentException ex) {
            return failed(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        } catch (Exception ex) {
            return failedRetryable(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        }
    }

    @Override
    public IssueConnectorResult read(IssueReadCommand command) {
        if (!ready()) return configurationFailure(IssueConnectorOperation.READ);
        try {
            var response = sendGet(joinUrl(properties.getBaseUrl(), "/issues/" + encode(command.issueId()) + ".json"),
                    Map.of("X-Redmine-API-Key", properties.getApiKey()), timeout);
            if (!ok(response)) return connectorHttpFailure(IssueConnectorOperation.READ, "Redmine read issue", response.statusCode(), response.body());
            Map<String, Object> parsed = parseJson(response.body());
            Map<?, ?> issue = parsed.get("issue") instanceof Map<?, ?> m ? m : Map.of();
            String issueId = issue.get("id") == null ? command.issueId() : String.valueOf(issue.get("id"));
            String status = nestedName(issue.get("status"));
            return IssueConnectorResult.success(IssueConnectorOperation.READ, provider(), issueId, issueUrl(issueId),
                    status, "redmine-read:" + issueId, response.statusCode());
        } catch (Exception ex) {
            return transportFailure(IssueConnectorOperation.READ, ex);
        }
    }

    @Override
    public IssueConnectorResult create(IssueCreateCommand command) {
        if (!ready()) return configurationFailure(IssueConnectorOperation.CREATE);
        if (properties.getProjectId() == null || properties.getProjectId().isBlank()) {
            return IssueConnectorResult.failure(IssueConnectorOperation.CREATE, provider(), null, false,
                    IssueProviderFailureCode.ISSUE_PROVIDER_CONFIGURATION_INVALID,
                    IssueProviderHealthImpact.NONE,
                    "Redmine project is required by the resolved Project Mapping");
        }
        try {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("project_id", properties.getProjectId());
            issue.put("subject", command.subject());
            if (command.description() != null) issue.put("description", withIdempotencyMarker(command.description(), command.idempotencyKey(), null));
            if (properties.getTrackerId() != null && !properties.getTrackerId().isBlank()) issue.put("tracker_id", parseReference(properties.getTrackerId()));
            if (command.priorityId() != null) issue.put("priority_id", parseReference(command.priorityId()));
            applyCreateProviderFields(issue, command.providerFields());
            log.info("issue_provider_http_request_started provider=REDMINE operation=CREATE method=POST path=/issues.json projectId={} trackerId={} idempotencyKeyPresent={}",
                    properties.getProjectId(), properties.getTrackerId(), command.idempotencyKey() != null && !command.idempotencyKey().isBlank());
            var response = sendJson("POST", joinUrl(properties.getBaseUrl(), "/issues.json"), writeJson(map("issue", issue)),
                    headersWithIdempotencyKey(command.idempotencyKey(), Map.of("X-Redmine-API-Key", properties.getApiKey())), timeout);
            log.info("issue_provider_http_response_received provider=REDMINE operation=CREATE statusCode={} success={} responseBodyPresent={}",
                    response.statusCode(), ok(response), response.body() != null && !response.body().isBlank());
            if (!ok(response)) return connectorHttpFailure(IssueConnectorOperation.CREATE, "Redmine create issue", response.statusCode(), response.body());
            Map<String, Object> parsed = parseJson(response.body());
            Map<?, ?> issueNode = parsed.get("issue") instanceof Map<?, ?> m ? m : Map.of();
            String issueId = issueNode.get("id") == null ? null : String.valueOf(issueNode.get("id"));
            if (issueId == null || issueId.isBlank()) {
                return IssueConnectorResult.uncertain(IssueConnectorOperation.CREATE, provider(), response.statusCode(),
                        IssueProviderHealthImpact.HEALTHY,
                        IssueProviderFailureCode.ISSUE_PROVIDER_OUTCOME_UNCERTAIN.name()
                                + ": Redmine returned success for CREATE but the response did not identify issue.id. "
                                + "Inspect Redmine and reconcile before retrying.");
            }
            return IssueConnectorResult.success(IssueConnectorOperation.CREATE, provider(), issueId, issueUrl(issueId),
                    nestedName(issueNode.get("status")), "redmine:" + issueId, response.statusCode());
        } catch (Exception ex) {
            return transportFailure(IssueConnectorOperation.CREATE, ex);
        }
    }

    /** Resolve Redmine's configured default issue priority without hard-coding a provider id. */
    public String resolveDefaultPriorityId() {
        if (!ready()) return null;
        try {
            var response = sendGet(joinUrl(properties.getBaseUrl(), "/enumerations/issue_priorities.json"),
                    Map.of("X-Redmine-API-Key", properties.getApiKey()), timeout);
            if (!ok(response)) return null;
            Map<String,Object> parsed = parseJson(response.body());
            Object raw = parsed.get("issue_priorities");
            if (!(raw instanceof java.util.List<?> values) || values.isEmpty()) return null;
            String first = null;
            for (Object value : values) {
                if (!(value instanceof Map<?,?> row)) continue;
                Object id = row.get("id");
                if (id == null) continue;
                String candidate = String.valueOf(id);
                if (first == null) first = candidate;
                if (Boolean.TRUE.equals(row.get("is_default"))) return candidate;
            }
            return first;
        } catch (Exception ex) {
            return null;
        }
    }

    private void applyCreateProviderFields(Map<String,Object> issue, Map<String,Object> providerFields) {
        if (providerFields == null || providerFields.isEmpty()) return;
        var custom = new java.util.ArrayList<Map<String,Object>>();
        for (var entry : providerFields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) continue;
            if (key.startsWith("custom_field:")) {
                String id = key.substring("custom_field:".length()).trim();
                if (!id.isBlank()) custom.add(Map.of("id", parseReference(id), "value", value));
                continue;
            }
            // Controlled create fields: project/tracker/subject/description/priority remain authoritative elsewhere.
            if (java.util.Set.of("assigned_to_id","category_id","fixed_version_id","start_date","due_date",
                    "estimated_hours","done_ratio","is_private").contains(key)) {
                issue.put(key, value);
            }
        }
        if (!custom.isEmpty()) issue.put("custom_fields", custom);
    }

    @Override
    public IssueConnectorResult comment(IssueCommentCommand command) {
        if (!ready()) return configurationFailure(IssueConnectorOperation.COMMENT);
        try {
            String notes = withIdempotencyMarker(command.comment(), command.idempotencyKey(), null);
            log.info("issue_provider_http_request_started provider=REDMINE operation=COMMENT method=PUT path=/issues/{issueId}.json issueId={} idempotencyKeyPresent={}",
                    command.issueId(), command.idempotencyKey() != null && !command.idempotencyKey().isBlank());
            var response = sendJson("PUT", joinUrl(properties.getBaseUrl(), "/issues/" + encode(command.issueId()) + ".json"),
                    writeJson(map("issue", map("notes", notes))),
                    headersWithIdempotencyKey(command.idempotencyKey(), Map.of("X-Redmine-API-Key", properties.getApiKey())), timeout);
            log.info("issue_provider_http_response_received provider=REDMINE operation=COMMENT issueId={} statusCode={} success={} responseBodyPresent={}",
                    command.issueId(), response.statusCode(), ok(response), response.body() != null && !response.body().isBlank());
            if (!ok(response)) return connectorHttpFailure(IssueConnectorOperation.COMMENT, "Redmine comment issue", response.statusCode(), response.body());
            return IssueConnectorResult.success(IssueConnectorOperation.COMMENT, provider(), command.issueId(), issueUrl(command.issueId()),
                    "comment_synced", "redmine-comment:" + command.issueId(), response.statusCode());
        } catch (Exception ex) {
            return transportFailure(IssueConnectorOperation.COMMENT, ex);
        }
    }

    @Override
    public IssueConnectorResult update(IssueUpdateCommand command) {
        if (!ready()) return configurationFailure(IssueConnectorOperation.UPDATE);
        try {
            Map<String, Object> issue = new LinkedHashMap<>();
            if (command.subject() != null) issue.put("subject", command.subject());
            if (command.description() != null) issue.put("description", command.description());
            if (command.statusId() != null) issue.put("status_id", parseReference(command.statusId()));
            if (command.priorityId() != null) issue.put("priority_id", parseReference(command.priorityId()));
            if (command.assigneeId() != null) issue.put("assigned_to_id", parseReference(command.assigneeId()));
            log.info("issue_provider_http_request_started provider=REDMINE operation=UPDATE method=PUT path=/issues/{issueId}.json issueId={} fieldCount={} idempotencyKeyPresent={}",
                    command.issueId(), issue.size(), command.idempotencyKey() != null && !command.idempotencyKey().isBlank());
            var response = sendJson("PUT", joinUrl(properties.getBaseUrl(), "/issues/" + encode(command.issueId()) + ".json"),
                    writeJson(map("issue", issue)),
                    headersWithIdempotencyKey(command.idempotencyKey(), Map.of("X-Redmine-API-Key", properties.getApiKey())), timeout);
            log.info("issue_provider_http_response_received provider=REDMINE operation=UPDATE issueId={} statusCode={} success={} responseBodyPresent={}",
                    command.issueId(), response.statusCode(), ok(response), response.body() != null && !response.body().isBlank());
            if (!ok(response)) return connectorHttpFailure(IssueConnectorOperation.UPDATE, "Redmine update issue", response.statusCode(), response.body());
            return IssueConnectorResult.success(IssueConnectorOperation.UPDATE, provider(), command.issueId(), issueUrl(command.issueId()),
                    "updated", "redmine-update:" + command.issueId(), response.statusCode());
        } catch (Exception ex) {
            return transportFailure(IssueConnectorOperation.UPDATE, ex);
        }
    }

    private IssueCreateCommand toCreateCommand(IssueExecutorRequest request) {
        String priority = redminePriorityReference(request);
        return new IssueCreateCommand(
                title(request),
                description(request),
                priority,
                idempotencyKey(request));
    }

    private IssueUpdateCommand toUpdateCommand(IssueExecutorRequest request) {
        Map<String, Object> payload = request.getPayload();
        return new IssueUpdateCommand(
                requiredIssueId(request),
                text(payload, "subject", "issueTitle", "title"),
                text(payload, "description", "issueDescription"),
                text(payload, "statusId", "status_id"),
                text(payload, "priorityId", "priority_id"),
                text(payload, "assigneeId", "assignedToId", "assigned_to_id"),
                idempotencyKey(request));
    }

    private String requiredIssueId(IssueExecutorRequest request) {
        String issueId = linkedIssueId(request);
        if (issueId == null || issueId.isBlank()) throw new IllegalArgumentException("issueId is required for Redmine Issue operation");
        return issueId;
    }

    private String forbiddenProxyField(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        for (String key : payload.keySet()) {
            if (key != null && FORBIDDEN_PROXY_KEYS.contains(key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT))) return key;
        }
        return null;
    }

    private boolean ready() {
        return enabled() && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private IssueConnectorResult configurationFailure(IssueConnectorOperation operation) {
        String message = !enabled() ? "Redmine connector is disabled"
                : properties.getBaseUrl() == null || properties.getBaseUrl().isBlank() ? "Redmine base URL is required by the resolved Integration Connection"
                : "Redmine credential is required by the resolved Integration Connection";
        return IssueConnectorResult.failure(operation, provider(), null, false,
                IssueProviderFailureCode.ISSUE_PROVIDER_CONFIGURATION_INVALID,
                IssueProviderHealthImpact.NONE, message);
    }

    private IssueConnectorResult connectorHttpFailure(IssueConnectorOperation operation, String label, int status, String body) {
        IssueProviderFailureCode code = failureCodeForStatus(status);
        IssueProviderHealthImpact impact = healthImpactForStatus(status);
        boolean retryable = code == IssueProviderFailureCode.ISSUE_PROVIDER_RATE_LIMITED
                || code == IssueProviderFailureCode.ISSUE_PROVIDER_TIMEOUT
                || code == IssueProviderFailureCode.ISSUE_PROVIDER_UNAVAILABLE;
        String safeBody = AdapterSecretRedactor.redactText(body);
        String message = code.name() + ": " + label + " returned " + status
                + (safeBody == null || safeBody.isBlank() ? "" : ": " + safeBody);
        if (mutating(operation) && ambiguousWriteStatus(status)) {
            return IssueConnectorResult.uncertain(operation, provider(), status,
                    IssueProviderHealthImpact.DEGRADED,
                    IssueProviderFailureCode.ISSUE_PROVIDER_OUTCOME_UNCERTAIN.name()
                            + ": Redmine may have accepted the write but OpenDispatch could not confirm the final outcome. "
                            + "Inspect Redmine before retrying. Provider response: " + status
                            + (safeBody == null || safeBody.isBlank() ? "" : ": " + safeBody));
        }
        return IssueConnectorResult.failure(operation, provider(), status, retryable, code, impact, message);
    }

    private IssueConnectorResult transportFailure(IssueConnectorOperation operation, Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        boolean timeoutFailure = ex instanceof java.net.http.HttpTimeoutException
                || ex instanceof java.net.SocketTimeoutException
                || ex instanceof java.util.concurrent.TimeoutException
                || message.toLowerCase(Locale.ROOT).contains("timeout")
                || message.toLowerCase(Locale.ROOT).contains("timed out");
        IssueProviderFailureCode code = timeoutFailure
                ? IssueProviderFailureCode.ISSUE_PROVIDER_TIMEOUT
                : IssueProviderFailureCode.ISSUE_PROVIDER_UNAVAILABLE;
        String safeMessage = AdapterSecretRedactor.redactText(message);
        if (mutating(operation)) {
            return IssueConnectorResult.uncertain(operation, provider(), null,
                    IssueProviderHealthImpact.DEGRADED,
                    IssueProviderFailureCode.ISSUE_PROVIDER_OUTCOME_UNCERTAIN.name()
                            + ": Redmine write outcome is uncertain after a transport failure. "
                            + "Do not retry blindly; inspect Redmine and reconcile first. Cause: " + safeMessage);
        }
        return IssueConnectorResult.failure(operation, provider(), null, true, code,
                IssueProviderHealthImpact.DEGRADED, code.name() + ": " + safeMessage);
    }

    private boolean mutating(IssueConnectorOperation operation) {
        return operation == IssueConnectorOperation.CREATE
                || operation == IssueConnectorOperation.COMMENT
                || operation == IssueConnectorOperation.UPDATE;
    }

    private boolean ambiguousWriteStatus(int status) {
        // 429 is explicitly safe to retry because the provider rejected/throttled the request.
        // 408/5xx can be emitted after the provider accepted a write, so treat them as uncertain.
        return status == 408 || status >= 500;
    }

    private IssueProviderFailureCode failureCodeForStatus(int status) {
        return switch (status) {
            case 401 -> IssueProviderFailureCode.ISSUE_PROVIDER_AUTHENTICATION_FAILED;
            case 403 -> IssueProviderFailureCode.ISSUE_PROVIDER_PERMISSION_DENIED;
            case 404 -> IssueProviderFailureCode.ISSUE_PROVIDER_RESOURCE_NOT_FOUND;
            case 408 -> IssueProviderFailureCode.ISSUE_PROVIDER_TIMEOUT;
            case 409 -> IssueProviderFailureCode.ISSUE_PROVIDER_CONFLICT;
            case 429 -> IssueProviderFailureCode.ISSUE_PROVIDER_RATE_LIMITED;
            case 400, 405, 406, 410, 412, 415, 422 -> IssueProviderFailureCode.ISSUE_PROVIDER_VALIDATION_FAILED;
            default -> status >= 500
                    ? IssueProviderFailureCode.ISSUE_PROVIDER_UNAVAILABLE
                    : status >= 400 && status < 500
                    ? IssueProviderFailureCode.ISSUE_PROVIDER_VALIDATION_FAILED
                    : IssueProviderFailureCode.ISSUE_PROVIDER_EXECUTION_FAILED;
        };
    }

    private IssueProviderHealthImpact healthImpactForStatus(int status) {
        return switch (status) {
            case 401, 408 -> IssueProviderHealthImpact.DEGRADED;
            case 429 -> IssueProviderHealthImpact.THROTTLED;
            case 403, 404, 409, 400, 405, 406, 410, 412, 415, 422 -> IssueProviderHealthImpact.HEALTHY;
            default -> status >= 500 ? IssueProviderHealthImpact.DEGRADED
                    : status >= 400 && status < 500 ? IssueProviderHealthImpact.HEALTHY
                    : IssueProviderHealthImpact.NONE;
        };
    }

    private IssueExecutorResponse toLegacy(IssueConnectorResult result) {
        IssueExecutorResponse response = new IssueExecutorResponse();
        response.setSuccess(result.success());
        response.setVendor(result.provider());
        response.setIssueId(result.issueId());
        response.setIssueUrl(result.issueUrl());
        response.setIssueStatus(result.issueStatus());
        response.setResponseRef(result.responseRef());
        response.setStatusCode(result.providerStatusCode());
        response.setRetryable(result.retryable());
        response.setError(result.errorMessage());
        return response;
    }

    private String redminePriorityReference(IssueExecutorRequest request) {
        String explicitPriorityId = text(request.getPayload(), "priorityId", "priority_id");
        if (explicitPriorityId != null) return explicitPriorityId;
        String severity = text(request.getPayload(), "severity", "priority", "taskPriority");
        if (severity == null) return null;
        String configuredPriority = priorityReferenceForSeverity(severity);
        if (configuredPriority == null || configuredPriority.isBlank()) return null;
        Object parsed = parseReference(configuredPriority);
        if (parsed instanceof Integer) return String.valueOf(parsed);
        Integer resolved = resolvePriorityName(configuredPriority);
        return resolved == null ? null : String.valueOf(resolved);
    }

    private String priorityReferenceForSeverity(String severity) {
        String normalized = normalize(severity);
        return switch (normalized) {
            case "CRITICAL", "FATAL", "SEV1", "P0" -> properties.getPriorityCritical();
            case "HIGH", "WARNING", "WARN", "SEV2", "P1" -> properties.getPriorityHigh();
            case "LOW", "INFO", "INFORMATION", "SEV4", "P3" -> properties.getPriorityLow();
            default -> properties.getPriorityMedium();
        };
    }

    private Integer resolvePriorityName(String priorityName) {
        try {
            var response = sendGet(joinUrl(properties.getBaseUrl(), "/enumerations/issue_priorities.json"),
                    Map.of("X-Redmine-API-Key", properties.getApiKey()), timeout);
            if (!ok(response)) return null;
            Map<String, Object> parsed = parseJson(response.body());
            Object priorities = parsed.get("issue_priorities");
            if (!(priorities instanceof List<?> list)) return null;
            String expected = normalizePriorityName(priorityName);
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Object name = map.get("name");
                Object id = map.get("id");
                if (name == null || id == null) continue;
                if (expected.equals(normalizePriorityName(String.valueOf(name)))) {
                    return id instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(id));
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Object parseReference(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return value.trim(); }
    }

    private String nestedName(Object value) {
        if (value instanceof Map<?, ?> map && map.get("name") != null) return String.valueOf(map.get("name"));
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "MIDDLE".equals(normalized) ? "MEDIUM" : normalized;
    }

    private String normalizePriorityName(String value) {
        String normalized = normalize(value).replace("_", "");
        return "MEDIUM".equals(normalized) ? "MIDDLE" : normalized;
    }

    private String issueUrl(String issueId) {
        if (!properties.getIssueUrlTemplate().isBlank()) {
            return formatIssueUrlTemplate(properties.getIssueUrlTemplate(), issueId);
        }
        return joinUrl(properties.getBaseUrl(), "/issues/" + encode(issueId));
    }

    private String formatIssueUrlTemplate(String template, String issueId) {
        String encoded = encode(issueId);
        if (template.contains("{issueId}")) return template.replace("{issueId}", encoded);
        if (template.contains("%s")) return String.format(template, encoded);
        return template.endsWith("/") ? template + encoded : template + "/" + encoded;
    }
}
