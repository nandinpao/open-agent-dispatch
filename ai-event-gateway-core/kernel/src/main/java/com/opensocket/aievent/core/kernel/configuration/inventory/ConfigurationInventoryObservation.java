package com.opensocket.aievent.core.kernel.configuration.inventory;

import java.time.OffsetDateTime;

/** Executable-source observation materialized for human governance. */
public record ConfigurationInventoryObservation(
    String configurationKey,
    String namespace,
    String sourceObservationHash,
    String currentFilesJson,
    String profilesJson,
    String valueShapesJson,
    String reviewFlagsJson,
    String mutabilityFloor,
    String sourceUsageEvidenceJson,
    String advisoryClassificationJson,
    OffsetDateTime observedAt
) {}
