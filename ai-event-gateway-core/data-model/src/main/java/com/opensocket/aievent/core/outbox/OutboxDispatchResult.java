package com.opensocket.aievent.core.outbox;
public record OutboxDispatchResult(int scanned,int published,int retried,int deadLettered) { public static OutboxDispatchResult empty(){return new OutboxDispatchResult(0,0,0,0);} }
