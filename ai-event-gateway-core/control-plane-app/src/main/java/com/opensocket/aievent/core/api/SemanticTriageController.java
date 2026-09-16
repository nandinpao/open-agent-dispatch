package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.SemanticTriageService;
import com.opensocket.aievent.core.capability.TriageDecision;
import com.opensocket.aievent.core.capability.TriagePolicy;
import com.opensocket.aievent.core.capability.TriagePolicyVersion;
import com.opensocket.aievent.core.capability.TriagePreviewRequest;
import com.opensocket.aievent.core.capability.TriageProposal;
import com.opensocket.aievent.core.capability.TriageRequest;
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

/** Phase 6 UNKNOWN-problem / Semantic Triage administration API. No provider routing or execution occurs here. */
@RestController
@RequestMapping("/admin/semantic-triage")
public class SemanticTriageController {
    private final SemanticTriageService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public SemanticTriageController(SemanticTriageService service, ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.service = service;
        this.scopedAccess = scopedAccess;
    }

    @GetMapping("/policies")
    public List<TriagePolicy> policies(@RequestParam(required = false) String tenantId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "100") int limit) {
        return service.listPolicies(effectiveTenant(tenantId), status, limit);
    }

    @PutMapping("/policies/{policyId}")
    public TriagePolicy upsertPolicy(@PathVariable String policyId,
                                     @RequestParam(required = false) String tenantId,
                                     @RequestHeader(name = "X-Change-Reason", required = false) String reason,
                                     @RequestBody(required = false) TriagePolicy request) {
        try { return service.upsertPolicy(effectiveTenant(tenantId), policyId, request, reason); }
        catch (IllegalArgumentException ex) { throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage()); }
    }

    @GetMapping("/policies/{policyId}/versions")
    public List<TriagePolicyVersion> policyVersions(@PathVariable String policyId,
                                                     @RequestParam(required = false) String tenantId) {
        return service.policyVersions(effectiveTenant(tenantId), policyId);
    }

    @PostMapping("/resolve-preview")
    public TriageDecision resolvePreview(@RequestParam(required = false) String tenantId,
                                         @RequestBody(required = false) TriagePreviewRequest request) {
        try { return service.resolvePreview(effectiveTenant(tenantId), request); }
        catch (IllegalArgumentException ex) { throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage()); }
    }

    @PostMapping("/requests/{requestId}/proposals")
    public TriageDecision submitProposal(@PathVariable String requestId,
                                         @RequestParam(required = false) String tenantId,
                                         @RequestBody(required = false) TriageProposal proposal) {
        try { return service.submitProposal(effectiveTenant(tenantId), requestId, proposal); }
        catch (IllegalArgumentException ex) { throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage()); }
    }

    @GetMapping("/requests")
    public List<TriageRequest> requests(@RequestParam(required = false) String tenantId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "100") int limit) {
        return service.listRequests(effectiveTenant(tenantId), status, limit);
    }

    @GetMapping("/proposals")
    public List<TriageProposal> proposals(@RequestParam(required = false) String tenantId,
                                          @RequestParam(required = false) String requestId,
                                          @RequestParam(defaultValue = "100") int limit) {
        return service.listProposals(effectiveTenant(tenantId), requestId, limit);
    }

    @GetMapping("/decisions")
    public List<TriageDecision> decisions(@RequestParam(required = false) String tenantId,
                                          @RequestParam(required = false) String requestId,
                                          @RequestParam(defaultValue = "100") int limit) {
        return service.listDecisions(effectiveTenant(tenantId), requestId, limit);
    }

    private String effectiveTenant(String requestedTenantId) {
        var guard = scopedAccess.getIfAvailable();
        if (guard == null) {
            if (requestedTenantId == null || requestedTenantId.isBlank()) throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, "tenantId is required");
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
