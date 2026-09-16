package com.opensocket.aievent.core.iam.api.idempotency;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Serializes API responses while delegating transaction ownership to {@link IamIdempotencyPort}. */
public final class IamIdempotencyExecutor {
    private final IamIdempotencyPort port;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IamIdempotencyExecutor(IamIdempotencyPort port, ObjectMapper mapper, Clock clock) {
        this.port = Objects.requireNonNull(port, "port");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public <T> T execute(
            String scope,
            String actor,
            String operation,
            String key,
            Object request,
            int successStatus,
            Class<T> type,
            Supplier<T> action) {
        return executeWithState(scope, actor, operation, key, request, successStatus, type, action).value();
    }

    /** Executes a durable idempotent mutation and exposes whether the response was replayed. */
    public <T> ExecutionResult<T> executeWithState(
            String scope,
            String actor,
            String operation,
            String key,
            Object request,
            int successStatus,
            Class<T> type,
            Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw IamApiException.badRequest("IAM_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required");
        }
        String requestHash = hash(request);
        IamIdempotencyPort.Execution execution = port.executeAtomically(
                scope,
                actor,
                operation,
                key.trim(),
                requestHash,
                clock.instant().plus(Duration.ofHours(24)),
                () -> {
                    T result = action.get();
                    return new IamIdempotencyPort.StoredResponse(
                            successStatus,
                            "application/json",
                            json(result));
                });
        if (execution.state() == IamIdempotencyPort.State.CONFLICT) {
            throw IamApiException.conflict(
                    "IAM_IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency key was reused with a different request");
        }
        try {
            T value = mapper.readValue(execution.response().responseBody(), type);
            return new ExecutionResult<>(value, execution.state() == IamIdempotencyPort.State.REPLAY);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to restore idempotent response", ex);
        }
    }

    /**
     * Executes a secret-producing mutation atomically without persisting or replaying the secret.
     * The durable idempotency record stores only a non-sensitive completion marker. A replay is
     * rejected so callers must rotate/re-issue rather than recover clear-text secret material.
     */
    public <T> T executeNonReplayableSecret(
            String scope,
            String actor,
            String operation,
            String key,
            Object request,
            int successStatus,
            Supplier<T> action) {
        if (key == null || key.isBlank()) {
            throw IamApiException.badRequest("IAM_IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required");
        }
        String requestHash = hash(request);
        AtomicReference<T> initialResult = new AtomicReference<>();
        IamIdempotencyPort.Execution execution = port.executeAtomically(
                scope, actor, operation, key.trim(), requestHash,
                clock.instant().plus(Duration.ofHours(24)),
                () -> {
                    T result = action.get();
                    initialResult.set(result);
                    return new IamIdempotencyPort.StoredResponse(
                            successStatus,
                            "application/json",
                            "{\"secretReplayable\":false}");
                });
        if (execution.state() == IamIdempotencyPort.State.CONFLICT) {
            throw IamApiException.conflict(
                    "IAM_IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency key was reused with a different request");
        }
        if (execution.state() == IamIdempotencyPort.State.REPLAY) {
            throw IamApiException.conflict(
                    "IAM_SECRET_RESPONSE_NOT_REPLAYABLE",
                    "This secret was already issued and cannot be displayed again. Rotate the credential to create a new secret.");
        }
        T result = initialResult.get();
        if (result == null) {
            throw new IllegalStateException("Secret-producing idempotent command completed without an in-memory result");
        }
        return result;
    }

    public record ExecutionResult<T>(T value, boolean replay) {}

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash idempotent request", ex);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to serialize idempotent response", ex);
        }
    }
}
