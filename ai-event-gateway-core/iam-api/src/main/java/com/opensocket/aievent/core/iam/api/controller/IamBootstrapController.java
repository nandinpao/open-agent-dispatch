package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamBootstrapApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionCookiePort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bootstrap")
@ConditionalOnBean(IamBootstrapApiPort.class)
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamBootstrapController {
    private final IamBootstrapApiPort port;
    private final IamApiRequestContextFactory contexts;
    private final IamSessionCookiePort cookies;

    public IamBootstrapController(
            IamBootstrapApiPort port,
            IamApiRequestContextFactory contexts,
            IamSessionCookiePort cookies) {
        this.port = port;
        this.contexts = contexts;
        this.cookies = cookies;
    }

    @GetMapping("/status")
    public BootstrapStatusResponse status() { return port.status(); }

    @PostMapping("/root/mfa")
    public MfaEnrollmentResponse beginMfa(
            @Valid @RequestBody BeginMfaEnrollmentRequest body,
            HttpServletRequest request) {
        return port.beginRootMfa(body, mutationContext(request));
    }

    @PostMapping("/root/mfa/confirm")
    public SessionResponse confirmMfa(
            @Valid @RequestBody ConfirmMfaEnrollmentRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        SessionResponse session = port.confirmRootMfa(body, mutationContext(request));
        cookies.write(session, request, response);
        return session;
    }

    @PostMapping("/tenant")
    public ResponseEntity<TenantResponse> tenant(
            @Valid @RequestBody CreateTenantRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(201).body(port.createFirstTenant(body, mutationContext(request)));
    }

    @PostMapping("/tenant-admin")
    public ResponseEntity<UserResponse> admin(
            @Valid @RequestBody CreateTenantAdminRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(201).body(port.createFirstTenantAdmin(body, mutationContext(request)));
    }

    @PostMapping("/complete")
    public BootstrapStatusResponse complete(
            @Valid @RequestBody CompleteBootstrapRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        BootstrapStatusResponse completed = port.complete(body, mutationContext(request));
        // Bootstrap completion is also a browser-session boundary. Expire the
        // restricted HttpOnly cookie in the authoritative server response.
        cookies.clear(request, response);
        return completed;
    }

    private IamApiRequestContext mutationContext(HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        context.requireIdempotencyKey();
        return context;
    }
}
