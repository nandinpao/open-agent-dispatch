package com.opensocket.aievent.core.integration.issue.projection;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.core.integration.handoff.HandoffDomainModuleEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;

@Component
@ConditionalOnProperty(prefix="issue-tracking",name="enabled",havingValue="true",matchIfMissing=true)
public class HandoffIssueProjectionEventHandler implements ModuleEventHandler<HandoffDomainModuleEvent> {
    private final IssueProjectionStateService service;
    public HandoffIssueProjectionEventHandler(IssueProjectionStateService service) { this.service = service; }
    public String eventType() { return HandoffDomainModuleEvent.TYPE; }
    public Class<HandoffDomainModuleEvent> payloadType() { return HandoffDomainModuleEvent.class; }
    public void handle(HandoffDomainModuleEvent event) { service.onHandoffEvent(event); }
}
