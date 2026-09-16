package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.dispatch.flow.FlowCapabilityLegacyEquivalenceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MRS A0-R4 operator API for DIRECT_AGENT compatibility bridge and legacy-equivalence evidence. */
@RestController
@RequestMapping("/admin/dispatch-flows/{flowId}/capability-migration")
public class DispatchFlowCapabilityEquivalenceController {
    private final FlowCapabilityLegacyEquivalenceService service;
    public DispatchFlowCapabilityEquivalenceController(FlowCapabilityLegacyEquivalenceService service){this.service=service;}

    @PostMapping("/compatibility-bridge/refresh")
    public FlowCapabilityLegacyEquivalenceService.BridgeRefresh refreshBridge(@PathVariable String flowId,@RequestParam String tenantId){return service.refreshBridge(tenantId,flowId,null);}
    @GetMapping("/compatibility-bridge")
    public List<FlowCapabilityLegacyEquivalenceService.BridgeRow> bridge(@PathVariable String flowId,@RequestParam String tenantId){return service.bridge(tenantId,flowId);}
    @PostMapping("/legacy-equivalence/evaluate")
    public FlowCapabilityLegacyEquivalenceService.LegacyEquivalenceEvidence evaluate(@PathVariable String flowId,@RequestParam String tenantId,@RequestParam String taskId,@RequestParam(required=false) String assignmentId){return service.evaluateTask(tenantId,flowId,taskId,assignmentId,null);}
    @GetMapping("/legacy-equivalence")
    public List<FlowCapabilityLegacyEquivalenceService.LegacyEquivalenceEvidence> evidence(@PathVariable String flowId,@RequestParam String tenantId,@RequestParam(defaultValue="100") int limit){return service.evidence(tenantId,flowId,limit);}
    @GetMapping("/legacy-equivalence/readiness")
    public FlowCapabilityLegacyEquivalenceService.EquivalenceReadiness readiness(@PathVariable String flowId,@RequestParam String tenantId){return service.readiness(tenantId,flowId);}
    @PostMapping("/legacy-equivalence/backfill")
    public java.util.Map<String,Object> backfill(@PathVariable String flowId,@RequestParam String tenantId,@RequestParam(defaultValue="100") int limit){int queued=service.enqueueHistorical(tenantId,flowId,limit);return java.util.Map.of("flowId",flowId,"queued",queued,"authorityMode","LEGACY_FLOW_DIRECT","shadowOnly",true);}
}
