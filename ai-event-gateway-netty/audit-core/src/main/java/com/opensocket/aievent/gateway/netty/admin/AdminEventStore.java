package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.audit.AuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.config.AdminProperties;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import com.opensocket.aievent.gateway.netty.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin API component for Admin Event Store. It aggregates gateway, agent, task, cluster, and
 * event-store data for operational dashboards.
 */
public class AdminEventStore {

    private static final Logger log = LoggerFactory.getLogger(AdminEventStore.class);

    private final GatewayProperties gatewayProperties;
    private final AdminProperties adminProperties;
    private final AuditLogProperties auditLogProperties;
    private final AuditEventPersistencePort auditEventPersistencePort;
    private final ArrayDeque<AdminEventPayload> events = new ArrayDeque<>();

    public AdminEventStore(
            GatewayProperties gatewayProperties,
            AdminProperties adminProperties,
            AuditLogProperties auditLogProperties,
            AuditEventPersistencePort auditEventPersistencePort
    ) {
        this.gatewayProperties = gatewayProperties;
        this.adminProperties = adminProperties;
        this.auditLogProperties = auditLogProperties;
        this.auditEventPersistencePort = auditEventPersistencePort;
    }

    public synchronized AdminEventPayload append(String eventType, String message, Map<String, Object> data) {
        var event = new AdminEventPayload(
                UUID.randomUUID().toString(),
                gatewayProperties.nodeId(),
                eventType,
                message,
                data == null ? Map.of() : Map.copyOf(data),
                OffsetDateTime.now()
        );
        events.addFirst(event);
        trim();
        persistAuditEvent(event);
        return event;
    }

    public synchronized List<AdminEventPayload> recent(int limit) {
        int safeLimit = limit <= 0 ? adminProperties.recentEventLimit() : Math.min(limit, adminProperties.recentEventLimit());
        var result = new ArrayList<AdminEventPayload>(safeLimit);
        int count = 0;
        for (AdminEventPayload event : events) {
            if (count >= safeLimit) {
                break;
            }
            result.add(event);
            count++;
        }
        return List.copyOf(result);
    }

    public synchronized Optional<AdminEventPayload> findById(String eventId) {
        return events.stream()
                .filter(event -> event.eventId().equals(eventId))
                .findFirst();
    }

    public synchronized long count() {
        return events.size();
    }

    public int limit() {
        return adminProperties.recentEventLimit();
    }

    private void trim() {
        while (events.size() > adminProperties.recentEventLimit()) {
            events.removeLast();
        }
    }

    private void persistAuditEvent(AdminEventPayload event) {
        if (!auditLogProperties.persistenceEnabled()) {
            return;
        }
        try {
            auditEventPersistencePort.persist(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist Admin audit event. eventId={}, sink={}, reason={}",
                    event.eventId(), auditLogProperties.sink(), ex.getMessage());
        }
    }
}
