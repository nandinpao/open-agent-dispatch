package com.opensocket.aievent.core.integration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteVerifier;
import com.opensocket.aievent.service.events.IntegrationEventEnvelope;

import tools.jackson.databind.ObjectMapper;

@Service
public class IntegrationEventDeliveryService {
    private final IntegrationEventRepository repository;
    private final IntegrationEventProperties properties;
    private final IntegrationEventSink sink;
    private final ObjectMapper mapper;

    public IntegrationEventDeliveryService(
            IntegrationEventRepository repository,
            IntegrationEventProperties properties,
            IntegrationEventSink sink,
            ObjectMapper mapper) {
        this.repository = repository;
        this.properties = properties;
        this.sink = sink;
        this.mapper = mapper;
    }

    public IntegrationEventDeliveryResult deliverPending() {
        if (!properties.isDeliveryEnabled()) {
            return new IntegrationEventDeliveryResult(0, 0, 0, 0);
        }
        int capped = Math.max(1, Math.min(properties.getBatchSize(), 1000));
        int claimed = 0;
        int delivered = 0;
        int retry = 0;
        int deadLetter = 0;

        // Claim immediately before each synchronous delivery so queued rows do not lose their
        // leases while earlier HTTP deliveries are still in flight.
        for (int index = 0; index < capped; index++) {
            OffsetDateTime claimTime = OffsetDateTime.now(ZoneOffset.UTC);
            ClaimRequest claim = ClaimRequest.forLease(
                    properties.getWorkerId(),
                    claimTime,
                    properties.getClaimLease(),
                    1);
            List<IntegrationEventRecord> batch = repository.claimDispatchable(claim);
            if (batch.isEmpty()) {
                break;
            }

            IntegrationEventRecord record = batch.getFirst();
            claimed++;
            ClaimOwnership ownership = new ClaimOwnership(record.getClaimedBy(), record.getClaimUntil());
            Exception deliveryFailure = null;
            try {
                sink.deliver(mapper.readValue(record.getEnvelopeJson(), IntegrationEventEnvelope.class));
            } catch (Exception exception) {
                deliveryFailure = exception;
            }
            if (deliveryFailure == null) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markDelivered(
                                record.getIntegrationEventId(),
                                ownership,
                                OffsetDateTime.now(ZoneOffset.UTC)),
                        "mark integration event delivered");
                delivered++;
                continue;
            }

            int attempt = record.getAttemptCount() + 1;
            OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);
            String error = root(deliveryFailure);
            if (attempt >= properties.getMaxAttempts()) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markDeadLetter(
                                record.getIntegrationEventId(),
                                ownership,
                                attempt,
                                error,
                                failedAt),
                        "mark integration event dead-letter");
                deadLetter++;
            } else {
                PersistenceWriteVerifier.requireApplied(
                        repository.markRetry(
                                record.getIntegrationEventId(),
                                ownership,
                                attempt,
                                failedAt.plus(backoff(attempt)),
                                error,
                                failedAt),
                        "mark integration event retry");
                retry++;
            }
        }
        return new IntegrationEventDeliveryResult(claimed, delivered, retry, deadLetter);
    }

    private Duration backoff(int attempt) {
        long factor = 1L << Math.min(Math.max(0, attempt - 1), 20);
        Duration value = properties.getInitialBackoff().multipliedBy(factor);
        return value.compareTo(properties.getMaxBackoff()) > 0 ? properties.getMaxBackoff() : value;
    }

    private String root(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }
}
