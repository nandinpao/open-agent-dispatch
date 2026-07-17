package com.opensocket.aievent.core.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.InMemoryTaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackType;

class DispatchAttemptLedgerServiceTest {
    @Test
    void buildsDurableLedgerFromDispatchRowAndPersistedCallbacks() {
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        InMemoryTaskCallbackRepository callbackRepository = new InMemoryTaskCallbackRepository();
        DispatchAttemptLedgerService service = new DispatchAttemptLedgerService(dispatchRepository, callbackRepository);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchRequest dispatch = dispatch("dispatch-001", "task-001", DispatchRequestStatus.RUNNING, now.minusMinutes(3));
        dispatch.setOwnerGatewayNodeId("gateway-node-002");
        dispatch.setAgentSessionId("session-old");
        dispatch.setAttemptCount(2);
        dispatchRepository.save(dispatch);

        TaskCallbackRecord ack = callback("cb-ack", TaskCallbackType.ACK, dispatch, now.minusMinutes(2));
        ack.setOwnerGatewayNodeId("gateway-node-002");
        ack.setAgentSessionId("session-old");
        callbackRepository.save(ack);

        TaskCallbackRecord result = callback("cb-result", TaskCallbackType.RESULT, dispatch, now.minusMinutes(1));
        result.setOwnerGatewayNodeId("gateway-node-001");
        result.setAgentSessionId("session-new");
        callbackRepository.save(result);

        List<DispatchAttemptLedger> ledgers = service.findByTaskId("task-001", 20);

        assertEquals(1, ledgers.size());
        DispatchAttemptLedger ledger = ledgers.getFirst();
        assertTrue(ledger.isAuthoritative());
        assertEquals("RUNNING", ledger.getDispatchStatus());
        assertEquals("DELIVERED_TO_GATEWAY", ledger.getDeliveryState());
        assertEquals("RESULT_RECEIVED", ledger.getCallbackState());
        assertEquals("RESULT_RECEIVED", ledger.getResultState());
        assertEquals("gateway-node-002", ledger.getLastKnownGatewayNodeId(), "dispatch ledger keeps Core dispatch owner as primary and does not depend on live node memory");
        assertEquals("cb-result", ledger.getLastCallbackId());
        assertFalse(ledger.isRecoveryRequired());
        assertEquals(3, ledger.getEvents().size());
        assertEquals("CALLBACK_RESULT", ledger.getEvents().getFirst().getEventType());
    }

    @Test
    void marksRejectedCallbackAsRecoveryRequired() {
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        InMemoryTaskCallbackRepository callbackRepository = new InMemoryTaskCallbackRepository();
        DispatchAttemptLedgerService service = new DispatchAttemptLedgerService(dispatchRepository, callbackRepository);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchRequest dispatch = dispatch("dispatch-002", "task-002", DispatchRequestStatus.DISPATCHED, now.minusMinutes(5));
        dispatchRepository.save(dispatch);

        TaskCallbackRecord rejected = callback("cb-rejected", TaskCallbackType.RESULT, dispatch, now.minusMinutes(1));
        rejected.setAccepted(false);
        rejected.setIgnoredReason("CALLBACK_REPLAY_MISMATCH");
        callbackRepository.save(rejected);

        DispatchAttemptLedger ledger = service.findByDispatchRequestId("dispatch-002", 20).orElseThrow();

        assertEquals("CALLBACK_REJECTED", ledger.getCallbackState());
        assertEquals("PENDING", ledger.getResultState());
        assertTrue(ledger.isRecoveryRequired());
        assertEquals("INSPECT_CALLBACK_REJECTION", ledger.getNextAction());
    }


    @Test
    void dispatchScopedLedgerUsesDirectDispatchCallbackQueryBeforeLegacyTaskFallback() {
        InMemoryDispatchRequestRepository dispatchRepository = new InMemoryDispatchRequestRepository();
        DirectQueryAssertingTaskCallbackRepository callbackRepository = new DirectQueryAssertingTaskCallbackRepository();
        DispatchAttemptLedgerService service = new DispatchAttemptLedgerService(dispatchRepository, callbackRepository);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DispatchRequest dispatch = dispatch("dispatch-direct", "task-direct", DispatchRequestStatus.DISPATCHED, now.minusMinutes(3));
        dispatchRepository.save(dispatch);

        TaskCallbackRecord callback = callback("cb-direct-ledger", TaskCallbackType.RESULT, dispatch, now.minusMinutes(1));
        callbackRepository.save(callback);

        DispatchAttemptLedger ledger = service.findByDispatchRequestId("dispatch-direct", 20).orElseThrow();

        assertEquals("cb-direct-ledger", ledger.getLastCallbackId());
        assertEquals(1, callbackRepository.dispatchScopedQueries);
        assertEquals(0, callbackRepository.taskScopedQueries, "dispatch-scoped ledger must not query all task callbacks before exact dispatch lookup");
    }

    private DispatchRequest dispatch(String dispatchRequestId, String taskId, DispatchRequestStatus status, OffsetDateTime createdAt) {
        DispatchRequest dispatch = new DispatchRequest();
        dispatch.setDispatchRequestId(dispatchRequestId);
        dispatch.setTaskId(taskId);
        dispatch.setAssignmentId("assignment-001");
        dispatch.setAgentId("agent-001");
        dispatch.setStatus(status);
        dispatch.setDispatchToken("dispatch-token");
        dispatch.setCreatedAt(createdAt);
        dispatch.setUpdatedAt(createdAt);
        dispatch.setDispatchedAt(createdAt.plusSeconds(5));
        return dispatch;
    }

    private TaskCallbackRecord callback(String callbackId, TaskCallbackType type, DispatchRequest dispatch, OffsetDateTime processedAt) {
        TaskCallbackRecord callback = new TaskCallbackRecord();
        callback.setCallbackId(callbackId);
        callback.setCallbackType(type);
        callback.setTaskId(dispatch.getTaskId());
        callback.setDispatchRequestId(dispatch.getDispatchRequestId());
        callback.setAssignmentId(dispatch.getAssignmentId());
        callback.setAgentId(dispatch.getAgentId());
        callback.setAttemptNo(dispatch.getAttemptCount());
        callback.setAccepted(true);
        callback.setProcessedAt(processedAt);
        callback.setOccurredAt(processedAt.minusSeconds(1));
        callback.setIdempotencyKey("idem-" + callbackId);
        return callback;
    }

    private static class DirectQueryAssertingTaskCallbackRepository extends InMemoryTaskCallbackRepository {
        int dispatchScopedQueries;
        int taskScopedQueries;

        @Override
        public List<TaskCallbackRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
            dispatchScopedQueries++;
            return super.findByDispatchRequestId(dispatchRequestId, limit);
        }

        @Override
        public List<TaskCallbackRecord> findByTaskId(String taskId, int limit) {
            taskScopedQueries++;
            return super.findByTaskId(taskId, limit);
        }
    }

}
