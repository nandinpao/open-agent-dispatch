package com.opensocket.aievent.core.a2a;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.events.A2ADomainModuleEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;

@Component
@Primary
public class OutboxA2ADomainEventPublisher implements A2ADomainEventPublisher {
    private final ModuleEventPublisher outbox;
    public OutboxA2ADomainEventPublisher(ModuleEventPublisher outbox){this.outbox=outbox;}
    @Override public void publish(A2ADomainEvent event){
        outbox.publish(new A2ADomainModuleEvent(event.eventId(),event.eventType().name(),event.tenantId(),event.aggregateId(),event.rootTaskId(),event.correlationId(),event.causationId(),event.actorType(),event.actorId(),event.occurredAt(),event.payload()));
    }
}
