package com.opensocket.aievent.core.action.executor.issue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;
import tools.jackson.databind.ObjectMapper;

public class RedmineIssueVendorExecutor extends AbstractHttpIssueVendorExecutor {
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

    public boolean enabled() { return properties != null && properties.isEnabled(); }

    @Override
    public IssueExecutorResponse execute(IssueExecutorRequest request) {
        if (!enabled()) return failed("Redmine executor is disabled");
        if (properties.getBaseUrl().isBlank()) return failed("REDMINE_EXECUTOR_BASE_URL is required");
        if (properties.getApiKey().isBlank()) return failed("REDMINE_EXECUTOR_API_KEY is required");
        try {
            if ("ISSUE_UPDATE_COMMENT".equalsIgnoreCase(request.getActionType())) {
                return updateComment(request);
            }
            return createIssue(request);
        } catch (IllegalArgumentException ex) {
            return failed(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        } catch (Exception ex) {
            return failedRetryable(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        }
    }

    private IssueExecutorResponse createIssue(IssueExecutorRequest request) throws Exception {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("project_id", firstNonBlank(text(request.getPayload(), "projectId", "project_id"), properties.getProjectId()));
        issue.put("subject", title(request));
        issue.put("description", description(request));
        if (!properties.getTrackerId().isBlank()) issue.put("tracker_id", parseTrackerId(properties.getTrackerId()));
        Object priorityId = redminePriorityId(request);
        if (priorityId != null) issue.put("priority_id", priorityId);
        Map<String, Object> body = map("issue", issue);
        var response = sendJson("POST", joinUrl(properties.getBaseUrl(), "/issues.json"), writeJson(body),
                headersWithIdempotency(request, Map.of("X-Redmine-API-Key", properties.getApiKey())), timeout);
        if (!ok(response)) return httpFailure("Redmine create issue", response.statusCode(), response.body());
        Map<String, Object> parsed;
        try {
            parsed = parseJson(response.body());
        } catch (Exception ex) {
            return failed("Redmine create issue response is not valid JSON: " + (ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()));
        }
        Map<?, ?> issueNode = parsed.get("issue") instanceof Map<?, ?> m ? m : Map.of();
        String issueId = issueNode.get("id") == null ? null : String.valueOf(issueNode.get("id"));
        if (issueId == null || issueId.isBlank()) return failed("Redmine create issue response missing issue.id");
        IssueExecutorResponse result = new IssueExecutorResponse();
        result.setSuccess(true);
        result.setVendor(vendor().name());
        result.setIssueId(issueId);
        result.setIssueUrl(issueUrl(issueId));
        result.setIssueStatus("open");
        result.setResponseRef("redmine:" + issueId);
        return result;
    }

    private IssueExecutorResponse updateComment(IssueExecutorRequest request) throws Exception {
        String issueId = linkedIssueId(request);
        if (issueId == null || issueId.isBlank()) return failed("linkedIssueId is required for Redmine comment update");
        Map<String, Object> body = map("issue", map("notes", comment(request)));
        var response = sendJson("PUT", joinUrl(properties.getBaseUrl(), "/issues/" + encode(issueId) + ".json"), writeJson(body),
                headersWithIdempotency(request, Map.of("X-Redmine-API-Key", properties.getApiKey())), timeout);
        if (!ok(response)) return httpFailure("Redmine update issue", response.statusCode(), response.body());
        IssueExecutorResponse result = new IssueExecutorResponse();
        result.setSuccess(true);
        result.setVendor(vendor().name());
        result.setIssueId(issueId);
        result.setIssueUrl(issueUrl(issueId));
        result.setIssueStatus("comment_synced");
        result.setResponseRef("redmine-comment:" + issueId);
        return result;
    }

    private Object redminePriorityId(IssueExecutorRequest request) {
        String severity = firstNonBlank(text(request.getPayload(), "severity", "priority", "taskPriority"), "MEDIUM");
        String configuredPriority = priorityReferenceForSeverity(severity);
        if (configuredPriority == null || configuredPriority.isBlank()) return null;
        Object parsed = parseTrackerId(configuredPriority);
        if (parsed instanceof Integer) return parsed;
        return resolvePriorityName(configuredPriority);
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

    private Object parseTrackerId(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return value; }
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
