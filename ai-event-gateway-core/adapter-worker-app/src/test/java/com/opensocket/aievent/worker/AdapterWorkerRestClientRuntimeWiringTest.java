package com.opensocket.aievent.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

class AdapterWorkerRestClientRuntimeWiringTest {

    @Test
    void boot4RestClientAutoConfigurationProvidesTheBuilderRequiredByWorker() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AdapterWorkerProperties.class, AdapterWorkerProperties::new);
            context.register(RestClientAutoConfiguration.class, AdapterWorkerCoreHttpClientConfiguration.class);
            context.refresh();

            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
            assertThat(context.getBean("adapterWorkerCoreRestClient", RestClient.class)).isNotNull();
        }
    }
}
