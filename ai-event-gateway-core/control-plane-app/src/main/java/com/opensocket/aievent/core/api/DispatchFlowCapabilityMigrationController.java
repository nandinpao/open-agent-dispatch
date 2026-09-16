package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.dispatch.flow.FlowCapabilityShadowMigrationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stage 3 operator API for Flow -> Capability shadow migration evidence. */
@RestController
@RequestMapping("/admin/dispatch-flows/{flowId}/capability-migration")
public class DispatchFlowCapabilityMigrationController {
    private final FlowCapabilityShadowMigrationService service;

    public DispatchFlowCapabilityMigrationController(FlowCapabilityShadowMigrationService service) {
        this.service = service;
    }

    @GetMapping
    public FlowCapabilityShadowMigrationService.MigrationState state(
            @PathVariable String flowId,
            @RequestParam String tenantId) {
        return service.state(tenantId, flowId);
    }

    @PutMapping("/state")
    public FlowCapabilityShadowMigrationService.MigrationState changeState(
            @PathVariable String flowId,
            @RequestParam String tenantId,
            @RequestBody FlowCapabilityShadowMigrationService.StateChange request) {
        return service.changeState(tenantId, flowId, request, null);
    }

    @PostMapping("/evaluations")
    public FlowCapabilityShadowMigrationService.ShadowEvaluation evaluate(
            @PathVariable String flowId,
            @RequestParam String tenantId,
            @RequestParam String taskId) {
        return service.evaluateTask(tenantId, flowId, taskId, null);
    }

    @GetMapping("/evaluations")
    public List<FlowCapabilityShadowMigrationService.ShadowEvaluation> evaluations(
            @PathVariable String flowId,
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        return service.evaluations(tenantId, flowId, limit);
    }

    @GetMapping("/readiness")
    public FlowCapabilityShadowMigrationService.MigrationReadiness readiness(
            @PathVariable String flowId,
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "10") int minimumSampleSize) {
        return service.readiness(tenantId, flowId, minimumSampleSize);
    }
}
