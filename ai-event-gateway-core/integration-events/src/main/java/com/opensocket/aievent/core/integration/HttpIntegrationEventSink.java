package com.opensocket.aievent.core.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.service.events.IntegrationEventEnvelope;
import org.springframework.stereotype.Component;

@Component
public class HttpIntegrationEventSink implements IntegrationEventSink {
    private final IntegrationEventProperties startupProperties;
    private final IntegrationEventsRuntimeConfigurationView runtimeConfiguration;
    private final ObjectMapper mapper;
    private final AtomicReference<ClientHolder> client = new AtomicReference<>();

    public HttpIntegrationEventSink(IntegrationEventProperties startupProperties,
                                    IntegrationEventsRuntimeConfigurationView runtimeConfiguration,
                                    ObjectMapper mapper){
        this.startupProperties=startupProperties; this.runtimeConfiguration=runtimeConfiguration; this.mapper=mapper;
    }

    @Override public void deliver(IntegrationEventEnvelope envelope) throws Exception {
        String endpoint=runtimeConfiguration.endpointUrl();
        Duration timeout=runtimeConfiguration.requestTimeout();
        HttpRequest.Builder b=HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(timeout)
                .header("Content-Type","application/json").header("Idempotency-Key",envelope.eventId())
                .header("X-Event-Id",envelope.eventId()).header("X-Event-Type",envelope.eventType())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(envelope)));
        // Authentication remains SECRET_REFERENCE / deployment authority by design.
        if(!startupProperties.getToken().isBlank()) b.header(startupProperties.getTokenHeader(),startupProperties.getToken());
        HttpResponse<String> r=client(timeout).send(b.build(),HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()<200||r.statusCode()>=300) throw new IllegalStateException("Integration event sink returned "+r.statusCode()+": "+r.body());
    }
    @Override public String name(){return "HTTP";}

    private HttpClient client(Duration timeout){
        ClientHolder current=client.get(); if(current!=null&&current.timeout().equals(timeout))return current.client();
        ClientHolder next=new ClientHolder(timeout,HttpClient.newBuilder().connectTimeout(timeout).build()); client.set(next); return next.client();
    }
    private record ClientHolder(Duration timeout,HttpClient client){}
}
