package com.opensocket.aievent.core.integration;

import com.opensocket.aievent.core.events.AdapterActionRequestedEvent;
import com.opensocket.aievent.core.events.DispatchDeadLetteredEvent;
import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.outbox.ModuleEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationEventProjectionConfiguration {
    @Bean ModuleEventHandler<IncidentEscalatedEvent> incidentEscalatedIntegrationProjector(IntegrationEventProjector projector){return handler("incident.escalated.v1",IncidentEscalatedEvent.class,projector);}
    @Bean ModuleEventHandler<TaskTerminalEvent> taskTerminalIntegrationProjector(IntegrationEventProjector projector){return handler("task.terminal.v1",TaskTerminalEvent.class,projector);}
    @Bean ModuleEventHandler<AdapterActionRequestedEvent> adapterActionRequestedIntegrationProjector(IntegrationEventProjector projector){return handler("adapter-action.requested.v1",AdapterActionRequestedEvent.class,projector);}
    @Bean ModuleEventHandler<DispatchDeadLetteredEvent> dispatchDeadLetteredIntegrationProjector(IntegrationEventProjector projector){return handler("dispatch.dead-lettered.v1",DispatchDeadLetteredEvent.class,projector);}
    private <T extends com.opensocket.aievent.core.events.ModuleEvent> ModuleEventHandler<T> handler(String type,Class<T> payloadType,IntegrationEventProjector projector){return new ModuleEventHandler<>(){public String eventType(){return type;}public Class<T> payloadType(){return payloadType;}public void handle(T event){projector.project(event);}};}
}
