package com.opensocket.aievent.core.iam.rbac.application.service;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.rbac.application.port.out.ShadowDecisionRecorderPort;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ShadowAuthorizationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @Test void mismatchAlwaysUsesCriticalLaneAndRemovesSensitiveMetadata() {
        Recorder recorder = new Recorder();
        ShadowAuthorizationService service = new ShadowAuthorizationService(recorder, 0, 16,
                Clock.fixed(NOW, ZoneOffset.UTC));
        PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-1");
        AuthorizationRequest request = new AuthorizationRequest(principal, TenantRef.tenant("tenant-a"),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-a", SecurityEpoch.ZERO,
                Map.of("traceId", "trace-1", "password", "do-not-store", "requestBody", "secret"));
        AuthorizationDecision decision = new AuthorizationDecision("decision-1", AuthorizationDecision.Effect.DENY,
                RbacReasonCode.AUTH_PERMISSION_DENIED.name(), Set.of(), Set.of(), "TENANT", "tenant-a",
                SecurityEpoch.ZERO, NOW);
        ShadowDecisionRecord record = service.compare(LegacyDecision.ALLOW, request, decision, "/api/tasks");
        assertEquals(ShadowLane.CRITICAL, record.lane());
        assertSame(record, recorder.critical);
        assertEquals(Map.of("traceId", "trace-1"), record.metadata());
    }

    @Test void heartbeatMatchUsesAggregateLane() {
        Recorder recorder = new Recorder();
        ShadowAuthorizationService service = new ShadowAuthorizationService(recorder, 1, 16,
                Clock.fixed(NOW, ZoneOffset.UTC));
        PrincipalRef principal = new PrincipalRef(PrincipalRef.PrincipalType.USER, "user-1");
        AuthorizationRequest request = new AuthorizationRequest(principal, TenantRef.tenant("tenant-a"),
                "identity.user.read", "USER", "user-2", "TENANT", "tenant-a", SecurityEpoch.ZERO, Map.of());
        AuthorizationDecision decision = new AuthorizationDecision("decision-1", AuthorizationDecision.Effect.ALLOW,
                RbacReasonCode.AUTH_PERMISSION_GRANTED.name(), Set.of("binding"), Set.of("role"), "TENANT",
                "tenant-a", SecurityEpoch.ZERO, NOW);
        ShadowDecisionRecord record = service.compare(LegacyDecision.ALLOW, request, decision, "/api/agents/heartbeat");
        assertEquals(ShadowLane.AGGREGATE, record.lane());
        assertSame(record, recorder.aggregate);
    }

    private static final class Recorder implements ShadowDecisionRecorderPort {
        ShadowDecisionRecord critical; ShadowDecisionRecord aggregate;
        public void recordCritical(ShadowDecisionRecord record) { critical = record; }
        public boolean enqueueSample(ShadowDecisionRecord record) { return true; }
        public void aggregate(ShadowDecisionRecord record) { aggregate = record; }
    }
}
