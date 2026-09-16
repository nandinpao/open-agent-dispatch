package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/phase3-release-readiness")
public class Phase3ReleaseReadinessController {
    private final boolean runtimeCertified;
    private final boolean postgresCertified;
    private final boolean workerChaosCertified;
    private final boolean adminUiCertified;
    private final boolean jiraLiveCertified;
    private final boolean redmineLiveCertified;
    private final boolean crossProviderRelayCertified;
    private final boolean evidenceLedgerPersisted;
    private final boolean signedEvidence;

    public Phase3ReleaseReadinessController(
            @Value("${phase3.release.runtime-certified:false}") boolean runtimeCertified,
            @Value("${phase3.release.postgresql-certified:false}") boolean postgresCertified,
            @Value("${phase3.release.worker-chaos-certified:false}") boolean workerChaosCertified,
            @Value("${phase3.release.admin-ui-certified:false}") boolean adminUiCertified,
            @Value("${phase3.release.jira-live-certified:false}") boolean jiraLiveCertified,
            @Value("${phase3.release.redmine-live-certified:false}") boolean redmineLiveCertified,
            @Value("${phase3.release.cross-provider-relay-certified:false}") boolean crossProviderRelayCertified,
            @Value("${phase3.release.evidence-ledger-persisted:false}") boolean evidenceLedgerPersisted,
            @Value("${phase3.release.signed-evidence:false}") boolean signedEvidence) {
        this.runtimeCertified = runtimeCertified;
        this.postgresCertified = postgresCertified;
        this.workerChaosCertified = workerChaosCertified;
        this.adminUiCertified = adminUiCertified;
        this.jiraLiveCertified = jiraLiveCertified;
        this.redmineLiveCertified = redmineLiveCertified;
        this.crossProviderRelayCertified = crossProviderRelayCertified;
        this.evidenceLedgerPersisted = evidenceLedgerPersisted;
        this.signedEvidence = signedEvidence;
    }

    @GetMapping
    public ReadinessResponse readiness() {
        return calculate(runtimeCertified, postgresCertified, workerChaosCertified, adminUiCertified,
                jiraLiveCertified, redmineLiveCertified, crossProviderRelayCertified,
                evidenceLedgerPersisted, signedEvidence);
    }

    static ReadinessResponse calculate(boolean runtime, boolean postgres, boolean workerChaos,
            boolean adminUi, boolean jira, boolean redmine, boolean crossProviderRelay,
            boolean evidenceLedger, boolean signed) {
        List<Gate> gates = List.of(
                gate("SOURCE", "Phase 3 source and operations workspace", true, true,
                        "make phase3j-release-gate"),
                gate("RUNTIME", "Java 25 / Maven / Spring runtime", runtime, true,
                        "release/phase3/certification-runs"),
                gate("POSTGRESQL", "PostgreSQL V1-V66 migration and trigger certification", postgres, true,
                        "release/phase3/certification-runs"),
                gate("WORKER_CHAOS", "Multi-worker ordering, claim, lease and crash recovery", workerChaos, true,
                        "release/phase3/certification-runs"),
                gate("ADMIN_UI", "Admin UI typecheck, lint, build, accessibility and E2E", adminUi, true,
                        "release/phase3/certification-runs"),
                gate("JIRA_LIVE", "Scoped Jira live integration", jira, true,
                        "release/phase3/certification-runs"),
                gate("REDMINE_LIVE", "Scoped Redmine live integration", redmine, true,
                        "release/phase3/certification-runs"),
                gate("CROSS_PROVIDER_RELAY", "Jira to Redmine isolated relay certification", crossProviderRelay, true,
                        "release/phase3/certification-runs"),
                gate("EVIDENCE_LEDGER", "Certification evidence persisted in the V66 append-only ledger", evidenceLedger, true,
                        "integration_phase3_certification_evidence"),
                gate("SIGNED_EVIDENCE", "Ed25519 signed and verified release manifest", signed, true,
                        "release/phase3/phase3-release-signature.json"));
        boolean ready = gates.stream().filter(Gate::blocking).allMatch(g -> "PASSED".equals(g.status()));
        return new ReadinessResponse("Phase 3", ready ? "PRODUCTION_READY" : "NOT_READY", ready,
                OffsetDateTime.now(ZoneOffset.UTC), gates,
                ready ? List.of() : List.of(
                        "Static/source verification does not imply runtime certification.",
                        "Worker concurrency, live Jira/Redmine and cross-provider relay evidence are mandatory.",
                        "V66 ledger persistence and an Ed25519-verified manifest are required before production release."));
    }

    private static Gate gate(String id, String label, boolean passed, boolean blocking, String evidence) {
        return new Gate(id, label, passed ? "PASSED" : "NOT_CERTIFIED", evidence, blocking);
    }

    public record Gate(String gateId, String label, String status, String evidence, boolean blocking) { }
    public record ReadinessResponse(String phase, String status, boolean productionReady,
            OffsetDateTime generatedAt, List<Gate> gates, List<String> limitations) { }
}
