package com.opensocket.aievent.gateway.netty.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;

class CoreOutboundRestClientRuntimeWiringTest {

    @Test
    void boot4RestClientAutoConfigurationProvidesTheBuilderRequiredByGateway() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CoreOutboundProperties.class, CoreOutboundProperties::new);
            context.register(RestClientAutoConfiguration.class, CoreOutboundHttpClientConfiguration.class);
            context.refresh();

            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
            assertThat(context.getBean("coreOutboundRestClient", RestClient.class)).isNotNull();
        }
    }
}
