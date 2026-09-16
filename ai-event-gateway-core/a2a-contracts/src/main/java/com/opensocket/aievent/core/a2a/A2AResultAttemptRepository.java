package com.opensocket.aievent.core.a2a;

import java.util.List;

public interface A2AResultAttemptRepository {
    A2AResultAttempt save(A2AResultAttempt attempt);
    List<A2AResultAttempt> findByRequest(String tenantId, String requestId, int limit);
    String mode();
}
