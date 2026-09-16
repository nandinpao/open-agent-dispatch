package com.opensocket.aievent.core.api;
import java.util.*; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.http.context.*; import com.opensocket.aievent.core.integration.issue.ProviderWebhookReconciliationService; import com.opensocket.aievent.core.integration.issue.webhook.*;
import com.opensocket.aievent.core.integration.identity.*; import org.springframework.beans.factory.annotation.Autowired;
@RestController @RequestMapping
public class ProviderWebhookOperationsController {
 private final ProviderWebhookReconciliationService service; private final ObjectMapper json;
 @Autowired(required=false) private IntegrationIdentityRepository identities;
 public ProviderWebhookOperationsController(ProviderWebhookReconciliationService service,ObjectMapper json){this.service=service;this.json=json;}
 /** External machine ingress. Tenant, Connection and Provider are derived exclusively from the authenticated endpoint. */
 @PostMapping(path="/api/external/provider-webhooks/{endpointToken}",consumes=MediaType.APPLICATION_JSON_VALUE)
 public ProviderWebhookReceipt receive(@PathVariable String endpointToken,@RequestHeader("X-Provider-Event-Id")String providerEventId,@RequestHeader("X-Webhook-Timestamp")String timestamp,@RequestHeader("X-Webhook-Nonce")String nonce,@RequestBody byte[] rawBody,Authentication authentication){
  ProviderWebhookMachinePrincipal machine=machine(authentication);ProviderWebhookRequest body=parse(rawBody);
  requireCanonicalProjectMapping(machine,body.externalProjectId());
  return run(()->service.receive(machine.tenantId(),machine.connectionId(),machine.providerType(),providerEventId,body.eventType(),body.externalProjectId(),body.externalIssueId(),body.externalIssueKey(),body.externalIssueStatus(),timestamp,nonce,true,new String(rawBody,java.nio.charset.StandardCharsets.UTF_8),body.observedDocumentJson(),body.providerIdentity(),body.mappingSchemaHash(),body.providerEventSequence(),body.providerChangeVersion(),machine.correlationId()));
 }
 @GetMapping("/api/integrations/provider-webhooks/inbox") public List<ProviderWebhookInboxEntry> inbox(@RequestParam(required=false)ProviderWebhookInboxStatus status,@RequestParam(defaultValue="200")int limit){return run(()->service.inbox(tenant(),status,limit));}
 @GetMapping("/api/integrations/provider-webhooks/inbox/{inboxId}/evidence") public List<WebhookReplayEvidence> evidence(@PathVariable String inboxId,@RequestParam(defaultValue="200")int limit){return run(()->service.replayEvidence(tenant(),inboxId,limit));}
 @PostMapping("/api/integrations/provider-webhooks/inbox/{inboxId}/replay") public ProviderWebhookInboxEntry replay(@PathVariable String inboxId,@RequestHeader("Idempotency-Key")String key,@RequestBody GovernedReason body){required(key,"Idempotency-Key");return run(()->service.replay(tenant(),inboxId,operator(),required(body.reason(),"reason")));}
 @GetMapping("/api/integrations/external-observations") public List<ExternalIssueObservation> observations(@RequestParam String connectionId,@RequestParam String externalIssueId,@RequestParam(defaultValue="200")int limit){return run(()->service.observations(tenant(),connectionId,externalIssueId,limit));}
 @GetMapping("/api/integrations/external-conflicts") public List<ExternalIssueConflict> conflicts(@RequestParam(required=false)ExternalIssueConflictStatus status,@RequestParam(defaultValue="200")int limit){return run(()->service.conflicts(tenant(),status,limit));}
 @GetMapping("/api/integrations/external-conflicts/{conflictId}/events") public List<ExternalIssueConflictEvent> conflictEvents(@PathVariable String conflictId,@RequestParam(defaultValue="200")int limit){return run(()->service.conflictEvents(tenant(),conflictId,limit));}
 @PostMapping("/api/integrations/external-conflicts/{conflictId}/resolve") public ExternalIssueConflict resolve(@PathVariable String conflictId,@RequestHeader("Idempotency-Key")String key,@RequestHeader("If-Match")String ifMatch,@RequestBody ConflictResolutionRequest body){throw new ResponseStatusException(HttpStatus.GONE,"LEGACY_ISSUE_CONFLICT_ARBITRATION_RETIRED: External Issue conflicts are historical evidence only. Redmine is the external Issue authority.");}

 private IntegrationProjectMapping requireCanonicalProjectMapping(ProviderWebhookMachinePrincipal machine,String externalProjectId){
  String project=required(externalProjectId,"externalProjectId");
  if(identities==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"RS5_EXTERNAL_PROJECT_MAPPING_REQUIRED: Integration mapping authority is unavailable.");
  List<IntegrationProjectMapping> matches=identities.listMappings(machine.tenantId(),machine.connectionId(),2000).stream()
   .filter(IntegrationProjectMapping::enabled)
   .filter(v->v.lifecycleStatus()==ProjectMappingLifecycle.ACTIVE)
   .filter(v->project.equals(v.externalProjectId()))
   .toList();
  if(matches.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"RS5_EXTERNAL_PROJECT_MAPPING_REQUIRED: provider project is not mapped in this Tenant/Connection.");
  IntegrationProjectMapping mapping;
  List<IntegrationProjectMapping> defaults=matches.stream().filter(IntegrationProjectMapping::defaultMapping).toList();
  if(defaults.size()==1)mapping=defaults.get(0);
  else if(matches.size()==1)mapping=matches.get(0);
  else throw new ResponseStatusException(HttpStatus.CONFLICT,"RS5_EXTERNAL_PROJECT_MAPPING_AMBIGUOUS: provider project has multiple active mappings.");
  if(mapping.webhookPrincipalId()!=null&&!mapping.webhookPrincipalId().isBlank()&&!mapping.webhookPrincipalId().equals(machine.principalId()))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"RS5_WEBHOOK_MAPPING_PRINCIPAL_MISMATCH: webhook principal does not match project mapping.");
  return mapping;
 }
 private ProviderWebhookRequest parse(byte[] body){try{return json.readValue(body,ProviderWebhookRequest.class);}catch(Exception e){throw bad("Webhook JSON body is invalid.");}}
 private ProviderWebhookMachinePrincipal machine(Authentication a){if(a==null||!(a.getPrincipal() instanceof ProviderWebhookMachinePrincipal p))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Webhook machine authentication is required.");return p;}
 private String tenant(){return required(context().tenantId(),"tenantId");} private String operator(){return required(context().operatorId(),"operatorId");} private OpenDispatchRequestContext context(){return OpenDispatchRequestContextHolder.current().orElseThrow(()->bad("Request context is required."));}
 private String required(String v,String n){if(v==null||v.isBlank())throw bad(n+" is required.");return v.trim();} private long parseVersion(String value){String v=required(value,"If-Match").replace("W/","").replace("\"","").replace("'","").trim();try{long parsed=Long.parseLong(v);if(parsed<1)throw new NumberFormatException();return parsed;}catch(NumberFormatException e){throw bad("If-Match must contain a positive row version.");}} private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private <T>T run(Op<T> op){try{return op.get();}catch(ResponseStatusException e){throw e;}catch(IllegalArgumentException e){throw bad(e.getMessage());}catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage());}} @FunctionalInterface private interface Op<T>{T get();}
 public record ProviderWebhookRequest(String eventType,String externalProjectId,String externalIssueId,String externalIssueKey,String externalIssueStatus,String observedDocumentJson,String providerIdentity,String mappingSchemaHash,Long providerEventSequence,String providerChangeVersion){}
 public record GovernedReason(String reason){} public record ConflictResolutionRequest(ConflictResolutionPolicy policy,String reason){}
}
