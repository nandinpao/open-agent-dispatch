package com.opensocket.aievent.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.CapacityReservationResult;
import com.opensocket.aievent.core.agent.InMemoryAgentDirectoryRepository;
import com.opensocket.aievent.core.agent.InMemoryAgentRuntimeStateRepository;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.InMemoryAgentGovernanceRepository;
import com.opensocket.aievent.core.agent.skill.AgentDispatchSkillEvaluationService;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class SkillAwareRoutingDecisionServiceTest {
    @Test
    void shouldNotRouteOnRuntimeOnlyCapabilityWhenCoreDidNotApproveIt() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-runtime-only-erp", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        governance.saveProfile(profile("agent-runtime-only-erp", "MES_ALARM_TRIAGE"));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        properties.setSkillAwareEnabled(false);
        properties.setSkillAwareEnforced(false);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        assertThat(decision.getDecisionReason()).contains("DISPATCH_SCORE_BELOW_THRESHOLD");
        assertThat(decision.getUserFacingError().getCode()).isEqualTo(DispatchUserFacingErrorCode.DISPATCH_SCORE_BELOW_THRESHOLD);
        assertThat(decision.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.agentId()).isEqualTo("agent-runtime-only-erp");
            assertThat(candidate.score()).isLessThan(50);
            assertThat(candidate.missingCapabilities()).contains("ERP_PURCHASE_ORDER_REVIEW");
        });
    }


    @Test
    void shouldRouteWhenApprovedCapabilitiesAreLoadedFromGovernanceCapabilityRows() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-db-detached-profile", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        AgentProfile detachedProfile = profile("agent-db-detached-profile", "ERP_PURCHASE_ORDER_REVIEW");
        detachedProfile.setCapabilities(List.of());
        governance.saveProfile(detachedProfile);
        governance.replaceCapabilities("agent-db-detached-profile", List.of(
                new AgentCapability("agent-db-detached-profile", "ERP_PURCHASE_ORDER_REVIEW"),
                new AgentCapability("agent-db-detached-profile", "PROPOSE_ONLY")));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        properties.setSkillAwareEnabled(false);
        properties.setSkillAwareEnforced(false);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-db-detached-profile");
        assertThat(decision.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.score()).isGreaterThanOrEqualTo(50);
            assertThat(candidate.missingCapabilities()).isEmpty();
            assertThat(candidate.matchedCapabilities()).contains("ERP_PURCHASE_ORDER_REVIEW", "PROPOSE_ONLY");
        });
    }

    @Test
    void shouldPreferAgentThatPassesSkillRegistryContractWhenEnabled() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-erp", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        directory.register(agent("agent-wrong", "MES_ALARM_TRIAGE", "MES", "MES", "READ_ONLY"));
        governance.saveProfile(profile("agent-erp", "ERP_PURCHASE_ORDER_REVIEW"));
        governance.saveProfile(profile("agent-wrong", "MES_ALARM_TRIAGE"));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        properties.setSkillAwareEnabled(true);
        properties.setSkillAwareEnforced(true);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "skillRegistryService", skillRegistry);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-erp");
        assertThat(decision.getDecisionReason()).contains("skill=").contains("eligible");
        assertThat(decision.getCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.agentId()).isEqualTo("agent-wrong");
            assertThat(candidate.score()).isLessThan(50);
            assertThat(candidate.missingCapabilities()).anyMatch(value -> value.startsWith("skill:"));
        });
    }



    @Test
    void shouldNotRouteWhenGovernanceProfileIsDisabledEvenIfRuntimeAndApprovedSkillMatch() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-disabled-governance", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        governance.saveProfile(profile("agent-disabled-governance", "ERP_PURCHASE_ORDER_REVIEW", AgentApprovalStatus.APPROVED, false));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        assertThat(decision.getSelectedAgentId()).isNull();
        assertThat(decision.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.agentId()).isEqualTo("agent-disabled-governance");
            assertThat(candidate.score()).isLessThan(50);
            assertThat(candidate.missingCapabilities()).contains("ERP_PURCHASE_ORDER_REVIEW", "PROPOSE_ONLY");
        });
    }

    @Test
    void shouldNotRouteWhenGovernanceProfileIsRejectedEvenIfRuntimeAndSkillMatch() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-rejected-governance", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        governance.saveProfile(profile("agent-rejected-governance", "ERP_PURCHASE_ORDER_REVIEW", AgentApprovalStatus.REJECTED, true));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        assertThat(decision.getSelectedAgentId()).isNull();
        assertThat(decision.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.agentId()).isEqualTo("agent-rejected-governance");
            assertThat(candidate.score()).isLessThan(50);
            assertThat(candidate.missingCapabilities()).contains("ERP_PURCHASE_ORDER_REVIEW", "PROPOSE_ONLY");
        });
    }


    @Test
    void shouldNotRouteOfflineAgentAfterRuntimeDisconnectEvenIfGovernanceAndSkillMatch() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-runtime-disconnected", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        directory.disconnected("gateway-1", "agent-runtime-disconnected", "session-agent-runtime-disconnected", "operator disabled runtime");
        governance.saveProfile(profile("agent-runtime-disconnected", "ERP_PURCHASE_ORDER_REVIEW"));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        assertThat(decision.getSelectedAgentId()).isNull();
        assertThat(decision.getCandidates()).isNotEmpty();
        assertThat(decision.getUserFacingError()).isNotNull();
        assertThat(decision.getUserFacingError().getCode()).isEqualTo(DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE);
        assertThat(decision.getDecisionReason()).contains("DISPATCH_AGENT_NOT_ASSIGNABLE");
    }

    @Test
    void shouldNotRouteAgentWhileRuntimeBackoffIsActiveAfterDispatchFailure() throws Exception {
        InMemoryAgentRuntimeStateRepository runtimeState = new InMemoryAgentRuntimeStateRepository();
        AgentDirectoryService directory = new AgentDirectoryService(
                new InMemoryAgentDirectoryRepository(),
                new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(),
                runtimeState);
        InMemoryAgentGovernanceRepository governance = new InMemoryAgentGovernanceRepository();
        AgentSkillRegistryService skillRegistry = new AgentSkillRegistryService();
        AgentDispatchSkillEvaluationService dispatchSkillEvaluation = new AgentDispatchSkillEvaluationService(
                skillRegistry, governance, runtimeState);

        directory.register(agent("agent-runtime-backoff", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY"));
        directory.applyRuntimeBackoff(
                "agent-runtime-backoff",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1),
                "gateway dispatch failed; waiting for recovery");
        governance.saveProfile(profile("agent-runtime-backoff", "ERP_PURCHASE_ORDER_REVIEW"));

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        RoutingDecisionService service = new RoutingDecisionService(directory, new InMemoryRoutingDecisionRepository(), properties);
        set(service, "dispatchSkillEvaluationService", dispatchSkillEvaluation);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.NO_CANDIDATE);
        assertThat(decision.getSelectedAgentId()).isNull();
        assertThat(decision.getCandidates()).isNotEmpty();
        assertThat(decision.getUserFacingError()).isNotNull();
        assertThat(decision.getUserFacingError().getCode()).isEqualTo(DispatchUserFacingErrorCode.DISPATCH_AGENT_NOT_ASSIGNABLE);
        assertThat(directory.findById("agent-runtime-backoff").orElseThrow().isRuntimeBackoffActive()).isTrue();
    }

    @Test
    void shouldDeduplicateRoutingCandidatesByAgentIdBeforeScoring() {
        AgentSnapshot duplicateA = agent("agent-dup", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY");
        AgentSnapshot duplicateB = agent("agent-dup", "ERP_PURCHASE_ORDER_REVIEW", "ERP", "SAP", "PROPOSE_ONLY");
        duplicateB.setAgentSessionId("session-agent-dup-newer-copy");

        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(0);

        RoutingDecisionService service = new RoutingDecisionService(
                new StaticAgentDirectory(List.of(duplicateA, duplicateB)),
                new InMemoryRoutingDecisionRepository(),
                properties);

        RoutingDecisionRecord decision = service.decide(task());

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getCandidates()).hasSize(1);
        assertThat(decision.getCandidates().getFirst().agentId()).isEqualTo("agent-dup");
        assertThat(decision.getSelectedAgentSessionId()).isEqualTo("session-agent-dup");
    }

    private TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-erp-po");
        task.setTaskType(TaskType.INCIDENT_RESPONSE);
        task.setStatus(TaskStatus.QUEUED);
        task.setSiteId("TPE");
        task.setRoutingPolicy("CAPABILITY_FIRST");
        task.setRequiredCapabilities(List.of("ERP_PURCHASE_ORDER_REVIEW", "PROPOSE_ONLY"));
        return task;
    }

    private AgentSnapshot agent(String agentId, String skillCode, String domain, String provider, String toolPolicy) {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId(agentId);
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId("gateway-1");
        agent.setAgentSessionId("session-" + agentId);
        agent.setSiteId("TPE");
        agent.setStatus(AgentStatus.IDLE);
        agent.setAvailableSlots(2);
        agent.setMaxConcurrentTasks(3);
        agent.setHealthScore(100);
        agent.setCapabilities(List.of("ERP_PURCHASE_ORDER_REVIEW", "PROPOSE_ONLY"));
        agent.setCapabilityProfile(Map.of(
                "supportedTaskTypes", List.of(skillCode),
                "supportedIssueProviders", List.of(provider),
                "toolPolicies", List.of(toolPolicy),
                "skills", List.of(Map.of(
                        "skillCode", skillCode,
                        "domain", domain,
                        "providers", List.of(provider),
                        "taskTypes", List.of(skillCode),
                        "operations", List.of("READ", "ANALYZE", "PROPOSE"),
                        "toolPolicies", List.of(toolPolicy),
                        "dataClasses", List.of("FINANCIAL")
                ))));
        return agent;
    }

    private AgentProfile profile(String agentId, String capabilityCode) {
        return profile(agentId, capabilityCode, AgentApprovalStatus.APPROVED, true);
    }

    private AgentProfile profile(String agentId, String capabilityCode, AgentApprovalStatus approvalStatus, boolean enabled) {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId(agentId);
        profile.setAgentType("OPENCLAW");
        profile.setApprovalStatus(approvalStatus);
        profile.setEnabled(enabled);
        profile.setCapabilities(List.of(new AgentCapability(agentId, capabilityCode), new AgentCapability(agentId, "PROPOSE_ONLY")));
        return profile;
    }


    private static final class StaticAgentDirectory implements AgentDirectoryFacade {
        private final List<AgentSnapshot> candidates;

        private StaticAgentDirectory(List<AgentSnapshot> candidates) {
            this.candidates = candidates;
        }

        @Override
        public Optional<AgentSnapshot> findById(String agentId) {
            return candidates.stream().filter(agent -> agentId.equals(agent.getAgentId())).findFirst();
        }

        @Override
        public List<AgentSnapshot> findCandidates(AgentQuery query) {
            return candidates;
        }

        @Override
        public CapacityReservationResult reserveCapacity(String agentId) {
            return CapacityReservationResult.rejected(agentId, "not under test");
        }

        @Override
        public boolean releaseCapacity(String agentId) {
            return false;
        }

        @Override
        public String mode() {
            return "STATIC_TEST";
        }
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
