package com.opensocket.aievent.core.a2a.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Framework-neutral coordination invariants. Application adapters invoke this before authority ports. */
public final class A2ACoordinationGuard {
    public void requireDirectionalDomains(String sourceDomainId, String targetDomainId) {
        if (blank(sourceDomainId) || blank(targetDomainId)) throw new IllegalArgumentException("sourceDomainId and targetDomainId are required");
        if (sourceDomainId.trim().equals(targetDomainId.trim())) throw new IllegalArgumentException("A2A source and target domains must differ");
    }
    public int requireHopWithinLimit(int currentHopCount, int maxHopCount) {
        if (maxHopCount < 1 || maxHopCount > 32) throw new IllegalArgumentException("maxHopCount must be between 1 and 32");
        int next = Math.max(0, currentHopCount) + 1;
        if (next > maxHopCount) throw new IllegalStateException("A2A_HOP_LIMIT_EXCEEDED");
        return next;
    }
    public void requireNoDomainCycle(List<String> domainPath, String targetDomainId) {
        if (domainPath == null || domainPath.isEmpty() || blank(targetDomainId)) return;
        Set<String> normalized = new LinkedHashSet<>();
        for (String domain : domainPath) if (!blank(domain)) normalized.add(domain.trim());
        if (normalized.contains(targetDomainId.trim())) throw new IllegalStateException("A2A_CYCLE_DETECTED");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
