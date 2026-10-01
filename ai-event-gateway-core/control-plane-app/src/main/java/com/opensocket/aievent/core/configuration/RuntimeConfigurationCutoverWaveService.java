package com.opensocket.aievent.core.configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWave;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveStore;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertificationStore;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveSafetyAttestationStore;

/**
 * C3R3-A ordered wave orchestrator over the generic two-phase Config Set cutover engine.
 *
 * <p>Wave prepare is all-or-nothing in one database transaction. Wave finalize is also
 * all-or-nothing: every member must already have converged to the v2 RUNTIME_ONLY fingerprint
 * before any member governance is committed MIGRATED.</p>
 */
@Service
public class RuntimeConfigurationCutoverWaveService {
    private final RuntimeConfigurationCutoverWaveStore waves;
    private final RuntimeConfigurationGenericCutoverService cutover;
    private final RuntimeConfigurationCutoverWaveCertificationStore certifications;
    private final RuntimeConfigurationCutoverWaveSafetyAttestationStore safetyAttestations;
    private final RuntimeConfigurationCutoverWaveAuditService audit;

    public RuntimeConfigurationCutoverWaveService(RuntimeConfigurationCutoverWaveStore waves,
            RuntimeConfigurationGenericCutoverService cutover,
            RuntimeConfigurationCutoverWaveCertificationStore certifications,
            RuntimeConfigurationCutoverWaveSafetyAttestationStore safetyAttestations,
            RuntimeConfigurationCutoverWaveAuditService audit) {
        this.waves = waves;
        this.cutover = cutover;
        this.certifications = certifications;
        this.safetyAttestations = safetyAttestations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WaveStatus> listStatus() {
        List<RuntimeConfigurationCutoverWave> definitions = waves.list().stream()
                .sorted(Comparator.comparingInt(RuntimeConfigurationCutoverWave::sequenceNo)).toList();
        List<WaveStatus> out = new ArrayList<>();
        WaveStatus predecessor = null;
        for (RuntimeConfigurationCutoverWave wave : definitions) {
            WaveStatus current = evaluate(wave, predecessor);
            out.add(current);
            predecessor = current;
        }
        return List.copyOf(out);
    }

    @Transactional(readOnly = true)
    public WaveStatus status(String waveId) {
        String id = required(waveId, "waveId");
        return listStatus().stream().filter(w -> id.equals(w.waveId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_NOT_FOUND waveId=" + id));
    }

    @Transactional
    public WaveStatus prepare(String waveId, String actor, String reason, String correlationId) {
        RuntimeConfigurationCutoverWave wave = definition(waveId);
        WaveStatus before = status(wave.waveId());
        if (!"READY_TO_PREPARE".equals(before.phase()))
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_NOT_READY waveId=" + wave.waveId()
                    + " phase=" + before.phase() + " blockers=" + before.blockers());
        String waveReason = "[" + wave.waveId() + "] " + required(reason, "reason");
        for (RuntimeConfigurationCutoverWave.Member member : orderedMembers(wave))
            cutover.prepareWaveManaged(member.setKey(), actor, waveReason, correlationId);
        WaveStatus after = status(wave.waveId());
        audit.record("PREPARE", "configuration.cutover.prepare", before, after, actor, reason, correlationId, java.util.Map.of());
        return after;
    }

    @Transactional
    public WaveStatus finalizeWave(String waveId, String actor, String reason, String correlationId) {
        RuntimeConfigurationCutoverWave wave = definition(waveId);
        WaveStatus before = status(wave.waveId());
        if (!"READY_TO_FINALIZE".equals(before.phase()))
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_NOT_FINALIZABLE waveId=" + wave.waveId()
                    + " phase=" + before.phase() + " blockers=" + before.blockers());
        String waveReason = "[" + wave.waveId() + "] " + required(reason, "reason");
        for (RuntimeConfigurationCutoverWave.Member member : orderedMembers(wave))
            cutover.finalizeWaveManaged(member.setKey(), actor, waveReason, correlationId);
        WaveStatus after = status(wave.waveId());
        audit.record("FINALIZE", "configuration.cutover.finalize", before, after, actor, reason, correlationId, java.util.Map.of());
        return after;
    }

    @Transactional
    public WaveStatus cancel(String waveId, String actor, String reason, String correlationId) {
        RuntimeConfigurationCutoverWave wave = definition(waveId);
        WaveStatus before = status(wave.waveId());
        if ("FINALIZED".equals(before.phase()) || "FINALIZED_WITH_DRIFT".equals(before.phase()))
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_ALREADY_FINALIZED waveId=" + wave.waveId());
        String waveReason = "[" + wave.waveId() + "] " + required(reason, "reason");
        boolean cancelled = false;
        for (RuntimeConfigurationCutoverWave.Member member : orderedMembers(wave)) {
            String phase = cutover.status(member.setKey()).phase();
            if ("READY_TO_FINALIZE".equals(phase) || "PREPARED_AWAITING_CONVERGENCE".equals(phase)) {
                cutover.cancelWaveManaged(member.setKey(), actor, waveReason, correlationId);
                cancelled = true;
            }
        }
        if (!cancelled)
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_PREPARED_PLAN_REQUIRED waveId=" + wave.waveId());
        WaveStatus after = status(wave.waveId());
        audit.record("CANCEL", "configuration.cutover.cancel", before, after, actor, reason, correlationId, java.util.Map.of());
        return after;
    }

    private WaveStatus evaluate(RuntimeConfigurationCutoverWave wave, WaveStatus predecessor) {
        List<String> blockers = new ArrayList<>();
        List<MemberStatus> members = new ArrayList<>();
        int readyToPrepare = 0, readyToFinalize = 0, finalized = 0, convergedNodes = 0, requiredNodes = 0, actualKeys = 0;
        boolean anyPrepared = false, anyFinalizedWithDrift = false;

        for (RuntimeConfigurationCutoverWave.Member member : orderedMembers(wave)) {
            RuntimeConfigurationGenericCutoverService.CutoverStatus status = cutover.status(member.setKey());
            int memberKeys = status.requiredKeys().size();
            actualKeys += memberKeys;
            if (memberKeys != member.expectedRuntimeKeyCount())
                blockers.add("CONFIG_SET_KEY_COUNT_DRIFT:" + member.setKey() + ":expected="
                        + member.expectedRuntimeKeyCount() + ":actual=" + memberKeys);
            for (String blocker : status.blockers()) blockers.add(member.setKey() + ":" + blocker);
            if ("READY_TO_PREPARE".equals(status.phase())) readyToPrepare++;
            if ("READY_TO_FINALIZE".equals(status.phase())) { readyToFinalize++; anyPrepared = true; }
            if ("PREPARED_AWAITING_CONVERGENCE".equals(status.phase())) anyPrepared = true;
            if ("ALREADY_MIGRATED".equals(status.phase())) finalized++;
            if ("MIGRATED_WITH_DRIFT".equals(status.phase())) { finalized++; anyFinalizedWithDrift = true; }
            convergedNodes += status.convergedNodeCount();
            requiredNodes += status.requiredNodeCount();
            members.add(new MemberStatus(member.setKey(), member.sequenceNo(), member.expectedRuntimeKeyCount(), memberKeys,
                    status.configSetId(), status.phase(), status.expectedSnapshotFingerprint(), status.requiredNodeCount(),
                    status.convergedNodeCount(), status.blockers()));
        }

        if (finalized > 0 && finalized < wave.members().size())
            blockers.add("WAVE_PARTIAL_FINALIZATION_DETECTED:finalized=" + finalized + ":total=" + wave.members().size());

        boolean predecessorFinalized = predecessor == null || "FINALIZED".equals(predecessor.phase());
        String predecessorWaveId = predecessor == null ? null : predecessor.waveId();
        String predecessorCertificationStatus = null;
        String predecessorCertificationId = null;
        boolean predecessorCertified = predecessor == null;
        if (predecessor != null) {
            var predecessorCertification = certifications.latest(predecessor.waveId()).orElse(null);
            if (predecessorCertification != null) {
                predecessorCertificationStatus = predecessorCertification.status();
                predecessorCertificationId = predecessorCertification.certificationId();
                predecessorCertified = "PASS".equals(predecessorCertification.status());
            }
        }
        if (!predecessorFinalized)
            blockers.add("PREDECESSOR_WAVE_NOT_FINALIZED:" + predecessor.waveId() + ":" + predecessor.phase());
        if (predecessor != null && !predecessorCertified)
            blockers.add("PREDECESSOR_WAVE_NOT_CERTIFIED:" + predecessor.waveId() + ":"
                    + (predecessorCertificationStatus == null ? "MISSING" : predecessorCertificationStatus));

        boolean safetyAttestationRequired = "C3R3-W4".equals(wave.waveId()) || "C3R3-W5".equals(wave.waveId()) || "C3R3-W6".equals(wave.waveId());
        String safetyAttestationProfile = "C3R3-W4".equals(wave.waveId()) ? "TASK_DISPATCH"
                : ("C3R3-W5".equals(wave.waveId()) ? "EXTERNAL_INTEGRATION_A2A"
                : ("C3R3-W6".equals(wave.waveId()) ? "PLATFORM_READINESS_RECOVERY" : null));
        String safetyAttestationStatus = null, safetyAttestationId = null, safetyAttestationExpiresAt = null;
        if (safetyAttestationRequired) {
            var latestSafety = safetyAttestations.latest(wave.waveId()).orElse(null);
            boolean safetyValid = false;
            if (latestSafety != null) {
                safetyAttestationStatus = latestSafety.status();
                safetyAttestationId = latestSafety.attestationId();
                safetyAttestationExpiresAt = latestSafety.expiresAt().toString();
                safetyValid = "PASS".equals(latestSafety.status()) && latestSafety.expiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC));
            }
            if (finalized != wave.members().size() && !safetyValid) {
                if (latestSafety == null) blockers.add("SAFETY_ATTESTATION_REQUIRED:" + wave.waveId());
                else if (!"PASS".equals(latestSafety.status())) blockers.add("SAFETY_ATTESTATION_NOT_PASS:" + latestSafety.status());
                else blockers.add("SAFETY_ATTESTATION_EXPIRED:" + latestSafety.expiresAt());
            }
        }

        String phase;
        if (finalized == wave.members().size())
            phase = anyFinalizedWithDrift || !blockers.isEmpty() ? "FINALIZED_WITH_DRIFT" : "FINALIZED";
        else if (finalized > 0)
            phase = "INVALID_PARTIAL_FINALIZATION";
        else if (readyToFinalize == wave.members().size() && predecessorFinalized && predecessorCertified && blockers.isEmpty())
            phase = "READY_TO_FINALIZE";
        else if (anyPrepared)
            phase = "PREPARED_AWAITING_CONVERGENCE";
        else if (readyToPrepare == wave.members().size() && predecessorFinalized && predecessorCertified && blockers.isEmpty())
            phase = "READY_TO_PREPARE";
        else
            phase = "NOT_READY";

        return new WaveStatus(wave.waveId(), wave.sequenceNo(), wave.displayName(), wave.riskTier(),
                wave.requiredAuthorityContractVersion(), wave.releaseStage(), phase, List.copyOf(blockers),
                List.copyOf(members), wave.members().size(), actualKeys, requiredNodes, convergedNodes,
                predecessorWaveId, predecessorCertificationStatus, predecessorCertificationId,
                safetyAttestationRequired, safetyAttestationProfile, safetyAttestationStatus, safetyAttestationId, safetyAttestationExpiresAt);
    }

    private RuntimeConfigurationCutoverWave definition(String waveId) {
        return waves.find(required(waveId, "waveId"))
                .orElseThrow(() -> new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_NOT_FOUND waveId=" + waveId));
    }

    private static List<RuntimeConfigurationCutoverWave.Member> orderedMembers(RuntimeConfigurationCutoverWave wave) {
        return wave.members().stream().sorted(Comparator.comparingInt(RuntimeConfigurationCutoverWave.Member::sequenceNo)).toList();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public record MemberStatus(String setKey, int sequenceNo, int expectedRuntimeKeyCount, int actualRuntimeKeyCount,
            String configSetId, String phase, String expectedSnapshotFingerprint, int requiredNodeCount,
            int convergedNodeCount, List<String> blockers) {}

    public record WaveStatus(String waveId, int sequenceNo, String displayName, String riskTier,
            int requiredAuthorityContractVersion, String releaseStage, String phase, List<String> blockers,
            List<MemberStatus> members, int configSetCount, int runtimeKeyCount, int requiredNodeCount,
            int convergedNodeCount, String predecessorWaveId, String predecessorCertificationStatus,
            String predecessorCertificationId, boolean safetyAttestationRequired, String safetyAttestationProfile,
            String safetyAttestationStatus, String safetyAttestationId, String safetyAttestationExpiresAt) {}
}
