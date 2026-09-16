package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.RuntimeStepAuthorityDecision;
import com.opensocket.aievent.core.capability.RuntimeStepAuthorityPolicy;
import com.opensocket.aievent.core.capability.RuntimeStepAuthorityPolicyVersion;
import com.opensocket.aievent.core.capability.RuntimeStepAuthorityAutomationService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Phase 12 administration surface for policy/evidence only. Runtime candidate/provider selection is server-side. */
@RestController
@RequestMapping("/admin/runtime-step-authority")
public class RuntimeStepAuthorityController {
    private final RuntimeStepAuthorityAutomationService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public RuntimeStepAuthorityController(RuntimeStepAuthorityAutomationService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}

    @GetMapping("/policies") public List<RuntimeStepAuthorityPolicy> policies(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String status,@RequestParam(defaultValue="100")int limit){return service.listPolicies(tenant(tenantId),status,limit);}
    @PutMapping("/policies/{policyId}") public RuntimeStepAuthorityPolicy upsert(@PathVariable String policyId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason,@RequestBody(required=false)RuntimeStepAuthorityPolicy request){try{return service.upsertPolicy(tenant(tenantId),policyId,request,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @GetMapping("/policies/{policyId}/versions") public List<RuntimeStepAuthorityPolicyVersion> versions(@PathVariable String policyId,@RequestParam(required=false)String tenantId){return service.policyVersions(tenant(tenantId),policyId);}
    @GetMapping("/decisions") public List<RuntimeStepAuthorityDecision> decisions(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String runId,@RequestParam(required=false)String stepId,@RequestParam(defaultValue="200")int limit){return service.decisions(tenant(tenantId),runId,stepId,limit);}

    private String tenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
}
