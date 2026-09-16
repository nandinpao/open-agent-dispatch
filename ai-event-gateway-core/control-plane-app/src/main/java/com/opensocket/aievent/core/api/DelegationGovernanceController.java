package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.DelegationAuthorizationDecision;
import com.opensocket.aievent.core.capability.DelegationAuthorizationRequest;
import com.opensocket.aievent.core.capability.DelegationGovernanceService;
import com.opensocket.aievent.core.capability.DelegationPolicy;
import com.opensocket.aievent.core.capability.DelegationPolicyAuditEvent;
import com.opensocket.aievent.core.capability.DelegationPolicyVersion;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 3 WHO MAY administration API. No provider ranking or execution endpoint exists here. */
@RestController
@RequestMapping("/admin/delegation-governance")
public class DelegationGovernanceController {
    private final DelegationGovernanceService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public DelegationGovernanceController(DelegationGovernanceService service,
                                          ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.service = service;
        this.scopedAccess = scopedAccess;
    }

    @GetMapping("/policies")
    public List<DelegationPolicy> policies(@RequestParam(required = false) String tenantId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String effect,
                                           @RequestParam(required = false) String capabilityCode,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(required = false) String afterPolicyId,
                                           @RequestParam(defaultValue = "100") int limit) {
        return service.listPolicies(effectiveTenant(tenantId), status, effect, capabilityCode, search, afterPolicyId, limit);
    }

    @GetMapping("/policies/{policyId}")
    public DelegationPolicy policy(@PathVariable String policyId, @RequestParam(required = false) String tenantId) {
        return service.findPolicy(effectiveTenant(tenantId), policyId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Delegation Policy not found: " + policyId));
    }

    @PutMapping("/policies/{policyId}")
    public DelegationPolicy upsertPolicy(@PathVariable String policyId,
                                         @RequestParam(required = false) String tenantId,
                                         @RequestHeader(name = "X-Change-Reason", required = false) String reason,
                                         @RequestBody(required = false) DelegationPolicy request) {
        try {
            return service.upsertPolicy(effectiveTenant(tenantId), policyId, request, reason);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/policies/{policyId}/versions")
    public List<DelegationPolicyVersion> policyVersions(@PathVariable String policyId,
                                                         @RequestParam(required = false) String tenantId) {
        return service.policyVersions(effectiveTenant(tenantId), policyId);
    }

    @GetMapping("/policies/{policyId}/audit-events")
    public List<DelegationPolicyAuditEvent> auditEvents(@PathVariable String policyId,
                                                         @RequestParam(required = false) String tenantId) {
        return service.auditEvents(effectiveTenant(tenantId), policyId);
    }

    @PostMapping("/evaluate-preview")
    public DelegationAuthorizationDecision evaluatePreview(@RequestParam(required = false) String tenantId,
                                                            @RequestBody(required = false) DelegationAuthorizationRequest request) {
        try {
            return service.evaluatePreview(effectiveTenant(tenantId), request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
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
