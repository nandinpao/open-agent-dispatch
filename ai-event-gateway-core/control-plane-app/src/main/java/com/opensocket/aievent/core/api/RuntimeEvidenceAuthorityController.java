package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.evidence.EvidencePayloadDispositionRequest;
import com.opensocket.aievent.core.evidence.ExecutionEvidenceAppendRequest;
import com.opensocket.aievent.core.evidence.ExecutionEvidenceAppendResult;
import com.opensocket.aievent.core.evidence.RuntimeAcceptanceEvidenceRequest;
import com.opensocket.aievent.core.evidence.RuntimeEvidenceAuthorityService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** A0-R8 evidence/isolation/runtime-acceptance surface. No routing, assignment, dispatch or network authority. */
@RestController
@RequestMapping("/admin/runtime-acceptance")
public class RuntimeEvidenceAuthorityController {
    private final RuntimeEvidenceAuthorityService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped;
    public RuntimeEvidenceAuthorityController(RuntimeEvidenceAuthorityService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped){this.service=service;this.scoped=scoped;}

    @PostMapping("/evidence")
    public ExecutionEvidenceAppendResult append(@RequestParam(required=false)String tenantId,@RequestBody ExecutionEvidenceAppendRequest body){
        String t=tenant(tenantId);return service.append(new ExecutionEvidenceAppendRequest(t,body.evidenceType(),body.sourceFamily(),body.sourceRef(),body.taskId(),body.planId(),body.planRevision(),body.stepId(),body.assignmentId(),body.dispatchIntentId(),body.decisionId(),body.policySnapshotRef(),body.actorType(),body.actorId(),body.classification(),body.contentType(),body.payload(),body.digestKeyRef(),body.digestKeyVersion(),body.retentionPolicyRef(),body.details(),body.occurredAt()));
    }
    @GetMapping("/evidence")
    public List<Map<String,Object>> evidence(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskId,@RequestParam(defaultValue="500")int limit){return service.evidence(tenant(tenantId),taskId,limit);}
    @PostMapping("/payloads/{payloadHandle}/disposition")
    public Map<String,Object> dispose(@PathVariable String payloadHandle,@RequestParam(required=false)String tenantId,@RequestBody EvidencePayloadDispositionRequest body){return service.dispose(new EvidencePayloadDispositionRequest(tenant(tenantId),payloadHandle,body.disposition(),body.reason(),body.legalHoldRef()));}
    @PostMapping("/digest-keys")
    public Map<String,Object> digestKey(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){return service.registerDigestKey(tenant(tenantId),body);}
    @PostMapping("/runs")
    public Map<String,Object> record(@RequestParam(required=false)String tenantId,@RequestBody RuntimeAcceptanceEvidenceRequest body){return service.recordAcceptance(new RuntimeAcceptanceEvidenceRequest(tenant(tenantId),body.scenarioCode(),body.result(),body.environmentRef(),body.evidenceRef(),body.details()));}
    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam(required=false)String tenantId){return service.acceptanceSummary(tenant(tenantId));}

    private String tenant(String requested){var g=scoped.getIfAvailable();if(g==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=g.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority.");return active;}
}
