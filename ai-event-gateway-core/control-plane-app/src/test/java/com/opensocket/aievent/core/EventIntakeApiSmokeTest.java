package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "core.decision.task-creation-enabled=false",
        "event.dedup.store=MEMORY",
        "incident.store=MEMORY",
        "incident.summary.store=MEMORY",
        "event.decisions.store=MEMORY",
        "agent-governance.store=MEMORY"
})
class EventIntakeApiSmokeTest {
    @Autowired
    private TestRestTemplate rest;

    @Test
    void statusAndDuplicateEventIntakeShouldWorkInMemoryMode() {
        ResponseEntity<Map> status = rest.getForEntity("/api/core/status", Map.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> statusData = data(status);
        assertThat(status.getBody()).containsEntry("code", "OK");
        assertThat(statusData).containsEntry("version", "1.0.0-p25.7.4-p5-callback-transition-governance-fix");
        assertThat(statusData).containsEntry("dedupStore", "MEMORY");

        ResponseEntity<Map> first = rest.postForEntity("/api/events/intake", request(), Map.class);
        ResponseEntity<Map> second = rest.postForEntity("/api/events/intake", request(), Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> firstData = data(first);
        Map<String, Object> secondData = data(second);
        assertThat(first.getBody()).containsEntry("code", "OK");
        assertThat(second.getBody()).containsEntry("code", "OK");
        assertThat(secondData).containsEntry("duplicate", true);
        assertThat(((Number) secondData.get("occurrenceCount")).longValue()).isEqualTo(2L);
        assertThat(secondData.get("incidentId")).isEqualTo(firstData.get("incidentId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("code", "message", "data", "timestamp");
        return (Map<String, Object>) response.getBody().get("data");
    }

    private Map<String, Object> request() {
        return Map.of(
                "tenantId", "tenant-a",
                "sourceSystem", "MES",
                "siteId", "TNN",
                "plantId", "TNN-FAB-01",
                "objectType", "EQUIPMENT",
                "objectId", "EQP-1001",
                "eventType", "EQUIPMENT_ALARM",
                "errorCode", "TEMP_HIGH",
                "severity", "HIGH",
                "message", "Chamber temperature over threshold");
    }
}
