package com.opensocket.aievent.core.api;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskOperationalQuery;

@RestController
@RequestMapping("/api/routing-decisions")
public class RoutingDecisionController {
    private final TaskOperationalQuery queryService;
    public RoutingDecisionController(TaskOperationalQuery queryService){this.queryService=queryService;}
    @GetMapping public List<RoutingDecisionRecord> recent(@RequestParam(defaultValue="100") int limit){return queryService.recentRoutingDecisions(limit);}
    @GetMapping("/{decisionId}") public RoutingDecisionRecord get(@PathVariable String decisionId){return queryService.findRoutingDecision(decisionId).orElseThrow(() -> new IllegalArgumentException("Routing decision not found: "+decisionId));}
    @GetMapping("/task/{taskId}") public List<RoutingDecisionRecord> byTask(@PathVariable String taskId,@RequestParam(defaultValue="100") int limit){return queryService.findRoutingDecisionsByTask(taskId,limit);}
}
