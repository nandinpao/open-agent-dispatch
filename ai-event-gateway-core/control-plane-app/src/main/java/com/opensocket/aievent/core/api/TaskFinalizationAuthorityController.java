package com.opensocket.aievent.core.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.opensocket.aievent.core.taskauthority.TaskFinalizationAdminService;

/** A0-R2 Task canonical state/finalization operator contract. */
@RestController
@RequestMapping("/admin/tasks")
public class TaskFinalizationAuthorityController {
    private final TaskFinalizationAdminService service;
    public TaskFinalizationAuthorityController(TaskFinalizationAdminService service){this.service=service;}
    @GetMapping("/{taskId}/finalization-authority") public TaskFinalizationAdminService.View view(@PathVariable String taskId){return service.view(taskId);}
    @PostMapping("/{taskId}/finalization-authority/retry") public TaskFinalizationAdminService.View retry(@PathVariable String taskId,@RequestBody(required=false) Map<String,String> body){return service.retry(taskId,body==null?null:body.get("reason"));}
    @GetMapping("/finalization-recovery") public List<TaskFinalizationAdminService.RecoveryItem> recovery(@RequestParam(defaultValue="100") int limit){return service.recoveryQueue(limit);}
}
