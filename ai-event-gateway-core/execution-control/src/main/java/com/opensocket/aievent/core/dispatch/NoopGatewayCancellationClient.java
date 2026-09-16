package com.opensocket.aievent.core.dispatch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.assignment.TaskAssignment;

@Component
@ConditionalOnProperty(prefix = "dispatch.client", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopGatewayCancellationClient implements GatewayCancellationClient {
    @Override
    public GatewayCancellationResult cancel(A2ACancellationRecord cancellation,
            DispatchRequest dispatch, TaskAssignment assignment) {
        return GatewayCancellationResult.failed(0, "CANCEL_CLIENT_DISABLED",
                "DISPATCH_CLIENT_DISABLED",
                "dispatch.client.enabled=false; runtime cancellation was not delivered");
    }
}
