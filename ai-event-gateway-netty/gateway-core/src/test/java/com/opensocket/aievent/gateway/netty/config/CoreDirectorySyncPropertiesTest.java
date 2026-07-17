package com.opensocket.aievent.gateway.netty.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreDirectorySyncPropertiesTest {

    @Test
    void defaultConstructorProvidesSafeDisabledDefaults() {
        var properties = new CoreDirectorySyncProperties();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:18080");
        assertThat(properties.gatewayRegisterUrl()).isEqualTo("http://localhost:18080/internal/gateway-nodes/register");
        assertThat(properties.gatewayHeartbeatUrl("gateway-node-001"))
                .isEqualTo("http://localhost:18080/internal/gateway-nodes/gateway-node-001/heartbeat");
        assertThat(properties.agentConnectedUrl("gateway-node-001", "agent-001"))
                .isEqualTo("http://localhost:18080/internal/gateway-nodes/gateway-node-001/agents/agent-001/connected");
        assertThat(properties.authHeaderName()).isEqualTo("X-Cluster-Token");
        assertThat(properties.timeoutMs()).isEqualTo(3000);
        assertThat(properties.gatewayLeaseTtlSeconds()).isEqualTo(45);
        assertThat(properties.agentLeaseTtlSeconds()).isEqualTo(45);
    }

    @Test
    void settersNormalizeBaseUrlAndLimits() {
        var properties = new CoreDirectorySyncProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(" http://core:18080/ ");
        properties.setTimeoutMs(0);
        properties.setAuthToken(" token ");
        properties.setAuthHeaderName(" X-Internal-Token ");
        properties.setDefaultAgentMaxConcurrentTasks(0);
        properties.setDefaultAgentHealthScore(150);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo("http://core:18080");
        assertThat(properties.timeoutMs()).isEqualTo(3000);
        assertThat(properties.authToken()).isEqualTo("token");
        assertThat(properties.hasAuthToken()).isTrue();
        assertThat(properties.authHeaderName()).isEqualTo("X-Internal-Token");
        assertThat(properties.defaultAgentMaxConcurrentTasks()).isEqualTo(1);
        assertThat(properties.defaultAgentHealthScore()).isEqualTo(100);
    }
}
