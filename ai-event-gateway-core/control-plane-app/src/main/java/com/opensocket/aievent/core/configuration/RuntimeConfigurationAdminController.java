package com.opensocket.aievent.core.configuration;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.kernel.configuration.revision.ConfigurationRevisionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform-only governed Runtime Configuration operator API. Existing IAM/ReBAC owns authorization. */
@RestController
@RequestMapping("/api/platform/runtime-configuration")
public class RuntimeConfigurationAdminController {
    private final RuntimeConfigurationAdminService service;
    public RuntimeConfigurationAdminController(RuntimeConfigurationAdminService service){this.service=service;}

    @GetMapping("/overview")
    public RuntimeConfigurationAdminService.Overview overview(){return service.overview();}

    @GetMapping("/governance")
    public RuntimeConfigurationAdminService.GovernanceOverview governance(){return service.governance();}

    @GetMapping("/settings/{key:.+}")
    public RuntimeConfigurationAdminService.SettingDetail detail(@PathVariable String key){
        try{return service.detail(key);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.NOT_FOUND,ex.getMessage(),ex);}
    }

    @GetMapping("/settings/{key:.+}/revisions")
    public List<RuntimeConfigurationAdminService.RevisionView> revisions(@PathVariable String key){return service.revisions(key);}

    @GetMapping("/settings/{key:.+}/apply-state")
    public RuntimeConfigurationAdminService.ApplyStateView applyState(@PathVariable String key){
        try{return service.applyState(key);}catch(IllegalArgumentException ex){throw new StandardApiException(StandardApiErrorCode.NOT_FOUND,ex.getMessage(),ex);}
    }

    @GetMapping("/settings/{key:.+}/rollback-preview")
    public RuntimeConfigurationAdminService.RollbackPreview rollbackPreview(@PathVariable String key,@RequestParam String restoreSourceRevisionId){
        try{return service.rollbackPreview(key,restoreSourceRevisionId);}catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/settings/{key:.+}/change-requests")
    public RuntimeConfigurationAdminService.RevisionView requestChange(@PathVariable String key,@RequestBody ChangeRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.requestChange(key,body.value(),body.expectedBaseRevisionId(),body.reason(),actor(authentication),correlationId(request));}
        catch(ConfigurationRevisionConflictException ex){throw conflict(ex);}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/revisions/{revisionId}/approve")
    public RuntimeConfigurationAdminService.RevisionView approve(@PathVariable String revisionId,@RequestBody ReasonRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.approve(revisionId,body.reason(),actor(authentication),correlationId(request));}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/revisions/{revisionId}/reject")
    public RuntimeConfigurationAdminService.RevisionView reject(@PathVariable String revisionId,@RequestBody ReasonRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.reject(revisionId,body.reason(),actor(authentication),correlationId(request));}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/revisions/{revisionId}/publish")
    public RuntimeConfigurationAdminService.RevisionView publish(@PathVariable String revisionId,@RequestBody PublishApprovedRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.publishApproved(revisionId,body.expectedBaseRevisionId(),body.reason(),actor(authentication),correlationId(request));}
        catch(ConfigurationRevisionConflictException ex){throw conflict(ex);}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/settings/{key:.+}/rollback-requests")
    public RuntimeConfigurationAdminService.RevisionView rollback(@PathVariable String key,@RequestBody RollbackRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.requestRollback(key,body.restoreSourceRevisionId(),body.reason(),actor(authentication),correlationId(request));}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/settings/{key:.+}/emergency-overrides")
    public RuntimeConfigurationAdminService.EmergencyOverrideView emergencyOverride(@PathVariable String key,@RequestBody EmergencyOverrideRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.createEmergencyOverride(key,body.value(),body.ttlMinutes(),body.reason(),actor(authentication),correlationId(request));}
        catch(ConfigurationRevisionConflictException ex){throw conflict(ex);}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    @PostMapping("/emergency-overrides/{overrideId}/revoke")
    public RuntimeConfigurationAdminService.EmergencyOverrideView revokeEmergencyOverride(@PathVariable String overrideId,@RequestBody ReasonRequest body,Authentication authentication,HttpServletRequest request){
        try{return service.revokeEmergencyOverride(overrideId,body.reason(),actor(authentication),correlationId(request));}
        catch(IllegalArgumentException|IllegalStateException ex){throw validation(ex);}
    }

    private static StandardApiException validation(RuntimeException ex){return new StandardApiException(StandardApiErrorCode.VALIDATION_ERROR,ex.getMessage(),ex);}
    private static StandardApiException conflict(RuntimeException ex){return new StandardApiException(StandardApiErrorCode.RESOURCE_VERSION_CONFLICT,"Runtime Configuration changed after this page was loaded. Reload the latest state before continuing.",ex);}
    private static String actor(Authentication authentication){return authentication==null||authentication.getName()==null||authentication.getName().isBlank()?"unknown-platform-operator":authentication.getName();}
    private static String correlationId(HttpServletRequest request){String value=request.getHeader("X-Correlation-Id");if(value==null||value.isBlank())value=request.getHeader("X-Request-Id");return value==null||value.isBlank()?UUID.randomUUID().toString():value.trim();}

    public record ChangeRequest(Object value,String expectedBaseRevisionId,String reason){}
    public record ReasonRequest(String reason){}
    public record PublishApprovedRequest(String expectedBaseRevisionId,String reason){}
    public record RollbackRequest(String restoreSourceRevisionId,String reason){}
    public record EmergencyOverrideRequest(Object value,int ttlMinutes,String reason){}
}
