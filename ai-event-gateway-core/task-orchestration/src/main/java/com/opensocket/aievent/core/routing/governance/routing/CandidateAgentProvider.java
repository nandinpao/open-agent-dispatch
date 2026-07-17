package com.opensocket.aievent.core.routing.governance.routing;

import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

public interface CandidateAgentProvider {
    Map<String, GenericCandidateAgent> provide(TaskRecord task, TaskRequirementEvidence requirement, List<AgentSnapshot> legacyCandidates);
}
