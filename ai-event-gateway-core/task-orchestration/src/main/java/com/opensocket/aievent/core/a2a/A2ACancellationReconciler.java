package com.opensocket.aievent.core.a2a;

import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationRuntimeUseCase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="a2a.legacy-reconcilers",name="cancellation-enabled",havingValue="true",matchIfMissing=false)
public class A2ACancellationReconciler {
    private static final Logger log = LoggerFactory.getLogger(A2ACancellationReconciler.class);
    private final A2ACancellationRepository repository;
    private final A2ACancellationRuntimeUseCase service;

    public A2ACancellationReconciler(A2ACancellationRepository repository,
            A2ACancellationRuntimeUseCase service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${a2a.cancellation.reconcile-delay-ms:30000}", scheduler = "reconciliationOperationalScheduler")
    public void reconcile() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (A2ACancellationRecord cancellation : repository.findDue(now, 100)) {
            try {
                service.reconcile(cancellation, now);
            } catch (RuntimeException ex) {
                log.error("a2a_cancellation_reconcile_failed tenantId={} cancellationId={} requestId={}",
                        cancellation.getTenantId(), cancellation.getCancellationId(),
                        cancellation.getRequestId(), ex);
            }
        }
    }
}
