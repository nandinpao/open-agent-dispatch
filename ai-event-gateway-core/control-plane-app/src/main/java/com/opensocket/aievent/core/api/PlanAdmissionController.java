package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** MRS A0-R5 Plan Admission administration. No Router/Assignment/Network authority exists here. */
@RestController
@RequestMapping("/admin/plan-admission")
public class PlanAdmissionController {
    private final PlanAdmissionService service;private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public PlanAdmissionController(PlanAdmissionService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}
    @GetMapping("/policies") public List<PlanAdmissionPolicy> policies(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String status,@RequestParam(defaultValue="100")int limit){return service.listPolicies(tenant(tenantId),status,limit);}
    @PutMapping("/policies/{policyId}") public PlanAdmissionPolicy upsertPolicy(@PathVariable String policyId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason,@RequestBody(required=false)PlanAdmissionPolicy request){try{return service.upsertPolicy(tenant(tenantId),policyId,request,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @PostMapping("/plans/{planId}/admit") public PlanAdmissionResult admit(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestParam(required=false)Integer revision){try{return service.admit(tenant(tenantId),planId,revision);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @GetMapping("/plans/{planId}/decisions") public List<PlanAdmissionDecision> decisions(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="100")int limit){return service.decisions(tenant(tenantId),planId,limit);}
    @GetMapping("/plans/{planId}/envelopes") public List<BindingAuthorizationEnvelope> envelopes(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestParam(required=false)Integer revision){return service.envelopes(tenant(tenantId),planId,revision);}
    @GetMapping("/decisions/{decisionId}/evaluations") public List<PlanAdmissionBindingEvaluation> evaluations(@PathVariable String decisionId,@RequestParam(required=false)String tenantId,@RequestParam(required=false)String stepId,@RequestParam(defaultValue="200")int limit){return service.evaluations(tenant(tenantId),decisionId,stepId,limit);}
    @PostMapping("/envelopes/{envelopeId}/revoke") public BindingAuthorizationEnvelope revoke(@PathVariable String envelopeId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason){try{return service.revokeEnvelope(tenant(tenantId),envelopeId,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    private String tenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
}
