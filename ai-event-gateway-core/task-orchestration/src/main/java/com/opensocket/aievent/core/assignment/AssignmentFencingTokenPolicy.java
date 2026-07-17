package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * TODO 15-D: validates assignment lease and fencing token before callback state mutation.
 */
@Service
public class AssignmentFencingTokenPolicy {

    public AssignmentFencingValidation validate(TaskAssignment assignment,
                                                String callbackAssignmentId,
                                                String callbackFencingToken,
                                                OffsetDateTime now) {
        OffsetDateTime at = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        if (assignment == null) {
            return AssignmentFencingValidation.rejected("ASSIGNMENT_NOT_FOUND", "Assignment not found");
        }
        if (blank(callbackAssignmentId)) {
            return AssignmentFencingValidation.rejected("ASSIGNMENT_ID_REQUIRED", "assignmentId is required for fenced callback");
        }
        if (!Objects.equals(assignment.getAssignmentId(), callbackAssignmentId)) {
            return AssignmentFencingValidation.rejected("ASSIGNMENT_ID_MISMATCH", "callback assignmentId does not match current assignment");
        }
        if (assignment.getLeaseExpiresAt() != null && !assignment.getLeaseExpiresAt().isAfter(at)) {
            return AssignmentFencingValidation.rejected("ASSIGNMENT_LEASE_EXPIRED", "Assignment lease expired at " + assignment.getLeaseExpiresAt());
        }
        String expected = assignment.getFencingToken();
        if (blank(expected)) {
            return AssignmentFencingValidation.accepted("Assignment has no fencing token; legacy assignment compatibility path");
        }
        if (blank(callbackFencingToken)) {
            return AssignmentFencingValidation.rejected("FENCING_TOKEN_REQUIRED", "fencingToken is required for this assignment");
        }
        if (!Objects.equals(expected, callbackFencingToken)) {
            return AssignmentFencingValidation.rejected("INVALID_FENCING_TOKEN", "callback fencingToken does not match current assignment fence");
        }
        return AssignmentFencingValidation.accepted("Assignment fencing token accepted");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
