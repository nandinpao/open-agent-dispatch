package com.opensocket.aievent.core.a2a.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACutoverUseCase;

/** Controls Expand → Backfill → Shadow Read → Cutover → Legacy Write Disabled → Contract. */
public class A2ACutoverService implements A2ACutoverUseCase {
    private final A2ACutoverRepository repository;
    public A2ACutoverService(A2ACutoverRepository repository){this.repository=repository;}

    @Transactional(readOnly=true)
    public A2ACutoverSnapshot current(String scopeId,int limit){
        A2ACutoverState state=find(scopeId);List<String> blockers=blockers(state);
        return new A2ACutoverSnapshot(state,repository.evidence(state.getScopeId(),Math.max(1,Math.min(limit,500))),blockers,blockers.isEmpty()&&state.getStage()==A2ACutoverStage.CONTRACT);
    }

    @Transactional
    public A2ACutoverState advance(A2ACutoverTransitionCommand command){
        if(command==null)throw new IllegalArgumentException("command is required");
        String scope=required(command.scopeId(),"scopeId");A2ACutoverState current=find(scope);
        if(current.getVersion()!=command.expectedVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        if(command.targetStage()==null||command.targetStage()!=current.getStage().next())throw new IllegalStateException("CUTOVER_STAGE_SEQUENCE_REQUIRED");
        validate(current,command);long expected=current.getVersion();A2ACutoverStage from=current.getStage();OffsetDateTime now=now();
        current.setStage(command.targetStage());current.setShadowReadEnabled(command.targetStage().ordinal()>=A2ACutoverStage.SHADOW_READ.ordinal()&&command.targetStage().ordinal()<A2ACutoverStage.CONTRACT.ordinal());
        current.setLegacyWriteEnabled(command.targetStage().legacyWritesAllowed());current.setIssueTrackingEnabled(command.issueTrackingEnabled());current.setShadowMismatchCount(Math.max(0,command.shadowMismatchCount()));
        current.setMigrationEvidenceReference(first(command.migrationEvidenceReference(),current.getMigrationEvidenceReference()));current.setRuntimeGateRunId(first(command.runtimeGateRunId(),current.getRuntimeGateRunId()));current.setReleaseEvidenceReference(first(command.releaseEvidenceReference(),current.getReleaseEvidenceReference()));
        current.setRollbackDeadline(command.rollbackDeadline()==null?current.getRollbackDeadline():command.rollbackDeadline());current.setUpdatedAt(now);current.setUpdatedBy(required(command.actorId(),"actorId"));current.setVersion(expected+1);
        A2ACutoverState saved=repository.saveExpectedVersion(current,expected);A2ACutoverEvidence evidence=new A2ACutoverEvidence();evidence.setEvidenceId("a2a-cutover-ev-"+UUID.randomUUID());evidence.setScopeId(scope);evidence.setFromStage(from);evidence.setToStage(saved.getStage());evidence.setEvidenceType("CUTOVER_STAGE_ADVANCED");evidence.setEvidenceReference(first(command.releaseEvidenceReference(),command.migrationEvidenceReference(),command.runtimeGateRunId()));evidence.setEvidenceHash(hash(scope+"|"+from+"|"+saved.getStage()+"|"+first(command.reason(),"none")+"|"+saved.getVersion()));evidence.setActorId(saved.getUpdatedBy());evidence.setOccurredAt(now);repository.appendEvidence(evidence);return saved;
    }

    private void validate(A2ACutoverState current,A2ACutoverTransitionCommand command){
        switch(command.targetStage()){
            case BACKFILL -> required(command.migrationEvidenceReference(),"migrationEvidenceReference");
            case SHADOW_READ -> required(first(command.migrationEvidenceReference(),current.getMigrationEvidenceReference()),"migrationEvidenceReference");
            case CUTOVER -> {if(command.shadowMismatchCount()!=0)throw new IllegalStateException("SHADOW_READ_MISMATCHES_REMAIN");required(command.runtimeGateRunId(),"runtimeGateRunId");if(command.issueTrackingEnabled())throw new IllegalStateException("ISSUE_TRACKING_MUST_BE_DISABLED_FOR_PHASE2_GATE");}
            case LEGACY_WRITE_DISABLED -> required(first(command.runtimeGateRunId(),current.getRuntimeGateRunId()),"runtimeGateRunId");
            case CONTRACT -> {required(command.releaseEvidenceReference(),"releaseEvidenceReference");if(command.rollbackDeadline()==null||command.rollbackDeadline().isAfter(now()))throw new IllegalStateException("ROLLBACK_WINDOW_NOT_CLOSED");}
            default -> { }
        }
    }
    private List<String> blockers(A2ACutoverState state){List<String>b=new ArrayList<>();if(state.getStage().ordinal()<A2ACutoverStage.CUTOVER.ordinal())b.add("CUTOVER_NOT_REACHED");if(state.isLegacyWriteEnabled())b.add("LEGACY_WRITES_ENABLED");if(state.isIssueTrackingEnabled())b.add("ISSUE_TRACKING_ENABLED");if(state.getShadowMismatchCount()!=0)b.add("SHADOW_READ_MISMATCHES");if(state.getRuntimeGateRunId()==null||state.getRuntimeGateRunId().isBlank())b.add("RUNTIME_GATE_EVIDENCE_MISSING");if(state.getStage()==A2ACutoverStage.CONTRACT&&(state.getReleaseEvidenceReference()==null||state.getReleaseEvidenceReference().isBlank()))b.add("SIGNED_RELEASE_EVIDENCE_MISSING");return List.copyOf(b);}
    private A2ACutoverState find(String scope){return repository.find(required(scope,"scopeId")).orElseThrow(()->new IllegalStateException("A2A_CUTOVER_STATE_NOT_INITIALIZED"));}
    private static String required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
    private static String first(String...values){for(String value:values)if(value!=null&&!value.isBlank())return value.trim();return null;}
    private static OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
