package com.opensocket.aievent.core.outbox;
import com.opensocket.aievent.core.events.ModuleEvent;
public interface ModuleEventPublisher {
    OutboxEventRecord publish(ModuleEvent event);
    static ModuleEventPublisher noop(){ return event -> { OutboxEventRecord r=new OutboxEventRecord(); r.setEventId(event==null?null:event.eventId()); r.setEventType(event==null?null:event.eventType()); r.setAggregateType(event==null?null:event.aggregateType()); r.setAggregateId(event==null?null:event.aggregateId()); return r; }; }
}
