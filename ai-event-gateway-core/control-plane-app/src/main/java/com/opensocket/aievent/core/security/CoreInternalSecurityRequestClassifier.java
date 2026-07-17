package com.opensocket.aievent.core.security;

import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

public class CoreInternalSecurityRequestClassifier {
    private final CoreInternalSecurityProperties properties;

    public CoreInternalSecurityRequestClassifier(CoreInternalSecurityProperties properties) {
        this.properties = properties;
    }

    public Optional<CoreInternalSecurityRole> requiredRole(HttpServletRequest request) {
        String path = normalizedPath(request);
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);

        if (isPermittedActuatorProbe(path)) {
            return Optional.empty();
        }
        if (path.startsWith("/actuator")) {
            return Optional.of(CoreInternalSecurityRole.ACTUATOR);
        }
        if (path.startsWith("/api/auth/")) {
            return Optional.empty();
        }
        if (path.startsWith("/api/events/")) {
            return Optional.of(CoreInternalSecurityRole.EVENT_INGESTION);
        }
        if (path.startsWith("/internal/gateway-nodes")) {
            return Optional.of(CoreInternalSecurityRole.GATEWAY);
        }
        if (path.startsWith("/internal/agents/authorize-connection")
                || path.startsWith("/internal/agents/security-events")
                || path.startsWith("/internal/agents/enrollments")) {
            return Optional.of(CoreInternalSecurityRole.GATEWAY);
        }
        if (path.startsWith("/internal/adapter-actions")) {
            if (path.contains("/recover-expired-lease") || path.endsWith("/recover-expired-leases")) {
                return Optional.of(CoreInternalSecurityRole.OPERATOR);
            }
            return Optional.of(CoreInternalSecurityRole.ADAPTER_WORKER);
        }
        if (path.startsWith("/internal/control-plane/tasks")) {
            if (isTaskCallbackWrite(path, method)) {
                return Optional.of(CoreInternalSecurityRole.GATEWAY);
            }
            return Optional.of(CoreInternalSecurityRole.OPERATOR);
        }
        if (path.startsWith("/internal/")) {
            return Optional.of(CoreInternalSecurityRole.OPERATOR);
        }
        if (path.startsWith("/admin/dispatch-governance/cutover")) {
            return Optional.of(isMutation(method)
                    ? CoreInternalSecurityRole.RECOVERY_ADMIN
                    : CoreInternalSecurityRole.OPERATOR);
        }
        if (path.startsWith("/admin/dispatch-governance/actions")) {
            if (isActionApprovalMutation(path, method)) {
                return Optional.of(CoreInternalSecurityRole.RECOVERY_APPROVER);
            }
            if (isActionAdminMutation(path, method) || isActionManualResolutionMutation(path, method)) {
                return Optional.of(CoreInternalSecurityRole.RECOVERY_ADMIN);
            }
            if (isMutation(method)) {
                return Optional.of(CoreInternalSecurityRole.RECOVERY_OPERATOR);
            }
            return Optional.of(CoreInternalSecurityRole.OPERATOR);
        }
        if (path.startsWith("/admin/recovery/approval-requests") && isMutation(method)) {
            return Optional.of(path.endsWith("/cancel")
                    ? CoreInternalSecurityRole.RECOVERY_OPERATOR
                    : CoreInternalSecurityRole.RECOVERY_APPROVER);
        }
        if (path.startsWith("/admin/recovery/actions/")) {
            return Optional.of(isRecoveryHighRiskMutation(path, method)
                    ? CoreInternalSecurityRole.RECOVERY_ADMIN
                    : CoreInternalSecurityRole.RECOVERY_OPERATOR);
        }
        if (path.startsWith("/admin/")) {
            return Optional.of(CoreInternalSecurityRole.OPERATOR);
        }
        if (properties.isProtectApiMutations() && path.startsWith("/api/") && isMutation(method)) {
            return Optional.of(CoreInternalSecurityRole.OPERATOR);
        }
        return Optional.empty();
    }

    private boolean isPermittedActuatorProbe(String path) {
        return properties.isPermitActuatorHealthInfo()
                && (path.equals("/actuator/health") || path.startsWith("/actuator/health/") || path.equals("/actuator/info"));
    }


    private boolean isTaskCallbackWrite(String path, String method) {
        if (!"POST".equals(method)) {
            return false;
        }
        return path.endsWith("/ack")
                || path.endsWith("/progress")
                || path.endsWith("/result")
                || path.endsWith("/error");
    }

    private boolean isMutation(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }


    private boolean isActionApprovalMutation(String path, String method) {
        if (!"POST".equals(method)) {
            return false;
        }
        return (path.contains("/grants/") && path.endsWith("/approve"))
                || (path.contains("/approval-requests/") && path.endsWith("/decide"));
    }

    private boolean isActionManualResolutionMutation(String path, String method) {
        return "POST".equals(method)
                && path.contains("/manual-cases/")
                && path.endsWith("/resolve");
    }

    private boolean isActionAdminMutation(String path, String method) {
        if ("PUT".equals(method)) {
            return path.contains("/catalog/") || path.contains("/grants/");
        }
        return "POST".equals(method) && path.contains("/grants/") && path.endsWith("/revoke");
    }

    private boolean isRecoveryHighRiskMutation(String path, String method) {
        if (!isMutation(method)) {
            return false;
        }
        return path.endsWith("/dead-letter") || path.endsWith("/restore-dead-letter");
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = !contextPath.isBlank() && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return path.isBlank() ? "/" : path;
    }
}
