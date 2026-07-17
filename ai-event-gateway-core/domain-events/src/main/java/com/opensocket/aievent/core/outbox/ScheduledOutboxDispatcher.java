package com.opensocket.aievent.core.outbox;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix="core.outbox",name="dispatcher-enabled",havingValue="true",matchIfMissing=true)
public class ScheduledOutboxDispatcher { private final OutboxEventDispatcher dispatcher; public ScheduledOutboxDispatcher(OutboxEventDispatcher dispatcher){this.dispatcher=dispatcher;} @Scheduled(fixedDelayString="${core.outbox.scan-interval-ms:1000}") public void dispatch(){dispatcher.dispatchPending();} }
