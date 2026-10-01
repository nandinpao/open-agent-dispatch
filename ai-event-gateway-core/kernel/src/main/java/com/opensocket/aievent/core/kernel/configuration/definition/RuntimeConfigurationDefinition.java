package com.opensocket.aievent.core.kernel.configuration.definition;

/** Materialized read model of one source-controlled Runtime Configuration definition. */
public record RuntimeConfigurationDefinition(
        String key,
        String displayName,
        String owner,
        String domainOwner,
        String authorityClass,
        String scope,
        String scopeRef,
        String dataType,
        String unit,
        String risk,
        String mutability,
        String consumerContract,
        String configSetKey,
        boolean requiresApproval,
        boolean adminEditable,
        String validationJson,
        String initialSeedJson,
        String introducedVersion,
        String uiMetadataJson,
        String reviewStatus,
        boolean migrationAuthorized,
        int schemaVersion,
        String sourceRef) {
}
