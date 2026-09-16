package com.opensocket.aievent.core.outbox;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.events.ModuleEvent;
import com.opensocket.aievent.core.events.ModuleEventEnvelope;
import org.springframework.stereotype.Service;

@Service
public class TransactionalOutboxPublisher implements ModuleEventPublisher {
    private final OutboxEventRepository repository; private final ObjectMapper mapper;
    public TransactionalOutboxPublisher(OutboxEventRepository repository,ObjectMapper mapper){this.repository=repository;this.mapper=mapper;}
    @Override public OutboxEventRecord publish(ModuleEvent event){
        if(event==null)throw new IllegalArgumentException("module event is required");
        try {
            OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
            ModuleEventEnvelope envelope=event.envelope();
            OutboxEventRecord r=new OutboxEventRecord();
            r.setOutboxId("outbox-"+UUID.randomUUID());
            r.setEventId(envelope.eventId());r.setEventType(envelope.eventType());r.setAggregateType(envelope.aggregateType());r.setAggregateId(envelope.aggregateId());
            r.setPayloadJson(mapper.writeValueAsString(event));r.setPayloadVersion(envelope.payloadVersion());r.setTenantId(envelope.tenantId());r.setRootTaskId(envelope.rootTaskId());r.setTaskId(envelope.taskId());
            r.setCorrelationId(envelope.correlationId());r.setCausationId(envelope.causationId());r.setTraceId(envelope.traceId());r.setSpanId(envelope.spanId());
            r.setActorType(envelope.actorType());r.setActorId(envelope.actorId());r.setLineageStatus(envelope.lineageStatus());
            r.setStatus(OutboxEventStatus.PENDING);r.setCreatedAt(now);r.setUpdatedAt(now);
            return repository.save(r);
        } catch(JacksonException e){throw new IllegalStateException("Unable to serialize module event "+event.eventType(),e);}
    }
}
