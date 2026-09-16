package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "integration-identity.store=MEMORY",
        "task-issue-link.store=MEMORY",
        "handoff-context.store=MEMORY",
        "issue-projection.store=MEMORY",
        "integration-sync.store=MEMORY",
        "issue-external-change.store=MEMORY",
        "issue-relay.store=MEMORY",
        "issue-projection-recovery.store=MEMORY",
        "phase3.release.runtime-certified=false",
        "phase3.release.postgresql-certified=false",
        "phase3.release.worker-chaos-certified=false",
        "phase3.release.admin-ui-certified=false",
        "phase3.release.jira-live-certified=false",
        "phase3.release.redmine-live-certified=false",
        "phase3.release.cross-provider-relay-certified=false",
        "phase3.release.evidence-ledger-persisted=false",
        "phase3.release.signed-evidence=false"
})
class Phase3JOperationsRuntimeSmokeTest {
    @Autowired TestRestTemplate rest;

    @Test
    void applicationContextMustExposeFailClosedReadiness() {
        ResponseEntity<Map> response = rest.getForEntity("/api/integrations/phase3-release-readiness", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String,Object> body = response.getBody();
        assertThat(body).isNotNull();
        Object raw = body.containsKey("data") ? body.get("data") : body;
        assertThat(raw).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked") Map<String,Object> readiness = (Map<String,Object>) raw;
        assertThat(readiness).containsEntry("status", "NOT_READY").containsEntry("productionReady", false);
    }
}
