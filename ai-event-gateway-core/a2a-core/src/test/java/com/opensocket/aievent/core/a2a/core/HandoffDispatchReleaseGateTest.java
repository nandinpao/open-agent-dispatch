package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.HandoffReconciliationClassification;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotReleaseStatus;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotStatus;
import com.opensocket.aievent.core.organization.SensitivityLevel;

class HandoffDispatchReleaseGateTest {
    private final HandoffSnapshotIntegrityGuard integrity = new HandoffSnapshotIntegrityGuard();
    private final HandoffDispatchReleaseGate gate = new HandoffDispatchReleaseGate();

    @Test
    void releasesOnlyWhenCurrentBindingAndContentMatch() {
        HandoffContextSnapshot snapshot = snapshot(OffsetDateTime.now().plusMinutes(10));
        var binding = new HandoffDispatchReleaseGate.CurrentBinding(
                "tenant-a", "source-task", "target-task", "source-agent", "target-agent", "target-domain", 3);
        assertTrue(gate.evaluate(snapshot, binding, OffsetDateTime.now()).allowed());
        var changedAgent = new HandoffDispatchReleaseGate.CurrentBinding(
                "tenant-a", "source-task", "target-task", "source-agent", "other-agent", "target-domain", 3);
        assertFalse(gate.evaluate(snapshot, changedAgent, OffsetDateTime.now()).allowed());
    }

    @Test
    void rejectsExpiredSnapshot() {
        HandoffContextSnapshot snapshot = snapshot(OffsetDateTime.now().minusSeconds(1));
        var binding = new HandoffDispatchReleaseGate.CurrentBinding(
                "tenant-a", "source-task", "target-task", "source-agent", "target-agent", "target-domain", 3);
        assertFalse(gate.evaluate(snapshot, binding, OffsetDateTime.now()).allowed());
    }

    private HandoffContextSnapshot snapshot(OffsetDateTime expiresAt) {
        String binding = integrity.targetBindingHash("tenant-a", "target-task", "target-agent", "target-domain");
        String hash = integrity.contentHash(2, "tenant-a", "root-task", "source-task", "target-task",
                "source-agent", "target-agent", "target-domain", binding, "policy-a", 3, 1,
                "summary", Map.of("safe", true), List.of(), List.of(), List.of(), List.of());
        return new HandoffContextSnapshot("tenant-a", "snapshot-a", "aggregate-a", 2,
                "root-task", "source-task", "target-task", "source-agent", "target-agent",
                "target-domain", binding, "policy-a", 3, 1, "summary", Map.of("safe", true),
                List.of(), List.of(), List.of(), List.of(), SensitivityLevel.INTERNAL, hash,
                OffsetDateTime.now(), OffsetDateTime.now(), "SYSTEM", "test", expiresAt,
                HandoffSnapshotStatus.APPROVED, "approver", OffsetDateTime.now(), "approval-hash",
                null, "correlation-a", List.of(), HandoffSnapshotReleaseStatus.READY, null, null,
                null, HandoffReconciliationClassification.NONE, OffsetDateTime.now(), 0, 1);
    }
}
