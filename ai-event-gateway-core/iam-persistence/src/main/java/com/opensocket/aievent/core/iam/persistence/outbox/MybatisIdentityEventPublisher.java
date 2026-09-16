package com.opensocket.aievent.core.iam.persistence.outbox;

import com.opensocket.aievent.core.iam.identity.application.port.out.IdentityEventPublisher;
import com.opensocket.aievent.core.iam.identity.event.IdentityDomainEvent;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class MybatisIdentityEventPublisher implements IdentityEventPublisher {
    private final IamTransactionalOutboxWriter outbox;
    public MybatisIdentityEventPublisher(IamTransactionalOutboxWriter outbox) { this.outbox = outbox; }
    @Override public void publish(IdentityDomainEvent event) {
        outbox.append(event.eventId(), event.eventType(), "IAM_IDENTITY", event.subjectId(), payload(event), event.occurredAt(),
                null, event.correlationId(), event.actorId());
    }
    private static String payload(IdentityDomainEvent e) {
        return "{\"eventId\":\""+escape(e.eventId())+"\",\"eventType\":\""+escape(e.eventType())+"\",\"subjectId\":\""+escape(e.subjectId())+"\",\"actorId\":\""+escape(e.actorId())+"\",\"correlationId\":\""+escape(e.correlationId())+"\",\"occurredAt\":\""+e.occurredAt()+"\"}";
    }
    private static String escape(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"");}
}
