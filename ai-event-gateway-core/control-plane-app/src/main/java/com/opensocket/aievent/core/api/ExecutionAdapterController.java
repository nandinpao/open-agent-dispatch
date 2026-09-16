package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.capability.ExecutionAdapterRegistration;
import com.opensocket.aievent.core.capability.ExecutionAdapterResolution;
import com.opensocket.aievent.core.capability.ExecutionAdapterResolutionRequest;
import com.opensocket.aievent.core.capability.ExecutionAdapterService;
import com.opensocket.aievent.core.capability.ExecutionAdapterVersion;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** Phase 5 HOW administration API. Resolution does not perform network I/O or Task dispatch. */
@RestController
@RequestMapping("/admin/execution-adapters")
public class ExecutionAdapterController {
    private final ExecutionAdapterService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;
    public ExecutionAdapterController(ExecutionAdapterService service,ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess){this.service=service;this.scopedAccess=scopedAccess;}

    @GetMapping public List<ExecutionAdapterRegistration> adapters(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String providerId,@RequestParam(required=false)String status,@RequestParam(required=false)String search,@RequestParam(required=false)String afterAdapterId,@RequestParam(defaultValue="100")int limit){return service.listAdapters(effectiveTenant(tenantId),providerId,status,search,afterAdapterId,limit);}
    @GetMapping("/{adapterId}") public ExecutionAdapterRegistration adapter(@PathVariable String adapterId,@RequestParam(required=false)String tenantId){return service.findAdapter(effectiveTenant(tenantId),adapterId).orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Execution Adapter not found: "+adapterId));}
    @PutMapping("/{adapterId}") public ExecutionAdapterRegistration upsert(@PathVariable String adapterId,@RequestParam(required=false)String tenantId,@RequestHeader(name="X-Change-Reason",required=false)String reason,@RequestBody(required=false)ExecutionAdapterRegistration request){try{return service.upsertAdapter(effectiveTenant(tenantId),adapterId,request,reason);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @GetMapping("/{adapterId}/versions") public List<ExecutionAdapterVersion> versions(@PathVariable String adapterId,@RequestParam(required=false)String tenantId){return service.adapterVersions(effectiveTenant(tenantId),adapterId);}
    @PostMapping("/resolve-preview") public ExecutionAdapterResolution resolvePreview(@RequestParam(required=false)String tenantId,@RequestBody(required=false)ExecutionAdapterResolutionRequest request){try{return service.resolvePreview(effectiveTenant(tenantId),request);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}}
    @GetMapping("/resolutions/evidence") public List<ExecutionAdapterResolution> resolutions(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String routingDecisionId,@RequestParam(defaultValue="100")int limit){return service.listResolutions(effectiveTenant(tenantId),routingDecisionId,limit);}

    private String effectiveTenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
}
