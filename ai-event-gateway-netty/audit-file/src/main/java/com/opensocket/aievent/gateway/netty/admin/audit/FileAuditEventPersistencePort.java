package com.opensocket.aievent.gateway.netty.admin.audit;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FILE audit adapter that appends Admin events as JSON Lines.
 *
 * <p>The adapter is intentionally simple and local-node scoped. It is suitable for development,
 * smoke tests, and deployments that ship logs with a host-level log collector. Enterprise-grade
 * audit retention should still use a durable JDBC, Kafka, or SIEM sink in a later phase.</p>
 */
public class FileAuditEventPersistencePort implements AuditEventPersistencePort {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AuditLogProperties auditLogProperties;
    private final ObjectMapper objectMapper;

    public FileAuditEventPersistencePort(AuditLogProperties auditLogProperties, ObjectMapper objectMapper) {
        this.auditLogProperties = auditLogProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void persist(AdminEventPayload event) {
        if (event == null) {
            return;
        }
        try {
            var directory = Path.of(auditLogProperties.spoolDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            var occurredAt = event.occurredAt() == null ? java.time.OffsetDateTime.now(ZoneOffset.UTC) : event.occurredAt();
            var file = directory.resolve("admin-audit-" + FILE_DATE.format(occurredAt.atZoneSameInstant(ZoneOffset.UTC)) + ".jsonl");
            var line = objectMapper.writeValueAsString(toJsonLine(event)) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to append Admin audit event to FILE sink", ex);
        }
    }

    private Map<String, Object> toJsonLine(AdminEventPayload event) {
        var line = new LinkedHashMap<String, Object>();
        line.put("eventId", event.eventId());
        line.put("nodeId", event.nodeId());
        line.put("eventType", event.eventType());
        line.put("message", event.message());
        line.put("data", event.data() == null ? Map.of() : event.data());
        line.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        return line;
    }
}
