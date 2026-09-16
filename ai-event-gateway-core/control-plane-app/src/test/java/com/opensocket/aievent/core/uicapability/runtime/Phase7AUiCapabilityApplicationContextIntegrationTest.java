package com.opensocket.aievent.core.uicapability.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.core.AuthoritativeResourceDescriptorService;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import com.opensocket.aievent.core.uicapability.api.UiCapabilityProjectionConfiguration;
import com.opensocket.aievent.core.uicapability.core.UiCapabilityAuthorityNamespacePort;
import com.opensocket.aievent.core.uicapability.core.UiCapabilityProjectionService;
import com.opensocket.aievent.core.uicapability.core.UiCapabilityResourceDescriptorPort;
import com.opensocket.aievent.core.uicapability.core.UiPageBootstrapService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Phase7AUiCapabilityApplicationContextIntegrationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UiCapabilityProjectionConfiguration.class)
            .withBean(ResourceAuthorizationPort.class, () -> mock(ResourceAuthorizationPort.class))
            .withBean(AuthoritativeResourceDescriptorService.class,
                    () -> mock(AuthoritativeResourceDescriptorService.class))
            .withBean(ResourceDecisionEvidenceRepository.class,
                    () -> mock(ResourceDecisionEvidenceRepository.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void disabledFeatureFlagsDoNotRegisterProjectionAuthorityAdapters() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(UiCapabilityProjectionService.class)
                .doesNotHaveBean(UiPageBootstrapService.class)
                .doesNotHaveBean(UiCapabilityResourceDescriptorPort.class)
                .doesNotHaveBean(UiCapabilityAuthorityNamespacePort.class));
    }

    @Test
    void enabledFeatureFlagsRegisterProjectionAndPageBootstrapWithoutNewAuthority() {
        runner.withPropertyValues(
                        "ui-capability.enabled=true",
                        "ui-capability.projection-api-enabled=true",
                        "ui-capability.page-bootstrap-enabled=true",
                        "ui-capability.projection.maximum-cache-entries=256",
                        "ui-capability.projection.maximum-ttl=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UiCapabilityProjectionService.class);
                    assertThat(context).hasSingleBean(UiPageBootstrapService.class);
                    assertThat(context).hasSingleBean(UiCapabilityResourceDescriptorPort.class);
                    assertThat(context).hasSingleBean(UiCapabilityAuthorityNamespacePort.class);
                    assertThat(context).hasSingleBean(ResourceAuthorizationPort.class);
                    assertThat(context).hasSingleBean(ResourceDecisionEvidenceRepository.class);
                });
    }
}
