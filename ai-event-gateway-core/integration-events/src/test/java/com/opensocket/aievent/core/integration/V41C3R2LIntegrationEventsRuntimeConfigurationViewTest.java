package com.opensocket.aievent.core.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2LIntegrationEventsRuntimeConfigurationViewTest {
    @Test
    void runtimeSnapshotControlsOperationalDeliverySettingsAndCutoverFailsClosed() {
        var registry=new RuntimeConfigurationLocalSnapshotRegistry();
        var values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        var authority=new RuntimeConfigurationAuthorityRegistry();
        var startup=new IntegrationEventProperties(); startup.setBatchSize(25); startup.setSource("startup-core");
        var view=new IntegrationEventsRuntimeConfigurationView(values,authority,startup);

        assertThat(view.batchSize()).isEqualTo(25);
        registry.atomicSwap(snapshot(payload("NONE","")));
        assertThat(view.batchSize()).isEqualTo(250);
        assertThat(view.source()).isEqualTo("runtime-core");
        assertThat(view.sink()).isEqualTo(IntegrationEventsRuntimeConfigurationView.SinkType.NONE);
        assertThat(view.exportedEventTypes()).contains("incident.escalated.v1","task.terminal.v1");

        authority.activate(Set.of(IntegrationEventsRuntimeConfigurationView.BATCH_SIZE));
        registry.atomicSwap(snapshot("{}"));
        assertThatThrownBy(view::batchSize).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    @Test
    void httpSinkRequiresHttpOrHttpsEndpoint() {
        var registry=new RuntimeConfigurationLocalSnapshotRegistry();
        var values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        var view=new IntegrationEventsRuntimeConfigurationView(values,new RuntimeConfigurationAuthorityRegistry(),new IntegrationEventProperties());
        registry.atomicSwap(snapshot(payload("HTTP","")));
        assertThatThrownBy(view::endpointUrl).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(IntegrationEventsRuntimeConfigurationView.ENDPOINT_URL);
        registry.atomicSwap(snapshot(payload("HTTP","https://events.example.internal/v1")));
        assertThat(view.endpointUrl()).isEqualTo("https://events.example.internal/v1");
    }

    private static String payload(String sink,String endpoint){return "{"+
            "\"core.integration-events.batch-size\":250,"+
            "\"core.integration-events.claim-lease\":\"PT30S\","+
            "\"core.integration-events.endpoint-url\":\""+endpoint+"\","+
            "\"core.integration-events.exported-event-types\":\"incident.escalated.v1,task.terminal.v1\","+
            "\"core.integration-events.initial-backoff\":\"PT2S\","+
            "\"core.integration-events.max-attempts\":8,"+
            "\"core.integration-events.max-backoff\":\"PT5M\","+
            "\"core.integration-events.request-timeout\":\"PT10S\","+
            "\"core.integration-events.scan-interval-ms\":750,"+
            "\"core.integration-events.sink\":\""+sink+"\","+
            "\"core.integration-events.source\":\"runtime-core\","+
            "\"core.integration-events.worker-id\":\"runtime-integration-worker\"}";}
    private static RuntimeConfigurationSnapshotEnvelope snapshot(String payload){
        var now=OffsetDateTime.of(2026,9,25,2,0,0,0,ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","core",IntegrationEventsRuntimeConfigurationView.SET_KEY,"r-l",1,8,now,now.plusHours(1),"CORE",payload,"hash","sig");
    }
}
