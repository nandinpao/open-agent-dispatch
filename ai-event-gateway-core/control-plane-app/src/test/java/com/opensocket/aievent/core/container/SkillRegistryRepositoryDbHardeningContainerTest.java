package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.skill.AgentApprovedSkill;
import com.opensocket.aievent.core.agent.skill.AgentSkillApprovalPolicy;
import com.opensocket.aievent.core.agent.skill.AgentSkillAuditEntry;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillDependencyEdge;
import com.opensocket.aievent.core.agent.skill.AgentSkillDeprecationPlan;
import com.opensocket.aievent.core.agent.skill.AgentSkillLifecycleStatus;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryRepository;
import com.opensocket.aievent.core.agent.skill.AgentSkillVersion;

class SkillRegistryRepositoryDbHardeningContainerTest extends P25RepositoryDbContainerSupport {

    @Test
    void skillDefinitionVersionApprovalAndAuditMustRoundTripThroughMybatisRepository() {
        AgentSkillRegistryRepository repository = skillRegistryRepository();
        AgentSkillDefinition skill = skill("P25_ERP_RISK_REVIEW", "ERP");

        repository.upsert(skill);
        assertThat(repository.findByCode("P25_ERP_RISK_REVIEW"))
                .hasValueSatisfying(found -> {
                    assertThat(found.getProviders()).containsExactly("ERP", "SAP");
                    assertThat(found.getTaskTypes()).containsExactly("INCIDENT_RESPONSE");
                    assertThat(found.getMetadata()).containsEntry("stage", "P25");
                });
        assertThat(repository.search("ERP", true))
                .extracting(AgentSkillDefinition::getSkillCode)
                .contains("P25_ERP_RISK_REVIEW");

        assertThat(repository.nextVersion("P25_ERP_RISK_REVIEW")).isEqualTo(1);
        repository.upsertVersion(version("P25_ERP_RISK_REVIEW", 1, AgentSkillLifecycleStatus.PENDING_APPROVAL));
        repository.upsertVersion(version("P25_ERP_RISK_REVIEW", 2, AgentSkillLifecycleStatus.PUBLISHED));
        assertThat(repository.nextVersion("P25_ERP_RISK_REVIEW")).isEqualTo(3);
        assertThat(repository.listVersions("P25_ERP_RISK_REVIEW"))
                .extracting(AgentSkillVersion::getVersion)
                .containsExactly(2, 1);

        repository.appendAuditEntry(audit("audit-db-1", "P25_ERP_RISK_REVIEW", 2));
        assertThat(repository.listAuditEntries("P25_ERP_RISK_REVIEW", 10))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo("PUBLISH");
                    assertThat(entry.getToStatus()).isEqualTo(AgentSkillLifecycleStatus.PUBLISHED);
                });

        repository.upsertApprovalPolicy(policy("P25_ERP_RISK_REVIEW"));
        assertThat(repository.findApprovalPolicy("P25_ERP_RISK_REVIEW"))
                .hasValueSatisfying(policy -> {
                    assertThat(policy.isSeparationOfDuties()).isTrue();
                    assertThat(policy.getApproveRoles()).containsExactly("COMPLIANCE");
                });

        repository.replaceApprovedSkills("agent-db-1", List.of(
                approvedSkill("agent-db-1", "P25_ERP_RISK_REVIEW", true),
                approvedSkill("agent-db-1", "P25_DISABLED_SKILL", false)));
        assertThat(repository.findApprovedSkills("agent-db-1", true))
                .extracting(AgentApprovedSkill::getSkillCode)
                .containsExactly("P25_ERP_RISK_REVIEW");
    }

    @Test
    void deprecationPlanAndDependencyEdgesMustBeReplaceableAndDepthAware() {
        AgentSkillRegistryRepository repository = skillRegistryRepository();
        repository.upsert(skill("P25_ROOT_SKILL", "ERP"));
        repository.upsert(skill("P25_CHILD_SKILL", "ERP"));
        repository.upsert(skill("P25_GRANDCHILD_SKILL", "ERP"));

        repository.upsertDeprecationPlan(deprecationPlan("P25_ROOT_SKILL"));
        assertThat(repository.findDeprecationPlan("P25_ROOT_SKILL"))
                .hasValueSatisfying(plan -> {
                    assertThat(plan.getStatus()).isEqualTo("PLANNED");
                    assertThat(plan.getReplacementSkillCodes()).containsExactly("P25_CHILD_SKILL");
                });
        assertThat(repository.listDeprecationPlans("PLANNED"))
                .extracting(AgentSkillDeprecationPlan::getSkillCode)
                .contains("P25_ROOT_SKILL");

        repository.replaceDependencyEdges("P25_ROOT_SKILL", List.of(
                dependency("edge-db-1", "P25_ROOT_SKILL", "P25_CHILD_SKILL", true),
                dependency("edge-db-disabled", "P25_ROOT_SKILL", "P25_DISABLED_SKILL", false)));
        repository.replaceDependencyEdges("P25_CHILD_SKILL", List.of(
                dependency("edge-db-2", "P25_CHILD_SKILL", "P25_GRANDCHILD_SKILL", true)));

        assertThat(repository.findDependencyEdges("P25_ROOT_SKILL", 1))
                .extracting(AgentSkillDependencyEdge::getTargetSkillCode)
                .containsExactly("P25_CHILD_SKILL");
        assertThat(repository.findDependencyEdges("P25_ROOT_SKILL", 2))
                .extracting(AgentSkillDependencyEdge::getTargetSkillCode)
                .contains("P25_CHILD_SKILL", "P25_GRANDCHILD_SKILL");
        assertThat(repository.findAllDependencyEdges())
                .extracting(AgentSkillDependencyEdge::getEdgeId)
                .contains("edge-db-1", "edge-db-2")
                .doesNotContain("edge-db-disabled");
    }

    private AgentSkillDefinition skill(String code, String domain) {
        AgentSkillDefinition skill = new AgentSkillDefinition();
        skill.setSkillCode(code);
        skill.setDisplayName(code + " Display");
        skill.setDomain(domain);
        skill.setDescription("P25 repository DB hardening skill");
        skill.setProviders(List.of(domain, "SAP"));
        skill.setTaskTypes(List.of("INCIDENT_RESPONSE"));
        skill.setOperations(List.of("READ", "ANALYZE"));
        skill.setToolPolicies(List.of("READ_ONLY"));
        skill.setResourceScopes(List.of(domain + ":RISK"));
        skill.setDataClasses(List.of("INTERNAL"));
        skill.setRiskLevel("MEDIUM");
        skill.setRequiresHumanApproval(true);
        skill.setMaskingRequired(false);
        skill.setEnabled(true);
        skill.setMetadata(Map.of("stage", "P25"));
        skill.setCreatedAt(now());
        skill.setUpdatedAt(now());
        return skill;
    }

    private AgentSkillVersion version(String skillCode, int version, AgentSkillLifecycleStatus status) {
        AgentSkillVersion skillVersion = new AgentSkillVersion();
        skillVersion.setSkillCode(skillCode);
        skillVersion.setVersion(version);
        skillVersion.setStatus(status);
        skillVersion.setDefinition(skill(skillCode, "ERP"));
        skillVersion.setSubmittedBy("qa");
        skillVersion.setSubmittedAt(now());
        skillVersion.setReviewedBy("qa-reviewer");
        skillVersion.setReviewedAt(now());
        skillVersion.setPublishedBy(status == AgentSkillLifecycleStatus.PUBLISHED ? "qa-publisher" : null);
        skillVersion.setPublishedAt(status == AgentSkillLifecycleStatus.PUBLISHED ? now() : null);
        skillVersion.setMetadata(Map.of("stage", "P25", "version", version));
        skillVersion.setCreatedAt(now());
        skillVersion.setUpdatedAt(now());
        return skillVersion;
    }

    private AgentSkillAuditEntry audit(String auditId, String skillCode, int version) {
        AgentSkillAuditEntry entry = new AgentSkillAuditEntry();
        entry.setAuditId(auditId);
        entry.setSkillCode(skillCode);
        entry.setVersion(version);
        entry.setAction("PUBLISH");
        entry.setOperatorId("qa");
        entry.setReason("repository DB hardening");
        entry.setFromStatus(AgentSkillLifecycleStatus.APPROVED);
        entry.setToStatus(AgentSkillLifecycleStatus.PUBLISHED);
        entry.setMetadata(Map.of("stage", "P25"));
        entry.setCreatedAt(now());
        return entry;
    }

    private AgentSkillApprovalPolicy policy(String skillCode) {
        AgentSkillApprovalPolicy policy = new AgentSkillApprovalPolicy();
        policy.setSkillCode(skillCode);
        policy.setEnabled(true);
        policy.setSubmitRoles(List.of("ADMIN"));
        policy.setApproveRoles(List.of("COMPLIANCE"));
        policy.setPublishRoles(List.of("SYSADMIN"));
        policy.setRollbackRoles(List.of("SYSADMIN"));
        policy.setSeparationOfDuties(true);
        policy.setUpdatedBy("qa");
        policy.setUpdatedAt(now());
        policy.setMetadata(Map.of("stage", "P25"));
        return policy;
    }

    private AgentApprovedSkill approvedSkill(String agentId, String skillCode, boolean enabled) {
        AgentApprovedSkill skill = new AgentApprovedSkill();
        skill.setAgentId(agentId);
        skill.setSkillCode(skillCode);
        skill.setPolicyVersion(2);
        skill.setEnabled(enabled);
        skill.setApprovedBy("qa");
        skill.setApprovedAt(now());
        skill.setMetadata(Map.of("stage", "P25"));
        skill.setCreatedAt(now());
        skill.setUpdatedAt(now());
        return skill;
    }

    private AgentSkillDeprecationPlan deprecationPlan(String skillCode) {
        AgentSkillDeprecationPlan plan = new AgentSkillDeprecationPlan();
        plan.setSkillCode(skillCode);
        plan.setStatus("PLANNED");
        plan.setReplacementSkillCodes(List.of("P25_CHILD_SKILL"));
        plan.setMigrationDeadline(now().plusDays(30));
        plan.setCreatedBy("qa");
        plan.setCreatedAt(now());
        plan.setUpdatedBy("qa");
        plan.setUpdatedAt(now());
        plan.setMetadata(Map.of("stage", "P25"));
        return plan;
    }

    private AgentSkillDependencyEdge dependency(String edgeId, String source, String target, boolean enabled) {
        AgentSkillDependencyEdge edge = new AgentSkillDependencyEdge();
        edge.setEdgeId(edgeId);
        edge.setSourceSkillCode(source);
        edge.setTargetSkillCode(target);
        edge.setRelationType("REQUIRES");
        edge.setRequired(true);
        edge.setEnabled(enabled);
        edge.setConfidence(0.95d);
        edge.setDescription("P25 dependency edge");
        edge.setCreatedBy("qa");
        edge.setCreatedAt(now());
        edge.setUpdatedBy("qa");
        edge.setUpdatedAt(now());
        edge.setMetadata(Map.of("stage", "P25"));
        return edge;
    }
}
