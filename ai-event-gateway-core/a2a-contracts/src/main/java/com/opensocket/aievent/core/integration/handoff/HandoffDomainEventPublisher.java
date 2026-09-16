package com.opensocket.aievent.core.integration.handoff;

public interface HandoffDomainEventPublisher {
    void publish(HandoffDomainModuleEvent event);

    static HandoffDomainEventPublisher noop() {
        return event -> { };
    }
}
