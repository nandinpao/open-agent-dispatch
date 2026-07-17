package com.opensocket.aievent.core.routing.governance.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.routing.governance.RequirementResolutionMode;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

class GenericCandidateAgentProviderStandardFlowTest {

    @Test
    void explicitFlowCandidatesComeOnlyFromFlowAgentAssignments() {
        GenericCandidateAgentRepository candidates = mock(GenericCandidateAgentRepository.class);
        AgentDirectoryFacade directory = mock(AgentDirectoryFacade.class);
        RoutingProperties properties = new RoutingProperties();
        properties.setMaxCandidates(20);
        GenericCandidateAgentProvider provider = new GenericCandidateAgentProvider(
                candidates, directory, properties);
        when(candidates.findExplicitFlowAgentIds("tenant-stage1", "flow-stage1", "EXTERNAL", 20))
                .thenReturn(List.of("agent-stage1"));
        AgentSnapshot runtime = new AgentSnapshot();
        runtime.setAgentId("agent-stage1");
        when(directory.findById("agent-stage1")).thenReturn(Optional.of(runtime));

        Map<String, GenericCandidateAgent> result = provider.provide(task(), requirement(), List.of());

        assertThat(result).containsOnlyKeys("agent-stage1");
        assertThat(result.get("agent-stage1").getRuntime()).isSameAs(runtime);
        assertThat(result.get("agent-stage1").getOrigins())
                .containsExactly(CandidatePoolOrigin.EXPLICIT_FLOW_ASSIGNMENT);
        verify(candidates).findExplicitFlowAgentIds("tenant-stage1", "flow-stage1", "EXTERNAL", 20);
    }

    private TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTenantId("tenant-stage1");
        task.setTaskId("task-stage1");
        task.setEventStage("EXTERNAL");
        return task;
    }

    private TaskRequirementEvidence requirement() {
        TaskRequirementEvidence evidence = new TaskRequirementEvidence();
        evidence.setTenantId("tenant-stage1");
        evidence.setTaskId("task-stage1");
        evidence.setMatchedFlowId("flow-stage1");
        evidence.setSourceSystem("SRC_STAGE1_RANDOM");
        evidence.setResolutionMode(RequirementResolutionMode.NONE);
        evidence.setCandidatePoolMode(CandidatePoolMode.EXPLICIT_FLOW_AGENTS);
        return evidence;
    }
}
