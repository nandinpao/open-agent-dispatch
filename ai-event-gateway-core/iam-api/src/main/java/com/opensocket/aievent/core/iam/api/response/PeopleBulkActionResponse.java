package com.opensocket.aievent.core.iam.api.response;

import java.util.List;

/** Synchronous batch envelope. Individual Person changes keep their own transaction boundary. */
public record PeopleBulkActionResponse(
        String bulkOperationId,
        String operation,
        String executionPolicy,
        int selectedCount,
        int succeededCount,
        int skippedCount,
        int failedCount,
        int changedCount,
        List<PeopleBulkItemResultResponse> results) {
    public PeopleBulkActionResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
