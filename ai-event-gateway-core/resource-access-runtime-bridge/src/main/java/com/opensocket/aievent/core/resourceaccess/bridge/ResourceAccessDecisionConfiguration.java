package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.resourceaccess.contract.LegacyAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementMode;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationCachePort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAuthorizationPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourcePermissionAuthorityPort;
import com.opensocket.aievent.core.resourceaccess.contract.RuntimeAuthorizationLeasePort;
import com.opensocket.aievent.core.resourceaccess.core.DefaultRuntimeResultFenceService;
import com.opensocket.aievent.core.resourceaccess.contract.RuntimeResultFencePort;
import com.opensocket.aievent.core.resourceaccess.contract.RuntimeLateResultQuarantinePort;
import com.opensocket.aievent.core.resourceaccess.core.AuthoritativeResourceDescriptorService;
import com.opensocket.aievent.core.resourceaccess.core.DefaultResourceAuthorizationService;
import com.opensocket.aievent.core.resourceaccess.core.InMemoryResourceAuthorizationCache;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceScopeShareRepositoryPort;
import com.opensocket.aievent.core.resourceaccess.core.DefaultRuntimeAuthorizationLeaseService;
import com.opensocket.aievent.core.resourceaccess.core.RuntimeAuthorizationLeaseRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Resource Access decision, cache and runtime-lease composition. Business enforcement remains feature-flag controlled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "resource-access", name = "enabled", havingValue = "true")
public class ResourceAccessDecisionConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ResourceAuthorizationCachePort resourceAuthorizationCachePort(
            @Value("${resource-access.cache.maximum-entries:10000}") int maximumEntries,
            @Value("${resource-access.cache.maximum-concurrent-loads:128}") int maximumConcurrentLoads,
            @Value("${resource-access.cache.single-flight-wait-timeout:2s}") Duration waitTimeout) {
        return new InMemoryResourceAuthorizationCache(maximumEntries, maximumConcurrentLoads, waitTimeout);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock resourceAccessClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ResourceAuthorizationPort.class)
    ResourceAuthorizationPort resourceAuthorizationPort(
            AuthoritativeResourceDescriptorService descriptors,
            ResourceDecisionEvidenceRepository evidence,
            ResourcePermissionAuthorityPort permissions,
            ResourceAuthorizationCachePort cache,
            ObjectProvider<LegacyAuthorizationPort> legacy,
            ObjectProvider<ResourceScopeShareRepositoryPort> scopeShares,
            @Value("${resource-access.enforcement-mode:OFF}") ResourceAccessEnforcementMode enforcementMode,
            @Value("${resource-access.decision-mode:SHADOW}") ResourceAccessEnforcementMode decisionMode,
            @Value("${resource-access.cache.allow-ttl:30s}") Duration allowTtl,
            @Value("${resource-access.cache.deny-ttl:5s}") Duration denyTtl,
            Clock resourceAccessClock) {
        // P4RA-D evaluates in SHADOW while enforcement is OFF. P4RA-E can deliberately select an enforce mode.
        ResourceAccessEnforcementMode effectiveMode = enforcementMode == ResourceAccessEnforcementMode.OFF
                ? decisionMode
                : enforcementMode;
        return new DefaultResourceAuthorizationService(
                descriptors,
                evidence,
                permissions,
                cache,
                Optional.ofNullable(legacy.getIfAvailable()),
                Optional.ofNullable(scopeShares.getIfAvailable()),
                effectiveMode,
                allowTtl,
                denyTtl,
                resourceAccessClock);
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeAuthorizationLeasePort.class)
    @ConditionalOnProperty(prefix="resource-access",name="runtime-lease-enabled",havingValue="true")
    RuntimeAuthorizationLeasePort runtimeAuthorizationLeasePort(
            RuntimeAuthorizationLeaseRepository repository, ResourceDecisionEvidenceRepository evidence, Clock resourceAccessClock,
            @Value("${resource-access.runtime-lease.read-ttl:15m}") Duration readTtl,
            @Value("${resource-access.runtime-lease.write-ttl:5m}") Duration writeTtl,
            @Value("${resource-access.runtime-lease.read-recheck:30s}") Duration readRecheck,
            @Value("${resource-access.runtime-lease.restricted-recheck:10s}") Duration restrictedRecheck) {
        return new DefaultRuntimeAuthorizationLeaseService(repository,evidence,resourceAccessClock,readTtl,writeTtl,readRecheck,restrictedRecheck);
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeResultFencePort.class)
    @ConditionalOnProperty(prefix="resource-access",name="runtime-lease-enabled",havingValue="true")
    RuntimeResultFencePort runtimeResultFencePort(
            RuntimeAuthorizationLeasePort leases,
            RuntimeLateResultQuarantinePort quarantines,
            Clock resourceAccessClock) {
        return new DefaultRuntimeResultFenceService(leases, quarantines, resourceAccessClock);
    }
}
