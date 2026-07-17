package com.opensocket.aievent.core.outbox;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.events.ModuleEvent;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteVerifier;

import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxEventDispatcher {
    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;
    private final OutboxProperties properties;
    private final Map<String, List<ModuleEventHandler<?>>> handlers = new HashMap<>();

    public OutboxEventDispatcher(
            OutboxEventRepository repository,
            ObjectMapper mapper,
            OutboxProperties properties,
            List<ModuleEventHandler<?>> handlers) {
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
        for (ModuleEventHandler<?> handler : handlers) {
            this.handlers.computeIfAbsent(handler.eventType(), ignored -> new ArrayList<>()).add(handler);
        }
    }

    public OutboxDispatchResult dispatchPending() {
        return dispatchPending(properties.getBatchSize());
    }

    public OutboxDispatchResult dispatchPending(int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        int claimed = 0;
        int published = 0;
        int retry = 0;
        int deadLetter = 0;

        // Claim immediately before processing each row. A synchronous batch must not allow later
        // rows to sit behind earlier handlers until their leases expire.
        for (int index = 0; index < capped; index++) {
            OffsetDateTime claimTime = OffsetDateTime.now(ZoneOffset.UTC);
            ClaimRequest claim = ClaimRequest.forLease(
                    properties.getWorkerId(),
                    claimTime,
                    properties.getClaimLease(),
                    1);
            List<OutboxEventRecord> batch = repository.claimDispatchable(claim);
            if (batch.isEmpty()) {
                break;
            }

            OutboxEventRecord record = batch.getFirst();
            claimed++;
            ClaimOwnership ownership = new ClaimOwnership(record.getClaimedBy(), record.getClaimUntil());
            Exception dispatchFailure = null;
            try {
                dispatch(record);
            } catch (Exception exception) {
                dispatchFailure = exception;
            }
            if (dispatchFailure == null) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markPublished(record.getOutboxId(), ownership, OffsetDateTime.now(ZoneOffset.UTC)),
                        "mark outbox event published");
                published++;
                continue;
            }

            int attempt = record.getAttemptCount() + 1;
            OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);
            String error = rootMessage(dispatchFailure);
            if (attempt >= properties.getMaxAttempts()) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markDeadLetter(record.getOutboxId(), ownership, attempt, error, failedAt),
                        "mark outbox event dead-letter");
                deadLetter++;
            } else {
                PersistenceWriteVerifier.requireApplied(
                        repository.markRetry(
                                record.getOutboxId(),
                                ownership,
                                attempt,
                                failedAt.plus(backoff(attempt)),
                                error,
                                failedAt),
                        "mark outbox event retry");
                retry++;
            }
        }
        return new OutboxDispatchResult(claimed, published, retry, deadLetter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dispatch(OutboxEventRecord record) throws Exception {
        List<ModuleEventHandler<?>> eventHandlers = handlers.get(record.getEventType());
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            throw new IllegalStateException("No module event handler registered for " + record.getEventType());
        }
        for (ModuleEventHandler handler : eventHandlers) {
            ModuleEvent event = (ModuleEvent) mapper.readValue(record.getPayloadJson(), handler.payloadType());
            handler.handle(event);
        }
    }

    private Duration backoff(int attempt) {
        long factor = 1L << Math.min(Math.max(0, attempt - 1), 20);
        Duration value = properties.getInitialBackoff().multipliedBy(factor);
        return value.compareTo(properties.getMaxBackoff()) > 0 ? properties.getMaxBackoff() : value;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getName() : message;
    }
}
