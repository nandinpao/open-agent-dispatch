package com.opensocket.aievent.core.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AssignmentFencingTokenPolicyTest {
    private final AssignmentFencingTokenPolicy policy = new AssignmentFencingTokenPolicy();

    @Test
    void shouldAcceptMatchingFenceBeforeLeaseExpires() {
        TaskAssignment assignment = assignment("assign-1", "fence-1", OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        AssignmentFencingValidation result = policy.validate(assignment, "assign-1", "fence-1", OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void shouldRejectStaleFenceAndExpiredLease() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = assignment("assign-1", "fence-current", now.plusMinutes(5));

        assertThat(policy.validate(assignment, "assign-1", "fence-old", now).code())
                .isEqualTo("INVALID_FENCING_TOKEN");
        assignment.setLeaseExpiresAt(now.minusSeconds(1));
        assertThat(policy.validate(assignment, "assign-1", "fence-current", now).code())
                .isEqualTo("ASSIGNMENT_LEASE_EXPIRED");
    }

    private TaskAssignment assignment(String assignmentId, String fence, OffsetDateTime leaseExpiresAt) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId(assignmentId);
        assignment.setFencingToken(fence);
        assignment.setLeaseExpiresAt(leaseExpiresAt);
        return assignment;
    }
}
