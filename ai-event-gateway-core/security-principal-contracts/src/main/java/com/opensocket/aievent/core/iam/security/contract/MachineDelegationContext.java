package com.opensocket.aievent.core.iam.security.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Framework-free machine delegation chain. The origin is never overwritten; every delegation hop
 * is append-only so audit can distinguish original, delegating and executing principals.
 */
public record MachineDelegationContext(MachinePrincipal originPrincipal, List<MachineDelegationHop> hops) {
    public static final int MAX_HOPS = 16;

    public MachineDelegationContext {
        Objects.requireNonNull(originPrincipal, "originPrincipal");
        hops = hops == null ? List.of() : List.copyOf(hops);
        if (hops.size() > MAX_HOPS) throw new IllegalArgumentException("machine delegation hop limit exceeded");
        MachinePrincipal expected = originPrincipal;
        for (MachineDelegationHop hop : hops) {
            Objects.requireNonNull(hop, "delegation hop");
            if (!hop.fromPrincipal().equals(expected)) {
                throw new IllegalArgumentException("machine delegation chain is not contiguous");
            }
            if (!originPrincipal.activeTenant().equals(hop.toPrincipal().activeTenant())) {
                throw new IllegalArgumentException("machine delegation cannot cross Tenant boundaries");
            }
            expected = hop.toPrincipal();
        }
    }

    public static MachineDelegationContext origin(MachinePrincipal principal) {
        return new MachineDelegationContext(principal, List.of());
    }

    public MachinePrincipal executingPrincipal() {
        return hops.isEmpty() ? originPrincipal : hops.getLast().toPrincipal();
    }

    public MachinePrincipal delegatingPrincipal() {
        return hops.isEmpty() ? originPrincipal : hops.getLast().fromPrincipal();
    }

    public MachineDelegationContext delegateTo(MachinePrincipal target, String purpose,
                                                String resourceType, String resourceId, Instant at) {
        if (hops.size() >= MAX_HOPS) throw new IllegalArgumentException("machine delegation hop limit exceeded");
        ArrayList<MachineDelegationHop> next = new ArrayList<>(hops);
        next.add(new MachineDelegationHop(executingPrincipal(), target, purpose, resourceType, resourceId, at));
        return new MachineDelegationContext(originPrincipal, next);
    }
}
