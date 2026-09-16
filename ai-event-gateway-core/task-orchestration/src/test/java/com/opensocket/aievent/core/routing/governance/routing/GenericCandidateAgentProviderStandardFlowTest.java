package com.opensocket.aievent.core.routing.governance.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.routing.governance.RequirementResolutionMode;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

class GenericCandidateAgentProviderStandardFlowTest {

    @Test
    void currentSourceFlowCandidatesComeOnlyFromAgentPoolMembership() {
        GenericCandidateAgentRepository legacyCandidates = mock(GenericCandidateAgentRepository.class);
        AgentPoolRoutingRepository pools = mock(AgentPoolRoutingRepository.class);
        AgentDirectoryFacade directory = mock(AgentDirectoryFacade.class);
        RoutingProperties properties = new RoutingProperties();
        properties.setMaxCandidates(20);
        GenericCandidateAgentProvider provider = new GenericCandidateAgentProvider(
                legacyCandidates, pools, directory, properties);

        AgentPoolRoutingMember member = new AgentPoolRoutingMember();
        member.setTenantId("tenant-stage1");
        member.setPoolId("pool-stage1");
        member.setAgentId("agent-stage1");
        member.setMemberStatus("ACTIVE");
        AgentPoolRoutingSnapshot pool = new AgentPoolRoutingSnapshot();
        pool.setTenantId("tenant-stage1");
        pool.setPoolId("pool-stage1");
        pool.setPoolCode("POOL_STAGE1");
        pool.setSelectionStrategy("LOWEST_LOAD");
        pool.setMembers(List.of(member));
        when(pools.findActivePool("tenant-stage1", "pool-stage1")).thenReturn(Optional.of(pool));

        AgentSnapshot runtime = new AgentSnapshot();
        runtime.setAgentId("agent-stage1");
        when(directory.findById("agent-stage1")).thenReturn(Optional.of(runtime));

        Map<String, GenericCandidateAgent> result = provider.provide(task(), requirement(), List.of());

        assertThat(result).containsOnlyKeys("agent-stage1");
        assertThat(result.get("agent-stage1").getRuntime()).isSameAs(runtime);
        assertThat(result.get("agent-stage1").getOrigins())
                .containsExactly(CandidatePoolOrigin.AGENT_POOL_MEMBERSHIP);
        verify(pools).findActivePool("tenant-stage1", "pool-stage1");
        verifyNoInteractions(legacyCandidates);
    }

    private TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTenantId("tenant-stage1");
        task.setTaskId("task-stage1");
        task.setEventStage("EXTERNAL");
        task.setTargetPoolId("pool-stage1");
        return task;
    }

    private TaskRequirementEvidence requirement() {
        TaskRequirementEvidence evidence = new TaskRequirementEvidence();
        evidence.setTenantId("tenant-stage1");
        evidence.setTaskId("task-stage1");
        evidence.setMatchedFlowId("flow-stage1");
        evidence.setSourceSystem("SRC_STAGE1_RANDOM");
        evidence.setResolutionMode(RequirementResolutionMode.NONE);
        evidence.setCandidatePoolMode(CandidatePoolMode.SOURCE_SYSTEM_POOL);
        evidence.setEvidence(Map.of("targetPoolId", "pool-stage1"));
        return evidence;
    }
}
