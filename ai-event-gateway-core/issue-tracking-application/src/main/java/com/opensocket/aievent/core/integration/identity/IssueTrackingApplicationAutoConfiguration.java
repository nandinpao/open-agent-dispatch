package com.opensocket.aievent.core.integration.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import com.opensocket.aievent.core.issuetracking.application.change.UnavailableExternalChangeProviderGateway;
import com.opensocket.aievent.core.issuetracking.application.change.UnavailableOpenDispatchCollaborationPort;
import com.opensocket.aievent.core.issuetracking.application.change.UnavailableProviderActionCommandPort;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangeProviderGateway;
import com.opensocket.aievent.core.issuetracking.change.OpenDispatchCollaborationPort;
import com.opensocket.aievent.core.issuetracking.change.ProviderActionCommandPort;
import com.opensocket.aievent.core.issuetracking.application.relay.UnavailableA2ARelayPolicySignalPort;
import com.opensocket.aievent.core.issuetracking.application.relay.UnavailableRelayProviderGateway;
import com.opensocket.aievent.core.issuetracking.relay.A2ARelayPolicySignalPort;
import com.opensocket.aievent.core.issuetracking.relay.RelayProviderGateway;
import com.opensocket.aievent.core.issuetracking.identity.*;

@AutoConfiguration
@Import({IntegrationIdentityService.class, IntegrationWebhookEndpointService.class, ProjectMappingGovernanceService.class, MetadataOnlyPermissionProbeGateway.class, MetadataOnlyProviderMetadataProbeGateway.class})
public class IssueTrackingApplicationAutoConfiguration {
    @Bean
    ProjectMappingMetadataRuntimeGuard projectMappingMetadataRuntimeGuard(ObjectProvider<ProviderMetadataProbeGateway> gateways, Environment environment) { return new ProjectMappingMetadataRuntimeGuard(gateways, environment); }

    @Bean
    IntegrationCredentialMaterialVerifier integrationCredentialMaterialVerifier(
            ObjectProvider<IntegrationSecretResolver> resolvers) {
        return new IntegrationCredentialMaterialVerifier(resolvers);
    }

    @Bean
    IntegrationIdentityRuntimeGuard integrationIdentityRuntimeGuard(
            ObjectProvider<IntegrationSecretResolver> resolvers,
            ObjectProvider<IntegrationPermissionProbeGateway> probes,
            Environment environment) {
        return new IntegrationIdentityRuntimeGuard(resolvers, probes, environment);
    }
    @Bean
    @ConditionalOnMissingBean(ExternalChangeProviderGateway.class)
    ExternalChangeProviderGateway unavailableExternalChangeProviderGateway() {
        return new UnavailableExternalChangeProviderGateway();
    }

    @Bean
    @ConditionalOnMissingBean(OpenDispatchCollaborationPort.class)
    OpenDispatchCollaborationPort unavailableOpenDispatchCollaborationPort() {
        return new UnavailableOpenDispatchCollaborationPort();
    }

    @Bean
    @ConditionalOnMissingBean(ProviderActionCommandPort.class)
    ProviderActionCommandPort unavailableProviderActionCommandPort() {
        return new UnavailableProviderActionCommandPort();
    }

    @Bean
    @ConditionalOnMissingBean(RelayProviderGateway.class)
    RelayProviderGateway unavailableRelayProviderGateway() {
        return new UnavailableRelayProviderGateway();
    }

    @Bean
    @ConditionalOnMissingBean(A2ARelayPolicySignalPort.class)
    A2ARelayPolicySignalPort unavailableA2ARelayPolicySignalPort() {
        return new UnavailableA2ARelayPolicySignalPort();
    }

    @Bean
    @ConditionalOnMissingBean(ProviderWriteIdentityPort.class)
    ProviderWriteIdentityPort providerWriteIdentityPort(IntegrationIdentityRepository identities, ExternalActorBindingRepository bindings) {
        return new ProviderWriteIdentityService(identities, bindings);
    }

}
