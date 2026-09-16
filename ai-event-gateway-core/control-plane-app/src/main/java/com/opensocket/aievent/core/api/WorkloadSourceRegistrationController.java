package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.intake.WorkloadSourceRegistrationCommand;
import com.opensocket.aievent.core.intake.WorkloadSourceRegistrationService;
import com.opensocket.aievent.core.intake.WorkloadSourceRegistrationView;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/**
 * A0-R1 registration administration remains nested beneath SourceSystem authority.
 * It deliberately reuses SourceSystem object authorization instead of inventing a parallel RBAC authority.
 */
@RestController
@RequestMapping("/admin/source-systems/{sourceSystemId}/registrations")
public class WorkloadSourceRegistrationController {
    private final WorkloadSourceRegistrationService registrations;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public WorkloadSourceRegistrationController(WorkloadSourceRegistrationService registrations,
                                                ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.registrations=registrations;this.scopedAccess=scopedAccess;
    }

    @GetMapping
    public List<WorkloadSourceRegistrationView> list(@PathVariable String sourceSystemId,@RequestParam(required=false) String tenantId){
        authorize(sourceSystemId,"admin.source.system.detail",ResourceAction.ActionKind.READ,false,"SOURCE_REGISTRATION_LIST");
        return registrations.list(effectiveTenant(tenantId),sourceSystemId);
    }

    @PostMapping
    public WorkloadSourceRegistrationView create(@PathVariable String sourceSystemId,@RequestParam(required=false) String tenantId,@RequestBody(required=false) WorkloadSourceRegistrationCommand request){
        authorize(sourceSystemId,"admin.source.system.update",ResourceAction.ActionKind.UPDATE,true,"SOURCE_REGISTRATION_CREATE");
        try{return registrations.create(effectiveTenant(tenantId),sourceSystemId,request);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}
    }

    @PutMapping("/{registrationId}")
    public WorkloadSourceRegistrationView update(@PathVariable String sourceSystemId,@PathVariable String registrationId,@RequestParam(required=false) String tenantId,@RequestBody(required=false) WorkloadSourceRegistrationCommand request){
        authorize(sourceSystemId,"admin.source.system.update",ResourceAction.ActionKind.UPDATE,true,"SOURCE_REGISTRATION_UPDATE");
        try{return registrations.update(effectiveTenant(tenantId),sourceSystemId,registrationId,request);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}
    }

    @DeleteMapping("/{registrationId}")
    public Map<String,Object> retire(@PathVariable String sourceSystemId,@PathVariable String registrationId,@RequestParam(required=false) String tenantId){
        authorize(sourceSystemId,"admin.source.system.update",ResourceAction.ActionKind.UPDATE,true,"SOURCE_REGISTRATION_RETIRE");
        try{registrations.retire(effectiveTenant(tenantId),sourceSystemId,registrationId);return Map.of("sourceRegistrationId",registrationId,"status","RETIRED");}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,ex.getMessage());}
    }

    private String effectiveTenant(String requested){var guard=scopedAccess.getIfAvailable();if(guard==null){if(requested==null||requested.isBlank())throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"tenantId is required");return requested.trim();}String active=guard.activeTenantId();if(requested!=null&&!requested.isBlank()&&!active.equals(requested.trim()))throw new StandardApiException(StandardApiErrorCode.FORBIDDEN,"Tenant query parameters cannot switch workspace authority. Use the active authenticated Tenant.");return active;}
    private void authorize(String id,String permission,ResourceAction.ActionKind kind,boolean sideEffect,String purpose){var guard=scopedAccess.getIfAvailable();if(guard!=null)guard.authorize(ResourceType.SOURCE_SYSTEM,id,permission,kind,sideEffect,VisibilityLevel.STANDARD,purpose);}
}
