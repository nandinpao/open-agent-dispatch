package com.opensocket.aievent.core.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import org.junit.jupiter.api.Test;

class OutboxMultipleHandlersTest {

    @Test
    void dispatchesAllHandlersRegisteredForSameEventType() {
        InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
        ObjectMapper mapper = JsonMapper.builder().build();
        OutboxProperties properties = new OutboxProperties();
        AtomicInteger calls = new AtomicInteger();

        ModuleEventHandler<IncidentEscalatedEvent> first = handler(calls);
        ModuleEventHandler<IncidentEscalatedEvent> second = handler(calls);
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(repository, mapper, properties, List.of(first, second));
        TransactionalOutboxPublisher publisher = new TransactionalOutboxPublisher(repository, mapper);

        publisher.publish(new IncidentEscalatedEvent(
                "event-1", "incident-1", "fp", "CRITICAL", 3,
                "source-1", "tenant-1", "site-1", OffsetDateTime.now()));

        OutboxDispatchResult result = dispatcher.dispatchPending();

        assertThat(result.published()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(2);
    }

    private ModuleEventHandler<IncidentEscalatedEvent> handler(AtomicInteger calls) {
        return new ModuleEventHandler<>() {
            @Override public String eventType() { return IncidentEscalatedEvent.TYPE; }
            @Override public Class<IncidentEscalatedEvent> payloadType() { return IncidentEscalatedEvent.class; }
            @Override public void handle(IncidentEscalatedEvent event) { calls.incrementAndGet(); }
        };
    }
}
