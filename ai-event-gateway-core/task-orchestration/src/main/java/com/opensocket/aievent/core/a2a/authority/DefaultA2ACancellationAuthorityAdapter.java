package com.opensocket.aievent.core.a2a.authority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.application.port.out.A2ACancellationAuthorityOperations;
import com.opensocket.aievent.core.assignment.*;
import com.opensocket.aievent.core.dispatch.*;
import com.opensocket.aievent.core.executionattempt.*;
import com.opensocket.aievent.core.task.*;

/** Rotates assignment/attempt fencing before runtime cancellation delivery. */
@Component
public class DefaultA2ACancellationAuthorityAdapter implements A2ACancellationAuthorityOperations {
    private final TaskAssignmentRepository assignments;
    private final TaskExecutionAttemptRepository attempts;
    private final DispatchRequestRepository dispatches;
    private final TaskAssignmentService assignmentService;

    public DefaultA2ACancellationAuthorityAdapter(TaskAssignmentRepository assignments,
            TaskExecutionAttemptRepository attempts, DispatchRequestRepository dispatches,
            TaskAssignmentService assignmentService) {
        this.assignments=assignments; this.attempts=attempts; this.dispatches=dispatches;
        this.assignmentService=assignmentService;
    }

    @Override
    public CancellationAuthorityReceipt revoke(A2ARequest request, TaskRecord child,
            String cancellationId, String reason) {
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        if (child.getStatus().isSucceeded() || child.getStatus()==TaskStatus.PARTIALLY_COMPLETED) {
            return new CancellationAuthorityReceipt(null,null,null,null,null,null,null,null,null,
                    false,true,"AGENT_ALREADY_COMPLETED");
        }
        TaskAssignment assignment=assignments.findOpenByTenantAndTaskId(
                request.getTenantId(),child.getTaskId()).orElse(null);
        if (assignment==null) {
            return new CancellationAuthorityReceipt(null,null,null,null,null,null,null,null,null,
                    false,false,"NO_OPEN_ASSIGNMENT");
        }
        String oldFence=assignment.getFencingToken();
        String rotatedFence="fence-revoked-"+UUID.randomUUID();
        boolean runtime=child.getStatus()==TaskStatus.RUNNING;
        TaskExecutionAttempt execution=attempts.findCurrentByAssignmentId(
                assignment.getAssignmentId()).orElse(null);
        if (execution!=null) {
            if (execution.getStatus()==TaskExecutionAttemptStatus.SUCCEEDED) {
                return new CancellationAuthorityReceipt(assignment.getAssignmentId(),
                        execution.getExecutionAttemptId(),execution.getAttemptNo(),null,
                        assignment.getAgentId(),assignment.getAgentSessionId(),
                        assignment.getOwnerGatewayNodeId(),hash(oldFence),hash(oldFence),
                        false,true,"AGENT_ALREADY_COMPLETED");
            }
            runtime=runtime||execution.getStatus()==TaskExecutionAttemptStatus.RUNNING
                    || execution.getStatus()==TaskExecutionAttemptStatus.CREATED;
            execution.setStatus(TaskExecutionAttemptStatus.CANCELLED);
            execution.setFencingToken(rotatedFence);
            execution.setErrorCode("A2A_CANCEL_REQUESTED");
            execution.setErrorMessage(reason);
            execution.setCompletedAt(now);
            execution.setUpdatedAt(now);
            attempts.save(execution);
        }
        DispatchRequest dispatch=dispatches.findOpenByAssignmentId(
                assignment.getAssignmentId()).orElse(null);
        if (dispatch!=null) {
            runtime=runtime||Set.of(DispatchRequestStatus.DISPATCHED,DispatchRequestStatus.ACKED,
                    DispatchRequestStatus.RUNNING,DispatchRequestStatus.DISPATCHING,DispatchRequestStatus.DELIVERY_UNKNOWN)
                    .contains(dispatch.getStatus());
            dispatch.setStatus(DispatchRequestStatus.CANCELLED);
            dispatch.setReason(reason);
            dispatch.setUpdatedAt(now);
            dispatches.save(dispatch);
        }
        assignment.setStatus(AssignmentStatus.CANCELLED);
        assignment.setFencingToken(rotatedFence);
        assignment.setLeaseExpiresAt(now);
        assignment.setReason(reason);
        assignment.setUpdatedAt(now);
        assignments.save(assignment);
        assignmentService.releaseCapacityReservation(assignment.getAssignmentId());
        return new CancellationAuthorityReceipt(assignment.getAssignmentId(),
                execution==null?null:execution.getExecutionAttemptId(),
                execution==null?null:execution.getAttemptNo(),
                dispatch==null?null:dispatch.getDispatchRequestId(),assignment.getAgentId(),
                assignment.getAgentSessionId(),assignment.getOwnerGatewayNodeId(),hash(oldFence),
                hash(rotatedFence),runtime,false,"ASSIGNMENT_AND_ATTEMPT_REVOKED");
    }

    private String hash(String value) {
        if (value==null||value.isBlank()) return null;
        try { return "sec-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
