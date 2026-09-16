package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamAuthenticationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionCookiePort;
import com.opensocket.aievent.core.iam.api.application.port.IamUiSessionApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.request.ActivateInvitationRequest;
import com.opensocket.aievent.core.iam.api.request.BeginMfaEnrollmentRequest;
import com.opensocket.aievent.core.iam.api.request.ChangePasswordRequest;
import com.opensocket.aievent.core.iam.api.request.ConfirmMfaEnrollmentRequest;
import com.opensocket.aievent.core.iam.api.request.ForgotPasswordRequest;
import com.opensocket.aievent.core.iam.api.request.LoginRequest;
import com.opensocket.aievent.core.iam.api.request.ResetPasswordRequest;
import com.opensocket.aievent.core.iam.api.request.SwitchTenantRequest;
import com.opensocket.aievent.core.iam.api.request.VerifyLoginMfaRequest;
import com.opensocket.aievent.core.iam.api.response.IamUiSessionResponse;
import com.opensocket.aievent.core.iam.api.response.MfaEnrollmentResponse;
import com.opensocket.aievent.core.iam.api.response.LoginResponse;
import com.opensocket.aievent.core.iam.api.response.SessionResponse;
import com.opensocket.aievent.core.iam.api.response.UiEntitlementResponse;
import com.opensocket.aievent.core.iam.api.application.service.UiFeatureEntitlementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * R2 canonical human-browser session surface. Every accepted credential provider
 * resolves to the same IAM subject and writes the same OPENDISPATCH_SESSION cookie.
 */
@RestController
@RequestMapping("/api/session")
@ConditionalOnBean({IamAuthenticationApiPort.class, IamUiSessionApiPort.class})
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class CanonicalSessionController {
    private final IamAuthenticationApiPort authentication;
    private final IamUiSessionApiPort projection;
    private final IamApiRequestContextFactory contexts;
    private final IamSessionCookiePort cookies;
    private final UiFeatureEntitlementService uiEntitlements = new UiFeatureEntitlementService();

    public CanonicalSessionController(
            IamAuthenticationApiPort authentication,
            IamUiSessionApiPort projection,
            IamApiRequestContextFactory contexts,
            IamSessionCookiePort cookies) {
        this.authentication = authentication;
        this.projection = projection;
        this.contexts = contexts;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginResponse result = authentication.login(body, contexts.from(request));
        if (result.session() != null) cookies.write(result.session(), request, response);
        return result;
    }

    @PostMapping("/mfa/verify")
    public LoginResponse verifyMfa(
            @Valid @RequestBody VerifyLoginMfaRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        LoginResponse result = authentication.verifyMfa(body, contexts.from(request));
        if (result.session() != null) cookies.write(result.session(), request, response);
        return result;
    }

    @GetMapping({"", "/"})
    public IamUiSessionResponse current(HttpServletRequest request) {
        return projection.current(contexts.from(request));
    }

    /** Backend-owned Menu/Page/Action projection for the active canonical session. */
    @GetMapping("/entitlements")
    public UiEntitlementResponse entitlements(HttpServletRequest request) {
        return uiEntitlements.project(projection.current(contexts.from(request)));
    }

    @PostMapping("/switch-tenant")
    public SessionResponse switchTenant(
            @Valid @RequestBody SwitchTenantRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        SessionResponse result = authentication.switchTenant(body, mutationContext(request));
        cookies.write(result, request, response);
        return result;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authentication.logout(mutationContext(request));
        cookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        authentication.logoutAll(mutationContext(request));
        cookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/activate-invitation")
    public ResponseEntity<Void> activateInvitation(
            @Valid @RequestBody ActivateInvitationRequest body,
            HttpServletRequest request) {
        authentication.activateInvitation(body, mutationContext(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/enrollment/start")
    public MfaEnrollmentResponse beginUserMfa(
            @Valid @RequestBody BeginMfaEnrollmentRequest body,
            HttpServletRequest request) {
        return authentication.beginUserMfa(body, mutationContext(request));
    }

    @PostMapping("/mfa/enrollment/confirm")
    public ResponseEntity<Void> confirmUserMfa(
            @Valid @RequestBody ConfirmMfaEnrollmentRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        authentication.confirmUserMfa(body, mutationContext(request));
        cookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        authentication.changePassword(body, mutationContext(request));
        cookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest body,
            HttpServletRequest request) {
        authentication.requestPasswordReset(body, mutationContext(request));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest body,
            HttpServletRequest request) {
        authentication.resetPassword(body, mutationContext(request));
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/preflight")
    public SessionPreflightResponse preflight(HttpServletRequest request, CsrfToken token) {
        IamApiRequestContext context = contexts.from(request);
        var authenticationContext = context.requireAuthentication();
        IamUiSessionResponse session = projection.current(context);
        String principalType = authenticationContext.subject().identityType().name();
        String tenantContext = session.selectedTenantId() == null ? "" : session.selectedTenantId();
        return new SessionPreflightResponse(
                true,
                principalType,
                true,
                token != null && token.getToken() != null && !token.getToken().isBlank(),
                tenantContext,
                "AUTHENTICATED_SESSION_READY",
                session.permissions().isEmpty() ? "EMPTY" : "AVAILABLE",
                context.correlationId());
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {}

    public record SessionPreflightResponse(
            boolean authenticated,
            String principalType,
            boolean sessionValid,
            boolean csrfReady,
            String tenantContext,
            String authorizationService,
            String permissionCatalog,
            String correlationId) {}


    private IamApiRequestContext mutationContext(HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        context.requireIdempotencyKey();
        return context;
    }
}
