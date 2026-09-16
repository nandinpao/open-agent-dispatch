package com.opensocket.aievent.core.a2a.authority;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.application.port.out.A2ADispatchAuthorityOperations;
import com.opensocket.aievent.core.a2a.A2ARequestRepository;
import com.opensocket.aievent.core.a2a.core.port.DispatchAuthorityPort.DispatchRequest;
import com.opensocket.aievent.core.a2a.application.port.out.HandoffDispatchReleasePort;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.integration.handoff.HandoffDispatchReleaseResult;

/**
 * Resolves an approved Handoff-gated Child Task back to its A2A Request and persists the dispatch
 * intent through the normal A2A Dispatch Authority. Non-A2A Tasks are intentionally ignored.
 */
@Component
public class DefaultA2AHandoffDispatchReleaseAdapter implements HandoffDispatchReleasePort {
    private final A2ARequestRepository requests;
    private final A2ADispatchAuthorityOperations dispatchAuthority;

    public DefaultA2AHandoffDispatchReleaseAdapter(
            A2ARequestRepository requests,
            A2ADispatchAuthorityOperations dispatchAuthority) {
        this.requests = requests;
        this.dispatchAuthority = dispatchAuthority;
    }

    @Override
    public HandoffDispatchReleaseResult requestDispatch(TaskRecord releasedTask, String correlationId) {
        if (releasedTask == null || releasedTask.getTenantId() == null
                || releasedTask.getTaskId() == null) {
            return HandoffDispatchReleaseResult.notApplicable("handoff-release:invalid-task");
        }
        String rootTaskId = firstNonBlank(releasedTask.getRootTaskId(), releasedTask.getTaskId());
        A2ARequest request = requests.findByRootTask(releasedTask.getTenantId(), rootTaskId, 1000)
                .stream()
                .filter(candidate -> releasedTask.getTaskId().equals(candidate.getChildTaskId()))
                .findFirst()
                .orElse(null);
        if (request == null) {
            return HandoffDispatchReleaseResult.notApplicable("handoff-release:non-a2a:" + releasedTask.getTaskId());
        }
        String effectiveCorrelationId = firstNonBlank(correlationId, request.getCorrelationId());
        var receipt = dispatchAuthority.requestDispatch(new DispatchRequest(
                request.getTenantId(),
                request.getRequestId(),
                request.getChildTaskId(),
                request.getTargetAgentPoolId(),
                "a2a-dispatch:" + request.getRequestId(),
                effectiveCorrelationId));
        return new HandoffDispatchReleaseResult(true, receipt.dispatchTokenReference(), receipt.replayed());
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
