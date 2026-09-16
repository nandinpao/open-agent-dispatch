package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.CapabilityDefinition;
import com.opensocket.aievent.core.capability.CapabilityRequirement;
import com.opensocket.aievent.core.capability.CanonicalCapabilityManagementService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 1 provider-neutral Canonical Capability administration API. */
@RestController
@RequestMapping("/admin/capability-definitions")
public class CanonicalCapabilityController {
    private final CanonicalCapabilityManagementService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public CanonicalCapabilityController(CanonicalCapabilityManagementService service,
                                         ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.service = service;
        this.scopedAccess = scopedAccess;
    }

    @GetMapping
    public List<CapabilityDefinition> list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(defaultValue = "200") int limit) {
        return service.list(effectiveTenant(tenantId), status, search, serviceCode, limit);
    }

    @GetMapping("/{capabilityCode}")
    public CapabilityDefinition detail(@PathVariable String capabilityCode,
                                       @RequestParam(required = false) String tenantId) {
        return service.find(effectiveTenant(tenantId), capabilityCode)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND,
                        "Capability Definition not found: " + capabilityCode));
    }

    @PutMapping("/{capabilityCode}")
    public CapabilityDefinition upsert(@PathVariable String capabilityCode,
                                       @RequestParam(required = false) String tenantId,
                                       @RequestBody(required = false) CapabilityDefinition request) {
        try {
            return service.upsert(effectiveTenant(tenantId), capabilityCode, request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/requirements/by-service-code/{serviceCode}")
    public List<CapabilityRequirement> requirementsByServiceCode(
            @PathVariable String serviceCode,
            @RequestParam(required = false) String tenantId) {
        return service.requirementsForServiceCode(effectiveTenant(tenantId), serviceCode);
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
