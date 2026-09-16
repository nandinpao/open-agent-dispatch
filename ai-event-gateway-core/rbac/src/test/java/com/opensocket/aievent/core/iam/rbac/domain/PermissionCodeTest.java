package com.opensocket.aievent.core.iam.rbac.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PermissionCodeTest {
    @Test
    void acceptsExistingShortAndPublishedLongFormPermissionCodes() {
        assertEquals("task.read", new PermissionCode("TASK.READ").value());
        assertEquals("integration.project_mapping.manage",
                new PermissionCode("integration.project_mapping.manage").value());
        assertEquals(
                "admin.agent.remediation.workflow.lease.recovery.list.recovered.workflow.execution.leases",
                new PermissionCode(
                        "admin.agent.remediation.workflow.lease.recovery.list.recovered.workflow.execution.leases")
                        .value());
    }

    @Test
    void rejectsSingleSegmentMalformedAndDatabaseOverflowCodes() {
        assertThrows(IllegalArgumentException.class, () -> new PermissionCode("read"));
        assertThrows(IllegalArgumentException.class, () -> new PermissionCode("task..read"));
        assertThrows(IllegalArgumentException.class,
                () -> new PermissionCode("a." + "b".repeat(PermissionCode.MAX_LENGTH)));
    }
}
