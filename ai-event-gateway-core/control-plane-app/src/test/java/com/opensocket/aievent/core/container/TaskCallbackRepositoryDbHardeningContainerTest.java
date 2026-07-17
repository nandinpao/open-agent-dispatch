package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;
import com.opensocket.aievent.core.callback.TaskCallbackType;

class TaskCallbackRepositoryDbHardeningContainerTest extends P25RepositoryDbContainerSupport {

    @Test
    void callbackReserveMustBeIdempotentAndUpsertMustPreserveReplayMetadata() throws Exception {
        TaskCallbackRepository repository = taskCallbackRepository();
        TaskCallbackRecord first = callback("callback-db-1", "task-cb-1", TaskCallbackType.ACK);

        List<Boolean> reservations = runConcurrent(List.of(
                () -> repository.tryReserve(first),
                () -> repository.tryReserve(callback("callback-db-1", "task-cb-1", TaskCallbackType.ACK))));

        assertThat(reservations).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("select count(*) from task_callbacks where callback_id = 'callback-db-1'", Long.class))
                .isEqualTo(1L);

        TaskCallbackRecord replay = callback("callback-db-1", "task-cb-1", TaskCallbackType.RESULT);
        replay.setDuplicate(true);
        replay.setReplayDetected(true);
        replay.setIgnoredReason("DUPLICATE_CALLBACK_ID");
        replay.setPreviousTaskStatus("RUNNING");
        replay.setNewTaskStatus("COMPLETED");
        replay.setPreviousDispatchStatus("RUNNING");
        replay.setNewDispatchStatus("COMPLETED");
        replay.setPayload(Map.of("result", "ok"));
        repository.save(replay);

        assertThat(repository.findByCallbackId("callback-db-1"))
                .hasValueSatisfying(record -> {
                    assertThat(record.getCallbackType()).isEqualTo(TaskCallbackType.RESULT);
                    assertThat(record.isDuplicate()).isTrue();
                    assertThat(record.isReplayDetected()).isTrue();
                    assertThat(record.getIgnoredReason()).isEqualTo("DUPLICATE_CALLBACK_ID");
                    assertThat(record.getPayload()).containsEntry("result", "ok");
                });
    }

    @Test
    void taskAndRecentQueriesMustReturnNewestProcessedCallbacksFirst() {
        TaskCallbackRepository repository = taskCallbackRepository();
        repository.save(callback("callback-db-2a", "task-cb-2", TaskCallbackType.ACK));
        TaskCallbackRecord progress = callback("callback-db-2b", "task-cb-2", TaskCallbackType.PROGRESS);
        progress.setProcessedAt(now().plusSeconds(10));
        progress.setProgressPercent(60);
        repository.save(progress);
        TaskCallbackRecord result = callback("callback-db-2c", "task-cb-2", TaskCallbackType.RESULT);
        result.setProcessedAt(now().plusSeconds(20));
        repository.save(result);

        assertThat(repository.findByTaskId("task-cb-2", 2))
                .extracting(TaskCallbackRecord::getCallbackId)
                .containsExactly("callback-db-2c", "callback-db-2b");
        assertThat(repository.recent(1))
                .singleElement()
                .satisfies(record -> assertThat(record.getCallbackId()).isEqualTo("callback-db-2c"));
    }


    @Test
    void dispatchRequestQueryMustReturnOnlyCallbacksForThatDispatchNewestFirst() {
        TaskCallbackRepository repository = taskCallbackRepository();
        TaskCallbackRecord older = callback("callback-db-direct-1", "task-cb-direct", TaskCallbackType.ACK);
        older.setDispatchRequestId("dispatch-db-direct-a");
        older.setProcessedAt(now().plusSeconds(1));
        repository.save(older);

        TaskCallbackRecord newest = callback("callback-db-direct-2", "task-cb-direct", TaskCallbackType.RESULT);
        newest.setDispatchRequestId("dispatch-db-direct-a");
        newest.setProcessedAt(now().plusSeconds(20));
        repository.save(newest);

        TaskCallbackRecord otherDispatch = callback("callback-db-direct-3", "task-cb-direct", TaskCallbackType.ERROR);
        otherDispatch.setDispatchRequestId("dispatch-db-direct-b");
        otherDispatch.setProcessedAt(now().plusSeconds(30));
        repository.save(otherDispatch);

        assertThat(repository.findByDispatchRequestId("dispatch-db-direct-a", 10))
                .extracting(TaskCallbackRecord::getCallbackId)
                .containsExactly("callback-db-direct-2", "callback-db-direct-1");
    }

    private TaskCallbackRecord callback(String callbackId, String taskId, TaskCallbackType type) {
        TaskCallbackRecord record = new TaskCallbackRecord();
        record.setCallbackId(callbackId);
        record.setCallbackType(type);
        record.setTaskId(taskId);
        record.setDispatchRequestId("dispatch-" + taskId);
        record.setAssignmentId("assignment-" + taskId);
        record.setAgentId("agent-db-1");
        record.setOwnerGatewayNodeId("gateway-db-1");
        record.setAgentSessionId("session-db-1");
        record.setAttemptNo(1);
        record.setFencingToken("fence-1");
        record.setAccepted(true);
        record.setMessage("P25 repository DB hardening");
        record.setPayload(Map.of("stage", "P25"));
        record.setOccurredAt(now());
        record.setProcessedAt(now());
        record.setIdempotencyKey("idem-" + callbackId);
        record.setCallbackFingerprint("fp-" + callbackId);
        return record;
    }
}
