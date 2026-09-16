package com.opensocket.aievent.core.a2a.application.service;
import org.springframework.scheduling.annotation.Scheduled; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** Claims reconciliation cases with a lease and prepares deterministic plans. */
@ConditionalOnProperty(prefix="a2a.legacy-reconcilers",name="planner-only-enabled",havingValue="true",matchIfMissing=false)
public class ScheduledA2AUnifiedReconciler {private final A2AUnifiedReconciliationService service;private final String workerId=System.getenv().getOrDefault("HOSTNAME","a2a-reconciler-local");public ScheduledA2AUnifiedReconciler(A2AUnifiedReconciliationService s){service=s;}@Scheduled(fixedDelayString="${a2a.unified-reconciliation-ms:30000}", scheduler="reconciliationOperationalScheduler")public void reconcile(){service.reconcileDue(workerId,100);}}
