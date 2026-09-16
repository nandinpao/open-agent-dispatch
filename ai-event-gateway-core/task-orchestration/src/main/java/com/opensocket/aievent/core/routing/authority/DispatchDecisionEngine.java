package com.opensocket.aievent.core.routing.authority;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.routing.cutover.GenericAuthoritativeRoutingResult;
import com.opensocket.aievent.core.routing.cutover.GenericDispatchAuthoritativeService;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * The single application-level authority for selecting an Agent for a Task.
 *
 * <p>The current dispatch path is fixed as Source System -&gt; Source Flow /
 * upstream governed Pool -&gt; Agent Pool -&gt; active Pool Members -&gt;
 * Core-approved Required Capability eligibility -&gt; Agent governance -&gt;
 * Runtime readiness/capacity -&gt; Agent Selection. Runtime-reported capability
 * metadata is diagnostic only and cannot grant qualification. Compatibility
 * services may supply evidence or adapters, but they must not independently
 * select an Agent.</p>
 */
@Service
public class DispatchDecisionEngine {
    private static final Logger log = LoggerFactory.getLogger(DispatchDecisionEngine.class);

    private final GenericDispatchAuthoritativeService poolFirstRoutingService;

    public DispatchDecisionEngine(GenericDispatchAuthoritativeService poolFirstRoutingService) {
        this.poolFirstRoutingService = poolFirstRoutingService;
    }

    public GenericAuthoritativeRoutingResult decide(TaskRecord task, Set<String> excludedAgentIds) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required for dispatch decision");
        }
        GenericAuthoritativeRoutingResult result = poolFirstRoutingService.route(
                task,
                excludedAgentIds == null ? Set.of() : Set.copyOf(excludedAgentIds));
        log.info("dispatch_authority_decision tenantId={} taskId={} flowId={} poolId={} status={} reasonCode={} candidateCount={} selectedAgentId={}",
                task.getTenantId(), task.getTaskId(), task.getMatchedFlowId(), task.getTargetPoolId() == null ? task.getAssignedPoolId() : task.getTargetPoolId(),
                result == null ? null : result.status(), result == null ? null : result.reasonCode(),
                result == null || result.candidates() == null ? 0 : result.candidates().size(),
                result == null || result.selected() == null ? null : result.selected().agentId());
        return result;
    }
}
