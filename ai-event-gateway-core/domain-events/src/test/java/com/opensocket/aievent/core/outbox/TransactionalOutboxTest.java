package com.opensocket.aievent.core.outbox;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import org.junit.jupiter.api.Test;

class TransactionalOutboxTest {
 @Test void publishesDispatchesAndDeduplicatesByEventId(){
   InMemoryOutboxEventRepository repo=new InMemoryOutboxEventRepository(); ObjectMapper mapper=JsonMapper.builder().build();
   TransactionalOutboxPublisher publisher=new TransactionalOutboxPublisher(repo,mapper); AtomicInteger calls=new AtomicInteger();
   ModuleEventHandler<IncidentEscalatedEvent> handler=new ModuleEventHandler<>(){public String eventType(){return IncidentEscalatedEvent.TYPE;} public Class<IncidentEscalatedEvent> payloadType(){return IncidentEscalatedEvent.class;} public void handle(IncidentEscalatedEvent e){calls.incrementAndGet();}};
   OutboxProperties props=new OutboxProperties(); OutboxEventDispatcher dispatcher=new OutboxEventDispatcher(repo,mapper,props,List.of(handler));
   var event=new IncidentEscalatedEvent("evt-1","inc-1","fp","CRITICAL",3,"source-1","tenant-1","site-1",OffsetDateTime.now());
   publisher.publish(event); publisher.publish(event); assertEquals(1,repo.recent(10).size());
   var result=dispatcher.dispatchPending(); assertEquals(1,result.published()); assertEquals(1,calls.get()); assertEquals(OutboxEventStatus.PUBLISHED,repo.recent(1).get(0).getStatus());
 }
}
