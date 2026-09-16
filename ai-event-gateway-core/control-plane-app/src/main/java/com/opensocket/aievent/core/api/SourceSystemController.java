package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import com.opensocket.aievent.core.source.SourceSystemManagementService;
import com.opensocket.aievent.core.source.SourceSystemView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/source-systems")
public class SourceSystemController {
    private final SourceSystemManagementService sourceSystemManagementService;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public SourceSystemController(SourceSystemManagementService sourceSystemManagementService,
                                  ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.sourceSystemManagementService = sourceSystemManagementService;
        this.scopedAccess = scopedAccess;
    }

    @GetMapping
    public List<SourceSystemView> list(@RequestParam(required = false) String tenantId) {
        var guard = scopedAccess.getIfAvailable();
        if (guard == null) return sourceSystemManagementService.list(tenantId);
        var plan = guard.plan("admin.source.system.list", ResourceType.SOURCE_SYSTEM, VisibilityLevel.STANDARD, "SOURCE_SYSTEM_LIST");
        requireRequestedTenantMatchesSession(tenantId, guard.activeTenantId());
        return sourceSystemManagementService.list(plan.tenantId(), plan);
    }

    @GetMapping("/{sourceSystemId}")
    public SourceSystemView detail(@PathVariable String sourceSystemId, @RequestParam(required = false) String tenantId) {
        authorize(sourceSystemId, "admin.source.system.detail", ResourceAction.ActionKind.READ, false, "SOURCE_SYSTEM_DETAIL");
        tenantId = effectiveTenant(tenantId);
        return sourceSystemManagementService.find(tenantId, sourceSystemId)
                .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Source System not found: " + sourceSystemId));
    }

    @PostMapping
    public SourceSystemView create(@RequestParam(required = false) String tenantId, @RequestBody(required = false) SourceSystemView request) {
        try {
            tenantId = effectiveTenant(tenantId);
            SourceSystemView body = request == null ? new SourceSystemView() : request;
            body.setTenantId(tenantId);
            var guard = scopedAccess.getIfAvailable();
            if (guard != null) {
                var plan = guard.plan("admin.source.system.create", ResourceType.SOURCE_SYSTEM, VisibilityLevel.STANDARD, "SOURCE_SYSTEM_CREATE_SCOPE");
                sourceSystemManagementService.requireAssignableOwner(plan, tenantId, body);
            }
            return sourceSystemManagementService.create(tenantId, body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{sourceSystemId}")
    public SourceSystemView update(@PathVariable String sourceSystemId, @RequestParam(required = false) String tenantId,
                                   @RequestBody(required = false) SourceSystemView request) {
        try {
            authorize(sourceSystemId, "admin.source.system.update", ResourceAction.ActionKind.UPDATE, true, "SOURCE_SYSTEM_UPDATE");
            tenantId = effectiveTenant(tenantId);
            SourceSystemView body = request == null ? new SourceSystemView() : request;
            body.setTenantId(tenantId); body.setSourceSystemId(sourceSystemId);
            SourceSystemView existing = sourceSystemManagementService.find(tenantId, sourceSystemId)
                    .orElseThrow(() -> new StandardApiException(StandardApiErrorCode.NOT_FOUND, "Source System not found: " + sourceSystemId));
            var guard = scopedAccess.getIfAvailable();
            boolean ownershipChanged = !Objects.equals(normalizedId(existing.getOwnerDepartmentId()), normalizedId(body.getOwnerDepartmentId()))
                    || !Objects.equals(normalizedId(existing.getOwnerGroupId()), normalizedId(body.getOwnerGroupId()));
            if (guard != null && ownershipChanged) {
                var plan = guard.plan("admin.source.system.update", ResourceType.SOURCE_SYSTEM, VisibilityLevel.STANDARD, "SOURCE_SYSTEM_REASSIGN_SCOPE");
                sourceSystemManagementService.requireAssignableOwner(plan, tenantId, body);
            }
            return sourceSystemManagementService.update(tenantId, sourceSystemId, body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{sourceSystemId}")
    public Map<String,Object> retire(@PathVariable String sourceSystemId,@RequestParam(required = false) String tenantId) {
        authorize(sourceSystemId, "admin.source.system.retire", ResourceAction.ActionKind.DELETE, true, "SOURCE_SYSTEM_RETIRE");
        tenantId = effectiveTenant(tenantId);
        sourceSystemManagementService.retire(tenantId, sourceSystemId);
        return Map.of("sourceSystemId",sourceSystemId,"status","RETIRED");
    }


    private String effectiveTenant(String requestedTenantId) {
        var guard = scopedAccess.getIfAvailable();
        if (guard == null) return requestedTenantId;
        String activeTenantId = guard.activeTenantId();
        requireRequestedTenantMatchesSession(requestedTenantId, activeTenantId);
        return activeTenantId;
    }

    private static void requireRequestedTenantMatchesSession(String requestedTenantId, String activeTenantId) {
        if (requestedTenantId != null && !requestedTenantId.isBlank() && !activeTenantId.equals(requestedTenantId.trim())) {
            throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,
                    "Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");
        }
    }

    private static String normalizedId(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void authorize(String id,String permission,ResourceAction.ActionKind kind,boolean sideEffect,String purpose){
        var guard=scopedAccess.getIfAvailable();
        if(guard!=null) guard.authorize(ResourceType.SOURCE_SYSTEM,id,permission,kind,sideEffect,VisibilityLevel.STANDARD,purpose);
    }
}
