package com.opensocket.aievent.core.uicapability.contract;

import java.util.List;

/** Ordered response matching the submitted contexts. */
public record UiCapabilityBatchResponse(
        String contractVersion,
        List<UiCapabilityEnvelope> contexts) {
    public UiCapabilityBatchResponse {
        contractVersion = require(contractVersion, "contractVersion");
        if (!UiCapabilityContract.VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unsupported response contract version: " + contractVersion);
        }
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
