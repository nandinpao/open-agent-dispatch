package com.opensocket.aievent.core.agent.skill;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the OpenSocket capability taxonomy / skill registry.
 *
 * <p>The registry is owned by Core because dispatch decisions must compare the
 * runtime-reported skill profile with the Core-approved skill taxonomy. Runtime
 * transports such as Netty may relay these profiles, but they must not become
 * the taxonomy authority.</p>
 */
public interface AgentSkillRegistryRepository {
    List<AgentSkillDefinition> search(String domain, boolean enabledOnly);
    Optional<AgentSkillDefinition> findByCode(String skillCode);
    AgentSkillDefinition upsert(AgentSkillDefinition skill);
    boolean delete(String skillCode);

    List<AgentApprovedSkill> findApprovedSkills(String agentId, boolean enabledOnly);
    List<AgentApprovedSkill> findApprovedSkillsBySkillCode(String skillCode, boolean enabledOnly);
    List<AgentApprovedSkill> replaceApprovedSkills(String agentId, List<AgentApprovedSkill> skills);


    List<AgentSkillVersion> listVersions(String skillCode);
    Optional<AgentSkillVersion> findVersion(String skillCode, int version);
    AgentSkillVersion upsertVersion(AgentSkillVersion version);
    int nextVersion(String skillCode);

    List<AgentSkillAuditEntry> listAuditEntries(String skillCode, int limit);
    AgentSkillAuditEntry appendAuditEntry(AgentSkillAuditEntry entry);

    Optional<AgentSkillApprovalPolicy> findApprovalPolicy(String skillCode);
    AgentSkillApprovalPolicy upsertApprovalPolicy(AgentSkillApprovalPolicy policy);

    Optional<AgentSkillDeprecationPlan> findDeprecationPlan(String skillCode);
    List<AgentSkillDeprecationPlan> listDeprecationPlans(String status);
    AgentSkillDeprecationPlan upsertDeprecationPlan(AgentSkillDeprecationPlan plan);

    List<AgentSkillDependencyEdge> findDependencyEdges(String skillCode, int depth);
    List<AgentSkillDependencyEdge> findAllDependencyEdges();
    List<AgentSkillDependencyEdge> replaceDependencyEdges(String skillCode, List<AgentSkillDependencyEdge> edges);

    String mode();
}

