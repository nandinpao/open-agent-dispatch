package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.ProviderEligibilityObservation;
import com.opensocket.aievent.core.capability.ProviderRoutingDecision;
import com.opensocket.aievent.core.capability.ProviderRoutingPreviewRequest;
import com.opensocket.aievent.core.capability.ProviderRoutingService;
import com.opensocket.aievent.core.capability.RoutingProfile;
import com.opensocket.aievent.core.capability.RoutingProfileVersion;
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

/** Phase 4 WHO SHOULD administration API. It ranks but never dispatches or executes. */
@RestController
@RequestMapping("/admin/provider-routing")
public class ProviderRoutingController {
    private final ProviderRoutingService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public ProviderRoutingController(ProviderRoutingService service, ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.service = service;
        this.scopedAccess = scopedAccess;
    }

    @GetMapping("/profiles")
    public List<RoutingProfile> profiles(@RequestParam(required = false) String tenantId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) String afterProfileId,
                                         @RequestParam(defaultValue = "100") int limit) {
        return service.listProfiles(effectiveTenant(tenantId), status, search, afterProfileId, limit);
    }

    @GetMapping("/profiles/{profileId}")
    public RoutingProfile profile(@PathVariable String profileId, @RequestParam(required = false) String tenantId) {
        return service.findProfile(effectiveTenant(tenantId), profileId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Routing Profile not found: " + profileId));
    }

    @PutMapping("/profiles/{profileId}")
    public RoutingProfile upsertProfile(@PathVariable String profileId,
                                         @RequestParam(required = false) String tenantId,
                                         @RequestHeader(name = "X-Change-Reason", required = false) String reason,
                                         @RequestBody(required = false) RoutingProfile request) {
        try { return service.upsertProfile(effectiveTenant(tenantId), profileId, request, reason); }
        catch (IllegalArgumentException ex) { throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage()); }
    }

    @GetMapping("/profiles/{profileId}/versions")
    public List<RoutingProfileVersion> profileVersions(@PathVariable String profileId, @RequestParam(required = false) String tenantId) {
        return service.profileVersions(effectiveTenant(tenantId), profileId);
    }

    @PostMapping("/eligibility-observations")
    public ProviderEligibilityObservation recordObservation(@RequestParam(required = false) String tenantId,
                                                             @RequestBody(required = false) ProviderEligibilityObservation request) {
        try { return service.recordObservation(effectiveTenant(tenantId), request); }
        catch (IllegalArgumentException ex) { throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage()); }
    }

    @GetMapping("/eligibility-observations")
    public List<ProviderEligibilityObservation> observations(@RequestParam(required = false) String tenantId,
                                                              @RequestParam(required = false) String bindingId,
                                                              @RequestParam(defaultValue = "100") int limit) {
        return service.listObservations(effectiveTenant(tenantId), bindingId, limit);
    }

    @PostMapping("/evaluate-preview")
    public ProviderRoutingDecision evaluatePreview(@RequestParam(required = false) String tenantId,
                                                    @RequestBody(required = false) ProviderRoutingPreviewRequest request) {
        try { return service.evaluatePreview(effectiveTenant(tenantId), request); }
        catch (IllegalArgumentException ex) { throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage()); }
    }

    @GetMapping("/decisions")
    public List<ProviderRoutingDecision> decisions(@RequestParam(required = false) String tenantId,
                                                   @RequestParam(required = false) String capabilityCode,
                                                   @RequestParam(defaultValue = "100") int limit) {
        return service.listDecisions(effectiveTenant(tenantId), capabilityCode, limit);
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
