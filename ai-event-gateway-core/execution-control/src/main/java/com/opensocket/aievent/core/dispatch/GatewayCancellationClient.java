package com.opensocket.aievent.core.dispatch;

import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.assignment.TaskAssignment;

public interface GatewayCancellationClient {
    GatewayCancellationResult cancel(A2ACancellationRecord cancellation,
            DispatchRequest dispatch, TaskAssignment assignment);
}
