package com.opensocket.aievent.core.routing.governance.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.routing.governance.GenericRoutingStrategy;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

class GenericRoutingScoreCalculatorTest {
    private final GenericRoutingScoreCalculator calculator = new GenericRoutingScoreCalculator();

    @Test
    void scoresOpaqueSourceAndCapabilityWithoutDomainInference() {
        TaskRecord task = task("task-random", "SITE-A");
        TaskRequirementEvidence requirement = new TaskRequirementEvidence();
        requirement.setRequiredCapabilities(List.of("CAP_RANDOM_ANALYSIS_REVIEW_TRIAGE"));

        AgentSnapshot local = agent("agent-local", "SITE-A", 90, 1, 10);
        AgentSnapshot remote = agent("agent-remote", "SITE-B", 90, 1, 10);

        GenericRoutingScore localScore = calculator.score(
                GenericRoutingStrategy.LOCAL_FIRST, task, requirement, local);
        GenericRoutingScore remoteScore = calculator.score(
                GenericRoutingStrategy.LOCAL_FIRST, task, requirement, remote);

        assertTrue(localScore.score() > remoteScore.score());
        assertEquals("LOCAL_FIRST", localScore.breakdown().get("strategy"));
    }

    @Test
    void lowestLoadPrefersAgentWithMoreAvailableCapacity() {
        TaskRecord task = task("task-capacity", "SITE-X");
        TaskRequirementEvidence requirement = new TaskRequirementEvidence();
        AgentSnapshot lowLoad = agent("agent-low", "SITE-X", 80, 1, 10);
        AgentSnapshot highLoad = agent("agent-high", "SITE-X", 80, 9, 10);

        int lowLoadScore = calculator.score(
                GenericRoutingStrategy.LOWEST_LOAD, task, requirement, lowLoad).score();
        int highLoadScore = calculator.score(
                GenericRoutingStrategy.LOWEST_LOAD, task, requirement, highLoad).score();

        assertTrue(lowLoadScore > highLoadScore);
    }

    @Test
    void roundRobinIsDeterministicForTaskAndAgent() {
        TaskRecord task = task("task-round-robin", null);
        TaskRequirementEvidence requirement = new TaskRequirementEvidence();
        AgentSnapshot first = agent("agent-a", null, 100, 0, 1);
        AgentSnapshot second = agent("agent-b", null, 100, 0, 1);

        int firstRun = calculator.score(
                GenericRoutingStrategy.ROUND_ROBIN, task, requirement, first).score();
        int secondRun = calculator.score(
                GenericRoutingStrategy.ROUND_ROBIN, task, requirement, first).score();
        int otherAgent = calculator.score(
                GenericRoutingStrategy.ROUND_ROBIN, task, requirement, second).score();

        assertEquals(firstRun, secondRun);
        assertNotEquals(first.getAgentId(), second.getAgentId());
        assertTrue(firstRun >= 0 && firstRun < 100);
        assertTrue(otherAgent >= 0 && otherAgent < 100);
    }

    @Test
    void manualReviewDoesNotAutoRankAnAgent() {
        GenericRoutingScore score = calculator.score(
                GenericRoutingStrategy.MANUAL_REVIEW,
                task("task-manual", null),
                new TaskRequirementEvidence(),
                agent("agent-manual", null, 100, 0, 1));

        assertEquals(0, score.score());
        assertEquals(Boolean.TRUE, score.breakdown().get("manualReviewRequired"));
    }

    private static TaskRecord task(String taskId, String siteId) {
        TaskRecord task = new TaskRecord();
        task.setTaskId(taskId);
        task.setSiteId(siteId);
        return task;
    }

    private static AgentSnapshot agent(
            String agentId, String siteId, int healthScore, int currentTasks, int maxConcurrentTasks) {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId(agentId);
        agent.setSiteId(siteId);
        agent.setHealthScore(healthScore);
        agent.setCurrentTaskCount(currentTasks);
        agent.setMaxConcurrentTasks(maxConcurrentTasks);
        agent.setCapacityUtilization((double) currentTasks / Math.max(1, maxConcurrentTasks));
        return agent;
    }
}
