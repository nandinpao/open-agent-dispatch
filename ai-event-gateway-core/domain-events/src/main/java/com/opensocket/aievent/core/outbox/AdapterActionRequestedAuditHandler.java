package com.opensocket.aievent.core.outbox;

import com.opensocket.aievent.core.events.AdapterActionRequestedEvent;
import org.springframework.stereotype.Component;

/** Marks adapter-action request events as consumed when no external subscriber is configured. */
@Component
public class AdapterActionRequestedAuditHandler implements ModuleEventHandler<AdapterActionRequestedEvent> {

    @Override
    public String eventType() {
        return AdapterActionRequestedEvent.TYPE;
    }

    @Override
    public Class<AdapterActionRequestedEvent> payloadType() {
        return AdapterActionRequestedEvent.class;
    }

    @Override
    public void handle(AdapterActionRequestedEvent event) {
        // Intentionally no-op. The adapter action itself remains the execution source of truth.
    }
}
