package com.opensocket.aievent.core.configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertification;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveCertificationStore;

import tools.jackson.databind.ObjectMapper;

/**
 * C3R3-B durable certification for a finalized cutover wave.
 *
 * <p>This certifies authority-contract v2 convergence and durable MIGRATED governance only.
 * It deliberately does not claim full product runtime certification.</p>
 */
@Service
public class RuntimeConfigurationCutoverWaveCertificationService {
    private final RuntimeConfigurationCutoverWaveService waves;
    private final RuntimeConfigurationGenericCutoverService cutover;
    private final RuntimeConfigurationCutoverWaveCertificationStore certifications;
    private final ObjectMapper json;
    private final RuntimeConfigurationCutoverWaveAuditService audit;

    public RuntimeConfigurationCutoverWaveCertificationService(RuntimeConfigurationCutoverWaveService waves,
            RuntimeConfigurationGenericCutoverService cutover,RuntimeConfigurationCutoverWaveCertificationStore certifications,ObjectMapper json,
            RuntimeConfigurationCutoverWaveAuditService audit) {
        this.waves=waves;this.cutover=cutover;this.certifications=certifications;this.json=json;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public CertificationStatus status(String waveId) {
        var wave=waves.status(required(waveId,"waveId"));
        return new CertificationStatus(wave,certifications.latest(wave.waveId()).orElse(null),eligible(wave));
    }

    @Transactional
    public RuntimeConfigurationCutoverWaveCertification certify(String waveId,String actor,String reason,String correlationId) {
        String id=required(waveId,"waveId"),operator=required(actor,"actor"),why=required(reason,"reason");
        var wave=waves.status(id);
        List<String> blockers=eligible(wave);
        if(!blockers.isEmpty()) throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_CERTIFICATION_NOT_READY waveId="+id+" blockers="+blockers);

        List<Map<String,Object>> memberEvidence=new ArrayList<>();
        int requiredNodeApplications=0,convergedNodeApplications=0;
        for(var member:wave.members()) {
            var current=cutover.status(member.setKey());
            List<String> memberBlockers=new ArrayList<>();
            if(!"ALREADY_MIGRATED".equals(current.phase())) memberBlockers.add("NOT_ALREADY_MIGRATED:"+current.phase());
            if(current.requiredKeys().size()!=member.expectedRuntimeKeyCount()) memberBlockers.add("KEY_COUNT_DRIFT");
            if(current.nodes().isEmpty()) memberBlockers.add("REQUIRED_NODE_TARGETS_MISSING");
            if(current.nodes().stream().anyMatch(n->n.supportedAuthorityContractVersion()<wave.requiredAuthorityContractVersion())) memberBlockers.add("NODE_AUTHORITY_CONTRACT_V2_UNSUPPORTED");
            if(current.nodes().stream().anyMatch(n->!n.converged())) memberBlockers.add("NODE_NOT_CONVERGED");
            if(current.keys().stream().anyMatch(k->!("MIGRATED".equals(k.governanceStatus())||"LEGACY_RETIRED".equals(k.governanceStatus())))) memberBlockers.add("KEY_NOT_MIGRATED");
            if(!memberBlockers.isEmpty()) throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_CERTIFICATION_MEMBER_INVALID setKey="+member.setKey()+" blockers="+memberBlockers);
            requiredNodeApplications+=current.requiredNodeCount(); convergedNodeApplications+=current.convergedNodeCount();
            Map<String,Object> evidence=new LinkedHashMap<>();
            evidence.put("setKey",member.setKey());evidence.put("configSetId",current.configSetId());evidence.put("phase",current.phase());
            evidence.put("revisionId",current.activeRevisionId());evidence.put("snapshotFingerprint",current.expectedSnapshotFingerprint());
            evidence.put("runtimeKeyCount",current.requiredKeys().size());evidence.put("requiredNodeCount",current.requiredNodeCount());
            evidence.put("convergedNodeCount",current.convergedNodeCount());
            evidence.put("nodeAuthorityContractVersions",current.nodes().stream().collect(java.util.stream.Collectors.toMap(
                    RuntimeConfigurationGenericCutoverService.NodeStatus::nodeId,
                    RuntimeConfigurationGenericCutoverService.NodeStatus::supportedAuthorityContractVersion,(a,b)->b,LinkedHashMap::new)));
            memberEvidence.add(evidence);
        }
        if(requiredNodeApplications<1||convergedNodeApplications!=requiredNodeApplications)
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_CERTIFICATION_CONVERGENCE_INCOMPLETE waveId="+id);
        Map<String,Object> evidenceRoot=new LinkedHashMap<>();
        evidenceRoot.put("stage",certificationStage(wave));
        evidenceRoot.put("waveId",id);
        evidenceRoot.put("wavePhase",wave.phase());
        evidenceRoot.put("authorityContractVersion",wave.requiredAuthorityContractVersion());
        evidenceRoot.put("correlationId",text(correlationId));
        if(wave.predecessorWaveId()!=null){
            evidenceRoot.put("predecessorWaveId",wave.predecessorWaveId());
            evidenceRoot.put("predecessorCertificationStatus",wave.predecessorCertificationStatus());
            evidenceRoot.put("predecessorCertificationId",wave.predecessorCertificationId());
        }
        if(wave.safetyAttestationRequired()){
            evidenceRoot.put("safetyAttestationStatus",wave.safetyAttestationStatus());
            evidenceRoot.put("safetyAttestationId",wave.safetyAttestationId());
            evidenceRoot.put("safetyAttestationExpiresAt",wave.safetyAttestationExpiresAt());
        }
        evidenceRoot.put("members",memberEvidence);
        String evidenceJson=serialize(evidenceRoot);
        var cert=new RuntimeConfigurationCutoverWaveCertification(UUID.randomUUID().toString(),id,"PASS",wave.requiredAuthorityContractVersion(),
                wave.configSetCount(),wave.runtimeKeyCount(),requiredNodeApplications,convergedNodeApplications,evidenceJson,operator,why,OffsetDateTime.now(ZoneOffset.UTC));
        var saved = certifications.save(cert);
        var after = waves.status(id);
        audit.record("CERTIFY", "configuration.cutover.certify", wave, after, operator, why, correlationId,
                Map.of("certificationId", saved.certificationId(), "status", saved.status()));
        return saved;
    }

    private List<String> eligible(RuntimeConfigurationCutoverWaveService.WaveStatus wave) {
        List<String> blockers=new ArrayList<>();
        if(!"FINALIZED".equals(wave.phase())) blockers.add("WAVE_NOT_FINALIZED:"+wave.phase());
        if(!wave.blockers().isEmpty()) blockers.addAll(wave.blockers());
        if(wave.requiredAuthorityContractVersion()<2) blockers.add("AUTHORITY_CONTRACT_V2_REQUIRED");
        if(wave.configSetCount()<1||wave.runtimeKeyCount()<1) blockers.add("EMPTY_WAVE");
        if(wave.requiredNodeCount()<1) blockers.add("REQUIRED_NODE_TARGETS_MISSING");
        if(wave.convergedNodeCount()!=wave.requiredNodeCount()) blockers.add("WAVE_NODE_CONVERGENCE_INCOMPLETE");
        if(wave.predecessorWaveId()!=null && !"PASS".equals(wave.predecessorCertificationStatus()))
            blockers.add("PREDECESSOR_WAVE_CERTIFICATION_REQUIRED:"+wave.predecessorWaveId()+":"
                    +(wave.predecessorCertificationStatus()==null?"MISSING":wave.predecessorCertificationStatus()));
        if(wave.safetyAttestationRequired() && !"PASS".equals(wave.safetyAttestationStatus()))
            blockers.add("SAFETY_ATTESTATION_PASS_REQUIRED:"+(wave.safetyAttestationStatus()==null?"MISSING":wave.safetyAttestationStatus()));
        return List.copyOf(blockers);
    }
    private static String certificationStage(RuntimeConfigurationCutoverWaveService.WaveStatus wave){
        return switch(wave.waveId()){
            case "C3R3-W1" -> "V41_C3R3B_AUTHORITY_CONTRACT_V2_ROLLOUT_WAVE1_RUNTIME_CUTOVER_CERTIFICATION";
            case "C3R3-W2" -> "V41_C3R3C_WAVE2_ADAPTER_EXECUTION_WORKER_SINGLE_AUTHORITY_CUTOVER";
            case "C3R3-W3" -> "V41_C3R3D_WAVE3_CLUSTER_CONNECTION_PROTECTION_SINGLE_AUTHORITY_CUTOVER";
            case "C3R3-W4" -> "V41_C3R3E_WAVE4_TASK_DISPATCH_TASK_AUTHORITY_SINGLE_AUTHORITY_CUTOVER";
            case "C3R3-W5" -> "V41_C3R3F_WAVE5_INCIDENT_ISSUE_INTEGRATION_A2A_SINGLE_AUTHORITY_CUTOVER";
            case "C3R3-W6" -> "V41_C3R3G_WAVE6_CORE_EDGE_GATEWAY_SINGLE_AUTHORITY_CUTOVER";
            default -> "V41_C3R3_RUNTIME_CUTOVER_WAVE_CERTIFICATION";
        };
    }
    private String serialize(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_CERTIFICATION_EVIDENCE_SERIALIZATION_FAILED",ex);}}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String text(String value){return value==null?"":value.trim();}
    public record CertificationStatus(RuntimeConfigurationCutoverWaveService.WaveStatus wave,
            RuntimeConfigurationCutoverWaveCertification latestCertification,List<String> certificationBlockers) {}
}
