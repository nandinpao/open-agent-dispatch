package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.task.TaskRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExecutionAuthorityProvenanceC0A5Test {

    @Test
    void currentA0R7AssignmentMustSnapshotCanonicalAuthorityIntoDispatchRequest() {
        Fixture f = fixture();
        TaskAssignment assignment = assignment("assignment-current");
        assignment.setExecutionAuthorityVersion("A0-R7-V206");
        assignment.setCanonicalExecutionAssignmentId("canonical-current");

        DispatchDecisionResult result = f.service.createIfEligible(assignment, task("task-current"));
        DispatchRequest saved = f.repository.findById(result.dispatchRequestId()).orElseThrow();

        assertThat(saved.getExecutionAuthorityVersion()).isEqualTo("A0-R7-V206");
        assertThat(saved.getCanonicalExecutionAssignmentId()).isEqualTo("canonical-current");
        assertThat(saved.getAuthorityProvenance()).isEqualTo(DispatchAuthorityProvenance.A0_R7_CANONICAL);
    }

    @Test
    void legacyAssignmentMustSnapshotExplicitLegacyProvenance() {
        Fixture f = fixture();
        TaskAssignment assignment = assignment("assignment-legacy");

        DispatchDecisionResult result = f.service.createIfEligible(assignment, task("task-legacy"));
        DispatchRequest saved = f.repository.findById(result.dispatchRequestId()).orElseThrow();

        assertThat(saved.getExecutionAuthorityVersion()).isEqualTo("LEGACY");
        assertThat(saved.getCanonicalExecutionAssignmentId()).isNull();
        assertThat(saved.getAuthorityProvenance()).isEqualTo(DispatchAuthorityProvenance.LEGACY_COMPATIBILITY);
    }

    private Fixture fixture() {
        InMemoryDispatchRequestRepository repository = new InMemoryDispatchRequestRepository();
        DispatchProperties properties = new DispatchProperties();
        properties.setRequireAssignableAgent(false);
        DispatchEligibilityService eligibility = new DispatchEligibilityService(null, properties);
        return new Fixture(repository, new DispatchRequestService(repository, eligibility, properties));
    }

    private TaskAssignment assignment(String id) {
        TaskAssignment a = new TaskAssignment();
        a.setAssignmentId(id);
        a.setTenantId("tenant-a");
        a.setTaskId(id.replace("assignment", "task"));
        a.setAgentId("agent-a");
        a.setOwnerGatewayNodeId("gateway-a");
        a.setAgentSessionId("session-a");
        a.setStatus(AssignmentStatus.ASSIGNED);
        a.setFencingToken("7");
        return a;
    }

    private TaskRecord task(String id) {
        TaskRecord t = new TaskRecord();
        t.setTaskId(id);
        t.setTenantId("tenant-a");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return t;
    }

    private record Fixture(InMemoryDispatchRequestRepository repository, DispatchRequestService service) {}
}
