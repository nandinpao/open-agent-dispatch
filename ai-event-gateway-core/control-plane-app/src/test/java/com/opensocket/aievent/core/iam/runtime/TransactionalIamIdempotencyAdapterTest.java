package com.opensocket.aievent.core.iam.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.runtime.idempotency.TransactionalIamIdempotencyAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalIamIdempotencyAdapterTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void commandAndStoredResponseShareOneTransactionAndReplayDoesNotExecuteCommand() {
        IamApiRuntimeDao dao = mock(IamApiRuntimeDao.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((Function<TransactionStatus, ?>) invocation.getArgument(0)).apply(mock(TransactionStatus.class)));

        AtomicReference<Map<String, Object>> state = new AtomicReference<>();
        when(dao.insertInstanceIdempotency(any())).thenAnswer(invocation -> {
            Map<String, Object> input = new HashMap<>((Map) invocation.getArgument(0));
            input.put("status", "IN_PROGRESS");
            state.set(input);
            return 1;
        });
        when(dao.lockInstanceIdempotency(any(), any(), any(), any()))
                .thenAnswer(invocation -> state.get());
        when(dao.completeInstanceIdempotency(any())).thenAnswer(invocation -> {
            Map<String, Object> completed = new HashMap<>(state.get());
            completed.putAll((Map) invocation.getArgument(0));
            completed.put("status", "COMPLETED");
            state.set(completed);
            return 1;
        });

        TransactionalIamIdempotencyAdapter adapter = new TransactionalIamIdempotencyAdapter(
                dao, transactions, Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC));
        var response = new IamIdempotencyPort.StoredResponse(201, "application/json", "{\"ok\":true}");

        var first = adapter.executeAtomically(
                "INSTANCE", "root", "bootstrap.activate", "key-1", "hash-1",
                Instant.parse("2026-07-24T03:00:00Z"), () -> response);
        assertThat(first.state()).isEqualTo(IamIdempotencyPort.State.COMPLETED);
        assertThat(state.get()).doesNotContainKey("tenantId");
        assertThat(state.get()).containsEntry("scopeId", "INSTANCE");

        var replay = adapter.executeAtomically(
                "INSTANCE", "root", "bootstrap.activate", "key-1", "hash-1",
                Instant.parse("2026-07-24T03:00:00Z"),
                () -> { throw new AssertionError("replayed command must not run"); });
        assertThat(replay.state()).isEqualTo(IamIdempotencyPort.State.REPLAY);
        assertThat(replay.response().responseBody()).isEqualTo("{\"ok\":true}");
        verify(transactions, times(2)).execute(any());
    }
}
