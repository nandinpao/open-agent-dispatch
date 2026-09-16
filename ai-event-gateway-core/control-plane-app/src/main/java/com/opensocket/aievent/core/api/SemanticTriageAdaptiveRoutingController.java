package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.SemanticTriageAdaptiveRoutingService;
import com.opensocket.aievent.core.capability.SemanticTriageRuntimeDecision;
import com.opensocket.aievent.core.capability.SemanticTriageRuntimePolicy;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Stage 10 rollout/evidence administration. No endpoint accepts a Provider, Binding, Agent, Peer or Pool target. */
@RestController
@RequestMapping("/admin/semantic-triage/adaptive-routing")
public class SemanticTriageAdaptiveRoutingController {
    private final SemanticTriageAdaptiveRoutingService service; private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped;
    public SemanticTriageAdaptiveRoutingController(SemanticTriageAdaptiveRoutingService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped){this.service=service;this.scoped=scoped;}
    @GetMapping("/policies") public List<SemanticTriageRuntimePolicy> policies(@RequestParam(required=false)String tenantId){return service.policies(tenant(tenantId));}
    @PutMapping("/policies/{policyId}") public SemanticTriageRuntimePolicy upsert(@PathVariable String policyId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason,@RequestBody(required=false)SemanticTriageRuntimePolicy body){try{return service.upsertPolicy(tenant(tenantId),policyId,body,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @PostMapping("/tasks/{taskId}/evaluate") public SemanticTriageRuntimeDecision evaluate(@PathVariable String taskId,@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="REPLAY")String trigger){try{return service.evaluateTask(tenant(tenantId),taskId,trigger);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @GetMapping("/decisions") public List<SemanticTriageRuntimeDecision> decisions(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskId,@RequestParam(defaultValue="100")int limit){return service.decisions(tenant(tenantId),taskId,limit);}
    private String tenant(String requested){var g=scoped.getIfAvailable();if(g==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=g.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority.");return active;}
}
