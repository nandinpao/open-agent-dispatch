package com.opensocket.aievent.core.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.service.events.IntegrationEventEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="core.integration-events", name="sink", havingValue="HTTP")
public class HttpIntegrationEventSink implements IntegrationEventSink {
    private final IntegrationEventProperties properties; private final ObjectMapper mapper; private final HttpClient client;
    public HttpIntegrationEventSink(IntegrationEventProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;this.client=HttpClient.newBuilder().connectTimeout(properties.getRequestTimeout()).build();}
    @Override public void deliver(IntegrationEventEnvelope envelope) throws Exception {if(properties.getEndpointUrl().isBlank())throw new IllegalStateException("core.integration-events.endpoint-url is required for HTTP sink");HttpRequest.Builder b=HttpRequest.newBuilder().uri(URI.create(properties.getEndpointUrl())).timeout(properties.getRequestTimeout()).header("Content-Type","application/json").header("Idempotency-Key",envelope.eventId()).header("X-Event-Id",envelope.eventId()).header("X-Event-Type",envelope.eventType()).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(envelope)));if(!properties.getToken().isBlank())b.header(properties.getTokenHeader(),properties.getToken());HttpResponse<String> r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("Integration event sink returned "+r.statusCode()+": "+r.body());}
    @Override public String name(){return "HTTP";}
}
