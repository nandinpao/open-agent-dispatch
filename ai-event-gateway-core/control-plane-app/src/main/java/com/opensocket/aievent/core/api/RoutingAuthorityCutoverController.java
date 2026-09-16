package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** A0-R6 admin surface. All evaluation is shadow-only; NEW_AUTHORITATIVE is rejected by service and DB. */
@RestController
@RequestMapping("/admin/routing-authority")
public class RoutingAuthorityCutoverController {
    private final RoutingAuthorityCutoverService service;private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public RoutingAuthorityCutoverController(RoutingAuthorityCutoverService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}
    @PostMapping("/plans/{planId}/shadow-evaluate") public RoutingAuthorityShadowResult evaluate(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestBody RoutingAuthorityShadowRequest request){try{return service.evaluateShadow(tenant(tenantId),planId,request);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @PostMapping("/plans/{planId}/rebinding-admission") public RebindingAdmissionDecision rebinding(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestBody RebindingAdmissionRequest request){try{return service.evaluateRebinding(tenant(tenantId),planId,request);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @GetMapping("/plans/{planId}/trace") public List<Map<String,Object>> trace(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="100")int limit){return service.trace(tenant(tenantId),planId,limit);}
    @GetMapping("/flows/migration-state") public List<FlowRoutingMigrationState> migrationStates(@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="200")int limit){return service.migrationStates(tenant(tenantId),limit);}
    @PutMapping("/flows/{flowId}/migration-state") public FlowRoutingMigrationState setMigration(@PathVariable String flowId,@RequestParam String state,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason){try{return service.setFlowMigrationState(tenant(tenantId),flowId,state,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    private String tenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
}
