package com.opensocket.aievent.core.capability.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.capability.CapabilityRequirement;
import com.opensocket.aievent.core.capability.PlanChildTaskReference;
import com.opensocket.aievent.core.capability.PlanChildTaskRequest;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalPlanChildTaskAdapterIdentityTest {
    private static final String TENANT = "tenant-hf19";
    private static final String PARENT = "task-parent-hf19";
    private static final String CAPABILITY = "inventory.availability.read";

    @Test
    void differentDelegationsUsingSameCapabilityCreateDistinctTaskKeys() {
        Fixture fixture = fixture();

        PlanChildTaskReference first = fixture.adapter.createChildTask(delegation("cap-delegation-11111111-1111-1111-1111-111111111111"));
        PlanChildTaskReference second = fixture.adapter.createChildTask(delegation("cap-delegation-22222222-2222-2222-2222-222222222222"));

        assertNotEquals(first.taskId(), second.taskId());
        assertEquals(2, fixture.children.size());
        assertNotEquals(fixture.children.get(0).getTaskKey(), fixture.children.get(1).getTaskKey());
        assertEquals("CAP-cap-delegation-11111111-1111-1111-1111-111111111111", fixture.children.get(0).getTaskKey());
        assertEquals("CAP-cap-delegation-22222222-2222-2222-2222-222222222222", fixture.children.get(1).getTaskKey());
        assertEquals(CAPABILITY, fixture.children.get(0).getRequiredCapabilities().get(0));
        assertEquals(CAPABILITY, fixture.children.get(1).getRequiredCapabilities().get(0));
    }

    @Test
    void retryingSameDelegationReusesIdempotentChild() {
        Fixture fixture = fixture();
        PlanChildTaskRequest request = delegation("cap-delegation-33333333-3333-3333-3333-333333333333");

        PlanChildTaskReference first = fixture.adapter.createChildTask(request);
        PlanChildTaskReference retry = fixture.adapter.createChildTask(request);

        assertEquals(first.taskId(), retry.taskId());
        assertEquals(1, fixture.children.size());
        assertEquals("cap-child:cap-delegation-33333333-3333-3333-3333-333333333333:delegated-capability:1",
                fixture.children.get(0).getCreationIdempotencyKey());
    }

    @Test
    void differentPlanRunsUsingSameStepAndCapabilityCreateDistinctTaskKeys() {
        Fixture fixture = fixture();

        fixture.adapter.createChildTask(plan("plan-run-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        fixture.adapter.createChildTask(plan("plan-run-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        assertEquals(2, fixture.children.size());
        assertNotEquals(fixture.children.get(0).getTaskKey(), fixture.children.get(1).getTaskKey());
        assertTrue(fixture.children.get(0).getTaskKey().startsWith("PLAN-plan-run-aaaaaaaa"));
        assertTrue(fixture.children.get(1).getTaskKey().startsWith("PLAN-plan-run-bbbbbbbb"));
    }


    @Test
    void capabilityDelegationInheritsResolvedParentIssuePolicyWithoutForgingFlowAuthority() {
        Fixture fixture = fixture(TaskIssueSyncPolicy.REQUIRED, "FLOW_DEFAULT");

        fixture.adapter.createChildTask(delegation("cap-delegation-44444444-4444-4444-4444-444444444444"));

        TaskRecord child = fixture.children.get(0);
        assertEquals(TaskIssueSyncPolicy.REQUIRED, child.getIssueSyncPolicy());
        assertEquals("CAPABILITY_PARENT_INHERITED", child.getIssueSyncPolicySource());
        assertEquals("CAPABILITY_PARENT", child.getIssueSyncPolicyInheritanceMode());
        assertEquals(PARENT, child.getIssueSyncPolicyInheritedFromTaskId());
        assertEquals(null, child.getMatchedFlowId());
        assertEquals(null, child.getMatchedRuleId());
    }

    @Test
    void planStepInheritsResolvedParentIssuePolicyWithPlanProvenance() {
        Fixture fixture = fixture(TaskIssueSyncPolicy.MANUAL, "RULE_OVERRIDE");

        fixture.adapter.createChildTask(plan("plan-run-cccccccc-cccc-cccc-cccc-cccccccccccc"));

        TaskRecord child = fixture.children.get(0);
        assertEquals(TaskIssueSyncPolicy.MANUAL, child.getIssueSyncPolicy());
        assertEquals("PLAN_PARENT_INHERITED", child.getIssueSyncPolicySource());
        assertEquals("PLAN_PARENT", child.getIssueSyncPolicyInheritanceMode());
        assertEquals(PARENT, child.getIssueSyncPolicyInheritedFromTaskId());
    }

    @Test
    void longExecutionScopeStillFitsCanonicalTaskKeyColumn() {
        String longRun = "plan-run-" + "r".repeat(180);
        String longStep = "step-" + "s".repeat(180);
        String key = CanonicalChildTaskKey.create(false, longRun, longStep, 123456);

        assertTrue(key.length() <= 160, () -> "task_key exceeded varchar(160): " + key.length());
        assertEquals(key, CanonicalChildTaskKey.create(false, longRun, longStep, 123456));
    }

    private static PlanChildTaskRequest delegation(String delegationId) {
        return new PlanChildTaskRequest(TENANT, delegationId, "delegated-capability", 1,
                requirement(), List.of(), PARENT, "requester-agent-hf19");
    }

    private static PlanChildTaskRequest plan(String planRunId) {
        return new PlanChildTaskRequest(TENANT, planRunId, "resolve-inventory", 1,
                requirement(), List.of(), PARENT);
    }

    private static CapabilityRequirement requirement() {
        return new CapabilityRequirement(CAPABILITY, "READ", Map.of(), Map.of(),
                "INTERNAL", null, null, null);
    }

    private static Fixture fixture() {
        return fixture(TaskIssueSyncPolicy.OPTIONAL, "SYSTEM_FALLBACK");
    }

    private static Fixture fixture(TaskIssueSyncPolicy issuePolicy, String issuePolicySource) {
        TaskRepository repository = mock(TaskRepository.class);
        TaskRecord parent = new TaskRecord();
        parent.setTaskId(PARENT);
        parent.setTaskKey("PARENT-HF19");
        parent.setTenantId(TENANT);
        parent.setTaskType(TaskType.RESOLUTION);
        parent.setStatus(TaskStatus.COMPLETED);
        parent.setPriority(TaskPriority.MEDIUM);
        parent.setCorrelationId("corr-hf19");
        parent.setRootTaskId(PARENT);
        parent.setIssueSyncPolicy(issuePolicy);
        parent.setIssueSyncPolicySource(issuePolicySource);
        parent.setIssueSyncPolicyInheritanceMode("NONE");
        parent.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        parent.setUpdatedAt(parent.getCreatedAt());

        List<TaskRecord> children = new ArrayList<>();
        when(repository.findByTenantAndId(TENANT, PARENT)).thenReturn(Optional.of(parent));
        when(repository.findByParentTaskId(eq(TENANT), eq(PARENT), anyInt())).thenAnswer(invocation -> List.copyOf(children));
        when(repository.save(any(TaskRecord.class))).thenAnswer(invocation -> {
            TaskRecord child = invocation.getArgument(0, TaskRecord.class);
            children.add(child);
            return child;
        });
        return new Fixture(new CanonicalPlanChildTaskAdapter(repository), children);
    }

    private record Fixture(CanonicalPlanChildTaskAdapter adapter, List<TaskRecord> children) {}
}
