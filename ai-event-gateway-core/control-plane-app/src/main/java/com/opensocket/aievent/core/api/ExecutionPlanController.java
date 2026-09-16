package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.ExecutionPlan;
import com.opensocket.aievent.core.capability.ExecutionPlanAmendmentRequest;
import com.opensocket.aievent.core.capability.ExecutionPlanDecision;
import com.opensocket.aievent.core.capability.ExecutionPlanPolicy;
import com.opensocket.aievent.core.capability.ExecutionPlanPolicyVersion;
import com.opensocket.aievent.core.capability.ExecutionPlanPreviewRequest;
import com.opensocket.aievent.core.capability.ExecutionPlanProposal;
import com.opensocket.aievent.core.capability.ExecutionPlanRequest;
import com.opensocket.aievent.core.capability.ExecutionPlanRevision;
import com.opensocket.aievent.core.capability.ExecutionPlanService;
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

/** Phase 7 semantic Execution Plan administration API. No Provider routing or execution occurs here. */
@RestController
@RequestMapping("/admin/execution-plans")
public class ExecutionPlanController {
    private final ExecutionPlanService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public ExecutionPlanController(ExecutionPlanService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}

    @GetMapping("/policies")
    public List<ExecutionPlanPolicy> policies(@RequestParam(required=false) String tenantId,@RequestParam(required=false) String status,@RequestParam(defaultValue="100") int limit){return service.listPolicies(effectiveTenant(tenantId),status,limit);}

    @PutMapping("/policies/{policyId}")
    public ExecutionPlanPolicy upsertPolicy(@PathVariable String policyId,@RequestParam(required=false) String tenantId,@RequestHeader(name="X-Change-Reason",required=false) String reason,@RequestBody(required=false) ExecutionPlanPolicy request){try{return service.upsertPolicy(effectiveTenant(tenantId),policyId,request,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}

    @GetMapping("/policies/{policyId}/versions")
    public List<ExecutionPlanPolicyVersion> policyVersions(@PathVariable String policyId,@RequestParam(required=false) String tenantId){return service.policyVersions(effectiveTenant(tenantId),policyId);}

    @PostMapping("/resolve-preview")
    public ExecutionPlanDecision resolvePreview(@RequestParam(required=false) String tenantId,@RequestBody(required=false) ExecutionPlanPreviewRequest request){try{return service.resolvePreview(effectiveTenant(tenantId),request);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}

    @PostMapping("/requests/{requestId}/proposals")
    public ExecutionPlanDecision submitProposal(@PathVariable String requestId,@RequestParam(required=false) String tenantId,@RequestBody(required=false) ExecutionPlanProposal proposal){try{return service.submitProposal(effectiveTenant(tenantId),requestId,proposal);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}

    @PostMapping("/{planId}/amendments")
    public ExecutionPlanDecision amendPlan(@PathVariable String planId,@RequestParam(required=false) String tenantId,@RequestBody(required=false) ExecutionPlanAmendmentRequest amendment){try{return service.amendPlan(effectiveTenant(tenantId),planId,amendment);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}

    @GetMapping
    public List<ExecutionPlan> plans(@RequestParam(required=false) String tenantId,@RequestParam(required=false) String status,@RequestParam(required=false) String afterPlanId,@RequestParam(defaultValue="100") int limit){return service.listPlans(effectiveTenant(tenantId),status,afterPlanId,limit);}

    @GetMapping("/{planId}")
    public ExecutionPlan plan(@PathVariable String planId,@RequestParam(required=false) String tenantId){return service.findPlan(effectiveTenant(tenantId),planId).orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Execution Plan not found"));}

    @GetMapping("/{planId}/revisions")
    public List<ExecutionPlanRevision> revisions(@PathVariable String planId,@RequestParam(required=false) String tenantId){return service.revisions(effectiveTenant(tenantId),planId);}

    @GetMapping("/requests")
    public List<ExecutionPlanRequest> requests(@RequestParam(required=false) String tenantId,@RequestParam(required=false) String status,@RequestParam(defaultValue="100") int limit){return service.listRequests(effectiveTenant(tenantId),status,limit);}

    @GetMapping("/proposals")
    public List<ExecutionPlanProposal> proposals(@RequestParam(required=false) String tenantId,@RequestParam(required=false) String requestId,@RequestParam(defaultValue="100") int limit){return service.listProposals(effectiveTenant(tenantId),requestId,limit);}

    @GetMapping("/decisions")
    public List<ExecutionPlanDecision> decisions(@RequestParam(required=false) String tenantId,@RequestParam(required=false) String requestId,@RequestParam(defaultValue="100") int limit){return service.listDecisions(effectiveTenant(tenantId),requestId,limit);}

    private String effectiveTenant(String requestedTenantId){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requestedTenantId==null||requestedTenantId.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requestedTenantId.trim();}String active=guard.activeTenantId();if(requestedTenantId!=null&&!requestedTenantId.isBlank()&&!active.equals(requestedTenantId.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
}
