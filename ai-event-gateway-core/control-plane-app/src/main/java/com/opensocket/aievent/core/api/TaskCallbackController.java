package com.opensocket.aievent.core.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import com.opensocket.aievent.core.resourceaccess.runtime.AgentTaskRuntimeAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.callback.DispatchRecoveryResult;
import com.opensocket.aievent.core.callback.DispatchRecoveryService;
import com.opensocket.aievent.core.callback.TaskCallbackProperties;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.callback.TaskCallbackService;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;

@RestController
@RequestMapping("/internal/control-plane/tasks")
public class TaskCallbackController {
    private static final Logger log = LoggerFactory.getLogger(TaskCallbackController.class);

    private final TaskCallbackService callbackService;
    private final ExecutionOperationalQuery queryService;
    private final DispatchRecoveryService recoveryService;
    private final TaskCallbackProperties properties;

    @Autowired(required=false)
    private AgentTaskRuntimeAuthorizationService agentTaskRuntimeAuthorization;

    @Autowired(required=false)
    private TaskOperationalQuery taskOperationalQuery;

    public TaskCallbackController(TaskCallbackService callbackService,
                                  ExecutionOperationalQuery queryService,
                                  DispatchRecoveryService recoveryService,
                                  TaskCallbackProperties properties) {
        this.callbackService = callbackService;
        this.queryService = queryService;
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    @PostMapping("/{taskId}/ack")
    public TaskCallbackResult ack(@PathVariable String taskId, @RequestBody(required = false) TaskCallbackRequest request) {
        log.info("callback_inbox_http_received taskId={} callbackType=ACK dispatchRequestId={} assignmentId={} agentId={} callbackId={}",
                taskId, request == null ? null : request.getDispatchRequestId(), request == null ? null : request.getAssignmentId(),
                request == null ? null : request.getAgentId(), request == null ? null : request.getCallbackId());
        authorizeAgentTask(taskId,request,"RS4_AGENT_TASK_ACK");
        return inTaskTenant(taskId, request, "ACK", () -> callbackService.ack(taskId, request));
    }

    @PostMapping("/{taskId}/progress")
    public TaskCallbackResult progress(@PathVariable String taskId, @RequestBody(required = false) TaskCallbackRequest request) {
        log.info("callback_inbox_http_received taskId={} callbackType=PROGRESS dispatchRequestId={} assignmentId={} agentId={} callbackId={}",
                taskId, request == null ? null : request.getDispatchRequestId(), request == null ? null : request.getAssignmentId(),
                request == null ? null : request.getAgentId(), request == null ? null : request.getCallbackId());
        authorizeAgentTask(taskId,request,"RS4_AGENT_TASK_PROGRESS");
        return inTaskTenant(taskId, request, "PROGRESS", () -> callbackService.progress(taskId, request));
    }

    @PostMapping("/{taskId}/result")
    public TaskCallbackResult result(@PathVariable String taskId, @RequestBody(required = false) TaskCallbackRequest request) {
        log.info("callback_inbox_http_received taskId={} callbackType=RESULT dispatchRequestId={} assignmentId={} agentId={} callbackId={} resultStatus={}",
                taskId, request == null ? null : request.getDispatchRequestId(), request == null ? null : request.getAssignmentId(),
                request == null ? null : request.getAgentId(), request == null ? null : request.getCallbackId(),
                request == null ? null : request.getResultStatus());
        authorizeAgentTask(taskId,request,"RS4_AGENT_TASK_RESULT");
        return inTaskTenant(taskId, request, "RESULT", () -> callbackService.result(taskId, request));
    }

    @PostMapping("/{taskId}/error")
    public TaskCallbackResult error(@PathVariable String taskId, @RequestBody(required = false) TaskCallbackRequest request) {
        log.info("callback_inbox_http_received taskId={} callbackType=ERROR dispatchRequestId={} assignmentId={} agentId={} callbackId={} errorCode={}",
                taskId, request == null ? null : request.getDispatchRequestId(), request == null ? null : request.getAssignmentId(),
                request == null ? null : request.getAgentId(), request == null ? null : request.getCallbackId(),
                request == null ? null : request.getErrorCode());
        authorizeAgentTask(taskId,request,"RS4_AGENT_TASK_ERROR");
        return inTaskTenant(taskId, request, "ERROR", () -> callbackService.error(taskId, request));
    }

    private void authorizeAgentTask(String taskId,TaskCallbackRequest request,String purpose){
        if(agentTaskRuntimeAuthorization==null)return;
        agentTaskRuntimeAuthorization.authorizeExecution(taskId,request==null?null:request.getAgentId(),
                request==null?null:request.getCallbackId(),purpose);
    }

    private TaskCallbackResult inTaskTenant(String taskId, TaskCallbackRequest request, String callbackType, Supplier<TaskCallbackResult> action) {
        if (taskOperationalQuery == null) {
            return action.get();
        }
        TaskRecord task = taskOperationalQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        String tenantId = task.getTenantId();
        String actorId = request != null && request.getAgentId() != null && !request.getAgentId().isBlank()
                ? request.getAgentId().trim()
                : "core-internal-gateway";
        log.info("callback_tenant_persistence_context taskId={} callbackType={} tenantId={} actorId={} source=CANONICAL_TASK",
                taskId, callbackType, tenantId, actorId);
        try (IamTenantContextHolder.Scope ignored = IamTenantContextHolder.open(new IamTenantExecutionContext(tenantId, actorId))) {
            return action.get();
        }
    }

    @GetMapping("/{taskId}/callbacks")
    public List<TaskCallbackRecord> callbacksByTask(@PathVariable String taskId, @RequestParam(defaultValue = "100") int limit) {
        return queryService.findCallbacksByTask(taskId, limit);
    }

    @GetMapping("/callbacks/recent")
    public List<TaskCallbackRecord> recentCallbacks(@RequestParam(defaultValue = "100") int limit) {
        return queryService.recentCallbacks(limit);
    }

    @PostMapping("/dispatch-recovery/scan-timeouts")
    public List<DispatchRecoveryResult> scanTimeouts(@RequestParam(defaultValue = "100") int limit) {
        return recoveryService.scanAndRecoverTimedOut(limit);
    }

    @PostMapping("/dispatch-recovery/{dispatchRequestId}/timeout")
    public DispatchRecoveryResult markTimeout(@PathVariable String dispatchRequestId) {
        return recoveryService.markTimedOut(dispatchRequestId);
    }

    @GetMapping("/callbacks/metadata")
    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("store", queryService.callbackStoreMode());
        metadata.put("idempotencyEnabled", properties.isIdempotencyEnabled());
        metadata.put("replayProtectionEnabled", properties.isReplayProtectionEnabled());
        metadata.put("rejectCallbackIdReplayMismatch", properties.isRejectCallbackIdReplayMismatch());
        metadata.put("requireDispatchToken", properties.isRequireDispatchToken());
        metadata.put("allowMissingDispatchRequestId", properties.isAllowMissingDispatchRequestId());
        metadata.put("enforceStateTransition", properties.isEnforceStateTransition());
        metadata.put("rejectOldAttemptCallbacks", properties.isRejectOldAttemptCallbacks());
        metadata.put("requireAttemptNo", properties.isRequireAttemptNo());
        metadata.put("enforceGatewayAndAgentIdentity", properties.isEnforceGatewayAndAgentIdentity());
        metadata.put("allowTerminalCallbackOverride", properties.isAllowTerminalCallbackOverride());
        metadata.put("timeoutEnabled", properties.getRecovery().isTimeoutEnabled());
        metadata.put("dispatchTimeout", properties.getRecovery().getDispatchTimeout().toString());
        metadata.put("retryEnabled", properties.getRecovery().isRetryEnabled());
        metadata.put("maxAttempts", properties.getRecovery().getMaxAttempts());
        metadata.put("initialBackoff", properties.getRecovery().getInitialBackoff().toString());
        metadata.put("maxBackoff", properties.getRecovery().getMaxBackoff().toString());
        metadata.put("jitterPercent", properties.getRecovery().getJitterPercent());
        metadata.put("autoFailTimedOut", properties.getRecovery().isAutoFailTimedOut());
        metadata.put("maxBatchSize", properties.getRecovery().getMaxBatchSize());
        return metadata;
    }
}
