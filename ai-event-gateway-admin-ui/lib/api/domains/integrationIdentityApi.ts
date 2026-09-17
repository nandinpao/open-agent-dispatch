import { coreTenantApiGet, coreTenantApiPost, coreTenantApiPut } from '@/lib/api/coreClient';
import { createIdempotencyKey } from '@/lib/utils/uuid';

export type ProviderType = 'JIRA' | 'REDMINE' | 'GITLAB_ISSUES';
export type IntegrationOperation = 'READ' | 'CREATE' | 'COMMENT' | 'UPDATE' | 'RELATION' | 'WEBHOOK';
export interface IntegrationConnection { tenantId?: string; connectionId: string; providerType: ProviderType; connectionName: string; baseUrl: string; deploymentType?: string; providerVersion?: string | null; status?: string; timeoutMs?: number; retryPolicyId?: string | null; rateLimitPolicyId?: string | null; tlsPolicyId?: string | null; enabled?: boolean; version?: number; createdAt?: string; updatedAt?: string }
export interface IntegrationPrincipal { tenantId?: string; principalId: string; connectionId: string; principalName: string; principalType: string; ownerDepartmentId?: string | null; ownerGroupId?: string | null; trustZoneId?: string | null; externalPrincipalIdentifier?: string | null; status?: string; riskLevel?: string; permissionSummary?: Record<string,string>; overPrivilegedReasons?: string[]; lastPermissionProbeAt?: string | null; version?: number }
export interface IntegrationPrincipalScope { tenantId?: string; principalId: string; isolationMode: string; scopeReference?: string | null; allowedProjectIds: string[]; allowedOperations: IntegrationOperation[]; allowedIssueTypes: string[]; productionAllowed: boolean; version?: number }
export interface IntegrationCredentialMetadata { tenantId?: string; credentialId: string; principalId: string; authType: string; secretRef?: string; secretVersion?: string | null; secretLast4?: string | null; validFrom?: string | null; expiresAt?: string | null; rotatedAt?: string | null; lastUsedAt?: string | null; status?: string; version?: number }
export interface PermissionProbeResult { probeId: string; overallStatus: string; capabilityResults: Record<string,string>; elevatedPermissions: string[]; providerResponseSummary: string; completedAt?: string }

function key(prefix:string){return createIdempotencyKey(prefix);}
export const listConnections=()=>coreTenantApiGet<IntegrationConnection[]>('/api/integrations/connections',{limit:500});
export const saveConnection=(value:IntegrationConnection)=>coreTenantApiPut<IntegrationConnection>(`/api/integrations/connections/${encodeURIComponent(value.connectionId)}`,value,{headers:value.version?{'If-Match':String(value.version)}:undefined});
export const listPrincipals=(connectionId:string)=>coreTenantApiGet<IntegrationPrincipal[]>(`/api/integrations/connections/${encodeURIComponent(connectionId)}/principals`,{limit:500});
export const discoverProviderMetadata=(connectionId:string,principalId?:string)=>coreTenantApiPost<ProviderMetadataSnapshot>(`/api/integrations/connections/${encodeURIComponent(connectionId)}/metadata-discovery`,undefined,{query:principalId?{principalId}:undefined});
export const savePrincipal=(connectionId:string,value:IntegrationPrincipal)=>coreTenantApiPut<IntegrationPrincipal>(`/api/integrations/connections/${encodeURIComponent(connectionId)}/principals/${encodeURIComponent(value.principalId)}`,value,{headers:value.version?{'If-Match':String(value.version)}:undefined});
export const getPrincipalScope=(principalId:string)=>coreTenantApiGet<IntegrationPrincipalScope>(`/api/integrations/principals/${encodeURIComponent(principalId)}/scope`);
export const listCredentials=(principalId:string)=>coreTenantApiGet<IntegrationCredentialMetadata[]>(`/api/integrations/principals/${encodeURIComponent(principalId)}/credentials`,{limit:500});
export const addCredential=(principalId:string,value:IntegrationCredentialMetadata)=>coreTenantApiPost<IntegrationCredentialMetadata>(`/api/integrations/principals/${encodeURIComponent(principalId)}/credentials/${encodeURIComponent(value.credentialId)}`,value,{headers:{'Idempotency-Key':key('credential-reference')}});
export const runPermissionProbe=(principalId:string,mappingId?:string)=>coreTenantApiPost<PermissionProbeResult>(`/api/integrations/principals/${encodeURIComponent(principalId)}/permission-probe`,undefined,{query:mappingId?{mappingId}:undefined});
export interface RedmineCredentialTestResult { credential: IntegrationCredentialMetadata; probe: PermissionProbeResult; authenticated: boolean; storageMode: string }
export const saveRedmineApiKeyAndTest=(principalId:string,apiKey:string)=>coreTenantApiPost<RedmineCredentialTestResult>(`/api/integrations/principals/${encodeURIComponent(principalId)}/redmine-api-key`,{apiKey},{headers:{'Idempotency-Key':key('redmine-api-key')}});
export interface IssueTrackingActivationResult { mapping: IntegrationProjectMapping; metadata?: ProviderMetadataSnapshot | null; validation?: ProjectMappingValidationResult | null; probe?: PermissionProbeResult | null; alreadyActive: boolean; retiredLegacyMappings: number; message: string }
export const activateSourceIssueTracking=(sourceSystemId:string,value:{connectionId:string;principalId:string;projectId:string;projectKey?:string|null;trackerId:string})=>coreTenantApiPost<IssueTrackingActivationResult>(`/api/integrations/source-systems/${encodeURIComponent(sourceSystemId)}/issue-tracking/activate`,value,{headers:{'Idempotency-Key':key('source-issue-tracking-activate')}});


export interface IssueTrackingReadinessCheck {
  code: string;
  label: string;
  status: 'READY' | 'BLOCKED' | 'WAITING' | 'CHECK_REQUIRED' | 'CERTIFIED' | 'NOT_CERTIFIED';
  reasonCode?: string | null;
  summary?: string | null;
  remediationRoute?: string | null;
}
export interface IssueTrackingRuntimeReadiness {
  sourceSystemId: string;
  taskType?: string | null;
  overallStatus: 'BLOCKED' | 'CHECK_REQUIRED' | 'READY_NOT_CERTIFIED' | 'CERTIFIED';
  configured: boolean;
  runtimeReady: boolean;
  providerAuthenticated: boolean;
  liveCreateCertified: boolean;
  liveCreateCertificationStatus: string;
  executionAuthority: string;
  autoExecutePending: boolean;
  connectorRuntimeEnabled: boolean;
  connectionId?: string | null;
  mappingId?: string | null;
  externalProjectId?: string | null;
  externalProjectKey?: string | null;
  externalTrackerId?: string | null;
  technicalPrincipalId?: string | null;
  credentialId?: string | null;
  blockers: string[];
  checks: IssueTrackingReadinessCheck[];
  evaluatedAt?: string | null;
}
export const getSourceIssueTrackingReadiness=(sourceSystemId:string,taskType?:string|null)=>coreTenantApiGet<IssueTrackingRuntimeReadiness>(`/api/integrations/source-systems/${encodeURIComponent(sourceSystemId)}/issue-tracking/readiness`,{taskType:taskType||undefined});
export const probeSourceIssueTrackingReadiness=(sourceSystemId:string,taskType?:string|null)=>coreTenantApiPost<IssueTrackingRuntimeReadiness>(`/api/integrations/source-systems/${encodeURIComponent(sourceSystemId)}/issue-tracking/readiness/probe`,undefined,{query:{taskType:taskType||undefined}});

export interface ConnectorRuntimePreflightResult {
  status: 'READY';
  providerType: ProviderType;
  connectionId: string;
  mappingId: string;
  mappingVersion?: number;
  mappingStatus?: string;
  mappingLifecycle?: string;
  sourceSystemId?: string | null;
  externalProjectId?: string | null;
  externalProjectKey?: string | null;
  externalTrackerId?: string | null;
  technicalPrincipalId?: string;
  credentialId?: string;
  credentialVersion?: string | null;
  credentialSecretScheme?: string | null;
  credentialSecretResolverMode?: string | null;
  credentialMaterialResolvable?: boolean;
  credentialMaterialErrorCode?: string | null;
  authenticationStatus?: string | null;
  authenticationSummary?: string | null;
}
export const runConnectorRuntimePreflight=(value:{connectionId?:string|null;departmentId?:string|null;groupId?:string|null;serviceDomainId?:string|null;sourceSystemId?:string|null;taskType?:string|null})=>coreTenantApiPost<ConnectorRuntimePreflightResult>('/api/integrations/project-mappings/connector-runtime-preflight',value);

export type ProjectMappingLifecycle = 'DRAFT' | 'VALIDATING' | 'VALID' | 'ACTIVE' | 'DEPRECATED' | 'DISABLED';
export interface IntegrationProjectMapping {
  tenantId?: string; mappingId: string; connectionId: string; departmentId?: string|null; groupId?: string|null;
  serviceDomainId?: string|null; sourceSystemId?: string|null; taskType?: string|null; externalProjectId: string;
  externalProjectKey?: string|null; externalIssueType?: string|null; externalTrackerId?: string|null;
  readPrincipalId?: string|null; createPrincipalId?: string|null; commentPrincipalId?: string|null;
  updatePrincipalId?: string|null; relationPrincipalId?: string|null; webhookPrincipalId?: string|null;
  permissionProfileId?: string|null; contextPolicyId?: string|null; resultSharingPolicyId?: string|null;
  mappingStatus?: string; resolutionPriority?: number; defaultMapping?: boolean; enabled?: boolean;
  lifecycleStatus?: ProjectMappingLifecycle; mappingVersion?: number; summaryTemplate?: string; descriptionTemplate?: string;
  requiredFields?: string[]; customFieldMappings?: Record<string,string>; transitionMappings?: Record<string,string>;
  commentPolicy?: string; linkPolicy?: string; metadataSnapshotId?: string|null; metadataSchemaHash?: string|null;
  validatedAt?: string|null; publishedAt?: string|null; supersedesMappingVersion?: number|null; version?: number;
}
export interface ProviderMetadataSnapshot { snapshotId:string; mappingId:string; providerProjectId?:string|null; providerProjectKey?:string|null; projects:Array<{projectId:string;projectKey?:string|null;displayName?:string|null;accessible:boolean}>; issueTypes:Array<{issueTypeId:string;issueTypeKey?:string|null;displayName?:string|null;requiredFieldIds:string[]}>; fields:Array<{fieldId:string;fieldKey?:string|null;displayName?:string|null;fieldType:string;required:boolean;allowedValues:string[]}>; transitions:Array<{transitionId:string;transitionKey?:string|null;displayName?:string|null;fromStatus?:string|null;toStatus?:string|null}>; linkTypes:Array<{linkTypeId:string;linkTypeKey?:string|null;outwardDescription?:string|null;inwardDescription?:string|null}>; permissions:Record<string,string>; schemaHash:string; metadataVersion:number; cacheStatus:string; probedAt:string; expiresAt:string; providerSummary?:string|null }
export interface IntegrationProjectMappingVersion { mappingId:string; mappingVersion:number; lifecycle:ProjectMappingLifecycle; configurationHash:string; metadataSnapshotId?:string|null; metadataSchemaHash?:string|null; createdBy:string; createdAt:string }
export interface ProjectMappingValidationResult { mappingId:string; mappingVersion:number; valid:boolean; errors:string[]; warnings:string[]; metadataSnapshotId?:string|null; metadataSchemaHash?:string|null }
export interface ProjectMappingPreview { mappingId:string; mappingVersion:number; providerProject:string; issueType:string; renderedSummary:string; renderedDescription:string; mappedFields:Record<string,unknown>; missingRequiredFields:string[]; metadataSnapshotId?:string|null; metadataSchemaHash?:string|null }
export interface ProjectMappingDiff { mappingId:string; fromVersion:number; toVersion:number; changes:Record<string,string> }

export const listProjectMappings=(connectionId?:string)=>coreTenantApiGet<IntegrationProjectMapping[]>('/api/integrations/project-mappings',{connectionId,limit:500});
export const saveProjectMapping=(value:IntegrationProjectMapping)=>coreTenantApiPut<IntegrationProjectMapping>(`/api/integrations/project-mappings/${encodeURIComponent(value.mappingId)}`,value,{headers:value.version?{'If-Match':String(value.version)}:undefined});
export const probeProviderMetadata=(mappingId:string,principalId?:string,force=false)=>coreTenantApiPost<ProviderMetadataSnapshot>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/metadata-probe`,undefined,{query:{principalId,force}});
export const listProviderMetadataSnapshots=(mappingId:string)=>coreTenantApiGet<ProviderMetadataSnapshot[]>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/metadata-snapshots`,{limit:100});
export const validateProjectMapping=(mappingId:string)=>coreTenantApiPost<ProjectMappingValidationResult>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/validate`,{actorId:'operator',reason:'Validate mapping against provider metadata.'},{headers:{'Idempotency-Key':key('mapping-validate')}});
export const publishProjectMapping=(mappingId:string)=>coreTenantApiPost<IntegrationProjectMapping>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/publish`,{actorId:'operator',reason:'Publish validated Project Mapping.'},{headers:{'Idempotency-Key':key('mapping-publish')}});
export const deprecateProjectMapping=(mappingId:string)=>coreTenantApiPost<IntegrationProjectMapping>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/deprecate`,{actorId:'operator',reason:'Deprecate published Project Mapping.'},{headers:{'Idempotency-Key':key('mapping-deprecate')}});
export const forkProjectMapping=(mappingId:string,newMappingId:string)=>coreTenantApiPost<IntegrationProjectMapping>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/fork`,{newMappingId,actorId:'operator',reason:'Create a new immutable Mapping version draft.'},{headers:{'Idempotency-Key':key('mapping-fork')}});
export const rollbackProjectMapping=(mappingId:string,targetVersion:number,newMappingId:string)=>coreTenantApiPost<IntegrationProjectMapping>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/rollback`,{targetVersion,newMappingId,actorId:'operator',reason:'Create rollback draft from immutable Mapping version.'},{headers:{'Idempotency-Key':key('mapping-rollback')}});
export const listProjectMappingVersions=(mappingId:string)=>coreTenantApiGet<IntegrationProjectMappingVersion[]>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/versions`,{limit:100});
export const previewProjectMapping=(mappingId:string,context:Record<string,unknown>)=>coreTenantApiPost<ProjectMappingPreview>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/preview`,{context});
export const diffProjectMapping=(mappingId:string,fromVersion:number,toVersion:number)=>coreTenantApiGet<ProjectMappingDiff>(`/api/integrations/project-mappings/${encodeURIComponent(mappingId)}/diff`,{fromVersion,toVersion});
