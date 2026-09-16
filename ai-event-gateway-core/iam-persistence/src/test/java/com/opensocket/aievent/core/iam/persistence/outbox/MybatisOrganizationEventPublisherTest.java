package com.opensocket.aievent.core.iam.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.iam.organization.event.OrganizationEvents;
import com.opensocket.aievent.core.iam.persistence.dao.IamOutboxDao;
import com.opensocket.aievent.core.iam.persistence.dao.IamTenantOrganizationDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;

class MybatisOrganizationEventPublisherTest {
    private final IamTenantOrganizationDao organizationDao = mock(IamTenantOrganizationDao.class);
    private final IamOutboxDao outboxDao = mock(IamOutboxDao.class);
    private final MybatisOrganizationEventPublisher publisher = new MybatisOrganizationEventPublisher(
            new IamTransactionalOutboxWriter(outboxDao),
            organizationDao
    );

    @AfterEach
    void clearContext() {
        IamTenantContextHolder.clear();
    }

    @Test
    void authorizationRelevantEventAdvancesSecurityEpoch() {
        when(organizationDao.incrementSecurityEpoch("tenant-a", "user-a")).thenReturn(42L);

        publisher.publish(new OrganizationEvents.DepartmentCreated(
                "event-1", "tenant-a", "department-a", "user-a", "correlation-1", Instant.EPOCH
        ));

        verify(organizationDao).incrementSecurityEpoch("tenant-a", "user-a");
        verify(organizationDao, never()).currentSecurityEpoch(any());
        verify(outboxDao).insert(any());
    }

    @Test
    void revisionEventReusesCurrentEpochWithoutInvalidatingAuthorizationCacheAgain() {
        when(organizationDao.currentSecurityEpoch("tenant-a")).thenReturn(42L);

        publisher.publish(new OrganizationEvents.DepartmentRevisionCreated(
                "event-2", "tenant-a", "department-a", 2L, "MOVED",
                "user-a", "correlation-2", Instant.EPOCH
        ));

        verify(organizationDao).currentSecurityEpoch("tenant-a");
        verify(organizationDao, never()).incrementSecurityEpoch(any(), any());
        verify(outboxDao).insert(any());
    }


    @Test
    void instanceContextTransitionsToNewTenantOnlyForTenantCreatedEvent() {
        when(organizationDao.incrementSecurityEpoch("tenant-a", "root")).thenAnswer(invocation -> {
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("tenant-a");
            assertThat(IamTenantContextHolder.require().actorId()).isEqualTo("root");
            return 1L;
        });

        IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("INSTANCE", "root"),
                () -> {
                    publisher.publish(new OrganizationEvents.TenantCreated(
                            "event-bootstrap-tenant", "tenant-a", "tenant-a", "root",
                            "correlation-bootstrap", Instant.EPOCH
                    ));
                    assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("INSTANCE");
                    return null;
                }
        );

        verify(organizationDao).incrementSecurityEpoch("tenant-a", "root");
        verify(outboxDao).insert(any());
    }

    @Test
    void instanceContextCannotPublishOtherTenantEvents() {
        assertThatThrownBy(() -> IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("INSTANCE", "root"),
                () -> {
                    publisher.publish(new OrganizationEvents.DepartmentCreated(
                            "event-instance-department", "tenant-a", "department-a", "root",
                            "correlation-instance", Instant.EPOCH
                    ));
                    return null;
                }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_CONTEXT_MISMATCH");
    }

    @Test
    void existingContextCannotBeSilentlyReplacedByAnotherTenant() {
        assertThatThrownBy(() -> IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("tenant-b", "user-b"),
                () -> {
                    publisher.publish(new OrganizationEvents.DepartmentCreated(
                            "event-3", "tenant-a", "department-a", "user-a",
                            "correlation-3", Instant.EPOCH
                    ));
                    return null;
                }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_CONTEXT_MISMATCH");
    }
}
