package com.opensocket.aievent.core.dispatch.flow;

import java.util.Optional;

/** Repository port for runtime Flow-owned Dispatch Rule matching. */
public interface FlowRuleRoutingRepository {
    Optional<FlowRuleRuntimeMatch> findBestMatch(FlowRuleRuntimeQuery query);
}
