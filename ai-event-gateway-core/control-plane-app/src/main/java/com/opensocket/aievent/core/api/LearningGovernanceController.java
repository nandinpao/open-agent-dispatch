package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.GovernedLearningFastPathService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Stage 12 Learning Governance administration. All outputs are recommendation/evidence/semantic-hint only. */
@RestController
@RequestMapping("/admin/learning-governance")
public class LearningGovernanceController {
    private final GovernedLearningFastPathService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public LearningGovernanceController(GovernedLearningFastPathService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}

    @GetMapping("/policies") public List<Map<String,Object>> policies(@RequestParam(required=false)String tenantId){return service.policies(effectiveTenant(tenantId));}
    @PutMapping("/policies/{policyId}") public Map<String,Object> policy(@PathVariable String policyId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason,@RequestBody(required=false)Map<String,Object> body){try{return service.upsertPolicy(effectiveTenant(tenantId),policyId,body==null?Map.of():body,reason);}catch(IllegalArgumentException e){throw bad(e);}}

    @GetMapping("/recommendations") public List<Map<String,Object>> recommendations(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String status,@RequestParam(defaultValue="200")int limit){return service.recommendations(effectiveTenant(tenantId),status,limit);}
    @PostMapping("/recommendations/from-memory") public Map<String,Object> generate(@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="200")int limit){try{return service.generateRecommendationsFromMemory(effectiveTenant(tenantId),limit);}catch(IllegalArgumentException e){throw bad(e);}}
    @PostMapping("/recommendations/{recommendationId}/review") public Map<String,Object> review(@PathVariable String recommendationId,@RequestParam(required=false)String tenantId,@RequestBody(required=false)Map<String,Object> body){try{Map<String,Object>b=body==null?Map.of():body;return service.reviewRecommendation(effectiveTenant(tenantId),recommendationId,String.valueOf(b.getOrDefault("status","")),String.valueOf(b.getOrDefault("reason","")));}catch(IllegalArgumentException e){throw bad(e);}}

    @GetMapping("/fast-path/candidates") public List<Map<String,Object>> candidates(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String status,@RequestParam(defaultValue="200")int limit){return service.candidates(effectiveTenant(tenantId),status,limit);}
    @PostMapping("/fast-path/candidates") public Map<String,Object> createCandidate(@RequestParam(required=false)String tenantId,@RequestBody(required=false)Map<String,Object> body){try{return service.createCandidate(effectiveTenant(tenantId),body==null?Map.of():body);}catch(IllegalArgumentException e){throw bad(e);}}
    @PostMapping("/fast-path/candidates/{candidateId}/replay") public Map<String,Object> replay(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody(required=false)Map<String,Object> body){try{return service.recordReplay(effectiveTenant(tenantId),candidateId,body==null?Map.of():body);}catch(IllegalArgumentException e){throw bad(e);}}
    @PostMapping("/fast-path/candidates/{candidateId}/shadow") public Map<String,Object> shadow(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody(required=false)Map<String,Object> body){try{return service.recordShadow(effectiveTenant(tenantId),candidateId,body==null?Map.of():body);}catch(IllegalArgumentException e){throw bad(e);}}
    @PostMapping("/fast-path/candidates/{candidateId}/certify") public Map<String,Object> certify(@PathVariable String candidateId,@RequestParam(required=false)String tenantId,@RequestBody(required=false)Map<String,Object> body){try{return service.certify(effectiveTenant(tenantId),candidateId,String.valueOf((body==null?Map.of():body).getOrDefault("reason","")));}catch(IllegalArgumentException e){throw bad(e);}}
    @GetMapping("/fast-path/certifications") public List<Map<String,Object>> certifications(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String candidateId,@RequestParam(defaultValue="200")int limit){return service.certifications(effectiveTenant(tenantId),candidateId,limit);}
    @PostMapping("/fast-path/runtime-hints/resolve") public Map<String,Object> hint(@RequestParam(required=false)String tenantId,@RequestBody(required=false)Map<String,Object> body){try{return service.resolveRuntimeHint(effectiveTenant(tenantId),body==null?Map.of():body);}catch(IllegalArgumentException e){throw bad(e);}}
    @GetMapping("/fast-path/runtime-hints") public List<Map<String,Object>> hints(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskRef,@RequestParam(defaultValue="200")int limit){return service.runtimeHints(effectiveTenant(tenantId),taskRef,limit);}

    private String effectiveTenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
    private StandardApiException bad(IllegalArgumentException e){return new StandardApiException(StandardApiErrorCode.BAD_REQUEST,e.getMessage());}
}
