package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.a2a.*;

class A2AStateMachineTest {
    private final A2AStateMachine machine = new A2AStateMachine();

    @Test
    void allowsVersionedApproval() {
        A2ARequest request = request(A2ARequestStatus.WAITING_APPROVAL,
                A2AOperationalStage.WAITING_APPROVAL, 7, future());
        var decision = machine.decide(request, A2ATransitionCommand.APPROVE,
                A2ARequestStatus.APPROVED, 7L, "approved", now());
        assertEquals(A2AOperationalStage.APPROVED, decision.toStage());
        assertEquals(8, decision.resultingVersion());
        assertEquals(A2ATransitionPermission.A2A_APPROVE, decision.requiredPermission());
        assertEquals(A2ATransitionEvidenceType.APPROVAL_EVIDENCE, decision.evidenceType());
    }

    @Test
    void rejectsMissingExpectedVersion() {
        A2ARequest request = request(A2ARequestStatus.WAITING_APPROVAL,
                A2AOperationalStage.WAITING_APPROVAL, 7, future());
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(request, A2ATransitionCommand.APPROVE,
                        A2ARequestStatus.APPROVED, null, "approved", now()));
        assertEquals(A2ATransitionFailureReason.EXPECTED_VERSION_REQUIRED, exception.reason());
    }

    @Test
    void rejectsStaleMutation() {
        A2ARequest request = request(A2ARequestStatus.WAITING_APPROVAL,
                A2AOperationalStage.WAITING_APPROVAL, 7, future());
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(request, A2ATransitionCommand.APPROVE,
                        A2ARequestStatus.APPROVED, 6L, "approved", now()));
        assertEquals(A2ATransitionFailureReason.RESOURCE_VERSION_CONFLICT, exception.reason());
    }

    @Test
    void rejectsInvalidCommandTargetPair() {
        A2ARequest request = request(A2ARequestStatus.REQUESTED,
                A2AOperationalStage.REQUESTED, 1, future());
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(request, A2ATransitionCommand.COMPLETE,
                        A2ARequestStatus.COMPLETED, 1L, "done", now()));
        assertEquals(A2ATransitionFailureReason.COMMAND_TARGET_MISMATCH, exception.reason());
    }

    @Test
    void rejectsMutationOfTerminalRequest() {
        A2ARequest request = request(A2ARequestStatus.COMPLETED,
                A2AOperationalStage.TERMINAL, 11, future());
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(request, A2ATransitionCommand.FAIL,
                        A2ARequestStatus.FAILED, 11L, "late failure", now()));
        assertEquals(A2ATransitionFailureReason.TERMINAL_STATE_IMMUTABLE, exception.reason());
    }

    @Test
    void childCreationUsesOperationalStagesWithoutInventingLifecycleStatuses() {
        A2ARequest approved = request(A2ARequestStatus.APPROVED,
                A2AOperationalStage.APPROVED, 4, future());
        var creating = machine.decide(approved, A2ATransitionCommand.BEGIN_CHILD_CREATION,
                A2ARequestStatus.APPROVED, 4L, "begin", now());
        assertEquals(A2ARequestStatus.APPROVED, creating.toStatus());
        assertEquals(A2AOperationalStage.CHILD_TASK_CREATING, creating.toStage());

        A2ARequest inProgress = request(A2ARequestStatus.APPROVED,
                A2AOperationalStage.CHILD_TASK_CREATING, 5, future());
        var created = machine.decide(inProgress, A2ATransitionCommand.CREATE_CHILD,
                A2ARequestStatus.CHILD_TASK_CREATED, 5L, "created", now());
        assertEquals(A2AOperationalStage.CHILD_TASK_CREATED, created.toStage());
    }

    @Test
    void dispatchRequestAndWaitingResultAreExplicitOperationalStages() {
        A2ARequest child = request(A2ARequestStatus.CHILD_TASK_CREATED,
                A2AOperationalStage.CHILD_TASK_CREATED, 6, future());
        var dispatchRequested = machine.decide(child, A2ATransitionCommand.REQUEST_DISPATCH,
                A2ARequestStatus.CHILD_TASK_CREATED, 6L, "outboxed", now());
        assertEquals(A2AOperationalStage.DISPATCH_REQUESTED, dispatchRequested.toStage());

        A2ARequest running = request(A2ARequestStatus.RUNNING,
                A2AOperationalStage.RUNNING, 9, future());
        var waiting = machine.decide(running, A2ATransitionCommand.WAIT_FOR_RESULT,
                A2ARequestStatus.RUNNING, 9L, "await result", now());
        assertEquals(A2AOperationalStage.WAITING_RESULT, waiting.toStage());
    }

    @Test
    void blockedStageRequiresStableBlockerCode() {
        A2ARequest child = request(A2ARequestStatus.CHILD_TASK_CREATED,
                A2AOperationalStage.CHILD_TASK_CREATED, 6, future());
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(child, A2ATransitionCommand.MARK_BLOCKED,
                        A2ARequestStatus.CHILD_TASK_CREATED, 6L, "blocked", now()));
        assertEquals(A2ATransitionFailureReason.BLOCKER_REQUIRED, exception.reason());

        var blocked = machine.decide(child, A2ATransitionCommand.MARK_BLOCKED,
                A2ARequestStatus.CHILD_TASK_CREATED, A2ABlockerCode.HANDOFF_REQUIRED,
                6L, "handoff missing", now());
        assertEquals(A2AOperationalStage.BLOCKED, blocked.toStage());
        assertEquals(A2ABlockerCode.HANDOFF_REQUIRED, blocked.blockerCode());
    }

    @Test
    void recoveryTransitionIsMarkedAndClearsBlocker() {
        A2ARequest blocked = request(A2ARequestStatus.CHILD_TASK_CREATED,
                A2AOperationalStage.BLOCKED, 7, future());
        blocked.setBlockerCode(A2ABlockerCode.HANDOFF_REQUIRED);
        var recovered = machine.decide(blocked, A2ATransitionCommand.RECOVER,
                A2ARequestStatus.CHILD_TASK_CREATED, 7L, "evidence repaired", now());
        assertTrue(recovered.recovery());
        assertEquals(A2AOperationalStage.DISPATCH_REQUESTED, recovered.toStage());
        assertEquals(A2ABlockerCode.NONE, recovered.blockerCode());
    }

    @Test
    void activeRequestUsesCancelIntentBeforeConfirmation() {
        A2ARequest running = request(A2ARequestStatus.RUNNING,
                A2AOperationalStage.WAITING_RESULT, 11, future());
        var requested = machine.decide(running, A2ATransitionCommand.CANCEL,
                A2ARequestStatus.CANCEL_REQUESTED, 11L, "operator cancellation", now());
        assertEquals(A2AOperationalStage.CANCEL_REQUESTED, requested.toStage());

        A2ARequest pending = request(A2ARequestStatus.CANCEL_REQUESTED,
                A2AOperationalStage.CANCEL_REQUESTED, 12, future());
        var confirmed = machine.decide(pending, A2ATransitionCommand.CONFIRM_CANCEL,
                A2ARequestStatus.CANCELLED_CONFIRMED, 12L, "runtime acknowledged", now());
        assertEquals(A2AOperationalStage.TERMINAL, confirmed.toStage());
        assertEquals(13, confirmed.resultingVersion());
    }

    @Test
    void rejectsExpiredApprovalUnlessCommandExpiresIt() {
        A2ARequest request = request(A2ARequestStatus.WAITING_APPROVAL,
                A2AOperationalStage.WAITING_APPROVAL, 2, now().minusSeconds(1));
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(request, A2ATransitionCommand.APPROVE,
                        A2ARequestStatus.APPROVED, 2L, "approved", now()));
        assertEquals(A2ATransitionFailureReason.APPROVAL_EXPIRED, exception.reason());

        var expired = machine.decide(request, A2ATransitionCommand.EXPIRE,
                A2ARequestStatus.EXPIRED, 2L, "approval expired", now());
        assertEquals(A2AOperationalStage.TERMINAL, expired.toStage());
    }

    @Test
    void missingEvidenceIsRejected() {
        A2ARequest request = request(A2ARequestStatus.WAITING_APPROVAL,
                A2AOperationalStage.WAITING_APPROVAL, 7, future());
        var exception = assertThrows(A2ATransitionException.class,
                () -> machine.decide(request, A2ATransitionCommand.APPROVE,
                        A2ARequestStatus.APPROVED, A2ABlockerCode.NONE,
                        7L, "approved", "   ", now()));
        assertEquals(A2ATransitionFailureReason.EVIDENCE_REQUIRED, exception.reason());
    }

    private A2ARequest request(A2ARequestStatus status, A2AOperationalStage stage,
            long version, OffsetDateTime expiresAt) {
        A2ARequest request = new A2ARequest();
        request.setRequestStatus(status);
        request.setOperationalStage(stage);
        request.setVersion(version);
        request.setExpiresAt(expiresAt);
        return request;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private OffsetDateTime future() {
        return now().plusMinutes(5);
    }
}
