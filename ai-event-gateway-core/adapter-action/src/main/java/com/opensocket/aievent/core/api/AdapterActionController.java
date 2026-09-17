package com.opensocket.aievent.core.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionProperties;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionService;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionSummary;
import com.opensocket.aievent.core.action.executor.AdapterExecutorCircuitBreaker;
import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;

@RestController
@RequestMapping("/api/adapter-actions")
public class AdapterActionController {
    private final AdapterActionFacade service;
    private final AdapterActionProperties properties;
    private final AdapterActionExecutionService executionService;
    private final AdapterActionExecutionProperties executionProperties;
    private final AdapterExecutorCircuitBreaker circuitBreaker;

    public AdapterActionController(
            AdapterActionFacade service,
            AdapterActionProperties properties,
            AdapterActionExecutionService executionService,
            AdapterActionExecutionProperties executionProperties,
            AdapterExecutorCircuitBreaker circuitBreaker) {
        this.service = service;
        this.properties = properties;
        this.executionService = executionService;
        this.executionProperties = executionProperties;
        this.circuitBreaker = circuitBreaker;
    }

    @GetMapping
    public List<AdapterAction> recent(@RequestParam(defaultValue = "100") int limit) {
        return service.recent(limit);
    }

    @GetMapping("/{actionId}")
    public AdapterAction get(@PathVariable String actionId) {
        return service.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Adapter action not found: " + actionId));
    }

    @GetMapping("/incident/{incidentId}")
    public List<AdapterAction> byIncident(
            @PathVariable String incidentId,
            @RequestParam(defaultValue = "100") int limit) {
        return service.byIncident(incidentId, limit);
    }

    @GetMapping("/task/{taskId}")
    public List<AdapterAction> byTask(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "100") int limit) {
        return service.byTask(taskId, limit);
    }

    @GetMapping("/status/{status}")
    public List<AdapterAction> byStatus(
            @PathVariable AdapterActionStatus status,
            @RequestParam(defaultValue = "100") int limit) {
        return service.byStatus(status, limit);
    }

    @PostMapping("/{actionId}/complete")
    public AdapterAction complete(
            @PathVariable String actionId,
            @RequestBody(required = false) Map<String, String> body) {
        return service.markCompleted(actionId, body == null ? null : body.get("responseRef"));
    }

    @PostMapping("/{actionId}/fail")
    public AdapterAction fail(
            @PathVariable String actionId,
            @RequestBody(required = false) Map<String, String> body) {
        return service.markFailed(actionId, body == null ? null : body.get("error"));
    }

    @PostMapping("/{actionId}/execute")
    public AdapterAction execute(@PathVariable String actionId) {
        return executionService.execute(actionId);
    }

    @PostMapping("/execute-pending")
    public AdapterActionExecutionSummary executePending(@RequestParam(defaultValue = "50") int limit) {
        return executionService.executePending(limit);
    }

    @PostMapping("/{actionId}/retry")
    public AdapterAction retry(
            @PathVariable String actionId,
            @RequestBody(required = false) RetryRequest body) {
        boolean resetAttempts = body != null && Boolean.TRUE.equals(body.resetAttempts());
        String reason = body == null ? null : body.reason();
        return service.retryForWorker(actionId, reason, resetAttempts);
    }

    @PostMapping("/{actionId}/execute-retry")
    public AdapterAction executeRetry(@PathVariable String actionId) {
        return executionService.retry(actionId);
    }

    @PostMapping("/{actionId}/reconcile-uncertain")
    public AdapterAction reconcileUncertain(
            @PathVariable String actionId,
            @RequestBody ProviderOutcomeReconciliationRequest body) {
        if (body == null) throw new IllegalArgumentException("reconciliation body is required");
        return executionService.reconcileUncertainIssueOutcome(actionId, body.resolution(), body.reason(),
                body.issueId(), body.issueUrl(), body.issueStatus(), body.responseRef());
    }


    @PostMapping("/{actionId}/cancel")
    public AdapterAction cancel(
            @PathVariable String actionId,
            @RequestBody(required = false) Map<String, String> body) {
        return service.cancel(actionId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/recover-expired-leases")
    public List<AdapterAction> recoverExpiredLeases(@RequestParam(defaultValue = "100") int limit) {
        return service.recoverExpiredWorkerLeases(limit);
    }

    @GetMapping("/{actionId}/audit")
    public List<AdapterExecutorAuditRecord> auditByAction(
            @PathVariable String actionId,
            @RequestParam(defaultValue = "100") int limit) {
        return service.auditByAction(actionId, limit);
    }

    @GetMapping("/executor-audit")
    public List<AdapterExecutorAuditRecord> executorAudit(@RequestParam(defaultValue = "100") int limit) {
        return service.recentExecutorAudit(limit);
    }

    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("store", service.storeMode());
        map.put("createSuppressedRecords", properties.isCreateSuppressedRecords());
        map.put("mcpEnabled", properties.getMcp().isEnabled());
        map.put("mcpRunOnCompletedTask", properties.getMcp().isRunOnCompletedTask());
        map.put("mcpRunOnFailedTask", properties.getMcp().isRunOnFailedTask());
        map.put("issueEnabled", properties.getIssue().isEnabled());
        map.put("issueCreateOnCompletedTask", properties.getIssue().isCreateOnCompletedTask());
        map.put("issueCreateOnFailedTask", properties.getIssue().isCreateOnFailedTask());
        map.put("issueUpdateExistingIssueComment", properties.getIssue().isUpdateExistingIssueComment());
        map.put("executorMode", executionProperties.getMode());
        map.put("executorEmbeddedMode", executionProperties.isEmbeddedMode());
        map.put("executorExternalMode", executionProperties.isExternalMode());
        map.put("executorDisabledMode", executionProperties.isDisabledMode());
        map.put("executorEnabled", executionProperties.isEnabled());
        map.put("executorAutoExecutePending", executionProperties.isAutoExecutePending());
        map.put("executorBatchSize", executionProperties.getBatchSize());
        map.put("executorMaxAttempts", executionProperties.getMaxAttempts());
        map.put("executorMockEnabled", executionProperties.getMock().isEnabled());
        map.put("executorExecutionTimeout", executionProperties.getExecutionTimeout().toString());
        map.put("executorCircuitBreakerEnabled", executionProperties.getCircuitBreaker().isEnabled());
        map.put("executorCircuitBreakerSnapshot", circuitBreaker.snapshot());
        map.put("executorAuditStore", service.executorAuditStoreMode());
        map.put("workerRetryEnabled", properties.getWorker().isRetryEnabled());
        map.put("workerMaxAttempts", properties.getWorker().getMaxAttempts());
        map.put("workerInitialBackoff", properties.getWorker().getInitialBackoff().toString());
        map.put("workerMaxBackoff", properties.getWorker().getMaxBackoff().toString());
        map.put("workerExpiredLeaseScanBatchSize", properties.getWorker().getExpiredLeaseScanBatchSize());
        map.put("mcpHttpEnabled", executionProperties.getMcp().isHttpEnabled());
        map.put("mcpEndpointConfigured", executionProperties.getMcp().getEndpointUrl() != null
                && !executionProperties.getMcp().getEndpointUrl().isBlank());
        map.put("issueDefaultVendor", executionProperties.getIssue().getDefaultVendor());
        map.put("jiraMockEnabled", executionProperties.getIssue().isJiraMockEnabled());
        map.put("redmineMockEnabled", executionProperties.getIssue().isRedmineMockEnabled());
        map.put("gitlabMockEnabled", executionProperties.getIssue().isGitlabMockEnabled());
        map.put("issueConnectorRuntimeEnabled", executionProperties.getIssue().isConnectorRuntimeEnabled());
        map.put("issueConnectorRuntimeRequired", executionProperties.getIssue().isConnectorRuntimeRequired());
        map.put("issueExecutionAuthority", executionProperties.getIssue().getExecutionAuthority().name());
        map.put("issueCoreGoverned", executionProperties.getIssue().getExecutionAuthority() == com.opensocket.aievent.core.action.executor.AdapterExecutionAuthority.CORE_GOVERNED);
        map.put("issueAutoExecutePending", executionProperties.getIssue().isAutoExecutePending());
        map.put("issueLinkProjectionReconciliationEnabled", executionProperties.getIssue().isLinkProjectionReconciliationEnabled());
        map.put("issueLinkProjectionMaxAttempts", executionProperties.getIssue().getLinkProjectionMaxAttempts());
        map.put("issueExternalWorkerAllowed", false);
        map.put("issueScopedIdentityEnabled", false);
        map.put("issueScopedIdentityRequired", false);
        return map;
    }

    public record RetryRequest(String reason, Boolean resetAttempts) {}
    public record ProviderOutcomeReconciliationRequest(String resolution, String reason, String issueId,
                                                       String issueUrl, String issueStatus, String responseRef) {}
}
