package com.opensocket.aievent.core.outbox;

import com.opensocket.aievent.core.events.DispatchDeadLetteredEvent;
import org.springframework.stereotype.Component;

/** Marks dispatch dead-letter notifications as consumed until an alert integration is attached. */
@Component
public class DispatchDeadLetteredAuditHandler implements ModuleEventHandler<DispatchDeadLetteredEvent> {

    @Override
    public String eventType() {
        return DispatchDeadLetteredEvent.TYPE;
    }

    @Override
    public Class<DispatchDeadLetteredEvent> payloadType() {
        return DispatchDeadLetteredEvent.class;
    }

    @Override
    public void handle(DispatchDeadLetteredEvent event) {
        // Intentionally no-op. Operational alerting can replace this handler later.
    }
}
