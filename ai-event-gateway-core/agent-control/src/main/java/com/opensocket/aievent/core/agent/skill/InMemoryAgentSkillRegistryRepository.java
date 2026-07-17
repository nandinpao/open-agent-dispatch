package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MEMORY")
public class InMemoryAgentSkillRegistryRepository implements AgentSkillRegistryRepository {
    private final Map<String, AgentSkillDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AgentApprovedSkill>> approvedByAgent = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, AgentSkillVersion>> versionsBySkill = new ConcurrentHashMap<>();
    private final Map<String, List<AgentSkillAuditEntry>> auditBySkill = new ConcurrentHashMap<>();
    private final Map<String, AgentSkillApprovalPolicy> approvalPolicies = new ConcurrentHashMap<>();
    private final Map<String, AgentSkillDeprecationPlan> deprecationPlans = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AgentSkillDependencyEdge>> dependencyEdgesBySource = new ConcurrentHashMap<>();

    @Override
    public List<AgentSkillDefinition> search(String domain, boolean enabledOnly) {
        String normalizedDomain = normalize(domain);
        return definitions.values().stream()
                .filter(skill -> normalizedDomain == null || normalizedDomain.equals(normalize(skill.getDomain())))
                .filter(skill -> !enabledOnly || skill.isEnabled())
                .sorted(Comparator.comparing(AgentSkillDefinition::getDomain, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AgentSkillDefinition::getSkillCode, Comparator.nullsLast(String::compareTo)))
                .map(this::copyDefinition)
                .toList();
    }

    @Override
    public Optional<AgentSkillDefinition> findByCode(String skillCode) {
        AgentSkillDefinition value = definitions.get(normalize(skillCode));
        return value == null ? Optional.empty() : Optional.of(copyDefinition(value));
    }

    @Override
    public AgentSkillDefinition upsert(AgentSkillDefinition skill) {
        AgentSkillDefinition copy = copyDefinition(skill);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        copy.setSkillCode(normalize(copy.getSkillCode()));
        if (copy.getCreatedAt() == null) copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        definitions.put(copy.getSkillCode(), copyDefinition(copy));
        return copyDefinition(copy);
    }

    @Override
    public boolean delete(String skillCode) {
        return definitions.remove(normalize(skillCode)) != null;
    }

    @Override
    public List<AgentApprovedSkill> findApprovedSkills(String agentId, boolean enabledOnly) {
        Map<String, AgentApprovedSkill> values = approvedByAgent.get(normalize(agentId));
        if (values == null) return List.of();
        return values.values().stream()
                .filter(skill -> !enabledOnly || skill.isEnabled())
                .sorted(Comparator.comparing(AgentApprovedSkill::getSkillCode, Comparator.nullsLast(String::compareTo)))
                .map(this::copyApprovedSkill)
                .toList();
    }

    @Override
    public List<AgentApprovedSkill> findApprovedSkillsBySkillCode(String skillCode, boolean enabledOnly) {
        String normalizedSkillCode = normalize(skillCode);
        return approvedByAgent.values().stream()
                .flatMap(map -> map.values().stream())
                .filter(skill -> normalizedSkillCode != null && normalizedSkillCode.equals(normalize(skill.getSkillCode())))
                .filter(skill -> !enabledOnly || skill.isEnabled())
                .sorted(Comparator.comparing(AgentApprovedSkill::getAgentId, Comparator.nullsLast(String::compareTo)))
                .map(this::copyApprovedSkill)
                .toList();
    }

    @Override
    public List<AgentApprovedSkill> replaceApprovedSkills(String agentId, List<AgentApprovedSkill> skills) {
        String key = normalize(agentId);
        Map<String, AgentApprovedSkill> map = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AgentApprovedSkill input : skills == null ? List.<AgentApprovedSkill>of() : skills) {
            if (input == null || blank(input.getSkillCode())) continue;
            AgentApprovedSkill copy = copyApprovedSkill(input);
            copy.setAgentId(key);
            copy.setSkillCode(normalize(copy.getSkillCode()));
            if (copy.getApprovedAt() == null) copy.setApprovedAt(now);
            if (copy.getCreatedAt() == null) copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            map.put(copy.getSkillCode(), copy);
        }
        approvedByAgent.put(key, map);
        return findApprovedSkills(key, false);
    }


    @Override
    public List<AgentSkillVersion> listVersions(String skillCode) {
        Map<Integer, AgentSkillVersion> values = versionsBySkill.get(normalize(skillCode));
        if (values == null) return List.of();
        return values.values().stream()
                .sorted(Comparator.comparingInt(AgentSkillVersion::getVersion).reversed())
                .map(this::copyVersion)
                .toList();
    }

    @Override
    public Optional<AgentSkillVersion> findVersion(String skillCode, int version) {
        Map<Integer, AgentSkillVersion> values = versionsBySkill.get(normalize(skillCode));
        if (values == null) return Optional.empty();
        AgentSkillVersion value = values.get(version);
        return value == null ? Optional.empty() : Optional.of(copyVersion(value));
    }

    @Override
    public AgentSkillVersion upsertVersion(AgentSkillVersion version) {
        AgentSkillVersion copy = copyVersion(version);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        copy.setSkillCode(normalize(copy.getSkillCode()));
        if (copy.getCreatedAt() == null) copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        versionsBySkill.computeIfAbsent(copy.getSkillCode(), ignored -> new ConcurrentHashMap<>())
                .put(copy.getVersion(), copyVersion(copy));
        return copyVersion(copy);
    }

    @Override
    public int nextVersion(String skillCode) {
        Map<Integer, AgentSkillVersion> values = versionsBySkill.get(normalize(skillCode));
        if (values == null || values.isEmpty()) return 1;
        return values.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    @Override
    public List<AgentSkillAuditEntry> listAuditEntries(String skillCode, int limit) {
        List<AgentSkillAuditEntry> values = auditBySkill.get(normalize(skillCode));
        if (values == null) return List.of();
        int max = limit <= 0 ? 100 : limit;
        return values.stream()
                .sorted(Comparator.comparing(AgentSkillAuditEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(max)
                .map(this::copyAuditEntry)
                .toList();
    }

    @Override
    public AgentSkillAuditEntry appendAuditEntry(AgentSkillAuditEntry entry) {
        AgentSkillAuditEntry copy = copyAuditEntry(entry);
        if (copy.getAuditId() == null || copy.getAuditId().isBlank()) copy.setAuditId(UUID.randomUUID().toString());
        copy.setSkillCode(normalize(copy.getSkillCode()));
        if (copy.getCreatedAt() == null) copy.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        auditBySkill.computeIfAbsent(copy.getSkillCode(), ignored -> new ArrayList<>()).add(copyAuditEntry(copy));
        return copyAuditEntry(copy);
    }

    @Override
    public Optional<AgentSkillApprovalPolicy> findApprovalPolicy(String skillCode) {
        AgentSkillApprovalPolicy value = approvalPolicies.get(normalize(skillCode));
        return value == null ? Optional.empty() : Optional.of(copyApprovalPolicy(value));
    }

    @Override
    public AgentSkillApprovalPolicy upsertApprovalPolicy(AgentSkillApprovalPolicy policy) {
        AgentSkillApprovalPolicy copy = copyApprovalPolicy(policy);
        copy.setSkillCode(normalize(copy.getSkillCode()));
        if (copy.getUpdatedAt() == null) copy.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        approvalPolicies.put(copy.getSkillCode(), copyApprovalPolicy(copy));
        return copyApprovalPolicy(copy);
    }


    @Override
    public Optional<AgentSkillDeprecationPlan> findDeprecationPlan(String skillCode) {
        AgentSkillDeprecationPlan value = deprecationPlans.get(normalize(skillCode));
        return value == null ? Optional.empty() : Optional.of(copyDeprecationPlan(value));
    }

    @Override
    public List<AgentSkillDeprecationPlan> listDeprecationPlans(String status) {
        String normalizedStatus = normalize(status);
        return deprecationPlans.values().stream()
                .filter(plan -> normalizedStatus == null || normalizedStatus.equals(normalize(plan.getStatus())))
                .sorted(Comparator.comparing(AgentSkillDeprecationPlan::getSkillCode, Comparator.nullsLast(String::compareTo)))
                .map(this::copyDeprecationPlan)
                .toList();
    }

    @Override
    public AgentSkillDeprecationPlan upsertDeprecationPlan(AgentSkillDeprecationPlan plan) {
        AgentSkillDeprecationPlan copy = copyDeprecationPlan(plan);
        copy.setSkillCode(normalize(copy.getSkillCode()));
        if (copy.getStatus() == null || copy.getStatus().isBlank()) copy.setStatus("PLANNED");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (copy.getCreatedAt() == null) copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        deprecationPlans.put(copy.getSkillCode(), copyDeprecationPlan(copy));
        return copyDeprecationPlan(copy);
    }



    @Override
    public List<AgentSkillDependencyEdge> findDependencyEdges(String skillCode, int depth) {
        String root = normalize(skillCode);
        if (root == null) return List.of();
        int maxDepth = Math.max(1, Math.min(5, depth));
        Map<String, AgentSkillDependencyEdge> result = new LinkedHashMap<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        collectDependencyEdges(root, maxDepth, result, visited);
        return result.values().stream().map(this::copyDependencyEdge).toList();
    }

    private void collectDependencyEdges(String sourceSkillCode, int remainingDepth, Map<String, AgentSkillDependencyEdge> result, Set<String> visited) {
        if (remainingDepth <= 0 || sourceSkillCode == null || !visited.add(sourceSkillCode)) return;
        Map<String, AgentSkillDependencyEdge> direct = dependencyEdgesBySource.get(sourceSkillCode);
        if (direct == null) return;
        for (AgentSkillDependencyEdge edge : direct.values()) {
            if (edge == null || !edge.isEnabled()) continue;
            result.put(edge.getEdgeId(), copyDependencyEdge(edge));
            collectDependencyEdges(normalize(edge.getTargetSkillCode()), remainingDepth - 1, result, visited);
        }
    }

    @Override
    public List<AgentSkillDependencyEdge> findAllDependencyEdges() {
        return dependencyEdgesBySource.values().stream()
                .flatMap(map -> map.values().stream())
                .filter(AgentSkillDependencyEdge::isEnabled)
                .sorted(Comparator.comparing(AgentSkillDependencyEdge::getSourceSkillCode, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AgentSkillDependencyEdge::getTargetSkillCode, Comparator.nullsLast(String::compareTo)))
                .map(this::copyDependencyEdge)
                .toList();
    }

    @Override
    public List<AgentSkillDependencyEdge> replaceDependencyEdges(String skillCode, List<AgentSkillDependencyEdge> edges) {
        String source = normalize(skillCode);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, AgentSkillDependencyEdge> values = new LinkedHashMap<>();
        for (AgentSkillDependencyEdge input : edges == null ? List.<AgentSkillDependencyEdge>of() : edges) {
            if (input == null || blank(input.getTargetSkillCode())) continue;
            AgentSkillDependencyEdge copy = copyDependencyEdge(input);
            copy.setSourceSkillCode(source);
            copy.setTargetSkillCode(normalize(copy.getTargetSkillCode()));
            copy.setRelationType(normalize(copy.getRelationType()) == null ? "RELATED_TO" : normalize(copy.getRelationType()));
            if (blank(copy.getEdgeId())) copy.setEdgeId(UUID.randomUUID().toString());
            if (blank(copy.getCreatedBy())) copy.setCreatedBy("system");
            if (copy.getCreatedAt() == null) copy.setCreatedAt(now);
            if (blank(copy.getUpdatedBy())) copy.setUpdatedBy(copy.getCreatedBy());
            copy.setUpdatedAt(now);
            values.put(copy.getEdgeId(), copy);
        }
        dependencyEdgesBySource.put(source, values);
        return values.values().stream().map(this::copyDependencyEdge).toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private AgentSkillDefinition copyDefinition(AgentSkillDefinition source) {
        AgentSkillDefinition copy = new AgentSkillDefinition();
        if (source == null) return copy;
        copy.setSkillCode(source.getSkillCode());
        copy.setDisplayName(source.getDisplayName());
        copy.setDomain(source.getDomain());
        copy.setDescription(source.getDescription());
        copy.setTaxonomyVersion(source.getTaxonomyVersion());
        copy.setTaskDefinitionId(source.getTaskDefinitionId());
        copy.setSourceSystem(source.getSourceSystem());
        copy.setTaskType(source.getTaskType());
        copy.setProviders(source.getProviders());
        copy.setTaskTypes(source.getTaskTypes());
        copy.setOperations(source.getOperations());
        copy.setToolPolicies(source.getToolPolicies());
        copy.setResourceScopes(source.getResourceScopes());
        copy.setDataClasses(source.getDataClasses());
        copy.setRiskLevel(source.getRiskLevel());
        copy.setRequiresHumanApproval(source.isRequiresHumanApproval());
        copy.setMaskingRequired(source.isMaskingRequired());
        copy.setEnabled(source.isEnabled());
        copy.setMetadata(source.getMetadata());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private AgentApprovedSkill copyApprovedSkill(AgentApprovedSkill source) {
        AgentApprovedSkill copy = new AgentApprovedSkill();
        if (source == null) return copy;
        copy.setAgentId(source.getAgentId());
        copy.setSkillCode(source.getSkillCode());
        copy.setPolicyVersion(source.getPolicyVersion());
        copy.setEnabled(source.isEnabled());
        copy.setApprovedBy(source.getApprovedBy());
        copy.setApprovedAt(source.getApprovedAt());
        copy.setMetadata(source.getMetadata());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }


    private AgentSkillVersion copyVersion(AgentSkillVersion source) {
        AgentSkillVersion copy = new AgentSkillVersion();
        if (source == null) return copy;
        copy.setSkillCode(source.getSkillCode());
        copy.setVersion(source.getVersion());
        copy.setStatus(source.getStatus());
        copy.setDefinition(copyDefinition(source.getDefinition()));
        copy.setSubmittedBy(source.getSubmittedBy());
        copy.setSubmittedAt(source.getSubmittedAt());
        copy.setReviewedBy(source.getReviewedBy());
        copy.setReviewedAt(source.getReviewedAt());
        copy.setReviewComment(source.getReviewComment());
        copy.setPublishedBy(source.getPublishedBy());
        copy.setPublishedAt(source.getPublishedAt());
        copy.setSupersedesVersion(source.getSupersedesVersion());
        copy.setRollbackOfVersion(source.getRollbackOfVersion());
        copy.setMetadata(source.getMetadata());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private AgentSkillApprovalPolicy copyApprovalPolicy(AgentSkillApprovalPolicy source) {
        AgentSkillApprovalPolicy copy = new AgentSkillApprovalPolicy();
        if (source == null) return copy;
        copy.setSkillCode(source.getSkillCode());
        copy.setEnabled(source.isEnabled());
        copy.setSubmitRoles(source.getSubmitRoles());
        copy.setApproveRoles(source.getApproveRoles());
        copy.setPublishRoles(source.getPublishRoles());
        copy.setRollbackRoles(source.getRollbackRoles());
        copy.setSeparationOfDuties(source.isSeparationOfDuties());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setMetadata(source.getMetadata());
        return copy;
    }


    private AgentSkillDeprecationPlan copyDeprecationPlan(AgentSkillDeprecationPlan source) {
        AgentSkillDeprecationPlan copy = new AgentSkillDeprecationPlan();
        if (source == null) return copy;
        copy.setSkillCode(source.getSkillCode());
        copy.setStatus(source.getStatus());
        copy.setReplacementSkillCodes(source.getReplacementSkillCodes());
        copy.setMigrationDeadline(source.getMigrationDeadline());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setMetadata(source.getMetadata());
        return copy;
    }



    private AgentSkillDependencyEdge copyDependencyEdge(AgentSkillDependencyEdge source) {
        AgentSkillDependencyEdge copy = new AgentSkillDependencyEdge();
        if (source == null) return copy;
        copy.setEdgeId(source.getEdgeId());
        copy.setSourceSkillCode(source.getSourceSkillCode());
        copy.setTargetSkillCode(source.getTargetSkillCode());
        copy.setRelationType(source.getRelationType());
        copy.setRequired(source.isRequired());
        copy.setEnabled(source.isEnabled());
        copy.setConfidence(source.getConfidence());
        copy.setDescription(source.getDescription());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setMetadata(source.getMetadata());
        return copy;
    }

    private AgentSkillAuditEntry copyAuditEntry(AgentSkillAuditEntry source) {
        AgentSkillAuditEntry copy = new AgentSkillAuditEntry();
        if (source == null) return copy;
        copy.setAuditId(source.getAuditId());
        copy.setSkillCode(source.getSkillCode());
        copy.setVersion(source.getVersion());
        copy.setAction(source.getAction());
        copy.setOperatorId(source.getOperatorId());
        copy.setReason(source.getReason());
        copy.setFromStatus(source.getFromStatus());
        copy.setToStatus(source.getToStatus());
        copy.setMetadata(source.getMetadata());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
