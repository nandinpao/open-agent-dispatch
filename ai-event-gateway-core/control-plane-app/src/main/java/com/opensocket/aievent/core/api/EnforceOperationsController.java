package com.opensocket.aievent.core.api;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.enforce.EnforceArtifactRetentionRecord;
import com.opensocket.aievent.core.enforce.EnforceLegacyFinalReportItem;
import com.opensocket.aievent.core.enforce.EnforceObservabilitySnapshot;
import com.opensocket.aievent.core.enforce.EnforceOperationsService;
import com.opensocket.aievent.core.enforce.EnforceOperatorIncidentRequest;
import com.opensocket.aievent.core.enforce.EnforceOperatorIncidentResult;
import com.opensocket.aievent.core.enforce.EnforceRoutingAuditRecord;

@RestController
@RequestMapping("/admin/enforce")
public class EnforceOperationsController {
    private final EnforceOperationsService service;

    public EnforceOperationsController(EnforceOperationsService service) {
        this.service = service;
    }

    @GetMapping("/observability")
    public EnforceObservabilitySnapshot observability() {
        return service.observabilitySnapshot();
    }

    @GetMapping("/routing-audit")
    public List<EnforceRoutingAuditRecord> routingAudit(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String blockingCode,
            @RequestParam(required = false) String policyCode,
            @RequestParam(required = false, defaultValue = "24h") String window,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return service.searchRoutingAudit(taskId, agentId, blockingCode, policyCode, window, limit);
    }

    @PostMapping("/incidents")
    public EnforceOperatorIncidentResult createIncident(
            @RequestBody EnforceOperatorIncidentRequest request,
            Principal principal) {
        return service.createOperatorIncident(request, principal == null ? null : principal.getName());
    }

    @GetMapping("/legacy-final-report")
    public List<EnforceLegacyFinalReportItem> legacyFinalReport() {
        return service.legacyFinalReport();
    }

    @GetMapping("/artifact-retention")
    public List<EnforceArtifactRetentionRecord> artifactRetention() {
        return service.artifactRetention();
    }
}
