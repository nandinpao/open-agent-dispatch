package com.opensocket.aievent.core.enforcement.activation.contract;

import java.util.Objects;

public record CutoverPlanRoute(int routeOrder, AuthorityRouteDefinition definition) {
    public CutoverPlanRoute {
        if (routeOrder < 0) throw new IllegalArgumentException("routeOrder must not be negative");
        Objects.requireNonNull(definition, "definition");
    }
}
