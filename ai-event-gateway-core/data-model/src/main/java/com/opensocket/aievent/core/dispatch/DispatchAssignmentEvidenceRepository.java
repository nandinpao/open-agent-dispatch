package com.opensocket.aievent.core.dispatch;

import java.util.List;

public interface DispatchAssignmentEvidenceRepository {
    DispatchAssignmentEvidence append(DispatchAssignmentEvidence evidence);
    List<DispatchAssignmentEvidence> findByDispatchRequest(String dispatchRequestId, int limit);
    boolean existsEvent(String dispatchRequestId, int attemptNo, String eventType);
    String mode();
}
