package com.opensocket.aievent.core.uicapability.api;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.AuthoritativeResourceDescriptorService;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import com.opensocket.aievent.core.uicapability.core.*;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ui-capability", name = {"enabled", "projection-api-enabled"}, havingValue = "true")
@ConditionalOnBean({ResourceAuthorizationPort.class, AuthoritativeResourceDescriptorService.class, ResourceDecisionEvidenceRepository.class})
@EnableConfigurationProperties(UiCapabilityProjectionProperties.class)
public class UiCapabilityProjectionConfiguration {

    @Bean UiCapabilityResourceDescriptorPort uiCapabilityResourceDescriptorPort(
            AuthoritativeResourceDescriptorService descriptors) {
        return (resourceRef, correlationId, requestedAt) -> {
            try {
                return java.util.Optional.of(descriptors.resolve(resourceRef,
                        new DescriptorResolutionContext(correlationId, "ui-capability-api", requestedAt)));
            } catch (IllegalArgumentException missing) {
                if ("RESOURCE_DESCRIPTOR_NOT_FOUND".equals(missing.getMessage())) return java.util.Optional.empty();
                throw missing;
            }
        };
    }
    @Bean UiCapabilityAuthorityNamespacePort uiCapabilityAuthorityNamespacePort(
            ResourceDecisionEvidenceRepository evidence) {
        return (tenantId, principal, resourceRef) -> new UiCapabilityAuthorityNamespace(
                evidence.currentPolicyVersion(tenantId),
                evidence.currentSecurityEpoch(tenantId, principal, resourceRef));
    }
    @Bean UiActionCatalogResolver uiActionCatalogResolver() { return new CanonicalUiActionCatalogResolver(); }
    @Bean UiPageCatalogResolver uiPageCatalogResolver() { return new CanonicalUiPageCatalogResolver(); }
    @Bean UiHydrationNoncePort uiHydrationNoncePort() { return new SecureRandomUiHydrationNonce(); }
    @Bean UiPageBootstrapService uiPageBootstrapService(UiPageCatalogResolver pages,
            UiCapabilityProjectionService projection, UiHydrationNoncePort nonces) {
        return new DefaultUiPageBootstrapService(pages, projection, nonces);
    }
    @Bean UiOperationPrerequisiteResolver uiOperationPrerequisiteResolver() { return new CatalogUiOperationPrerequisiteResolver(); }
    @Bean UiCapabilityProjectionCachePort uiCapabilityProjectionCache(UiCapabilityProjectionProperties properties) {
        return new InMemoryUiCapabilityProjectionCache(properties.getMaximumCacheEntries());
    }
    @Bean UiCapabilityProjectionService uiCapabilityProjectionService(
            ResourceAuthorizationPort authorization, UiCapabilityResourceDescriptorPort descriptors,
            UiCapabilityAuthorityNamespacePort namespaces, UiActionCatalogResolver catalog,
            UiOperationPrerequisiteResolver prerequisites, UiCapabilityProjectionCachePort cache,
            Clock clock, UiCapabilityProjectionProperties properties) {
        return new DefaultUiCapabilityProjectionService(authorization, descriptors, namespaces, catalog,
                prerequisites, cache, clock, properties.getMaximumTtl());
    }
}
