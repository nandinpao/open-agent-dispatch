package com.opensocket.aievent.core.action;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import com.opensocket.aievent.core.action.executor.audit.AdapterExecutorAuditRecord;

import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.task.TaskRecord;

/** Public application boundary owned by the Adapter Action module. */
public interface AdapterActionFacade {
    AdapterActionOrchestrationResult evaluateAfterTaskCallback(TaskRecord task,
                                                                DispatchRequest dispatchRequest,
                                                                TaskCallbackRequest callback,
                                                                TaskCallbackType callbackType);

    Optional<AdapterAction> findById(String actionId);
    AdapterAction markCompleted(String actionId, String responseRef);
    AdapterAction markFailed(String actionId, String error);
    Optional<AdapterAction> claimNext(AdapterType adapterType, String workerId, Duration leaseDuration);
    AdapterAction heartbeat(String actionId, String workerId, Duration leaseDuration);
    AdapterAction completeByWorker(String actionId, String workerId, String responseRef);
    AdapterAction failByWorker(String actionId, String workerId, String error, Boolean retryable);
    List<AdapterAction> recoverExpiredWorkerLeases(int limit);
    AdapterAction recoverExpiredWorkerLease(String actionId);
    AdapterAction retryForWorker(String actionId, String reason, boolean resetAttempts);
    AdapterAction cancel(String actionId, String reason);
    List<AdapterAction> recent(int limit);
    List<AdapterAction> byIncident(String incidentId, int limit);
    List<AdapterAction> byTask(String taskId, int limit);
    List<AdapterAction> byStatus(AdapterActionStatus status, int limit);
    Map<String, Integer> statusCounts(int limit);
    List<AdapterExecutorAuditRecord> auditByAction(String actionId, int limit);
    List<AdapterExecutorAuditRecord> recentExecutorAudit(int limit);
    String storeMode();
    String executorAuditStoreMode();
}
