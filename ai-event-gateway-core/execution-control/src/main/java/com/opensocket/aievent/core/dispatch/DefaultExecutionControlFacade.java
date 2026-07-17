package com.opensocket.aievent.core.dispatch;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.callback.DispatchRecoveryResult;
import com.opensocket.aievent.core.callback.DispatchRecoveryService;
import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.callback.TaskCallbackResult;
import com.opensocket.aievent.core.callback.TaskCallbackService;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;

@Service
public class DefaultExecutionControlFacade implements ExecutionControlFacade, ExecutionOperationalQuery {
    private final DispatchExecutionService dispatchExecutionService;
    private final TaskCallbackService taskCallbackService;
    private final DispatchRecoveryService dispatchRecoveryService;
    private final DispatchRequestRepository dispatchRequestRepository;
    private final TaskCallbackRepository taskCallbackRepository;

    public DefaultExecutionControlFacade(DispatchExecutionService dispatchExecutionService,
                                         TaskCallbackService taskCallbackService,
                                         DispatchRecoveryService dispatchRecoveryService,
                                         DispatchRequestRepository dispatchRequestRepository,
                                         TaskCallbackRepository taskCallbackRepository) {
        this.dispatchExecutionService = dispatchExecutionService;
        this.taskCallbackService = taskCallbackService;
        this.dispatchRecoveryService = dispatchRecoveryService;
        this.dispatchRequestRepository = dispatchRequestRepository;
        this.taskCallbackRepository = taskCallbackRepository;
    }

    @Override
    public DispatchExecutionResult executeDispatch(String dispatchRequestId) {
        return dispatchExecutionService.execute(dispatchRequestId);
    }

    @Override
    public List<DispatchExecutionResult> executeDueDispatches(int limit) {
        return dispatchExecutionService.executeApproved(limit);
    }

    @Override
    public TaskCallbackResult handleCallback(TaskCallbackType type, String taskId, TaskCallbackRequest request) {
        return taskCallbackService.handle(type, taskId, request);
    }

    @Override
    public List<DispatchRecoveryResult> recoverTimedOutDispatches(int limit) {
        return dispatchRecoveryService.scanAndRecoverTimedOut(limit);
    }
    @Override
    public Optional<DispatchRequest> findDispatchRequest(String dispatchRequestId) {
        return dispatchRequestRepository.findById(dispatchRequestId);
    }

    @Override
    public List<DispatchRequest> recentDispatchRequests(int limit) {
        return dispatchRequestRepository.recent(Math.max(1, limit));
    }

    @Override
    public List<DispatchRequest> findDispatchRequestsByStatus(DispatchRequestStatus status, int limit) {
        return dispatchRequestRepository.findByStatus(status, Math.max(1, limit));
    }

    @Override
    public List<DispatchRequest> findDispatchRequestsByTask(String taskId, int limit) {
        return dispatchRequestRepository.findByTaskId(taskId, Math.max(1, limit));
    }

    @Override
    public List<TaskCallbackRecord> recentCallbacks(int limit) {
        return taskCallbackRepository.recent(Math.max(1, limit));
    }

    @Override
    public List<TaskCallbackRecord> findCallbacksByTask(String taskId, int limit) {
        return taskCallbackRepository.findByTaskId(taskId, Math.max(1, limit));
    }

    @Override
    public Map<String, Integer> dispatchStatusCounts(int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DispatchRequestStatus status : DispatchRequestStatus.values()) {
            counts.put(status.name(), dispatchRequestRepository.findByStatus(status, Math.max(1, limit)).size());
        }
        return counts;
    }

    @Override public String dispatchStoreMode() { return dispatchRequestRepository.mode(); }
    @Override public String callbackStoreMode() { return taskCallbackRepository.mode(); }

}
