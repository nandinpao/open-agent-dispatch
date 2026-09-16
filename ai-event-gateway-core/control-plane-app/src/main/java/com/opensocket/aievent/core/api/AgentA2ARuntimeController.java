package com.opensocket.aievent.core.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stage 6 tombstone for the retired directional Agent A2A runtime route.
 *
 * <p>This controller intentionally has no dependency on A2A governance, Task orchestration,
 * persistence, routing, or runtime authorization services. It exists only to fail closed for an
 * obsolete internal route and to point callers at the capability-first runtime contract.</p>
 */
@RestController
@RequestMapping("/internal/control-plane/tasks")
public class AgentA2ARuntimeController {

    @PostMapping("/{taskId}/a2a-requests")
    public void retired(@PathVariable String taskId) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "A2A_LEGACY_RUNTIME_ROUTE_RETIRED: /internal/control-plane/tasks/" + taskId
                        + "/a2a-requests is retired. Use POST /internal/control-plane/tasks/{taskId}/capability-delegations with WHAT-only capability intent.");
    }
}
