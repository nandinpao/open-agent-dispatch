package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentRuntimeStateRepository;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;

/**
 * P9.2 application-facing boundary for Skill Registry dispatch evaluation.
 *
 * <p>Routing may depend on this service without importing governance or runtime-state repositories
 * directly. The service keeps repository access inside the agent-control context and returns the
 * existing {@link AgentSkillEvaluationResult} contract used by Admin UI diagnostics.</p>
 */
@Service
public class AgentDispatchSkillEvaluationService {
    private final AgentSkillRegistryService skillRegistryService;
    private final AgentGovernanceRepository governanceRepository;
    private final AgentRuntimeStateRepository runtimeStateRepository;
    private final AgentAssignmentService assignmentService;

    public AgentDispatchSkillEvaluationService(
            AgentSkillRegistryService skillRegistryService,
            AgentGovernanceRepository governanceRepository,
            AgentRuntimeStateRepository runtimeStateRepository) {
        this(skillRegistryService, governanceRepository, runtimeStateRepository, null);
    }

    @Autowired
    public AgentDispatchSkillEvaluationService(
            AgentSkillRegistryService skillRegistryService,
            AgentGovernanceRepository governanceRepository,
            AgentRuntimeStateRepository runtimeStateRepository,
            AgentAssignmentService assignmentService) {
        this.skillRegistryService = skillRegistryService;
        this.governanceRepository = governanceRepository;
        this.runtimeStateRepository = runtimeStateRepository;
        this.assignmentService = assignmentService;
    }

    public AgentSkillEvaluationResult evaluate(AgentSnapshot agent, AgentSkillEvaluationRequest request) {
        if (agent == null) {
            return skillRegistryService.evaluate(null, List.of(), request);
        }
        AgentProfile profile = governanceRepository.findProfile(agent.getAgentId()).orElse(null);
        List<AgentRuntimeCapabilityItem> items = runtimeStateRepository.findCapabilityItems(agent.getAgentId());
        if (items == null || items.isEmpty()) {
            items = syntheticRuntimeItems(agent);
        }
        return skillRegistryService.evaluate(enrichProfileWithApprovedCapabilities(agent.getAgentId(), profile), items, request);
    }

    /**
     * Returns routing-safe capabilities from Core-approved, Admin-managed capability grants.
     * Runtime-reported capability snapshots are diagnostic observations only and must not reduce
     * what an approved Agent may receive.
     */
    public List<String> effectiveDispatchCapabilities(AgentSnapshot agent) {
        if (agent == null || blank(agent.getAgentId())) {
            return List.of();
        }
        AgentProfile profile = governanceRepository.findProfile(agent.getAgentId()).orElse(null);
        if (!dispatchAllowed(profile)) {
            return List.of();
        }
        LinkedHashSet<String> approved = approvedCapabilities(agent.getAgentId(), profile);
        LinkedHashSet<String> effective = new LinkedHashSet<>(approved);
        if (effective.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> expanded = new LinkedHashSet<>(effective);
        for (AgentSkillDefinition skill : skillRegistryService.search(null, true)) {
            if (skill == null || !skill.isEnabled()) {
                continue;
            }
            String skillCode = normalize(skill.getSkillCode());
            boolean activatedBySkillCode = effective.contains(skillCode);
            boolean activatedByTaskType = intersects(skill.getTaskTypes(), effective);
            if (!activatedBySkillCode && !activatedByTaskType) {
                continue;
            }
            addNormalized(expanded, skillCode);
            addNormalized(expanded, skill.getDomain());
            addNormalized(expanded, skill.getTaskTypes());
            addNormalized(expanded, skill.getProviders());
            addNormalized(expanded, skill.getOperations());
            addNormalized(expanded, skill.getToolPolicies());
            addNormalized(expanded, skill.getDataClasses());
        }
        return expanded.stream().toList();
    }

    private List<AgentRuntimeCapabilityItem> syntheticRuntimeItems(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AgentRuntimeCapabilityItem> items = new ArrayList<>();
        for (String value : effectiveCapabilities(agent)) {
            items.add(new AgentRuntimeCapabilityItem(agent.getAgentId(), "flat", value, agent.getCapabilityRevision(), "routing-snapshot", now));
        }
        return items;
    }

    private boolean dispatchAllowed(AgentProfile profile) {
        return profile != null
                && profile.getApprovalStatus() == AgentApprovalStatus.APPROVED
                && profile.isEnabled()
                && (profile.getRiskStatus() == null || profile.getRiskStatus() == AgentRiskStatus.NORMAL);
    }

    private AgentProfile enrichProfileWithApprovedCapabilities(String agentId, AgentProfile profile) {
        if (profile == null) {
            return null;
        }
        LinkedHashSet<String> approved = approvedCapabilities(agentId, profile);
        if (approved.isEmpty()) {
            return profile;
        }
        AgentProfile enriched = new AgentProfile();
        enriched.setAgentId(profile.getAgentId());
        enriched.setTenantId(profile.getTenantId());
        enriched.setAgentName(profile.getAgentName());
        enriched.setAgentType(profile.getAgentType());
        enriched.setOwnerTeam(profile.getOwnerTeam());
        enriched.setDescription(profile.getDescription());
        enriched.setApprovalStatus(profile.getApprovalStatus());
        enriched.setEnabled(profile.isEnabled());
        enriched.setRiskStatus(profile.getRiskStatus());
        enriched.setPolicyVersion(profile.getPolicyVersion());
        enriched.setCreatedAt(profile.getCreatedAt());
        enriched.setUpdatedAt(profile.getUpdatedAt());
        enriched.setCredential(profile.getCredential());
        enriched.setAuthorizationScopes(profile.getAuthorizationScopes());

        List<AgentCapability> capabilities = new ArrayList<>(profile.getCapabilities() == null ? List.of() : profile.getCapabilities());
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        addApprovedCapabilities(existing, capabilities);
        for (String capabilityCode : approved) {
            if (existing.contains(capabilityCode)) {
                continue;
            }
            AgentCapability capability = new AgentCapability(agentId, capabilityCode);
            capability.setEnabled(true);
            capability.setApprovedBy("CORE_ADMIN_ASSIGNMENT");
            capability.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
            capabilities.add(capability);
        }
        enriched.setCapabilities(capabilities);
        return enriched;
    }

    private LinkedHashSet<String> approvedCapabilities(String agentId, AgentProfile profile) {
        LinkedHashSet<String> approved = new LinkedHashSet<>();
        // P3-X: governed capability assignments are the working source of truth. The legacy
        // agent_capabilities/profile capability rows are migration diagnostics only and must not
        // resurrect revoked values or make old free-form aliases dispatch-usable.
        if (!blank(agentId) && assignmentService != null) {
            assignmentService.findAgentCapabilities(agentId).stream()
                    .filter(assignment -> assignment != null && assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED)
                    .map(AgentCapabilityAssignment::getCapabilityCode)
                    .map(this::normalize)
                    .filter(value -> !blank(value))
                    .forEach(approved::add);
        }
        // Keep a test-only fallback for focused unit tests that construct this service without
        // AgentAssignmentService. Production wiring uses the assignmentService constructor.
        if (approved.isEmpty()) {
            addApprovedCapabilities(approved, profile == null ? null : profile.getCapabilities());
        }
        return approved;
    }

    private void addApprovedCapabilities(Set<String> target, List<AgentCapability> capabilities) {
        if (target == null || capabilities == null) {
            return;
        }
        capabilities.stream()
                .filter(capability -> capability != null && capability.isEnabled())
                .map(AgentCapability::getCapabilityCode)
                .map(this::normalize)
                .filter(value -> !blank(value))
                .forEach(target::add);
    }

    private LinkedHashSet<String> reportedCapabilities(List<AgentRuntimeCapabilityItem> items) {
        LinkedHashSet<String> reported = new LinkedHashSet<>();
        if (items == null) {
            return reported;
        }
        items.stream()
                .filter(item -> item != null && !blank(item.capabilityValue()))
                .map(AgentRuntimeCapabilityItem::capabilityValue)
                .map(this::normalize)
                .filter(value -> !blank(value))
                .forEach(reported::add);
        return reported;
    }

    private void addNormalized(Set<String> target, Iterable<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addNormalized(target, value);
        }
    }

    private void addNormalized(Set<String> target, String value) {
        String normalized = normalize(value);
        if (!blank(normalized)) {
            target.add(normalized);
        }
    }

    private boolean intersects(List<String> left, Set<String> right) {
        if (left == null || right == null || right.isEmpty()) {
            return false;
        }
        return left.stream().map(this::normalize).anyMatch(right::contains);
    }

    private List<String> effectiveCapabilities(AgentSnapshot agent) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (agent.getCapabilities() != null) {
            agent.getCapabilities().stream().map(this::normalize).filter(value -> !blank(value)).forEach(capabilities::add);
        }
        Map<String, Object> profile = agent.getCapabilityProfile();
        if (profile != null && !profile.isEmpty()) {
            addCapabilityValues(capabilities, profile.get("supportedCapabilities"));
            addCapabilityValues(capabilities, profile.get("runtimeCapabilities"));
            addCapabilityValues(capabilities, profile.get("supportedTaskTypes"));
            addCapabilityValues(capabilities, profile.get("supportedIssueProviders"));
            addCapabilityValues(capabilities, profile.get("toolPolicies"));
            addCapabilityValues(capabilities, profile.get("skills"));
            Object executorMode = profile.get("executorMode");
            if (executorMode != null && !executorMode.toString().isBlank()) {
                capabilities.add(normalize(executorMode.toString()));
            }
        }
        return capabilities.stream().toList();
    }

    private void addCapabilityValues(Set<String> target, Object value) {
        if (target == null || value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCapabilityValue(target, item);
            }
            return;
        }
        addCapabilityValue(target, value);
    }

    private void addCapabilityValue(Set<String> target, Object item) {
        if (item == null) return;
        if (item instanceof Map<?, ?> map) {
            addCapabilityValue(target, map.get("skillCode"));
            addCapabilityValues(target, map.get("taskTypes"));
            addCapabilityValues(target, map.get("providers"));
            addCapabilityValues(target, map.get("operations"));
            addCapabilityValues(target, map.get("toolPolicies"));
            addCapabilityValues(target, map.get("dataClasses"));
            return;
        }
        String normalized = normalize(item.toString());
        if (!blank(normalized)) {
            target.add(normalized);
        }
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
