package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.audit.FileAuditEventPersistencePort;
import com.opensocket.aievent.gateway.netty.admin.dto.AdminEventPayload;
import com.opensocket.aievent.gateway.netty.config.AuditLogProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileAuditEventPersistencePortTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAppendAdminEventAsJsonLine() throws Exception {
        var properties = new AuditLogProperties();
        properties.setSink("FILE");
        properties.setSpoolDirectory(tempDir.toString());
        var adapter = new FileAuditEventPersistencePort(properties, JsonMapper.builder().build());
        var event = new AdminEventPayload(
                "event-001",
                "gateway-node-test",
                "TASK_RETRY_REQUESTED",
                "Retry requested",
                Map.of("taskId", "task-001"),
                OffsetDateTime.parse("2026-06-01T00:00:00Z")
        );

        adapter.persist(event);

        var files = Files.list(tempDir).toList();
        assertThat(files).hasSize(1);
        var content = Files.readString(files.getFirst());
        assertThat(content).contains("event-001");
        assertThat(content).contains("TASK_RETRY_REQUESTED");
        assertThat(content).endsWith(System.lineSeparator());
    }
}
