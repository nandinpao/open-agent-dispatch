package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.core.*;
import com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestService;
import com.opensocket.aievent.core.uicapability.core.UiActionCatalogResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "access-request-enabled"}, havingValue = "true")
@ConditionalOnBean({UiActionCatalogResolver.class, AuthoritativeResourceDescriptorService.class,
        ResourceScopeGrantService.class, GovernedAccessRequestRepository.class, ResourceAuthorizationPort.class})
public class UiAccessRequestConfiguration {
    @Bean GovernedAccessRequestService governedAccessRequestService(UiActionCatalogResolver actions,
            AuthoritativeResourceDescriptorService descriptors, ResourceScopeGrantService grants,
            GovernedAccessRequestRepository requests, ResourceAuthorizationPort authorization) {
        return new GovernedAccessRequestService(actions, descriptors, grants, requests, authorization);
    }

    @Bean UiAccessRequestApplicationService uiAccessRequestApplicationService(
            GovernedAccessRequestService accessRequests) {
        return new UiAccessRequestApplicationService(accessRequests);
    }
}
