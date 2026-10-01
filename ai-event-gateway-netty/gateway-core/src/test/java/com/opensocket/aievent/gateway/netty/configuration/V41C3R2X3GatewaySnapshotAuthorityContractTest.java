package com.opensocket.aievent.gateway.netty.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2X3GatewaySnapshotAuthorityContractTest {
    @Test
    void requiredGatewayKeyCannotUseStartupFallbackAfterAuthorityContractMarksItRequired(){
        GatewayRuntimeConfigurationLocalRegistry registry=new GatewayRuntimeConfigurationLocalRegistry();
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        registry.atomicSwap(new GatewayRuntimeConfigurationSnapshot("LOCAL","gateway-set","RUNTIME/GATEWAY/SYSTEM","r1",1,1,now,now.plusHours(1),"GATEWAY","DUAL_READ",Set.of("gateway.required"),"{}","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sig"));
        GatewayRuntimeConfigurationSnapshotValues values=new GatewayRuntimeConfigurationSnapshotValues(registry,JsonMapper.builder().build());
        assertThatThrownBy(() -> values.longValue("RUNTIME/GATEWAY/SYSTEM","gateway.required"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }
}
