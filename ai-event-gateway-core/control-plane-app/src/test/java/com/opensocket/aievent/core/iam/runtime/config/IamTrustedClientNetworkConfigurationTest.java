package com.opensocket.aievent.core.iam.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextFilter;
import com.opensocket.aievent.core.http.observation.OpenDispatchHttpObservationConfiguration;
import com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class IamTrustedClientNetworkConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    IamTrustedClientNetworkConfiguration.class,
                    OpenDispatchHttpObservationConfiguration.class,
                    TestInfrastructure.class);

    @Test
    void requestContextResolverExistsWhenMachineOAuthIsDisabled() {
        runner.withPropertyValues("aeg.iam.machine-token.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IamMachineTokenProperties.class);
                    assertThat(context).hasSingleBean(TrustedClientIpResolver.class);
                    assertThat(context).hasSingleBean(OpenDispatchRequestContextFilter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructure {
        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }
}
