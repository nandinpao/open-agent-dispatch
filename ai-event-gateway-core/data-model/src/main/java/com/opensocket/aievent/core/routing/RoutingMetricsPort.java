package com.opensocket.aievent.core.routing;

/** Optional observability boundary. */
@FunctionalInterface
public interface RoutingMetricsPort {
    void recordRoutingDecision(RoutingDecisionRecord decision);
}
