package com.opensocket.aievent.core.integration.issue.projection;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.core.events.A2ADomainModuleEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;

@Component
@ConditionalOnProperty(prefix="issue-tracking",name="enabled",havingValue="true",matchIfMissing=true)
public class A2AIssueProjectionEventHandler implements ModuleEventHandler<A2ADomainModuleEvent> {
    private final IssueProjectionStateService service;
    public A2AIssueProjectionEventHandler(IssueProjectionStateService service) { this.service = service; }
    public String eventType() { return A2ADomainModuleEvent.TYPE; }
    public Class<A2ADomainModuleEvent> payloadType() { return A2ADomainModuleEvent.class; }
    public void handle(A2ADomainModuleEvent event) { service.onA2AEvent(event); }
}
