package com.opensocket.aievent.core.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class CallbackInboxServiceTest {
    @Test
    void exposesPersistedCallbacksAsDurableInboxEntries() {
        InMemoryTaskCallbackRepository repository = new InMemoryTaskCallbackRepository();
        CallbackInboxService service = new CallbackInboxService(repository);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TaskCallbackRecord ack = callback("cb-ack", TaskCallbackType.ACK, now.minusMinutes(2));
        ack.setOwnerGatewayNodeId("gateway-node-002");
        ack.setAgentSessionId("session-old");
        repository.save(ack);

        TaskCallbackRecord result = callback("cb-result", TaskCallbackType.RESULT, now.minusMinutes(1));
        result.setOwnerGatewayNodeId("gateway-node-001");
        result.setAgentSessionId("session-new");
        repository.save(result);

        List<CallbackInboxEntry> entries = service.findByTaskId("task-001", 20);
        CallbackInboxSummary summary = service.summarizeTask("task-001", 20);

        assertEquals(2, entries.size());
        assertEquals("cb-result", entries.getFirst().getCallbackId());
        assertEquals("gateway-node-001", entries.getFirst().getReceivedByGatewayNodeId(), "callback inbox records the node that received the callback without making that node the truth source");
        assertTrue(entries.getFirst().isAuthoritative());
        assertEquals("PROCESSED", entries.getFirst().getProcessStatus());
        assertEquals(2, summary.getTotalCallbacks());
        assertEquals(2, summary.getAcceptedCallbacks());
        assertTrue(summary.isTerminalCallbackReceived());
        assertFalse(summary.isRecoveryRequired());
        assertEquals("NONE", summary.getNextAction());
    }

    @Test
    void summarizesRejectedReplayAsRecoveryRequired() {
        InMemoryTaskCallbackRepository repository = new InMemoryTaskCallbackRepository();
        CallbackInboxService service = new CallbackInboxService(repository);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TaskCallbackRecord replay = callback("cb-replay", TaskCallbackType.RESULT, now.minusMinutes(1));
        replay.setAccepted(false);
        replay.setReplayDetected(true);
        replay.setIgnoredReason("CALLBACK_REPLAY_MISMATCH");
        repository.save(replay);

        CallbackInboxSummary summary = service.summarizeDispatchRequest("dispatch-001", 20);
        List<CallbackInboxEntry> entries = service.findByDispatchRequestId("dispatch-001", 20);

        assertEquals(1, entries.size());
        assertEquals("REPLAY_REJECTED", entries.getFirst().getProcessStatus());
        assertEquals(1, summary.getRejectedCallbacks());
        assertEquals(1, summary.getReplayRejectedCallbacks());
        assertTrue(summary.isRecoveryRequired());
        assertEquals("INSPECT_CALLBACK_REJECTION", summary.getNextAction());
    }


    @Test
    void dispatchScopedInboxUsesDirectDispatchRequestQueryInsteadOfRecentWindowScan() {
        DirectQueryAssertingTaskCallbackRepository repository = new DirectQueryAssertingTaskCallbackRepository();
        CallbackInboxService service = new CallbackInboxService(repository);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        repository.save(callback("cb-other", TaskCallbackType.RESULT, now.minusMinutes(1)));
        TaskCallbackRecord exact = callback("cb-direct", TaskCallbackType.RESULT, now.minusSeconds(10));
        exact.setDispatchRequestId("dispatch-direct");
        repository.save(exact);

        List<CallbackInboxEntry> entries = service.findByDispatchRequestId("dispatch-direct", 20);

        assertEquals(1, entries.size());
        assertEquals("cb-direct", entries.getFirst().getCallbackId());
        assertEquals(1, repository.dispatchScopedQueries);
        assertEquals(0, repository.recentQueries, "dispatch-scoped callback inbox must not use recent-window scans");
    }

    private TaskCallbackRecord callback(String callbackId, TaskCallbackType type, OffsetDateTime processedAt) {
        TaskCallbackRecord callback = new TaskCallbackRecord();
        callback.setCallbackId(callbackId);
        callback.setCallbackType(type);
        callback.setTaskId("task-001");
        callback.setDispatchRequestId("dispatch-001");
        callback.setAssignmentId("assignment-001");
        callback.setAgentId("agent-001");
        callback.setAttemptNo(1);
        callback.setAccepted(true);
        callback.setProcessedAt(processedAt);
        callback.setOccurredAt(processedAt.minusSeconds(1));
        callback.setIdempotencyKey("idem-" + callbackId);
        return callback;
    }

    private static class DirectQueryAssertingTaskCallbackRepository extends InMemoryTaskCallbackRepository {
        int dispatchScopedQueries;
        int recentQueries;

        @Override
        public List<TaskCallbackRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
            dispatchScopedQueries++;
            return super.findByDispatchRequestId(dispatchRequestId, limit);
        }

        @Override
        public List<TaskCallbackRecord> recent(int limit) {
            recentQueries++;
            return super.recent(limit);
        }
    }

}
