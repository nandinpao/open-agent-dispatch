package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.dispatch.flow.FlowRuleRuntimeQuery;
import com.opensocket.aievent.database.persistence.dispatch.flow.JdbcFlowRuleRoutingRepository;
import com.opensocket.aievent.database.persistence.dispatch.governance.repository.JdbcGenericCandidateAgentRepository;

/**
 * Integration-first proof that the standard Flow runtime is independent of
 * Assignment Profile, Service Scope, Task Scope, Qualification and historical
 * Skill registry rows.
 */
class Stage7LegacyIsolationContainerTest extends P25RepositoryDbContainerSupport {

    private static final String TENANT = "tenant-stage7-isolation";
    private static final String SOURCE = "SRC_STAGE7_ARBITRARY";
    private static final String FLOW_ID = "flow-stage7-isolation";
    private static final String RULE_ID = "rule-stage7-isolation";
    private static final String AGENT_ID = "agent-stage7-isolation";

    @Test
    void standardFlowRoutingWorksWithTenantLegacyRowsEmpty() {
        seedApprovedAgent();
        saveFlow();

        assertThat(tenantCount("agent_assignment_profiles")).isZero();
        assertThat(tenantCount("agent_qualifications")).isZero();
        assertThat(tenantCount("dispatch_task_definitions")).isZero();
        assertThat(tenantCount("assignment_profile_capability_bindings")).isZero();
        assertStandardRuntimeReadsFlowAndAgent();
    }

    @Test
    void conflictingLegacyRowsCannotChangeFlowRuleOrCandidateSelection() {
        seedApprovedAgent();
        saveFlow();
        assertStandardRuntimeReadsFlowAndAgent();

        insertConflictingLegacyEvidence();

        assertThat(tenantCount("agent_assignment_profiles")).isEqualTo(1);
        assertThat(tenantCount("agent_qualifications")).isEqualTo(1);
        assertThat(tenantCount("dispatch_task_definitions")).isEqualTo(1);
        assertThat(tenantCount("assignment_profile_capability_bindings")).isEqualTo(1);
        assertStandardRuntimeReadsFlowAndAgent();

        deleteTenantLegacyEvidence();
        assertStandardRuntimeReadsFlowAndAgent();
    }

    private void assertStandardRuntimeReadsFlowAndAgent() {
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbc.getDataSource());
        JdbcFlowRuleRoutingRepository rules = new JdbcFlowRuleRoutingRepository(namedJdbc);
        JdbcGenericCandidateAgentRepository candidates = new JdbcGenericCandidateAgentRepository(namedJdbc);

        FlowRuleRuntimeQuery query = new FlowRuleRuntimeQuery();
        query.setTenantId(TENANT);
        query.setSourceSystem(SOURCE);
        query.setEventStage("EXTERNAL");
        query.setObjectType("OBJECT_STAGE7");
        query.setEventType("EVENT_STAGE7");
        query.setErrorCode("*");

        var matched = rules.findBestMatch(query).orElseThrow();
        assertThat(matched.getFlowId()).isEqualTo(FLOW_ID);
        assertThat(matched.getRuleId()).isEqualTo(RULE_ID);
        assertThat(matched.getCandidatePoolMode()).isEqualTo("EXPLICIT_FLOW_AGENTS");
        assertThat(matched.getCapabilityRequirementMode()).isEqualTo("NONE");
        assertThat(matched.getRequestedSkill()).isNull();
        assertThat(matched.getRequiredSkills()).isEmpty();
        assertThat(candidates.findExplicitFlowAgentIds(TENANT, FLOW_ID, "EXTERNAL", 20))
                .containsExactly(AGENT_ID);
        assertThat(candidates.findExplicitFlowAgentIds("TENANT-STAGE7-ISOLATION", "FLOW_STAGE7_ISOLATION", "EXTERNAL", 20))
                .containsExactly(AGENT_ID);
    }

    private void seedApprovedAgent() {
        jdbc.update("""
                insert into agent_profiles(agent_id, tenant_id, agent_name, agent_type, approval_status, enabled)
                values (?, ?, ?, 'WORKER', 'APPROVED', true)
                """, AGENT_ID, TENANT, "Stage 7 Standard Agent");
    }

    private void saveFlow() {
        DispatchFlowRuleView rule = new DispatchFlowRuleView();
        rule.setTenantId(TENANT);
        rule.setFlowId(FLOW_ID);
        rule.setRuleId(RULE_ID);
        rule.setRuleCode("RULE_STAGE7_ISOLATION");
        rule.setRuleName("Stage 7 isolation rule");
        rule.setSourceSystem(SOURCE);
        rule.setEventStage("EXTERNAL");
        rule.setObjectType("OBJECT_STAGE7");
        rule.setEventType("EVENT_STAGE7");
        rule.setErrorCode("*");
        rule.setCapabilityRequirementMode("NONE");
        rule.setCandidatePoolMode("EXPLICIT_FLOW_AGENTS");
        rule.setEnabled(true);

        DispatchFlowAgentView agent = new DispatchFlowAgentView();
        agent.setTenantId(TENANT);
        agent.setFlowId(FLOW_ID);
        agent.setId("flow-agent-stage7-isolation");
        agent.setAgentId(AGENT_ID);
        agent.setEventStage("EXTERNAL");
        agent.setAgentRole("LEAD");

        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId(TENANT);
        flow.setFlowId(FLOW_ID);
        flow.setFlowCode("FLOW_STAGE7_ISOLATION");
        flow.setFlowName("Stage 7 Legacy-independent Flow");
        flow.setSourceSystem(SOURCE);
        flow.setStatus("ACTIVE");
        flow.setRules(List.of(rule));
        flow.setRequiredSkills(List.of());
        flow.setAgents(List.of(agent));

        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(status -> new DispatchFlowManagementService(
                new NamedParameterJdbcTemplate(jdbc.getDataSource()), new ObjectMapper())
                .createOrUpdateFlow(flow));
    }

    private void insertConflictingLegacyEvidence() {
        jdbc.update("""
                insert into dispatch_task_definitions(
                    definition_id, tenant_id, source_system, task_type, display_name, status)
                values ('legacy-taskdef-stage7', ?, ?, 'LEGACY_TASK_SCOPE', 'Conflicting legacy task scope', 'ACTIVE')
                """, TENANT, SOURCE);
        jdbc.update("""
                insert into agent_assignment_profiles(
                    profile_id, tenant_id, profile_code, profile_name, agent_type,
                    task_definition_id, source_system, task_type, is_active,
                    allowed_task_types_json, allowed_issue_providers_json,
                    required_runtime_features_json, metadata_json)
                values ('legacy-profile-stage7', ?, 'LEGACY_BLOCKING_PROFILE',
                    'Conflicting legacy profile', 'WORKER', 'legacy-taskdef-stage7', ?,
                    'LEGACY_TASK_SCOPE', true, '["LEGACY_TASK_SCOPE"]'::jsonb,
                    '["LEGACY_PROVIDER"]'::jsonb, '["LEGACY_RUNTIME_FEATURE"]'::jsonb,
                    '{"stage7Conflict":true}'::jsonb)
                """, TENANT, SOURCE);
        jdbc.update("""
                insert into agent_qualifications(
                    qualification_id, tenant_id, agent_id, profile_code,
                    qualification_status, evidence_type, reason)
                values ('legacy-qualification-stage7', ?, ?, 'LEGACY_BLOCKING_PROFILE',
                    'REVOKED', 'MANUAL', 'Must not alter Flow routing')
                """, TENANT, AGENT_ID);
        jdbc.update("""
                insert into assignment_profile_capability_bindings(
                    tenant_id, binding_id, profile_code, capability_code,
                    capability_name, binding_mode, is_required, is_active, approval_status)
                values (?, 'legacy-binding-stage7', 'LEGACY_BLOCKING_PROFILE',
                    'LEGACY_MISSING_CAPABILITY', 'Legacy missing capability',
                    'REQUIRED', true, true, 'ACTIVE')
                """, TENANT);
    }

    private void deleteTenantLegacyEvidence() {
        jdbc.update("delete from assignment_profile_capability_bindings where tenant_id=?", TENANT);
        jdbc.update("delete from agent_qualifications where tenant_id=?", TENANT);
        jdbc.update("delete from agent_assignment_profiles where tenant_id=?", TENANT);
        jdbc.update("delete from dispatch_task_definitions where tenant_id=?", TENANT);
    }

    private long tenantCount(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where tenant_id=?", Long.class, TENANT);
    }
}
