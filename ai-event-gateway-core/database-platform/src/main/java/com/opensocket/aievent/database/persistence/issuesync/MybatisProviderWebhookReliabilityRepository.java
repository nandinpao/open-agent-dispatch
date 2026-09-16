package com.opensocket.aievent.database.persistence.issuesync;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.opensocket.aievent.core.integration.issue.webhook.*;
import com.opensocket.aievent.database.persistence.issuesync.dao.ProviderWebhookReliabilityDao;
import com.opensocket.aievent.database.persistence.issuesync.po.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
public class MybatisProviderWebhookReliabilityRepository implements ProviderWebhookReliabilityRepository {
    private final ProviderWebhookReliabilityDao dao;

    public MybatisProviderWebhookReliabilityRepository(ProviderWebhookReliabilityDao dao) {
        this.dao = dao;
    }

    @Override public ProviderWebhookInboxEntry saveInbox(ProviderWebhookInboxEntry value) { dao.upsertInbox(in(value)); return value; }
    @Override public boolean insertInbox(ProviderWebhookInboxEntry value) { return dao.insertInbox(in(value)) == 1; }
    @Override public boolean updateInboxExpectedVersion(ProviderWebhookInboxEntry value,long expectedVersion,String claimTokenHash) { return dao.updateInboxExpectedVersion(in(value),expectedVersion,claimTokenHash) == 1; }
    @Override public Optional<ProviderWebhookInboxEntry> findInbox(String tenantId,String inboxId) { return Optional.ofNullable(dao.findInbox(tenantId,inboxId)).map(this::out); }
    @Override public Optional<ProviderWebhookInboxEntry> findByProviderEvent(String tenantId,String connectionId,String providerEventId) { return Optional.ofNullable(dao.findByProviderEvent(tenantId,connectionId,providerEventId)).map(this::out); }
    @Override public Optional<ProviderWebhookInboxEntry> findByNonce(String tenantId,String connectionId,String nonce) { return Optional.ofNullable(dao.findByNonce(tenantId,connectionId,nonce)).map(this::out); }
    @Override public List<ProviderWebhookInboxEntry> listInbox(String tenantId,ProviderWebhookInboxStatus status,int limit) { return dao.listInbox(tenantId,status==null?null:status.name(),limit(limit)).stream().map(this::out).toList(); }
    @Override public List<ProviderWebhookInboxEntry> listDue(OffsetDateTime now,int limit) { return dao.listDue(now,limit(limit)).stream().map(this::out).toList(); }
    @Override public List<ProviderWebhookInboxEntry> claimDue(OffsetDateTime now,OffsetDateTime leaseUntil,String workerId,String claimTokenHash,String processingAttemptId,int limit) { return dao.claimDue(now,leaseUntil,workerId,claimTokenHash,processingAttemptId,limit(limit)).stream().map(this::out).toList(); }
    @Override public int recoverStaleClaims(OffsetDateTime now,int limit) { return dao.recoverStaleClaims(now,limit(limit)); }

    @Override public ExternalIssueObservation saveObservation(ExternalIssueObservation value) { dao.insertObservation(in(value)); return findObservationByInbox(value.tenantId(),value.inboxId()).orElse(value); }
    @Override public Optional<ExternalIssueObservation> findObservationByInbox(String tenantId,String inboxId) { return Optional.ofNullable(dao.findObservationByInbox(tenantId,inboxId)).map(this::out); }
    @Override public Optional<ExternalIssueObservation> latestObservation(String tenantId,String connectionId,String externalIssueId) { return Optional.ofNullable(dao.latestObservation(tenantId,connectionId,externalIssueId)).map(this::out); }
    @Override public List<ExternalIssueObservation> listObservations(String tenantId,String connectionId,String externalIssueId,int limit) { return dao.listObservations(tenantId,connectionId,externalIssueId,limit(limit)).stream().map(this::out).toList(); }

    @Override public ExternalIssueObservedState saveObservedState(ExternalIssueObservedState value) { dao.upsertObservedState(in(value)); return value; }
    @Override public boolean saveObservedStateIfNewer(ExternalIssueObservedState value) { return dao.upsertObservedStateIfNewer(in(value)) == 1; }
    @Override public Optional<ExternalIssueObservedState> findObservedState(String tenantId,String connectionId,String externalIssueId) { return Optional.ofNullable(dao.findObservedState(tenantId,connectionId,externalIssueId)).map(this::out); }

    @Override public ExternalIssueConflict saveConflict(ExternalIssueConflict value) { dao.upsertConflict(in(value)); return value; }
    @Override public boolean saveConflictExpectedVersion(ExternalIssueConflict value,long expectedVersion) { return dao.updateConflictExpectedVersion(in(value),expectedVersion) == 1; }
    @Override public Optional<ExternalIssueConflict> findConflict(String tenantId,String conflictId) { return Optional.ofNullable(dao.findConflict(tenantId,conflictId)).map(this::out); }
    @Override public Optional<ExternalIssueConflict> findConflictByResolutionIdempotency(String tenantId,String idempotencyKey) { return Optional.ofNullable(dao.findConflictByResolutionIdempotency(tenantId,idempotencyKey)).map(this::out); }
    @Override public List<ExternalIssueConflict> listConflicts(String tenantId,ExternalIssueConflictStatus status,int limit) { return dao.listConflicts(tenantId,status==null?null:status.name(),limit(limit)).stream().map(this::out).toList(); }
    @Override public ExternalIssueConflictEvent appendConflictEvent(ExternalIssueConflictEvent value) { dao.insertConflictEvent(in(value)); return value; }
    @Override public Optional<ExternalIssueConflictEvent> latestConflictEvent(String tenantId,String conflictId) { return Optional.ofNullable(dao.latestConflictEvent(tenantId,conflictId)).map(this::out); }
    @Override public List<ExternalIssueConflictEvent> listConflictEvents(String tenantId,String conflictId,int limit) { return dao.listConflictEvents(tenantId,conflictId,limit(limit)).stream().map(this::out).toList(); }

    @Override public WebhookReplayEvidence appendReplayEvidence(WebhookReplayEvidence value) { dao.insertReplayEvidence(in(value)); return value; }
    @Override public List<WebhookReplayEvidence> listReplayEvidence(String tenantId,String inboxId,int limit) { return dao.listReplayEvidence(tenantId,inboxId,limit(limit)).stream().map(this::out).toList(); }
    @Override public String mode() { return "MYBATIS_POSTGRESQL"; }

    private int limit(int value) { return Math.max(1,Math.min(1000,value)); }
    private <E extends Enum<E>> E enumValue(Class<E> type,String value,E fallback) { try { return value==null?fallback:Enum.valueOf(type,value); } catch(Exception ignored) { return fallback; } }

    private ProviderWebhookInboxPo in(ProviderWebhookInboxEntry value) {
        var po=new ProviderWebhookInboxPo();
        po.setTenantId(value.tenantId()); po.setInboxId(value.inboxId()); po.setConnectionId(value.connectionId()); po.setProviderType(value.providerType()); po.setProviderEventId(value.providerEventId()); po.setEventType(value.eventType());
        po.setExternalProjectId(value.externalProjectId()); po.setExternalIssueId(value.externalIssueId()); po.setExternalIssueKey(value.externalIssueKey()); po.setNonce(value.nonce()); po.setProviderTimestamp(value.providerTimestamp());
        po.setSignatureVerified(value.signatureVerified()); po.setTimestampVerified(value.timestampVerified()); po.setNonceAccepted(value.nonceAccepted()); po.setTenantBound(value.tenantBound()); po.setConnectionBound(value.connectionBound());
        po.setPayloadJson(value.payloadJson()); po.setPayloadHash(value.payloadHash()); po.setStatus(value.status().name()); po.setReplayCount(value.replayCount()); po.setRetryCount(value.retryCount()); po.setNextRetryAt(value.nextRetryAt());
        po.setClaimOwner(value.claimOwner()); po.setClaimTokenHash(value.claimTokenHash()); po.setClaimedAt(value.claimedAt()); po.setLeaseUntil(value.leaseUntil()); po.setProcessingAttemptId(value.processingAttemptId());
        po.setReceivedAt(value.receivedAt()); po.setProcessedAt(value.processedAt()); po.setLastErrorCode(value.lastErrorCode()); po.setLastErrorMessage(value.lastErrorMessage()); po.setVersion(value.version()); po.setCorrelationId(value.correlationId());
        return po;
    }

    private ProviderWebhookInboxEntry out(ProviderWebhookInboxPo po) {
        return new ProviderWebhookInboxEntry(po.getTenantId(),po.getInboxId(),po.getConnectionId(),po.getProviderType(),po.getProviderEventId(),po.getEventType(),po.getExternalProjectId(),po.getExternalIssueId(),po.getExternalIssueKey(),po.getNonce(),po.getProviderTimestamp(),po.isSignatureVerified(),po.isTimestampVerified(),po.isNonceAccepted(),po.isTenantBound(),po.isConnectionBound(),po.getPayloadJson(),po.getPayloadHash(),enumValue(ProviderWebhookInboxStatus.class,po.getStatus(),ProviderWebhookInboxStatus.RECEIVED),po.getReplayCount(),po.getRetryCount(),po.getNextRetryAt(),po.getClaimOwner(),po.getClaimTokenHash(),po.getClaimedAt(),po.getLeaseUntil(),po.getProcessingAttemptId(),po.getReceivedAt(),po.getProcessedAt(),po.getLastErrorCode(),po.getLastErrorMessage(),po.getVersion(),po.getCorrelationId());
    }

    private ExternalIssueObservationPo in(ExternalIssueObservation value) {
        var po=new ExternalIssueObservationPo();
        po.setTenantId(value.tenantId()); po.setObservationId(value.observationId()); po.setInboxId(value.inboxId()); po.setConnectionId(value.connectionId()); po.setProviderType(value.providerType()); po.setProviderEventId(value.providerEventId()); po.setProviderEventSequence(value.providerEventSequence()); po.setProviderChangeVersion(value.providerChangeVersion());
        po.setExternalProjectId(value.externalProjectId()); po.setExternalIssueId(value.externalIssueId()); po.setExternalIssueKey(value.externalIssueKey()); po.setExternalIssueStatus(value.externalIssueStatus()); po.setRawObservedDocumentJson(value.rawObservedDocumentJson()); po.setNormalizedObservationJson(value.normalizedObservationJson()); po.setObservedDocumentHash(value.observedDocumentHash()); po.setNormalizationProfileVersion(value.normalizationProfileVersion());
        po.setProviderIdentityHash(value.providerIdentityHash()); po.setMappingSchemaHash(value.mappingSchemaHash()); po.setProviderObservedAt(value.providerObservedAt()); po.setCreatedAt(value.createdAt()); po.setCorrelationId(value.correlationId()); return po;
    }

    private ExternalIssueObservation out(ExternalIssueObservationPo po) {
        return new ExternalIssueObservation(po.getTenantId(),po.getObservationId(),po.getInboxId(),po.getConnectionId(),po.getProviderType(),po.getProviderEventId(),po.getProviderEventSequence(),po.getProviderChangeVersion(),po.getExternalProjectId(),po.getExternalIssueId(),po.getExternalIssueKey(),po.getExternalIssueStatus(),po.getRawObservedDocumentJson(),po.getNormalizedObservationJson(),po.getObservedDocumentHash(),po.getNormalizationProfileVersion(),po.getProviderIdentityHash(),po.getMappingSchemaHash(),po.getProviderObservedAt(),po.getCreatedAt(),po.getCorrelationId());
    }

    private ExternalIssueObservedStatePo in(ExternalIssueObservedState value) {
        var po=new ExternalIssueObservedStatePo();
        po.setTenantId(value.tenantId()); po.setStateId(value.stateId()); po.setConnectionId(value.connectionId()); po.setExternalProjectId(value.externalProjectId()); po.setExternalIssueId(value.externalIssueId()); po.setExternalIssueKey(value.externalIssueKey()); po.setTaskIssueLinkId(value.taskIssueLinkId()); po.setProjectionId(value.projectionId()); po.setLatestObservationId(value.latestObservationId()); po.setLastAppliedProviderEventId(value.lastAppliedProviderEventId()); po.setProviderEventSequence(value.providerEventSequence()); po.setProviderChangeVersion(value.providerChangeVersion()); po.setLastAppliedEventAt(value.lastAppliedEventAt());
        po.setDesiredDocumentHash(value.desiredDocumentHash()); po.setObservedDocumentHash(value.observedDocumentHash()); po.setNormalizedObservationJson(value.normalizedObservationJson()); po.setDiffJson(value.diffJson()); po.setDiffHash(value.diffHash()); po.setProviderIdentityHash(value.providerIdentityHash()); po.setMappingSchemaHash(value.mappingSchemaHash()); po.setConflictClassification(value.conflictClassification().name()); po.setObservedAt(value.observedAt()); po.setVersion(value.version()); po.setUpdatedAt(value.updatedAt()); return po;
    }

    private ExternalIssueObservedState out(ExternalIssueObservedStatePo po) {
        return new ExternalIssueObservedState(po.getTenantId(),po.getStateId(),po.getConnectionId(),po.getExternalProjectId(),po.getExternalIssueId(),po.getExternalIssueKey(),po.getTaskIssueLinkId(),po.getProjectionId(),po.getLatestObservationId(),po.getLastAppliedProviderEventId(),po.getProviderEventSequence(),po.getProviderChangeVersion(),po.getLastAppliedEventAt(),po.getDesiredDocumentHash(),po.getObservedDocumentHash(),po.getNormalizedObservationJson(),po.getDiffJson(),po.getDiffHash(),po.getProviderIdentityHash(),po.getMappingSchemaHash(),enumValue(ExternalIssueConflictClassification.class,po.getConflictClassification(),ExternalIssueConflictClassification.NONE),po.getObservedAt(),po.getVersion(),po.getUpdatedAt());
    }

    private ExternalIssueConflictPo in(ExternalIssueConflict value) {
        var po=new ExternalIssueConflictPo();
        po.setTenantId(value.tenantId()); po.setConflictId(value.conflictId()); po.setConnectionId(value.connectionId()); po.setExternalProjectId(value.externalProjectId()); po.setExternalIssueId(value.externalIssueId()); po.setTaskIssueLinkId(value.taskIssueLinkId()); po.setProjectionId(value.projectionId()); po.setObservationId(value.observationId()); po.setClassification(value.classification().name()); po.setResolutionPolicy(value.resolutionPolicy().name()); po.setStatus(value.status().name()); po.setDesiredDocumentHash(value.desiredDocumentHash()); po.setObservedDocumentHash(value.observedDocumentHash()); po.setStateDiffJson(value.stateDiffJson()); po.setEvidenceHash(value.evidenceHash()); po.setReasonCode(value.reasonCode()); po.setDetectedAt(value.detectedAt()); po.setDecisionAt(value.decisionAt()); po.setResolvedAt(value.resolvedAt()); po.setResolvedBy(value.resolvedBy()); po.setResolutionReason(value.resolutionReason()); po.setResolutionIdempotencyKey(value.resolutionIdempotencyKey()); po.setResolutionRequestHash(value.resolutionRequestHash()); po.setVersion(value.version()); po.setCorrelationId(value.correlationId()); return po;
    }

    private ExternalIssueConflict out(ExternalIssueConflictPo po) {
        return new ExternalIssueConflict(po.getTenantId(),po.getConflictId(),po.getConnectionId(),po.getExternalProjectId(),po.getExternalIssueId(),po.getTaskIssueLinkId(),po.getProjectionId(),po.getObservationId(),enumValue(ExternalIssueConflictClassification.class,po.getClassification(),ExternalIssueConflictClassification.NONE),enumValue(ConflictResolutionPolicy.class,po.getResolutionPolicy(),ConflictResolutionPolicy.MANUAL_REVIEW),enumValue(ExternalIssueConflictStatus.class,po.getStatus(),ExternalIssueConflictStatus.OPEN),po.getDesiredDocumentHash(),po.getObservedDocumentHash(),po.getStateDiffJson(),po.getEvidenceHash(),po.getReasonCode(),po.getDetectedAt(),po.getDecisionAt(),po.getResolvedAt(),po.getResolvedBy(),po.getResolutionReason(),po.getResolutionIdempotencyKey(),po.getResolutionRequestHash(),po.getVersion(),po.getCorrelationId());
    }

    private ExternalIssueConflictEventPo in(ExternalIssueConflictEvent value) {
        var po=new ExternalIssueConflictEventPo();
        po.setTenantId(value.tenantId()); po.setEventId(value.eventId()); po.setConflictId(value.conflictId()); po.setEventType(value.eventType().name()); po.setActorId(value.actorId()); po.setReasonCode(value.reasonCode()); po.setMetadataJson(value.metadataJson()); po.setPreviousEventHash(value.previousEventHash()); po.setEventHash(value.eventHash()); po.setOccurredAt(value.occurredAt()); po.setCorrelationId(value.correlationId()); return po;
    }

    private ExternalIssueConflictEvent out(ExternalIssueConflictEventPo po) {
        return new ExternalIssueConflictEvent(po.getTenantId(),po.getEventId(),po.getConflictId(),enumValue(ExternalIssueConflictEventType.class,po.getEventType(),ExternalIssueConflictEventType.CONFLICT_CREATED),po.getActorId(),po.getReasonCode(),po.getMetadataJson(),po.getPreviousEventHash(),po.getEventHash(),po.getOccurredAt(),po.getCorrelationId());
    }

    private WebhookReplayEvidencePo in(WebhookReplayEvidence value) {
        var po=new WebhookReplayEvidencePo(); po.setTenantId(value.tenantId()); po.setEvidenceId(value.evidenceId()); po.setInboxId(value.inboxId()); po.setEventType(value.eventType()); po.setDecision(value.decision()); po.setReasonCode(value.reasonCode()); po.setEvidenceHash(value.evidenceHash()); po.setActorId(value.actorId()); po.setOccurredAt(value.occurredAt()); po.setCorrelationId(value.correlationId()); return po;
    }

    private WebhookReplayEvidence out(WebhookReplayEvidencePo po) {
        return new WebhookReplayEvidence(po.getTenantId(),po.getEvidenceId(),po.getInboxId(),po.getEventType(),po.getDecision(),po.getReasonCode(),po.getEvidenceHash(),po.getActorId(),po.getOccurredAt(),po.getCorrelationId());
    }
}
