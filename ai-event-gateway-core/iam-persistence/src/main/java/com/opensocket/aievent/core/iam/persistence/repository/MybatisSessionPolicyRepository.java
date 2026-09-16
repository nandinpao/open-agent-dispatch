package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.bool;
import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.intValue;
import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.longValue;

import com.opensocket.aievent.core.iam.authentication.application.port.out.SessionPolicyRepository;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.persistence.dao.IamAuthenticationDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@DatabaseRepositoryAdapter
public class MybatisSessionPolicyRepository implements SessionPolicyRepository {
    private final IamAuthenticationDao dao;

    public MybatisSessionPolicyRepository(IamAuthenticationDao dao) {
        this.dao = dao;
    }

    @Override
    public SessionPolicy instanceMinimum() {
        Map<String, Object> row = dao.findInstanceSessionPolicy();
        return row == null ? SessionPolicy.secureDefault() : toDomain(row);
    }

    @Override
    public Optional<SessionPolicy> findTenantPolicy(String tenantId) {
        return Optional.ofNullable(dao.findTenantSessionPolicy(tenantId)).map(this::toDomain);
    }

    @Override
    public SessionPolicy saveTenantPolicy(
            String tenantId, SessionPolicy policy, String actorId, long expectedVersion) {
        Map<String, Object> row = toRow(tenantId, policy, actorId,
                expectedVersion == 0 ? 1 : expectedVersion + 1);
        int changed = expectedVersion == 0
                ? dao.insertTenantSessionPolicy(row)
                : dao.updateTenantSessionPolicy(row, expectedVersion);
        if (changed != 1) {
            throw new IamOptimisticLockException("TenantSessionPolicy", tenantId, expectedVersion);
        }
        return policy;
    }

    private SessionPolicy toDomain(Map<String, Object> row) {
        return new SessionPolicy(
                Duration.ofSeconds(longValue(row, "idleTimeoutSeconds")),
                Duration.ofSeconds(longValue(row, "absoluteTimeoutSeconds")),
                intValue(row, "maxConcurrentSessions"),
                bool(row, "revokeOnPasswordChange"),
                bool(row, "revokeOnMfaReset"),
                Duration.ofSeconds(longValue(row, "reauthenticationWindowSeconds")));
    }

    private Map<String, Object> toRow(
            String tenantId, SessionPolicy policy, String actorId, long version) {
        Map<String, Object> row = new HashMap<>();
        row.put("tenantId", tenantId);
        row.put("idleTimeoutSeconds", policy.idleTimeout().toSeconds());
        row.put("absoluteTimeoutSeconds", policy.absoluteTimeout().toSeconds());
        row.put("maxConcurrentSessions", policy.maxConcurrentSessions());
        row.put("revokeOnPasswordChange", policy.revokeOnPasswordChange());
        row.put("revokeOnMfaReset", policy.revokeOnMfaReset());
        row.put("reauthenticationWindowSeconds", policy.reauthenticationWindow().toSeconds());
        row.put("updatedAt", Instant.now());
        row.put("updatedBy", actorId);
        row.put("version", version);
        return row;
    }
}
