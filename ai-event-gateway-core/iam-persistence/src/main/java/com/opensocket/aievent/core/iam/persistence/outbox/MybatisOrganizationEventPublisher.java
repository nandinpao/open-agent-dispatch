package com.opensocket.aievent.core.iam.persistence.outbox;

import java.util.Set;
import java.util.function.LongSupplier;

import com.opensocket.aievent.core.iam.organization.application.port.out.OrganizationEventPublisher;
import com.opensocket.aievent.core.iam.organization.event.OrganizationDomainEvent;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** Persists organization events and advances security epochs only for authorization-relevant changes. */
@DatabaseRepositoryAdapter
public class MybatisOrganizationEventPublisher implements OrganizationEventPublisher {
    private static final Set<String> SECURITY_EPOCH_EVENTS = Set.of(
            "TENANT_CREATED",
            "TENANT_STATUS_CHANGED",
            "TENANT_MEMBERSHIP_CHANGED",
            "DEPARTMENT_CREATED",
            "DEPARTMENT_MOVED",
            "DEPARTMENT_MEMBERSHIP_CHANGED",
            "GROUP_CREATED",
            "GROUP_MEMBERSHIP_CHANGED"
    );

    private final IamTransactionalOutboxWriter outbox;
    private final IamTenantOrganizationDao organizationDao;

    public MybatisOrganizationEventPublisher(
            IamTransactionalOutboxWriter outbox,
            IamTenantOrganizationDao organizationDao
    ) {
        this.outbox = outbox;
        this.organizationDao = organizationDao;
    }

    @Override
    public void publish(OrganizationDomainEvent event) {
        long epoch = withEventTenantContext(event, () -> SECURITY_EPOCH_EVENTS.contains(event.eventType())
                ? organizationDao.incrementSecurityEpoch(event.tenantId(), event.actorId())
                : organizationDao.currentSecurityEpoch(event.tenantId()));
        outbox.append(
                event.eventId(),
                event.eventType(),
                "IAM_ORGANIZATION",
                event.subjectId(),
                payload(event, epoch),
                event.occurredAt(),
                event.tenantId(),
                event.correlationId(),
                event.actorId()
        );
    }

    private long withEventTenantContext(OrganizationDomainEvent event, LongSupplier operation) {
        return IamTenantContextHolder.current()
                .map(current -> executeWithExistingContext(event, current, operation))
                .orElseGet(() -> IamTenantContextHolder.withContext(
                        new IamTenantExecutionContext(event.tenantId(), event.actorId()),
                        operation::getAsLong
                ));
    }

    private long executeWithExistingContext(
            OrganizationDomainEvent event,
            IamTenantExecutionContext current,
            LongSupplier operation
    ) {
        if (current.tenantId().equals(event.tenantId())) {
            return operation.getAsLong();
        }
        // Tenant creation is the one legitimate INSTANCE-to-Tenant persistence boundary.
        // The Root/Platform request remains instance-authorized while the newly created
        // Tenant receives its first security epoch and organization event under its own
        // RLS context. Every other cross-Tenant event remains fail-closed.
        if ("INSTANCE".equals(current.tenantId()) && "TENANT_CREATED".equals(event.eventType())) {
            return IamTenantContextHolder.withContext(
                    new IamTenantExecutionContext(event.tenantId(), event.actorId()),
                    operation::getAsLong
            );
        }
        throw new IllegalStateException(
                "TENANT_CONTEXT_MISMATCH expected=" + event.tenantId()
                        + " actual=" + current.tenantId()
        );
    }

    private static String payload(OrganizationDomainEvent event, long epoch) {
        return "{\"eventId\":\"" + escape(event.eventId())
                + "\",\"eventType\":\"" + escape(event.eventType())
                + "\",\"tenantId\":\"" + escape(event.tenantId())
                + "\",\"subjectId\":\"" + escape(event.subjectId())
                + "\",\"actorId\":\"" + escape(event.actorId())
                + "\",\"correlationId\":\"" + escape(event.correlationId())
                + "\",\"securityEpoch\":" + epoch
                + ",\"occurredAt\":\"" + event.occurredAt() + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
