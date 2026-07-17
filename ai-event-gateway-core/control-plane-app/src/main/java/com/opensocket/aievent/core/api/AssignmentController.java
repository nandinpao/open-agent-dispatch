package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.opensocket.aievent.core.assignment.*;
import com.opensocket.aievent.core.task.*;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {
    private final TaskOperationalQuery queryService;
    private final TaskOrchestrationFacade orchestration;
    public AssignmentController(TaskOperationalQuery queryService, TaskOrchestrationFacade orchestration){this.queryService=queryService;this.orchestration=orchestration;}
    @GetMapping public List<TaskAssignment> recent(@RequestParam(defaultValue="100") int limit){return queryService.recentAssignments(limit);}
    @GetMapping("/{assignmentId}") public TaskAssignment get(@PathVariable String assignmentId){return queryService.findAssignment(assignmentId).orElseThrow(() -> new IllegalArgumentException("Assignment not found: "+assignmentId));}
    @GetMapping("/task/{taskId}") public List<TaskAssignment> byTask(@PathVariable String taskId,@RequestParam(defaultValue="100") int limit){return queryService.findAssignmentsByTask(taskId,limit);}
    @PostMapping("/task/{taskId}/decide") public AssignmentDecisionResult decide(@PathVariable String taskId){return orchestration.assignTask(taskId);}
    @PostMapping("/recovery/scan") public TaskDispatchRecoveryScanResult scanDelayedDispatchRecovery(@RequestParam(defaultValue="50") int limit){return orchestration.recoverDelayedDispatches(limit, OffsetDateTime.now(ZoneOffset.UTC));}
}
