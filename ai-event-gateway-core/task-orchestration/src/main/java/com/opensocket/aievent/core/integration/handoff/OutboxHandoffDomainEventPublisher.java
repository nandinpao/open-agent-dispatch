package com.opensocket.aievent.core.integration.handoff;

import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;

@Component
public class OutboxHandoffDomainEventPublisher implements HandoffDomainEventPublisher {
    private final ModuleEventPublisher outbox;
    public OutboxHandoffDomainEventPublisher(ModuleEventPublisher outbox){this.outbox=outbox;}
    @Override public void publish(HandoffDomainModuleEvent event){outbox.publish(event);}
}
