package com.opensocket.aievent.core.a2a.application.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import com.opensocket.aievent.core.a2a.application.service.A2ACancellationService;
import com.opensocket.aievent.core.a2a.application.service.A2ACompatibilityFacadeService;
import com.opensocket.aievent.core.a2a.application.service.A2ACutoverService;
import com.opensocket.aievent.core.a2a.application.service.A2ALegacyWriteGuard;
import com.opensocket.aievent.core.a2a.application.service.A2AGovernanceService;
import com.opensocket.aievent.core.a2a.application.service.A2ALateResultGovernanceService;
import com.opensocket.aievent.core.a2a.application.service.A2AOperationsWorkspaceService;
import com.opensocket.aievent.core.a2a.application.service.A2AResultAcceptanceService;
import com.opensocket.aievent.core.a2a.application.service.A2AResultCompletionCoordinator;
import com.opensocket.aievent.core.a2a.application.service.A2AResultReliabilityService;
import com.opensocket.aievent.core.a2a.application.service.A2AUnifiedReconciliationService;
import com.opensocket.aievent.core.a2a.application.service.ScheduledA2AReconciliationPipeline;
import com.opensocket.aievent.core.a2a.application.service.A2ACanonicalRepairExecutor;
import com.opensocket.aievent.core.a2a.application.service.A2AReconciliationCaseProducer;


import com.opensocket.aievent.core.integration.handoff.HandoffContextService;


/** Explicit canonical wiring for the A2A application boundary. */
@AutoConfiguration
@Import({
        A2AGovernanceService.class,
        A2ALegacyWriteGuard.class,
        A2ACompatibilityFacadeService.class,
        A2ACutoverService.class,
        A2AResultAcceptanceService.class,
        A2AResultCompletionCoordinator.class,
        A2AResultReliabilityService.class,
        A2ACancellationService.class,
        A2ALateResultGovernanceService.class,
        A2AOperationsWorkspaceService.class,
        A2AUnifiedReconciliationService.class,
        HandoffContextService.class,
        A2AReconciliationCaseProducer.class,
        A2ACanonicalRepairExecutor.class,
        ScheduledA2AReconciliationPipeline.class
})
public class A2AApplicationAutoConfiguration {
}
