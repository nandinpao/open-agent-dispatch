package com.opensocket.aievent.core.outbox;
import com.opensocket.aievent.core.events.ModuleEvent;
public interface ModuleEventPublisher {
    OutboxEventRecord publish(ModuleEvent event);
    static ModuleEventPublisher noop(){ return event -> {
        OutboxEventRecord r=new OutboxEventRecord();
        if(event==null)return r;
        var e=event.envelope();
        r.setEventId(e.eventId());r.setEventType(e.eventType());r.setAggregateType(e.aggregateType());r.setAggregateId(e.aggregateId());
        r.setPayloadVersion(e.payloadVersion());r.setTenantId(e.tenantId());r.setRootTaskId(e.rootTaskId());r.setTaskId(e.taskId());
        r.setCorrelationId(e.correlationId());r.setCausationId(e.causationId());r.setTraceId(e.traceId());r.setSpanId(e.spanId());
        r.setActorType(e.actorType());r.setActorId(e.actorId());r.setLineageStatus(e.lineageStatus());
        return r;
    }; }
}
