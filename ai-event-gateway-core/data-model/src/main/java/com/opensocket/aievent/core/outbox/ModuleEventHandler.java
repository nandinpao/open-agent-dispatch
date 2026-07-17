package com.opensocket.aievent.core.outbox;
import com.opensocket.aievent.core.events.ModuleEvent;
public interface ModuleEventHandler<T extends ModuleEvent> { String eventType(); Class<T> payloadType(); void handle(T event); }
