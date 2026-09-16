package com.opensocket.aievent.core.integration.identity;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical runtime-readiness contract for an external-Issue Project Mapping.
 *
 * <p>Every surface that claims a Mapping is usable by Route B must use this
 * contract. In particular, UI/setup readiness must not treat lifecycle ACTIVE
 * alone as equivalent to provider-write readiness.</p>
 */
public final class ProjectMappingRuntimeReadiness {
    private ProjectMappingRuntimeReadiness() {}

    public static boolean isReady(IntegrationProjectMapping mapping) {
        return blockers(mapping).isEmpty();
    }


    /**
     * Keep the legacy mappingStatus state machine aligned with the governance
     * lifecycle. VALID/ACTIVE lifecycle states are only reachable after
     * provider metadata validation, therefore their runtime status must be VALID.
     */
    public static ProjectMappingStatus statusForLifecycle(
            ProjectMappingStatus current, ProjectMappingLifecycle lifecycle) {
        if (lifecycle == ProjectMappingLifecycle.VALID || lifecycle == ProjectMappingLifecycle.ACTIVE) {
            return ProjectMappingStatus.VALID;
        }
        return current == null ? ProjectMappingStatus.DRAFT : current;
    }

    public static List<String> blockers(IntegrationProjectMapping mapping) {
        List<String> blockers = new ArrayList<>();
        if (mapping == null) {
            blockers.add("MAPPING_MISSING");
            return List.copyOf(blockers);
        }
        if (!mapping.enabled()) blockers.add("MAPPING_DISABLED");
        if (mapping.lifecycleStatus() != ProjectMappingLifecycle.ACTIVE) {
            blockers.add("LIFECYCLE_NOT_ACTIVE");
        }
        if (mapping.mappingStatus() != ProjectMappingStatus.VALID) {
            blockers.add("MAPPING_STATUS_NOT_VALID");
        }
        if (blank(mapping.metadataSnapshotId())) blockers.add("METADATA_SNAPSHOT_MISSING");
        if (blank(mapping.metadataSchemaHash())) blockers.add("METADATA_SCHEMA_MISSING");
        if (blank(mapping.connectionId())) blockers.add("CONNECTION_ID_MISSING");
        if (blank(mapping.externalProjectId())) blockers.add("EXTERNAL_PROJECT_ID_MISSING");
        return List.copyOf(blockers);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
