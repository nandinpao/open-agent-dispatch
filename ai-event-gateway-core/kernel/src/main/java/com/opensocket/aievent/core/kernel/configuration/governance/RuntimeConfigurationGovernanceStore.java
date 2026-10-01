package com.opensocket.aievent.core.kernel.configuration.governance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** V40-7 governance persistence port. Existing IAM/ReBAC remains the authorization authority. */
public interface RuntimeConfigurationGovernanceStore {
    RuntimeConfigurationEmergencyOverride createEmergencyOverride(String overrideId, String configSetId, String definitionKey,
            String baseRevisionId, String canonicalJson, String valueFingerprint, OffsetDateTime expiresAt,
            String actor, String reason, String correlationId);
    Optional<RuntimeConfigurationEmergencyOverride> findEmergencyOverride(String overrideId);
    List<RuntimeConfigurationEmergencyOverride> listActiveEmergencyOverrides(String configSetId);
    RuntimeConfigurationEmergencyOverride revokeEmergencyOverride(String overrideId, String actor, String reason, String correlationId);
    List<RuntimeConfigurationEmergencyOverride> expireDueEmergencyOverrides(int limit, String actor);
}
