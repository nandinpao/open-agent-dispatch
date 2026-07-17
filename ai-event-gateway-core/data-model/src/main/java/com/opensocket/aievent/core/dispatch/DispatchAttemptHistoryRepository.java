package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;
import java.util.List;

public interface DispatchAttemptHistoryRepository {
    DispatchAttemptHistoryRecord append(DispatchAttemptHistoryRecord record);
    List<DispatchAttemptHistoryRecord> findByTaskId(String taskId, int limit);
    List<DispatchAttemptHistoryRecord> findByDispatchRequestId(String dispatchRequestId, int limit);
    List<DispatchAttemptHistoryRecord> recent(int limit);
    List<DispatchAttemptHistoryRecord> findSince(OffsetDateTime since, int limit);
    String mode();
}
