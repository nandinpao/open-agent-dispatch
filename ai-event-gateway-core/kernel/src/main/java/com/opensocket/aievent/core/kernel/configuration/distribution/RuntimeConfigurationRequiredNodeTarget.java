package com.opensocket.aievent.core.kernel.configuration.distribution;

import java.time.OffsetDateTime;

/**
 * Required runtime-configuration convergence target.
 *
 * <p>This is topology truth, not apply truth. A target may exist before the node has ever
 * reconciled, which is how the control plane can represent NOT_SEEN instead of silently
 * shrinking the denominator to the nodes that happened to ACK.</p>
 */
public record RuntimeConfigurationRequiredNodeTarget(
        String configSetId,
        String nodeId,
        String nodeRole,
        String nodeInstanceId,
        boolean required,
        String registrationSource,
        int supportedAuthorityContractVersion,
        OffsetDateTime registeredAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime updatedAt) {
}
