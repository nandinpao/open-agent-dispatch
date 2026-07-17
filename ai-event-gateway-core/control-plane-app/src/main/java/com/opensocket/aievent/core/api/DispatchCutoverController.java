package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.routing.cutover.DispatchCutoverPolicy;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverReadiness;
import com.opensocket.aievent.core.routing.cutover.DispatchCutoverService;

@RestController
@RequestMapping("/admin/dispatch-governance/cutover")
public class DispatchCutoverController {
    private final DispatchCutoverService service;
    public DispatchCutoverController(DispatchCutoverService service){this.service=service;}

    @GetMapping("/policies")
    public List<DispatchCutoverPolicy> policies(@RequestParam String tenantId,
                                                @RequestParam(defaultValue="200") int limit){
        return service.policies(tenantId,limit);
    }
    @PutMapping("/policies/{policyId}")
    public DispatchCutoverPolicy save(@PathVariable String policyId,@RequestParam String tenantId,
                                      @RequestParam String actor,@RequestBody DispatchCutoverPolicy request){
        return service.savePolicy(tenantId,policyId,request,actor);
    }
    @GetMapping("/readiness/{flowId}")
    public DispatchCutoverReadiness readiness(@PathVariable String flowId,@RequestParam String tenantId){
        return service.readiness(tenantId,flowId);
    }
    @PostMapping("/policies/{policyId}/rollback")
    public DispatchCutoverPolicy rollback(@PathVariable String policyId,@RequestParam String tenantId,
                                          @RequestParam String actor,@RequestParam String reason){
        return service.rollback(tenantId,policyId,reason,actor);
    }
}
