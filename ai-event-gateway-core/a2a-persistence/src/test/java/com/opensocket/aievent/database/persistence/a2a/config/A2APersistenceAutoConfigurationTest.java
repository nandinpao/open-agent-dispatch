package com.opensocket.aievent.database.persistence.a2a.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.opensocket.aievent.core.a2a.A2AAggregationEvidenceRepository;
import com.opensocket.aievent.core.a2a.A2ACancellationEvidenceRepository;
import com.opensocket.aievent.core.a2a.A2ACancellationRepository;
import com.opensocket.aievent.core.a2a.A2ACutoverRepository;
import com.opensocket.aievent.core.a2a.A2AIdempotencyRepository;
import com.opensocket.aievent.core.a2a.A2AOperationsReadRepository;
import com.opensocket.aievent.core.a2a.A2AParentAggregationRepository;
import com.opensocket.aievent.core.a2a.A2APolicyRepository;
import com.opensocket.aievent.core.a2a.A2ARateLimitRepository;
import com.opensocket.aievent.core.a2a.A2AReconciliationCaseRepository;
import com.opensocket.aievent.core.a2a.A2AReconciliationEvidenceRepository;
import com.opensocket.aievent.core.a2a.A2ARequestRepository;
import com.opensocket.aievent.core.a2a.A2AResultAttemptRepository;
import com.opensocket.aievent.core.a2a.A2AResultEvidenceRepository;
import com.opensocket.aievent.core.a2a.A2AResultIdempotencyClaimRepository;
import com.opensocket.aievent.core.a2a.A2AResultProcessingRepository;
import com.opensocket.aievent.core.a2a.A2AResultQuarantineRepository;
import com.opensocket.aievent.core.a2a.A2AResultRepository;
import com.opensocket.aievent.core.a2a.A2AStateHistoryRepository;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AAggregationEvidenceDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ACancellationDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ACancellationEvidenceDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ACutoverDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AIdempotencyDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AParentAggregationDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2APolicyDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ARateLimitDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AReconciliationCaseDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AReconciliationEvidenceDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2ARequestDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AResultAttemptDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AResultDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AResultEvidenceDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AResultIdempotencyClaimDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AResultProcessingDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AResultQuarantineDao;
import com.opensocket.aievent.database.persistence.a2a.dao.A2AStateHistoryDao;
import com.opensocket.aievent.database.persistence.a2a.operations.A2AOperationsReadDao;

import tools.jackson.databind.ObjectMapper;

class A2APersistenceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(A2APersistenceAutoConfiguration.class))
            .withPropertyValues("task.store=MYBATIS")
            .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class))
            .withBean(A2AAggregationEvidenceDao.class, () -> mock(A2AAggregationEvidenceDao.class))
            .withBean(A2ACancellationDao.class, () -> mock(A2ACancellationDao.class))
            .withBean(A2ACancellationEvidenceDao.class, () -> mock(A2ACancellationEvidenceDao.class))
            .withBean(A2ACutoverDao.class, () -> mock(A2ACutoverDao.class))
            .withBean(A2AIdempotencyDao.class, () -> mock(A2AIdempotencyDao.class))
            .withBean(A2AParentAggregationDao.class, () -> mock(A2AParentAggregationDao.class))
            .withBean(A2APolicyDao.class, () -> mock(A2APolicyDao.class))
            .withBean(A2ARateLimitDao.class, () -> mock(A2ARateLimitDao.class))
            .withBean(A2AReconciliationCaseDao.class, () -> mock(A2AReconciliationCaseDao.class))
            .withBean(A2AReconciliationEvidenceDao.class, () -> mock(A2AReconciliationEvidenceDao.class))
            .withBean(A2ARequestDao.class, () -> mock(A2ARequestDao.class))
            .withBean(A2AResultAttemptDao.class, () -> mock(A2AResultAttemptDao.class))
            .withBean(A2AResultIdempotencyClaimDao.class, () -> mock(A2AResultIdempotencyClaimDao.class))
            .withBean(A2AResultDao.class, () -> mock(A2AResultDao.class))
            .withBean(A2AResultEvidenceDao.class, () -> mock(A2AResultEvidenceDao.class))
            .withBean(A2AResultProcessingDao.class, () -> mock(A2AResultProcessingDao.class))
            .withBean(A2AResultQuarantineDao.class, () -> mock(A2AResultQuarantineDao.class))
            .withBean(A2AStateHistoryDao.class, () -> mock(A2AStateHistoryDao.class))
            .withBean(A2AOperationsReadDao.class, () -> mock(A2AOperationsReadDao.class));

    @Test
    void registersEveryCanonicalA2ARepositoryWhenMybatisTaskStoreIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(A2ARequestRepository.class);
            assertThat(context).hasSingleBean(A2AAggregationEvidenceRepository.class);
            assertThat(context).hasSingleBean(A2APolicyRepository.class);
            assertThat(context).hasSingleBean(A2AResultRepository.class);
            assertThat(context).hasSingleBean(A2AStateHistoryRepository.class);
            assertThat(context).hasSingleBean(A2AIdempotencyRepository.class);
            assertThat(context).hasSingleBean(A2ARateLimitRepository.class);
            assertThat(context).hasSingleBean(A2AResultAttemptRepository.class);
            assertThat(context).hasSingleBean(A2AResultIdempotencyClaimRepository.class);
            assertThat(context).hasSingleBean(A2AResultEvidenceRepository.class);
            assertThat(context).hasSingleBean(A2AResultQuarantineRepository.class);
            assertThat(context).hasSingleBean(A2AParentAggregationRepository.class);
            assertThat(context).hasSingleBean(A2ACancellationRepository.class);
            assertThat(context).hasSingleBean(A2ACancellationEvidenceRepository.class);
            assertThat(context).hasSingleBean(A2AReconciliationCaseRepository.class);
            assertThat(context).hasSingleBean(A2AReconciliationEvidenceRepository.class);
            assertThat(context).hasSingleBean(A2AResultProcessingRepository.class);
            assertThat(context).hasSingleBean(A2ACutoverRepository.class);
            assertThat(context).hasSingleBean(A2AOperationsReadRepository.class);
        });
    }

    @Test
    void backsOffWhenTaskStoreIsNotMybatis() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(A2APersistenceAutoConfiguration.class))
                .withPropertyValues("task.store=MEMORY")
                .run(context -> assertThat(context).doesNotHaveBean(A2ARequestRepository.class));
    }
}
