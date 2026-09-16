package com.opensocket.aievent.core.iam.rbac.application.service;
import com.opensocket.aievent.core.iam.rbac.application.command.entrypoint.*;
import com.opensocket.aievent.core.iam.rbac.application.command.manifest.RegisterApplicationPermissionManifestCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.shadow.*;
import com.opensocket.aievent.core.iam.rbac.application.port.in.EntryPointAuthorityAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.EntryPointAuthorityRepository;
import com.opensocket.aievent.core.iam.rbac.domain.entrypoint.*;
import com.opensocket.aievent.core.iam.rbac.domain.manifest.*;
import com.opensocket.aievent.core.iam.rbac.domain.shadow.*;
import java.math.BigDecimal;
import java.util.*;
/** Phase 5G authority for Entry Point inventory, legacy mapping and time-bounded compatibility bypasses. */
public final class EntryPointAuthorityAdministrationService implements EntryPointAuthorityAdministrationPort {
 private final EntryPointAuthorityRepository repository;
 public EntryPointAuthorityAdministrationService(EntryPointAuthorityRepository repository){this.repository=repository;}
 @Override public List<EntryPointAuthorityRecord> entries(String text,String state,String ownerModule,int limit){return repository.findEntries(clean(text),clean(state),clean(ownerModule),bounded(limit));}
 @Override public EntryPointBurnDownSummary summary(){return repository.summary();}
 @Override public List<LegacyAuthorityMapping> mappings(String status,int limit){return repository.findMappings(clean(status),bounded(limit));}
 @Override public List<EntryPointBypass> bypasses(String status,int limit){return repository.findBypasses(clean(status),bounded(limit));}
 @Override public EntryPointAuthorityRecord updateEntryPoint(UpdateEntryPointAuthorityCommand c){
  EntryPointAuthorityRecord old=repository.findEntry(c.entryPointId()).orElseThrow(()->new IllegalStateException("ENTRY_POINT_NOT_FOUND"));
  validateEntry(c);
  EntryPointAuthorityRecord updated=new EntryPointAuthorityRecord(old.entryPointId(),old.entryPointType(),old.applicationId(),require(c.ownerModule(),"ownerModule"),old.displayName(),old.routePattern(),old.httpMethod(),c.authorityState(),
   normalized(c.targetPermissionCode()),normalized(c.legacyAuthorityType()),List.copyOf(c.legacyAuthorities()==null?List.of():c.legacyAuthorities()),require(c.resourceType(),"resourceType"),require(c.resourceResolverId(),"resourceResolverId"),
   normalized(c.exemptionReason()),c.migrationDeadline(),old.manifestRevision(),old.sourceRef(),old.sourceHash(),c.requestedAt(),old.version()+1,false,false,false,false,old.activeBypass(),old.expiredBypass());
  if(!repository.updateEntryPoint(updated,c.expectedVersion(),c.actorId(),c.correlationId(),c.auditReason()))throw new IllegalStateException("ENTRY_POINT_VERSION_CONFLICT");
  return repository.findEntry(c.entryPointId()).orElse(updated);
 }
 @Override public LegacyAuthorityMapping updateMapping(UpdateLegacyAuthorityMappingCommand c){
  LegacyAuthorityMapping old=repository.findMapping(c.mappingId()).orElseThrow(()->new IllegalStateException("LEGACY_MAPPING_NOT_FOUND"));
  String status=require(c.status(),"status").toUpperCase(Locale.ROOT);if(!Set.of("UNMAPPED","MAPPED","DEPRECATED","RETIRED").contains(status))throw new IllegalArgumentException("LEGACY_MAPPING_STATUS_INVALID");
  Optional<String> permission=normalized(c.targetPermissionCode());if(status.equals("MAPPED")&&permission.isEmpty())throw new IllegalArgumentException("LEGACY_MAPPING_TARGET_REQUIRED");
  LegacyAuthorityMapping updated=new LegacyAuthorityMapping(old.mappingId(),old.legacyAuthorityType(),old.legacyAuthorityCode(),permission,require(c.ownerModule(),"ownerModule"),status,c.migrationDeadline(),clean(c.notes()),c.requestedAt(),old.version()+1);
  if(!repository.updateMapping(updated,c.expectedVersion(),c.actorId(),c.correlationId(),c.auditReason()))throw new IllegalStateException("LEGACY_MAPPING_VERSION_CONFLICT");return repository.findMapping(c.mappingId()).orElse(updated);
 }
 @Override public EntryPointBypass createBypass(CreateEntryPointBypassCommand c){
  repository.findEntry(c.entryPointId()).orElseThrow(()->new IllegalStateException("ENTRY_POINT_NOT_FOUND"));
  if(c.expiresAt()==null||!c.expiresAt().isAfter(c.requestedAt()))throw new IllegalArgumentException("ENTRY_POINT_BYPASS_EXPIRY_INVALID");if(c.expiresAt().isAfter(c.requestedAt().plus(java.time.Duration.ofDays(180))))throw new IllegalArgumentException("ENTRY_POINT_BYPASS_EXPIRY_TOO_LONG");
  EntryPointBypass value=new EntryPointBypass(require(c.bypassId(),"bypassId"),c.entryPointId(),require(c.ownerId(),"ownerId"),min(c.reason(),12,"reason"),min(c.replacement(),3,"replacement"),c.expiresAt(),"ACTIVE",c.requestedAt(),c.actorId(),Optional.empty(),Optional.empty(),Optional.empty(),1);
  repository.insertBypass(value,c.correlationId(),c.auditReason());return value;
 }
 @Override public EntryPointBypass revokeBypass(RevokeEntryPointBypassCommand c){EntryPointBypass old=repository.findBypass(c.bypassId()).orElseThrow(()->new IllegalStateException("ENTRY_POINT_BYPASS_NOT_FOUND"));if(!repository.revokeBypass(c.bypassId(),c.expectedVersion(),c.actorId(),min(c.reason(),12,"reason"),c.requestedAt(),c.correlationId(),c.auditReason()))throw new IllegalStateException("ENTRY_POINT_BYPASS_VERSION_CONFLICT");return repository.findBypass(c.bypassId()).orElse(new EntryPointBypass(old.bypassId(),old.entryPointId(),old.ownerId(),old.reason(),old.replacement(),old.expiresAt(),"REVOKED",old.createdAt(),old.createdBy(),Optional.of(c.requestedAt()),Optional.of(c.actorId()),Optional.of(c.reason()),old.version()+1));}
 @Override public List<ApplicationPermissionManifest> manifests(int limit){return repository.findApplicationManifests(bounded(limit));}
 @Override public ApplicationPermissionManifest manifest(String manifestId){return repository.findApplicationManifest(require(manifestId,"manifestId")).orElseThrow(()->new IllegalStateException("PERMISSION_MANIFEST_NOT_FOUND"));}
 @Override public List<ApplicationPermissionManifestEntry> manifestEntries(String manifestId,String driftStatus,int limit){return repository.findApplicationManifestEntries(require(manifestId,"manifestId"),clean(driftStatus),bounded(limit));}
 @Override public PermissionManifestDriftSummary manifestDrift(String manifestId){return repository.summarizeManifestDrift(require(manifestId,"manifestId"));}
 @Override public List<PermissionCoverageEvidence> coverageEvidence(int limit){return repository.findCoverageEvidence(bounded(limit));}
 @Override public ApplicationPermissionManifest registerManifest(RegisterApplicationPermissionManifestCommand c){validateManifest(c);return repository.registerManifest(c);}
 @Override public List<ShadowObservation> shadowObservations(String tenantId,String domainCode,String category,int limit){return repository.findShadowObservations(require(tenantId,"tenantId"),clean(domainCode).toUpperCase(Locale.ROOT),clean(category).toUpperCase(Locale.ROOT),bounded(limit));}
 @Override public ShadowObservationSummary shadowSummary(String tenantId,String domainCode){return repository.summarizeShadowObservations(require(tenantId,"tenantId"),clean(domainCode).toUpperCase(Locale.ROOT));}
 @Override public List<ShadowMismatchCase> mismatchCases(String tenantId,String domainCode,String status,int limit){return repository.findMismatchCases(require(tenantId,"tenantId"),clean(domainCode).toUpperCase(Locale.ROOT),clean(status).toUpperCase(Locale.ROOT),bounded(limit));}
 @Override public ShadowMismatchCase createMismatchCase(CreateMismatchCaseCommand c){
  ShadowObservation observation=repository.findShadowObservation(require(c.tenantId(),"tenantId"),require(c.comparisonId(),"comparisonId")).orElseThrow(()->new IllegalStateException("SHADOW_OBSERVATION_NOT_FOUND"));
  if("MATCH".equals(observation.category()))throw new IllegalArgumentException("MATCH_OBSERVATION_CASE_FORBIDDEN");
  if(c.slaDueAt()==null||!c.slaDueAt().isAfter(c.requestedAt()))throw new IllegalArgumentException("MISMATCH_CASE_SLA_INVALID");
  ShadowMismatchCase value=new ShadowMismatchCase(require(c.caseId(),"caseId"),observation.tenantId(),observation.comparisonId(),observation.domainCode(),c.entryPointId()==null||c.entryPointId().isEmpty()?observation.entryPointId():c.entryPointId(),observation.permissionCode(),observation.category(),observation.severity(),"OPEN",require(c.ownerId(),"ownerId"),c.slaDueAt(),min(c.title(),8,"title"),Optional.empty(),observation.comparedAt(),observation.comparedAt(),1,c.requestedAt(),c.actorId(),c.requestedAt(),c.actorId(),1,false,0,0);
  repository.insertMismatchCase(value,c.correlationId(),c.auditReason());return repository.findMismatchCase(value.caseId()).orElse(value);
 }
 @Override public ShadowMismatchCase updateMismatchCase(UpdateMismatchCaseCommand c){
  ShadowMismatchCase old=repository.findMismatchCase(require(c.caseId(),"caseId")).orElseThrow(()->new IllegalStateException("MISMATCH_CASE_NOT_FOUND"));
  String status=require(c.status(),"status").toUpperCase(Locale.ROOT);if(!Set.of("OPEN","TRIAGED","IN_REMEDIATION","AWAITING_VALIDATION","WAIVED","RESOLVED","REOPENED").contains(status))throw new IllegalArgumentException("MISMATCH_CASE_STATUS_INVALID");
  validateMismatchTransition(old.status(),status);
  String severity=require(c.severity(),"severity").toUpperCase(Locale.ROOT);if(!Set.of("LOW","MEDIUM","HIGH","CRITICAL").contains(severity))throw new IllegalArgumentException("MISMATCH_CASE_SEVERITY_INVALID");
  if(c.slaDueAt()==null)throw new IllegalArgumentException("MISMATCH_CASE_SLA_REQUIRED");
  if(status.equals("WAIVED")&&!old.activeWaiver())throw new IllegalArgumentException("MISMATCH_CASE_ACTIVE_WAIVER_REQUIRED");
  if(status.equals("RESOLVED")){
   if(c.resolution().filter(x->x.trim().length()>=12).isEmpty())throw new IllegalArgumentException("MISMATCH_CASE_RESOLUTION_REQUIRED");
   if(old.regressionPassed()<1||old.regressionFailed()>0)throw new IllegalArgumentException("MISMATCH_CASE_REGRESSION_PASS_REQUIRED");
  }
  ShadowMismatchCase value=new ShadowMismatchCase(old.caseId(),old.sourceTenantId(),old.comparisonId(),old.domainCode(),old.entryPointId(),old.permissionCode(),old.category(),severity,status,require(c.ownerId(),"ownerId"),c.slaDueAt(),old.title(),c.resolution(),old.firstSeenAt(),c.requestedAt(),old.occurrenceCount(),old.createdAt(),old.createdBy(),c.requestedAt(),c.actorId(),old.version()+1,old.activeWaiver(),old.regressionPassed(),old.regressionFailed());
  if(!repository.updateMismatchCase(value,c.expectedVersion(),c.correlationId(),c.auditReason()))throw new IllegalStateException("MISMATCH_CASE_VERSION_CONFLICT");return repository.findMismatchCase(value.caseId()).orElse(value);
 }
 @Override public ShadowMismatchWaiver createMismatchWaiver(CreateMismatchWaiverCommand c){
  ShadowMismatchCase value=repository.findMismatchCase(require(c.caseId(),"caseId")).orElseThrow(()->new IllegalStateException("MISMATCH_CASE_NOT_FOUND"));
  if(value.severity().equals("CRITICAL")||Set.of("UNEXPECTED_ALLOW","SCOPE_WIDENED","VISIBILITY_WIDENED","TARGET_ERROR").contains(value.category()))throw new IllegalArgumentException("CRITICAL_MISMATCH_WAIVER_FORBIDDEN");
  if(c.expiresAt()==null||!c.expiresAt().isAfter(c.requestedAt())||c.expiresAt().isAfter(c.requestedAt().plus(java.time.Duration.ofDays(30))))throw new IllegalArgumentException("MISMATCH_WAIVER_EXPIRY_INVALID");
  return repository.insertMismatchWaiver(c);
 }
 @Override public ShadowRegressionEvidence addRegressionEvidence(AddRegressionEvidenceCommand c){String result=require(c.result(),"result").toUpperCase(Locale.ROOT);if(!Set.of("PASSED","FAILED").contains(result))throw new IllegalArgumentException("REGRESSION_RESULT_INVALID");repository.findMismatchCase(c.caseId()).orElseThrow(()->new IllegalStateException("MISMATCH_CASE_NOT_FOUND"));return repository.insertRegressionEvidence(new AddRegressionEvidenceCommand(c.evidenceId(),c.caseId(),min(c.testReference(),3,"testReference"),result,c.detailsJson(),c.actorId(),c.correlationId(),c.auditReason(),c.executedAt()));}
 @Override public List<DomainReadinessEvidence> domainReadiness(String tenantId,String domainCode,int limit){return repository.findDomainReadiness(require(tenantId,"tenantId"),clean(domainCode).toUpperCase(Locale.ROOT),bounded(limit));}
 @Override public DomainReadinessEvidence evaluateDomainReadiness(EvaluateDomainReadinessCommand c){require(c.tenantId(),"tenantId");require(c.domainCode(),"domainCode");if(c.windowStartedAt()==null||c.windowEndedAt()==null||!c.windowEndedAt().isAfter(c.windowStartedAt()))throw new IllegalArgumentException("DOMAIN_READINESS_WINDOW_INVALID");return repository.evaluateDomainReadiness(c);}
 @Override public List<Phase6EligibilityEvidence> phase6Eligibility(String tenantId,int limit){return repository.findPhase6Eligibility(require(tenantId,"tenantId"),bounded(limit));}
 @Override public Phase6EligibilityEvidence evaluatePhase6Eligibility(EvaluatePhase6EligibilityCommand c){require(c.tenantId(),"tenantId");return repository.evaluatePhase6Eligibility(c);}
 @Override public ShadowPipelineReadiness shadowPipelineReadiness(){return repository.shadowPipelineReadiness();}
 @Override public ShadowStorageReadiness shadowStorageReadiness(){return repository.shadowStorageReadiness();}
 @Override public List<ShadowRetentionRun> shadowRetentionRuns(int limit){return repository.shadowRetentionRuns(bounded(limit));}
 @Override public String processShadowPipeline(String workerId,int batchSize,String actorId,String correlationId,String auditReason){return repository.processShadowPipeline(require(workerId,"workerId"),Math.min(Math.max(batchSize,1),5000),require(actorId,"actorId"),require(correlationId,"correlationId"),require(auditReason,"auditReason"));}
 @Override public ShadowRetentionRun runShadowRetention(RunShadowRetentionCommand command){return repository.runShadowRetention(command);}
 @Override public List<Phase5RuntimeCertificationEvidence> phase5RuntimeCertificationEvidence(int limit){return repository.phase5RuntimeCertificationEvidence(bounded(limit));}
 @Override public Phase5RuntimeCertificationEvidence generatePhase5RuntimeCertificationEvidence(GeneratePhase5RuntimeCertificationCommand command){return repository.generatePhase5RuntimeCertificationEvidence(command);}
 private static void validateMismatchTransition(String current,String next){
  String from=require(current,"currentStatus").toUpperCase(Locale.ROOT);if(from.equals(next))return;
  Map<String,Set<String>> allowed=Map.of(
   "OPEN",Set.of("TRIAGED","WAIVED","RESOLVED"),
   "TRIAGED",Set.of("IN_REMEDIATION","WAIVED","RESOLVED","REOPENED"),
   "IN_REMEDIATION",Set.of("AWAITING_VALIDATION","WAIVED","REOPENED"),
   "AWAITING_VALIDATION",Set.of("RESOLVED","IN_REMEDIATION","REOPENED","WAIVED"),
   "WAIVED",Set.of("REOPENED","IN_REMEDIATION","RESOLVED"),
   "RESOLVED",Set.of("REOPENED"),
   "REOPENED",Set.of("TRIAGED","IN_REMEDIATION","WAIVED","RESOLVED"));
  if(!allowed.getOrDefault(from,Set.of()).contains(next))throw new IllegalArgumentException("MISMATCH_CASE_TRANSITION_INVALID");
 }
 private void validateManifest(RegisterApplicationPermissionManifestCommand c){
  require(c.manifestId(),"manifestId");require(c.applicationId(),"applicationId");require(c.environment(),"environment");require(c.buildVersion(),"buildVersion");require(c.manifestRevision(),"manifestRevision");require(c.catalogRevisionId(),"catalogRevisionId");require(c.catalogRevisionCode(),"catalogRevisionCode");require(c.sourceInventoryRevision(),"sourceInventoryRevision");
  if(c.schemaVersion()!=1)throw new IllegalArgumentException("PERMISSION_MANIFEST_SCHEMA_UNSUPPORTED");
  if(c.manifestHash()==null||!c.manifestHash().matches("sha256:[0-9a-f]{64}"))throw new IllegalArgumentException("PERMISSION_MANIFEST_HASH_INVALID");
  List<PermissionManifestRegistrationEntry> entries=c.entries()==null?List.of():c.entries();
  if(entries.size()!=c.entryCount()||c.coveredEntryCount()!=c.entryCount()||c.uncoveredCount()!=0||c.coveragePercent().compareTo(new BigDecimal("100.0000"))!=0)throw new IllegalArgumentException("PERMISSION_MANIFEST_COVERAGE_NOT_100");
  Set<String> ids=new HashSet<>();
  for(PermissionManifestRegistrationEntry e:entries){
   if(!ids.add(require(e.entryPointId(),"entryPointId")))throw new IllegalArgumentException("PERMISSION_MANIFEST_DUPLICATE_ENTRY_POINT");
   if(!Set.of("COVERED","EXEMPT","DELEGATED").contains(require(e.coverageStatus(),"coverageStatus")))throw new IllegalArgumentException("PERMISSION_MANIFEST_COVERAGE_STATUS_INVALID");
   if(!Set.of("TARGET_PERMISSION","DUAL_SHADOW","LEGACY_AUTHORITY","EXEMPT","INTERNAL_DELEGATED").contains(require(e.protectionMode(),"protectionMode")))throw new IllegalArgumentException("PERMISSION_MANIFEST_PROTECTION_MODE_INVALID");
   if(e.scopeRequired()&&Set.of("","NONE").contains(require(e.resourceResolverId(),"resourceResolverId")))throw new IllegalArgumentException("PERMISSION_MANIFEST_MISSING_RESOURCE_RESOLVER");
   if(!require(e.sourceHash(),"sourceHash").matches("[0-9a-f]{64}")||!require(e.descriptorHash(),"descriptorHash").matches("[0-9a-f]{64}"))throw new IllegalArgumentException("PERMISSION_MANIFEST_ENTRY_HASH_INVALID");
  }
 }
 private void validateEntry(UpdateEntryPointAuthorityCommand c){List<String> legacy=c.legacyAuthorities()==null?List.of():c.legacyAuthorities();if(Set.of(EntryPointAuthorityState.LEGACY_ONLY,EntryPointAuthorityState.DUAL_SHADOW).contains(c.authorityState())&&legacy.isEmpty())throw new IllegalArgumentException("ENTRY_POINT_LEGACY_AUTHORITY_REQUIRED");if(Set.of(EntryPointAuthorityState.DUAL_SHADOW,EntryPointAuthorityState.TARGET_READY,EntryPointAuthorityState.TARGET_ONLY).contains(c.authorityState())&&normalized(c.targetPermissionCode()).isEmpty())throw new IllegalArgumentException("ENTRY_POINT_TARGET_PERMISSION_REQUIRED");if(c.authorityState()==EntryPointAuthorityState.EXEMPT&&normalized(c.exemptionReason()).filter(x->x.length()>=12).isEmpty())throw new IllegalArgumentException("ENTRY_POINT_EXEMPTION_REASON_REQUIRED");}
 private static int bounded(int n){if(n<1||n>500)throw new IllegalArgumentException("ENTRY_POINT_LIMIT_INVALID");return n;}
 private static Optional<String> normalized(Optional<String> value){return value==null?Optional.empty():value.map(String::trim).filter(x->!x.isEmpty());}
 private static String clean(String value){return value==null?"":value.trim();}
 private static String require(String value,String field){String x=clean(value);if(x.isEmpty())throw new IllegalArgumentException(field+" is required");return x;}
 private static String min(String value,int n,String field){String x=require(value,field);if(x.length()<n)throw new IllegalArgumentException(field+" is too short");return x;}
}
