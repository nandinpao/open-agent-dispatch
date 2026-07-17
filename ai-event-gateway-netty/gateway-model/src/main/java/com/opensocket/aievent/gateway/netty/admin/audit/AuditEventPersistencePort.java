package com.opensocket.aievent.gateway.netty.admin.audit;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;

/** Persistence boundary for Admin audit events. */
public interface AuditEventPersistencePort {

    /** Persists one Admin event to an external audit sink. Implementations should be non-blocking or fast. */
    void persist(AdminEventPayload event);
}
