package com.opensocket.aievent.core.dispatch;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix="a2a.legacy-reconcilers",name="dispatch-enabled",havingValue="true",matchIfMissing=false) public class ScheduledDispatchBridgeReconciler {private final DispatchBridgeReconciliationService service;public ScheduledDispatchBridgeReconciler(DispatchBridgeReconciliationService service){this.service=service;}@Scheduled(fixedDelayString="${dispatch.bridge-reconciliation-ms:30000}", scheduler = "dispatchOperationalScheduler") public void reconcile(){service.reconcileDue(100);}}
