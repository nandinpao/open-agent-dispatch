package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRequiredSkillView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRuntimeQuery;
import com.opensocket.aievent.database.persistence.dispatch.flow.JdbcFlowRuleRoutingRepository;
import com.opensocket.aievent.database.persistence.dispatch.governance.repository.JdbcGenericCandidateAgentRepository;

/**
 * Stage 3 integration-first TDD for the authoritative Dispatch Flow aggregate.
 *
 * <p>The test uses PostgreSQL + the real Flyway chain. Flow, Rule, selected Agents, and required
 * Capabilities must be committed as one aggregate. Runtime repositories must read the same rows
 * returned by the Admin API read model.</p>
 */
class Stage3DispatchFlowAggregateTransactionContainerTest extends P25RepositoryDbContainerSupport {

    private static final String TENANT = "tenant-stage3-tdd";
    private static final String SOURCE = "SRC_STAGE3_TDD";
    private static final String FLOW_ID = "flow-stage3-tdd";
    private static final String AGENT_1 = "agent-stage3-001";
    private static final String AGENT_2 = "agent-stage3-002";
    private static final String CAP_A = "CAP_STAGE3_ANALYZE";
    private static final String CAP_B = "CAP_STAGE3_CLASSIFY";

    @Test
    void aggregateUpdateRemovesStaleRulesCapabilitiesAndAgents() {
        seedAgent(AGENT_1, "Stage 3 Agent 1", TENANT);
        seedAgent(AGENT_2, "Stage 3 Agent 2", TENANT);
        seedCapability(CAP_A);
        seedCapability(CAP_B);

        DispatchFlowManagementService service = service();
        inTransaction(() -> service.createOrUpdateFlow(explicitAggregate(
                "Initial aggregate",
                List.of(rule("rule-stage3-a", "EVT_STAGE3_A", CAP_A), rule("rule-stage3-b", "EVT_STAGE3_B", CAP_B)),
                List.of(skill("skill-stage3-a", "rule-stage3-a", CAP_A), skill("skill-stage3-b", "rule-stage3-b", CAP_B)),
                List.of(agent("flow-agent-stage3-1", AGENT_1), agent("flow-agent-stage3-2", AGENT_2)))));

        DispatchFlowView saved = inTransactionResult(() -> service.createOrUpdateFlow(explicitAggregate(
                "Replacement aggregate",
                List.of(rule("rule-stage3-a", "EVT_STAGE3_A", CAP_A)),
                List.of(skill("skill-stage3-a", "rule-stage3-a", CAP_A)),
                List.of(agent("flow-agent-stage3-1", AGENT_1)))));

        assertThat(saved.getFlowName()).isEqualTo("Replacement aggregate");
        assertThat(saved.getRules()).extracting(DispatchFlowRuleView::getRuleId)
                .containsExactly("rule-stage3-a");
        assertThat(saved.getRequiredSkills()).extracting(DispatchFlowRequiredSkillView::getSkillCode)
                .containsExactly(CAP_A);
        assertThat(saved.getAgents()).extracting(DispatchFlowAgentView::getAgentId)
                .containsExactly(AGENT_1);
        assertThat(count("dispatch_policies")).isEqualTo(1);
        assertThat(count("flow_required_skills")).isEqualTo(1);
        assertThat(count("flow_agent_assignments")).isEqualTo(1);
    }

    @Test
    void runtimeCandidateAndRuleRepositoriesReadTheSameSavedAggregate() {
        seedAgent(AGENT_1, "Stage 3 Runtime Agent", TENANT);
        DispatchFlowManagementService service = service();
        DispatchFlowView saved = inTransactionResult(() -> service.createOrUpdateFlow(noCapabilityAggregate()));

        NamedParameterJdbcTemplate namedJdbc = namedJdbc();
        JdbcGenericCandidateAgentRepository candidates = new JdbcGenericCandidateAgentRepository(namedJdbc);
        JdbcFlowRuleRoutingRepository rules = new JdbcFlowRuleRoutingRepository(namedJdbc);

        assertThat(saved.getAgents()).extracting(DispatchFlowAgentView::getAgentId)
                .containsExactly(AGENT_1);
        assertThat(candidates.findExplicitFlowAgentIds(TENANT, FLOW_ID, "EXTERNAL", 20))
                .containsExactly(AGENT_1);
        assertThat(candidates.findExplicitFlowAgentIds("TENANT-STAGE3-TDD", "FLOW_STAGE3_TDD", "EXTERNAL", 20))
                .containsExactly(AGENT_1);

        FlowRuleRuntimeQuery query = new FlowRuleRuntimeQuery();
        query.setTenantId(TENANT);
        query.setSourceSystem(SOURCE);
        query.setEventStage("EXTERNAL");
        query.setObjectType("OBJECT_STAGE3");
        query.setEventType("EVT_STAGE3_NONE");
        query.setErrorCode("*");

        var matched = rules.findBestMatch(query).orElseThrow();
        assertThat(matched.getFlowId()).isEqualTo(FLOW_ID);
        assertThat(matched.getRuleId()).isEqualTo("rule-stage3-none");
        assertThat(matched.getCandidatePoolMode()).isEqualTo("EXPLICIT_FLOW_AGENTS");
        assertThat(matched.getCapabilityRequirementMode()).isEqualTo("NONE");
        assertThat(matched.getRequestedSkill()).isNull();
        assertThat(matched.getRequiredSkills()).isEmpty();
    }

    @Test
    void requiredCapabilityRowsAreAuthoritativeForTheRuntimeCompatibilityProjection() {
        seedAgent(AGENT_1, "Stage 3 Capability Projection Agent", TENANT);
        seedCapability(CAP_A);
        DispatchFlowRuleView rule = rule("rule-stage3-projection", "EVT_STAGE3_PROJECTION", null);
        rule.setCapabilityRequirementMode(null);
        rule.setRequestedSkill(null);

        DispatchFlowView saved = inTransactionResult(() -> service().createOrUpdateFlow(explicitAggregate(
                "Capability row authority",
                List.of(rule),
                List.of(skill("skill-stage3-projection", "rule-stage3-projection", CAP_A)),
                List.of(agent("flow-agent-stage3-1", AGENT_1)))));

        assertThat(saved.getDefaultCapabilityRequirementMode()).isEqualTo("EXPLICIT");
        assertThat(saved.getRules()).singleElement().satisfies(persistedRule -> {
            assertThat(persistedRule.getCapabilityRequirementMode()).isEqualTo("EXPLICIT");
            assertThat(persistedRule.getRequestedSkill()).isEqualTo(CAP_A);
        });
    }

    @Test
    void childIdsCannotBeMovedFromAnotherDispatchFlow() {
        seedAgent(AGENT_1, "Stage 3 Ownership Agent", TENANT);
        seedCapability(CAP_A);
        DispatchFlowManagementService service = service();

        DispatchFlowView owner = explicitAggregate(
                "Owner aggregate",
                List.of(rule("rule-stage3-owner", "EVT_STAGE3_OWNER", CAP_A)),
                List.of(skill("skill-stage3-shared", "rule-stage3-owner", CAP_A)),
                List.of(agent("flow-agent-stage3-shared", AGENT_1)));
        reidentify(owner, "flow-stage3-owner", "FLOW_STAGE3_OWNER");
        inTransaction(() -> service.createOrUpdateFlow(owner));

        DispatchFlowView stealsCapability = explicitAggregate(
                "Must not steal Capability row",
                List.of(rule("rule-stage3-main-a", "EVT_STAGE3_MAIN_A", CAP_A)),
                List.of(skill("skill-stage3-shared", "rule-stage3-main-a", CAP_A)),
                List.of(agent("flow-agent-stage3-main-a", AGENT_1)));
        assertThatThrownBy(() -> inTransaction(() -> service.createOrUpdateFlow(stealsCapability)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required Capability ID is already owned by another Dispatch Flow");

        DispatchFlowView stealsAgentSelection = explicitAggregate(
                "Must not steal Agent selection",
                List.of(rule("rule-stage3-main-b", "EVT_STAGE3_MAIN_B", CAP_A)),
                List.of(skill("skill-stage3-main-b", "rule-stage3-main-b", CAP_A)),
                List.of(agent("flow-agent-stage3-shared", AGENT_1)));
        assertThatThrownBy(() -> inTransaction(() -> service.createOrUpdateFlow(stealsAgentSelection)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flow Agent selection ID is already owned by another Dispatch Flow");

        assertThat(jdbc.queryForObject(
                "select flow_id from flow_required_skills where tenant_id=? and id=?",
                String.class, TENANT, "skill-stage3-shared")).isEqualTo("flow-stage3-owner");
        assertThat(jdbc.queryForObject(
                "select flow_id from flow_agent_assignments where tenant_id=? and id=?",
                String.class, TENANT, "flow-agent-stage3-shared")).isEqualTo("flow-stage3-owner");
    }

    @Test
    void ruleIdCannotBeMovedFromAnotherDispatchFlow() {
        seedAgent(AGENT_1, "Stage 3 Rule Ownership Agent", TENANT);
        seedCapability(CAP_A);
        DispatchFlowManagementService service = service();

        DispatchFlowView owner = explicitAggregate(
                "Rule owner aggregate",
                List.of(rule("rule-stage3-shared", "EVT_STAGE3_OWNER_RULE", CAP_A)),
                List.of(skill("skill-stage3-owner-rule", "rule-stage3-shared", CAP_A)),
                List.of(agent("flow-agent-stage3-owner-rule", AGENT_1)));
        reidentify(owner, "flow-stage3-rule-owner", "FLOW_STAGE3_RULE_OWNER");
        inTransaction(() -> service.createOrUpdateFlow(owner));

        DispatchFlowView stealing = explicitAggregate(
                "Must not steal Rule",
                List.of(rule("rule-stage3-shared", "EVT_STAGE3_STEAL_RULE", CAP_A)),
                List.of(skill("skill-stage3-steal-rule", "rule-stage3-shared", CAP_A)),
                List.of(agent("flow-agent-stage3-steal-rule", AGENT_1)));
        assertThatThrownBy(() -> inTransaction(() -> service.createOrUpdateFlow(stealing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flow Rule ID is already owned by another Dispatch Flow");

        assertThat(jdbc.queryForObject(
                "select flow_id from dispatch_policies where policy_id=?",
                String.class, "rule-stage3-shared")).isEqualTo("flow-stage3-rule-owner");
    }

    @Test
    void concurrentSameFlowReplacementsNeverLeaveAMixedAggregate() throws Exception {
        seedAgent(AGENT_1, "Stage 3 Concurrent Agent 1", TENANT);
        seedAgent(AGENT_2, "Stage 3 Concurrent Agent 2", TENANT);
        seedCapability(CAP_A);
        seedCapability(CAP_B);
        DispatchFlowManagementService service = service();

        DispatchFlowView aggregateA = explicitAggregate(
                "Concurrent aggregate A",
                List.of(rule("rule-stage3-concurrent-a", "EVT_STAGE3_CONCURRENT_A", CAP_A)),
                List.of(skill("skill-stage3-concurrent-a", "rule-stage3-concurrent-a", CAP_A)),
                List.of(agent("flow-agent-stage3-concurrent-a", AGENT_1)));
        DispatchFlowView aggregateB = explicitAggregate(
                "Concurrent aggregate B",
                List.of(rule("rule-stage3-concurrent-b", "EVT_STAGE3_CONCURRENT_B", CAP_B)),
                List.of(skill("skill-stage3-concurrent-b", "rule-stage3-concurrent-b", CAP_B)),
                List.of(agent("flow-agent-stage3-concurrent-b", AGENT_2)));

        runConcurrent(List.of(
                () -> inTransactionResult(() -> service.createOrUpdateFlow(aggregateA)),
                () -> inTransactionResult(() -> service.createOrUpdateFlow(aggregateB))));

        DispatchFlowView finalFlow = service.findFlow(TENANT, FLOW_ID).orElseThrow();
        assertThat(finalFlow.getRules()).hasSize(1);
        assertThat(finalFlow.getRequiredSkills()).hasSize(1);
        assertThat(finalFlow.getAgents()).hasSize(1);
        String finalRule = finalFlow.getRules().getFirst().getRuleId();
        if ("rule-stage3-concurrent-a".equals(finalRule)) {
            assertThat(finalFlow.getFlowName()).isEqualTo("Concurrent aggregate A");
            assertThat(finalFlow.getRequiredSkills().getFirst().getSkillCode()).isEqualTo(CAP_A);
            assertThat(finalFlow.getAgents().getFirst().getAgentId()).isEqualTo(AGENT_1);
        } else {
            assertThat(finalRule).isEqualTo("rule-stage3-concurrent-b");
            assertThat(finalFlow.getFlowName()).isEqualTo("Concurrent aggregate B");
            assertThat(finalFlow.getRequiredSkills().getFirst().getSkillCode()).isEqualTo(CAP_B);
            assertThat(finalFlow.getAgents().getFirst().getAgentId()).isEqualTo(AGENT_2);
        }
    }

    @Test
    void childWriteFailureRollsBackParentAndEveryChild() {
        seedAgent(AGENT_1, "Stage 3 Rollback Agent", TENANT);
        seedCapability(CAP_A);
        DispatchFlowManagementService service = service();

        inTransaction(() -> service.createOrUpdateFlow(explicitAggregate(
                "Before rollback",
                List.of(rule("rule-stage3-a", "EVT_STAGE3_A", CAP_A)),
                List.of(skill("skill-stage3-a", "rule-stage3-a", CAP_A)),
                List.of(agent("flow-agent-stage3-1", AGENT_1)))));

        DispatchFlowRequiredSkillView invalid = skill("skill-stage3-a", "rule-stage3-a", CAP_A);
        invalid.setSkillName("X".repeat(300)); // flow_required_skills.skill_name is varchar(255)
        DispatchFlowView failing = explicitAggregate(
                "Must rollback",
                List.of(rule("rule-stage3-a", "EVT_STAGE3_A", CAP_A)),
                List.of(invalid),
                List.of(agent("flow-agent-stage3-1", AGENT_1)));

        assertThatThrownBy(() -> inTransaction(() -> service.createOrUpdateFlow(failing)))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
                "select flow_name from dispatch_flows where tenant_id=? and flow_id=?",
                String.class, TENANT, FLOW_ID)).isEqualTo("Before rollback");
        assertThat(count("dispatch_policies")).isEqualTo(1);
        assertThat(count("flow_required_skills")).isEqualTo(1);
        assertThat(count("flow_agent_assignments")).isEqualTo(1);
    }

    @Test
    void crossTenantAgentCannotBePersistedAsAFlowCandidate() {
        seedAgent(AGENT_1, "Other tenant Agent", "tenant-other");
        DispatchFlowManagementService service = service();

        assertThatThrownBy(() -> inTransaction(() -> service.createOrUpdateFlow(noCapabilityAggregate())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist in the selected tenant");

        assertThat(count("dispatch_flows")).isZero();
        assertThat(count("flow_agent_assignments")).isZero();
    }

    private DispatchFlowManagementService service() {
        return new DispatchFlowManagementService(namedJdbc(), new ObjectMapper());
    }

    private NamedParameterJdbcTemplate namedJdbc() {
        return new NamedParameterJdbcTemplate(jdbc.getDataSource());
    }

    private void inTransaction(Runnable action) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(status -> action.run());
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> action) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        return tx.execute(status -> action.get());
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where tenant_id=?", Long.class, TENANT);
    }

    private void seedAgent(String agentId, String name, String tenantId) {
        jdbc.update("""
                insert into agent_profiles(agent_id, tenant_id, agent_name, agent_type, approval_status, enabled)
                values (?, ?, ?, 'WORKER', 'APPROVED', true)
                """, agentId, tenantId, name);
    }

    private void seedCapability(String code) {
        jdbc.update("""
                insert into agent_capability_catalog(
                    tenant_id, capability_id, capability_code, capability_name,
                    status, is_dispatch_eligible, requires_approval)
                values (?, ?, ?, ?, 'ACTIVE', true, true)
                """, TENANT, "capability-" + code.toLowerCase(), code, code);
    }

    private DispatchFlowView noCapabilityAggregate() {
        DispatchFlowRuleView rule = rule("rule-stage3-none", "EVT_STAGE3_NONE", null);
        rule.setCapabilityRequirementMode(null);
        rule.setRequestedSkill(null);
        return aggregate("No Capability aggregate", List.of(rule), List.of(), List.of(agent("flow-agent-stage3-1", AGENT_1)));
    }

    private void reidentify(DispatchFlowView flow, String flowId, String flowCode) {
        flow.setFlowId(flowId);
        flow.setFlowCode(flowCode);
        flow.getRules().forEach(rule -> rule.setFlowId(flowId));
        flow.getRequiredSkills().forEach(skill -> skill.setFlowId(flowId));
        flow.getAgents().forEach(agent -> agent.setFlowId(flowId));
    }

    private DispatchFlowView explicitAggregate(
            String flowName,
            List<DispatchFlowRuleView> rules,
            List<DispatchFlowRequiredSkillView> skills,
            List<DispatchFlowAgentView> agents) {
        return aggregate(flowName, rules, skills, agents);
    }

    private DispatchFlowView aggregate(
            String flowName,
            List<DispatchFlowRuleView> rules,
            List<DispatchFlowRequiredSkillView> skills,
            List<DispatchFlowAgentView> agents) {
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId(TENANT);
        flow.setFlowId(FLOW_ID);
        flow.setFlowCode("FLOW_STAGE3_TDD");
        flow.setFlowName(flowName);
        flow.setSourceSystem(SOURCE);
        flow.setStatus("ACTIVE");
        flow.setRules(rules);
        flow.setRequiredSkills(skills);
        flow.setAgents(agents);
        return flow;
    }

    private DispatchFlowRuleView rule(String id, String eventType, String capability) {
        DispatchFlowRuleView rule = new DispatchFlowRuleView();
        rule.setTenantId(TENANT);
        rule.setFlowId(FLOW_ID);
        rule.setRuleId(id);
        rule.setRuleCode(id.toUpperCase().replace('-', '_'));
        rule.setRuleName(id);
        rule.setSourceSystem(SOURCE);
        rule.setEventStage("EXTERNAL");
        rule.setObjectType("OBJECT_STAGE3");
        rule.setEventType(eventType);
        rule.setErrorCode("*");
        rule.setEnabled(true);
        if (capability == null) {
            rule.setCapabilityRequirementMode("NONE");
        } else {
            rule.setCapabilityRequirementMode("EXPLICIT");
            rule.setRequestedSkill(capability);
        }
        return rule;
    }

    private DispatchFlowRequiredSkillView skill(String id, String ruleId, String capability) {
        DispatchFlowRequiredSkillView skill = new DispatchFlowRequiredSkillView();
        skill.setTenantId(TENANT);
        skill.setFlowId(FLOW_ID);
        skill.setId(id);
        skill.setRuleId(ruleId);
        skill.setEventStage("EXTERNAL");
        skill.setAgentRole("LEAD");
        skill.setSkillCode(capability);
        skill.setSkillName(capability);
        skill.setSkillKind("CAPABILITY");
        skill.setRequired(true);
        skill.setOpenClawSkill(false);
        return skill;
    }

    private DispatchFlowAgentView agent(String id, String agentId) {
        DispatchFlowAgentView agent = new DispatchFlowAgentView();
        agent.setTenantId(TENANT);
        agent.setFlowId(FLOW_ID);
        agent.setId(id);
        agent.setAgentId(agentId);
        agent.setEventStage("EXTERNAL");
        agent.setAgentRole("LEAD");
        return agent;
    }
}
