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
import org.springframework.beans.factory.annotation.Autowired;

import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedDispatchRequestQueryService;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;

import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryRecord;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.DispatchExecutionResult;
import com.opensocket.aievent.core.dispatch.DispatchExecutionService;
import com.opensocket.aievent.core.dispatch.DispatchProperties;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.dispatch.DispatchRequestService;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchRuntimeConfigurationView;

@RestController
@RequestMapping("/api/dispatch-requests")
public class DispatchRequestController {
    private final ExecutionOperationalQuery queryService;
    private final DispatchRequestService service;
    private final DispatchProperties properties;
    private final DispatchRuntimeConfigurationView runtimeConfiguration;
    private final DispatchExecutionService executionService;
    private final DispatchAttemptHistoryService attemptHistoryService;

    @Autowired(required=false)
    private ScopedBusinessResourceAccessCoordinator scopedAccess;
    @Autowired(required=false)
    private ScopedDispatchRequestQueryService scopedQueries;

    public DispatchRequestController(ExecutionOperationalQuery queryService, DispatchRequestService service, DispatchProperties properties, DispatchRuntimeConfigurationView runtimeConfiguration, DispatchExecutionService executionService, DispatchAttemptHistoryService attemptHistoryService) {
        this.queryService = queryService;
        this.service = service;
        this.properties = properties;
        this.runtimeConfiguration = runtimeConfiguration;
        this.executionService = executionService;
        this.attemptHistoryService = attemptHistoryService;
    }

    @GetMapping
    public List<DispatchRequest> recent(@RequestParam(defaultValue = "100") int limit) {
        if (scopedAccess == null) return queryService.recentDispatchRequests(limit);
        return requireScopedQueries().recent(scopedAccess.plan("task.read",ResourceType.TASK,VisibilityLevel.SUMMARY,"RS4_DISPATCH_REQUEST_LIST"),null,limit);
    }

    @GetMapping("/{dispatchRequestId}")
    public DispatchRequest get(@PathVariable String dispatchRequestId) {
        DispatchRequest value=requireDispatchRequest(dispatchRequestId);
        authorizeTask(value.getTaskId(),"task.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SUMMARY,"RS4_DISPATCH_REQUEST_DETAIL");
        return value;
    }

    @GetMapping("/status/{status}")
    public List<DispatchRequest> byStatus(@PathVariable DispatchRequestStatus status, @RequestParam(defaultValue = "100") int limit) {
        if (scopedAccess == null) return queryService.findDispatchRequestsByStatus(status, limit);
        return requireScopedQueries().recent(scopedAccess.plan("task.read",ResourceType.TASK,VisibilityLevel.SUMMARY,"RS4_DISPATCH_REQUEST_STATUS_LIST"),status,limit);
    }

    @GetMapping("/task/{taskId}")
    public List<DispatchRequest> byTask(@PathVariable String taskId, @RequestParam(defaultValue = "100") int limit) {
        authorizeTask(taskId,"task.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SUMMARY,"RS4_DISPATCH_REQUEST_TASK_LIST");
        return queryService.findDispatchRequestsByTask(taskId, limit);
    }

    @GetMapping("/task/{taskId}/history")
    public List<DispatchAttemptHistoryRecord> historyByTask(@PathVariable String taskId, @RequestParam(defaultValue = "100") int limit) {
        authorizeTask(taskId,"task.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SUMMARY,"RS4_DISPATCH_HISTORY_TASK");
        return attemptHistoryService.findByTaskId(taskId, Math.max(1, Math.min(limit, 500)));
    }

    @GetMapping("/history/recent")
    public List<DispatchAttemptHistoryRecord> recentHistory(@RequestParam(defaultValue = "100") int limit) {
        if(scopedAccess!=null) scopedAccess.requireTenantWide("task.read",ResourceType.TASK,VisibilityLevel.SUMMARY,"RS4_DISPATCH_HISTORY_TENANT");
        return attemptHistoryService.recent(Math.max(1, Math.min(limit, 500)));
    }

    @PostMapping("/{dispatchRequestId}/approve")
    public DispatchRequest approve(@PathVariable String dispatchRequestId, @RequestBody(required = false) Map<String, String> body) {
        authorizeDispatch(dispatchRequestId,"task.approve",ResourceAction.ActionKind.APPROVE,true,"RS4_DISPATCH_APPROVE");
        return service.approve(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{dispatchRequestId}/reject")
    public DispatchRequest reject(@PathVariable String dispatchRequestId, @RequestBody(required = false) Map<String, String> body) {
        authorizeDispatch(dispatchRequestId,"task.approve",ResourceAction.ActionKind.APPROVE,true,"RS4_DISPATCH_REJECT");
        return service.reject(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{dispatchRequestId}/execute")
    public DispatchExecutionResult execute(@PathVariable String dispatchRequestId) {
        authorizeDispatch(dispatchRequestId,"task.execute",ResourceAction.ActionKind.EXECUTE,true,"RS4_DISPATCH_EXECUTE");
        return executionService.execute(dispatchRequestId);
    }

    @PostMapping("/{dispatchRequestId}/retry")
    public DispatchRequest retry(@PathVariable String dispatchRequestId,
                                 @RequestBody(required = false) RetryRequest body) {
        boolean resetAttempts = body != null && Boolean.TRUE.equals(body.resetAttempts());
        boolean immediate = body == null || body.immediate() == null || Boolean.TRUE.equals(body.immediate());
        String reason = body == null ? null : body.reason();
        authorizeDispatch(dispatchRequestId,"task.execute",ResourceAction.ActionKind.EXECUTE,true,"RS4_DISPATCH_RETRY");
        return service.retry(dispatchRequestId, reason, resetAttempts, immediate);
    }

    @PostMapping("/{dispatchRequestId}/dead-letter")
    public DispatchRequest deadLetter(@PathVariable String dispatchRequestId,
                                      @RequestBody(required = false) Map<String, String> body) {
        authorizeDispatch(dispatchRequestId,"task.update",ResourceAction.ActionKind.UPDATE,true,"RS4_DISPATCH_DEAD_LETTER");
        return service.moveToDeadLetter(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{dispatchRequestId}/cancel")
    public DispatchRequest cancel(@PathVariable String dispatchRequestId,
                                  @RequestBody(required = false) Map<String, String> body) {
        authorizeDispatch(dispatchRequestId,"task.update",ResourceAction.ActionKind.UPDATE,true,"RS4_DISPATCH_CANCEL");
        return service.cancel(dispatchRequestId, body == null ? null : body.get("reason"));
    }

    private ScopedDispatchRequestQueryService requireScopedQueries(){
        if(scopedQueries==null) throw new IllegalStateException("RS4 scoped Dispatch Request query service is unavailable");
        return scopedQueries;
    }

    private DispatchRequest requireDispatchRequest(String dispatchRequestId){
        DispatchRequest value=queryService.findDispatchRequest(dispatchRequestId).orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if(scopedAccess!=null && !scopedAccess.activeTenantId().equals(value.getTenantId())) throw new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId);
        return value;
    }

    private void authorizeDispatch(String dispatchRequestId,String permission,ResourceAction.ActionKind kind,boolean sideEffecting,String purpose){
        DispatchRequest value=requireDispatchRequest(dispatchRequestId);
        authorizeTask(value.getTaskId(),permission,kind,sideEffecting,VisibilityLevel.SENSITIVE,purpose);
    }

    private void authorizeTask(String taskId,String permission,ResourceAction.ActionKind kind,boolean sideEffecting,VisibilityLevel visibility,String purpose){
        if(scopedAccess!=null) scopedAccess.authorize(ResourceType.TASK,taskId,permission,kind,sideEffecting,visibility,purpose);
    }

    public record RetryRequest(String reason, Boolean resetAttempts, Boolean immediate) {}

    @PostMapping("/execute-approved")
    public List<DispatchExecutionResult> executeApproved(@RequestParam(defaultValue = "50") int limit) {
        if(scopedAccess!=null) scopedAccess.requireTenantWide("task.execute",ResourceType.TASK,VisibilityLevel.SENSITIVE,"RS4_DISPATCH_EXECUTE_APPROVED_TENANT");
        return executionService.executeApproved(limit);
    }

    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("store", queryService.dispatchStoreMode());
        metadata.put("requestCreationEnabled", properties.isRequestCreationEnabled());
        metadata.put("reviewMode", runtimeConfiguration.reviewMode().name());
        metadata.put("executionPolicy", runtimeConfiguration.executionPolicy().name());
        metadata.put("sourceNodeId", runtimeConfiguration.sourceNodeId());
        metadata.put("gatewayDispatchPath", runtimeConfiguration.gatewayDispatchPath());
        metadata.put("requireAssignableAgent", runtimeConfiguration.requireAssignableAgent());
        metadata.put("retryEnabled", runtimeConfiguration.retryEnabled());
        metadata.put("retryMaxAttempts", runtimeConfiguration.maxAttempts());
        metadata.put("retryInitialBackoff", runtimeConfiguration.initialBackoff().toString());
        metadata.put("retryMaxBackoff", runtimeConfiguration.maxBackoff().toString());
        metadata.put("retryJitterPercent", runtimeConfiguration.jitterPercent());
        metadata.put("runtimeBackoffJitterPercent", runtimeConfiguration.runtimeJitterPercent());
        metadata.put("poisonAgentFailureThreshold", runtimeConfiguration.poisonAgentFailureThreshold());
        metadata.put("runtimeConfigBacked", runtimeConfiguration.runtimeBacked());
        metadata.put("runtimeConfigRevision", runtimeConfiguration.revisionId());
        metadata.put("dispatchClientEnabled", properties.getClient().isEnabled());
        metadata.put("autoExecuteApproved", properties.getClient().isAutoExecuteApproved());
        metadata.put("autoExecutionActive", properties.getClient().isEnabled() && runtimeConfiguration.executionPolicy().autoExecutes());
        metadata.put("dispatchClientBlocked", !properties.getClient().isEnabled() || !runtimeConfiguration.executionPolicy().autoExecutes());
        metadata.put("defaultGatewayBaseUrl", runtimeConfiguration.defaultGatewayBaseUrl());
        metadata.put("gatewayBaseUrls", runtimeConfiguration.gatewayBaseUrls());
        metadata.put("internalTokenHeader", properties.getClient().getInternalTokenHeader());
        metadata.put("maxBatchSize", runtimeConfiguration.maxBatchSize());
        metadata.put("autoExecuteIntervalMs", runtimeConfiguration.autoExecuteInterval().toMillis());
        metadata.put("connectTimeout", runtimeConfiguration.connectTimeout().toString());
        metadata.put("requestTimeout", runtimeConfiguration.requestTimeout().toString());
        metadata.put("claimLease", runtimeConfiguration.claimLease().toString());
        metadata.put("workerId", runtimeConfiguration.workerId());
        metadata.put("effectiveSource", runtimeConfiguration.runtimeBacked() ? "RUNTIME_SNAPSHOT" : "STARTUP_FALLBACK");
        return metadata;
    }
}
