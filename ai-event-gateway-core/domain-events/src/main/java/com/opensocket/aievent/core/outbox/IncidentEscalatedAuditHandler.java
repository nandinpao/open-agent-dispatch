package com.opensocket.aievent.core.outbox;

import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import org.springframework.stereotype.Component;

/**
 * Terminal audit handler for incident escalation events.
 *
 * <p>The durable outbox record is currently the audit artifact. Registering an
 * explicit handler prevents informational events from being retried and moved
 * to dead letter solely because no downstream integration has been configured.
 */
@Component
public class IncidentEscalatedAuditHandler implements ModuleEventHandler<IncidentEscalatedEvent> {

    @Override
    public String eventType() {
        return IncidentEscalatedEvent.TYPE;
    }

    @Override
    public Class<IncidentEscalatedEvent> payloadType() {
        return IncidentEscalatedEvent.class;
    }

    @Override
    public void handle(IncidentEscalatedEvent event) {
        // Intentionally no-op. Successful dispatch marks the durable event PUBLISHED.
    }
}
