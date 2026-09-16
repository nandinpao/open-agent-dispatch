package com.opensocket.aievent.core.iam.api.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class IamIdempotencyExecutorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void delegatesCommandExecutionToTransactionOwningPort() {
        AtomicInteger calls = new AtomicInteger();
        IamIdempotencyPort port = new IamIdempotencyPort() {
            @Override
            public Execution executeAtomically(
                    String scopeId,
                    String actorId,
                    String operation,
                    String key,
                    String requestHash,
                    Instant expiresAt,
                    Supplier<StoredResponse> command) {
                calls.incrementAndGet();
                return new Execution(State.COMPLETED, command.get());
            }
        };
        IamIdempotencyExecutor executor = new IamIdempotencyExecutor(port, mapper, clock);
        Result result = executor.execute(
                "tenant-a",
                "user-1",
                "identity.user.update",
                "idem-1",
                new Request("value"),
                200,
                Result.class,
                () -> new Result("done"));
        assertEquals("done", result.value());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsConflictingRequestReuse() {
        IamIdempotencyPort port = (scope, actor, operation, key, hash, expires, command) ->
                new IamIdempotencyPort.Execution(IamIdempotencyPort.State.CONFLICT, null);
        IamIdempotencyExecutor executor = new IamIdempotencyExecutor(port, mapper, clock);
        IamApiException error = assertThrows(
                IamApiException.class,
                () -> executor.execute(
                        "tenant-a",
                        "user-1",
                        "identity.user.update",
                        "idem-1",
                        new Request("different"),
                        200,
                        Result.class,
                        () -> new Result("ignored")));
        assertEquals("IAM_IDEMPOTENCY_KEY_CONFLICT", error.errorCode());
    }

    @Test
    void secretProducingCommandStoresOnlyNonSensitiveMarker() {
        AtomicReference<IamIdempotencyPort.StoredResponse> stored = new AtomicReference<>();
        IamIdempotencyPort port = (scope, actor, operation, key, hash, expires, command) -> {
            var response = command.get();
            stored.set(response);
            return new IamIdempotencyPort.Execution(IamIdempotencyPort.State.COMPLETED, response);
        };
        IamIdempotencyExecutor executor = new IamIdempotencyExecutor(port, mapper, clock);
        String secret = executor.executeNonReplayableSecret(
                "tenant-a", "user-1", "credential.issue", "idem-secret", new Request("x"), 201,
                () -> "clear-text-client-secret");
        assertEquals("clear-text-client-secret", secret);
        assertEquals("{\"secretReplayable\":false}", stored.get().responseBody());
    }

    @Test
    void secretProducingCommandRejectsReplayInsteadOfReplayingSecret() {
        IamIdempotencyPort port = (scope, actor, operation, key, hash, expires, command) ->
                new IamIdempotencyPort.Execution(
                        IamIdempotencyPort.State.REPLAY,
                        new IamIdempotencyPort.StoredResponse(201, "application/json", "{\"secretReplayable\":false}"));
        IamIdempotencyExecutor executor = new IamIdempotencyExecutor(port, mapper, clock);
        IamApiException error = assertThrows(
                IamApiException.class,
                () -> executor.executeNonReplayableSecret(
                        "tenant-a", "user-1", "credential.issue", "idem-secret", new Request("x"), 201,
                        () -> "must-not-run"));
        assertEquals("IAM_SECRET_RESPONSE_NOT_REPLAYABLE", error.errorCode());
    }

    record Request(String value) {}
    record Result(String value) {}
}
