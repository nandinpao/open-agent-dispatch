package com.opensocket.aievent.core.outbox;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.events.ModuleEvent;
import org.springframework.stereotype.Service;

@Service
public class TransactionalOutboxPublisher implements ModuleEventPublisher {
    private final OutboxEventRepository repository; private final ObjectMapper mapper;
    public TransactionalOutboxPublisher(OutboxEventRepository repository,ObjectMapper mapper){this.repository=repository;this.mapper=mapper;}
    @Override public OutboxEventRecord publish(ModuleEvent event){
        if(event==null)throw new IllegalArgumentException("module event is required");
        try {OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);OutboxEventRecord r=new OutboxEventRecord();r.setOutboxId("outbox-"+UUID.randomUUID());r.setEventId(event.eventId());r.setEventType(event.eventType());r.setAggregateType(event.aggregateType());r.setAggregateId(event.aggregateId());r.setPayloadJson(mapper.writeValueAsString(event));r.setStatus(OutboxEventStatus.PENDING);r.setCreatedAt(now);r.setUpdatedAt(now);return repository.save(r);} catch(JacksonException e){throw new IllegalStateException("Unable to serialize module event "+event.eventType(),e);}
    }
}
