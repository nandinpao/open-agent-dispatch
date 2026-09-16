package com.opensocket.aievent.core.iam.persistence.repository;

import static com.opensocket.aievent.core.iam.persistence.repository.RowValues.*;

import com.opensocket.aievent.core.iam.organization.application.port.out.TenantMembershipRepository;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@DatabaseRepositoryAdapter
public class MybatisTenantMembershipRepository implements TenantMembershipRepository {
    private final IamTenantOrganizationDao dao;

    public MybatisTenantMembershipRepository(IamTenantOrganizationDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<TenantMembership> find(TenantId tenantId, PrincipalRef user) {
        return Optional.ofNullable(dao.findTenantMembership(tenantId.value(), user.principalId())).map(this::domain);
    }

    @Override
    public Optional<TenantMembership> findById(TenantId tenantId, MembershipId membershipId) {
        return Optional.ofNullable(dao.findTenantMembershipById(tenantId.value(), membershipId.value())).map(this::domain);
    }

    @Override
    public TenantMembership save(TenantMembership value, long expectedVersion) {
        int rows = expectedVersion == 0
                ? dao.insertTenantMembership(row(value))
                : dao.updateTenantMembership(row(value), expectedVersion);
        if (rows != 1) {
            throw new IamOptimisticLockException(
                    "TenantMembership",
                    value.membershipId().value(),
                    expectedVersion);
        }
        return value;
    }

    @Override
    public void appendEvidence(TenantMembershipEvidence evidence) {
        if (dao.insertTenantMembershipEvidence(evidenceRow(evidence)) != 1) {
            throw new IllegalStateException("TENANT_MEMBERSHIP_EVENT_APPEND_FAILED");
        }
    }

    private TenantMembership domain(Map<String, Object> row) {
        return new TenantMembership(
                new MembershipId(string(row, "membershipId")),
                new TenantId(string(row, "tenantId")),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, string(row, "userId")),
                MembershipStatus.valueOf(string(row, "status")),
                Optional.ofNullable(string(row, "employeeId")),
                instant(row, "joinedAt"),
                Optional.ofNullable(instant(row, "expiresAt")),
                instant(row, "updatedAt"),
                string(row, "createdBy"),
                string(row, "updatedBy"),
                string(row, "statusReason"),
                bool(row, "defaultTenant"),
                TenantMembershipSource.valueOf(string(row, "membershipSource")),
                longValue(row, "version"));
    }

    private Map<String, Object> row(TenantMembership value) {
        Map<String, Object> row = new HashMap<>();
        row.put("tenantId", value.tenantId().value());
        row.put("membershipId", value.membershipId().value());
        row.put("userId", value.userPrincipal().principalId());
        row.put("status", value.status().name());
        row.put("employeeId", value.employeeId().orElse(null));
        row.put("joinedAt", value.joinedAt());
        row.put("expiresAt", value.expiresAt().orElse(null));
        row.put("updatedAt", value.updatedAt());
        row.put("createdBy", value.createdBy());
        row.put("updatedBy", value.updatedBy());
        row.put("statusReason", value.statusReason());
        row.put("defaultTenant", value.defaultTenant());
        row.put("membershipSource", value.membershipSource().name());
        row.put("version", value.version());
        return row;
    }

    private Map<String, Object> evidenceRow(TenantMembershipEvidence value) {
        Map<String, Object> row = new HashMap<>();
        row.put("eventId", value.eventId());
        row.put("tenantId", value.tenantId().value());
        row.put("membershipId", value.membershipId().value());
        row.put("userId", value.userId());
        row.put("previousStatus", value.previousStatus().map(Enum::name).orElse(null));
        row.put("currentStatus", value.currentStatus().name());
        row.put("reason", value.reason());
        row.put("actorId", value.actorId());
        row.put("correlationId", value.correlationId());
        row.put("membershipVersion", value.membershipVersion());
        row.put("occurredAt", value.occurredAt());
        return row;
    }
}
