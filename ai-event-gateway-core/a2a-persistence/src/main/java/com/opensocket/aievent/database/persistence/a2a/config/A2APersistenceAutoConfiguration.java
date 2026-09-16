package com.opensocket.aievent.database.persistence.a2a.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.opensocket.aievent.database.persistence.a2a.A2APersistenceConverter;
import com.opensocket.aievent.database.persistence.a2a.operations.MybatisA2AOperationsReadRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2ACancellationEvidenceRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2ACancellationRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2ACutoverRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AIdempotencyRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AParentAggregationRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AResultProcessingRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AAggregationEvidenceRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2APolicyRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2ARateLimitRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AReconciliationCaseRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AReconciliationEvidenceRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2ARequestRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AResultAttemptRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AResultEvidenceRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AResultIdempotencyClaimRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AResultQuarantineRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AResultRepository;
import com.opensocket.aievent.database.persistence.a2a.repository.MybatisA2AStateHistoryRepository;

/**
 * Registers the canonical A2A MyBatis repository adapters and persistence converter.
 *
 * <p>The executable application is rooted at {@code com.opensocket.aievent.core}, while the
 * persistence implementation intentionally lives under {@code com.opensocket.aievent.database}.
 * Those classes are therefore outside the application's default component scan. This
 * module-owned auto-configuration imports every approved A2A persistence adapter explicitly.</p>
 *
 * <p>MyBatis mapper interfaces continue to be registered by the SharedUtility database scanner
 * through {@code pg.mybatis.base-packages} and {@code pg.mybatis.mapper-scan-packages}. This
 * configuration deliberately does not create a second mapper scanner.</p>
 */
@AutoConfiguration
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
@Import({
        A2APersistenceConverter.class,
        MybatisA2AOperationsReadRepository.class,
        MybatisA2ACancellationEvidenceRepository.class,
        MybatisA2ACancellationRepository.class,
        MybatisA2ACutoverRepository.class,
        MybatisA2AIdempotencyRepository.class,
        MybatisA2AParentAggregationRepository.class,
        MybatisA2AResultProcessingRepository.class,
        MybatisA2AAggregationEvidenceRepository.class,
        MybatisA2APolicyRepository.class,
        MybatisA2ARateLimitRepository.class,
        MybatisA2AReconciliationCaseRepository.class,
        MybatisA2AReconciliationEvidenceRepository.class,
        MybatisA2ARequestRepository.class,
        MybatisA2AResultAttemptRepository.class,
        MybatisA2AResultIdempotencyClaimRepository.class,
        MybatisA2AResultEvidenceRepository.class,
        MybatisA2AResultQuarantineRepository.class,
        MybatisA2AResultRepository.class,
        MybatisA2AStateHistoryRepository.class
})
public class A2APersistenceAutoConfiguration {
}
