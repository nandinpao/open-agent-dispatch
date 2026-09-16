package com.opensocket.aievent.core.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.flowmatch.FlowMatchAuthorityQueryService;

/** A0-R3 canonical deterministic Flow Match operator read contract. */
@RestController
@RequestMapping("/admin/tasks")
public class TaskFlowMatchAuthorityController {
    private final FlowMatchAuthorityQueryService service;
    public TaskFlowMatchAuthorityController(FlowMatchAuthorityQueryService service){this.service=service;}
    @GetMapping("/{taskId}/flow-match-authority")
    public FlowMatchAuthorityQueryService.View view(@PathVariable String taskId){return service.view(taskId);}
}
