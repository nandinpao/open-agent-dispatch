package com.opensocket.aievent.gateway.netty.admin.audit;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;

/** No-op audit persistence adapter used until FILE/JDBC/queue sinks are introduced. */
public class NoopAuditEventPersistencePort implements AuditEventPersistencePort {

    @Override
    public void persist(AdminEventPayload event) {
        // Intentionally empty. The AdminEventStore still keeps recent events in memory.
    }
}
