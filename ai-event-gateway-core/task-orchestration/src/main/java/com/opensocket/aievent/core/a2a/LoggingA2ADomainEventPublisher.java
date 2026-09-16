package com.opensocket.aievent.core.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Compatibility logger; production publisher is OutboxA2ADomainEventPublisher. */
public class LoggingA2ADomainEventPublisher implements A2ADomainEventPublisher {
    private static final Logger log=LoggerFactory.getLogger(LoggingA2ADomainEventPublisher.class);
    public void publish(A2ADomainEvent event){log.info("a2a_domain_event type={} tenantId={} aggregateId={} rootTaskId={} correlationId={} payload={}",event.eventType(),event.tenantId(),event.aggregateId(),event.rootTaskId(),event.correlationId(),event.payload());}
}
