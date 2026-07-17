package com.opensocket.aievent.core.dispatch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.core.callback.TaskCallbackRecord;

/** Read-only query and operational boundary owned by Execution Control. */
public interface ExecutionOperationalQuery {
    Optional<DispatchRequest> findDispatchRequest(String dispatchRequestId);
    List<DispatchRequest> recentDispatchRequests(int limit);
    List<DispatchRequest> findDispatchRequestsByStatus(DispatchRequestStatus status, int limit);
    List<DispatchRequest> findDispatchRequestsByTask(String taskId, int limit);
    List<TaskCallbackRecord> recentCallbacks(int limit);
    List<TaskCallbackRecord> findCallbacksByTask(String taskId, int limit);
    Map<String, Integer> dispatchStatusCounts(int limit);
    String dispatchStoreMode();
    String callbackStoreMode();
}
