package com.opensocket.aievent.core.integration.identity;

import java.util.EnumMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

/** Explicit local-only diagnostic gateway. It never reports a capability as granted. */
@Profile("!prod")
@ConditionalOnProperty(prefix = "integration-identity", name = "permission-probe", havingValue = "METADATA_ONLY")
public final class MetadataOnlyPermissionProbeGateway implements IntegrationPermissionProbeGateway {
    @Override
    public PermissionProbeObservation probe(IntegrationConnection connection, IntegrationPrincipal principal,
                                             IntegrationCredentialMetadata credential, IntegrationProjectMapping mapping) {
        var results = new EnumMap<IntegrationPermissionCapability, PermissionProbeResultStatus>(IntegrationPermissionCapability.class);
        for (var capability : IntegrationPermissionCapability.values()) results.put(capability, PermissionProbeResultStatus.NOT_TESTED);
        return new PermissionProbeObservation(results, java.util.List.of(),
                "No provider request was executed. Metadata-only probing cannot authorize a production mapping.");
    }
}
