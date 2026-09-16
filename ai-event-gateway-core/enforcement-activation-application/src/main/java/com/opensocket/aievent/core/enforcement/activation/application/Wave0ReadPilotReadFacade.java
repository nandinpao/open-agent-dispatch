package com.opensocket.aievent.core.enforcement.activation.application;

import java.util.Objects;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0AdminSummaryView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0PermissionCatalogView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotResponse;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotSource;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadinessEvidenceView;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0RuntimeStatusView;

public final class Wave0ReadPilotReadFacade {
    private final Wave0ReadPilotService pilot;
    private final Wave0ReadModelRepository reads;

    public Wave0ReadPilotReadFacade(Wave0ReadPilotService pilot, Wave0ReadModelRepository reads) {
        this.pilot = Objects.requireNonNull(pilot, "pilot");
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    public Wave0ReadPilotResponse<Wave0RuntimeStatusView> runtimeStatus(Wave0ReadPilotService.ActorContext actor) {
        return pilot.execute(Wave0ReadPilotEntryPoint.ENFORCEMENT_RUNTIME_STATUS, actor,
                () -> reads.runtimeStatus(Wave0ReadPilotSource.LEGACY),
                () -> reads.runtimeStatus(Wave0ReadPilotSource.TARGET));
    }

    public Wave0ReadPilotResponse<Wave0ReadinessEvidenceView> readinessEvidence(
            ReadinessEvidenceType type,
            UUID evidenceId,
            Wave0ReadPilotService.ActorContext actor) {
        return pilot.execute(Wave0ReadPilotEntryPoint.READINESS_EVIDENCE, actor,
                () -> reads.readinessEvidence(Wave0ReadPilotSource.LEGACY, type, evidenceId)
                        .orElseThrow(() -> new Wave0ReadPilotException("READINESS_EVIDENCE_NOT_FOUND", "Readiness evidence was not found")),
                () -> reads.readinessEvidence(Wave0ReadPilotSource.TARGET, type, evidenceId)
                        .orElseThrow(() -> new Wave0ReadPilotException("READINESS_EVIDENCE_NOT_FOUND", "Readiness evidence was not found")));
    }

    public Wave0ReadPilotResponse<Wave0PermissionCatalogView> activePermissionCatalog(
            Wave0ReadPilotService.ActorContext actor) {
        return pilot.execute(Wave0ReadPilotEntryPoint.PERMISSION_CATALOG, actor,
                () -> reads.activePermissionCatalog(Wave0ReadPilotSource.LEGACY)
                        .orElseThrow(() -> new Wave0ReadPilotException("ACTIVE_PERMISSION_CATALOG_NOT_FOUND", "Active permission catalog was not found")),
                () -> reads.activePermissionCatalog(Wave0ReadPilotSource.TARGET)
                        .orElseThrow(() -> new Wave0ReadPilotException("ACTIVE_PERMISSION_CATALOG_NOT_FOUND", "Active permission catalog was not found")));
    }

    public Wave0ReadPilotResponse<Wave0AdminSummaryView> nonSensitiveAdminSummary(
            Wave0ReadPilotService.ActorContext actor) {
        return pilot.execute(Wave0ReadPilotEntryPoint.NON_SENSITIVE_ADMIN, actor,
                () -> reads.nonSensitiveAdminSummary(Wave0ReadPilotSource.LEGACY),
                () -> reads.nonSensitiveAdminSummary(Wave0ReadPilotSource.TARGET));
    }
}
