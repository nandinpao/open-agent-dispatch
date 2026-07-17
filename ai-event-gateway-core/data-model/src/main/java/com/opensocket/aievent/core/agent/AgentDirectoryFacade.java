package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Public application boundary for agent lookup and capacity management. */
public interface AgentDirectoryFacade {
    Optional<AgentSnapshot> findById(String agentId);
    List<AgentSnapshot> findCandidates(AgentQuery query);
    CapacityReservationResult reserveCapacity(String agentId);
    boolean releaseCapacity(String agentId);

    /**
     * Temporarily removes an agent from dispatch routing after a runtime/transport failure.
     * The Agent remains connected and observable, but {@link AgentSnapshot#isAssignable()} returns
     * false until the backoff window expires.
     */
    default Optional<AgentSnapshot> applyRuntimeBackoff(String agentId, OffsetDateTime backoffUntil, String reason) {
        return Optional.empty();
    }

    default Optional<AgentSnapshot> clearRuntimeBackoff(String agentId, String reason) {
        return Optional.empty();
    }

    String mode();
}
