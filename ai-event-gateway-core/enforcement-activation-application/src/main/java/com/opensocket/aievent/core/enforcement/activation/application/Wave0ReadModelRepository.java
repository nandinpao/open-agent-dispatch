package com.opensocket.aievent.core.enforcement.activation.application;

import java.util.Optional;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0AdminSummaryView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0PermissionCatalogView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotSource;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadinessEvidenceView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0RuntimeStatusView;

public interface Wave0ReadModelRepository {
    Wave0RuntimeStatusView runtimeStatus(Wave0ReadPilotSource source);
    Optional<Wave0ReadinessEvidenceView> readinessEvidence(
            Wave0ReadPilotSource source,
            ReadinessEvidenceType type,
            UUID evidenceId);
    Optional<Wave0PermissionCatalogView> activePermissionCatalog(Wave0ReadPilotSource source);
    Wave0AdminSummaryView nonSensitiveAdminSummary(Wave0ReadPilotSource source);
}
