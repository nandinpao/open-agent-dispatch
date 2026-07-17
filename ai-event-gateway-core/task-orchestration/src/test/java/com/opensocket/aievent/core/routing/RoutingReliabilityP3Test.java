package com.opensocket.aievent.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.CapacityReservationResult;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

class RoutingReliabilityP3Test {
    @Test
    void shouldExcludePoisonAgentWithoutUsingSourceSpecificPolicy() {
        AgentSnapshot poison = agent("agent-poison", "TPE", "CAP_RANDOM_REVIEW");
        poison.setRuntimeFailureCount(5);
        poison.setHealthScore(100);
        AgentSnapshot healthy = agent("agent-healthy", "TPE", "CAP_RANDOM_REVIEW");
        healthy.setHealthScore(70);

        RoutingProperties properties = properties();
        properties.setMinimumScore(0);
        properties.setPoisonAgentFailureThreshold(5);

        RoutingDecisionRecord decision = service(List.of(poison, healthy), properties)
                .decide(task("CAPABILITY_FIRST", "CAP_RANDOM_REVIEW"));

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-healthy");
        assertThat(decision.getDecisionReason()).contains("poisonAgentExcluded=[agent-poison]");
        assertThat(decision.getCandidates()).extracting(AgentCandidateScore::agentId).doesNotContain("agent-poison");
    }

    @Test
    void shouldFailClosedForNewTaskWithoutFlowOrPersistedRecoveryEvidence() {
        RoutingDecisionRecord decision = service(
                List.of(agent("agent-random", "TPE", "CAP_RANDOM_REVIEW")), properties())
                .decide(task(null, "CAP_RANDOM_REVIEW"));

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.MANUAL_REVIEW_REQUIRED);
        assertThat(decision.getRoutingPolicy()).isEqualTo(RoutingPolicy.MANUAL_REVIEW);
    }

    @Test
    void shouldUseGenericLocalFirstOnlyWhenExplicitlyConfigured() {
        AgentSnapshot localBusy = agent("agent-local", "TPE", "CAP_RANDOM_ANALYZE");
        localBusy.setCapacityUtilization(0.95d);
        AgentSnapshot remoteIdle = agent("agent-remote", "TYO", "CAP_RANDOM_ANALYZE");
        remoteIdle.setCapacityUtilization(0.0d);

        RoutingDecisionRecord decision = service(List.of(remoteIdle, localBusy), properties())
                .decide(task("LOCAL_FIRST", "CAP_RANDOM_ANALYZE"));

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getRoutingPolicy()).isEqualTo(RoutingPolicy.LOCAL_FIRST);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-local");
        assertThat(decision.getCandidates()).extracting(AgentCandidateScore::agentId).containsExactly("agent-local");
    }

    @Test
    void shouldRecoverPersistedTaskFromSavedEvidenceWithoutSourceInference() {
        TaskRecord task = task("HISTORICAL_POLICY_V1", "CAP_RANDOM_REVIEW");
        task.setRoutingPath("PERSISTED_RECOVERY");
        task.setRequestedSkill("CAP_RANDOM_REVIEW");
        task.setDispatchAttemptCount(1);

        RoutingDecisionRecord decision = service(
                List.of(agent("agent-recovery", "TPE", "CAP_RANDOM_REVIEW")), properties()).decide(task);

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getRoutingPolicy()).isEqualTo(RoutingPolicy.CAPABILITY_FIRST);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-recovery");
    }

    @Test
    void shouldEnforceSkillVersionCompatibilityWithGenericCapabilityCode() {
        AgentSnapshot v1 = agent("agent-v1", "TPE", "CAP_RANDOM_REVIEW");
        v1.setCapabilityProfile(Map.of(
                "skillVersions", List.of(Map.of("skillCode", "CAP_RANDOM_REVIEW", "version", 1))));
        AgentSnapshot v2 = agent("agent-v2", "TPE", "CAP_RANDOM_REVIEW");
        v2.setCapabilityProfile(Map.of(
                "skillVersions", List.of(Map.of("skillCode", "CAP_RANDOM_REVIEW", "version", 2))));

        TaskRecord task = task("CAPABILITY_FIRST", "CAP_RANDOM_REVIEW");
        task.setRequiredCapabilities(List.of("CAP_RANDOM_REVIEW", "SKILL_VERSION:CAP_RANDOM_REVIEW:2"));

        RoutingDecisionRecord decision = service(List.of(v1, v2), properties()).decide(task);

        assertThat(decision.getStatus()).isEqualTo(RoutingDecisionStatus.SELECTED);
        assertThat(decision.getSelectedAgentId()).isEqualTo("agent-v2");
        assertThat(decision.getCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.agentId()).isEqualTo("agent-v1");
            assertThat(candidate.missingCapabilities())
                    .anyMatch(value -> value.contains("skillVersion:CAP_RANDOM_REVIEW>=2 actual=1"));
            assertThat(candidate.score()).isLessThan(50);
        });
    }

    private RoutingDecisionService service(List<AgentSnapshot> candidates, RoutingProperties properties) {
        return new RoutingDecisionService(new QueryAwareAgentDirectory(candidates), new InMemoryRoutingDecisionRepository(), properties);
    }

    private RoutingProperties properties() {
        RoutingProperties properties = new RoutingProperties();
        properties.setAssignmentEnabled(true);
        properties.setMinimumScore(50);
        properties.setZeroSpecialCaseRuntimeEnabled(true);
        properties.setPersistedLegacyEvidenceRecoveryEnabled(true);
        properties.setFlowRuleRoutingEnabled(true);
        properties.setFlowRuleLegacyFallbackEnabled(false);
        properties.setPoisonAgentExclusionEnabled(true);
        properties.setPoisonAgentFailureThreshold(5);
        properties.setLoadAwareScoringEnabled(true);
        properties.setSkillVersionCompatibilityEnabled(true);
        properties.setSkillVersionEnforced(true);
        return properties;
    }

    private TaskRecord task(String routingPolicy, String capability) {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-random");
        task.setTaskType(TaskType.INCIDENT_RESPONSE);
        task.setStatus(TaskStatus.QUEUED);
        task.setSiteId("TPE");
        task.setObjectType("OBJECT_RANDOM");
        task.setRoutingPolicy(routingPolicy);
        task.setRequiredCapabilities(List.of(capability));
        return task;
    }

    private AgentSnapshot agent(String agentId, String siteId, String capability) {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId(agentId);
        agent.setAgentType("OPENCLAW");
        agent.setOwnerGatewayNodeId("gateway-1");
        agent.setAgentSessionId("session-" + agentId);
        agent.setSiteId(siteId);
        agent.setStatus(AgentStatus.IDLE);
        agent.setAvailableSlots(2);
        agent.setMaxConcurrentTasks(3);
        agent.setHealthScore(100);
        agent.setCapabilities(List.of(capability));
        agent.setCapabilityProfile(Map.of());
        return agent;
    }

    private static final class QueryAwareAgentDirectory implements AgentDirectoryFacade {
        private final List<AgentSnapshot> candidates;

        private QueryAwareAgentDirectory(List<AgentSnapshot> candidates) {
            this.candidates = candidates;
        }

        @Override
        public Optional<AgentSnapshot> findById(String agentId) {
            return candidates.stream().filter(agent -> agentId.equals(agent.getAgentId())).findFirst();
        }

        @Override
        public List<AgentSnapshot> findCandidates(AgentQuery query) {
            return candidates.stream()
                    .filter(agent -> query == null || query.getSiteId() == null || query.getSiteId().isBlank()
                            || query.getSiteId().equals(agent.getSiteId()))
                    .filter(agent -> query == null || !query.isAssignableOnly() || agent.isAssignable())
                    .filter(agent -> query == null || query.getRequiredCapabilities() == null
                            || query.getRequiredCapabilities().isEmpty()
                            || agent.getCapabilities().containsAll(query.getRequiredCapabilities()))
                    .limit(query == null ? 100 : query.getLimit())
                    .toList();
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
            return "QUERY_AWARE_TEST";
        }
    }
}
