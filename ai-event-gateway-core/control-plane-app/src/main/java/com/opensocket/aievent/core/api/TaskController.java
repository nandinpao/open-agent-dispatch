package com.opensocket.aievent.core.api;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.opensocket.aievent.core.lifecycle.*;
import com.opensocket.aievent.core.task.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskOperationalQuery queryService;
    private final TaskLifecycleService lifecycleService;
    public TaskController(TaskOperationalQuery queryService, TaskLifecycleService lifecycleService) { this.queryService=queryService; this.lifecycleService=lifecycleService; }

    @GetMapping
    public List<TaskRecord> search(@RequestParam(required=false) String incidentId,
                                   @RequestParam(required=false) String tenantId,
                                   @RequestParam(required=false) String siteId,
                                   @RequestParam(required=false) String plantId,
                                   @RequestParam(required=false) TaskType taskType,
                                   @RequestParam(required=false) TaskStatus status,
                                   @RequestParam(defaultValue="100") int limit) {
        TaskQuery q=new TaskQuery(); q.setIncidentId(incidentId); q.setTenantId(tenantId); q.setSiteId(siteId); q.setPlantId(plantId); q.setTaskType(taskType); q.setStatus(status); q.setLimit(limit);
        return queryService.searchTasks(q);
    }
    @GetMapping("/{taskId}") public TaskRecord get(@PathVariable String taskId) { return queryService.findTask(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found: "+taskId)); }
    @GetMapping("/incident/{incidentId}") public List<TaskRecord> byIncident(@PathVariable String incidentId,@RequestParam(defaultValue="100") int limit){return queryService.findTasksByIncident(incidentId,limit);}
    @PostMapping("/{taskId}/timeout") public TaskRecord timeout(@PathVariable String taskId,@RequestBody(required=false) LifecycleRequest r){return lifecycleService.timeout(taskId,r==null?null:r.reason());}
    @PostMapping("/{taskId}/cancel") public TaskRecord cancel(@PathVariable String taskId,@RequestBody(required=false) LifecycleRequest r){return lifecycleService.cancel(taskId,r==null?null:r.reason());}
    @PostMapping("/{taskId}/reassign") public TaskRecord reassign(@PathVariable String taskId,@RequestBody(required=false) LifecycleRequest r){return lifecycleService.reassign(taskId,r==null?null:r.reason());}
    @PostMapping("/lifecycle/process-timeouts") public LifecycleScanResult processTimeouts(){return lifecycleService.processTimeoutsAndReassignments();}
    @GetMapping("/metadata") public TaskMetadata metadata(){return new TaskMetadata(queryService.taskStoreMode(),TaskType.values(),TaskStatus.values(),TaskPriority.values());}
    public record LifecycleRequest(String reason){}
    public record TaskMetadata(String storeMode,TaskType[] taskTypes,TaskStatus[] statuses,TaskPriority[] priorities){}
}
