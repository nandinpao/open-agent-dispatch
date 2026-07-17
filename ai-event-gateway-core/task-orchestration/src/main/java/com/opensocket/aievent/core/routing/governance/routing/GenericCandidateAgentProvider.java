package com.opensocket.aievent.core.routing.governance.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Stage 8 direct-dispatch candidate provider.
 *
 * Candidates are read only from Flow Agent Assignments. There is deliberately no
 * source pool, default pool, capability pool, action-grant pool, or fallback path.
 */
@Service
public class GenericCandidateAgentProvider implements CandidateAgentProvider {
    private final GenericCandidateAgentRepository repository;
    private final AgentDirectoryFacade agentDirectory;
    private final RoutingProperties properties;

    public GenericCandidateAgentProvider(
            GenericCandidateAgentRepository repository,
            AgentDirectoryFacade agentDirectory,
            RoutingProperties properties) {
        this.repository = repository;
        this.agentDirectory = agentDirectory;
        this.properties = properties;
    }

    @Override
    public Map<String, GenericCandidateAgent> provide(
            TaskRecord task,
            TaskRequirementEvidence requirement,
            List<AgentSnapshot> ignoredPreviousCandidates) {
        LinkedHashMap<String, GenericCandidateAgent> result = new LinkedHashMap<>();
        if (requirement == null || requirement.getCandidatePoolMode() != CandidatePoolMode.EXPLICIT_FLOW_AGENTS) {
            return result;
        }
        int limit = properties.getMaxCandidates();
        String targetAgentId = explicitTargetAgent(requirement);
        if (!targetAgentId.isBlank()) {
            add(result, targetAgentId, null);
        } else {
            for (String id : repository.findExplicitFlowAgentIds(
                    requirement.getTenantId(),
                    requirement.getMatchedFlowId(),
                    task == null ? null : task.getEventStage(),
                    limit)) {
                add(result, id, null);
            }
        }
        for (GenericCandidateAgent candidate : result.values()) {
            if (candidate.getRuntime() == null) {
                candidate.setRuntime(agentDirectory.findById(candidate.getAgentId()).orElse(null));
            }
        }
        return result;
    }

    private void add(Map<String, GenericCandidateAgent> result, String id, AgentSnapshot runtime) {
        String key = normalizeAgent(id);
        if (key.isBlank()) return;
        GenericCandidateAgent candidate = result.computeIfAbsent(key, GenericCandidateAgent::new);
        candidate.addOrigin(CandidatePoolOrigin.EXPLICIT_FLOW_ASSIGNMENT);
        if (runtime != null) candidate.setRuntime(runtime);
    }

    private static String explicitTargetAgent(TaskRequirementEvidence requirement) {
        if (requirement == null || requirement.getEvidence() == null) return "";
        Object value = requirement.getEvidence().get("targetAgentId");
        return value == null ? "" : normalizeAgent(String.valueOf(value));
    }

    private static String normalizeAgent(String value) {
        return value == null ? "" : value.trim();
    }
}
