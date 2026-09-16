package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.service.IamTokenAdministrationService;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.pagination.CursorPage;
import com.opensocket.aievent.core.iam.api.pagination.IamPaginationPolicy;
import com.opensocket.aievent.core.iam.api.request.CreateServiceAccountRequest;
import com.opensocket.aievent.core.iam.api.request.CreateServiceAccountCredentialRequest;
import com.opensocket.aievent.core.iam.api.request.UpdateServiceAccountMachineBoundaryRequest;
import com.opensocket.aievent.core.iam.api.request.IssueTokenRequest;
import com.opensocket.aievent.core.iam.api.request.RevokeTokenRequest;
import com.opensocket.aievent.core.iam.api.request.RotateTokenRequest;
import com.opensocket.aievent.core.iam.api.response.IssuedTokenResponse;
import com.opensocket.aievent.core.iam.api.response.IssuedServiceAccountCredentialResponse;
import com.opensocket.aievent.core.iam.api.response.ServiceAccountCredentialResponse;
import com.opensocket.aievent.core.iam.api.response.ServiceAccountResponse;
import com.opensocket.aievent.core.iam.api.response.TokenSummaryResponse;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access/security")
@ConditionalOnBean({
        IamTokenAdministrationService.class,
        IamAdministrationProjectionPort.class,
        IamPermissionGuard.class})
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamTokenController {
    private final IamTokenAdministrationService service;
    private final IamAdministrationProjectionPort projections;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;
    private final IamPaginationPolicy pagination;

    public IamTokenController(
            IamTokenAdministrationService service,
            IamAdministrationProjectionPort projections,
            IamPermissionGuard guard,
            IamApiRequestContextFactory contexts,
            IamPaginationPolicy pagination) {
        this.service = service;
        this.projections = projections;
        this.guard = guard;
        this.contexts = contexts;
        this.pagination = pagination;
    }

    @GetMapping("/service-accounts")
    public CursorPage<ServiceAccountResponse> accounts(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        var context = contexts.from(request);
        guard.requireTenant(context, IamPermissions.TOKEN_READ, "SERVICE_ACCOUNT", "");
        return projections.serviceAccounts(
                context.activeTenantId(),
                pagination.limit(limit),
                cursor,
                status);
    }

    @PostMapping("/service-accounts")
    public ResponseEntity<ServiceAccountResponse> createAccount(
            @Valid @RequestBody CreateServiceAccountRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "SERVICE_ACCOUNT", body.serviceAccountId());
        return ResponseEntity.status(201).body(service.createServiceAccount(body, context));
    }


    @PostMapping("/service-accounts/{id}/machine-boundary")
    public ServiceAccountResponse updateMachineBoundary(
            @PathVariable String id,
            @Valid @RequestBody UpdateServiceAccountMachineBoundaryRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "SERVICE_ACCOUNT", id);
        return service.updateMachineBoundary(id, body, context);
    }

    @GetMapping("/service-accounts/{id}/credentials")
    public CursorPage<ServiceAccountCredentialResponse> credentials(
            @PathVariable String id,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        var context = contexts.from(request);
        guard.requireTenant(context, IamPermissions.TOKEN_READ, "SERVICE_ACCOUNT", id);
        return projections.serviceAccountCredentials(
                context.activeTenantId(), id, pagination.limit(limit), cursor, status);
    }

    @PostMapping("/service-accounts/{id}/credentials")
    public ResponseEntity<IssuedServiceAccountCredentialResponse> issueCredential(
            @PathVariable String id,
            @Valid @RequestBody CreateServiceAccountCredentialRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "SERVICE_ACCOUNT", id);
        return ResponseEntity.status(201).body(service.issueCredential(id, body, context));
    }

    @PostMapping("/service-accounts/{id}/credentials/{credentialId}/rotate")
    public IssuedServiceAccountCredentialResponse rotateCredential(
            @PathVariable String id,
            @PathVariable String credentialId,
            @Valid @RequestBody RotateTokenRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "SERVICE_ACCOUNT", id);
        return service.rotateCredential(id, credentialId, body, context);
    }

    @PostMapping("/service-accounts/{id}/credentials/{credentialId}/revoke")
    public ResponseEntity<Void> revokeCredential(
            @PathVariable String id,
            @PathVariable String credentialId,
            @Valid @RequestBody RevokeTokenRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "SERVICE_ACCOUNT", id);
        service.revokeCredential(id, credentialId, body, context);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tokens")
    public CursorPage<TokenSummaryResponse> tokens(
            @RequestParam(defaultValue = "") String principalId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        var context = contexts.from(request);
        guard.requireTenant(context, IamPermissions.TOKEN_READ, "TOKEN", "");
        return projections.tokens(
                context.activeTenantId(),
                principalId,
                pagination.limit(limit),
                cursor,
                status);
    }

    @PostMapping("/tokens/personal")
    public ResponseEntity<IssuedTokenResponse> personal(
            @Valid @RequestBody IssueTokenRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "TOKEN", "");
        return ResponseEntity.status(201).body(service.issuePersonal(body, context));
    }

    @PostMapping("/service-accounts/{id}/tokens")
    public ResponseEntity<IssuedTokenResponse> service(
            @PathVariable String id,
            @Valid @RequestBody IssueTokenRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "SERVICE_ACCOUNT", id);
        return ResponseEntity.status(201).body(service.issueService(id, body, context));
    }

    @PostMapping("/tokens/{id}/rotate")
    public IssuedTokenResponse rotate(
            @PathVariable String id,
            @Valid @RequestBody RotateTokenRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "TOKEN", id);
        return service.rotate(id, body, context);
    }

    @PostMapping("/tokens/{id}/revoke")
    public ResponseEntity<Void> revoke(
            @PathVariable String id,
            @Valid @RequestBody RevokeTokenRequest body,
            HttpServletRequest request) {
        var context = contexts.from(request);
        context.requireAuditReason();
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "TOKEN", id);
        service.revoke(id, body, context);
        return ResponseEntity.noContent().build();
    }
}
