package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.ExecutionSafetyActivationRequest;
import com.opensocket.aievent.core.capability.ExecutionSafetyAuthorityService;
import com.opensocket.aievent.core.capability.FlowRoutingMigrationState;
import com.opensocket.aievent.core.release.ProductionFoundationReleaseService;
import com.opensocket.aievent.core.release.SignedReleaseEvidenceService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Stage 10 Release / Production Foundation authority. No global cutover endpoint exists. */
@RestController
@RequestMapping("/admin/production-foundation")
public class ProductionFoundationReleaseController {
    private final ProductionFoundationReleaseService release;
    private final ExecutionSafetyAuthorityService executionSafety;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped;
    private final SignedReleaseEvidenceService signedEvidence;

    public ProductionFoundationReleaseController(ProductionFoundationReleaseService release,
                                                 ExecutionSafetyAuthorityService executionSafety,
                                                 ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped,
                                                 SignedReleaseEvidenceService signedEvidence){
        this.release=release;this.executionSafety=executionSafety;this.scoped=scoped;this.signedEvidence=signedEvidence;
    }

    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam(required=false)String tenantId){return release.summary(tenant(tenantId));}

    @PostMapping("/cutover-runs")
    public Map<String,Object> recordRun(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.recordCutoverRun(tenant(tenantId),body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @GetMapping("/evidence/trusted-executors")
    public java.util.List<Map<String,Object>> trustedExecutors(@RequestParam(required=false)String tenantId){return signedEvidence.trustedExecutors(tenant(tenantId));}

    @PostMapping("/evidence/trusted-executors")
    public Map<String,Object> registerTrustedExecutor(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return signedEvidence.registerTrustedExecutor(tenant(tenantId),body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/evidence/trusted-executors/{executorId}/{keyId}/revoke")
    public Map<String,Object> revokeTrustedExecutor(@PathVariable String executorId,@PathVariable String keyId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return signedEvidence.revokeTrustedExecutor(tenant(tenantId),executorId,keyId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/evidence/attestations")
    public Map<String,Object> ingestSignedEvidence(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return signedEvidence.ingest(tenant(tenantId),body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @GetMapping("/candidates/{candidateId}/evidence")
    public Map<String,Object> candidateEvidence(@PathVariable String candidateId,@RequestParam(required=false)String tenantId){
        try{return signedEvidence.candidateEvidence(tenant(tenantId),candidateId);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates")
    public Map<String,Object> createCandidate(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.createCandidate(tenant(tenantId),body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/certify")
    public Map<String,Object> certifyCandidate(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.certifyCandidate(tenant(tenantId),candidateId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/preflight")
    public Map<String,Object> candidatePreflight(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.candidatePreflight(tenant(tenantId),candidateId,String.valueOf(body.get("action")));}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/activate")
    public Map<String,Object> activateCandidate(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.activateCandidate(tenant(tenantId),candidateId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/revoke")
    public Map<String,Object> revokeCandidate(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.revokeCandidate(tenant(tenantId),candidateId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/flows/{flowId}/preflight")
    public Map<String,Object> flowPreflight(@PathVariable String flowId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.flowPreflight(tenant(tenantId),flowId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/flows/{flowId}/promote")
    public FlowRoutingMigrationState promoteFlow(@PathVariable String flowId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{
            String t=tenant(tenantId);Map<String,Object> normalized=new java.util.TreeMap<>(body);normalized.put("targetState","NEW_AUTHORITATIVE");
            release.assertFlowOperation(t,flowId,normalized);
            return executionSafety.setFlowAuthority(t,flowId,new ExecutionSafetyActivationRequest("NEW_AUTHORITATIVE",text(body,"reason"),text(body,"releaseCandidateId")));
        }catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/flows/{flowId}/rollback")
    public FlowRoutingMigrationState rollbackFlow(@PathVariable String flowId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{
            String target=String.valueOf(body.get("targetState"));
            if(!"SHADOW".equals(target)&&!"LEGACY_AUTHORITATIVE".equals(target))throw new IllegalArgumentException("Rollback targetState must be SHADOW or LEGACY_AUTHORITATIVE");
            String t=tenant(tenantId);release.assertFlowOperation(t,flowId,body);
            return executionSafety.setFlowAuthority(t,flowId,new ExecutionSafetyActivationRequest(target,text(body,"reason"),null));
        }catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    /** HF5.32C compatibility route retained for discoverability only. All authority mutations require explicit preflight operations. */
    @PutMapping("/flows/{flowId}/authority")
    public FlowRoutingMigrationState setFlowAuthorityLegacyRollbackOnly(@PathVariable String flowId,@RequestParam(required=false)String tenantId,
                                                      @RequestBody ExecutionSafetyActivationRequest request){
        throw bad(new IllegalArgumentException("RELEASE_AUTHORITY_CHANGE_REQUIRES_PREFLIGHT_ENDPOINT: use /preflight then /promote or /rollback"));
    }

    private String text(Map<String,Object> body,String key){Object v=body==null?null:body.get(key);return v==null?null:String.valueOf(v);}
    private StandardApiException bad(RuntimeException ex){return new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}
    private String tenant(String requested){var g=scoped.getIfAvailable();if(g==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=g.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority.");return active;}
}
