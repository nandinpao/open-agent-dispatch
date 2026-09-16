package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Normalized submission result returned by Managed Agent, A2A, MCP or Internal Service adapters. */
public record ExecutionAdapterResult(boolean accepted, String executionState, String externalExecutionRef, List<String> reasonCodes, OffsetDateTime acceptedAt) {
    public ExecutionAdapterResult { reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes); }
}
