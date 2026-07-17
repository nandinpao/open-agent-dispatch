package com.opensocket.aievent.gateway.netty.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreForwardPropertiesTest {

    @Test
    void defaultConstructorProvidesSafeDefaultsForSpringConfigurationBinding() {
        var properties = new CoreForwardProperties();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.endpointUrl()).isEqualTo("http://localhost:18080/internal/core/inbound-events");
        assertThat(properties.timeoutMs()).isEqualTo(3000);
        assertThat(properties.historyLimit()).isEqualTo(500);
        assertThat(properties.shouldRecord(InboundEventCategory.BUSINESS_EVENT)).isTrue();
        assertThat(properties.shouldRecord(InboundEventCategory.HEARTBEAT_SIGNAL)).isFalse();
        assertThat(properties.shouldForward(InboundEventCategory.BUSINESS_EVENT)).isTrue();
        assertThat(properties.shouldForward(InboundEventCategory.TRANSPORT_SIGNAL)).isFalse();
    }

    @Test
    void settersNormalizeEndpointAndLimits() {
        var properties = new CoreForwardProperties();
        properties.setBaseUrl(" http://core:8080/ ");
        properties.setInboundPath("internal/inbound");
        properties.setTimeoutMs(0);
        properties.setHistoryLimit(20000);
        properties.setAuthToken(" token ");

        assertThat(properties.endpointUrl()).isEqualTo("http://core:8080/internal/inbound");
        assertThat(properties.timeoutMs()).isEqualTo(3000);
        assertThat(properties.historyLimit()).isEqualTo(10000);
        assertThat(properties.authToken()).isEqualTo("token");
        assertThat(properties.hasAuthToken()).isTrue();
    }

    @Test
    void backwardCompatibleConstructorKeepsCategoryDefaults() {
        var properties = new CoreForwardProperties(
                true,
                "http://core:18080",
                "inbound",
                1500,
                "secret",
                250
        );

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.endpointUrl()).isEqualTo("http://core:18080/inbound");
        assertThat(properties.timeoutMs()).isEqualTo(1500);
        assertThat(properties.historyLimit()).isEqualTo(250);
        assertThat(properties.shouldRecord(InboundEventCategory.TASK_LIFECYCLE_EVENT)).isTrue();
        assertThat(properties.shouldRecord(InboundEventCategory.TRANSPORT_SIGNAL)).isFalse();
        assertThat(properties.shouldForward(InboundEventCategory.TASK_LIFECYCLE_EVENT)).isTrue();
        assertThat(properties.shouldForward(InboundEventCategory.SYSTEM_SIGNAL)).isFalse();
    }
}
