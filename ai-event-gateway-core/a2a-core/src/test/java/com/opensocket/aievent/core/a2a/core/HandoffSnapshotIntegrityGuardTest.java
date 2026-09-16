package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HandoffSnapshotIntegrityGuardTest {
    private final HandoffSnapshotIntegrityGuard guard = new HandoffSnapshotIntegrityGuard();

    @Test void hashIsDeterministicAndBoundToTenantAndTarget() {
        String a = hash("tenant-a", "task-b");
        assertEquals(a, hash("tenant-a", "task-b"));
        assertNotEquals(a, hash("tenant-b", "task-b"));
        assertNotEquals(a, hash("tenant-a", "task-c"));
    }
    @Test void rejectsTampering() {
        assertThrows(IllegalStateException.class, () -> guard.verify("tenant-a", "task-b", "domain-b",
                OffsetDateTime.now().plusMinutes(5), "APPROVED", "expected", "tampered"));
    }
    @Test void rejectsExpiredSnapshot() {
        assertThrows(IllegalStateException.class, () -> guard.verify("tenant-a", "task-b", "domain-b",
                OffsetDateTime.now().minusSeconds(1), "APPROVED", "same", "same"));
    }
    @Test void rejectsWrongTargetBinding() {
        assertThrows(IllegalStateException.class, () -> guard.verifyBinding("domain-b", "domain-c",
                "HANDOFF_CONTEXT_TARGET_BINDING_MISMATCH"));
    }

    @Test void schemaTwoBindsSourceAndTargetAgents() {
        String binding = guard.targetBindingHash("tenant-a", "task-b", "agent-b", "domain-b");
        String a = guard.contentHash(2, "tenant-a", "root-1", "task-a", "task-b",
                "agent-a", "agent-b", "domain-b", binding, "policy-1", 3, 1,
                "summary", Map.of("safe", true), List.of(), List.of(), List.of(), List.of());
        String changed = guard.contentHash(2, "tenant-a", "root-1", "task-a", "task-b",
                "agent-a", "agent-c", "domain-b", binding, "policy-1", 3, 1,
                "summary", Map.of("safe", true), List.of(), List.of(), List.of(), List.of());
        assertNotEquals(a, changed);
    }
    private String hash(String tenant, String target) {
        return guard.contentHash(tenant, "root-1", "task-a", target, "domain-b", "assignment-a",
                "policy-1", 3, 1, "summary", Map.of("safe", true), List.of(), List.of(), List.of(), List.of());
    }
}
