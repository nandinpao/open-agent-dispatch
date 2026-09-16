package com.opensocket.aievent.core.routing.governance.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingMember;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingRepository;
import com.opensocket.aievent.core.dispatch.flow.AgentPoolRoutingSnapshot;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.governance.CandidatePoolMode;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Canonical candidate provider for dispatch.
 *
 * <p>V38-7A1 makes Agent Pool membership the candidate authority for current
 * Source Flow and governed-pool routing. Legacy flow_agent_assignments remain
 * readable only for explicit compatibility evidence; they are not used by the
 * current SOURCE_SYSTEM_POOL path.</p>
 */
@Service
public class GenericCandidateAgentProvider implements CandidateAgentProvider {
    private final GenericCandidateAgentRepository legacyRepository;
    private final AgentPoolRoutingRepository agentPoolRepository;
    private final AgentDirectoryFacade agentDirectory;
    private final RoutingProperties properties;

    public GenericCandidateAgentProvider(
            GenericCandidateAgentRepository legacyRepository,
            AgentPoolRoutingRepository agentPoolRepository,
            AgentDirectoryFacade agentDirectory,
            RoutingProperties properties) {
        this.legacyRepository = legacyRepository;
        this.agentPoolRepository = agentPoolRepository;
        this.agentDirectory = agentDirectory;
        this.properties = properties;
    }

    @Override
    public Map<String, GenericCandidateAgent> provide(
            TaskRecord task,
            TaskRequirementEvidence requirement,
            List<AgentSnapshot> ignoredPreviousCandidates) {
        LinkedHashMap<String, GenericCandidateAgent> result = new LinkedHashMap<>();
        if (requirement == null) return result;

        if (requirement.getCandidatePoolMode() == CandidatePoolMode.SOURCE_SYSTEM_POOL) {
            addAgentPoolCandidates(result, task, requirement);
        } else if (requirement.getCandidatePoolMode() == CandidatePoolMode.EXPLICIT_FLOW_AGENTS) {
            // Compatibility-only path for legacy records that have not yet been migrated.
            addLegacyFlowCandidates(result, task, requirement);
        }

        for (GenericCandidateAgent candidate : result.values()) {
            if (candidate.getRuntime() == null) {
                candidate.setRuntime(agentDirectory.findById(candidate.getAgentId()).orElse(null));
            }
        }
        return result;
    }

    private void addAgentPoolCandidates(
            Map<String, GenericCandidateAgent> result,
            TaskRecord task,
            TaskRequirementEvidence requirement) {
        String targetPoolId = targetPoolId(task, requirement);
        if (targetPoolId.isBlank() || agentPoolRepository == null) return;
        AgentPoolRoutingSnapshot pool = agentPoolRepository
                .findActivePool(requirement.getTenantId(), targetPoolId)
                .orElse(null);
        if (pool == null || pool.getMembers() == null) return;
        int limit = properties.getMaxCandidates();
        int count = 0;
        for (AgentPoolRoutingMember member : pool.getMembers()) {
            if (member == null || blank(member.getAgentId())) continue;
            add(result, member.getAgentId(), null, CandidatePoolOrigin.AGENT_POOL_MEMBERSHIP);
            if (++count >= limit) break;
        }
    }

    private void addLegacyFlowCandidates(
            Map<String, GenericCandidateAgent> result,
            TaskRecord task,
            TaskRequirementEvidence requirement) {
        String targetAgentId = explicitTargetAgent(requirement);
        if (!targetAgentId.isBlank()) {
            add(result, targetAgentId, null, CandidatePoolOrigin.EXPLICIT_FLOW_ASSIGNMENT);
            return;
        }
        if (legacyRepository == null) return;
        for (String id : legacyRepository.findExplicitFlowAgentIds(
                requirement.getTenantId(),
                requirement.getMatchedFlowId(),
                task == null ? null : task.getEventStage(),
                properties.getMaxCandidates())) {
            add(result, id, null, CandidatePoolOrigin.EXPLICIT_FLOW_ASSIGNMENT);
        }
    }

    private void add(Map<String, GenericCandidateAgent> result, String id, AgentSnapshot runtime, CandidatePoolOrigin origin) {
        String key = normalizeAgent(id);
        if (key.isBlank()) return;
        GenericCandidateAgent candidate = result.computeIfAbsent(key, GenericCandidateAgent::new);
        candidate.addOrigin(origin);
        if (runtime != null) candidate.setRuntime(runtime);
    }

    private static String targetPoolId(TaskRecord task, TaskRequirementEvidence requirement) {
        if (requirement != null && requirement.getEvidence() != null) {
            Object value = requirement.getEvidence().get("targetPoolId");
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        if (task != null && !blank(task.getTargetPoolId())) return task.getTargetPoolId().trim();
        if (task != null && !blank(task.getAssignedPoolId())) return task.getAssignedPoolId().trim();
        return "";
    }

    private static String explicitTargetAgent(TaskRequirementEvidence requirement) {
        if (requirement == null || requirement.getEvidence() == null) return "";
        Object value = requirement.getEvidence().get("targetAgentId");
        return value == null ? "" : normalizeAgent(String.valueOf(value));
    }

    private static String normalizeAgent(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
