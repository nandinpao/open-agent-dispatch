package com.opensocket.aievent.core.a2a.application.port.in;
import com.opensocket.aievent.core.a2a.*;
public interface A2AUnifiedReconciliationUseCase {
 A2AReconciliationPage search(A2AReconciliationSearchQuery query);
 A2AReconciliationDetail detail(String tenantId,String caseId);
 A2AReconciliationCase execute(A2ARepairExecutionCommand command);
 A2AReconciliationCase recordOutcome(A2ARepairOutcomeCommand command);
 int reconcileDue(String workerId,int limit);
}
