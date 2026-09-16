package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record Wave0RuntimeStatusView(
        long targetRevision,
        String targetChecksum,
        long generation,
        List<Wave0RuntimeNodeView> nodes,
        int healthyNodes,
        int failedNodes,
        int staleNodes,
        boolean converged,
        Instant evaluatedAt) implements Wave0CanonicalPayload {

    public Wave0RuntimeStatusView {
        targetChecksum = targetChecksum == null ? "" : targetChecksum.trim();
        nodes = nodes == null ? List.of() : nodes.stream().sorted(Comparator.comparing(Wave0RuntimeNodeView::nodeId)).toList();
        if (targetRevision < 0 || generation < 0 || healthyNodes < 0 || failedNodes < 0 || staleNodes < 0 || evaluatedAt == null) throw new IllegalArgumentException("runtime status values are invalid");
    }

    @Override public String canonicalValue() {
        return targetRevision + "|" + targetChecksum + "|" + generation + "|" + healthyNodes + "|" + failedNodes + "|" + staleNodes + "|" + converged + "|" + nodes.stream().map(Wave0RuntimeNodeView::canonicalValue).reduce("", (left, right) -> left + "[" + right + "]");
    }
}
