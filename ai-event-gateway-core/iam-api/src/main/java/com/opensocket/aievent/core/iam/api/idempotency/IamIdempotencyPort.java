package com.opensocket.aievent.core.iam.api.idempotency;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Durable, transaction-owning idempotency adapter contract.
 *
 * <p>The implementation MUST execute reservation lookup/insert, the supplied command,
 * and persisted HTTP response in the same local database transaction. A failure MUST
 * roll back both the business command and the reservation. Implementations that only
 * reserve before the command and complete afterwards do not satisfy this contract.</p>
 */
public interface IamIdempotencyPort {
    Execution executeAtomically(
            String scopeId,
            String actorId,
            String operation,
            String key,
            String requestHash,
            Instant expiresAt,
            Supplier<StoredResponse> command);

    record Execution(State state, StoredResponse response) {
        public Execution {
            if (state == null) {
                throw new IllegalArgumentException("state is required");
            }
            if ((state == State.COMPLETED || state == State.REPLAY) && response == null) {
                throw new IllegalArgumentException("stored response is required for completed/replay execution");
            }
        }
    }

    record StoredResponse(int httpStatus, String contentType, String responseBody) {
        public StoredResponse {
            contentType = contentType == null || contentType.isBlank() ? "application/json" : contentType;
            responseBody = responseBody == null ? "null" : responseBody;
        }
    }

    enum State {
        COMPLETED,
        REPLAY,
        CONFLICT
    }
}
