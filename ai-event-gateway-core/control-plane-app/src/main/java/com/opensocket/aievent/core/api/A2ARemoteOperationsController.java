package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.A2ARemoteOperationsService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Stage 8 remote execution observability and governed cancellation. */
@RestController
@RequestMapping("/admin/a2a-remote-executions")
public class A2ARemoteOperationsController {
    private final A2ARemoteOperationsService service; private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped;
    public A2ARemoteOperationsController(A2ARemoteOperationsService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scoped){this.service=service;this.scoped=scoped;}
    @GetMapping("/{executionId}") public Map<String,Object> execution(@PathVariable String executionId,@RequestParam(required=false)String tenantId){return service.execution(tenant(tenantId),executionId);}
    @GetMapping("/{executionId}/tracking") public Map<String,Object> tracking(@PathVariable String executionId,@RequestParam(required=false)String tenantId){return service.tracking(tenant(tenantId),executionId);}
    @GetMapping("/{executionId}/journal") public List<Map<String,Object>> journal(@PathVariable String executionId,@RequestParam(required=false)String tenantId,@RequestParam(defaultValue="100")int limit){return service.journal(tenant(tenantId),executionId,limit);}
    @PostMapping("/{executionId}/cancel") public Map<String,Object> cancel(@PathVariable String executionId,@RequestParam(required=false)String tenantId){String t=tenant(tenantId);service.cancel(t,executionId);return Map.of("executionId",executionId,"status","CANCELING");}
    private String tenant(String requested){var g=scoped.getIfAvailable();if(g==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=g.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority.");return active;}
}
