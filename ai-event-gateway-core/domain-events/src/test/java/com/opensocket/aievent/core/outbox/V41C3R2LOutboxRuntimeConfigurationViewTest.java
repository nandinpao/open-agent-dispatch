package com.opensocket.aievent.core.outbox;

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

class V41C3R2LOutboxRuntimeConfigurationViewTest {
    @Test
    void runtimeSnapshotOverridesStartupAndCutoverFailsClosed() {
        var registry=new RuntimeConfigurationLocalSnapshotRegistry();
        var values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        var authority=new RuntimeConfigurationAuthorityRegistry();
        var startup=new OutboxProperties(); startup.setBatchSize(25); startup.setScanIntervalMs(2000);
        var view=new OutboxRuntimeConfigurationView(values,authority,startup);

        assertThat(view.batchSize()).isEqualTo(25);
        registry.atomicSwap(snapshot(payload()));
        assertThat(view.batchSize()).isEqualTo(200);
        assertThat(view.scanInterval().toMillis()).isEqualTo(500);
        assertThat(view.workerId()).isEqualTo("runtime-outbox");

        authority.activate(Set.of(OutboxRuntimeConfigurationView.BATCH_SIZE));
        registry.atomicSwap(snapshot("{}"));
        assertThatThrownBy(view::batchSize).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    @Test
    void maximumBackoffCannotBeBelowInitialBackoff() {
        var registry=new RuntimeConfigurationLocalSnapshotRegistry();
        var values=new RuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        var view=new OutboxRuntimeConfigurationView(values,new RuntimeConfigurationAuthorityRegistry(),new OutboxProperties());
        registry.atomicSwap(snapshot(payload().replace("\"PT1M\"","\"PT1S\"")));
        assertThatThrownBy(view::maxBackoff).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OutboxRuntimeConfigurationView.MAX_BACKOFF);
    }

    private static String payload(){return "{"+
            "\"core.outbox.batch-size\":200,"+
            "\"core.outbox.claim-lease\":\"PT45S\","+
            "\"core.outbox.initial-backoff\":\"PT30S\","+
            "\"core.outbox.max-attempts\":10,"+
            "\"core.outbox.max-backoff\":\"PT1M\","+
            "\"core.outbox.scan-interval-ms\":500,"+
            "\"core.outbox.worker-id\":\"runtime-outbox\"}";}
    private static RuntimeConfigurationSnapshotEnvelope snapshot(String payload){
        var now=OffsetDateTime.of(2026,9,25,2,0,0,0,ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","core",OutboxRuntimeConfigurationView.SET_KEY,"r-l",1,8,now,now.plusHours(1),"CORE",payload,"hash","sig");
    }
}
