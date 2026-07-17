package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.setup.AgentSetupRequest;
import com.opensocket.aievent.core.agent.setup.AgentSetupResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.database.persistence.agent.assignment.converter.AgentAssignmentPersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.assignment.dao.AgentAssignmentDao;
import com.opensocket.aievent.database.persistence.agent.assignment.repository.MybatisAgentAssignmentRepository;

/**
 * Stage 8-F0f production contract: backend Agent setup must create the tenant-scoped
 * Agent profile and active runtime binding needed by Dispatch Flow aggregate creation.
 */
class Stage8AgentSetupFlowPreconditionContainerTest extends P25RepositoryDbContainerSupport {
    private static final String TENANT = "tenant-stage8-f0f";
    private static final String AGENT = "agent-stage8-f0f-001";
    private static final String SOURCE = "SRC_STAGE8_F0F";
    private static final String FLOW = "flow-stage8-f0f";

    @Test
    void setupAgentIsIdempotentAndThenDispatchFlowCanSelectTheAgent() {
        AgentSetupService setupService = setupService();

        AgentSetupResponse first = setupService.setupAgent(setupRequest());
        AgentSetupResponse second = setupService.setupAgent(setupRequest());

        assertThat(first.getAgentProfile()).isNotNull();
        assertThat(first.getAgentProfile().getTenantId()).isEqualTo(TENANT);
        assertThat(first.getAgentProfile().getAgentId()).isEqualTo(AGENT);
        assertThat(second.getRuntimeBinding()).isNotNull();
        assertThat(second.getRuntimeBinding().getBindingId()).isEqualTo(first.getRuntimeBinding().getBindingId());
        assertThat(second.getRuntimeBinding().getBindingStatus()).isEqualTo("ACTIVE");

        assertThat(countWhere("agent_profiles", "tenant_id=? and agent_id=?", TENANT, AGENT)).isEqualTo(1);
        assertThat(countWhere("agent_runtime_bindings", "tenant_id=? and agent_id=? and binding_status='ACTIVE'", TENANT, AGENT)).isEqualTo(1);

        DispatchFlowView saved = inTransactionResult(() -> flowService().createOrUpdateFlow(flowAggregate()));

        assertThat(saved.getFlowId()).isEqualTo(FLOW);
        assertThat(saved.getRules()).extracting(DispatchFlowRuleView::getRuleId)
                .containsExactly("rule-stage8-f0f");
        assertThat(saved.getAgents()).extracting(DispatchFlowAgentView::getAgentId)
                .containsExactly(AGENT);
        assertThat(countWhere("dispatch_policies", "tenant_id=? and flow_id=?", TENANT, FLOW)).isEqualTo(1);
        assertThat(countWhere("flow_agent_assignments", "tenant_id=? and flow_id=? and agent_id=?", TENANT, FLOW, AGENT)).isEqualTo(1);
    }

    private AgentSetupRequest setupRequest() {
        AgentSetupRequest request = new AgentSetupRequest();
        request.setTenantId(TENANT);
        request.setAgentId(AGENT);
        request.setAgentName("Stage 8 F0f Agent");
        request.setPurpose("FLOW_EXECUTION");
        request.setCredentialToken("stage8-f0f-token");
        request.setAutoApprove(true);
        request.setCreateRuntimeBinding(true);
        request.setCapacityLimit(3);
        request.setGatewayUrl("http://127.0.0.1:18081");
        return request;
    }

    private DispatchFlowView flowAggregate() {
        DispatchFlowRuleView rule = new DispatchFlowRuleView();
        rule.setTenantId(TENANT);
        rule.setFlowId(FLOW);
        rule.setRuleId("rule-stage8-f0f");
        rule.setRuleCode("RULE_STAGE8_F0F");
        rule.setRuleName("Stage 8 F0f rule");
        rule.setSourceSystem(SOURCE);
        rule.setEventStage("EXTERNAL");
        rule.setObjectType("OBJECT_STAGE8_F0F");
        rule.setEventType("EVENT_STAGE8_F0F");
        rule.setErrorCode("*");
        rule.setCapabilityRequirementMode("NONE");
        rule.setCandidatePoolMode("EXPLICIT_FLOW_AGENTS");
        rule.setEnabled(true);

        DispatchFlowAgentView agent = new DispatchFlowAgentView();
        agent.setTenantId(TENANT);
        agent.setFlowId(FLOW);
        agent.setId("flow-agent-stage8-f0f");
        agent.setAgentId(AGENT);
        agent.setEventStage("EXTERNAL");
        agent.setAgentRole("LEAD");

        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId(TENANT);
        flow.setFlowId(FLOW);
        flow.setFlowCode("FLOW_STAGE8_F0F");
        flow.setFlowName("Stage 8 F0f Flow");
        flow.setSourceSystem(SOURCE);
        flow.setStatus("ACTIVE");
        flow.setDefaultCapabilityRequirementMode("NONE");
        flow.setDefaultCandidatePoolMode("EXPLICIT_FLOW_AGENTS");
        flow.setRules(List.of(rule));
        flow.setAgents(List.of(agent));
        flow.setRequiredSkills(List.of());
        return flow;
    }

    private AgentSetupService setupService() {
        AgentGovernanceService governance = new AgentGovernanceService(agentGovernanceRepository());
        AgentAssignmentService assignment = new AgentAssignmentService(agentAssignmentRepository());
        return new AgentSetupService(governance, assignment, null);
    }

    private MybatisAgentAssignmentRepository agentAssignmentRepository() {
        return new MybatisAgentAssignmentRepository(
                mybatis.mapper(AgentAssignmentDao.class),
                new AgentAssignmentPersistenceConverter(objectMapper));
    }

    private DispatchFlowManagementService flowService() {
        return new DispatchFlowManagementService(namedJdbc(), new ObjectMapper());
    }

    private NamedParameterJdbcTemplate namedJdbc() {
        return new NamedParameterJdbcTemplate(jdbc.getDataSource());
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> action) {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        return tx.execute(status -> action.get());
    }

    private long countWhere(String table, String where, Object... args) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + where, Long.class, args);
    }
}
