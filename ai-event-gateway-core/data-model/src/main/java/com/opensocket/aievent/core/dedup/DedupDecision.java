package com.opensocket.aievent.core.dedup;

public record DedupDecision(boolean duplicate, DedupState state, String reason) {
}
