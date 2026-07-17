package com.opensocket.aievent.core.callback;

import java.util.List;
import java.util.Optional;

public interface TaskCallbackRepository {
    TaskCallbackRecord save(TaskCallbackRecord record);
    /**
     * Reserves a callback id before mutating task/dispatch state.
     * Returns false when the same callback id was already processed or reserved.
     */
    default boolean tryReserve(TaskCallbackRecord record) {
        save(record);
        return true;
    }
    Optional<TaskCallbackRecord> findByCallbackId(String callbackId);
    List<TaskCallbackRecord> findByTaskId(String taskId, int limit);
    /**
     * Finds callback inbox rows for one dispatch request using the dispatch_request_id access path.
     *
     * <p>Dispatch-scoped views must not call {@link #recent(int)} and filter in memory, because
     * recent-window scans can miss older callbacks and become unstable as callback volume grows.</p>
     */
    List<TaskCallbackRecord> findByDispatchRequestId(String dispatchRequestId, int limit);
    List<TaskCallbackRecord> recent(int limit);
    String mode();
}
