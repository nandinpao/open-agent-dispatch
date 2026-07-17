package com.opensocket.aievent.core.outbox;
public enum OutboxEventStatus { PENDING, RETRY_WAITING, DISPATCHING, PUBLISHED, DEAD_LETTER }
