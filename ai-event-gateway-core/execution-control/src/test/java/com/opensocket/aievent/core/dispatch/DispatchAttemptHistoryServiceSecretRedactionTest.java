package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class DispatchAttemptHistoryServiceSecretRedactionTest {

    @Test
    void shouldNotPersistRawDispatchTokenInUnconfirmedDeliveryHistoryPayload() {
        CapturingRepository repository = new CapturingRepository();
        DispatchAttemptHistoryService service = new DispatchAttemptHistoryService(repository, new ObjectMapper());

        DispatchRequest request = new DispatchRequest();
        request.setTaskId("task-1");
        request.setDispatchRequestId("dispatch-1");
        request.setDispatchToken("raw-dispatch-token-super-secret");
        request.setAttemptCount(3);

        service.recordGatewayDeliveredUnconfirmed(
                request,
                GatewayDispatchResult.success(202, null),
                "CLAIM_LOST_AFTER_GATEWAY_ACCEPTED",
                OffsetDateTime.parse("2026-06-25T00:00:00Z"));

        assertThat(repository.records).hasSize(1);
        String payload = repository.records.getFirst().getPayloadJson();
        assertThat(payload).doesNotContain("raw-dispatch-token-super-secret");
        assertThat(payload).doesNotContain("dispatchToken");
        assertThat(payload).contains("dispatchAuthPresent");
        assertThat(payload).contains("dispatchAuthFingerprint");
        assertThat(payload).contains("sha256:");
    }

    private static class CapturingRepository implements DispatchAttemptHistoryRepository {
        private final List<DispatchAttemptHistoryRecord> records = new ArrayList<>();

        @Override
        public DispatchAttemptHistoryRecord append(DispatchAttemptHistoryRecord record) {
            records.add(record);
            return record;
        }

        @Override
        public List<DispatchAttemptHistoryRecord> findByTaskId(String taskId, int limit) {
            return List.of();
        }

        @Override
        public List<DispatchAttemptHistoryRecord> findByDispatchRequestId(String dispatchRequestId, int limit) {
            return List.of();
        }

        @Override
        public List<DispatchAttemptHistoryRecord> recent(int limit) {
            return List.of();
        }

        @Override
        public List<DispatchAttemptHistoryRecord> findSince(OffsetDateTime since, int limit) {
            return List.of();
        }

        @Override
        public String mode() {
            return "TEST";
        }
    }
}
