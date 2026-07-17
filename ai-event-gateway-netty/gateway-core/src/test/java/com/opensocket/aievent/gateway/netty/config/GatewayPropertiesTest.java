package com.opensocket.aievent.gateway.netty.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPropertiesTest {

    @Test
    void defaultConstructorShouldProvideSafeRuntimeDefaults() {
        var properties = new GatewayProperties();

        assertThat(properties.nodeId()).isEqualTo("gateway-node-001");
        assertThat(properties.environment()).isEqualTo("local");
        assertThat(properties.version()).isEqualTo("dev");
        assertThat(properties.description()).isEqualTo("AI Event Gateway Netty");
        assertThat(properties.siteId()).isEqualTo("LOCAL");
        assertThat(properties.siteName()).isEqualTo("Local Site");
        assertThat(properties.region()).isEqualTo("local");
        assertThat(properties.zone()).isEqualTo("local-zone");
    }

    @Test
    void constructorShouldRemainBackwardCompatibleWithExistingUnitTests() {
        var properties = new GatewayProperties("gateway-node-test", "test", "1.2.5", "test gateway");

        assertThat(properties.nodeId()).isEqualTo("gateway-node-test");
        assertThat(properties.environment()).isEqualTo("test");
        assertThat(properties.version()).isEqualTo("1.2.5");
        assertThat(properties.description()).isEqualTo("test gateway");
        assertThat(properties.siteId()).isEqualTo("LOCAL");
    }

    @Test
    void springBinderShouldBindGatewayPropertiesWithoutConstructorBindingFailure() {
        var environment = new MockEnvironment()
                .withProperty("gateway.node-id", " gateway-tpe-001 ")
                .withProperty("gateway.environment", "docker")
                .withProperty("gateway.version", "1.2.5-p9-p6.5.2")
                .withProperty("gateway.description", "TPE gateway")
                .withProperty("gateway.site-id", "tpe")
                .withProperty("gateway.site-name", "Taipei Site")
                .withProperty("gateway.region", "tw-north")
                .withProperty("gateway.zone", "tpe-a");

        var properties = Binder.get(environment)
                .bind("gateway", Bindable.of(GatewayProperties.class))
                .orElseThrow(() -> new IllegalStateException("GatewayProperties binding failed"));

        assertThat(properties.nodeId()).isEqualTo("gateway-tpe-001");
        assertThat(properties.environment()).isEqualTo("docker");
        assertThat(properties.version()).isEqualTo("1.2.5-p9-p6.5.2");
        assertThat(properties.description()).isEqualTo("TPE gateway");
        assertThat(properties.siteId()).isEqualTo("TPE");
        assertThat(properties.siteName()).isEqualTo("Taipei Site");
        assertThat(properties.region()).isEqualTo("tw-north");
        assertThat(properties.zone()).isEqualTo("tpe-a");
    }
}
