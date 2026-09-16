package com.opensocket.aievent.core.resourceaccess.api;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** P4RA-D decision API. It is disabled by default and does not intercept business APIs. */
@RestController
@RequestMapping(path="/api/resource-access/decisions",produces=MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix="resource-access",name={"enabled","decision-api-enabled"},havingValue="true")
public final class ResourceDecisionController {
 private final ResourceAuthorizationPort authorization;private final ResourceAuthorizationApiContextPort contexts;
 public ResourceDecisionController(ResourceAuthorizationPort authorization,ResourceAuthorizationApiContextPort contexts){this.authorization=authorization;this.contexts=contexts;}
 @PostMapping(path="/evaluate",consumes=MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationDecision evaluate(@RequestBody DecisionBody body){return authorization.evaluate(request(body));}
 @PostMapping(path="/explain",consumes=MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationDecision explain(@RequestBody DecisionBody body){return authorization.explain(request(body));}
 @PostMapping(path="/simulate",consumes=MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationDecision simulate(@RequestBody SimulationBody body){ResourceAuthorizationApiContext c=contexts.current();var auth=c.authentication();ResourceRef ref=new ResourceRef(auth.activeTenant().tenantId(),body.resourceType(),required(body.resourceId(),"resourceId"));ResourceAction action=new ResourceAction(required(body.permissionCode(),"permissionCode"),body.actionKind(),body.sideEffecting());PrincipalRef target=new PrincipalRef(body.targetPrincipalType(),required(body.targetPrincipalId(),"targetPrincipalId"));return authorization.simulate(new AuthorizationSimulationRequest(auth,target,auth.activeTenant(),action,ref,body.requestedVisibility(),required(body.purpose(),"purpose"),c.correlationId(),body.presentedEpoch(),Map.of("simulation-actor",auth.principal().principalId())));}
 private AuthorizationRequest request(DecisionBody body){ResourceAuthorizationApiContext c=contexts.current();var auth=c.authentication();ResourceRef ref=new ResourceRef(auth.activeTenant().tenantId(),body.resourceType(),required(body.resourceId(),"resourceId"));ResourceAction action=new ResourceAction(required(body.permissionCode(),"permissionCode"),body.actionKind(),body.sideEffecting());return new AuthorizationRequest(auth.principal(),auth,auth.activeTenant(),action,ref,body.requestedVisibility(),RequestChannel.REST,required(body.purpose(),"purpose"),body.operationPhase(),"",c.correlationId(),body.presentedEpoch(),Map.of());}
 public record DecisionBody(String permissionCode,ResourceType resourceType,String resourceId,ResourceAction.ActionKind actionKind,boolean sideEffecting,VisibilityLevel requestedVisibility,String purpose,OperationPhase operationPhase,SecurityEpoch presentedEpoch){public DecisionBody{if(resourceType==null)throw new IllegalArgumentException("resourceType is required");if(actionKind==null)throw new IllegalArgumentException("actionKind is required");requestedVisibility=requestedVisibility==null?VisibilityLevel.NONE:requestedVisibility;operationPhase=operationPhase==null?OperationPhase.START:operationPhase;presentedEpoch=presentedEpoch==null?SecurityEpoch.ZERO:presentedEpoch;}}
 public record SimulationBody(PrincipalRef.PrincipalType targetPrincipalType,String targetPrincipalId,String permissionCode,ResourceType resourceType,String resourceId,ResourceAction.ActionKind actionKind,boolean sideEffecting,VisibilityLevel requestedVisibility,String purpose,SecurityEpoch presentedEpoch){public SimulationBody{if(targetPrincipalType==null)throw new IllegalArgumentException("targetPrincipalType is required");if(resourceType==null)throw new IllegalArgumentException("resourceType is required");if(actionKind==null)throw new IllegalArgumentException("actionKind is required");requestedVisibility=requestedVisibility==null?VisibilityLevel.NONE:requestedVisibility;presentedEpoch=presentedEpoch==null?SecurityEpoch.ZERO:presentedEpoch;}}
 private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
