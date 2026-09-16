package com.opensocket.aievent.core.uicapability.contract;

import java.util.List;

/** Batch wire request. The endpoint profile applies the generic or list-row limit. */
public record UiCapabilityBatchRequest(
        String contractVersion,
        List<UiCapabilityContextRequest> contexts) {
    public UiCapabilityBatchRequest {
        contractVersion = require(contractVersion, "contractVersion");
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
        if (contexts.isEmpty()) throw new IllegalArgumentException("contexts is required");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
