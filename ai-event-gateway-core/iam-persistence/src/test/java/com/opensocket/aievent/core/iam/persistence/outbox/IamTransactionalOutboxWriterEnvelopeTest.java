package com.opensocket.aievent.core.iam.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.opensocket.aievent.core.iam.persistence.dao.IamOutboxDao;

class IamTransactionalOutboxWriterEnvelopeTest {

    @Test
    void writesCanonicalCorrelationTenantActorAndLineageColumns() {
        IamOutboxDao dao = mock(IamOutboxDao.class);
        IamTransactionalOutboxWriter writer = new IamTransactionalOutboxWriter(dao);

        writer.append(
                "event-1", "ROOT_IDENTITY_CREATED", "IAM_IDENTITY", "root", "{}", Instant.EPOCH,
                "tenant-a", "correlation-1", "root-installer");

        ArgumentCaptor<Map<String,Object>> captor = mapCaptor();
        verify(dao).insert(captor.capture());
        Map<String,Object> row = captor.getValue();
        assertThat(row.get("payloadVersion")).isEqualTo("1");
        assertThat(row.get("tenantId")).isEqualTo("tenant-a");
        assertThat(row.get("correlationId")).isEqualTo("correlation-1");
        assertThat(row.get("actorType")).isEqualTo("SYSTEM");
        assertThat(row.get("actorId")).isEqualTo("root-installer");
        assertThat(row.get("lineageStatus")).isEqualTo(IamTransactionalOutboxWriter.PROPAGATED);
    }

    @Test
    void missingCorrelationFallsBackToEventIdAndMarksLegacyLineage() {
        IamOutboxDao dao = mock(IamOutboxDao.class);
        IamTransactionalOutboxWriter writer = new IamTransactionalOutboxWriter(dao);

        writer.append("event-legacy", "RBAC_CHANGED", "RBAC", "role-a", "{}", Instant.EPOCH);

        ArgumentCaptor<Map<String,Object>> captor = mapCaptor();
        verify(dao).insert(captor.capture());
        Map<String,Object> row = captor.getValue();
        assertThat(row.get("correlationId")).isEqualTo("event-legacy");
        assertThat(row.get("lineageStatus")).isEqualTo(IamTransactionalOutboxWriter.LEGACY_CORRELATION_FALLBACK);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String,Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
