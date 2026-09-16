package com.opensocket.aievent.core.a2a;

import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;
import com.opensocket.aievent.core.events.A2ADispatchProgressedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;

@Component
public class A2ADispatchProgressedEventHandler implements ModuleEventHandler<A2ADispatchProgressedEvent> {
    private final A2AGovernanceUseCase governance;
    public A2ADispatchProgressedEventHandler(A2AGovernanceUseCase governance) { this.governance = governance; }
    @Override public String eventType() { return A2ADispatchProgressedEvent.TYPE; }
    @Override public Class<A2ADispatchProgressedEvent> payloadType() { return A2ADispatchProgressedEvent.class; }
    @Override public void handle(A2ADispatchProgressedEvent event) {
        if (event == null) return;
        A2ABlockerCode blocker = A2ABlockerCode.NONE;
        if (event.blockerCode() != null && !event.blockerCode().isBlank()) {
            try { blocker = A2ABlockerCode.valueOf(event.blockerCode()); } catch (IllegalArgumentException ignored) { blocker = A2ABlockerCode.RECONCILIATION_REQUIRED; }
        }
        governance.recordDispatchProgress(new A2ADispatchProgressCommand(event.tenantId(), event.taskId(),
                event.dispatchRequestId(), event.stage(), blocker, event.reason(), event.evidenceReference(),
                "DISPATCH_AUTHORITY", event.occurredAt()));
    }
}
