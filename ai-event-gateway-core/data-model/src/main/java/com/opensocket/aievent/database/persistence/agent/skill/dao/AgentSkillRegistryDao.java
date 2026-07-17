package com.opensocket.aievent.database.persistence.agent.skill.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillDefinitionPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentApprovedSkillPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillAuditEntryPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillVersionPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillApprovalPolicyPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillDeprecationPlanPo;
import com.opensocket.aievent.database.persistence.agent.skill.po.AgentSkillDependencyEdgePo;

@Mapper
public interface AgentSkillRegistryDao {
    List<AgentSkillDefinitionPo> search(@Param("domain") String domain, @Param("enabledOnly") boolean enabledOnly);
    AgentSkillDefinitionPo findByCode(@Param("skillCode") String skillCode);
    int upsert(@Param("skill") AgentSkillDefinitionPo skill);
    int delete(@Param("skillCode") String skillCode);

    List<AgentApprovedSkillPo> findApprovedSkills(@Param("agentId") String agentId, @Param("enabledOnly") boolean enabledOnly);
    List<AgentApprovedSkillPo> findApprovedSkillsBySkillCode(@Param("skillCode") String skillCode, @Param("enabledOnly") boolean enabledOnly);
    int deleteApprovedSkills(@Param("agentId") String agentId);
    int upsertApprovedSkill(@Param("skill") AgentApprovedSkillPo skill);

    List<AgentSkillVersionPo> listVersions(@Param("skillCode") String skillCode);
    AgentSkillVersionPo findVersion(@Param("skillCode") String skillCode, @Param("version") int version);
    int upsertVersion(@Param("version") AgentSkillVersionPo version);
    Integer maxVersion(@Param("skillCode") String skillCode);

    List<AgentSkillAuditEntryPo> listAuditEntries(@Param("skillCode") String skillCode, @Param("limit") int limit);
    int insertAuditEntry(@Param("entry") AgentSkillAuditEntryPo entry);

    AgentSkillApprovalPolicyPo findApprovalPolicy(@Param("skillCode") String skillCode);
    int upsertApprovalPolicy(@Param("policy") AgentSkillApprovalPolicyPo policy);

    AgentSkillDeprecationPlanPo findDeprecationPlan(@Param("skillCode") String skillCode);
    List<AgentSkillDeprecationPlanPo> listDeprecationPlans(@Param("status") String status);
    int upsertDeprecationPlan(@Param("plan") AgentSkillDeprecationPlanPo plan);

    List<AgentSkillDependencyEdgePo> findDependencyEdges(@Param("skillCode") String skillCode, @Param("depth") int depth);
    List<AgentSkillDependencyEdgePo> findAllDependencyEdges();
    int deleteDependencyEdges(@Param("skillCode") String skillCode);
    int upsertDependencyEdge(@Param("edge") AgentSkillDependencyEdgePo edge);
}

