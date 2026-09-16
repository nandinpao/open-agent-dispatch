package com.opensocket.aievent.core.api.contract;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.opensocket.aievent.core.governance.ApiMutationReceipt;
import com.opensocket.aievent.core.governance.ApiMutationStart;
import com.opensocket.aievent.core.governance.AuditEvidenceService;
import com.opensocket.aievent.core.governance.AuthorizationDecision;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;

/**
 * Enforces the Phase 0H mutation contract without changing existing response
 * body types. Metadata is exposed through standard response headers.
 */
@Component
public class ApiMutationContractInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ApiMutationContractInterceptor.class);
    public static final String RECEIPT_ATTR = ApiMutationContractInterceptor.class.getName() + ".receipt";

    private final ApiContractProperties properties;
    private final ApiPermissionPointResolver permissions;
    private final AuditEvidenceService audit;

    public ApiMutationContractInterceptor(ApiContractProperties properties,
                                          ApiPermissionPointResolver permissions,
                                          AuditEvidenceService audit) {
        this.properties = properties;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!isMutation(request)) return true;

        String path = request.getRequestURI();
        String method = request.getMethod();
        String idempotencyKey = trim(request.getHeader("Idempotency-Key"));
        String ifMatch = trim(request.getHeader("If-Match"));
        String auditReason = trim(request.getHeader("X-Audit-Reason"));
        String suppliedRequestHash = trim(request.getHeader("X-Request-Hash"));

        OpenDispatchRequestContext requestContext = OpenDispatchRequestContextHolder.current().orElse(null);
        String tenantId = requestContext == null ? null : trim(requestContext.tenantId());
        String correlationId = requestContext == null ? null : trim(requestContext.correlationId());
        String actorId = requestContext == null ? null : trim(requestContext.operatorId());
        String actorType = actorType(actorId);
        String permissionPoint = permissions.resolve(method, path);

        List<String> missing = new ArrayList<>();
        if (blank(tenantId)) missing.add("TENANT_CONTEXT");
        if (properties.isRequireIdempotency() && blank(idempotencyKey) && !idempotencyExempt(path)) {
            missing.add("IDEMPOTENCY_KEY");
        }
        if (properties.isRequireCorrelationId() && blank(correlationId)) {
            missing.add("CORRELATION_ID");
        }
        if (properties.isRequireExpectedVersion() && expectedVersionRequired(method, path) && blank(ifMatch)) {
            missing.add("EXPECTED_VERSION");
        }
        if (properties.isRequireActorIdentity() && blank(actorId)) {
            missing.add("ACTOR_IDENTITY");
        }
        if (properties.isRequireAuditReason() && auditReasonRequired(path) && blank(auditReason)) {
            missing.add("AUDIT_REASON");
        }

        VersionParse version = parseVersion(ifMatch);
        if (!blank(ifMatch) && !version.valid()) missing.add("EXPECTED_VERSION_INVALID");

        if (!missing.isEmpty() && properties.getEnforcement() == ApiContractEnforcementMode.STRICT) {
            writeContractFailure(response, correlationId, missing);
            return false;
        }

        // AUDIT mode keeps local/development workflows usable while surfacing debt.
        tenantId = firstNonBlank(tenantId, "UNRESOLVED");
        correlationId = firstNonBlank(correlationId, UUID.randomUUID().toString());
        actorId = firstNonBlank(actorId, "anonymous");
        idempotencyKey = firstNonBlank(idempotencyKey, "audit-only:" + UUID.randomUUID());

        try {
            AuthorizationDecision decision = audit.acceptApiMutationContract(
                    tenantId,
                    permissionPoint,
                    resourceType(path),
                    resourceId(path),
                    actorType,
                    actorId,
                    correlationId,
                    List.of("tenant:" + tenantId));

            String requestMaterial = requestMaterial(
                    request, method, path, ifMatch, suppliedRequestHash);
            ApiMutationStart start = audit.beginMutation(
                    tenantId,
                    method,
                    path,
                    idempotencyKey,
                    requestMaterial,
                    version.value(),
                    actorType,
                    actorId,
                    auditReason,
                    correlationId,
                    decision.decisionId(),
                    permissionPoint);

            applyMetadata(response, decision.decisionId(), correlationId,
                    start.receipt().syncStatus(), version.value());

            if (start.replay()) {
                return handleReplay(request, response, start, decision, correlationId);
            }

            request.setAttribute(RECEIPT_ATTR, start.receipt());
            if (!missing.isEmpty()) {
                response.addHeader("Warning", "299 OpenDispatch \"API contract warnings: "
                        + String.join(",", missing) + "\"");
            }
            return true;
        } catch (IllegalStateException exception) {
            if (messageContains(exception, "API_IDEMPOTENCY_CONFLICT")) {
                writeError(response, HttpStatus.CONFLICT.value(), "API_IDEMPOTENCY_CONFLICT",
                        "The Idempotency-Key was already used with different request content.",
                        correlationId, Map.of());
                return false;
            }
            throw exception;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        Object value = request.getAttribute(RECEIPT_ATTR);
        if (!(value instanceof ApiMutationReceipt receipt)) return;

        String status = exception == null && response.getStatus() < 400 ? "COMPLETED" : "FAILED";
        try {
            audit.finish(
                    receipt,
                    status,
                    String.valueOf(response.getStatus()),
                    null,
                    null,
                    reflectedVersion(response),
                    firstNonBlank(response.getHeader("X-Sync-Status"), "NOT_APPLICABLE"));
            audit.record(
                    receipt.tenantId(),
                    "API_MUTATION_" + status,
                    "API_MUTATION",
                    receipt.receiptId(),
                    null,
                    receipt.actorType(),
                    receipt.actorId(),
                    receipt.requestMethod() + " " + receipt.requestPath(),
                    exception == null ? "API_MUTATION_COMPLETED" : "API_MUTATION_FAILED",
                    receipt.auditReason(),
                    receipt.correlationId(),
                    null,
                    receipt.authorizationDecisionId(),
                    requestContextValue("requestId"),
                    requestContextValue("clientAddress"),
                    receipt.requestHash(),
                    exception == null ? "SUCCESS" : "FAILURE",
                    Map.of(
                            "httpStatus", response.getStatus(),
                            "permissionPoint", receipt.permissionPoint()));
        } catch (Exception auditFailure) {
            log.error("Phase 0H audit evidence write failed receiptId={} correlationId={}",
                    receipt.receiptId(), receipt.correlationId(), auditFailure);
        }
    }

    private boolean handleReplay(HttpServletRequest request,
                                 HttpServletResponse response,
                                 ApiMutationStart start,
                                 AuthorizationDecision decision,
                                 String correlationId) {
        ApiMutationReceipt receipt = start.receipt();
        response.setHeader("X-Idempotent-Replay", "true");
        if (start.inProgressReplay()) {
            writeError(response, HttpStatus.CONFLICT.value(), "API_IDEMPOTENCY_IN_PROGRESS",
                    "A request with this Idempotency-Key is still in progress.", correlationId,
                    Map.of("receiptId", receipt.receiptId()));
            recordReplay(request, receipt, decision, "CONFLICT", "API_IDEMPOTENCY_IN_PROGRESS");
            return false;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("receiptId", receipt.receiptId());
        data.put("status", receipt.status());
        data.put("resultResourceType", receipt.resultResourceType());
        data.put("resultResourceId", receipt.resultResourceId());
        data.put("resourceVersion", receipt.resultVersion());
        data.put("syncStatus", firstNonBlank(receipt.syncStatus(), "NOT_APPLICABLE"));
        writeSuccess(response, "Idempotent replay; the mutation was not executed again.",
                correlationId, data);
        recordReplay(request, receipt, decision, "SUCCESS", "API_IDEMPOTENCY_REPLAY");
        return false;
    }

    private void recordReplay(HttpServletRequest request,
                              ApiMutationReceipt receipt,
                              AuthorizationDecision decision,
                              String outcome,
                              String reasonCode) {
        try {
            audit.record(
                    receipt.tenantId(),
                    "API_MUTATION_REPLAYED",
                    "API_MUTATION",
                    receipt.receiptId(),
                    null,
                    receipt.actorType(),
                    receipt.actorId(),
                    receipt.requestMethod() + " " + receipt.requestPath(),
                    reasonCode,
                    receipt.auditReason(),
                    receipt.correlationId(),
                    receipt.receiptId(),
                    decision.decisionId(),
                    requestContextValue("requestId"),
                    requestContextValue("clientAddress"),
                    receipt.requestHash(),
                    outcome,
                    Map.of("idempotentReplay", true, "priorStatus", receipt.status()));
        } catch (Exception auditFailure) {
            log.error("Unable to record idempotent replay receiptId={}", receipt.receiptId(), auditFailure);
        }
    }

    private void applyMetadata(HttpServletResponse response,
                               String authorizationDecisionId,
                               String correlationId,
                               String syncStatus,
                               Long version) {
        response.setHeader("X-Correlation-Id", correlationId);
        response.setHeader("X-Authorization-Decision-Id", authorizationDecisionId);
        response.setHeader("X-Sync-Status", firstNonBlank(syncStatus, "NOT_APPLICABLE"));
        if (version != null) response.setHeader("ETag", "\"" + version + "\"");
    }

    private String requestMaterial(HttpServletRequest request,
                                   String method,
                                   String path,
                                   String ifMatch,
                                   String suppliedRequestHash) {
        return method + "|" + path
                + "|query=" + String.valueOf(request.getQueryString())
                + "|ifMatch=" + String.valueOf(ifMatch)
                + "|contentType=" + String.valueOf(request.getContentType())
                + "|contentLength=" + request.getContentLengthLong()
                + "|requestHash=" + String.valueOf(suppliedRequestHash);
    }

    private boolean isMutation(HttpServletRequest request) {
        return request.getRequestURI() != null
                && request.getRequestURI().startsWith("/api/")
                && !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
    }

    private boolean idempotencyExempt(String path) {
        return path.startsWith("/api/session/")
                || path.startsWith("/api/events/")
                || path.contains("/webhooks/");
    }

    private boolean expectedVersionRequired(String method, String path) {
        if (Set.of("PUT", "PATCH", "DELETE").contains(method)) return true;
        return properties.getExpectedVersionPathFragments().stream().anyMatch(path::contains);
    }

    private boolean auditReasonRequired(String path) {
        return properties.getAuditReasonExemptPathPrefixes().stream().noneMatch(path::startsWith);
    }

    private String resourceType(String path) {
        if (path.contains("a2a")) return "A2A_REQUEST";
        if (path.contains("integrations") || path.contains("task-issues")) return "INTEGRATION";
        if (path.contains("tasks")) return "TASK";
        if (path.contains("agents")) return "AGENT";
        return "API_RESOURCE";
    }

    private String resourceId(String path) {
        String[] segments = path.split("/");
        for (int index = segments.length - 1; index >= 0; index--) {
            if (!segments[index].isBlank()) return segments[index];
        }
        return path;
    }

    private VersionParse parseVersion(String value) {
        if (blank(value)) return new VersionParse(null, true);
        try {
            String normalized = value.trim();
            if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
            normalized = normalized.replace("\"", "").trim();
            return new VersionParse(Long.parseLong(normalized), true);
        } catch (Exception ignored) {
            return new VersionParse(null, false);
        }
    }

    private Long reflectedVersion(HttpServletResponse response) {
        VersionParse parsed = parseVersion(response.getHeader("ETag"));
        return parsed.valid() ? parsed.value() : null;
    }

    private String requestContextValue(String name) {
        OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElse(null);
        if (context == null) return null;
        return switch (name) {
            case "requestId" -> context.requestId();
            case "clientAddress" -> context.clientAddress();
            default -> null;
        };
    }

    private String actorType(String actorId) {
        if (blank(actorId)) return "SYSTEM";
        String normalized = actorId.toLowerCase();
        return normalized.contains("service") || normalized.contains("worker")
                || normalized.contains("system") ? "SERVICE" : "USER";
    }

    private boolean messageContains(Exception exception, String token) {
        return exception.getMessage() != null && exception.getMessage().contains(token);
    }

    private void writeContractFailure(HttpServletResponse response,
                                      String correlationId,
                                      List<String> missing) {
        String code;
        int status;
        if (missing.contains("TENANT_CONTEXT")) {
            code = "TENANT_CONTEXT_REQUIRED";
            status = HttpStatus.BAD_REQUEST.value();
        } else if (missing.contains("CORRELATION_ID")) {
            code = "API_CORRELATION_ID_REQUIRED";
            status = HttpStatus.BAD_REQUEST.value();
        } else if (missing.contains("EXPECTED_VERSION_INVALID")) {
            code = "API_EXPECTED_VERSION_INVALID";
            status = HttpStatus.BAD_REQUEST.value();
        } else if (missing.contains("EXPECTED_VERSION")) {
            code = "API_EXPECTED_VERSION_REQUIRED";
            status = 428;
        } else if (missing.contains("ACTOR_IDENTITY")) {
            code = "API_ACTOR_IDENTITY_REQUIRED";
            status = HttpStatus.UNAUTHORIZED.value();
        } else if (missing.contains("AUDIT_REASON")) {
            code = "API_AUDIT_REASON_REQUIRED";
            status = HttpStatus.BAD_REQUEST.value();
        } else {
            code = "API_IDEMPOTENCY_KEY_REQUIRED";
            status = HttpStatus.BAD_REQUEST.value();
        }
        writeError(response, status, code,
                "Missing or invalid mutation contract fields: " + String.join(", ", missing),
                correlationId, Map.of("contractFields", missing));
    }

    private void writeSuccess(HttpServletResponse response,
                              String message,
                              String correlationId,
                              Map<String, Object> data) {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json");
        if (!blank(correlationId)) response.setHeader("X-Correlation-Id", correlationId);
        writeJson(response, "OK", message, data);
    }

    private void writeError(HttpServletResponse response,
                            int status,
                            String code,
                            String message,
                            String correlationId,
                            Map<String, Object> data) {
        response.setStatus(status);
        response.setContentType("application/json");
        if (!blank(correlationId)) response.setHeader("X-Correlation-Id", correlationId);
        writeJson(response, code, message, data);
    }

    private void writeJson(HttpServletResponse response,
                           String code,
                           String message,
                           Map<String, Object> data) {
        try {
            String json = "{\"code\":\"" + jsonEscape(code)
                    + "\",\"message\":\"" + jsonEscape(message)
                    + "\",\"data\":" + mapJson(data)
                    + ",\"timestamp\":\"" + OffsetDateTime.now(ZoneOffset.UTC) + "\"}";
            response.getWriter().write(json);
        } catch (Exception writeFailure) {
            log.error("Unable to write API contract response code={}", code, writeFailure);
        }
    }

    private String mapJson(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) builder.append(',');
            first = false;
            builder.append('"').append(jsonEscape(entry.getKey())).append("\":")
                    .append(valueJson(entry.getValue()));
        }
        return builder.append('}').toString();
    }

    private String valueJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) builder.append(',');
                first = false;
                builder.append(valueJson(item));
            }
            return builder.append(']').toString();
        }
        return "\"" + jsonEscape(String.valueOf(value)) + "\"";
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private record VersionParse(Long value, boolean valid) { }
}
