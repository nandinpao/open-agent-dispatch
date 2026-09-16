package com.opensocket.aievent.core.resourceaccess.persistence;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;

/** Issues opaque, short-lived internal handles; no Provider direct URL or storage reference is returned. */
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "resource-access", name = {"enabled", "attachment-enabled"}, havingValue = "true")
public final class JdbcAttachmentContentGatewayAdapter implements AttachmentContentGatewayPort {
    private final JdbcTemplate jdbc;
    private final Duration ttl;
    private final Clock clock;

    public JdbcAttachmentContentGatewayAdapter(
            JdbcTemplate jdbc,
            @Value("${resource-access.attachment.handle-ttl:2m}") Duration ttl,
            Clock resourceAccessClock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(2) : ttl;
        this.clock = Objects.requireNonNull(resourceAccessClock);
    }

    @Override
    public AttachmentContentHandle createHandle(
            ResourceAttachmentMetadata metadata,
            String decisionId,
            String leaseId,
            Instant leaseExpiresAt) {
        if (!metadata.contentAvailable() || metadata.storageObjectRef().isBlank()) {
            throw new IllegalStateException("ATTACHMENT_CONTENT_UNAVAILABLE");
        }
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        Instant now = clock.instant();
        Instant configuredExpiry = now.plus(ttl);
        Instant expires = configuredExpiry.isBefore(leaseExpiresAt) ? configuredExpiry : leaseExpiresAt;
        if (!expires.isAfter(now)) throw new IllegalStateException("ATTACHMENT_RUNTIME_LEASE_EXPIRED");
        String id = "ach-" + UUID.randomUUID();
        jdbc.update(
                "insert into resource_attachment_content_handles(tenant_id,handle_id,resource_type,resource_id,storage_object_ref,authorization_decision_id,runtime_lease_id,expires_at,created_at) values(?,?,?,?,?,?,?,?,?)",
                metadata.attachmentRef().tenantId(),
                id,
                metadata.attachmentRef().resourceType().name(),
                metadata.attachmentRef().resourceId(),
                metadata.storageObjectRef(),
                decisionId,
                leaseId,
                Timestamp.from(expires),
                Timestamp.from(now));
        return new AttachmentContentHandle(id, expires);
    }
}
