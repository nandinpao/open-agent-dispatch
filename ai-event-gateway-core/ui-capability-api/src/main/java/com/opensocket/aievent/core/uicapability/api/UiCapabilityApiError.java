package com.opensocket.aievent.core.uicapability.api;

import java.time.Instant;

public record UiCapabilityApiError(String code, String message, boolean retryable,
                                   String correlationId, Instant timestamp) { }
