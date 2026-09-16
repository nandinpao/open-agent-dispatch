package com.opensocket.aievent.core.governance;
import java.util.*;
public interface AuditEvidenceRepository {
 AuthorizationDecision saveDecision(AuthorizationDecision value); Optional<AuthorizationDecision> findDecision(String tenantId,String decisionId);
 AuditEvidence saveEvidence(AuditEvidence value); List<AuditEvidence> listEvidence(String tenantId,String aggregateType,String aggregateId,int limit);
 ApiMutationReceipt saveReceipt(ApiMutationReceipt value); Optional<ApiMutationReceipt> findReceiptByIdempotency(String tenantId,String method,String path,String idempotencyKey); Optional<ApiMutationReceipt> findReceipt(String tenantId,String receiptId);
 String mode();
}
