package com.opensocket.aievent.core.resourceaccess.contract;

/**
 * RS0 base authorization invariant.
 *
 * <p>These are necessary but not sufficient conditions. The existing Resource Access engine must
 * still apply explicit deny, security state, visibility policy, policy/security epoch freshness,
 * runtime lease/fencing, and other domain-specific fail-closed checks.</p>
 */
public final class CanonicalAuthorizationContract {
    private CanonicalAuthorizationContract() {}

    public static boolean baseEligible(
            boolean authenticated,
            boolean tenantMatched,
            boolean permissionGranted,
            boolean scopeMatched,
            boolean contextualConstraintsPassed) {
        return authenticated
                && tenantMatched
                && permissionGranted
                && scopeMatched
                && contextualConstraintsPassed;
    }
}
