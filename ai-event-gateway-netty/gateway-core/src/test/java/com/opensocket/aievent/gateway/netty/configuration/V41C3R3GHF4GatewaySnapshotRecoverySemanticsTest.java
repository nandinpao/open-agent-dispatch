package com.opensocket.aievent.gateway.netty.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class V41C3R3GHF4GatewaySnapshotRecoverySemanticsTest {
    @Test void freshnessTransitionsFromActiveToStaleLkgToExpired(){
        OffsetDateTime now=OffsetDateTime.of(2026,9,29,6,0,0,0,ZoneOffset.UTC);
        var active=snapshot(now,now.plusSeconds(30),"DUAL_READ",Set.of(),"{}");
        var stale=snapshot(now.minusMinutes(2),now.minusSeconds(30),"RUNTIME_ONLY",Set.of("x"),"{\"x\":1}");
        var expired=snapshot(now.minusMinutes(10),now.minusMinutes(6),"RUNTIME_ONLY",Set.of("x"),"{\"x\":1}");
        assertThat(GatewayRuntimeConfigurationSnapshotFreshness.state(active,300000,now)).isEqualTo(GatewayRuntimeConfigurationSnapshotState.ACTIVE);
        assertThat(GatewayRuntimeConfigurationSnapshotFreshness.state(stale,300000,now)).isEqualTo(GatewayRuntimeConfigurationSnapshotState.STALE_LKG);
        assertThat(GatewayRuntimeConfigurationSnapshotFreshness.state(expired,300000,now)).isEqualTo(GatewayRuntimeConfigurationSnapshotState.EXPIRED);
    }

    @Test void runtimeOnlySnapshotBeyondMaxStaleCannotReactivateStartupValue(){
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        var registry=new GatewayRuntimeConfigurationLocalRegistry();
        registry.atomicSwap(snapshot(now.minusHours(2),now.minusHours(1),"RUNTIME_ONLY",Set.of("gateway.enabled"),"{\"gateway.enabled\":false}"));
        var values=new GatewayRuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build(),true);
        assertThatThrownBy(()->values.booleanValueOrStartup("RUNTIME/GATEWAY/SYSTEM","gateway.enabled",true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RUNTIME_CONFIGURATION_SNAPSHOT_EXPIRED");
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(OffsetDateTime issued,OffsetDateTime expires,String mode,Set<String> required,String payload){
        return new GatewayRuntimeConfigurationSnapshot("LOCAL","gateway-hf4","RUNTIME/GATEWAY/SYSTEM","hf4-r1",1,14,issued,expires,"GATEWAY",mode,required,payload,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","signature");
    }
}
