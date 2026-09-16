package com.opensocket.aievent.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.CapacityReservationResult;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingService;
import com.opensocket.aievent.core.routing.flow.FlowResolution;
import com.opensocket.aievent.core.routing.flow.FlowResolver;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

/** Regression gate: A2A Policy is a Pool authority; Dispatch must not re-resolve Source Flow. */
class A2AGovernedPoolRoutingTest {

    @Test
    void a2aGovernedPoolMustNotBeRuntimeRepairedBySourceFlow() {
        RoutingProperties properties = properties();
        FlowRuleRoutingService flowRules = mock(FlowRuleRoutingService.class);
        FlowResolver resolver = new FlowResolver(properties, flowRules);
        TaskRecord task = a2aTask("pool-mes");

        FlowResolution resolved = resolver.resolve(task);

        assertThat(resolved.task()).isSameAs(task);
        assertThat(resolved.policy()).isEqualTo(RoutingPolicy.GOVERNED_POOL);
        assertThat(resolver.isGovernedPoolTask(task)).isTrue();
        assertThat(resolver.isAuthoritativePoolTask(task)).isTrue();
        assertThat(task.getTargetPoolId()).isEqualTo("pool-mes");
        verifyNoInteractions(flowRules);
    }

    @Test
    void a2aGovernedPoolMustReachNormalPoolAgentSelectionEvenWhenFlowFallbackIsDisabled() throws Exception {
        AgentSnapshot mesAgent = agent("agent-mes");
        RoutingDecisionService routing = new RoutingDecisionService(
                new TestAgentDirectory(List.of(mesAgent)),
                new InMemoryRoutingDecisionRepository(),
                properties());

        setField(routing, "agentPoolRoutingRepository", poolRepository("pool-mes", "agent-mes"));
        FlowRuleRoutingService flowRules = mock(FlowRuleRoutingService.class);
        setField(routing, "flowRuleRoutingService", flowRules);

        RoutingDecisionRecord decision = routing.decide(a2aTask("pool-mes"));

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getRoutingPolicy()).isEqualTo(RoutingPolicy.GOVERNED_POOL);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-mes");
        assertThat(decision.getDecisionReason()).doesNotContain("NO_ACTIVE_FLOW_RULE");
        verifyNoInteractions(flowRules);
    }

    @Test
    void arbitraryTargetPoolMustNotBypassSourceFlowGovernance() throws Exception {
        AgentSnapshot agent = agent("agent-random");
        RoutingDecisionService routing = new RoutingDecisionService(
                new TestAgentDirectory(List.of(agent)),
                new InMemoryRoutingDecisionRepository(),
                properties());
        setField(routing, "agentPoolRoutingRepository", poolRepository("pool-random", "agent-random"));

        TaskRecord task = baseTask();
        task.setTargetPoolId("pool-random");
        task.setAssignedPoolId("pool-random");
        task.setRoutingPath("CLIENT_SUPPLIED_POOL");

        RoutingDecisionRecord decision = routing.decide(task);

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        assertThat(decision.getDecisionReason()).contains("NO_ACTIVE_FLOW_RULE");
    }

    private RoutingProperties properties() {
        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(0);
        properties.setZeroSpecialCaseRuntimeEnabled(true);
        properties.setFlowRuleRoutingEnabled(true);
        properties.setFlowRuleLegacyFallbackEnabled(false);
        properties.setGenericAuthoritativeEnabled(false);
        return properties;
    }

    private TaskRecord a2aTask(String poolId) {
        TaskRecord task = baseTask();
        task.setSourceSystem("ERP");
        task.setEventStage("A2A");
        task.setTargetSystem("MES");
        task.setA2aPolicyId("policy-erp-mes");
        task.setRoutingPolicy("GOVERNED_POOL");
        task.setRoutingPath("A2A_POLICY_TO_AGENT_POOL");
        task.setTargetPoolId(poolId);
        task.setAssignedPoolId(poolId);
        task.setRequiredCapabilities(List.of("MES_WORK_ORDER_TRACE"));
        return task;
    }

    private TaskRecord baseTask() {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-a2a-child");
        task.setTenantId("tenant-a");
        task.setTaskType(TaskType.RESOLUTION);
        task.setTaskTypeCode("INCIDENT_RESPONSE");
        task.setStatus(TaskStatus.QUEUED);
        task.setSiteId("LOCAL");
        task.setObjectType("WORK_ORDER");
        task.setObjectId("WO-1");
        return task;
    }

    private AgentSnapshot agent(String agentId) {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId(agentId);
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId("gateway-1");
        agent.setAgentSessionId("session-" + agentId);
        agent.setSiteId("LOCAL");
        agent.setStatus(AgentStatus.IDLE);
        agent.setAvailableSlots(2);
        agent.setMaxConcurrentTasks(3);
        agent.setHealthScore(100);
        agent.setCapabilities(List.of("MES_WORK_ORDER_TRACE"));
        agent.setCapabilityProfile(Map.of());
        return agent;
    }

    private AgentPoolRoutingRepository poolRepository(String poolId, String agentId) {
        AgentPoolRoutingMember member = new AgentPoolRoutingMember();
        member.setTenantId("tenant-a");
        member.setPoolId(poolId);
        member.setAgentId(agentId);
        member.setMemberStatus("ACTIVE");

        AgentPoolRoutingSnapshot pool = new AgentPoolRoutingSnapshot();
        pool.setTenantId("tenant-a");
        pool.setPoolId(poolId);
        pool.setPoolCode(poolId.toUpperCase());
        pool.setPoolName(poolId);
        pool.setSelectionStrategy("LOWEST_LOAD");
        pool.setStatus("ACTIVE");
        pool.setMembers(List.of(member));

        return (tenantId, requestedPoolId) ->
                "tenant-a".equals(tenantId) && poolId.equals(requestedPoolId)
                        ? Optional.of(pool)
                        : Optional.empty();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestAgentDirectory implements AgentDirectoryFacade {
        private final List<AgentSnapshot> agents;

        private TestAgentDirectory(List<AgentSnapshot> agents) {
            this.agents = agents;
        }

        @Override
        public Optional<AgentSnapshot> findById(String agentId) {
            return agents.stream().filter(agent -> agentId.equals(agent.getAgentId())).findFirst();
        }

        @Override
        public List<AgentSnapshot> findCandidates(AgentQuery query) {
            return agents;
        }

        @Override
        public CapacityReservationResult reserveCapacity(String agentId) {
            return CapacityReservationResult.rejected(agentId, "not under test");
        }

        @Override
        public boolean releaseCapacity(String agentId) {
            return false;
        }

        @Override
        public String mode() {
            return "A2A_GOVERNED_POOL_TEST";
        }
    }
}
