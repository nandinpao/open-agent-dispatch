package com.opensocket.aievent.core.routing;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "routing", name = "decision-store", havingValue = "MEMORY")
public class InMemoryRoutingDecisionRepository implements RoutingDecisionRepository {
    private final ConcurrentHashMap<String, RoutingDecisionRecord> decisions = new ConcurrentHashMap<>();

    @Override
    public RoutingDecisionRecord save(RoutingDecisionRecord decision) {
        decisions.put(decision.getDecisionId(), decision);
        return decision;
    }

    @Override
    public Optional<RoutingDecisionRecord> findById(String decisionId) {
        return Optional.ofNullable(decisions.get(decisionId));
    }

    @Override
    public List<RoutingDecisionRecord> findByTaskId(String taskId, int limit) {
        return decisions.values().stream()
                .filter(decision -> taskId.equals(decision.getTaskId()))
                .sorted(Comparator.comparing(RoutingDecisionRecord::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<RoutingDecisionRecord> recent(int limit) {
        return decisions.values().stream()
                .sorted(Comparator.comparing(RoutingDecisionRecord::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public String mode() { return "MEMORY"; }
}
