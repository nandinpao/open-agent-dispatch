package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.ExecutionDispatchIntentStore;
import com.opensocket.aievent.core.capability.ExecutionDispatchIntentWorker;
import com.opensocket.aievent.core.capability.ExecutionSafetyActivationRequest;
import com.opensocket.aievent.core.capability.ExecutionSafetyAuthorityService;
import com.opensocket.aievent.core.capability.ExecutionSafetyPrepareRequest;
import com.opensocket.aievent.core.capability.ExecutionSafetyPrepareResult;
import com.opensocket.aievent.core.capability.FlowRoutingMigrationState;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** A0-R7 per-Flow execution authority and durable DispatchIntent administration. */
@RestController
@RequestMapping("/admin/execution-safety")
public class ExecutionSafetyAuthorityController {
    private final ExecutionSafetyAuthorityService safety;
    private final ExecutionDispatchIntentStore intents;
    private final ExecutionDispatchIntentWorker worker;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public ExecutionSafetyAuthorityController(ExecutionSafetyAuthorityService safety, ExecutionDispatchIntentStore intents,
                                              ExecutionDispatchIntentWorker worker,
                                              ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.safety=safety;this.intents=intents;this.worker=worker;this.scopedAccess=scopedAccess;
    }

    @PutMapping("/flows/{flowId}/authority")
    public FlowRoutingMigrationState setFlowAuthority(@PathVariable String flowId,@RequestParam(required=false)String tenantId,
                                                       @RequestBody ExecutionSafetyActivationRequest request){
        try{return safety.setFlowAuthority(tenant(tenantId),flowId,request);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/plans/{planId}/prepare")
    public ExecutionSafetyPrepareResult prepare(@PathVariable String planId,@RequestParam(required=false)String tenantId,
                                                 @RequestBody ExecutionSafetyPrepareRequest request){
        try{return safety.prepare(tenant(tenantId),planId,request);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @PostMapping("/intents/handoff-next")
    public Map<String,Object> handoffNext(@RequestParam(required=false)String tenantId,
                                          @RequestParam(defaultValue="a0-r7-admin-worker")String workerId){
        try{return worker.handoffNext(tenant(tenantId),workerId);}catch(IllegalArgumentException|IllegalStateException ex){throw bad(ex);}
    }

    @GetMapping("/intents/recent")
    public List<Map<String,Object>> recent(@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="100")int limit){
        return intents.recent(tenant(tenantId),limit);
    }

    @GetMapping("/plans/{planId}/trace")
    public List<Map<String,Object>> trace(@PathVariable String planId,@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="100")int limit){
        return safety.trace(tenant(tenantId),planId,limit);
    }

    private StandardApiException bad(RuntimeException ex){return new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}
    private String tenant(String requested){
        var guard=scopedAccess.getIfAvailable();
        if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}
        String active=guard.activeTenantId();
        if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");
        return active;
    }
}
