package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTokenDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.token.application.port.out.ServiceAccountCredentialRepository;
import com.opensocket.aievent.core.iam.token.domain.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Instant;
import java.util.*;

@DatabaseRepositoryAdapter
public class MybatisServiceAccountCredentialRepository implements ServiceAccountCredentialRepository {
    private final IamTokenDao dao;

    public MybatisServiceAccountCredentialRepository(IamTokenDao dao) { this.dao = dao; }

    @Override
    public Optional<ServiceAccountCredential> find(String tenantId, ServiceAccountCredentialId credentialId) {
        return Optional.ofNullable(dao.findServiceAccountCredential(tenantId, credentialId.value())).map(this::domain);
    }

    @Override
    public Optional<ServiceAccountCredential> findByClientId(String tenantId, String clientId) {
        return Optional.ofNullable(dao.findServiceAccountCredentialByClientId(tenantId, clientId)).map(this::domain);
    }

    @Override
    public long countUsable(String tenantId, ServiceAccountId serviceAccountId) {
        return dao.countUsableServiceAccountCredentials(tenantId, serviceAccountId.value());
    }

    @Override
    public ServiceAccountCredential save(ServiceAccountCredential credential, long expectedVersion) {
        int updated = expectedVersion == 0
                ? dao.insertServiceAccountCredential(row(credential))
                : dao.updateServiceAccountCredential(row(credential), expectedVersion);
        if (updated != 1) throw new IamOptimisticLockException("ServiceAccountCredential", credential.credentialId().value(), expectedVersion);
        return credential;
    }

    @Override
    public boolean recordUsage(String tenantId, ServiceAccountCredentialId credentialId, Instant usedAt) {
        return dao.recordServiceAccountCredentialUsage(tenantId, credentialId.value(), usedAt) == 1;
    }

    private ServiceAccountCredential domain(Map<String,Object> x) {
        return ServiceAccountCredential.reconstitute(
                string(x,"tenantId"),
                new ServiceAccountCredentialId(string(x,"credentialId")),
                new ServiceAccountId(string(x,"serviceAccountId")),
                ServiceAccountCredentialType.valueOf(string(x,"credentialType")),
                string(x,"credentialName"), string(x,"clientId"), string(x,"last4"),
                new TokenHash(string(x,"hashAlgorithm"), string(x,"secretHash")),
                instant(x,"issuedAt"), instant(x,"expiresAt"), instant(x,"lastUsedAt"),
                string(x,"rotatedFromCredentialId"), instant(x,"rotationGraceExpiresAt"),
                instant(x,"revokedAt"), string(x,"revokedBy"), string(x,"revocationReason"),
                ServiceAccountCredentialStatus.valueOf(string(x,"status")), longValue(x,"useCount"),
                instant(x,"createdAt"), instant(x,"updatedAt"), string(x,"createdBy"),
                string(x,"updatedBy"), longValue(x,"version"));
    }

    private Map<String,Object> row(ServiceAccountCredential c) {
        Map<String,Object> m = new HashMap<>();
        m.put("tenantId", c.tenantId());
        m.put("credentialId", c.credentialId().value());
        m.put("serviceAccountId", c.serviceAccountId().value());
        m.put("credentialType", c.credentialType().name());
        m.put("credentialName", c.name());
        m.put("clientId", c.clientId());
        m.put("last4", c.last4());
        m.put("hashAlgorithm", c.secretHash().algorithm());
        m.put("secretHash", c.secretHash().encoded());
        m.put("issuedAt", c.issuedAt());
        m.put("expiresAt", c.expiresAt());
        m.put("lastUsedAt", c.lastUsedAt());
        m.put("rotatedFromCredentialId", c.rotatedFromCredentialId().isBlank() ? null : c.rotatedFromCredentialId());
        m.put("rotationGraceExpiresAt", c.rotationGraceExpiresAt());
        m.put("revokedAt", c.revokedAt());
        m.put("revokedBy", c.revokedBy().isBlank() ? null : c.revokedBy());
        m.put("revocationReason", c.revocationReason());
        m.put("status", c.status().name());
        m.put("useCount", c.useCount());
        m.put("createdAt", c.createdAt());
        m.put("updatedAt", c.updatedAt());
        m.put("createdBy", c.createdBy());
        m.put("updatedBy", c.updatedBy());
        m.put("version", c.version());
        return m;
    }
}
