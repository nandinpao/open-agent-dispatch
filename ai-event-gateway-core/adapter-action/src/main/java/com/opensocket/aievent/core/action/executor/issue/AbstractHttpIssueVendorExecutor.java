package com.opensocket.aievent.core.action.executor.issue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.action.executor.AdapterSecretRedactor;

abstract class AbstractHttpIssueVendorExecutor implements IssueTrackingActionExecutor {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    protected final String executorName;
    protected final ObjectMapper mapper;
    protected final HttpClient client;

    AbstractHttpIssueVendorExecutor(String executorName, ObjectMapper mapper, Duration timeout) {
        this.executorName = executorName;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(AdapterSecretRedactor.safeHttpTimeout(timeout, Duration.ofSeconds(30)))
                .build();
    }

    public String executorName() { return executorName; }

    protected String text(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) return null;
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String s && !s.isBlank()) return s.trim();
            if (value instanceof Number n) return String.valueOf(n);
        }
        return null;
    }

    protected String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    protected String title(IssueExecutorRequest request) {
        Map<String, Object> payload = request.getPayload();
        String explicit = text(payload, "issueTitle", "title", "subject");
        if (explicit != null) return explicit;
        String severity = firstNonBlank(text(payload, "priority", "severity"), "TASK");
        String system = firstNonBlank(text(payload, "sourceSystem"), inferSystem(payload));
        String objectId = firstNonBlank(text(payload, "objectId"), text(payload, "objectType"), "target");
        String eventType = firstNonBlank(text(payload, "eventType"), "event");
        String errorCode = text(payload, "errorCode");
        String message = firstNonBlank(text(payload, "message", "incidentMessage", "lastMessage", "callbackMessage"));
        String suffix = objectId + " / " + eventType + (errorCode == null ? "" : " / " + errorCode);
        return truncate("[" + severity + "][" + system + "] " + firstNonBlank(message, suffix) + " - " + suffix, 240);
    }

    protected String description(IssueExecutorRequest request) {
        Map<String, Object> payload = request.getPayload();
        String explicit = text(payload, "issueDescription", "description");
        if (explicit != null) return withIdempotencyMarker(explicit, request);
        String summary = firstNonBlank(text(payload, "agentSummary", "summary", "resultSummary", "callbackMessage"), "Agent result is not available yet.");
        String generated = "## OpenDispatch Agent Result\n\n"
                + summary + "\n\n"
                + "## Context\n"
                + "- Incident: " + firstNonBlank(request.getIncidentId(), text(payload, "incidentId"), "-") + "\n"
                + "- Task: " + firstNonBlank(request.getTaskId(), text(payload, "taskId"), "-") + "\n"
                + "- Tenant: " + firstNonBlank(text(payload, "tenantId"), "-") + "\n"
                + "- Source system: " + firstNonBlank(text(payload, "sourceSystem"), "-") + "\n"
                + "- Severity: " + firstNonBlank(text(payload, "severity", "priority"), "-") + "\n"
                + "- Message: " + firstNonBlank(text(payload, "message", "incidentMessage", "lastMessage"), "-") + "\n"
                + "- Agent: " + firstNonBlank(text(payload, "agentId"), "-") + "\n"
                + "- Site / Plant: " + firstNonBlank(text(payload, "siteId"), "-") + " / " + firstNonBlank(text(payload, "plantId"), "-") + "\n"
                + "- Object: " + firstNonBlank(text(payload, "objectType"), "-") + " / " + firstNonBlank(text(payload, "objectId"), "-") + "\n"
                + "- Event: " + firstNonBlank(text(payload, "eventType"), "-") + " / " + firstNonBlank(text(payload, "errorCode"), "-") + "\n";
        return withIdempotencyMarker(generated, request);
    }

    protected String comment(IssueExecutorRequest request) {
        Map<String, Object> payload = request.getPayload();
        return withIdempotencyMarker(firstNonBlank(text(payload, "issueComment", "comment", "notes", "body"), description(request)), request);
    }

    protected String linkedIssueId(IssueExecutorRequest request) {
        String raw = text(request.getPayload(), "linkedIssueId", "issueId", "externalIssueId", "iid", "key");
        if (raw == null) return null;
        int colon = raw.indexOf(':');
        if (colon >= 0 && colon < raw.length() - 1) return raw.substring(colon + 1).trim();
        if (raw.contains("#")) return raw.substring(raw.lastIndexOf('#') + 1).trim();
        return raw.trim();
    }


    protected String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    protected String inferSystem(Map<String, Object> payload) {
        return firstNonBlank(
                text(payload, "sourceSystem", "originSourceSystem"),
                "OpenDispatch");
    }

    protected String idempotencyKey(IssueExecutorRequest request) {
        if (request == null) return null;
        return firstNonBlank(
                request.getIdempotencyKey(),
                text(request.getPayload(), "issueActionIdempotencyKey", "adapterActionIdempotencyKey", "idempotencyKey", "issueCommentDedupeKey"));
    }

    protected String idempotencyMarker(IssueExecutorRequest request) {
        String key = idempotencyKey(request);
        if (key == null || key.isBlank()) return null;
        String actionId = firstNonBlank(request.getActionId(), text(request.getPayload(), "adapterActionId"));
        return "<!-- OpenDispatch: issue-action-idempotency-key=" + key
                + (actionId == null ? "" : "; actionId=" + actionId)
                + " -->";
    }

    protected String withIdempotencyMarker(String text, IssueExecutorRequest request) {
        String marker = idempotencyMarker(request);
        if (marker == null || marker.isBlank()) return text;
        String value = text == null ? "" : text;
        return value.contains(marker) ? value : value + "\n\n" + marker;
    }

    protected Map<String, String> headersWithIdempotency(IssueExecutorRequest request, Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers != null) result.putAll(headers);
        String key = idempotencyKey(request);
        if (key != null && !key.isBlank()) {
            result.put("X-OpenDispatch-Idempotency-Key", key);
        }
        return result;
    }

    protected Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    protected Map<String, Object> parseJson(String body) throws Exception {
        if (body == null || body.isBlank()) return Map.of();
        return mapper.readValue(body, MAP_TYPE);
    }

    protected String writeJson(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    protected String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    protected boolean retryableHttpStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    protected IssueExecutorResponse httpFailure(String operation, int statusCode, String body) {
        return failed(operation + " returned " + statusCode + ": " + AdapterSecretRedactor.redactText(body), retryableHttpStatus(statusCode), statusCode);
    }

    protected String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String suffix = path == null ? "" : path.trim();
        if (!suffix.startsWith("/")) suffix = "/" + suffix;
        return base + suffix;
    }

    protected IssueExecutorResponse failed(String error) {
        return failed(error, false, null);
    }

    protected IssueExecutorResponse failedRetryable(String error) {
        return failed(error, true, null);
    }

    protected IssueExecutorResponse failed(String error, boolean retryable, Integer statusCode) {
        IssueExecutorResponse response = new IssueExecutorResponse();
        response.setSuccess(false);
        response.setVendor(vendor().name());
        response.setError(AdapterSecretRedactor.redactText(error));
        response.setRetryable(retryable);
        response.setStatusCode(statusCode);
        return response;
    }

    protected HttpResponse<String> sendGet(String url, Map<String, String> headers, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(AdapterSecretRedactor.safeHttpTimeout(timeout, Duration.ofSeconds(30)))
                .GET();
        if (headers != null) headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> sendJson(String method, String url, String body, Map<String, String> headers, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(AdapterSecretRedactor.safeHttpTimeout(timeout, Duration.ofSeconds(30)))
                .header("Content-Type", "application/json");
        if (headers != null) headers.forEach(builder::header);
        if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
        } else {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected boolean ok(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }
}
