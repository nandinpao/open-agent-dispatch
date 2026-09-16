package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Phase 11 Human Admin console for runtime policy, SHADOW evidence and human certification. Runtime bootstrap uses FastPathRuntimePort internally. */
@RestController
@RequestMapping("/admin/fast-path-runtime")
public class FastPathRuntimeController {
    private final FastPathRuntimeIntegrationService service; private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public FastPathRuntimeController(FastPathRuntimeIntegrationService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}
    @GetMapping("/policies") public List<FastPathRuntimePolicy> policies(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String status,@RequestParam(defaultValue="100")int limit){return service.policies(effectiveTenant(tenantId),status,limit);}
    @PutMapping("/policies/{policyId}") public FastPathRuntimePolicy upsert(@PathVariable String policyId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason,@RequestBody(required=false)FastPathRuntimePolicy body){try{return service.upsertPolicy(effectiveTenant(tenantId),policyId,body,reason);}catch(IllegalArgumentException e){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,e.getMessage());}}
    @GetMapping("/policies/{policyId}/versions") public List<FastPathRuntimePolicyVersion> versions(@PathVariable String policyId,@RequestParam(required=false)String tenantId){return service.policyVersions(effectiveTenant(tenantId),policyId);}
    @PostMapping("/shadow-comparisons") public FastPathShadowComparison compare(@RequestParam(required=false)String tenantId,@RequestBody(required=false)FastPathShadowComparisonRequest body){try{return service.compareShadow(effectiveTenant(tenantId),body);}catch(IllegalArgumentException e){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,e.getMessage());}}
    @GetMapping("/shadow-comparisons") public List<FastPathShadowComparison> comparisons(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String patternId,@RequestParam(defaultValue="100")int limit){return service.shadowComparisons(effectiveTenant(tenantId),patternId,limit);}
    @PostMapping("/patterns/{patternId}/certifications") public FastPathRuntimeCertification certify(@PathVariable String patternId,@RequestParam(required=false)String tenantId,@RequestBody(required=false)FastPathCertificationRequest body){try{return service.certify(effectiveTenant(tenantId),patternId,body);}catch(IllegalArgumentException e){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,e.getMessage());}}
    @GetMapping("/certifications") public List<FastPathRuntimeCertification> certifications(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String patternId,@RequestParam(defaultValue="100")int limit){return service.certifications(effectiveTenant(tenantId),patternId,limit);}
    @GetMapping("/decisions") public List<FastPathRuntimeDecision> decisions(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskRef,@RequestParam(defaultValue="100")int limit){return service.runtimeDecisions(effectiveTenant(tenantId),taskRef,limit);}
    private String effectiveTenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
}
