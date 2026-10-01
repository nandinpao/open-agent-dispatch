package com.opensocket.aievent.core.kernel.configuration.inventory;

import com.opensocket.aievent.core.kernel.configuration.ConfigurationInventoryGovernanceStatus;
import java.time.OffsetDateTime;

/** Human-owned governance state. Automated discovery is not allowed to advance this lifecycle. */
public record ConfigurationInventoryGovernance(
    String configurationKey,
    ConfigurationInventoryGovernanceStatus status,
    String sourceObservationHash,
    String domainOwner,
    String authorityClass,
    String scope,
    String risk,
    String mutability,
    String consumerContract,
    Boolean adminEditable,
    Boolean requiresApproval,
    String classifiedBy,
    OffsetDateTime classifiedAt,
    String ownerReviewedBy,
    OffsetDateTime ownerReviewedAt,
    String architectureApprovedBy,
    OffsetDateTime architectureApprovedAt,
    String migrationAuthorizedBy,
    OffsetDateTime migrationAuthorizedAt,
    String reason,
    long version,
    OffsetDateTime updatedAt
) {}
