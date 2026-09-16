package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;
import com.opensocket.aievent.core.security.incident.SecurityIncidentControlService;
import com.opensocket.aievent.core.security.incident.SecurityIncidentControlService.ApplyControlCommand;
import com.opensocket.aievent.core.security.incident.SecurityIncidentControlService.CreateCaseCommand;
import com.opensocket.aievent.core.security.incident.SecurityIncidentControlService.SecurityIncidentCaseDetails;
import com.opensocket.aievent.core.security.incident.SecurityIncidentControlService.SecurityIncidentCaseView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** High-risk Human Security Operations surface. Every side effect requires target Resource Access. */
@RestController
@RequestMapping("/api/security/incidents")
public class SecurityIncidentControlController {
    private final SecurityIncidentControlService service;
    private final ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess;

    public SecurityIncidentControlController(SecurityIncidentControlService service,
            ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccess) {
        this.service=service; this.scopedAccess=scopedAccess;
    }

    @GetMapping
    public List<SecurityIncidentCaseView> list(@RequestParam(required=false) String status,
            @RequestParam(required=false) String severity,@RequestParam(defaultValue="100") int limit) {
        String tenant=guard().activeTenantId();
        return service.listCases(tenant,status,severity,limit).stream().filter(this::canRead).toList();
    }

    @GetMapping("/{caseId}")
    public SecurityIncidentCaseDetails detail(@PathVariable String caseId) {
        SecurityIncidentCaseDetails details=service.details(guard().activeTenantId(),caseId);
        authorizeCaseRead(details.incident()); return details;
    }

    @PostMapping
    public SecurityIncidentCaseView create(@RequestBody CreateCaseCommand command) {
        authorizeCreate(command); return service.createCase(guard().activeTenantId(),command,actorId());
    }

    @PostMapping("/{caseId}/controls")
    public SecurityIncidentCaseDetails apply(@PathVariable String caseId,@RequestBody ApplyControlCommand command,HttpServletRequest request) {
        SecurityIncidentCaseDetails details=service.details(guard().activeTenantId(),caseId); authorizeCaseRead(details.incident());
        AuthorizationDecision decision=authorizeTarget(command);
        return service.apply(guard().activeTenantId(),caseId,command,actorId(),correlation(request),decision==null?"":decision.decisionId());
    }

    @PostMapping("/{caseId}/controls/{controlId}/release")
    public SecurityIncidentCaseDetails release(@PathVariable String caseId,@PathVariable String controlId,@RequestBody ReleaseControlRequest body,HttpServletRequest request) {
        SecurityIncidentCaseDetails details=service.details(guard().activeTenantId(),caseId); authorizeCaseRead(details.incident());
        var control=details.controls().stream().filter(value->controlId.equals(value.controlId())).findFirst()
                .orElseThrow(()->new StandardApiException(StandardApiErrorCode.NOT_FOUND,"Security control not found: "+controlId));
        AuthorizationDecision decision=authorizeTarget(new ApplyControlCommand(control.targetType(),control.targetId(),control.parentTargetId(),control.controlType(),control.limitPerMinute(),control.expiresAt(),body==null?null:body.reason()));
        return service.release(guard().activeTenantId(),caseId,controlId,body==null?null:body.reason(),actorId(),correlation(request),decision==null?"":decision.decisionId());
    }

    @PostMapping("/{caseId}/resolve")
    public SecurityIncidentCaseView resolve(@PathVariable String caseId,@RequestBody ResolveCaseRequest body,HttpServletRequest request) {
        SecurityIncidentCaseDetails details=service.details(guard().activeTenantId(),caseId); authorizeCaseRead(details.incident());
        // Resolution is case governance plus evidence retention. Active controls must be released first.
        return service.resolve(guard().activeTenantId(),caseId,body==null?null:body.reason(),actorId(),correlation(request));
    }

    private AuthorizationDecision authorizeTarget(ApplyControlCommand c) {
        if(c==null) throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"Control request body is required.");
        String type=upper(c.targetType()); String action=upper(c.action());
        return switch(type) {
            case "CREDENTIAL" -> guard().authorize(ResourceType.SERVICE_ACCOUNT,service.credentialServiceAccountId(guard().activeTenantId(),required(c.targetId(),"targetId")),"security.token.manage",ResourceAction.ActionKind.MANAGE,true,VisibilityLevel.SECRET_METADATA,"SECURITY_INCIDENT_CREDENTIAL_CONTROL");
            case "SERVICE_ACCOUNT" -> guard().authorize(ResourceType.SERVICE_ACCOUNT,required(c.targetId(),"targetId"),"security.token.manage",ResourceAction.ActionKind.MANAGE,true,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_SERVICE_ACCOUNT_CONTROL");
            case "AGENT" -> guard().authorize(ResourceType.AGENT,required(c.targetId(),"targetId"),"REVOKE".equals(action)?"admin.agent.governance.revoke.agent":"admin.agent.governance.suspend.agent",ResourceAction.ActionKind.MANAGE,true,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_AGENT_CONTROL");
            case "TASK" -> guard().authorize(ResourceType.TASK,required(c.targetId(),"targetId"),"task.update",ResourceAction.ActionKind.MANAGE,true,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_TASK_CONTROL");
            case "SOURCE_SYSTEM" -> guard().authorize(ResourceType.SOURCE_SYSTEM,required(c.targetId(),"targetId"),"admin.source.system.update",ResourceAction.ActionKind.MANAGE,true,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_SOURCE_CONTROL");
            default -> throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"Unsupported incident target type: "+type);
        };
    }

    private void authorizeCreate(CreateCaseCommand c) {
        if(c==null) throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,"Request body is required.");
        if(!blank(c.rootTaskId())) { guard().authorize(ResourceType.TASK,c.rootTaskId(),"task.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CREATE_FROM_TASK"); return; }
        if(!blank(c.sourceIncidentId())) { guard().authorize(ResourceType.INCIDENT,c.sourceIncidentId(),"api.incident.incident",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CREATE_FROM_INCIDENT"); return; }
        if(!blank(c.sourceSystemId())) { guard().authorize(ResourceType.SOURCE_SYSTEM,c.sourceSystemId(),"admin.source.system.detail",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CREATE_FROM_SOURCE"); return; }
        guard().requireTenantWide("resource.security-state.manage",ResourceType.INCIDENT,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CREATE_TENANT_WIDE");
    }

    private boolean canRead(SecurityIncidentCaseView c) { try { authorizeCaseRead(c); return true; } catch(RuntimeException denied) { return false; } }
    private void authorizeCaseRead(SecurityIncidentCaseView c) {
        if(!blank(c.rootTaskId())) { guard().authorize(ResourceType.TASK,c.rootTaskId(),"task.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CASE_READ"); return; }
        if(!blank(c.sourceIncidentId())) { guard().authorize(ResourceType.INCIDENT,c.sourceIncidentId(),"api.incident.incident",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CASE_READ"); return; }
        if(!blank(c.sourceSystemId())) { guard().authorize(ResourceType.SOURCE_SYSTEM,c.sourceSystemId(),"admin.source.system.detail",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CASE_READ"); return; }
        guard().requireTenantWide("resource.governance.read",ResourceType.INCIDENT,VisibilityLevel.SENSITIVE,"SECURITY_INCIDENT_CASE_READ_TENANT_WIDE");
    }

    private ScopedBusinessResourceAccessCoordinator guard() {
        ScopedBusinessResourceAccessCoordinator value=scopedAccess.getIfAvailable();
        if(value==null) throw new StandardApiException(StandardApiErrorCode.DEPENDENCY_UNAVAILABLE,"Formal Resource Access is required for Security Incident controls.");
        return value;
    }
    private static String actorId(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated()||blank(a.getName()))throw new StandardApiException(StandardApiErrorCode.UNAUTHORIZED,"Authenticated Human actor is required.");return a.getName();}
    private static String correlation(HttpServletRequest r){String v=r==null?null:r.getHeader("X-Correlation-Id");if(blank(v)&&r!=null)v=r.getHeader("X-Request-Id");return blank(v)?"security-incident-"+java.util.UUID.randomUUID():v.trim();}
    private static String required(String v,String field){if(blank(v))throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST,field+" is required.");return v.trim();}
    private static String upper(String v){return required(v,"value").toUpperCase(java.util.Locale.ROOT);}
    private static boolean blank(String v){return v==null||v.isBlank();}
    public record ReleaseControlRequest(String reason){}
    public record ResolveCaseRequest(String reason){}
}
