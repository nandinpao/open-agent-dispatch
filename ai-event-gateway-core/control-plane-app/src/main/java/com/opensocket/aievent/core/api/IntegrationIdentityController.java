package com.opensocket.aievent.core.api;

import com.opensocket.aievent.core.http.context.*;
import com.opensocket.aievent.core.integration.identity.*;
import com.opensocket.aievent.core.integration.issue.credential.ManagedIntegrationSecretIntakeService;
import com.opensocket.aievent.core.integration.issue.readiness.IssueTrackingRuntimeReadinessService;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.IntegrationResourceAccessCoordinator;
import java.time.*; import java.util.*; import java.util.function.Function; import java.util.stream.Collectors;
import org.slf4j.*; import org.springframework.beans.factory.annotation.*; import org.springframework.http.*;
import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException;

/** Current Integration identity API with P4RA-F query-time scope and metadata-only credential responses. */
@RestController @RequestMapping("/api/integrations")
public class IntegrationIdentityController {
 private static final Logger log=LoggerFactory.getLogger(IntegrationIdentityController.class);
 private final IntegrationIdentityService service; private final ProjectMappingGovernanceService mappings; private final IntegrationWebhookEndpointService webhookEndpoints; private final ManagedIntegrationSecretIntakeService managedSecrets; private final IntegrationCredentialMaterialVerifier credentialMaterials; private final IssueTrackingRuntimeReadinessService issueReadiness;
 @Autowired(required=false) private IntegrationResourceAccessCoordinator resourceAccess;
 @Value("${resource-access.integration-enabled:false}") private boolean integrationResourceAccessEnabled;
 @Value("${resource-access.enforcement-mode:OFF}") private ResourceAccessEnforcementMode enforcementMode=ResourceAccessEnforcementMode.OFF;
 public IntegrationIdentityController(IntegrationIdentityService service,ProjectMappingGovernanceService mappings,IntegrationWebhookEndpointService webhookEndpoints,ManagedIntegrationSecretIntakeService managedSecrets,IntegrationCredentialMaterialVerifier credentialMaterials,IssueTrackingRuntimeReadinessService issueReadiness){this.service=service;this.mappings=mappings;this.webhookEndpoints=webhookEndpoints;this.managedSecrets=managedSecrets;this.credentialMaterials=credentialMaterials;this.issueReadiness=issueReadiness;}

 @GetMapping("/connections") public List<IntegrationConnection> connections(@RequestParam(defaultValue="200")int limit){return run(()->scopedConnections(limit));}
 @GetMapping("/connections/{connectionId}") public IntegrationConnection connection(@PathVariable String connectionId){authorize(ResourceType.ISSUE_CONNECTION,connectionId,"integration.issue.connection.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.STANDARD,"INTEGRATION_CONNECTION_READ");return run(()->service.connection(tenant(),connectionId));}
 @PutMapping("/connections/{connectionId}") public IntegrationConnection connection(@PathVariable String connectionId,@RequestHeader(value="If-Match",required=false)Long expected,@RequestBody IntegrationConnection body){authorize(ResourceType.ISSUE_CONNECTION,connectionId,"integration.issue.connection.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_CONNECTION_UPDATE");return run(()->service.saveConnection(tenant(),connectionId,body,expected));}

 @PostMapping("/connections/{connectionId}/metadata-discovery") public ProviderMetadataSnapshot metadataDiscovery(@PathVariable String connectionId,@RequestParam(required=false)String principalId){authorize(ResourceType.ISSUE_CONNECTION,connectionId,"integration.issue.connection.read",ResourceAction.ActionKind.EXECUTE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_METADATA_DISCOVERY");return run(()->mappings.discover(tenant(),connectionId,principalId,correlation()));}

 @PostMapping("/source-systems/{sourceSystemId}/issue-tracking/activate")
 public IssueTrackingActivationView activateIssueTracking(
         @PathVariable String sourceSystemId,
         @RequestHeader("Idempotency-Key") String key,
         @RequestBody IssueTrackingActivationRequest body) {
  required(key, "Idempotency-Key");
  String source = required(sourceSystemId, "sourceSystemId");
  String baseMappingId = sourceIssueMappingId(source);
  authorize(ResourceType.ISSUE_PROJECT_MAPPING, baseMappingId, "integration.issue.mapping.update",
          ResourceAction.ActionKind.APPROVE, true, VisibilityLevel.SENSITIVE,
          "INTEGRATION_SOURCE_ISSUE_TRACKING_ACTIVATE");
  return run(() -> {
   String tenant = tenant();
   String connectionId = required(body == null ? null : body.connectionId(), "connectionId");
   String principalId = required(body == null ? null : body.principalId(), "principalId");
   String projectId = required(body == null ? null : body.projectId(), "projectId");
   String trackerId = required(body == null ? null : body.trackerId(), "trackerId");
   String projectKey = body == null ? null : body.projectKey();

   List<IntegrationProjectMapping> allMappings = service.mappings(tenant, null, 1000);
   List<IntegrationProjectMapping> sourceDefaults = allMappings.stream()
           .filter(value -> source.equals(value.sourceSystemId()))
           .filter(value -> value.taskType() == null || value.taskType().isBlank())
           .toList();

   IntegrationProjectMapping runtimeReady = sourceDefaults.stream()
           .filter(ProjectMappingRuntimeReadiness::isReady)
           .findFirst().orElse(null);
   if (runtimeReady != null && sameIssueTrackingTarget(runtimeReady, connectionId, principalId, projectId, trackerId)) {
    try {
     var runtime = service.resolveConnectorExecutionContext(new MappingResolutionRequest(
             tenant, connectionId, null, null, null, source, null));
     if (Objects.equals(runtime.mapping().mappingId(), runtimeReady.mappingId())) {
      var material = credentialMaterials.requireReady(runtime.credential());
      var liveProbe = service.probeConnectorExecutionContext(tenant, runtime.principal().principalId(), runtime.mapping().mappingId(), correlation());
      if (liveProbe.capabilityResults().getOrDefault(IntegrationPermissionCapability.AUTHENTICATE, PermissionProbeResultStatus.DENIED)
              != PermissionProbeResultStatus.GRANTED) {
       throw new IllegalStateException("ISSUE_TRACKING_RUNTIME_AUTHENTICATION_NOT_READY: " + liveProbe.providerResponseSummary());
      }
      log.info("issue_tracking_activation_existing_mapping_runtime_ready sourceSystemId={} mappingId={} credentialId={} credentialVersion={} secretScheme={} secretResolverMode={} materialResolvable=true",
              source, runtimeReady.mappingId(), runtime.credential().credentialId(), runtime.credential().secretVersion(),
              material.secretScheme(), material.resolverMode());
      return new IssueTrackingActivationView(runtimeReady, null, null, liveProbe, true, 0,
              "Issue Tracking is already runtime-ready for this Source System.");
     }
     log.warn("issue_tracking_activation_existing_mapping_not_authoritative sourceSystemId={} expectedMappingId={} resolvedMappingId={}",
             source, runtimeReady.mappingId(), runtime.mapping().mappingId());
    } catch (IllegalStateException ex) {
     // A visually ACTIVE mapping can still be unusable because of ambiguity, connector
     // readiness or credential state. Continue through the governed repair path instead
     // of returning the old false-positive 'already active' result.
     log.warn("issue_tracking_activation_existing_mapping_preflight_failed sourceSystemId={} mappingId={} error={}",
             source, runtimeReady.mappingId(), ex.getMessage());
    }
   }

   var permission = service.probe(tenant, principalId, null, correlation());
   if (permission.capabilityResults().getOrDefault(
           IntegrationPermissionCapability.AUTHENTICATE,
           PermissionProbeResultStatus.DENIED) != PermissionProbeResultStatus.GRANTED) {
    throw new IllegalStateException("REDMINE_AUTHENTICATION_REQUIRED: " + permission.providerResponseSummary());
   }

   String mappingId = nextSourceIssueMappingId(baseMappingId, allMappings);
   var now = OffsetDateTime.now();
   var draft = new IntegrationProjectMapping(
           tenant, mappingId, connectionId, null, null, null, source, null,
           projectId, projectKey, trackerId, trackerId, principalId,
           null, null, null, null, null, null, null, null,
           ProjectMappingStatus.DRAFT, 1000, true, false, 0L, now, now);
   var saved = service.saveMapping(tenant, mappingId, draft, null);
   var metadata = mappings.probe(tenant, saved.mappingId(), principalId, true, correlation());
   if (metadata.cacheStatus() == ProviderMetadataCacheStatus.FAILED) {
    throw new IllegalStateException("REDMINE_METADATA_PROBE_FAILED: " + metadata.providerSummary());
   }
   var validation = mappings.validate(tenant, saved.mappingId(), "operator");
   if (!validation.valid()) {
    throw new IllegalStateException("REDMINE_PROJECT_SELECTION_INVALID: " + String.join(", ", validation.errors()));
   }

   int retiredMappings = (int) allMappings.stream()
           .filter(value -> value.enabled() && value.lifecycleStatus() == ProjectMappingLifecycle.ACTIVE)
           .filter(value -> source.equals(value.sourceSystemId()))
           .count();
   var published = mappings.publishSourceDefaultAndRetireOverrides(tenant, saved.mappingId(), source, "operator");
   if (!ProjectMappingRuntimeReadiness.isReady(published)) {
    throw new IllegalStateException("ISSUE_TRACKING_ACTIVATION_NOT_RUNTIME_READY: "
            + String.join(",", ProjectMappingRuntimeReadiness.blockers(published)));
   }
   var runtime = service.resolveConnectorExecutionContext(new MappingResolutionRequest(
           tenant, connectionId, null, null, null, source, null));
   if (!Objects.equals(runtime.mapping().mappingId(), published.mappingId())) {
    throw new IllegalStateException("ISSUE_TRACKING_RUNTIME_MAPPING_MISMATCH: expected="
            + published.mappingId() + ", resolved=" + runtime.mapping().mappingId());
   }
   var material = credentialMaterials.requireReady(runtime.credential());
   var runtimeProbe = service.probeConnectorExecutionContext(tenant, runtime.principal().principalId(), runtime.mapping().mappingId(), correlation());
   if (runtimeProbe.capabilityResults().getOrDefault(IntegrationPermissionCapability.AUTHENTICATE, PermissionProbeResultStatus.DENIED)
           != PermissionProbeResultStatus.GRANTED) {
    throw new IllegalStateException("ISSUE_TRACKING_RUNTIME_AUTHENTICATION_NOT_READY: " + runtimeProbe.providerResponseSummary());
   }
   log.info("issue_tracking_activation_runtime_ready sourceSystemId={} mappingId={} credentialId={} credentialVersion={} secretScheme={} secretResolverMode={} materialResolvable=true",
           source, published.mappingId(), runtime.credential().credentialId(), runtime.credential().secretVersion(),
           material.secretScheme(), material.resolverMode());
   String message = sourceDefaults.stream().anyMatch(value -> value.enabled()
           && value.lifecycleStatus() == ProjectMappingLifecycle.ACTIVE)
           ? "Issue Tracking repaired and activated for Source System " + source + "."
           : "Issue Tracking activated for Source System " + source + ".";
   return new IssueTrackingActivationView(
           published, metadata, validation, runtimeProbe, false, retiredMappings, message);
  });
 }

 @GetMapping("/source-systems/{sourceSystemId}/issue-tracking/readiness")
 public IssueTrackingRuntimeReadiness issueTrackingReadiness(
         @PathVariable String sourceSystemId,
         @RequestParam(required=false) String taskType) {
  String source=required(sourceSystemId,"sourceSystemId");
  authorize(ResourceType.SOURCE_SYSTEM,source,"admin.source.system.detail",
          ResourceAction.ActionKind.READ,false,VisibilityLevel.STANDARD,"INTEGRATION_SOURCE_ISSUE_TRACKING_READINESS");
  return run(()->issueReadiness.evaluate(tenant(),source,taskType,false,correlation()));
 }

 @PostMapping("/source-systems/{sourceSystemId}/issue-tracking/readiness/probe")
 public IssueTrackingRuntimeReadiness probeIssueTrackingReadiness(
         @PathVariable String sourceSystemId,
         @RequestParam(required=false) String taskType) {
  String source=required(sourceSystemId,"sourceSystemId");
  authorize(ResourceType.SOURCE_SYSTEM,source,"admin.source.system.update",
          ResourceAction.ActionKind.EXECUTE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_SOURCE_ISSUE_TRACKING_READINESS_PROBE");
  return run(()->issueReadiness.evaluate(tenant(),source,taskType,true,correlation()));
 }

 @GetMapping("/connections/{connectionId}/principals") public List<IntegrationPrincipal> principals(@PathVariable String connectionId,@RequestParam(defaultValue="200")int limit){return run(()->scopedPrincipals(connectionId,limit));}
 @GetMapping("/principals/{principalId}") public IntegrationPrincipal principal(@PathVariable String principalId){authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.principal.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_READ");return run(()->service.principal(tenant(),principalId));}
 @PutMapping("/connections/{connectionId}/principals/{principalId}") public IntegrationPrincipal principal(@PathVariable String connectionId,@PathVariable String principalId,@RequestHeader(value="If-Match",required=false)Long expected,@RequestBody IntegrationPrincipal body){authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.principal.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_UPDATE");return run(()->service.savePrincipal(tenant(),connectionId,principalId,body,expected));}
 @GetMapping("/principals/{principalId}/scope") public IntegrationPrincipalScope principalScope(@PathVariable String principalId){authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.principal.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_SCOPE_READ");return run(()->service.principalScope(tenant(),principalId));}
 @PutMapping("/principals/{principalId}/scope") public IntegrationPrincipalScope principalScope(@PathVariable String principalId,@RequestHeader(value="If-Match",required=false)Long expected,@RequestBody IntegrationPrincipalScope body){authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.principal.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_SCOPE_UPDATE");throw new ResponseStatusException(HttpStatus.GONE,"INTEGRATION_PRINCIPAL_SCOPE_RETIRED: Historical scope rows are read-only. Use Project Mapping + one Technical Service Account + Credential; Redmine/Jira is the final Issue permission authority.");}
 @GetMapping("/principal-scopes") public List<IntegrationPrincipalScope> principalScopes(@RequestParam(required=false)String connectionId,@RequestParam(defaultValue="500")int limit){if(connectionId!=null&&!connectionId.isBlank())authorize(ResourceType.ISSUE_CONNECTION,connectionId,"integration.issue.principal.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_SCOPE_LIST");return run(()->service.principalScopes(tenant(),connectionId,limit));}

 @GetMapping("/principals/{principalId}/credentials") public List<CredentialMetadataView> credentials(@PathVariable String principalId,@RequestParam(defaultValue="200")int limit){return run(()->scopedCredentials(principalId,limit).stream().map(CredentialMetadataView::from).toList());}
 @GetMapping("/credentials/{credentialId}") public CredentialMetadataView credential(@PathVariable String credentialId){authorize(ResourceType.ISSUE_CREDENTIAL_METADATA,credentialId,"integration.issue.credential-metadata.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SECRET_METADATA,"INTEGRATION_CREDENTIAL_METADATA_READ");return run(()->CredentialMetadataView.from(service.credential(tenant(),credentialId)));}
 @PostMapping("/principals/{principalId}/credentials/{credentialId}") public CredentialMetadataView credential(@PathVariable String principalId,@PathVariable String credentialId,@RequestHeader("Idempotency-Key")String key,@RequestBody IntegrationCredentialMetadata body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.credential.rotate",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SECRET_METADATA,"INTEGRATION_CREDENTIAL_CREATE");return run(()->CredentialMetadataView.from(service.addCredential(tenant(),principalId,credentialId,body)));}
 @PostMapping("/principals/{principalId}/redmine-api-key") public RedmineCredentialTestView redmineApiKey(@PathVariable String principalId,@RequestHeader("Idempotency-Key")String key,@RequestBody RedmineApiKeyRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.credential.rotate",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SECRET_METADATA,"INTEGRATION_REDMINE_API_KEY_INTAKE");return run(()->{String tenant=tenant();String apiKey=required(body==null?null:body.apiKey(),"apiKey");var principal=service.principal(tenant,principalId);var connection=service.connection(tenant,principal.connectionId());if(connection.providerType()!=IntegrationProviderType.REDMINE)throw bad("REDMINE_CONNECTION_REQUIRED");service.revokePendingCredentials(tenant,principalId);var active=service.credentials(tenant,principalId,100).stream().filter(v->v.status()==IntegrationCredentialStatus.ACTIVE).max(Comparator.comparing(IntegrationCredentialMetadata::createdAt,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingLong(IntegrationCredentialMetadata::version)).orElse(null);String credentialId=(principalId+"-redmine-api-"+UUID.randomUUID().toString().substring(0,8)).replaceAll("[^A-Za-z0-9._-]","-");if(credentialId.length()>128)credentialId=credentialId.substring(0,119)+"-"+UUID.randomUUID().toString().substring(0,8);char[] chars=apiKey.toCharArray();try{var stored=managedSecrets.storeRedmineApiKey(tenant,principalId,credentialId,chars);var metadata=new IntegrationCredentialMetadata(tenant,credentialId,principalId,IntegrationAuthType.API_TOKEN,stored.secretRef(),stored.secretVersion(),stored.secretLast4(),OffsetDateTime.now(),null,null,null,IntegrationCredentialStatus.PENDING_VALIDATION,1,OffsetDateTime.now(),OffsetDateTime.now());IntegrationCredentialMetadata saved=active==null?service.addCredential(tenant,principalId,credentialId,metadata):service.rotateCredential(tenant,principalId,active.credentialId(),credentialId,metadata);var probe=service.probe(tenant,principalId,null,correlation());boolean authenticated=probe.capabilityResults().getOrDefault(IntegrationPermissionCapability.AUTHENTICATE,PermissionProbeResultStatus.DENIED)==PermissionProbeResultStatus.GRANTED;var current=service.credential(tenant,saved.credentialId());return new RedmineCredentialTestView(CredentialMetadataView.from(current),probe,authenticated,stored.storageMode());}finally{Arrays.fill(chars,'\0');}});}
 @PostMapping("/principals/{principalId}/rotate-credential") public CredentialMetadataView rotate(@PathVariable String principalId,@RequestHeader("Idempotency-Key")String key,@RequestBody RotateCredentialRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_CREDENTIAL_METADATA,body.oldCredentialId(),"integration.issue.credential.rotate",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SECRET_METADATA,"INTEGRATION_CREDENTIAL_ROTATE");return run(()->CredentialMetadataView.from(service.rotateCredential(tenant(),principalId,body.oldCredentialId(),body.newCredentialId(),body.newCredential())));}
 @PostMapping("/principals/{principalId}/rotations/{rotationEventId}/complete") public CredentialRotationEvent completeRotation(@PathVariable String principalId,@PathVariable String rotationEventId,@RequestHeader("Idempotency-Key")String key){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.credential.rotate",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SECRET_METADATA,"INTEGRATION_CREDENTIAL_ROTATION_COMPLETE");return run(()->service.completeRotation(tenant(),principalId,rotationEventId));}
 @PostMapping("/principals/{principalId}/revoke") public IntegrationPrincipal revoke(@PathVariable String principalId,@RequestHeader("Idempotency-Key")String key,@RequestBody(required=false)RevokePrincipalRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.principal.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_REVOKE");return run(()->service.revokePrincipal(tenant(),principalId,body==null?"Revoked by operator.":body.reason()));}

 @GetMapping("/project-mappings") public List<IntegrationProjectMapping> projectMappings(@RequestParam(required=false)String connectionId,@RequestParam(defaultValue="500")int limit){return run(()->scopedMappings(connectionId,limit));}
 @GetMapping("/project-mappings/{mappingId}") public IntegrationProjectMapping mapping(@PathVariable String mappingId){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.STANDARD,"INTEGRATION_MAPPING_READ");return run(()->service.mapping(tenant(),mappingId));}
 @PutMapping("/project-mappings/{mappingId}") public IntegrationProjectMapping mapping(@PathVariable String mappingId,@RequestHeader(value="If-Match",required=false)Long expected,@RequestBody IntegrationProjectMapping body){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_UPDATE");return run(()->service.saveMapping(tenant(),mappingId,body,expected));}
 @PostMapping("/project-mappings/resolve") public IntegrationProjectMapping resolve(@RequestBody MappingResolutionRequest body){return run(()->service.resolve(new MappingResolutionRequest(tenant(),body.connectionId(),body.departmentId(),body.groupId(),body.serviceDomainId(),body.sourceSystemId(),body.taskType())));}
 @PostMapping("/project-mappings/connector-runtime-preflight")
 public Map<String,Object> connectorRuntimePreflight(@RequestBody MappingResolutionRequest body){
  return run(()->{
   var request=new MappingResolutionRequest(tenant(),body.connectionId(),body.departmentId(),body.groupId(),body.serviceDomainId(),body.sourceSystemId(),body.taskType());
   var context=service.resolveConnectorExecutionContext(request);
   var out=new LinkedHashMap<String,Object>();
   out.put("status","READY");
   out.put("providerType",context.connection().providerType().name());
   out.put("connectionId",context.connection().connectionId());
   out.put("mappingId",context.mapping().mappingId());
   out.put("mappingVersion",context.mapping().mappingVersion());
   out.put("mappingStatus",context.mapping().mappingStatus().name());
   out.put("mappingLifecycle",context.mapping().lifecycleStatus().name());
   out.put("mappingSchemaHash",context.mapping().metadataSchemaHash());
   out.put("sourceSystemId",context.mapping().sourceSystemId());
   out.put("externalProjectId",context.mapping().externalProjectId());
   out.put("externalProjectKey",context.mapping().externalProjectKey());
   out.put("externalTrackerId",context.mapping().externalTrackerId());
   out.put("technicalPrincipalId",context.principal().principalId());
   out.put("credentialId",context.credential().credentialId());
   out.put("credentialVersion",context.credential().secretVersion());
   var material=credentialMaterials.verify(context.credential());
   out.put("credentialSecretScheme",material.secretScheme());
   out.put("credentialSecretResolverMode",material.resolverMode());
   out.put("credentialMaterialResolvable",material.materialResolvable());
   out.put("credentialMaterialErrorCode",material.errorCode());
   if(!material.materialResolvable())throw new IllegalStateException(material.errorCode());
   var probe=service.probeConnectorExecutionContext(tenant(),context.principal().principalId(),context.mapping().mappingId(),correlation());
   var authenticated=probe.capabilityResults().getOrDefault(IntegrationPermissionCapability.AUTHENTICATE,PermissionProbeResultStatus.DENIED)==PermissionProbeResultStatus.GRANTED;
   out.put("authenticationStatus",authenticated?"READY":"NOT_READY");
   out.put("authenticationSummary",probe.providerResponseSummary());
   if(!authenticated)throw new IllegalStateException("ISSUE_TRACKING_RUNTIME_AUTHENTICATION_NOT_READY: "+probe.providerResponseSummary());
   return out;
  });
 }
 @PostMapping("/principals/{principalId}/permission-probe") public PermissionProbeResult probePrincipal(@PathVariable String principalId,@RequestParam(required=false)String mappingId){authorize(ResourceType.ISSUE_PRINCIPAL,principalId,"integration.issue.principal.read",ResourceAction.ActionKind.EXECUTE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_PERMISSION_PROBE");return run(()->service.probe(tenant(),principalId,mappingId,correlation()));}
 @PostMapping("/project-mappings/{mappingId}/permission-probe") public PermissionProbeResult probeMapping(@PathVariable String mappingId,@RequestParam String principalId){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.EXECUTE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_PERMISSION_PROBE");return run(()->service.probe(tenant(),principalId,mappingId,correlation()));}
 @PostMapping("/project-mappings/{mappingId}/metadata-probe") public ProviderMetadataSnapshot metadataProbe(@PathVariable String mappingId,@RequestParam(required=false)String principalId,@RequestParam(defaultValue="false")boolean force){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.EXECUTE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_METADATA_PROBE");return run(()->mappings.probe(tenant(),mappingId,principalId,force,correlation()));}
 @GetMapping("/project-mappings/{mappingId}/metadata-snapshots") public List<ProviderMetadataSnapshot> metadataSnapshots(@PathVariable String mappingId,@RequestParam(defaultValue="100")int limit){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_METADATA_SNAPSHOT_LIST");return run(()->mappings.snapshots(tenant(),mappingId,limit));}
 @GetMapping("/metadata-snapshots/{snapshotId}") public ProviderMetadataSnapshot metadataSnapshot(@PathVariable String snapshotId){authorize(ResourceType.ISSUE_CONTEXT_SNAPSHOT,snapshotId,"integration.issue.snapshot.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_METADATA_SNAPSHOT_READ");return run(()->mappings.snapshot(tenant(),snapshotId));}

 @PostMapping("/project-mappings/{mappingId}/validate") public ProjectMappingValidationResult validateMapping(@PathVariable String mappingId,@RequestHeader("Idempotency-Key")String key,@RequestBody(required=false)MappingActionRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_VALIDATE");return run(()->mappings.validate(tenant(),mappingId,body==null?"operator":body.actorId()));}
 @PostMapping("/project-mappings/{mappingId}/publish") public IntegrationProjectMapping publishMapping(@PathVariable String mappingId,@RequestHeader("Idempotency-Key")String key,@RequestBody MappingActionRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.update",ResourceAction.ActionKind.APPROVE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_PUBLISH");return run(()->mappings.publish(tenant(),mappingId,required(body.actorId(),"actorId")));}
 @PostMapping("/project-mappings/{mappingId}/deprecate") public IntegrationProjectMapping deprecateMapping(@PathVariable String mappingId,@RequestHeader("Idempotency-Key")String key,@RequestBody MappingActionRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_DEPRECATE");return run(()->mappings.deprecate(tenant(),mappingId,required(body.actorId(),"actorId")));}
 @PostMapping("/project-mappings/{mappingId}/fork") public IntegrationProjectMapping forkMapping(@PathVariable String mappingId,@RequestHeader("Idempotency-Key")String key,@RequestBody ForkMappingRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.update",ResourceAction.ActionKind.CREATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_FORK");return run(()->mappings.fork(tenant(),mappingId,required(body.newMappingId(),"newMappingId"),required(body.actorId(),"actorId")));}
 @PostMapping("/project-mappings/{mappingId}/rollback") public IntegrationProjectMapping rollbackMapping(@PathVariable String mappingId,@RequestHeader("Idempotency-Key")String key,@RequestBody RollbackMappingRequest body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_ROLLBACK");return run(()->mappings.rollback(tenant(),mappingId,body.targetVersion(),required(body.newMappingId(),"newMappingId"),required(body.actorId(),"actorId")));}
 @GetMapping("/project-mappings/{mappingId}/versions") public List<IntegrationProjectMappingVersion> mappingVersions(@PathVariable String mappingId,@RequestParam(defaultValue="100")int limit){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_VERSION_LIST");return run(()->mappings.versions(tenant(),mappingId,limit));}
 @PostMapping("/project-mappings/{mappingId}/preview") public ProjectMappingPreview previewMapping(@PathVariable String mappingId,@RequestBody(required=false)ProjectMappingPreviewRequest body){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_PREVIEW");return run(()->mappings.preview(tenant(),mappingId,body));}
 @GetMapping("/project-mappings/{mappingId}/diff") public ProjectMappingDiff diffMapping(@PathVariable String mappingId,@RequestParam int fromVersion,@RequestParam int toVersion){authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_MAPPING_DIFF");return run(()->mappings.diff(tenant(),mappingId,fromVersion,toVersion));}

 @GetMapping("/webhook-endpoints") public List<IntegrationWebhookEndpoint> webhookEndpoints(@RequestParam(required=false)String connectionId,@RequestParam(defaultValue="200")int limit){if(connectionId!=null&&!connectionId.isBlank())authorize(ResourceType.ISSUE_CONNECTION,connectionId,"integration.issue.connection.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_WEBHOOK_ENDPOINT_LIST");return run(()->webhookEndpoints.endpoints(tenant(),connectionId,limit));}
 @GetMapping("/webhook-endpoints/{endpointId}") public IntegrationWebhookEndpoint webhookEndpoint(@PathVariable String endpointId){return run(()->webhookEndpoints.endpoint(tenant(),endpointId));}
 @PutMapping("/webhook-endpoints/{endpointId}") public IntegrationWebhookEndpoint webhookEndpoint(@PathVariable String endpointId,@RequestHeader(value="If-Match",required=false)Long expected,@RequestBody IntegrationWebhookEndpoint body){authorize(ResourceType.ISSUE_CONNECTION,body.connectionId(),"integration.issue.connection.update",ResourceAction.ActionKind.UPDATE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_WEBHOOK_ENDPOINT_UPDATE");return run(()->webhookEndpoints.save(tenant(),endpointId,body,expected));}

 @GetMapping("/security-overrides") public List<IntegrationSecurityOverride> securityOverrides(@RequestParam(required=false)String principalId,@RequestParam(required=false)String mappingId,@RequestParam(defaultValue="200")int limit){if(mappingId!=null&&!mappingId.isBlank())authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_SECURITY_OVERRIDE_LIST");return run(()->service.securityOverrides(tenant(),principalId,mappingId,limit));}
 @PostMapping("/security-overrides/{overrideId}") public IntegrationSecurityOverride grantOverride(@PathVariable String overrideId,@RequestHeader("Idempotency-Key")String key,@RequestBody IntegrationSecurityOverride body){required(key,"Idempotency-Key");authorize(ResourceType.ISSUE_PROJECT_MAPPING,body.mappingId(),"integration.issue.mapping.update",ResourceAction.ActionKind.APPROVE,true,VisibilityLevel.SENSITIVE,"INTEGRATION_SECURITY_OVERRIDE_GRANT");return run(()->service.grantSecurityOverride(tenant(),overrideId,body));}
 @PostMapping("/security-overrides/{overrideId}/revoke") public IntegrationSecurityOverride revokeOverride(@PathVariable String overrideId,@RequestHeader("Idempotency-Key")String key,@RequestBody RevokeOverrideRequest body){required(key,"Idempotency-Key");return run(()->service.revokeSecurityOverride(tenant(),overrideId,body.actorId()));}
 @GetMapping("/permission-changes") public List<IntegrationPermissionChangeEvent> permissionChanges(@RequestParam(required=false)String principalId,@RequestParam(required=false)String mappingId,@RequestParam(defaultValue="200")int limit){return run(()->service.permissionChanges(tenant(),principalId,mappingId,limit));}
 @GetMapping("/authorization-failures") public List<IntegrationProviderAuthorizationFailure> authorizationFailures(@RequestParam(required=false)String mappingId,@RequestParam(defaultValue="200")int limit){if(mappingId!=null&&!mappingId.isBlank())authorize(ResourceType.ISSUE_PROJECT_MAPPING,mappingId,"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_AUTH_FAILURE_LIST");return run(()->service.authorizationFailures(tenant(),mappingId,limit));}
 @PostMapping("/issue-relay/readiness") public CrossProjectRelayReadiness relayReadiness(@RequestBody RelayReadinessRequest body){authorize(ResourceType.ISSUE_PROJECT_MAPPING,body.sourceMappingId(),"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_RELAY_READINESS");authorize(ResourceType.ISSUE_PROJECT_MAPPING,body.targetMappingId(),"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,"INTEGRATION_RELAY_READINESS");return run(()->service.relayReadiness(tenant(),body.sourceMappingId(),body.targetMappingId()));}
 @GetMapping("/credentials/{credentialId}/blast-radius") public CredentialBlastRadius blastRadius(@PathVariable String credentialId){authorize(ResourceType.ISSUE_CREDENTIAL_METADATA,credentialId,"integration.issue.credential-metadata.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SECRET_METADATA,"INTEGRATION_CREDENTIAL_BLAST_RADIUS");return run(()->service.blastRadius(tenant(),credentialId));}

 private List<IntegrationConnection> scopedConnections(int limit){return scopedList("integration.issue.connection.read",ResourceType.ISSUE_CONNECTION,VisibilityLevel.STANDARD,"INTEGRATION_CONNECTION_LIST",limit,()->service.connections(tenant(),limit),s->service.connections(tenant(),s,limit),IntegrationConnection::connectionId);}
 private List<IntegrationPrincipal> scopedPrincipals(String connectionId,int limit){return scopedList("integration.issue.principal.read",ResourceType.ISSUE_PRINCIPAL,VisibilityLevel.SENSITIVE,"INTEGRATION_PRINCIPAL_LIST",limit,()->service.principals(tenant(),connectionId,limit),s->service.principals(tenant(),connectionId,s,limit),IntegrationPrincipal::principalId);}
 private List<IntegrationCredentialMetadata> scopedCredentials(String principalId,int limit){return scopedList("integration.issue.credential-metadata.read",ResourceType.ISSUE_CREDENTIAL_METADATA,VisibilityLevel.SECRET_METADATA,"INTEGRATION_CREDENTIAL_METADATA_LIST",limit,()->service.credentials(tenant(),principalId,limit),s->service.credentials(tenant(),principalId,s,limit),IntegrationCredentialMetadata::credentialId);}
 private List<IntegrationProjectMapping> scopedMappings(String connectionId,int limit){return scopedList("integration.issue.mapping.read",ResourceType.ISSUE_PROJECT_MAPPING,VisibilityLevel.STANDARD,"INTEGRATION_MAPPING_LIST",limit,()->service.mappings(tenant(),connectionId,limit),s->service.mappings(tenant(),connectionId,s,limit),IntegrationProjectMapping::mappingId);}
 private <T> List<T> scopedList(String permission,ResourceType type,VisibilityLevel visibility,String purpose,int limit,Op<List<T>> legacy,Function<IntegrationResourceScope,List<T>> scoped,Function<T,String> id){
  if(!integrationResourceAccessEnabled||enforcementMode==ResourceAccessEnforcementMode.OFF)return legacy.get();
  if(resourceAccess==null){if(enforcementMode==ResourceAccessEnforcementMode.SHADOW||enforcementMode==ResourceAccessEnforcementMode.WRITE_ENFORCE){log.error("integration_resource_scope_unavailable purpose={}",purpose);return legacy.get();}throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Resource Access Integration scope planner is unavailable.");}
  IntegrationScopeQueryPlan plan=resourceAccess.plan(permission,type,visibility,purpose);List<T> allowed=scoped.apply(resourceAccess.toScope(plan));
  if(enforcementMode==ResourceAccessEnforcementMode.READ_ENFORCE||enforcementMode==ResourceAccessEnforcementMode.FULL_ENFORCE)return allowed;
  List<T> old=legacy.get();Set<String> oldIds=old.stream().map(id).collect(Collectors.toSet()),newIds=allowed.stream().map(id).collect(Collectors.toSet());
  if(!oldIds.equals(newIds)){Set<String> legacyOnly=diff(oldIds,newIds),scopedOnly=diff(newIds,oldIds);log.warn("integration_resource_scope_shadow_mismatch purpose={} planHash={} legacyOnly={} scopedOnly={}",purpose,plan.planHash(),legacyOnly,scopedOnly);resourceAccess.shadowMismatch(plan,purpose,legacyOnly,scopedOnly);}return old;
 }
 private void authorize(ResourceType type,String id,String permission,ResourceAction.ActionKind kind,boolean sideEffect,VisibilityLevel visibility,String purpose){if(!integrationResourceAccessEnabled)return;if(resourceAccess==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Resource Access Integration guard is unavailable.");resourceAccess.authorize(type,required(id,"resourceId"),permission,kind,sideEffect,visibility,purpose);}
 private static Set<String> diff(Set<String>a,Set<String>b){return a.stream().filter(v->!b.contains(v)).limit(50).collect(Collectors.toSet());}
 private boolean sameIssueTrackingTarget(IntegrationProjectMapping mapping, String connectionId,
                                                String principalId, String projectId, String trackerId) {
  if (mapping == null) return false;
  if (!Objects.equals(mapping.connectionId(), connectionId)
          || !Objects.equals(mapping.externalProjectId(), projectId)
          || !Objects.equals(mapping.externalTrackerId(), trackerId)) return false;
  List<String> explicit = Arrays.asList(mapping.readPrincipalId(), mapping.createPrincipalId(),
          mapping.commentPrincipalId(), mapping.updatePrincipalId()).stream()
          .filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().toList();
  return explicit.isEmpty() || (explicit.size() == 1 && Objects.equals(explicit.get(0), principalId));
 }
 private String nextSourceIssueMappingId(String baseMappingId, List<IntegrationProjectMapping> mappings) {
  Set<String> ids = mappings.stream().map(IntegrationProjectMapping::mappingId).collect(Collectors.toSet());
  if (!ids.contains(baseMappingId)) return baseMappingId;
  for (int version = 2; version < 10000; version++) {
   String candidate = baseMappingId + "-v" + version;
   if (!ids.contains(candidate)) return candidate;
  }
  throw new IllegalStateException("ISSUE_TRACKING_MAPPING_VERSION_EXHAUSTED");
 }
 private String tenant(){return required(context().tenantId(),"tenantId");} private String correlation(){String c=context().correlationId();return c==null||c.isBlank()?UUID.randomUUID().toString():c;} private OpenDispatchRequestContext context(){return OpenDispatchRequestContextHolder.current().orElseThrow(()->bad("Request context is required."));}
 private String required(String v,String n){if(v==null||v.isBlank())throw bad(n+" is required.");return v.trim();} private String sourceIssueMappingId(String source){String safe=source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-+|-+$","");if(safe.isBlank())safe="source";if(safe.length()>80)safe=safe.substring(0,80);return "issue-source-"+safe+"-default";} private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private <T>T run(Op<T> op){try{return op.get();}catch(ResponseStatusException e){throw e;}catch(IllegalArgumentException e){if(e.getMessage()!=null&&e.getMessage().contains("not found in Tenant"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());throw bad(e.getMessage());}catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage());}}
 @FunctionalInterface private interface Op<T>{T get();}
 public record CredentialMetadataView(String tenantId,String credentialId,String principalId,IntegrationAuthType authType,String secretVersion,String secretLast4,OffsetDateTime validFrom,OffsetDateTime expiresAt,OffsetDateTime rotatedAt,OffsetDateTime lastUsedAt,IntegrationCredentialStatus status,long version,OffsetDateTime createdAt,OffsetDateTime updatedAt){static CredentialMetadataView from(IntegrationCredentialMetadata v){return new CredentialMetadataView(v.tenantId(),v.credentialId(),v.principalId(),v.authType(),v.secretVersion(),v.secretLast4(),v.validFrom(),v.expiresAt(),v.rotatedAt(),v.lastUsedAt(),v.status(),v.version(),v.createdAt(),v.updatedAt());}}
 public record RedmineApiKeyRequest(String apiKey){} public record RedmineCredentialTestView(CredentialMetadataView credential,PermissionProbeResult probe,boolean authenticated,String storageMode){}
 public record IssueTrackingActivationRequest(String connectionId,String principalId,String projectId,String projectKey,String trackerId){} public record IssueTrackingActivationView(IntegrationProjectMapping mapping,ProviderMetadataSnapshot metadata,ProjectMappingValidationResult validation,PermissionProbeResult probe,boolean alreadyActive,int retiredLegacyMappings,String message){}
 public record RevokePrincipalRequest(String reason){} public record RevokeOverrideRequest(String actorId){} public record RelayReadinessRequest(String sourceMappingId,String targetMappingId){} public record RotateCredentialRequest(String oldCredentialId,String newCredentialId,IntegrationCredentialMetadata newCredential){} public record MappingActionRequest(String actorId,String reason){} public record ForkMappingRequest(String newMappingId,String actorId,String reason){} public record RollbackMappingRequest(int targetVersion,String newMappingId,String actorId,String reason){}
}
