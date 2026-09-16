import { coreTenantApiGet, coreTenantApiPost } from '@/lib/api/coreClient';

export type SecurityIncidentSeverity='LOW'|'MEDIUM'|'HIGH'|'CRITICAL';
export type SecurityIncidentStatus='OPEN'|'CONTAINED'|'RESOLVED';
export type SecurityIncidentTargetType='CREDENTIAL'|'SERVICE_ACCOUNT'|'AGENT'|'TASK'|'SOURCE_SYSTEM';
export type SecurityIncidentControlAction='THROTTLE'|'SUSPEND'|'REVOKE'|'HOLD'|'BLOCK_RETRY'|'CANCEL'|'REASSIGN'|'FORCE_FAIL'|'QUARANTINE';
export interface SecurityIncidentCase {tenantId:string;caseId:string;title:string;severity:SecurityIncidentSeverity;status:SecurityIncidentStatus;summary?:string|null;sourceIncidentId?:string|null;rootTaskId?:string|null;sourceSystemId?:string|null;ownerDepartmentId?:string|null;ownerGroupId?:string|null;openedBy:string;openedAt:string;containedAt?:string|null;resolvedAt?:string|null;resolutionReason?:string|null;lastActionAt?:string|null;version:number;}
export interface SecurityIncidentControl {tenantId:string;controlId:string;caseId:string;targetType:SecurityIncidentTargetType;targetId:string;parentTargetId?:string|null;controlType:string;status:'ACTIVE'|'RELEASED'|'EXPIRED';limitPerMinute?:number|null;reason:string;requestedBy:string;startedAt:string;expiresAt?:string|null;releasedAt?:string|null;releasedBy?:string|null;releaseReason?:string|null;version:number;}
export interface SecurityIncidentAction {tenantId:string;actionId:string;caseId:string;controlId?:string|null;targetType:SecurityIncidentTargetType;targetId:string;actionType:string;actorId:string;reason:string;beforeState:string;afterState:string;authorizationDecisionId?:string|null;correlationId?:string|null;occurredAt:string;}
export interface SecurityIncidentDetails {incident:SecurityIncidentCase;controls:SecurityIncidentControl[];actions:SecurityIncidentAction[];}
export interface CreateSecurityIncidentRequest {title:string;severity:SecurityIncidentSeverity;summary?:string;sourceIncidentId?:string;rootTaskId?:string;sourceSystemId?:string;}
export interface ApplySecurityControlRequest {targetType:SecurityIncidentTargetType;targetId:string;parentTargetId?:string;action:SecurityIncidentControlAction;limitPerMinute?:number;expiresAt?:string;reason:string;}

export const securityIncidentApi={
  list(status?:string,severity?:string,limit=100){const q=new URLSearchParams();if(status)q.set('status',status);if(severity)q.set('severity',severity);q.set('limit',String(limit));return coreTenantApiGet<SecurityIncidentCase[]>(`/api/security/incidents?${q.toString()}`);},
  detail(caseId:string){return coreTenantApiGet<SecurityIncidentDetails>(`/api/security/incidents/${encodeURIComponent(caseId)}`);},
  create(body:CreateSecurityIncidentRequest){return coreTenantApiPost<SecurityIncidentCase>('/api/security/incidents',body);},
  apply(caseId:string,body:ApplySecurityControlRequest){return coreTenantApiPost<SecurityIncidentDetails>(`/api/security/incidents/${encodeURIComponent(caseId)}/controls`,body);},
  release(caseId:string,controlId:string,reason:string){return coreTenantApiPost<SecurityIncidentDetails>(`/api/security/incidents/${encodeURIComponent(caseId)}/controls/${encodeURIComponent(controlId)}/release`,{reason});},
  resolve(caseId:string,reason:string){return coreTenantApiPost<SecurityIncidentCase>(`/api/security/incidents/${encodeURIComponent(caseId)}/resolve`,{reason});},
};
