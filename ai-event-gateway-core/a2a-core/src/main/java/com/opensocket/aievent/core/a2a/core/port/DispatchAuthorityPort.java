package com.opensocket.aievent.core.a2a.core.port;

/** Port through which A2A requests dispatch. Agent selection and Assignment creation remain Dispatch Authority responsibilities. */
public interface DispatchAuthorityPort {
    DispatchReceipt requestDispatch(DispatchRequest request);
    record DispatchRequest(String tenantId, String a2aRequestId, String childTaskId, String targetAgentPoolId, String idempotencyKey, String correlationId) {}
    record DispatchReceipt(String assignmentId, long assignmentAttempt, String dispatchTokenReference, boolean replayed) {}
}
