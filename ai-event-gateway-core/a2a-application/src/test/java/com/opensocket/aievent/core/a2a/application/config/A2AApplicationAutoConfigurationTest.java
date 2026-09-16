package com.opensocket.aievent.core.a2a.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.*;
import com.opensocket.aievent.core.a2a.application.port.out.*;
import com.opensocket.aievent.core.a2a.application.service.*;
import com.opensocket.aievent.core.a2a.core.port.ResultEvidenceAuthorityPort;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.integration.handoff.*;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.task.TaskRepository;

class A2AApplicationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(A2AApplicationAutoConfiguration.class))
            .withBean(A2ATaskAuthorityOperations.class, () -> mock(A2ATaskAuthorityOperations.class))
            .withBean(A2ADispatchAuthorityOperations.class, () -> mock(A2ADispatchAuthorityOperations.class))
            .withBean(A2ACancellationAuthorityOperations.class, () -> mock(A2ACancellationAuthorityOperations.class))
            .withBean(ResultEvidenceAuthorityPort.class, () -> mock(ResultEvidenceAuthorityPort.class))
            .withBean(A2APolicyRepository.class, () -> mock(A2APolicyRepository.class))
            .withBean(A2ARequestRepository.class, () -> mock(A2ARequestRepository.class))
            .withBean(A2AStateHistoryRepository.class, () -> mock(A2AStateHistoryRepository.class))
            .withBean(A2ARateLimitRepository.class, () -> mock(A2ARateLimitRepository.class))
            .withBean(A2AIdempotencyRepository.class, () -> mock(A2AIdempotencyRepository.class))
            .withBean(A2ADomainEventPublisher.class, () -> mock(A2ADomainEventPublisher.class))
            .withBean(A2AResultRepository.class, () -> mock(A2AResultRepository.class))
            .withBean(A2AResultAttemptRepository.class, () -> mock(A2AResultAttemptRepository.class))
            .withBean(A2AResultIdempotencyClaimRepository.class, () -> mock(A2AResultIdempotencyClaimRepository.class))
            .withBean(A2AResultEvidenceRepository.class, () -> mock(A2AResultEvidenceRepository.class))
            .withBean(A2AResultQuarantineRepository.class, () -> mock(A2AResultQuarantineRepository.class))
            .withBean(A2AParentAggregationRepository.class, () -> mock(A2AParentAggregationRepository.class))
            .withBean(A2AResultProcessingRepository.class, () -> mock(A2AResultProcessingRepository.class))
            .withBean(A2AAggregationEvidenceRepository.class, () -> mock(A2AAggregationEvidenceRepository.class))
            .withBean(A2ACancellationRepository.class, () -> mock(A2ACancellationRepository.class))
            .withBean(A2ACancellationEvidenceRepository.class, () -> mock(A2ACancellationEvidenceRepository.class))
            .withBean(A2AReconciliationCaseRepository.class, () -> mock(A2AReconciliationCaseRepository.class))
            .withBean(A2AReconciliationEvidenceRepository.class, () -> mock(A2AReconciliationEvidenceRepository.class))
            .withBean(A2ACutoverRepository.class, () -> mock(A2ACutoverRepository.class))
            .withBean(A2ADispatchRepairPort.class, () -> mock(A2ADispatchRepairPort.class))
            .withBean(ModuleEventPublisher.class, () -> mock(ModuleEventPublisher.class))
            .withBean(TaskRepository.class, () -> mock(TaskRepository.class))
            .withBean(TaskAssignmentRepository.class, () -> mock(TaskAssignmentRepository.class))
            .withBean(DispatchRequestRepository.class, () -> mock(DispatchRequestRepository.class))
            .withBean(HandoffContextRepository.class, () -> mock(HandoffContextRepository.class))
            .withBean(HandoffDispatchReleasePort.class, () -> mock(HandoffDispatchReleasePort.class))
            .withBean(HandoffDomainEventPublisher.class, () -> mock(HandoffDomainEventPublisher.class));

    @Test
    void cutoverServiceSupportsClassBasedTransactionalProxy() {
        A2ACutoverService target = new A2ACutoverService(mock(A2ACutoverRepository.class));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());

        Object proxy = factory.getProxy();

        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
        assertThat(proxy).isInstanceOf(A2ACutoverService.class);
    }

    @Test
    void canonicalInboundPortsHaveExactlyOneApplicationBean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(A2AGovernanceUseCase.class);
            assertThat(context).hasSingleBean(A2AResultAcceptanceUseCase.class);
            assertThat(context).hasSingleBean(A2AResultReliabilityUseCase.class);
            assertThat(context).hasSingleBean(A2ACancellationUseCase.class);
            assertThat(context).hasSingleBean(A2ACancellationReliabilityUseCase.class);
            assertThat(context).hasSingleBean(A2ACancellationRuntimeUseCase.class);
            assertThat(context).hasSingleBean(A2ALateResultGovernanceUseCase.class);
            assertThat(context).hasSingleBean(A2AOperationsWorkspaceUseCase.class);
            assertThat(context).hasSingleBean(A2AUnifiedReconciliationUseCase.class);
            assertThat(context).hasSingleBean(A2ACutoverUseCase.class);
            assertThat(context).hasSingleBean(HandoffContextService.class);
        });
    }

    @Test
    void canonicalServicesAreTheOnlyInboundPortImplementations() {
        runner.run(context -> {
            assertThat(context.getBean(A2AGovernanceUseCase.class)).isInstanceOf(A2AGovernanceService.class);
            assertThat(context.getBean(A2AResultAcceptanceUseCase.class)).isInstanceOf(A2AResultAcceptanceService.class);
            assertThat(context.getBean(A2AResultReliabilityUseCase.class)).isInstanceOf(A2AResultReliabilityService.class);
            assertThat(context.getBean(A2ACancellationUseCase.class)).isInstanceOf(A2ACancellationService.class);
            assertThat(context.getBean(A2ACancellationReliabilityUseCase.class)).isInstanceOf(A2ACancellationService.class);
            assertThat(context.getBean(A2ALateResultGovernanceUseCase.class)).isInstanceOf(A2ALateResultGovernanceService.class);
            assertThat(context.getBean(A2AOperationsWorkspaceUseCase.class)).isInstanceOf(A2AOperationsWorkspaceService.class);
            assertThat(context.getBean(A2AUnifiedReconciliationUseCase.class)).isInstanceOf(A2AUnifiedReconciliationService.class);
            assertThat(context.getBean(A2ACutoverUseCase.class)).isInstanceOf(A2ACutoverService.class);
        });
    }
}
