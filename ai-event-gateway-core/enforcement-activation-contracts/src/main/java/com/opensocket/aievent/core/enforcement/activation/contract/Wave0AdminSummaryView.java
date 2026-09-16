package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;

public record Wave0AdminSummaryView(
        long publishedAuthorityRevisions,
        long cutoverPlans,
        long activePermissions,
        long runtimeNodes,
        long failedRuntimeNodes,
        Instant generatedAt) implements Wave0CanonicalPayload {

    public Wave0AdminSummaryView {
        if (publishedAuthorityRevisions < 0 || cutoverPlans < 0 || activePermissions < 0 || runtimeNodes < 0 || failedRuntimeNodes < 0 || generatedAt == null) throw new IllegalArgumentException("admin summary values are invalid");
    }

    @Override public String canonicalValue() {
        return publishedAuthorityRevisions + "|" + cutoverPlans + "|" + activePermissions + "|" + runtimeNodes + "|" + failedRuntimeNodes;
    }
}
