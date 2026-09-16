package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamUserInvitationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.response.UserInvitationStatusResponse;
import com.opensocket.aievent.core.iam.api.request.InvitationDeliveryRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access/tenants/{tenantId}/users/{userId}/invitation")
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamUserInvitationController {
    private final IamUserInvitationApiPort invitations;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public IamUserInvitationController(
            IamUserInvitationApiPort invitations,
            IamPermissionGuard guard,
            IamApiRequestContextFactory contexts) {
        this.invitations = invitations;
        this.guard = guard;
        this.contexts = contexts;
    }

    @GetMapping
    public UserInvitationStatusResponse status(
            @PathVariable String tenantId,
            @PathVariable String userId,
            HttpServletRequest request) {
        var context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.USER_READ, "USER", userId);
        return invitations.status(userId, context);
    }

    @PostMapping("/resend")
    public ResponseEntity<UserInvitationStatusResponse> resend(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody InvitationDeliveryRequest body,
            HttpServletRequest request) {
        var context = tenantContext(request, tenantId);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "USER", userId);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(invitations.resend(userId, body.deliveryMethod(), context));
    }

    @PostMapping("/revoke")
    public UserInvitationStatusResponse revoke(
            @PathVariable String tenantId,
            @PathVariable String userId,
            HttpServletRequest request) {
        var context = tenantContext(request, tenantId);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "USER", userId);
        return invitations.revoke(userId, context);
    }

    private IamApiRequestContext tenantContext(
            HttpServletRequest request, String tenantId) {
        var context = contexts.from(request);
        if (!context.activeTenantId().equals(tenantId.trim())) {
            throw IamApiException.forbidden(
                    "AUTH_TENANT_MISMATCH",
                    "The route Tenant does not match the authorized Tenant context",
                    IamPermissions.USER_READ);
        }
        return context;
    }
}
