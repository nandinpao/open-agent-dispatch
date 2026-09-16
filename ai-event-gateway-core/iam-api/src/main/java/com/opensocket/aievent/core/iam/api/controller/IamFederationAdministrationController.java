package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.IamFederationAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.request.LinkExternalIdentityRequest;
import com.opensocket.aievent.core.iam.api.request.UpdateFederationPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.UpsertAuthenticationProviderRequest;
import com.opensocket.aievent.core.iam.api.response.AuthenticationProviderResponse;
import com.opensocket.aievent.core.iam.api.response.ExternalIdentityLinkResponse;
import com.opensocket.aievent.core.iam.api.response.FederationPolicyResponse;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.api.security.IamPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-owned enterprise authentication configuration and explicit identity linking. */
@RestController
@RequestMapping("/api/admin/access/tenants/{tenantId}/federation")
@ConditionalOnBean({IamFederationAdministrationApiPort.class, IamPermissionGuard.class})
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamFederationAdministrationController {
    private final IamFederationAdministrationApiPort federation;
    private final IamPermissionGuard guard;
    private final IamApiRequestContextFactory contexts;

    public IamFederationAdministrationController(
            IamFederationAdministrationApiPort federation,
            IamPermissionGuard guard,
            IamApiRequestContextFactory contexts) {
        this.federation = federation;
        this.guard = guard;
        this.contexts = contexts;
    }

    @GetMapping("/providers")
    public List<AuthenticationProviderResponse> providers(
            @PathVariable String tenantId, HttpServletRequest request) {
        IamApiRequestContext context = read(tenantId, request);
        return federation.providers(tenantId, context);
    }

    @PostMapping("/providers")
    public ResponseEntity<AuthenticationProviderResponse> createProvider(
            @PathVariable String tenantId,
            @Valid @RequestBody UpsertAuthenticationProviderRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = manage(tenantId, request);
        return ResponseEntity.status(201).body(federation.upsertProvider(tenantId, body, 0, context));
    }

    @PutMapping("/providers/{providerId}")
    public AuthenticationProviderResponse updateProvider(
            @PathVariable String tenantId,
            @PathVariable String providerId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpsertAuthenticationProviderRequest body,
            HttpServletRequest request) {
        if (!providerId.equals(body.providerId())) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_PROVIDER_ID_IMMUTABLE",
                    "Provider ID in the path and request body must match.");
        }
        IamApiRequestContext context = manage(tenantId, request);
        return federation.upsertProvider(tenantId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/providers/{providerId}/status/{status}")
    public AuthenticationProviderResponse providerStatus(
            @PathVariable String tenantId,
            @PathVariable String providerId,
            @PathVariable String status,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = manage(tenantId, request);
        return federation.changeProviderStatus(
                tenantId, providerId, status, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/policy")
    public FederationPolicyResponse policy(@PathVariable String tenantId, HttpServletRequest request) {
        return federation.policy(tenantId, read(tenantId, request));
    }

    @PutMapping("/policy")
    public FederationPolicyResponse updatePolicy(
            @PathVariable String tenantId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateFederationPolicyRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = manage(tenantId, request);
        return federation.updatePolicy(tenantId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/users/{userId}/links")
    public List<ExternalIdentityLinkResponse> userLinks(
            @PathVariable String tenantId,
            @PathVariable String userId,
            HttpServletRequest request) {
        return federation.userLinks(tenantId, userId, read(tenantId, request));
    }

    @PostMapping("/users/{userId}/links")
    public ResponseEntity<ExternalIdentityLinkResponse> linkUser(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody LinkExternalIdentityRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(201).body(federation.linkUser(tenantId, userId, body, manage(tenantId, request)));
    }

    @DeleteMapping("/users/{userId}/links/{credentialLinkId}")
    public ResponseEntity<Void> unlinkUser(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @PathVariable String credentialLinkId,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = manage(tenantId, request);
        federation.unlinkUser(
                tenantId, userId, credentialLinkId, context.requireExpectedVersion(ifMatch), context);
        return ResponseEntity.noContent().build();
    }

    private IamApiRequestContext read(String tenantId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requirePathTenant(tenantId, context);
        guard.requireTenant(context, IamPermissions.POLICY_READ, "AUTHENTICATION_FEDERATION", tenantId);
        return context;
    }

    private IamApiRequestContext manage(String tenantId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requirePathTenant(tenantId, context);
        context.requireAuditReason();
        context.requireIdempotencyKey();
        guard.requireTenant(context, IamPermissions.POLICY_MANAGE, "AUTHENTICATION_FEDERATION", tenantId);
        return context;
    }

    private static void requirePathTenant(String tenantId, IamApiRequestContext context) {
        if (!tenantId.equals(context.activeTenantId())) {
            throw IamApiException.forbidden(
                    "AUTH_TENANT_MISMATCH",
                    "The requested Tenant does not match the active authenticated Tenant.",
                    "");
        }
    }
}
