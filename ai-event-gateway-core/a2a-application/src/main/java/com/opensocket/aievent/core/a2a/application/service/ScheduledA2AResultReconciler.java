package com.opensocket.aievent.core.a2a.application.service;

import java.time.OffsetDateTime; import java.time.ZoneOffset; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import com.opensocket.aievent.core.a2a.A2AResultProcessingRepository;
/** Repairs canonical Result processing gaps without guessing external outcomes. */
@ConditionalOnProperty(prefix="a2a.legacy-reconcilers",name="result-enabled",havingValue="true",matchIfMissing=false)
public class ScheduledA2AResultReconciler {
 private final A2AResultProcessingRepository processing; private final A2AResultCompletionCoordinator coordinator;
 public ScheduledA2AResultReconciler(A2AResultProcessingRepository processing,A2AResultCompletionCoordinator coordinator){this.processing=processing;this.coordinator=coordinator;}
 @Scheduled(fixedDelayString="${a2a.result-reconciliation-ms:30000}", scheduler="reconciliationOperationalScheduler") public void reconcile(){for(var item:processing.findDue(OffsetDateTime.now(ZoneOffset.UTC),200)){coordinator.process(item.getTenantId(),item.getResultId(),"A2A_RESULT_RECONCILER");}}
}
