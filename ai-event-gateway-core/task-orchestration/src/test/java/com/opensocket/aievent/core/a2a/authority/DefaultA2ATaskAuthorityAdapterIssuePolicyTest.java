package com.opensocket.aievent.core.a2a.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.a2a.core.port.TaskAuthorityPort.CreateChildTaskRequest;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.task.InMemoryTaskRepository;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.TaskDomainService;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;

class DefaultA2ATaskAuthorityAdapterIssuePolicyTest {

    @Test
    void a2aChildExplicitlyInheritsRequiredIssuePolicyWithoutFabricatingFlowAuthority() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskRecord parent = parent(tasks, TaskIssueSyncPolicy.REQUIRED, "FLOW_DEFAULT");
        DefaultA2ATaskAuthorityAdapter adapter = adapter(tasks);

        var receipt = adapter.createChildTask(command(parent, "a2a-req-required", "idem-required"));
        TaskRecord child = tasks.findById(receipt.childTaskId()).orElseThrow();

        assertThat(child.getIssueSyncPolicy()).isEqualTo(TaskIssueSyncPolicy.REQUIRED);
        assertThat(child.getIssueSyncPolicySource()).isEqualTo("A2A_PARENT_INHERITED");
        assertThat(child.getIssueSyncPolicyInheritanceMode()).isEqualTo("A2A_PARENT");
        assertThat(child.getIssueSyncPolicyInheritedFromTaskId()).isEqualTo(parent.getTaskId());
        assertThat(child.getMatchedFlowId()).isNull();
        assertThat(child.getMatchedRuleId()).isNull();
        assertThat(child.getRoutingPath()).isEqualTo("A2A_POLICY_TO_AGENT_POOL");
    }

    @Test
    void a2aChildInheritsOptionalPolicyExplicitlyRatherThanRelyingOnTaskDefault() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskRecord parent = parent(tasks, TaskIssueSyncPolicy.OPTIONAL, "RULE_OVERRIDE");
        DefaultA2ATaskAuthorityAdapter adapter = adapter(tasks);

        var receipt = adapter.createChildTask(command(parent, "a2a-req-optional", "idem-optional"));
        TaskRecord child = tasks.findById(receipt.childTaskId()).orElseThrow();

        assertThat(child.getIssueSyncPolicy()).isEqualTo(TaskIssueSyncPolicy.OPTIONAL);
        assertThat(child.getIssueSyncPolicySource()).isEqualTo("A2A_PARENT_INHERITED");
        assertThat(child.getIssueSyncPolicyInheritedFromTaskId()).isEqualTo(parent.getTaskId());
    }

    @Test
    void idempotentReplayPreservesChildIssuePolicyAuthority() {
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        TaskRecord parent = parent(tasks, TaskIssueSyncPolicy.REQUIRED, "RULE_OVERRIDE");
        DefaultA2ATaskAuthorityAdapter adapter = adapter(tasks);
        CreateChildTaskRequest command = command(parent, "a2a-req-replay", "idem-replay");

        var first = adapter.createChildTask(command);
        var replay = adapter.createChildTask(command);
        TaskRecord child = tasks.findById(first.childTaskId()).orElseThrow();

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.childTaskId()).isEqualTo(first.childTaskId());
        assertThat(child.getIssueSyncPolicy()).isEqualTo(TaskIssueSyncPolicy.REQUIRED);
        assertThat(child.getIssueSyncPolicySource()).isEqualTo("A2A_PARENT_INHERITED");
        assertThat(child.getIssueSyncPolicyInheritanceMode()).isEqualTo("A2A_PARENT");
    }

    private static DefaultA2ATaskAuthorityAdapter adapter(InMemoryTaskRepository tasks) {
        return new DefaultA2ATaskAuthorityAdapter(
                tasks,
                mock(TaskDomainService.class),
                mock(TaskAssignmentRepository.class));
    }

    private static TaskRecord parent(InMemoryTaskRepository tasks, TaskIssueSyncPolicy policy, String policySource) {
        TaskRecord parent = new TaskRecord();
        parent.setTaskId("task-parent-" + policy.name().toLowerCase());
        parent.setTaskKey("PARENT-" + policy.name());
        parent.setTenantId("tenant-hf6");
        parent.setStatus(TaskStatus.RUNNING);
        parent.setSourceSystem("MES");
        parent.setExecutorDomainId("MES");
        parent.setMatchedFlowId("flow-parent");
        parent.setMatchedRuleId("rule-parent");
        parent.setIssueSyncPolicy(policy);
        parent.setIssueSyncPolicySource(policySource);
        parent.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
        parent.setUpdatedAt(parent.getCreatedAt());
        return tasks.save(parent);
    }

    private static CreateChildTaskRequest command(TaskRecord parent, String requestId, String idempotencyKey) {
        return new CreateChildTaskRequest(
                parent.getTenantId(), requestId, parent.getTaskId(), parent.getRootTaskId(), parent.getTaskId(),
                "agent-requester", "dept-target", "group-target", "ERP", "pool-erp", "A2A_RESOLUTION",
                "svc-a2a", List.of("cap-a2a"), "INTERNAL", 1, "policy-a2a", "ctx-policy",
                "OPTIONAL", "MANUAL_REVIEW", "MANUAL_DECISION", "WAIT_HUMAN", "HF6 contract test",
                idempotencyKey, "corr-hf6");
    }
}
