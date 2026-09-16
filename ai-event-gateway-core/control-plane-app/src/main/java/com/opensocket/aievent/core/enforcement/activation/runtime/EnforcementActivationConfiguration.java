package com.opensocket.aievent.core.enforcement.activation.runtime;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.opensocket.aievent.core.enforcement.activation.application.AuthorityRevisionSynchronizationService;
import com.opensocket.aievent.core.enforcement.activation.application.ClusterSnapshotStatusService;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanService;
import com.opensocket.aievent.core.enforcement.activation.application.CutoverPlanValidator;
import com.opensocket.aievent.core.enforcement.activation.application.SnapshotBootstrapService;
import com.opensocket.aievent.core.enforcement.activation.application.SnapshotRefreshService;
import com.opensocket.aievent.core.enforcement.activation.application.TaskReadCertificationService;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadModelRepository;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotGateService;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotReadFacade;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotRepository;
import com.opensocket.aievent.core.enforcement.activation.application.Wave0ReadPilotService;
import com.opensocket.aievent.core.enforcement.activation.contract.HardGuardDecision;
import com.opensocket.aievent.core.enforcement.activation.contract.HardSecurityGuard;
import com.opensocket.aievent.core.enforcement.activation.contract.Wave0ReadPilotEntryPoint;
import com.opensocket.aievent.core.enforcement.activation.core.UnifiedEnforcementKernel;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcWave0ReadModelRepository;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcWave0ReadPilotRepository;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotFactory;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotLoader;
import com.opensocket.aievent.core.enforcement.activation.core.RevisionedAuthorityRouter;
import com.opensocket.aievent.core.enforcement.activation.core.StableCohortHasher;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcAuthorityActivationTargetRepository;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcAuthorityRevisionPublisher;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcAuthoritySnapshotRepository;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcCutoverPlanRepository;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcReadinessEvidenceRepository;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcSnapshotRefreshStatusRepository;
import com.opensocket.aievent.core.enforcement.activation.persistence.JdbcTaskReadCertificationRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "aeg.enforcement-activation",
        name = "control-plane-enabled",
        havingValue = "true")
public class EnforcementActivationConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnforcementActivationConfiguration.class);

    @Bean
    AuthoritySnapshotFactory authoritySnapshotFactory() {
        return new AuthoritySnapshotFactory();
    }

    @Bean
    RevisionedAuthorityRouter revisionedAuthorityRouter(AuthoritySnapshotFactory factory) {
        return new RevisionedAuthorityRouter(factory, new StableCohortHasher(), 20);
    }

    @Bean
    HardSecurityGuard wave0ReadHardSecurityGuard() {
        return context -> {
            if ("*".equals(context.tenantId())) return HardGuardDecision.deny("WILDCARD_TENANT_DENIED");
            return HardGuardDecision.allow("IAM_RLS_AND_ROUTE_CONTEXT_GUARDED");
        };
    }

    @Bean
    UnifiedEnforcementKernel unifiedEnforcementKernel(
            HardSecurityGuard wave0ReadHardSecurityGuard,
            RevisionedAuthorityRouter router) {
        return new UnifiedEnforcementKernel(wave0ReadHardSecurityGuard, router);
    }

    @Bean
    JdbcAuthoritySnapshotRepository authoritySnapshotRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions) {
        return new JdbcAuthoritySnapshotRepository(jdbc, transactions);
    }

    @Bean
    AuthoritySnapshotLoader authoritySnapshotLoader(
            JdbcAuthoritySnapshotRepository repository,
            RevisionedAuthorityRouter router) {
        return new AuthoritySnapshotLoader(repository, router);
    }

    @Bean("enforcementActivationTransactionTemplate")
    TransactionTemplate enforcementActivationTransactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    JdbcCutoverPlanRepository cutoverPlanRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions) {
        return new JdbcCutoverPlanRepository(jdbc, transactions);
    }

    @Bean
    JdbcReadinessEvidenceRepository readinessEvidenceRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions) {
        return new JdbcReadinessEvidenceRepository(jdbc, transactions);
    }

    @Bean
    JdbcAuthorityRevisionPublisher authorityRevisionPublisher(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions,
            AuthoritySnapshotFactory factory) {
        return new JdbcAuthorityRevisionPublisher(jdbc, transactions, factory);
    }

    @Bean
    JdbcAuthorityActivationTargetRepository authorityActivationTargetRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions) {
        return new JdbcAuthorityActivationTargetRepository(jdbc, transactions);
    }

    @Bean
    JdbcSnapshotRefreshStatusRepository snapshotRefreshStatusRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions,
            @Value("${aeg.enforcement-activation.node-id:${HOSTNAME:local}}") String nodeId) {
        return new JdbcSnapshotRefreshStatusRepository(jdbc, transactions, nodeId);
    }

    @Bean
    JdbcWave0ReadPilotRepository wave0ReadPilotRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions) {
        return new JdbcWave0ReadPilotRepository(jdbc, transactions);
    }

    @Bean
    JdbcWave0ReadModelRepository wave0ReadModelRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions,
            Clock clock,
            @Value("${aeg.enforcement-activation.cluster-node-stale-after:PT15S}") Duration staleAfter) {
        return new JdbcWave0ReadModelRepository(jdbc, transactions, clock, staleAfter);
    }

    @Bean
    CutoverPlanValidator cutoverPlanValidator(Clock clock) {
        return new CutoverPlanValidator(clock);
    }

    @Bean
    SnapshotRefreshService snapshotRefreshService(
            AuthoritySnapshotLoader loader,
            RevisionedAuthorityRouter router,
            JdbcSnapshotRefreshStatusRepository repository,
            Clock clock) {
        return new SnapshotRefreshService(loader, router, repository, clock);
    }

    @Bean
    SnapshotBootstrapService snapshotBootstrapService(
            AuthoritySnapshotLoader loader,
            RevisionedAuthorityRouter router,
            JdbcSnapshotRefreshStatusRepository repository,
            Clock clock) {
        return new SnapshotBootstrapService(loader, router, repository, clock);
    }

    @Bean
    AuthorityRevisionSynchronizationService authorityRevisionSynchronizationService(
            JdbcAuthorityActivationTargetRepository targets,
            SnapshotRefreshService refresh,
            RevisionedAuthorityRouter router) {
        return new AuthorityRevisionSynchronizationService(targets, refresh, router);
    }

    @Bean
    ClusterSnapshotStatusService clusterSnapshotStatusService(
            JdbcAuthorityActivationTargetRepository targets,
            JdbcSnapshotRefreshStatusRepository statuses,
            SnapshotRefreshService refresh,
            Clock clock,
            @Value("${aeg.enforcement-activation.cluster-node-stale-after:PT15S}") Duration staleAfter) {
        return new ClusterSnapshotStatusService(targets, statuses, refresh, clock, staleAfter);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "aeg.enforcement-activation",
            name = "revision-sync-enabled",
            havingValue = "true")
    ScheduledAuthorityRevisionSynchronizer scheduledAuthorityRevisionSynchronizer(
            AuthorityRevisionSynchronizationService synchronization,
            @Value("${aeg.enforcement-activation.node-id:${HOSTNAME:local}}") String nodeId) {
        return new ScheduledAuthorityRevisionSynchronizer(synchronization, nodeId);
    }

    @Bean
    ApplicationRunner enforcementActivationSnapshotBootstrap(
            SnapshotBootstrapService bootstrap,
            AuthorityRevisionSynchronizationService synchronization,
            @Value("${aeg.enforcement-activation.node-id:${HOSTNAME:local}}") String nodeId,
            @Value("${aeg.enforcement-activation.revision-sync-enabled:false}") boolean revisionSyncEnabled) {
        return arguments -> {
            String correlation = "phase6c0-bootstrap-" + nodeId + "-" + UUID.randomUUID();
            try {
                bootstrap.bootstrap("system:phase6c0-bootstrap:" + nodeId, correlation);
                if (revisionSyncEnabled) {
                    synchronization.synchronize(
                            "system:phase6c0-bootstrap-sync:" + nodeId,
                            correlation + "-sync");
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Phase 6C-0 authority snapshot bootstrap failed; the local router remains fail-safe Legacy. nodeId={} correlationId={}",
                        nodeId,
                        correlation,
                        exception);
            }
        };
    }

    @Bean
    Wave0ReadPilotGateService wave0ReadPilotGateService(
            Wave0ReadPilotRepository repository,
            Clock clock,
            @Value("${aeg.enforcement-activation.wave0-read-pilot-enabled:false}") boolean enabled,
            @Value("${aeg.enforcement-activation.task-list-search-pilot-enabled:false}") boolean taskPilotEnabled) {
        Set<Wave0ReadPilotEntryPoint> additional = taskPilotEnabled
                ? Set.of(Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH) : Set.of();
        return new Wave0ReadPilotGateService(repository, clock, enabled, additional);
    }

    @Bean
    Wave0ReadPilotService wave0ReadPilotService(
            UnifiedEnforcementKernel kernel,
            Wave0ReadPilotRepository repository,
            Wave0ReadPilotGateService gates,
            Clock clock,
            @Value("${aeg.enforcement-activation.wave0-read-pilot-enabled:false}") boolean enabled,
            @Value("${aeg.enforcement-activation.task-list-search-pilot-enabled:false}") boolean taskPilotEnabled) {
        Set<Wave0ReadPilotEntryPoint> additional = taskPilotEnabled
                ? Set.of(Wave0ReadPilotEntryPoint.TASK_LIST_SEARCH) : Set.of();
        return new Wave0ReadPilotService(kernel, repository, gates, clock, enabled, additional);
    }

    @Bean
    Wave0ReadPilotReadFacade wave0ReadPilotReadFacade(
            Wave0ReadPilotService pilot,
            Wave0ReadModelRepository reads) {
        return new Wave0ReadPilotReadFacade(pilot, reads);
    }

    @Bean
    JdbcTaskReadCertificationRepository taskReadCertificationRepository(
            JdbcTemplate jdbc,
            @Qualifier("enforcementActivationTransactionTemplate") TransactionTemplate transactions) {
        return new JdbcTaskReadCertificationRepository(jdbc, transactions);
    }

    @Bean
    TaskReadCertificationService taskReadCertificationService(
            JdbcTaskReadCertificationRepository certifications,
            Wave0ReadPilotRepository pilot,
            Clock clock) {
        return new TaskReadCertificationService(certifications, pilot, clock);
    }

    @Bean
    CutoverPlanService cutoverPlanService(
            JdbcCutoverPlanRepository plans,
            JdbcReadinessEvidenceRepository evidence,
            JdbcAuthorityRevisionPublisher publisher,
            SnapshotRefreshService refresh,
            CutoverPlanValidator validator,
            Clock clock) {
        return new CutoverPlanService(plans, evidence, publisher, refresh, validator, clock);
    }
}
