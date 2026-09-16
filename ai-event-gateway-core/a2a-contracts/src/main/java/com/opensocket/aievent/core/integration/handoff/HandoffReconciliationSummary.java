package com.opensocket.aievent.core.integration.handoff;

public record HandoffReconciliationSummary(
        int examined,
        int released,
        int deferred,
        int waitHuman,
        int expired) {
}
