package com.opensocket.aievent.database.persistence.agent.skill.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillAuditEntry;
import com.opensocket.aievent.core.agent.skill.AgentSkillVersion;
import com.opensocket.aievent.core.agent.skill.AgentSkillApprovalPolicy;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkill;
import com.opensocket.aievent.core.agent.skill.AgentSkillDeprecationPlan;
import com.opensocket.aievent.core.agent.skill.AgentSkillDependencyEdge;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryRepository;
import com.opensocket.aievent.database.persistence.agent.skill.converter.AgentSkillRegistryPersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.skill.dao.AgentSkillRegistryDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "agent-directory", name = "store", havingValue = "MYBATIS")
public class MybatisAgentSkillRegistryRepository implements AgentSkillRegistryRepository {
    private final AgentSkillRegistryDao dao;
    private final AgentSkillRegistryPersistenceConverter converter;

    public MybatisAgentSkillRegistryRepository(AgentSkillRegistryDao dao, AgentSkillRegistryPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public List<AgentSkillDefinition> search(String domain, boolean enabledOnly) {
        return dao.search(domain, enabledOnly).stream().map(converter::toDomain).toList();
    }

    @Override
    public Optional<AgentSkillDefinition> findByCode(String skillCode) {
        return Optional.ofNullable(dao.findByCode(skillCode)).map(converter::toDomain);
    }

    @Override
    public AgentSkillDefinition upsert(AgentSkillDefinition skill) {
        dao.upsert(converter.toPo(skill));
        return findByCode(skill.getSkillCode()).orElse(skill);
    }

    @Override
    public boolean delete(String skillCode) {
        return dao.delete(skillCode) > 0;
    }

    @Override
    public List<AgentApprovedSkill> findApprovedSkills(String agentId, boolean enabledOnly) {
        return dao.findApprovedSkills(agentId, enabledOnly).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<AgentApprovedSkill> findApprovedSkillsBySkillCode(String skillCode, boolean enabledOnly) {
        return dao.findApprovedSkillsBySkillCode(skillCode, enabledOnly).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<AgentApprovedSkill> replaceApprovedSkills(String agentId, List<AgentApprovedSkill> skills) {
        dao.deleteApprovedSkills(agentId);
        if (skills != null) {
            for (AgentApprovedSkill skill : skills) {
                dao.upsertApprovedSkill(converter.toPo(skill));
            }
        }
        return findApprovedSkills(agentId, false);
    }


    @Override
    public List<AgentSkillVersion> listVersions(String skillCode) {
        return dao.listVersions(skillCode).stream().map(converter::toDomain).toList();
    }

    @Override
    public Optional<AgentSkillVersion> findVersion(String skillCode, int version) {
        return Optional.ofNullable(dao.findVersion(skillCode, version)).map(converter::toDomain);
    }

    @Override
    public AgentSkillVersion upsertVersion(AgentSkillVersion version) {
        dao.upsertVersion(converter.toPo(version));
        return findVersion(version.getSkillCode(), version.getVersion()).orElse(version);
    }

    @Override
    public int nextVersion(String skillCode) {
        Integer max = dao.maxVersion(skillCode);
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public List<AgentSkillAuditEntry> listAuditEntries(String skillCode, int limit) {
        int max = limit <= 0 ? 100 : limit;
        return dao.listAuditEntries(skillCode, max).stream().map(converter::toDomain).toList();
    }

    @Override
    public AgentSkillAuditEntry appendAuditEntry(AgentSkillAuditEntry entry) {
        dao.insertAuditEntry(converter.toPo(entry));
        return entry;
    }

    @Override
    public Optional<AgentSkillApprovalPolicy> findApprovalPolicy(String skillCode) {
        return Optional.ofNullable(dao.findApprovalPolicy(skillCode)).map(converter::toDomain);
    }

    @Override
    public AgentSkillApprovalPolicy upsertApprovalPolicy(AgentSkillApprovalPolicy policy) {
        dao.upsertApprovalPolicy(converter.toPo(policy));
        return findApprovalPolicy(policy.getSkillCode()).orElse(policy);
    }


    @Override
    public Optional<AgentSkillDeprecationPlan> findDeprecationPlan(String skillCode) {
        return Optional.ofNullable(dao.findDeprecationPlan(skillCode)).map(converter::toDomain);
    }

    @Override
    public List<AgentSkillDeprecationPlan> listDeprecationPlans(String status) {
        return dao.listDeprecationPlans(status).stream().map(converter::toDomain).toList();
    }

    @Override
    public AgentSkillDeprecationPlan upsertDeprecationPlan(AgentSkillDeprecationPlan plan) {
        dao.upsertDeprecationPlan(converter.toPo(plan));
        return findDeprecationPlan(plan.getSkillCode()).orElse(plan);
    }



    @Override
    public List<AgentSkillDependencyEdge> findDependencyEdges(String skillCode, int depth) {
        int maxDepth = Math.max(1, Math.min(5, depth));
        return dao.findDependencyEdges(skillCode, maxDepth).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<AgentSkillDependencyEdge> findAllDependencyEdges() {
        return dao.findAllDependencyEdges().stream().map(converter::toDomain).toList();
    }

    @Override
    public List<AgentSkillDependencyEdge> replaceDependencyEdges(String skillCode, List<AgentSkillDependencyEdge> edges) {
        dao.deleteDependencyEdges(skillCode);
        if (edges != null) {
            for (AgentSkillDependencyEdge edge : edges) {
                dao.upsertDependencyEdge(converter.toPo(edge));
            }
        }
        return findDependencyEdges(skillCode, 1);
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }
}
