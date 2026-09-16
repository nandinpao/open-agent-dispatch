package com.opensocket.aievent.core.a2a;

public record A2AIdempotencyClaim(A2AIdempotencyRecord record, boolean acquired) {}
