package com.opensocket.aievent.core.routing;

import java.util.List;
import java.util.Optional;

public interface RoutingDecisionRepository {
    RoutingDecisionRecord save(RoutingDecisionRecord decision);
    Optional<RoutingDecisionRecord> findById(String decisionId);
    List<RoutingDecisionRecord> findByTaskId(String taskId, int limit);
    List<RoutingDecisionRecord> recent(int limit);
    String mode();
}
