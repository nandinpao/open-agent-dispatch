package com.opensocket.aievent.core.a2a;
import java.util.List;
public interface A2AReconciliationEvidenceRepository {
    A2AReconciliationEvidence append(A2AReconciliationEvidence evidence);
    List<A2AReconciliationEvidence> findByCase(String tenantId, String caseId, int limit);
    boolean existsEvent(String tenantId, String eventKey);
    String mode();
}
