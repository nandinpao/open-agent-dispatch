package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamUserLifecycleApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.request.UserOnboardingRequest;
import com.opensocket.aievent.core.iam.api.response.UserOnboardingResponse;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access/tenants/{tenantId}/user-onboarding")
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamUserLifecycleController {
    private final IamUserLifecycleApiPort lifecycle;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public IamUserLifecycleController(
            IamUserLifecycleApiPort lifecycle,
            IamPermissionGuard guard,
            IamApiRequestContextFactory contexts) {
        this.lifecycle = lifecycle;
        this.guard = guard;
        this.contexts = contexts;
    }

    @PostMapping
    public ResponseEntity<UserOnboardingResponse> onboard(
            @PathVariable String tenantId,
            @Valid @RequestBody UserOnboardingRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        String activeTenantId = context.activeTenantId();
        if (!activeTenantId.equals(tenantId.trim())) {
            throw IamApiException.forbidden(
                    "AUTH_TENANT_MISMATCH",
                    "The route Tenant does not match the authorized Tenant context",
                    IamPermissions.USER_CREATE);
        }
        context.requireIdempotencyKey();
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.USER_CREATE, "USER", body.authorizationTarget());
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "USER", body.authorizationTarget());
        if (!body.departments().isEmpty() || !body.groups().isEmpty()) {
            guard.requireTenant(context, IamPermissions.MEMBERSHIP_MANAGE, "USER", body.authorizationTarget());
        }
        if (!body.roles().isEmpty()) {
            guard.requireTenant(context, IamPermissions.ROLE_BINDING_MANAGE, "USER", body.authorizationTarget());
        }
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(lifecycle.onboard(body, context));
    }
}
