package com.opensocket.aievent.core.enforcement.activation.core;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.enforcement.activation.contract.*;

class UnifiedEnforcementKernelTest {
    private static AuthorityRoutingContext context(String cohort) {
        return new AuthorityRoutingContext("tenant-a", "TASK", "READ", "READ", "task.list", cohort, "corr-1");
    }

    @Test void hardGuardDenialCannotBeBypassedByTargetOnly() {
        RevisionedAuthorityRouter router = router(new AuthorityRouteDefinition(context("x").routeKey(), AuthorityMode.TARGET_ONLY, 10_000, Set.of(), Set.of(), "test"));
        UnifiedEnforcementKernel kernel = new UnifiedEnforcementKernel(value -> HardGuardDecision.deny("TENANT_ISOLATION_DENIED"), router);
        AuthorityDecision decision = kernel.decide(context("x"));
        assertEquals(AuthorityPlane.NONE, decision.plane());
        assertTrue(decision.hardGuardDenied());
    }

    @Test void exactRouteWinsOverWildcard() {
        AuthorityRouteDefinition wildcard = new AuthorityRouteDefinition(new AuthorityRouteKey("*", "TASK", "*", "READ", "*"), AuthorityMode.LEGACY_ONLY, 0, Set.of(), Set.of(), "wildcard");
        AuthorityRouteDefinition exact = new AuthorityRouteDefinition(context("x").routeKey(), AuthorityMode.TARGET_ONLY, 10_000, Set.of(), Set.of(), "exact");
        RevisionedAuthorityRouter router = router(wildcard, exact);
        assertEquals(AuthorityPlane.TARGET, router.route(context("x")).plane());
    }

    @Test void canaryAssignmentIsStableAndHonorsCohorts() {
        AuthorityRouteDefinition route = new AuthorityRouteDefinition(context("x").routeKey(), AuthorityMode.TARGET_CANARY, 2500, Set.of("force"), Set.of("blocked"), "canary");
        RevisionedAuthorityRouter router = router(route);
        AuthorityDecision one = router.route(context("normal"));
        AuthorityDecision two = router.route(context("normal"));
        assertEquals(one.plane(), two.plane());
        assertEquals(one.cohortBucketBasisPoints(), two.cohortBucketBasisPoints());
        assertEquals(AuthorityPlane.TARGET, router.route(context("force")).plane());
        assertEquals(AuthorityPlane.LEGACY, router.route(context("blocked")).plane());
    }


    @Test void equalSpecificityWildcardAmbiguityFailsClosedToLegacy() {
        AuthorityRouteDefinition byTenant = new AuthorityRouteDefinition(new AuthorityRouteKey("tenant-a", "TASK", "*", "READ", "*"), AuthorityMode.TARGET_ONLY, 10_000, Set.of(), Set.of(), "tenant-route");
        AuthorityRouteDefinition byUnit = new AuthorityRouteDefinition(new AuthorityRouteKey("*", "TASK", "READ", "READ", "*"), AuthorityMode.TARGET_ONLY, 10_000, Set.of(), Set.of(), "unit-route");
        RevisionedAuthorityRouter router = router(byTenant, byUnit);
        AuthorityDecision decision = router.route(context("x"));
        assertEquals(AuthorityPlane.LEGACY, decision.plane());
        assertEquals("AMBIGUOUS_ROUTE_FAIL_CLOSED", decision.reasonCode());
    }

    @Test void snapshotLoaderIsIdempotentButRejectsSameRevisionMutation() {
        AuthorityRouteDefinition route = new AuthorityRouteDefinition(context("x").routeKey(), AuthorityMode.SHADOW, 0, Set.of(), Set.of(), "shadow");
        AuthoritySnapshotFactory factory = new AuthoritySnapshotFactory();
        AuthoritySnapshotData data = new AuthoritySnapshotData(1, Instant.now(), factory.checksum(1, List.of(route)), List.of(route));
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        AuthoritySnapshotLoader loader = new AuthoritySnapshotLoader(repository(data), router);
        assertEquals(1, loader.loadLatestPublished().revision());
        assertEquals(1, loader.loadLatestPublished().revision());
        AuthorityRouteDefinition changed = new AuthorityRouteDefinition(context("x").routeKey(), AuthorityMode.TARGET_ONLY, 10_000, Set.of(), Set.of(), "changed");
        AuthoritySnapshotData mutated = new AuthoritySnapshotData(1, Instant.now(), "", List.of(changed));
        AuthoritySnapshotLoader invalid = new AuthoritySnapshotLoader(repository(mutated), router);
        assertThrows(IllegalStateException.class, invalid::loadLatestPublished);
    }

    @Test void revisionsAreMonotonicAndChecksumProtected() {
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        AuthorityRouteDefinition route = new AuthorityRouteDefinition(context("x").routeKey(), AuthorityMode.SHADOW, 0, Set.of(), Set.of(), "shadow");
        AuthoritySnapshotFactory factory = new AuthoritySnapshotFactory();
        String checksum = factory.checksum(1, List.of(route));
        router.publish(new AuthoritySnapshotData(1, Instant.now(), checksum, List.of(route)));
        assertThrows(IllegalArgumentException.class, () -> router.publish(new AuthoritySnapshotData(1, Instant.now(), checksum, List.of(route))));
        assertThrows(IllegalArgumentException.class, () -> new AuthoritySnapshotFactory().create(new AuthoritySnapshotData(2, Instant.now(), "sha256:bad", List.of(route))));
    }


    private static AuthoritySnapshotRepository repository(AuthoritySnapshotData data) {
        return new AuthoritySnapshotRepository() {
            @Override public java.util.Optional<AuthoritySnapshotData> findLatestPublished() {
                return java.util.Optional.of(data);
            }
            @Override public java.util.Optional<AuthoritySnapshotData> findPublished(long revision) {
                return data.revision() == revision ? java.util.Optional.of(data) : java.util.Optional.empty();
            }
        };
    }

    private static RevisionedAuthorityRouter router(AuthorityRouteDefinition... routes) {
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        router.publish(new AuthoritySnapshotData(1, Instant.now(), "", List.of(routes)));
        return router;
    }
}
