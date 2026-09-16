package com.opensocket.aievent.core.iam.persistence.outbox;

import com.opensocket.aievent.core.iam.authentication.application.port.out.AuthenticationEventPublisher;
import com.opensocket.aievent.core.iam.authentication.event.AuthenticationDomainEvent;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.Map;
import java.util.TreeMap;

@DatabaseRepositoryAdapter
public class MybatisAuthenticationEventPublisher implements AuthenticationEventPublisher {
    private final IamTransactionalOutboxWriter outbox;
    public MybatisAuthenticationEventPublisher(IamTransactionalOutboxWriter outbox){this.outbox=outbox;}
    @Override public void publish(AuthenticationDomainEvent e){
        outbox.append(e.eventId(),e.eventType(),"AUTHENTICATION",e.subjectId(),json(e),e.occurredAt(),
                e.tenantId(), e.correlationId(), e.actorId());
    }
    private String json(AuthenticationDomainEvent e){
        StringBuilder b=new StringBuilder("{");field(b,"eventType",e.eventType());field(b,"subjectType",e.subjectType());field(b,"subjectId",e.subjectId());field(b,"tenantId",e.tenantId());field(b,"actorId",e.actorId());field(b,"correlationId",e.correlationId());field(b,"reasonCode",e.reasonCode());b.append("\"metadata\":{");boolean first=true;for(Map.Entry<String,String>x:new TreeMap<>(e.metadata()).entrySet()){if(!first)b.append(',');first=false;b.append('"').append(escape(x.getKey())).append("\":\"").append(escape(x.getValue())).append('"');}b.append("}}");return b.toString();
    }
    private void field(StringBuilder b,String k,String v){b.append('"').append(k).append("\":\"").append(escape(v)).append("\",");}
    private String escape(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
