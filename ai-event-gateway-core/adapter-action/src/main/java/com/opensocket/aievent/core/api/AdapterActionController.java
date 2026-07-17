package com.opensocket.aievent.core.api;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionProperties;
import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionService;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionSummary;
import com.opensocket.aievent.core.action.executor.AdapterExecutorCircuitBreaker;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;

@RestController
@RequestMapping("/api/adapter-actions")
public class AdapterActionController {
    private final AdapterActionFacade service;
    private final AdapterActionProperties properties;
    private final AdapterActionExecutionService executionService;
    private final AdapterActionExecutionProperties executionProperties;
    private final AdapterExecutorCircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;
    private final HttpClient redmineHttpClient;

    public AdapterActionController(AdapterActionFacade service,
                                   AdapterActionProperties properties,
                                   AdapterActionExecutionService executionService,
                                   AdapterActionExecutionProperties executionProperties,
                                   AdapterExecutorCircuitBreaker circuitBreaker,
                                   ObjectMapper objectMapper) {
        this.service = service;
        this.properties = properties;
        this.executionService = executionService;
        this.executionProperties = executionProperties;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        this.redmineHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @GetMapping
    public List<AdapterAction> recent(@RequestParam(defaultValue = "100") int limit) {
        return service.recent(limit);
    }

    @GetMapping("/{actionId}")
    public AdapterAction get(@PathVariable String actionId) {
        return service.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
    }

    @GetMapping("/incident/{incidentId}")
    public List<AdapterAction> byIncident(@PathVariable String incidentId, @RequestParam(defaultValue = "100") int limit) {
        return service.byIncident(incidentId, limit);
    }

    @GetMapping("/task/{taskId}")
    public List<AdapterAction> byTask(@PathVariable String taskId, @RequestParam(defaultValue = "100") int limit) {
        return service.byTask(taskId, limit);
    }

    @GetMapping("/status/{status}")
    public List<AdapterAction> byStatus(@PathVariable AdapterActionStatus status, @RequestParam(defaultValue = "100") int limit) {
        return service.byStatus(status, limit);
    }

    @PostMapping("/{actionId}/complete")
    public AdapterAction complete(@PathVariable String actionId, @RequestBody(required = false) Map<String, String> body) {
        return service.markCompleted(actionId, body == null ? null : body.get("responseRef"));
    }

    @PostMapping("/{actionId}/fail")
    public AdapterAction fail(@PathVariable String actionId, @RequestBody(required = false) Map<String, String> body) {
        return service.markFailed(actionId, body == null ? null : body.get("error"));
    }

    @PostMapping("/{actionId}/execute")
    public AdapterAction execute(@PathVariable String actionId) {
        return executionService.execute(actionId);
    }

    @PostMapping("/execute-pending")
    public AdapterActionExecutionSummary executePending(@RequestParam(defaultValue = "50") int limit) {
        return executionService.executePending(limit);
    }

    @PostMapping("/{actionId}/retry")
    public AdapterAction retry(@PathVariable String actionId,
                               @RequestBody(required = false) RetryRequest body) {
        boolean resetAttempts = body != null && Boolean.TRUE.equals(body.resetAttempts());
        String reason = body == null ? null : body.reason();
        return service.retryForWorker(actionId, reason, resetAttempts);
    }

    @PostMapping("/{actionId}/execute-retry")
    public AdapterAction executeRetry(@PathVariable String actionId) {
        return executionService.retry(actionId);
    }

    @PostMapping("/{actionId}/cancel")
    public AdapterAction cancel(@PathVariable String actionId, @RequestBody(required = false) Map<String, String> body) {
        return service.cancel(actionId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/recover-expired-leases")
    public List<AdapterAction> recoverExpiredLeases(@RequestParam(defaultValue = "100") int limit) {
        return service.recoverExpiredWorkerLeases(limit);
    }

    @GetMapping("/{actionId}/audit")
    public List<AdapterExecutorAuditRecord> auditByAction(@PathVariable String actionId, @RequestParam(defaultValue = "100") int limit) {
        return service.auditByAction(actionId, limit);
    }

    @GetMapping("/executor-audit")
    public List<AdapterExecutorAuditRecord> executorAudit(@RequestParam(defaultValue = "100") int limit) {
        return service.recentExecutorAudit(limit);
    }

    @GetMapping("/issue-tracking/redmine/diagnostics")
    public Map<String, Object> redmineDiagnostics() {
        AdapterActionExecutionProperties.Redmine redmine = executionProperties.getIssue().getRedmine();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("provider", "REDMINE");
        map.put("issueActionEnabled", properties.getIssue().isEnabled());
        map.put("executorEnabled", executionProperties.isEnabled());
        map.put("executorMode", executionProperties.getMode());
        map.put("executorAutoExecutePending", executionProperties.isAutoExecutePending());
        map.put("defaultVendor", executionProperties.getIssue().getDefaultVendor());
        map.put("redmineExecutorEnabled", redmine.isEnabled());
        map.put("baseUrlConfigured", !blank(redmine.getBaseUrl()));
        map.put("apiKeyConfigured", !blank(redmine.getApiKey()));
        map.put("projectIdConfigured", !blank(redmine.getProjectId()));
        map.put("trackerIdConfigured", !blank(redmine.getTrackerId()));
        map.put("issueUrlTemplateConfigured", !blank(redmine.getIssueUrlTemplate()));
        map.put("priorityMapping", Map.of(
                "CRITICAL", redmine.getPriorityCritical(),
                "HIGH", redmine.getPriorityHigh(),
                "MEDIUM", redmine.getPriorityMedium(),
                "LOW", redmine.getPriorityLow()));
        map.put("configuredProjectId", redactConfigValue(redmine.getProjectId()));
        map.put("configuredTrackerId", redactConfigValue(redmine.getTrackerId()));
        map.put("configuredBaseUrl", redactBaseUrl(redmine.getBaseUrl()));
        map.put("ready", properties.getIssue().isEnabled()
                && executionProperties.isEnabled()
                && "REDMINE".equalsIgnoreCase(executionProperties.getIssue().getDefaultVendor())
                && redmine.isEnabled()
                && !blank(redmine.getBaseUrl())
                && !blank(redmine.getApiKey())
                && !blank(redmine.getProjectId()));
        map.put("checkedAt", Instant.now().toString());
        return map;
    }

    @PostMapping("/issue-tracking/redmine/test-connection")
    public Map<String, Object> redmineTestConnection() {
        Map<String, Object> map = redmineDiagnostics();
        map.put("operation", "TEST_CONNECTION");
        List<Map<String, Object>> checks = new ArrayList<>();
        map.put("checks", checks);

        AdapterActionExecutionProperties.Redmine redmine = executionProperties.getIssue().getRedmine();
        if (!redmine.isEnabled() || blank(redmine.getBaseUrl()) || blank(redmine.getApiKey())) {
            map.put("ok", false);
            map.put("message", "Redmine executor must be enabled and configured with baseUrl/apiKey before testing the connection.");
            return map;
        }

        RedmineHttpResult projects = redmineGet("/projects.json?limit=100");
        checks.add(checkRow("projects", projects));
        if (projects.ok()) map.put("projects", redmineCollection(projects.body(), "projects"));

        RedmineHttpResult trackers = redmineGet("/trackers.json");
        checks.add(checkRow("trackers", trackers));
        if (trackers.ok()) map.put("trackers", redmineCollection(trackers.body(), "trackers"));

        boolean ok = projects.ok() && trackers.ok();
        map.put("ok", ok);
        map.put("message", ok ? "Redmine connection is reachable and API key can read projects/trackers." : "Redmine connection test failed. Check base URL, API key, network, Redmine project permissions, and tracker access.");
        return map;
    }

    @GetMapping("/issue-tracking/redmine/projects")
    public Map<String, Object> redmineProjects() {
        return redmineReadCollection("projects", "/projects.json?limit=100");
    }

    @GetMapping("/issue-tracking/redmine/trackers")
    public Map<String, Object> redmineTrackers() {
        return redmineReadCollection("trackers", "/trackers.json");
    }

    @PostMapping("/issue-tracking/redmine/test-issue")
    public Map<String, Object> redmineTestIssue(@RequestBody(required = false) RedmineTestIssueRequest body) {
        RedmineTestIssueRequest request = body == null ? new RedmineTestIssueRequest(null, null, null, null, null, null, null, null, null, null) : body;
        AdapterActionExecutionProperties.Redmine redmine = executionProperties.getIssue().getRedmine();
        Map<String, Object> map = redmineDiagnostics();
        map.put("operation", "CREATE_TEST_ISSUE");

        if (!redmine.isEnabled() || blank(redmine.getBaseUrl()) || blank(redmine.getApiKey())) {
            map.put("ok", false);
            map.put("message", "Redmine executor must be enabled and configured with baseUrl/apiKey before creating a test issue.");
            return map;
        }

        String projectId = firstNonBlank(request.projectId(), redmine.getProjectId());
        if (blank(projectId)) {
            map.put("ok", false);
            map.put("message", "Redmine projectId is required. Configure REDMINE_EXECUTOR_PROJECT_ID or choose a project in Admin UI.");
            return map;
        }

        String subject = firstNonBlank(request.subject(), redmineTestIssueSubject(request));
        String description = firstNonBlank(request.description(), redmineTestIssueDescription(request));
        String trackerId = firstNonBlank(request.trackerId(), redmine.getTrackerId());

        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("project_id", projectId);
        issue.put("subject", subject);
        issue.put("description", description);
        if (!blank(trackerId)) issue.put("tracker_id", parseNumberOrString(trackerId));
        Object priorityId = firstNonBlank(request.priorityId(), "").isBlank()
                ? resolveRedminePriorityId(redmine, request.severity())
                : parseNumberOrString(request.priorityId());
        if (priorityId != null && !String.valueOf(priorityId).isBlank()) issue.put("priority_id", priorityId);
        Map<String, Object> requestBody = Map.of("issue", issue);

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            RedmineHttpResult result = redmineSend("POST", "/issues.json", json);
            map.put("httpStatus", result.statusCode());
            map.put("ok", result.ok());
            if (!result.ok()) {
                map.put("message", "Redmine test issue create failed with HTTP " + result.statusCode() + ".");
                map.put("error", truncate(result.body()));
                return map;
            }
            Map<String, Object> parsed = parseJsonObject(result.body());
            Object issueNode = parsed.get("issue");
            Object issueId = issueNode instanceof Map<?, ?> m ? m.get("id") : null;
            map.put("message", "Redmine test issue created successfully.");
            map.put("issueId", issueId == null ? null : String.valueOf(issueId));
            map.put("issueUrl", issueId == null ? null : issueUrl(redmine, String.valueOf(issueId)));
            map.put("response", sanitizeRedmineIssueResponse(parsed));
            return map;
        } catch (Exception ex) {
            map.put("ok", false);
            map.put("message", "Redmine test issue create failed: " + safeMessage(ex));
            return map;
        }
    }

    public record RetryRequest(String reason, Boolean resetAttempts) {}

    public record RedmineTestIssueRequest(
            String projectId,
            String trackerId,
            String subject,
            String description,
            String severity,
            String message,
            String priorityId,
            String objectId,
            String eventType,
            String errorCode) {}


    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("store", service.storeMode());
        map.put("createSuppressedRecords", properties.isCreateSuppressedRecords());
        map.put("mcpEnabled", properties.getMcp().isEnabled());
        map.put("mcpRunOnCompletedTask", properties.getMcp().isRunOnCompletedTask());
        map.put("mcpRunOnFailedTask", properties.getMcp().isRunOnFailedTask());
        map.put("issueEnabled", properties.getIssue().isEnabled());
        map.put("issueCreateOnCompletedTask", properties.getIssue().isCreateOnCompletedTask());
        map.put("issueCreateOnFailedTask", properties.getIssue().isCreateOnFailedTask());
        map.put("issueUpdateExistingIssueComment", properties.getIssue().isUpdateExistingIssueComment());
        map.put("executorMode", executionProperties.getMode());
        map.put("executorEmbeddedMode", executionProperties.isEmbeddedMode());
        map.put("executorExternalMode", executionProperties.isExternalMode());
        map.put("executorDisabledMode", executionProperties.isDisabledMode());
        map.put("executorEnabled", executionProperties.isEnabled());
        map.put("executorAutoExecutePending", executionProperties.isAutoExecutePending());
        map.put("executorBatchSize", executionProperties.getBatchSize());
        map.put("executorMaxAttempts", executionProperties.getMaxAttempts());
        map.put("executorMockEnabled", executionProperties.getMock().isEnabled());
        map.put("executorExecutionTimeout", executionProperties.getExecutionTimeout().toString());
        map.put("executorCircuitBreakerEnabled", executionProperties.getCircuitBreaker().isEnabled());
        map.put("executorCircuitBreakerSnapshot", circuitBreaker.snapshot());
        map.put("executorAuditStore", service.executorAuditStoreMode());
        map.put("workerRetryEnabled", properties.getWorker().isRetryEnabled());
        map.put("workerMaxAttempts", properties.getWorker().getMaxAttempts());
        map.put("workerInitialBackoff", properties.getWorker().getInitialBackoff().toString());
        map.put("workerMaxBackoff", properties.getWorker().getMaxBackoff().toString());
        map.put("workerExpiredLeaseScanBatchSize", properties.getWorker().getExpiredLeaseScanBatchSize());
        map.put("mcpHttpEnabled", executionProperties.getMcp().isHttpEnabled());
        map.put("mcpEndpointConfigured", executionProperties.getMcp().getEndpointUrl() != null && !executionProperties.getMcp().getEndpointUrl().isBlank());
        map.put("issueDefaultVendor", executionProperties.getIssue().getDefaultVendor());
        map.put("jiraMockEnabled", executionProperties.getIssue().isJiraMockEnabled());
        map.put("redmineMockEnabled", executionProperties.getIssue().isRedmineMockEnabled());
        map.put("gitlabMockEnabled", executionProperties.getIssue().isGitlabMockEnabled());
        map.put("redmineExecutorEnabled", executionProperties.getIssue().getRedmine().isEnabled());
        map.put("redmineEndpointConfigured", executionProperties.getIssue().getRedmine().getBaseUrl() != null && !executionProperties.getIssue().getRedmine().getBaseUrl().isBlank());
        map.put("gitlabExecutorEnabled", executionProperties.getIssue().getGitlab().isEnabled());
        map.put("gitlabEndpointConfigured", executionProperties.getIssue().getGitlab().getBaseUrl() != null && !executionProperties.getIssue().getGitlab().getBaseUrl().isBlank());
        return map;
    }
    private Map<String, Object> redmineReadCollection(String key, String path) {
        Map<String, Object> map = redmineDiagnostics();
        map.put("operation", "READ_" + key.toUpperCase());
        AdapterActionExecutionProperties.Redmine redmine = executionProperties.getIssue().getRedmine();
        if (!redmine.isEnabled() || blank(redmine.getBaseUrl()) || blank(redmine.getApiKey())) {
            map.put("ok", false);
            map.put("message", "Redmine executor must be enabled and configured with baseUrl/apiKey before reading " + key + ".");
            map.put(key, List.of());
            return map;
        }
        RedmineHttpResult result = redmineGet(path);
        map.put("httpStatus", result.statusCode());
        map.put("ok", result.ok());
        map.put("message", result.ok() ? "Redmine " + key + " loaded." : "Redmine " + key + " request failed with HTTP " + result.statusCode() + ".");
        map.put(key, result.ok() ? redmineCollection(result.body(), key) : List.of());
        if (!result.ok()) map.put("error", truncate(result.body()));
        return map;
    }

    private RedmineHttpResult redmineGet(String pathAndQuery) {
        return redmineSend("GET", pathAndQuery, null);
    }

    private RedmineHttpResult redmineSend(String method, String pathAndQuery, String body) {
        AdapterActionExecutionProperties.Redmine redmine = executionProperties.getIssue().getRedmine();
        try {
            URI uri = URI.create(joinUrl(redmine.getBaseUrl(), pathAndQuery));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(redmineRequestTimeout())
                    .header("Accept", "application/json")
                    .header("X-Redmine-API-Key", redmine.getApiKey());
            if (body != null) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = redmineHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new RedmineHttpResult(response.statusCode(), response.body() == null ? "" : response.body());
        } catch (Exception ex) {
            return new RedmineHttpResult(0, safeMessage(ex));
        }
    }

    private Duration redmineRequestTimeout() {
        return executionProperties.getExecutionTimeout() == null ? Duration.ofSeconds(30) : executionProperties.getExecutionTimeout();
    }

    private Map<String, Object> checkRow(String label, RedmineHttpResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("ok", result.ok());
        row.put("httpStatus", result.statusCode());
        row.put("message", result.ok() ? "OK" : truncate(result.body()));
        return row;
    }

    private List<Map<String, Object>> redmineCollection(String body, String key) {
        try {
            Map<String, Object> parsed = parseJsonObject(body);
            Object raw = parsed.get(key);
            if (!(raw instanceof List<?> list)) return List.of();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> safe = new LinkedHashMap<>();
                    Object id = m.get("id");
                    Object identifier = m.get("identifier");
                    Object name = m.get("name");
                    if (id != null) safe.put("id", String.valueOf(id));
                    if (identifier != null) safe.put("identifier", String.valueOf(identifier));
                    if (name != null) safe.put("name", String.valueOf(name));
                    items.add(safe);
                }
            }
            return items;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> parseJsonObject(String body) throws Exception {
        return objectMapper.readValue(body == null ? "{}" : body, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> sanitizeRedmineIssueResponse(Map<String, Object> parsed) {
        Object issueNode = parsed.get("issue");
        if (!(issueNode instanceof Map<?, ?> issue)) return Map.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : List.of("id", "subject", "description", "created_on", "updated_on")) {
            Object value = issue.get(key);
            if (value != null) safe.put(key, value);
        }
        Object project = issue.get("project");
        if (project instanceof Map<?, ?> m) safe.put("project", Map.of("id", String.valueOf(m.get("id")), "name", String.valueOf(m.get("name"))));
        Object tracker = issue.get("tracker");
        if (tracker instanceof Map<?, ?> m) safe.put("tracker", Map.of("id", String.valueOf(m.get("id")), "name", String.valueOf(m.get("name"))));
        return safe;
    }

    private Object parseNumberOrString(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }


    private String redmineTestIssueSubject(RedmineTestIssueRequest request) {
        String severity = firstNonBlank(request.severity(), "MEDIUM");
        String message = firstNonBlank(request.message(), "OpenDispatch Redmine adapter test");
        String objectId = firstNonBlank(request.objectId(), "manual-test");
        String event = firstNonBlank(request.eventType(), "ISSUE_TRACKING_TEST");
        String error = firstNonBlank(request.errorCode(), "");
        String suffix = objectId + " / " + event + (blank(error) ? "" : " / " + error);
        return truncateSubject("[" + severity + "][Redmine] " + message + " - " + suffix);
    }

    private String redmineTestIssueDescription(RedmineTestIssueRequest request) {
        return "## OpenDispatch Redmine Test Issue\n\n"
                + "- Severity: " + firstNonBlank(request.severity(), "MEDIUM") + "\n"
                + "- Message: " + firstNonBlank(request.message(), "Created by OpenDispatch Admin UI issue tracking management.") + "\n"
                + "- Object: " + firstNonBlank(request.objectId(), "manual-test") + "\n"
                + "- Event / Error: " + firstNonBlank(request.eventType(), "ISSUE_TRACKING_TEST") + " / " + firstNonBlank(request.errorCode(), "-") + "\n"
                + "\nThis verifies Redmine API key, project, tracker, priority mapping, and issue create permissions.";
    }

    private Object resolveRedminePriorityId(AdapterActionExecutionProperties.Redmine redmine, String severity) {
        String reference = priorityReferenceForSeverity(redmine, severity);
        if (blank(reference)) return null;
        Object parsed = parseNumberOrString(reference);
        if (parsed instanceof Number) return parsed;
        try {
            RedmineHttpResult result = redmineGet("/enumerations/issue_priorities.json");
            if (!result.ok()) return null;
            List<Map<String, Object>> priorities = redmineCollection(result.body(), "issue_priorities");
            String expected = normalizePriorityName(reference);
            for (Map<String, Object> priority : priorities) {
                Object name = priority.get("name");
                Object id = priority.get("id");
                if (name != null && id != null && expected.equals(normalizePriorityName(String.valueOf(name)))) {
                    return parseNumberOrString(String.valueOf(id));
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String priorityReferenceForSeverity(AdapterActionExecutionProperties.Redmine redmine, String severity) {
        String normalized = normalizeSeverity(severity);
        return switch (normalized) {
            case "CRITICAL", "FATAL", "SEV1", "P0" -> redmine.getPriorityCritical();
            case "HIGH", "WARNING", "WARN", "SEV2", "P1" -> redmine.getPriorityHigh();
            case "LOW", "INFO", "INFORMATION", "SEV4", "P3" -> redmine.getPriorityLow();
            default -> redmine.getPriorityMedium();
        };
    }

    private String normalizeSeverity(String value) {
        if (blank(value)) return "MEDIUM";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return "MIDDLE".equals(normalized) ? "MEDIUM" : normalized;
    }

    private String normalizePriorityName(String value) {
        String normalized = normalizeSeverity(value).replace("_", "");
        return "MEDIUM".equals(normalized) ? "MIDDLE" : normalized;
    }

    private String truncateSubject(String value) {
        if (value == null || value.length() <= 240) return value;
        return value.substring(0, 239) + "…";
    }

    private String issueUrl(AdapterActionExecutionProperties.Redmine redmine, String issueId) {
        if (!blank(redmine.getIssueUrlTemplate())) return formatIssueUrlTemplate(redmine.getIssueUrlTemplate(), issueId);
        return joinUrl(redmine.getBaseUrl(), "/issues/" + urlEncode(issueId));
    }

    private String formatIssueUrlTemplate(String template, String issueId) {
        String encoded = urlEncode(issueId);
        if (template.contains("{issueId}")) return template.replace("{issueId}", encoded);
        if (template.contains("%s")) return String.format(template, encoded);
        return template.endsWith("/") ? template + encoded : template + "/" + encoded;
    }

    private String joinUrl(String base, String pathAndQuery) {
        String b = base == null ? "" : base.replaceAll("/+$", "");
        String p = pathAndQuery == null ? "" : pathAndQuery;
        if (!p.startsWith("/")) p = "/" + p;
        return b + p;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!blank(value)) return value.trim();
        }
        return "";
    }

    private String redactConfigValue(String value) {
        if (blank(value)) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 4 ? "****" : trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private String redactBaseUrl(String value) {
        if (blank(value)) return "";
        try {
            URI uri = URI.create(value.trim());
            String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (Exception ignored) {
            return "configured";
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 800 ? compact.substring(0, 800) + "..." : compact;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getName() : message;
    }

    private record RedmineHttpResult(int statusCode, String body) {
        boolean ok() { return statusCode >= 200 && statusCode < 300; }
    }

}
