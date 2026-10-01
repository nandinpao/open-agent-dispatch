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
    private final OutboxRuntimeConfigurationView runtimeConfiguration;
    private final Map<String, List<ModuleEventHandler<?>>> handlers = new HashMap<>();

    public OutboxEventDispatcher(
            OutboxEventRepository repository,
            ObjectMapper mapper,
            OutboxRuntimeConfigurationView runtimeConfiguration,
            List<ModuleEventHandler<?>> handlers) {
        this.repository = repository;
        this.mapper = mapper;
        this.runtimeConfiguration = runtimeConfiguration;
        for (ModuleEventHandler<?> handler : handlers) {
            this.handlers.computeIfAbsent(handler.eventType(), ignored -> new ArrayList<>()).add(handler);
        }
    }

    public OutboxDispatchResult dispatchPending() {
        return dispatchPending(runtimeConfiguration.batchSize());
    }

    public OutboxDispatchResult dispatchPending(int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        String workerId = runtimeConfiguration.workerId();
        Duration claimLease = runtimeConfiguration.claimLease();
        int maxAttempts = runtimeConfiguration.maxAttempts();
        Duration initialBackoff = runtimeConfiguration.initialBackoff();
        Duration maxBackoff = runtimeConfiguration.maxBackoff();
        int claimed = 0;
        int published = 0;
        int retry = 0;
        int deadLetter = 0;

        // Capture one coherent runtime configuration view for this dispatch run. A newly
        // published snapshot takes effect on the next dispatch cycle instead of changing retry
        // semantics halfway through an in-flight batch.
        for (int index = 0; index < capped; index++) {
            OffsetDateTime claimTime = OffsetDateTime.now(ZoneOffset.UTC);
            ClaimRequest claim = ClaimRequest.forLease(workerId, claimTime, claimLease, 1);
            List<OutboxEventRecord> batch = repository.claimDispatchable(claim);
            if (batch.isEmpty()) break;

            OutboxEventRecord record = batch.getFirst();
            claimed++;
            ClaimOwnership ownership = new ClaimOwnership(record.getClaimedBy(), record.getClaimUntil());
            Exception dispatchFailure = null;
            try { dispatch(record); }
            catch (Exception exception) { dispatchFailure = exception; }
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
            if (attempt >= maxAttempts) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markDeadLetter(record.getOutboxId(), ownership, attempt, error, failedAt),
                        "mark outbox event dead-letter");
                deadLetter++;
            } else {
                PersistenceWriteVerifier.requireApplied(
                        repository.markRetry(record.getOutboxId(), ownership, attempt,
                                failedAt.plus(backoff(attempt, initialBackoff, maxBackoff)), error, failedAt),
                        "mark outbox event retry");
                retry++;
            }
        }
        return new OutboxDispatchResult(claimed, published, retry, deadLetter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dispatch(OutboxEventRecord record) throws Exception {
        List<ModuleEventHandler<?>> eventHandlers = handlers.get(record.getEventType());
        if (eventHandlers == null || eventHandlers.isEmpty())
            throw new IllegalStateException("No module event handler registered for " + record.getEventType());
        for (ModuleEventHandler handler : eventHandlers) {
            ModuleEvent event = (ModuleEvent) mapper.readValue(record.getPayloadJson(), handler.payloadType());
            handler.handle(event);
        }
    }

    private static Duration backoff(int attempt, Duration initialBackoff, Duration maxBackoff) {
        long factor = 1L << Math.min(Math.max(0, attempt - 1), 20);
        Duration value = initialBackoff.multipliedBy(factor);
        return value.compareTo(maxBackoff) > 0 ? maxBackoff : value;
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null ? current.getClass().getName() : message;
    }
}
