package com.opensocket.aievent.core.uicapability.contract;

import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiCapabilityContractTest {
    @Test
    void keepsCanonicalBatchLimitsDistinct() {
        assertEquals(20, UiCapabilityBatchLimits.GENERIC_CONTEXTS);
        assertEquals(50, UiCapabilityBatchLimits.LIST_ROW_CONTEXTS);
        assertEquals(131072, UiCapabilityBatchLimits.GENERIC_MAX_PAYLOAD_BYTES);
        assertEquals(262144, UiCapabilityBatchLimits.LIST_ROW_MAX_PAYLOAD_BYTES);
    }

    @Test
    void rejectsStepUpBooleanDrift() {
        assertThrows(IllegalArgumentException.class, () -> new UiCapability(
                "agent.credential.rotate",
                UiDisplayMode.STEP_UP_REQUIRED,
                UiReasonCategory.STEP_UP_REQUIRED,
                false,
                false,
                VisibilityLevel.SECRET_METADATA,
                false,
                "agent-credential-rotation"));
    }

    @Test
    void rejectsDuplicateActionsInEnvelope() {
        UiCapability action = new UiCapability(
                "a2a.request.read",
                UiDisplayMode.ENABLED,
                null,
                false,
                false,
                VisibilityLevel.STANDARD,
                false,
                "");
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new UiCapabilityEnvelope(
                UiCapabilityContract.VERSION,
                "a2a.request.detail",
                "tenant-a",
                1,
                1,
                1,
                "hash",
                3L,
                "FULL_ENFORCE",
                List.of(action, action),
                now.plusSeconds(60),
                now.plusSeconds(30),
                ""));
    }
    @Test
    void rejectsEpochOutsideJsonSafeIntegerRange() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new UiCapabilityEnvelope(
                UiCapabilityContract.VERSION,
                "task.detail",
                "tenant-a",
                UiWireNumbers.MAX_JSON_SAFE_INTEGER + 1,
                1,
                1,
                "hash",
                1L,
                "FULL_ENFORCE",
                List.of(),
                now.plusSeconds(60),
                now.plusSeconds(30),
                ""));
    }

}
