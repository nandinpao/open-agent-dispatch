package com.opensocket.aievent.core.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEvidenceService {
    private final AuditEvidenceRepository repository;

    public AuditEvidenceService(AuditEvidenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Records API mutation admission evidence after the HTTP contract checks pass.
     * This method does not evaluate IAM Permission or Resource Scope.
     */
    @Transactional
    public AuthorizationDecision acceptApiMutationContract(String tenant,
                                                            String permission,
                                                            String resourceType,
                                                            String resourceId,
                                                            String actorType,
                                                            String actorId,
                                                            String correlationId,
                                                            List<String> scopes) {
        String id = "authz-" + UUID.randomUUID();
        return repository.saveDecision(new AuthorizationDecision(
                required(tenant, "tenantId"),
                id,
                required(permission, "permissionPoint"),
                resourceType,
                resourceId,
                required(actorType, "actorType"),
                required(actorId, "actorId"),
                "ALLOW",
                "API_CONTRACT_ACCEPTED",
                scopes,
                required(correlationId, "correlationId"),
                now()));
    }

    /**
     * @deprecated Use {@link #acceptApiMutationContract(String, String, String, String, String, String, String, List)}.
     * The historical name implied authorization authority that this service does not own.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public AuthorizationDecision allow(String tenant,
                                       String permission,
                                       String resourceType,
                                       String resourceId,
                                       String actorType,
                                       String actorId,
                                       String correlationId,
                                       List<String> scopes) {
        return acceptApiMutationContract(tenant, permission, resourceType, resourceId, actorType, actorId, correlationId, scopes);
    }

    @Transactional
    public AuthorizationDecision deny(String tenant,
                                      String permission,
                                      String resourceType,
                                      String resourceId,
                                      String actorType,
                                      String actorId,
                                      String reason,
                                      String correlationId,
                                      List<String> scopes) {
        String id = "authz-" + UUID.randomUUID();
        return repository.saveDecision(new AuthorizationDecision(
                required(tenant, "tenantId"),
                id,
                required(permission, "permissionPoint"),
                resourceType,
                resourceId,
                required(actorType, "actorType"),
                required(actorId, "actorId"),
                "DENY",
                required(reason, "reasonCode"),
                scopes,
                required(correlationId, "correlationId"),
                now()));
    }

    @Transactional
    public ApiMutationStart beginMutation(String tenant,
                                          String method,
                                          String path,
                                          String idempotency,
                                          String requestMaterial,
                                          Long expectedVersion,
                                          String actorType,
                                          String actorId,
                                          String auditReason,
                                          String correlation,
                                          String decisionId,
                                          String permission) {
        String normalizedTenant = required(tenant, "tenantId");
        String normalizedMethod = required(method, "method");
        String normalizedPath = required(path, "path");
        String key = required(idempotency, "idempotencyKey");
        String requestHash = hash(requestMaterial);
        var prior = repository.findReceiptByIdempotency(
                normalizedTenant, normalizedMethod, normalizedPath, key);
        if (prior.isPresent()) {
            if (!requestHash.equals(prior.get().requestHash())) {
                throw new IllegalStateException("API_IDEMPOTENCY_CONFLICT");
            }
            return new ApiMutationStart(prior.get(), true);
        }
        ApiMutationReceipt created = repository.saveReceipt(new ApiMutationReceipt(
                normalizedTenant,
                "mut-" + UUID.randomUUID(),
                normalizedMethod,
                normalizedPath,
                key,
                requestHash,
                expectedVersion,
                required(actorType, "actorType"),
                required(actorId, "actorId"),
                auditReason,
                required(correlation, "correlationId"),
                required(decisionId, "authorizationDecisionId"),
                required(permission, "permissionPoint"),
                "IN_PROGRESS",
                null,
                null,
                null,
                null,
                "NOT_APPLICABLE",
                now(),
                null));
        return new ApiMutationStart(created, false);
    }

    /** Compatibility method retained for existing callers. */
    @Transactional
    public ApiMutationReceipt begin(String tenant,
                                    String method,
                                    String path,
                                    String idempotency,
                                    String requestMaterial,
                                    Long expectedVersion,
                                    String actorType,
                                    String actorId,
                                    String auditReason,
                                    String correlation,
                                    String decisionId,
                                    String permission) {
        return beginMutation(tenant, method, path, idempotency, requestMaterial,
                expectedVersion, actorType, actorId, auditReason, correlation,
                decisionId, permission).receipt();
    }

    @Transactional
    public ApiMutationReceipt finish(ApiMutationReceipt before,
                                     String status,
                                     String responseMaterial,
                                     String resourceType,
                                     String resourceId,
                                     Long version,
                                     String syncStatus) {
        return repository.saveReceipt(new ApiMutationReceipt(
                before.tenantId(), before.receiptId(), before.requestMethod(),
                before.requestPath(), before.idempotencyKey(), before.requestHash(),
                before.expectedVersion(), before.actorType(), before.actorId(),
                before.auditReason(), before.correlationId(),
                before.authorizationDecisionId(), before.permissionPoint(), status,
                hash(responseMaterial), resourceType, resourceId, version, syncStatus,
                before.createdAt(), now()));
    }

    @Transactional
    public AuditEvidence record(String tenant,
                                String eventType,
                                String aggregateType,
                                String aggregateId,
                                String rootTaskId,
                                String actorType,
                                String actorId,
                                String action,
                                String reasonCode,
                                String auditReason,
                                String correlationId,
                                String causationId,
                                String authorizationDecisionId,
                                String requestId,
                                String clientAddress,
                                String payloadMaterial,
                                String outcome,
                                Map<String, Object> evidence) {
        return repository.saveEvidence(new AuditEvidence(
                required(tenant, "tenantId"),
                "audit-" + UUID.randomUUID(),
                required(eventType, "eventType"),
                aggregateType,
                aggregateId,
                rootTaskId,
                required(actorType, "actorType"),
                required(actorId, "actorId"),
                required(action, "action"),
                reasonCode,
                auditReason,
                required(correlationId, "correlationId"),
                causationId,
                authorizationDecisionId,
                requestId,
                clientAddress,
                hash(payloadMaterial),
                required(outcome, "outcome"),
                evidence,
                now()));
    }

    public List<AuditEvidence> evidence(String tenant, String type, String id, int limit) {
        return repository.listEvidence(required(tenant, "tenantId"), type, id, limit);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
