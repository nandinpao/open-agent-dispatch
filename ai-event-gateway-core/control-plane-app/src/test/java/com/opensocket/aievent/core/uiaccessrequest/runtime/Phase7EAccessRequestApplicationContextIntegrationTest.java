package com.opensocket.aievent.core.uiaccessrequest.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.core.AuthoritativeResourceDescriptorService;
import com.opensocket.aievent.core.resourceaccess.core.GovernedAccessRequestRepository;
import com.opensocket.aievent.core.resourceaccess.core.ResourceScopeGrantService;
import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestService;
import com.opensocket.aievent.core.uicapability.api.UiAccessRequestConfiguration;
import com.opensocket.aievent.core.uicapability.core.UiActionCatalogResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Phase7EAccessRequestApplicationContextIntegrationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UiAccessRequestConfiguration.class)
            .withBean(UiActionCatalogResolver.class, () -> mock(UiActionCatalogResolver.class))
            .withBean(AuthoritativeResourceDescriptorService.class, () -> mock(AuthoritativeResourceDescriptorService.class))
            .withBean(ResourceScopeGrantService.class, () -> mock(ResourceScopeGrantService.class))
            .withBean(GovernedAccessRequestRepository.class, () -> mock(GovernedAccessRequestRepository.class))
            .withBean(ResourceAuthorizationPort.class, () -> mock(ResourceAuthorizationPort.class));

    @Test
    void disabledFlagsDoNotRegisterSubmissionAdapter() {
        runner.run(context -> assertThat(context).doesNotHaveBean(GovernedAccessRequestService.class));
    }

    @Test
    void enabledFlagsRegisterSubmissionAdapterWithoutReplacingAuthorizationAuthority() {
        runner.withPropertyValues("ui-capability.enabled=true", "ui-capability.access-request-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GovernedAccessRequestService.class);
                    assertThat(context).hasSingleBean(ResourceAuthorizationPort.class);
                    assertThat(context).hasSingleBean(GovernedAccessRequestRepository.class);
                    assertThat(context).hasSingleBean(ResourceScopeGrantService.class);
                });
    }
}
