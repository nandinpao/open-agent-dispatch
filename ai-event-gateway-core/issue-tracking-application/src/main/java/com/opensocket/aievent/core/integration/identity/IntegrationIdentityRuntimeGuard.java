package com.opensocket.aievent.core.integration.identity;

import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Fail-fast production wiring guard for external Issue identity and credentials. */
public final class IntegrationIdentityRuntimeGuard implements SmartInitializingSingleton {
    private final ObjectProvider<IntegrationSecretResolver> resolvers;
    private final ObjectProvider<IntegrationPermissionProbeGateway> probes;
    private final Environment environment;

    public IntegrationIdentityRuntimeGuard(ObjectProvider<IntegrationSecretResolver> resolvers,
                                           ObjectProvider<IntegrationPermissionProbeGateway> probes,
                                           Environment environment) {
        this.resolvers = resolvers;
        this.probes = probes;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!environment.getProperty("issue-tracking.enabled", Boolean.class, true)) return;
        List<IntegrationSecretResolver> installed = resolvers.orderedStream().toList();
        if (installed.isEmpty()) throw new IllegalStateException("ISSUE_TRACKING_SECRET_RESOLVER_REQUIRED");
        if (installed.size() != 1) throw new IllegalStateException("ISSUE_TRACKING_SECRET_RESOLVER_MUST_BE_UNIQUE");
        IntegrationSecretResolver resolver = installed.getFirst();
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (production && !"VAULT".equals(resolver.mode())) {
            throw new IllegalStateException("PRODUCTION_ISSUE_TRACKING_REQUIRES_VAULT_SECRET_RESOLVER");
        }
        List<IntegrationPermissionProbeGateway> gateways = probes.orderedStream().toList();
        if (gateways.isEmpty()) throw new IllegalStateException("ISSUE_TRACKING_PERMISSION_PROBE_GATEWAY_REQUIRED");
        if (production && gateways.stream().anyMatch(v -> v.getClass().getSimpleName().contains("MetadataOnly"))) {
            throw new IllegalStateException("METADATA_ONLY_PERMISSION_PROBE_FORBIDDEN_IN_PRODUCTION");
        }
    }
}
