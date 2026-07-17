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

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.DispatchExecutionResult;
import com.opensocket.aievent.core.dispatch.DispatchExecutionService;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.dispatch.DispatchRequestService;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;

@RestController
@RequestMapping("/api/dispatch-requests")
public class DispatchRequestController {
    private final ExecutionOperationalQuery queryService;
    private final DispatchRequestService service;
    private final DispatchProperties properties;
    private final DispatchExecutionService executionService;
    private final DispatchAttemptHistoryService attemptHistoryService;

    public DispatchRequestController(ExecutionOperationalQuery queryService, DispatchRequestService service, DispatchProperties properties, DispatchExecutionService executionService, DispatchAttemptHistoryService attemptHistoryService) {
        this.queryService = queryService;
        this.service = service;
        this.properties = properties;
        this.executionService = executionService;
        this.attemptHistoryService = attemptHistoryService;
    }

    @GetMapping
    public List<DispatchRequest> recent(@RequestParam(defaultValue = "100") int limit) {
        return queryService.recentDispatchRequests(limit);
    }

    @GetMapping("/{dispatchRequestId}")
    public DispatchRequest get(@PathVariable String dispatchRequestId) {
        return queryService.findDispatchRequest(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
    }

    @GetMapping("/status/{status}")
    public List<DispatchRequest> byStatus(@PathVariable DispatchRequestStatus status, @RequestParam(defaultValue = "100") int limit) {
        return queryService.findDispatchRequestsByStatus(status, limit);
    }

    @GetMapping("/task/{taskId}")
    public List<DispatchRequest> byTask(@PathVariable String taskId, @RequestParam(defaultValue = "100") int limit) {
        return queryService.findDispatchRequestsByTask(taskId, limit);
    }

    @GetMapping("/task/{taskId}/history")
    public List<DispatchAttemptHistoryRecord> historyByTask(@PathVariable String taskId, @RequestParam(defaultValue = "100") int limit) {
        return attemptHistoryService.findByTaskId(taskId, Math.max(1, Math.min(limit, 500)));
    }

    @GetMapping("/history/recent")
    public List<DispatchAttemptHistoryRecord> recentHistory(@RequestParam(defaultValue = "100") int limit) {
        return attemptHistoryService.recent(Math.max(1, Math.min(limit, 500)));
    }

    @PostMapping("/{dispatchRequestId}/approve")
    public DispatchRequest approve(@PathVariable String dispatchRequestId, @RequestBody(required = false) Map<String, String> body) {
        return service.approve(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{dispatchRequestId}/reject")
    public DispatchRequest reject(@PathVariable String dispatchRequestId, @RequestBody(required = false) Map<String, String> body) {
        return service.reject(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{dispatchRequestId}/execute")
    public DispatchExecutionResult execute(@PathVariable String dispatchRequestId) {
        return executionService.execute(dispatchRequestId);
    }

    @PostMapping("/{dispatchRequestId}/retry")
    public DispatchRequest retry(@PathVariable String dispatchRequestId,
                                 @RequestBody(required = false) RetryRequest body) {
        boolean resetAttempts = body != null && Boolean.TRUE.equals(body.resetAttempts());
        boolean immediate = body == null || body.immediate() == null || Boolean.TRUE.equals(body.immediate());
        String reason = body == null ? null : body.reason();
        return service.retry(dispatchRequestId, reason, resetAttempts, immediate);
    }

    @PostMapping("/{dispatchRequestId}/dead-letter")
    public DispatchRequest deadLetter(@PathVariable String dispatchRequestId,
                                      @RequestBody(required = false) Map<String, String> body) {
        return service.moveToDeadLetter(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{dispatchRequestId}/cancel")
    public DispatchRequest cancel(@PathVariable String dispatchRequestId,
                                  @RequestBody(required = false) Map<String, String> body) {
        return service.cancel(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    public record RetryRequest(String reason, Boolean resetAttempts, Boolean immediate) {}

    @PostMapping("/execute-approved")
    public List<DispatchExecutionResult> executeApproved(@RequestParam(defaultValue = "50") int limit) {
        return executionService.executeApproved(limit);
    }

    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("store", queryService.dispatchStoreMode());
        metadata.put("requestCreationEnabled", properties.isRequestCreationEnabled());
        metadata.put("reviewMode", properties.getReviewMode().name());
        metadata.put("executionPolicy", properties.getExecutionPolicy().name());
        metadata.put("sourceNodeId", properties.getSourceNodeId());
        metadata.put("gatewayDispatchPath", properties.getGatewayDispatchPath());
        metadata.put("requireAssignableAgent", properties.isRequireAssignableAgent());
        metadata.put("retryEnabled", properties.getRetry().isEnabled());
        metadata.put("retryMaxAttempts", properties.getRetry().getMaxAttempts());
        metadata.put("retryInitialBackoff", properties.getRetry().getInitialBackoff().toString());
        metadata.put("retryMaxBackoff", properties.getRetry().getMaxBackoff().toString());
        metadata.put("retryJitterPercent", properties.getRetry().getJitterPercent());
        metadata.put("runtimeBackoffJitterPercent", properties.getFailureRequeue().getRuntimeJitterPercent());
        metadata.put("poisonAgentFailureThreshold", properties.getFailureRequeue().getPoisonAgentFailureThreshold());
        metadata.put("dispatchClientEnabled", properties.getClient().isEnabled());
        metadata.put("autoExecuteApproved", properties.getClient().isAutoExecuteApproved());
        metadata.put("autoExecutionActive", properties.getClient().isEnabled() && properties.getExecutionPolicy().autoExecutes());
        metadata.put("dispatchClientBlocked", !properties.getClient().isEnabled() || !properties.getExecutionPolicy().autoExecutes());
        metadata.put("defaultGatewayBaseUrl", properties.getClient().getDefaultGatewayBaseUrl());
        metadata.put("gatewayBaseUrls", properties.getClient().getGatewayBaseUrls());
        metadata.put("internalTokenHeader", properties.getClient().getInternalTokenHeader());
        metadata.put("maxBatchSize", properties.getClient().getMaxBatchSize());
        return metadata;
    }
}
