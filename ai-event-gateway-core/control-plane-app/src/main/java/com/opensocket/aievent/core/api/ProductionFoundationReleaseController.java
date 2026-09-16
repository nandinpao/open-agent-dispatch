package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.ExecutionSafetyActivationRequest;
import com.opensocket.aievent.core.capability.ExecutionSafetyAuthorityService;
import com.opensocket.aievent.core.capability.FlowRoutingMigrationState;
import com.opensocket.aievent.core.release.ProductionFoundationReleaseService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Stage 10 A0 Release / Production Foundation Cutover Gate. No global cutover endpoint exists. */
@RestController
@RequestMapping("/admin/production-foundation")
public class ProductionFoundationReleaseController {
    private final ProductionFoundationReleaseService release;
    private final ExecutionSafetyAuthorityService executionSafety;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped;

    public ProductionFoundationReleaseController(ProductionFoundationReleaseService release,
                                                 ExecutionSafetyAuthorityService executionSafety,
                                                 ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped){
        this.release=release;this.executionSafety=executionSafety;this.scoped=scoped;
    }

    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam(required=false)String tenantId){return release.summary(tenant(tenantId));}

    @PostMapping("/cutover-runs")
    public Map<String,Object> recordRun(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.recordCutoverRun(tenant(tenantId),body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates")
    public Map<String,Object> createCandidate(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.createCandidate(tenant(tenantId),body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/certify")
    public Map<String,Object> certifyCandidate(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.certifyCandidate(tenant(tenantId),candidateId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/activate")
    public Map<String,Object> activateCandidate(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.activateCandidate(tenant(tenantId),candidateId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/candidates/{candidateId}/revoke")
    public Map<String,Object> revokeCandidate(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){
        try{return release.revokeCandidate(tenant(tenantId),candidateId,body);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PutMapping("/flows/{flowId}/authority")
    public FlowRoutingMigrationState setFlowAuthority(@PathVariable String flowId,@RequestParam(required=false)String tenantId,
                                                      @RequestBody ExecutionSafetyActivationRequest request){
        try{return executionSafety.setFlowAuthority(tenant(tenantId),flowId,request);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    private StandardApiException bad(RuntimeException ex){return new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}
    private String tenant(String requested){var g=scoped.getIfAvailable();if(g==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=g.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority.");return active;}
}
