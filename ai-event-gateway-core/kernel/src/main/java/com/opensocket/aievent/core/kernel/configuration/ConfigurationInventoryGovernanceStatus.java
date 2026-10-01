package com.opensocket.aievent.core.kernel.configuration;

/**
 * Governance lifecycle for discovered configuration observations.
 * Discovery tooling may create DISCOVERED evidence, but may not grant a later state.
 */
public enum ConfigurationInventoryGovernanceStatus {
    DISCOVERED,
    CLASSIFIED,
    OWNER_REVIEWED,
    ARCHITECTURE_APPROVED,
    MIGRATION_READY,
    MIGRATED,
    LEGACY_RETIRED
}
