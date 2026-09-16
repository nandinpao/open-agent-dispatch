package com.opensocket.aievent.core.enforcement.activation.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityDecision;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityPlane;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouter;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRoutingContext;

public final class RevisionedAuthorityRouter implements AuthorityRouter {
    private final AtomicReference<AuthoritySnapshot> current = new AtomicReference<>(AuthoritySnapshot.bootstrap());
    private final Deque<AuthoritySnapshot> retainedSnapshots = new ArrayDeque<>();
    private final AuthoritySnapshotFactory factory;
    private final StableCohortHasher hasher;
    private final int retentionLimit;

    public RevisionedAuthorityRouter() {
        this(new AuthoritySnapshotFactory(), new StableCohortHasher(), 20);
    }

    public RevisionedAuthorityRouter(AuthoritySnapshotFactory factory, StableCohortHasher hasher, int retentionLimit) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        if (retentionLimit < 1) throw new IllegalArgumentException("retentionLimit must be positive");
        this.retentionLimit = retentionLimit;
    }

    public synchronized AuthoritySnapshot publish(AuthoritySnapshotData data) {
        AuthoritySnapshot candidate = factory.create(data);
        AuthoritySnapshot previous = current.get();
        if (candidate.revision() <= previous.revision()) {
            throw new IllegalArgumentException("authority revision must increase monotonically");
        }
        retainedSnapshots.addFirst(previous);
        while (retainedSnapshots.size() > retentionLimit) retainedSnapshots.removeLast();
        current.set(candidate);
        return candidate;
    }

    public AuthoritySnapshot currentSnapshot() {
        return current.get();
    }

    /**
     * Restores the exact pre-activation snapshot when the activation transaction fails after
     * the atomic swap. A later successfully activated revision is never overwritten.
     */
    public synchronized boolean restoreAfterFailedActivation(
            long failedRevision,
            AuthoritySnapshot previousSnapshot) {
        Objects.requireNonNull(previousSnapshot, "previousSnapshot");
        AuthoritySnapshot active = current.get();
        if (active.revision() != failedRevision) return false;
        current.set(previousSnapshot);
        retainedSnapshots.removeIf(snapshot -> snapshot.revision() == previousSnapshot.revision());
        return true;
    }

    public List<Long> retainedRevisions() {
        synchronized (this) {
            return retainedSnapshots.stream().map(AuthoritySnapshot::revision).toList();
        }
    }

    @Override
    public AuthorityDecision route(AuthorityRoutingContext context) {
        Objects.requireNonNull(context, "context");
        AuthoritySnapshot snapshot = current.get();
        Resolution resolution = resolve(snapshot, context.routeKey());
        if (resolution.ambiguous()) {
            return new AuthorityDecision(AuthorityPlane.LEGACY, AuthorityMode.LEGACY_ONLY, snapshot.revision(), context.routeKey(),
                    "AMBIGUOUS_ROUTE_FAIL_CLOSED", false, false, -1);
        }
        AuthorityRouteDefinition route = resolution.route();
        if (route == null) {
            return new AuthorityDecision(AuthorityPlane.LEGACY, AuthorityMode.LEGACY_ONLY, snapshot.revision(), context.routeKey(),
                    "NO_ROUTE_DEFAULT_LEGACY", false, false, -1);
        }
        return decide(snapshot.revision(), route, context);
    }

    private Resolution resolve(AuthoritySnapshot snapshot, AuthorityRouteKey candidate) {
        AuthorityRouteDefinition exact = snapshot.exactRoutes().get(candidate);
        if (exact != null) return new Resolution(exact, false);
        List<AuthorityRouteDefinition> matches = new ArrayList<>();
        int best = -1;
        for (AuthorityRouteDefinition route : snapshot.wildcardRoutes()) {
            if (!route.key().matches(candidate)) continue;
            int specificity = route.key().specificity();
            if (best < 0) best = specificity;
            if (specificity < best) break;
            matches.add(route);
        }
        if (matches.size() > 1) return new Resolution(null, true);
        return new Resolution(matches.isEmpty() ? null : matches.getFirst(), false);
    }

    private AuthorityDecision decide(long revision, AuthorityRouteDefinition route, AuthorityRoutingContext context) {
        return switch (route.mode()) {
            case LEGACY_ONLY -> decision(AuthorityPlane.LEGACY, route, revision, "LEGACY_ONLY", false, -1);
            case SHADOW -> decision(AuthorityPlane.LEGACY, route, revision, "SHADOW_LEGACY_AUTHORITY", true, -1);
            case PAUSED -> decision(AuthorityPlane.LEGACY, route, revision, "ACTIVATION_PAUSED_LEGACY_SAFE", false, -1);
            case TARGET_ONLY -> decision(AuthorityPlane.TARGET, route, revision, "TARGET_ONLY", false, -1);
            case TARGET_PRIMARY -> targetPrimary(route, context, revision);
            case TARGET_CANARY -> targetCanary(route, context, revision);
        };
    }

    private AuthorityDecision targetPrimary(AuthorityRouteDefinition route, AuthorityRoutingContext context, long revision) {
        if (route.excludeCohorts().contains(context.cohortKey())) return decision(AuthorityPlane.LEGACY, route, revision, "EXPLICIT_EXCLUDE", false, -1);
        return decision(AuthorityPlane.TARGET, route, revision,
                route.includeCohorts().contains(context.cohortKey()) ? "EXPLICIT_INCLUDE" : "TARGET_PRIMARY", false, -1);
    }

    private AuthorityDecision targetCanary(AuthorityRouteDefinition route, AuthorityRoutingContext context, long revision) {
        if (route.excludeCohorts().contains(context.cohortKey())) return decision(AuthorityPlane.LEGACY, route, revision, "EXPLICIT_EXCLUDE", false, -1);
        if (route.includeCohorts().contains(context.cohortKey())) return decision(AuthorityPlane.TARGET, route, revision, "EXPLICIT_INCLUDE", false, -1);
        int bucket = hasher.bucketBasisPoints(route.key().canonicalValue(), context.cohortKey());
        AuthorityPlane plane = bucket < route.targetBasisPoints() ? AuthorityPlane.TARGET : AuthorityPlane.LEGACY;
        return decision(plane, route, revision, plane == AuthorityPlane.TARGET ? "STABLE_HASH_CANARY_TARGET" : "STABLE_HASH_CANARY_LEGACY", false, bucket);
    }

    private AuthorityDecision decision(AuthorityPlane plane, AuthorityRouteDefinition route, long revision, String reason, boolean shadow, int bucket) {
        return new AuthorityDecision(plane, route.mode(), revision, route.key(), reason + ":" + route.reasonCode(), shadow, false, bucket);
    }

    @Override public long currentRevision() { return current.get().revision(); }
    @Override public String currentChecksum() { return current.get().checksum(); }

    private record Resolution(AuthorityRouteDefinition route, boolean ambiguous) {}
}
