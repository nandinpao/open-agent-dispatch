package com.opensocket.aievent.core.a2a;

import java.util.List;
import java.util.Optional;

public interface A2ACutoverRepository {
    Optional<A2ACutoverState> find(String scopeId);
    A2ACutoverState save(A2ACutoverState state);
    A2ACutoverState saveExpectedVersion(A2ACutoverState state, long expectedVersion);
    A2ACutoverEvidence appendEvidence(A2ACutoverEvidence evidence);
    List<A2ACutoverEvidence> evidence(String scopeId, int limit);
    String mode();
}
