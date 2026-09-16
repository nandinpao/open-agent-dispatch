package com.opensocket.aievent.core.iam.persistence.outbox;

import com.opensocket.aievent.core.iam.rbac.application.port.out.RbacEventPublisher;
import com.opensocket.aievent.core.iam.rbac.event.RbacSecurityEvent;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class MybatisRbacEventPublisher implements RbacEventPublisher {
    private final IamTransactionalOutboxWriter outbox;

    public MybatisRbacEventPublisher(IamTransactionalOutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(RbacSecurityEvent event) {
        String payload = "{"
                + "\"eventType\":\"" + safe(event.eventType()) + "\","
                + "\"tenantId\":\"" + safe(event.tenantId()) + "\","
                + "\"actorId\":\"" + safe(event.actorId()) + "\","
                + "\"principalId\":\"" + safe(event.principalId()) + "\","
                + "\"roleId\":\"" + safe(event.roleId()) + "\","
                + "\"permission\":\"" + safe(event.permission()) + "\","
                + "\"reasonCode\":\"" + safe(event.reasonCode()) + "\""
                + "}";
        String aggregateId = event.roleId().isBlank() ? event.principalId() : event.roleId();
        outbox.append(event.eventId(), event.eventType(), "RBAC", aggregateId, payload, event.occurredAt());
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
