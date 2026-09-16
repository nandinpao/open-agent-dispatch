package com.opensocket.aievent.database.persistence.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opensocket.aievent.core.governance.ApiMutationReceipt;
import com.opensocket.aievent.core.governance.AuditEvidence;
import com.opensocket.aievent.core.governance.AuditEvidenceRepository;
import com.opensocket.aievent.core.governance.AuthorizationDecision;
import com.opensocket.aievent.database.persistence.governance.dao.AuditEvidenceDao;
import com.opensocket.aievent.database.persistence.governance.po.ApiMutationReceiptPo;
import com.opensocket.aievent.database.persistence.governance.po.AuditEvidencePo;
import com.opensocket.aievent.database.persistence.governance.po.AuthorizationDecisionPo;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "core.audit-evidence", name = "store", havingValue = "MYBATIS")
public class MybatisAuditEvidenceRepository implements AuditEvidenceRepository {
    private static final Logger log=LoggerFactory.getLogger(MybatisAuditEvidenceRepository.class);
    private final AuditEvidenceDao dao;
    private final ObjectMapper mapper;

    public MybatisAuditEvidenceRepository(AuditEvidenceDao dao, ObjectMapper mapper) {
        this.dao = dao;
        this.mapper = mapper;
    }

    public AuthorizationDecision saveDecision(AuthorizationDecision value) {
        dao.insertDecision(toPo(value));
        return findDecision(value.tenantId(), value.decisionId()).orElse(value);
    }

    public Optional<AuthorizationDecision> findDecision(String tenantId, String decisionId) {
        return Optional.ofNullable(dao.findDecision(tenantId, decisionId)).map(this::fromPo);
    }

    public AuditEvidence saveEvidence(AuditEvidence value) {
        dao.insertEvidence(toPo(value));
        return value;
    }

    public List<AuditEvidence> listEvidence(String tenantId,
                                            String aggregateType,
                                            String aggregateId,
                                            int limit) {
        return dao.listEvidence(tenantId, aggregateType, aggregateId,
                        Math.max(1, Math.min(limit, 1000)))
                .stream().map(this::fromPo).toList();
    }

    public ApiMutationReceipt saveReceipt(ApiMutationReceipt value) {
        ApiMutationReceiptPo existing = dao.findReceipt(value.tenantId(), value.receiptId());
        if (existing != null) {
            dao.updateReceipt(toPo(value));
            return findReceipt(value.tenantId(), value.receiptId()).orElse(value);
        }

        int inserted = dao.insertReceipt(toPo(value));
        if (inserted > 0) return findReceipt(value.tenantId(), value.receiptId()).orElse(value);

        ApiMutationReceipt concurrent = findReceiptByIdempotency(
                value.tenantId(), value.requestMethod(), value.requestPath(), value.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("API_MUTATION_RECEIPT_INSERT_CONFLICT"));
        if (!concurrent.requestHash().equals(value.requestHash())) {
            throw new IllegalStateException("API_IDEMPOTENCY_CONFLICT");
        }
        return concurrent;
    }

    public Optional<ApiMutationReceipt> findReceiptByIdempotency(String tenantId,
                                                                 String requestMethod,
                                                                 String requestPath,
                                                                 String idempotencyKey) {
        return Optional.ofNullable(dao.findReceiptByIdempotency(
                tenantId, requestMethod, requestPath, idempotencyKey)).map(this::fromPo);
    }

    public Optional<ApiMutationReceipt> findReceipt(String tenantId, String receiptId) {
        return Optional.ofNullable(dao.findReceipt(tenantId, receiptId)).map(this::fromPo);
    }

    public String mode() { return "MYBATIS"; }

    private AuthorizationDecisionPo toPo(AuthorizationDecision value) {
        var po = new AuthorizationDecisionPo();
        po.setTenantId(value.tenantId()); po.setDecisionId(value.decisionId());
        po.setPermissionPoint(value.permissionPoint()); po.setResourceType(value.resourceType());
        po.setResourceId(value.resourceId()); po.setActorType(value.actorType());
        po.setActorId(value.actorId()); po.setDecision(value.decision());
        po.setReasonCode(value.reasonCode()); po.setEvaluatedScopesJson(write(value.evaluatedScopes()));
        po.setCorrelationId(value.correlationId()); po.setDecidedAt(value.decidedAt());
        return po;
    }

    private AuthorizationDecision fromPo(AuthorizationDecisionPo po) {
        return new AuthorizationDecision(po.getTenantId(), po.getDecisionId(),
                po.getPermissionPoint(), po.getResourceType(), po.getResourceId(),
                po.getActorType(), po.getActorId(), po.getDecision(), po.getReasonCode(),
                readList(po.getEvaluatedScopesJson()), po.getCorrelationId(), po.getDecidedAt());
    }

    private AuditEvidencePo toPo(AuditEvidence value) {
        var po = new AuditEvidencePo();
        po.setTenantId(value.tenantId()); po.setEvidenceId(value.evidenceId());
        po.setEventType(value.eventType()); po.setAggregateType(value.aggregateType());
        po.setAggregateId(value.aggregateId()); po.setRootTaskId(value.rootTaskId());
        po.setActorType(value.actorType()); po.setActorId(value.actorId());
        po.setAction(value.action()); po.setReasonCode(value.reasonCode());
        po.setAuditReason(value.auditReason()); po.setCorrelationId(value.correlationId());
        po.setCausationId(value.causationId());
        po.setAuthorizationDecisionId(value.authorizationDecisionId());
        po.setRequestId(value.requestId()); po.setClientAddress(value.clientAddress());
        po.setPayloadHash(value.payloadHash()); po.setOutcome(value.outcome());
        po.setEvidenceJson(write(value.evidence())); po.setOccurredAt(value.occurredAt());
        return po;
    }

    private AuditEvidence fromPo(AuditEvidencePo po) {
        return new AuditEvidence(po.getTenantId(), po.getEvidenceId(), po.getEventType(),
                po.getAggregateType(), po.getAggregateId(), po.getRootTaskId(),
                po.getActorType(), po.getActorId(), po.getAction(), po.getReasonCode(),
                po.getAuditReason(), po.getCorrelationId(), po.getCausationId(),
                po.getAuthorizationDecisionId(), po.getRequestId(), po.getClientAddress(),
                po.getPayloadHash(), po.getOutcome(), readMap(po.getEvidenceJson()),
                po.getOccurredAt());
    }

    private ApiMutationReceiptPo toPo(ApiMutationReceipt value) {
        var po = new ApiMutationReceiptPo();
        po.setTenantId(value.tenantId()); po.setReceiptId(value.receiptId());
        po.setRequestMethod(value.requestMethod()); po.setRequestPath(value.requestPath());
        po.setIdempotencyKey(value.idempotencyKey()); po.setRequestHash(value.requestHash());
        po.setExpectedVersion(value.expectedVersion()); po.setActorType(value.actorType());
        po.setActorId(value.actorId()); po.setAuditReason(value.auditReason());
        po.setCorrelationId(value.correlationId());
        po.setAuthorizationDecisionId(value.authorizationDecisionId());
        po.setPermissionPoint(value.permissionPoint()); po.setStatus(value.status());
        po.setResponseHash(value.responseHash()); po.setResultResourceType(value.resultResourceType());
        po.setResultResourceId(value.resultResourceId()); po.setResultVersion(value.resultVersion());
        po.setSyncStatus(value.syncStatus()); po.setCreatedAt(value.createdAt());
        po.setCompletedAt(value.completedAt());
        return po;
    }

    private ApiMutationReceipt fromPo(ApiMutationReceiptPo po) {
        return new ApiMutationReceipt(po.getTenantId(), po.getReceiptId(),
                po.getRequestMethod(), po.getRequestPath(), po.getIdempotencyKey(),
                po.getRequestHash(), po.getExpectedVersion(), po.getActorType(),
                po.getActorId(), po.getAuditReason(), po.getCorrelationId(),
                po.getAuthorizationDecisionId(), po.getPermissionPoint(), po.getStatus(),
                po.getResponseHash(), po.getResultResourceType(), po.getResultResourceId(),
                po.getResultVersion(), po.getSyncStatus(), po.getCreatedAt(), po.getCompletedAt());
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private List<String> readList(String value) {
        try {
            if (value == null) return List.of();
            List<?> raw = mapper.readValue(value, List.class);
            return raw.stream().map(String::valueOf).toList();
        } catch (Exception failure) {
            log.error("audit_evidence_list_deserialization_failed reasonCode=AUDIT_EVIDENCE_DESERIALIZATION_FAILED rawLength={} evidencePreservedAsCorruptMarker=true",
                    value==null?0:value.length(),failure);
            return List.of("!CORRUPT:AUDIT_EVIDENCE_DESERIALIZATION_FAILED");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        try { return value == null ? Map.of() : mapper.readValue(value, Map.class); }
        catch (Exception failure) {
            log.error("audit_evidence_map_deserialization_failed reasonCode=AUDIT_EVIDENCE_DESERIALIZATION_FAILED rawLength={} evidencePreservedAsCorruptMarker=true",
                    value==null?0:value.length(),failure);
            return Map.of("_evidenceStatus","CORRUPT","reasonCode","AUDIT_EVIDENCE_DESERIALIZATION_FAILED");
        }
    }
}
