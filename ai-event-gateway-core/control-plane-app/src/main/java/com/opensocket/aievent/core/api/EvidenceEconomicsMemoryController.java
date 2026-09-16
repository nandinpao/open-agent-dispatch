package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.observability.EvidenceEconomicsMemoryService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Stage 11 read-mostly observability surface. It has no Dispatch/Assignment/Plan/Learning/Fast-Path operation. */
@RestController
@RequestMapping("/admin/observability")
public class EvidenceEconomicsMemoryController {
    private final EvidenceEconomicsMemoryService service;private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped;
    public EvidenceEconomicsMemoryController(EvidenceEconomicsMemoryService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped){this.service=service;this.scoped=scoped;}
    @GetMapping("/decision-trace") public List<Map<String,Object>> trace(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskId,@RequestParam(required=false)String runId,@RequestParam(required=false)String delegationId,@RequestParam(defaultValue="500")int limit){return service.decisionTrace(tenant(tenantId),taskId,runId,delegationId,limit);}
    @GetMapping("/economics/ledger") public List<Map<String,Object>> ledger(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskId,@RequestParam(required=false)String contextType,@RequestParam(required=false)String contextId,@RequestParam(defaultValue="500")int limit){return service.costLedger(tenant(tenantId),taskId,contextType,contextId,limit);}
    @PostMapping("/economics/ledger") public Map<String,Object> append(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){return service.appendCost(tenant(tenantId),body);}
    @GetMapping("/economics/summary") public List<Map<String,Object>> summary(@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="500")int limit){return service.economicsSummary(tenant(tenantId),limit);}
    @GetMapping("/economics/completeness") public List<Map<String,Object>> completeness(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String taskId,@RequestParam(defaultValue="500")int limit){return service.costCompleteness(tenant(tenantId),taskId,limit);}
    @GetMapping("/retention-policy") public Map<String,Object> retention(@RequestParam(required=false)String tenantId){return service.retentionPolicy(tenant(tenantId));}
    @PutMapping("/retention-policy") public Map<String,Object> retention(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){return service.upsertRetentionPolicy(tenant(tenantId),body);}
    @GetMapping("/archive-segments") public List<Map<String,Object>> archives(@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="200")int limit){return service.archiveSegments(tenant(tenantId),limit);}
    @PostMapping("/archive-segments") public Map<String,Object> archive(@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){return service.registerArchiveSegment(tenant(tenantId),body);}
    @PostMapping("/archive-segments/{segmentId}/verify") public Map<String,Object> verifyArchive(@PathVariable String segmentId,@RequestParam(required=false)String tenantId,@RequestBody Map<String,Object> body){return service.verifyArchiveSegment(tenant(tenantId),segmentId,body);}
    @PostMapping("/execution-memory/refresh") public Map<String,Object> refresh(@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="30")int days){return service.refreshExecutionMemory(tenant(tenantId),days);}
    @GetMapping("/execution-memory") public List<Map<String,Object>> memory(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String capabilityCode,@RequestParam(required=false)String providerType,@RequestParam(defaultValue="500")int limit){return service.executionMemory(tenant(tenantId),capabilityCode,providerType,limit);}
    private String tenant(String requested){var g=scoped.getIfAvailable();if(g==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=g.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority.");return active;}
}
