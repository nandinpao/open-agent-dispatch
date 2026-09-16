package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.runtime.orchestration.IamAuthenticationRuntimeOrchestrator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates IAM cookies and enforces the password-change-only bootstrap session. */
public final class IamBrowserSessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> SESSION_RECOVERY_ALLOWED_PATHS = Set.of(
            "/api/session/csrf",
            "/api/session",
            "/api/session/",
            "/api/session/login",
            "/api/session/mfa/verify",
            "/api/session/mfa/enrollment/start",
            "/api/session/mfa/enrollment/confirm",
            "/api/session/activate-invitation",
            "/api/session/forgot-password",
            "/api/session/reset-password",
            "/api/session/change-password",
            "/api/session/logout",
            "/api/session/logout-all",
            "/api/platform/runtime-capabilities",
            "/api/platform/runtime-capabilities/");

    private final ServletIamSessionCookieAdapter cookies;
    private final IamSessionCookieCodec codec;
    private final IamRuntimeSessionAuthenticator authenticator;

    public IamBrowserSessionAuthenticationFilter(
            ServletIamSessionCookieAdapter cookies,
            IamSessionCookieCodec codec,
            IamRuntimeSessionAuthenticator authenticator) {
        this.cookies = cookies;
        this.codec = codec;
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String raw = cookies.read(request);
        if (!raw.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var context = authenticator.authenticate(codec.decode(raw));
                SecurityContextHolder.getContext().setAuthentication(
                        new IamRuntimeAuthenticationToken(context));
                if (context.assurance().methods().contains(
                        IamAuthenticationRuntimeOrchestrator.PASSWORD_CHANGE_REQUIRED_METHOD)
                        && !allowedDuringPasswordChange(request)) {
                    writeRestricted(response, "AUTH_PASSWORD_CHANGE_REQUIRED",
                            "This bootstrap session may only change the initial password or sign out.");
                    return;
                }
                if (context.assurance().methods().contains(
                        IamAuthenticationRuntimeOrchestrator.ROOT_BOOTSTRAP_REQUIRED_METHOD)
                        && !allowedDuringRootBootstrap(request)) {
                    writeRestricted(response, "AUTH_MFA_ENROLLMENT_REQUIRED",
                            "Complete Root MFA enrollment and installation bootstrap before using the administration APIs.");
                    return;
                }
                if (context.assurance().methods().contains(
                        IamAuthenticationRuntimeOrchestrator.HUMAN_MFA_ENROLLMENT_REQUIRED_METHOD)
                        && !allowedDuringHumanMfaEnrollment(request)) {
                    writeRestricted(response, "AUTH_MFA_ENROLLMENT_REQUIRED",
                            "Complete MFA enrollment before using Tenant administration APIs.");
                    return;
                }
            } catch (RuntimeException ex) {
                request.setAttribute("iam.authentication.failure", ex.getMessage());
                if (invalidatesSession(ex)) {
                    cookies.clear(request, response);
                } else {
                    writeUnavailable(request, response);
                    return;
                }
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder.clear();
        }
    }

    private boolean allowedDuringPasswordChange(HttpServletRequest request) {
        return SESSION_RECOVERY_ALLOWED_PATHS.contains(request.getRequestURI());
    }

    private boolean allowedDuringHumanMfaEnrollment(HttpServletRequest request) {
        return SESSION_RECOVERY_ALLOWED_PATHS.contains(request.getRequestURI());
    }

    private boolean allowedDuringRootBootstrap(HttpServletRequest request) {
        String path = request.getRequestURI();
        return SESSION_RECOVERY_ALLOWED_PATHS.contains(path)
                || path.equals("/api/bootstrap/status")
                || path.startsWith("/api/bootstrap/");
    }


    private boolean invalidatesSession(RuntimeException failure) {
        if (failure instanceof AuthenticationDomainException domain) {
            return domain.reasonCode() == AuthenticationReasonCode.AUTH_SESSION_EXPIRED
                    || domain.reasonCode() == AuthenticationReasonCode.AUTH_SESSION_REVOKED
                    || domain.reasonCode() == AuthenticationReasonCode.AUTH_SESSION_STALE_EPOCH;
        }
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        return message.contains("AUTH_SESSION_COOKIE_INVALID")
                || message.contains("AUTH_SESSION_REVOKED")
                || message.contains("AUTH_SESSION_EXPIRED")
                || message.contains("AUTH_SCOPE_MISMATCH")
                || message.contains("AUTH_POLICY_VERSION_STALE");
    }

    private void writeUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        response.setStatus(503);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-Id", correlationId);
        response.getWriter().write(
                "{\"code\":\"AUTH_SESSION_SERVICE_UNAVAILABLE\"," +
                        "\"error_code\":\"AUTH_SESSION_SERVICE_UNAVAILABLE\"," +
                        "\"message\":\"The authenticated session could not be verified because the session service is temporarily unavailable.\"," +
                        "\"correlationId\":\"" + correlationId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }

    private void writeRestricted(
            HttpServletResponse response,
            String errorCode,
            String message) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error_code\":\"" + errorCode + "\","
                        + "\"message\":\"" + message + "\"}");
    }
}
