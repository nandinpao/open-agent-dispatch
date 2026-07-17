package com.opensocket.aievent.core.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCapability;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;

class AgentSkillRegistryServiceTest {
    private final AgentSkillRegistryService service = new AgentSkillRegistryService();

    @Test
    void shouldEvaluateExplicitGenericSkillFromApprovedAndReportedIntersection() {
        AgentSkillDefinition definition = new AgentSkillDefinition();
        definition.setSkillCode("CAP_RANDOM_REVIEW");
        definition.setDisplayName("Random review capability");
        definition.setDomain("DOMAIN_RANDOM");
        definition.setProviders(List.of("SOURCE_RANDOM"));
        definition.setTaskTypes(List.of("TASK_RANDOM"));
        definition.setOperations(List.of("ANALYZE"));
        definition.setToolPolicies(List.of("PROPOSE_ONLY"));
        definition.setEnabled(true);
        service.upsert(definition);

        AgentProfile profile = new AgentProfile();
        profile.setAgentId("agent-random-001");
        profile.setApprovalStatus(AgentApprovalStatus.APPROVED);
        profile.setEnabled(true);
        profile.setRiskStatus(AgentRiskStatus.NORMAL);
        profile.setCapabilities(List.of(new AgentCapability("agent-random-001", "CAP_RANDOM_REVIEW")));
        profile.getCapabilities().get(0).setEnabled(true);

        List<AgentRuntimeCapabilityItem> runtime = List.of(
                new AgentRuntimeCapabilityItem("agent-random-001", "skill", "CAP_RANDOM_REVIEW", "rev1", "runtime", null),
                new AgentRuntimeCapabilityItem("agent-random-001", "skillProvider", "SOURCE_RANDOM", "rev1", "runtime", null),
                new AgentRuntimeCapabilityItem("agent-random-001", "skillToolPolicy", "PROPOSE_ONLY", "rev1", "runtime", null)
        );
        AgentSkillEvaluationRequest request = new AgentSkillEvaluationRequest();
        request.setDomain("DOMAIN_RANDOM");
        request.setProvider("SOURCE_RANDOM");
        request.setTaskType("TASK_RANDOM");
        request.setRequiredToolPolicy("PROPOSE_ONLY");
        request.setRequiredCapabilities(List.of("CAP_RANDOM_REVIEW"));

        AgentSkillEvaluationResult result = service.evaluate(profile, runtime, request);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getMatchedSkillCodes()).contains("CAP_RANDOM_REVIEW");
    }
}
