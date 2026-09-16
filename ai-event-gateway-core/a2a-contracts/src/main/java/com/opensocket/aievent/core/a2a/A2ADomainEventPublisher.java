package com.opensocket.aievent.core.a2a;

public interface A2ADomainEventPublisher {
    void publish(A2ADomainEvent event);
}
