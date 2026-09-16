package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** C9 read-only capability-first delegation operations API. Runtime authority remains unchanged. */
@RestController
@RequestMapping("/api/a2a-operations/capability-delegations")
public class CapabilityDelegationOperationsController {
    private final CapabilityDelegationOperationsService service;

    public CapabilityDelegationOperationsController(CapabilityDelegationOperationsService service) {
        this.service = service;
    }

    @GetMapping
    public CapabilityDelegationOperationsService.Page search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String capabilityCode,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) String parentTaskId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String requestingAgentId,
            @RequestParam(required = false, name = "q") String text,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            HttpServletRequest request) {
        return service.search(activeTenant(request), status, capabilityCode, providerType, parentTaskId, correlationId,
                requestingAgentId, text, offset, limit, sortDirection);
    }

    @GetMapping("/{delegationId}")
    public CapabilityDelegationOperationsService.Detail detail(
            @PathVariable String delegationId, HttpServletRequest request) {
        return service.detail(activeTenant(request), delegationId);
    }

    /**
     * Resolve the Tenant from R3/R4's request-scoped authentication projection first.
     * INSTANCE_ROOT sessions remain INSTANCE-scoped in the durable session by design;
     * a selected administration workspace is projected only for the current request.
     * Reading SecurityContext directly would therefore discard the authorized
     * X-Tenant-Id projection and incorrectly reject valid Root deep links.
     */
    private String activeTenant(HttpServletRequest request) {
        Object projected = request.getAttribute(IamApiRequestContextFactory.AUTHENTICATION_CONTEXT_ATTRIBUTE);
        if (projected instanceof AuthenticationContext context) {
            String tenant = tenantId(context);
            if (!tenant.isBlank()) return tenant;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticationContext context) {
            String tenant = tenantId(context);
            if (!tenant.isBlank()) return tenant;
        }
        throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,
                "An authenticated active Tenant workspace is required.");
    }

    private String tenantId(AuthenticationContext context) {
        if (context.activeTenant().scope() != TenantRef.Scope.TENANT
                || context.activeTenant().tenantId() == null) return "";
        return context.activeTenant().tenantId().trim();
    }
}
