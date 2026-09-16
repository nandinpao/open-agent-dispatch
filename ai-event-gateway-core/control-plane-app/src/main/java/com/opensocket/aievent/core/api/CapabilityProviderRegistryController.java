package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.CapabilityBinding;
import com.opensocket.aievent.core.capability.CapabilityBindingTrustEvent;
import com.opensocket.aievent.core.capability.CapabilityProvider;
import com.opensocket.aievent.core.capability.CapabilityProviderRegistryService;
import com.opensocket.aievent.core.capability.ManagedAgentProviderLink;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 2 WHO CAN administration API. No authorization/ranking/execution endpoint exists here. */
@RestController
@RequestMapping("/admin/capability-provider-registry")
public class CapabilityProviderRegistryController {
    private final CapabilityProviderRegistryService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public CapabilityProviderRegistryController(CapabilityProviderRegistryService service,
                                                ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.service = service;
        this.scopedAccess = scopedAccess;
    }

    @GetMapping("/providers")
    public List<CapabilityProvider> providers(@RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false) String providerType,
                                             @RequestParam(required = false) String catalogStatus,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) String afterProviderId,
                                             @RequestParam(defaultValue = "100") int limit) {
        return service.listProviders(effectiveTenant(tenantId), providerType, catalogStatus, search, afterProviderId, limit);
    }

    @GetMapping("/providers/{providerId}")
    public CapabilityProvider provider(@PathVariable String providerId, @RequestParam(required = false) String tenantId) {
        return service.findProvider(effectiveTenant(tenantId), providerId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Capability Provider not found: " + providerId));
    }

    @PutMapping("/providers/{providerId}")
    public CapabilityProvider upsertProvider(@PathVariable String providerId,
                                             @RequestParam(required = false) String tenantId,
                                             @RequestBody(required = false) CapabilityProvider request) {
        try {
            return service.upsertProvider(effectiveTenant(tenantId), providerId, request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/providers/{providerId}/managed-agent-link")
    public ManagedAgentProviderLink managedAgentLink(@PathVariable String providerId,
                                                     @RequestParam(required = false) String tenantId) {
        return service.findManagedAgentProviderLink(effectiveTenant(tenantId), providerId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND,
                        "Managed Agent Provider Link not found: " + providerId));
    }

    @PutMapping("/providers/{providerId}/managed-agent-link")
    public ManagedAgentProviderLink upsertManagedAgentLink(@PathVariable String providerId,
                                                           @RequestParam(required = false) String tenantId,
                                                           @RequestBody(required = false) ManagedAgentProviderLink request) {
        try {
            return service.upsertManagedAgentProviderLink(effectiveTenant(tenantId), providerId, request, null);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/bindings")
    public List<CapabilityBinding> bindings(@RequestParam(required = false) String tenantId,
                                           @RequestParam(required = false) String capabilityCode,
                                           @RequestParam(required = false) String providerId,
                                           @RequestParam(required = false) String trustStatus,
                                           @RequestParam(required = false) String afterBindingId,
                                           @RequestParam(defaultValue = "100") int limit) {
        return service.listBindings(effectiveTenant(tenantId), capabilityCode, providerId, trustStatus, afterBindingId, limit);
    }

    @PutMapping("/bindings/{bindingId}")
    public CapabilityBinding upsertBinding(@PathVariable String bindingId,
                                           @RequestParam(required = false) String tenantId,
                                           @RequestHeader(name = "X-Change-Reason", required = false) String reason,
                                           @RequestBody(required = false) CapabilityBinding request) {
        try {
            return service.upsertBinding(effectiveTenant(tenantId), bindingId, request, null, reason);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/bindings/{bindingId}/trust-events")
    public List<CapabilityBindingTrustEvent> trustEvents(@PathVariable String bindingId,
                                                         @RequestParam(required = false) String tenantId) {
        return service.trustEvents(effectiveTenant(tenantId), bindingId);
    }

    private String effectiveTenant(String requestedTenantId) {
        var guard = scopedAccess.getIfAvailable();
        if (guard == null) {
            if (requestedTenantId == null || requestedTenantId.isBlank()) {
                throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, "tenantId is required");
            }
            return requestedTenantId.trim();
        }
        String activeTenantId = guard.activeTenantId();
        if (requestedTenantId != null && !requestedTenantId.isBlank() && !activeTenantId.equals(requestedTenantId.trim())) {
            throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,
                    "Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");
        }
        return activeTenantId;
    }
}
