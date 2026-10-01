package com.opensocket.aievent.core.integration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteVerifier;
import com.opensocket.aievent.service.events.IntegrationEventEnvelope;

import tools.jackson.databind.ObjectMapper;

@Service
public class IntegrationEventDeliveryService {
    private final IntegrationEventRepository repository;
    private final IntegrationEventProperties startupProperties;
    private final IntegrationEventsRuntimeConfigurationView runtimeConfiguration;
    private final Map<String,IntegrationEventSink> sinks;
    private final ObjectMapper mapper;

    public IntegrationEventDeliveryService(
            IntegrationEventRepository repository,
            IntegrationEventProperties startupProperties,
            IntegrationEventsRuntimeConfigurationView runtimeConfiguration,
            List<IntegrationEventSink> sinks,
            ObjectMapper mapper) {
        this.repository = repository;
        this.startupProperties = startupProperties;
        this.runtimeConfiguration = runtimeConfiguration;
        LinkedHashMap<String,IntegrationEventSink> mapped=new LinkedHashMap<>();
        for(IntegrationEventSink sink:sinks){
            String name=sink.name().trim().toUpperCase(Locale.ROOT);
            if(mapped.putIfAbsent(name,sink)!=null) throw new IllegalStateException("Duplicate IntegrationEventSink name="+name);
        }
        this.sinks=Map.copyOf(mapped);
        this.mapper = mapper;
    }

    public IntegrationEventDeliveryResult deliverPending() {
        // delivery-enabled remains TEST_RELEASE_ONLY and intentionally controls whether this
        // production delivery path is enabled at startup. Operational tuning below is runtime-backed.
        if (!startupProperties.isDeliveryEnabled()) return new IntegrationEventDeliveryResult(0, 0, 0, 0);

        int capped = runtimeConfiguration.batchSize();
        String workerId = runtimeConfiguration.workerId();
        Duration claimLease = runtimeConfiguration.claimLease();
        int maxAttempts = runtimeConfiguration.maxAttempts();
        Duration initialBackoff = runtimeConfiguration.initialBackoff();
        Duration maxBackoff = runtimeConfiguration.maxBackoff();
        IntegrationEventSink sink = requireSink(runtimeConfiguration.sink());
        int claimed = 0, delivered = 0, retry = 0, deadLetter = 0;

        for (int index = 0; index < capped; index++) {
            OffsetDateTime claimTime = OffsetDateTime.now(ZoneOffset.UTC);
            ClaimRequest claim = ClaimRequest.forLease(workerId, claimTime, claimLease, 1);
            List<IntegrationEventRecord> batch = repository.claimDispatchable(claim);
            if (batch.isEmpty()) break;

            IntegrationEventRecord record = batch.getFirst();
            claimed++;
            ClaimOwnership ownership = new ClaimOwnership(record.getClaimedBy(), record.getClaimUntil());
            Exception deliveryFailure = null;
            try { sink.deliver(mapper.readValue(record.getEnvelopeJson(), IntegrationEventEnvelope.class)); }
            catch (Exception exception) { deliveryFailure = exception; }
            if (deliveryFailure == null) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markDelivered(record.getIntegrationEventId(), ownership, OffsetDateTime.now(ZoneOffset.UTC)),
                        "mark integration event delivered");
                delivered++;
                continue;
            }

            int attempt = record.getAttemptCount() + 1;
            OffsetDateTime failedAt = OffsetDateTime.now(ZoneOffset.UTC);
            String error = root(deliveryFailure);
            if (attempt >= maxAttempts) {
                PersistenceWriteVerifier.requireApplied(
                        repository.markDeadLetter(record.getIntegrationEventId(), ownership, attempt, error, failedAt),
                        "mark integration event dead-letter");
                deadLetter++;
            } else {
                PersistenceWriteVerifier.requireApplied(
                        repository.markRetry(record.getIntegrationEventId(), ownership, attempt,
                                failedAt.plus(backoff(attempt,initialBackoff,maxBackoff)), error, failedAt),
                        "mark integration event retry");
                retry++;
            }
        }
        return new IntegrationEventDeliveryResult(claimed, delivered, retry, deadLetter);
    }

    private IntegrationEventSink requireSink(IntegrationEventsRuntimeConfigurationView.SinkType type){
        IntegrationEventSink sink=sinks.get(type.name());
        if(sink==null) throw new IllegalStateException("INTEGRATION_EVENT_SINK_NOT_AVAILABLE sink="+type.name());
        return sink;
    }
    private static Duration backoff(int attempt,Duration initialBackoff,Duration maxBackoff) {
        long factor = 1L << Math.min(Math.max(0, attempt - 1), 20);
        Duration value = initialBackoff.multipliedBy(factor);
        return value.compareTo(maxBackoff) > 0 ? maxBackoff : value;
    }
    private static String root(Throwable exception) {
        Throwable current = exception; while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }
}
