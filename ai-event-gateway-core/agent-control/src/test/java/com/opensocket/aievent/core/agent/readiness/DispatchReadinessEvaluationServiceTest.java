package com.opensocket.aievent.core.agent.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.InMemoryAgentDirectoryRepository;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.agent.governance.InMemoryAgentGovernanceRepository;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.agent.skill.TaskDispatchContractResolverService;

class DispatchReadinessEvaluationServiceTest {
    @Test
    void shouldReturnBeginnerReadyChecklistWhenSkillGovernanceRuntimeAndTaskAlign() {
        Fixture fixture = new Fixture();
        fixture.upsertIncidentAnalysisSkill();
        fixture.registerApprovedAgent("agent-001", List.of("INCIDENT_ANALYSIS"), List.of("INCIDENT_ANALYSIS"));

        DispatchReadinessEvaluationRequest request = fixture.defaultRequest("agent-001");
        DispatchReadinessEvaluationResult result = fixture.service.evaluate(request);

        assertThat(result.isReady()).isTrue();
        assertThat(result.getBeginnerSummary()).contains("可以派工");
        assertThat(result.getChecks()).extracting(DispatchReadinessCheck::getKey)
                .contains("CAPABILITY_DEFINED", "EFFECTIVE_CAPABILITY_CONTRACT", "GOVERNANCE_APPROVED_CAPABILITY", "RUNTIME_REPORTED_CAPABILITY", "CAPABILITY_CONTRACT_ELIGIBLE");
        assertThat(result.getChecks()).allMatch(check -> check.getStatus() != DispatchReadinessStatus.FAIL);
    }

    @Test
    void shouldExplainGovernanceMissingCapabilityForBeginners() {
        Fixture fixture = new Fixture();
        fixture.upsertIncidentAnalysisSkill();
        fixture.registerApprovedAgent("agent-002", List.of("GENERAL_AGENT"), List.of("INCIDENT_ANALYSIS"));

        DispatchReadinessEvaluationResult result = fixture.service.evaluate(fixture.defaultRequest("agent-002"));

        assertThat(result.isReady()).isFalse();
        DispatchReadinessCheck check = result.getChecks().stream()
                .filter(item -> "GOVERNANCE_APPROVED_CAPABILITY".equals(item.getKey()))
                .findFirst()
                .orElseThrow();
        assertThat(check.getStatus()).isEqualTo(DispatchReadinessStatus.FAIL);
        assertThat(check.getBeginnerHint()).contains("Approve these capabilities");
        assertThat(check.getFixAction()).isNotNull();
        assertThat(check.getFixAction().getActionType()).isEqualTo("APPROVE_AGENT_CAPABILITY");
    }

    @Test
    void shouldTreatRuntimeCapabilityObservationAsOptional() {
        Fixture fixture = new Fixture();
        fixture.upsertIncidentAnalysisSkill();
        fixture.registerApprovedAgent("agent-003", List.of("INCIDENT_ANALYSIS"), List.of("GENERAL_AGENT"));

        DispatchReadinessEvaluationResult result = fixture.service.evaluate(fixture.defaultRequest("agent-003"));

        assertThat(result.isReady()).isFalse();
        DispatchReadinessCheck check = result.getChecks().stream()
                .filter(item -> "RUNTIME_REPORTED_CAPABILITY".equals(item.getKey()))
                .findFirst()
                .orElseThrow();
        assertThat(check.getStatus()).isEqualTo(DispatchReadinessStatus.PASS);
        assertThat(check.getBeginnerHint()).contains("Admin UI/Core");
        assertThat(check.getFixAction()).isNull();
    }


    @Test
    void shouldPreserveExplicitCapabilityWithoutNameBasedExpansion() {
        Fixture fixture = new Fixture();
        fixture.upsertRandomAnalysisSkill();
        fixture.registerApprovedAgent("agent-random", List.of("CAP_RANDOM_ANALYZE"), List.of("CAP_RANDOM_ANALYZE"));

        DispatchReadinessEvaluationRequest request = fixture.defaultRequest("agent-random");
        request.setRequiredCapabilities(List.of("CAP_RANDOM_ANALYZE"));
        request.setTaskType("TASK_RANDOM");
        request.setDomain("DOMAIN_RANDOM");
        request.setProvider("SOURCE_RANDOM");
        request.setOperation("ANALYZE");

        DispatchReadinessEvaluationResult result = fixture.service.evaluate(request);

        assertThat(result.getRawTaskRequirements()).containsExactly("CAP_RANDOM_ANALYZE");
        assertThat(result.getEffectiveDispatchCapabilities()).containsExactly("CAP_RANDOM_ANALYZE");
        assertThat(result.getLegacyTaskAliases()).isEmpty();
        assertThat(result.getChecks()).filteredOn(check -> "GOVERNANCE_APPROVED_CAPABILITY".equals(check.getKey())).first()
                .extracting(DispatchReadinessCheck::getStatus).isEqualTo(DispatchReadinessStatus.PASS);
        assertThat(result.getChecks()).filteredOn(check -> "RUNTIME_REPORTED_CAPABILITY".equals(check.getKey())).first()
                .extracting(DispatchReadinessCheck::getStatus).isEqualTo(DispatchReadinessStatus.PASS);
    }

    private static class Fixture {
        final InMemoryAgentGovernanceRepository governanceRepository = new InMemoryAgentGovernanceRepository();
        final AgentDirectoryService directoryService = new AgentDirectoryService(new InMemoryAgentDirectoryRepository());
        final AgentSkillRegistryService skillRegistryService = new AgentSkillRegistryService();
        final DispatchReadinessEvaluationService service = new DispatchReadinessEvaluationService(
                directoryService,
                governanceRepository,
                skillRegistryService,
                new TaskDispatchContractResolverService(skillRegistryService)
        );

        void upsertIncidentAnalysisSkill() {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("INCIDENT_ANALYSIS");
            skill.setDisplayName("Incident Analysis");
            skill.setDomain("MES");
            skill.setProviders(List.of("MES"));
            skill.setTaskTypes(List.of("INCIDENT_RESPONSE", "INCIDENT_ANALYSIS"));
            skill.setOperations(List.of("ANALYZE"));
            skill.setToolPolicies(List.of("READ_ONLY"));
            skill.setDataClasses(List.of("PRODUCTION"));
            skill.setRiskLevel("MEDIUM");
            skill.setEnabled(true);
            skillRegistryService.upsert(skill);
        }


        void upsertRandomAnalysisSkill() {
            AgentSkillDefinition skill = new AgentSkillDefinition();
            skill.setSkillCode("CAP_RANDOM_ANALYZE");
            skill.setDisplayName("Random analysis capability");
            skill.setDomain("DOMAIN_RANDOM");
            skill.setProviders(List.of("SOURCE_RANDOM"));
            skill.setTaskTypes(List.of("TASK_RANDOM"));
            skill.setOperations(List.of("ANALYZE"));
            skill.setToolPolicies(List.of("READ_ONLY"));
            skill.setDataClasses(List.of("PRODUCTION"));
            skill.setRiskLevel("MEDIUM");
            skill.setEnabled(true);
            skillRegistryService.upsert(skill);
        }

        void registerApprovedAgent(String agentId, List<String> governanceCapabilities, List<String> runtimeCapabilities) {
            AgentProfile profile = new AgentProfile();
            profile.setAgentId(agentId);
            profile.setApprovalStatus(AgentApprovalStatus.APPROVED);
            profile.setEnabled(true);
            profile.setRiskStatus(AgentRiskStatus.NORMAL);
            profile.setCapabilities(governanceCapabilities.stream().map(value -> {
                AgentCapability capability = new AgentCapability(agentId, value);
                capability.setEnabled(true);
                return capability;
            }).toList());
            governanceRepository.saveProfile(profile);
            governanceRepository.replaceCapabilities(agentId, profile.getCapabilities());

            AgentSnapshot snapshot = new AgentSnapshot();
            snapshot.setAgentId(agentId);
            snapshot.setSiteId("LOCAL");
            snapshot.setStatus(AgentStatus.IDLE);
            snapshot.setCapabilities(runtimeCapabilities);
            snapshot.setMaxConcurrentTasks(2);
            snapshot.setAvailableSlots(2);
            directoryService.register(snapshot);
        }

        DispatchReadinessEvaluationRequest defaultRequest(String agentId) {
            DispatchReadinessEvaluationRequest request = new DispatchReadinessEvaluationRequest();
            request.setTenantId("tenant-random");
            request.setAgentId(agentId);
            request.setDomain("MES");
            request.setProvider("MES");
            request.setTaskType("INCIDENT_RESPONSE");
            request.setOperation("ANALYZE");
            request.setRequiredToolPolicy("READ_ONLY");
            request.setSiteCode("LOCAL");
            request.setRequiredCapabilities(List.of("INCIDENT_ANALYSIS"));
            request.setDataClasses(List.of("PRODUCTION"));
            return request;
        }
    }
}
