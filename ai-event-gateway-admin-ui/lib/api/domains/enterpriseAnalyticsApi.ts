import { coreApiGet } from '@/lib/api/coreClient';

export interface AnalyticsFilters { from?: string; to?: string; departmentId?: string; groupId?: string; }
export interface WorkloadSummary { totalTasks:number; failedTasks:number; criticalTasks:number; humanOriginTasks:number; serviceAccountOriginTasks:number; agentOriginTasks:number; systemOriginTasks:number; distinctAgents:number; distinctCredentials:number; averageDurationMs?:number|null; }
export interface BreakdownRow { key:string; taskCount:number; failureCount:number; criticalCount:number; }
export interface OrganizationRow { departmentId:string; departmentName:string; taskCount:number; failureCount:number; distinctAgents:number; }
export interface TrendRow { bucketStart:string; taskCount:number; failureCount:number; criticalCount:number; averageDurationMs?:number|null; }
export interface RankedWorkloadRow { key:string; label:string; taskCount:number; failureCount:number; criticalCount:number; failureRatePercent:number; }
export interface SecuritySummary { incidentCount:number; openIncidentCount:number; criticalIncidentCount:number; controlActionCount:number; averageContainmentMs?:number|null; averageResolutionMs?:number|null; }
export interface SecurityOrganizationRow { departmentId:string; departmentName:string; incidentCount:number; openIncidentCount:number; criticalIncidentCount:number; averageContainmentMs?:number|null; averageResolutionMs?:number|null; }
export interface ProjectionStatus { projectionName:string; lastEventId?:string|null; lastEventAt?:string|null; lastProjectedAt?:string|null; projectedEventCount:number; failureCount:number; rebuildRequired:boolean; pendingEvents:number; failedEvents:number; lagSeconds?:number|null; updatedAt?:string|null; }
export interface DashboardPerformanceStatus { dirtyRollupBuckets:number; oldestDirtyBucket?:string|null; lastRollupRefresh?:string|null; rollupRows:number; }
export interface RecentWorkloadRow { taskId:string; occurredAt:string; status:string; originPrincipalType?:string|null; originPrincipalId?:string|null; departmentId?:string|null; groupId?:string|null; assignedAgentId?:string|null; credentialId?:string|null; sourceSystem?:string|null; failureDomain?:string|null; failureCode?:string|null; initialPriority?:string|null; initialSeverity?:string|null; }
export interface RecentWorkloadPage { items:RecentWorkloadRow[]; nextCursor:string; hasMore:boolean; }

export interface AnalyticsScopeOption { scopeType:'DEPARTMENT'|'GROUP'|string; id:string; label:string; }
export interface AnalyticsAccessProfile {
  tenantWide:boolean;
  denied:boolean;
  requiresScopeSelection:boolean;
  defaultScopeType:string;
  defaultScopeId:string;
  departments:AnalyticsScopeOption[];
  groups:AnalyticsScopeOption[];
  planHash:string;
}
export interface AnalyticsOrganizationPage { items:OrganizationRow[]; page:number; size:number; hasMore:boolean; }
export type AnalyticsSectionStatus='READY'|'OMITTED'|'UNAVAILABLE';
export interface AnalyticsSection<T> { status:AnalyticsSectionStatus; revision:number; errorCode:string; data?:T|null; }
export interface EnterpriseAnalyticsOverviewView {
  access:AnalyticsAccessProfile;
  departmentId:string;
  groupId:string;
  sections:Record<string,AnalyticsSection<unknown>>;
}

const query=(filters:AnalyticsFilters, extra:Record<string,string|number|undefined>={})=>({from:filters.from,to:filters.to,departmentId:filters.departmentId,groupId:filters.groupId,...extra});

export const enterpriseAnalyticsApi={
  overview:(filters:AnalyticsFilters,bucket:'HOUR'|'DAY',organizationText='',organizationPage=0,organizationSize=25)=>coreApiGet<EnterpriseAnalyticsOverviewView>('/api/admin/analytics/overview-view',query(filters,{bucket,organizationText,organizationPage,organizationSize})),
  scopeProfile:()=>coreApiGet<AnalyticsAccessProfile>('/api/admin/analytics/scope-profile'),
  organization:(filters:AnalyticsFilters,kind:'DEPARTMENT'|'GROUP',text='',page=0,size=25)=>coreApiGet<AnalyticsOrganizationPage>('/api/admin/analytics/organization',{from:filters.from,to:filters.to,departmentId:filters.departmentId,kind,text,page,size}),
  summary:(filters:AnalyticsFilters)=>coreApiGet<WorkloadSummary>('/api/admin/analytics/workloads/summary',query(filters)),
  origin:(filters:AnalyticsFilters)=>coreApiGet<BreakdownRow[]>('/api/admin/analytics/workloads/by-origin',query(filters)),
  failureDomain:(filters:AnalyticsFilters)=>coreApiGet<BreakdownRow[]>('/api/admin/analytics/workloads/by-failure-domain',query(filters)),
  departments:(filters:AnalyticsFilters)=>coreApiGet<OrganizationRow[]>('/api/admin/analytics/workloads/by-department',{from:filters.from,to:filters.to,limit:200}),
  groups:(filters:AnalyticsFilters)=>coreApiGet<OrganizationRow[]>('/api/admin/analytics/workloads/by-group',{from:filters.from,to:filters.to,departmentId:filters.departmentId,limit:200}),
  trend:(filters:AnalyticsFilters,bucket:'HOUR'|'DAY')=>coreApiGet<TrendRow[]>('/api/admin/analytics/workloads/trend',query(filters,{bucket})),
  agents:(filters:AnalyticsFilters)=>coreApiGet<RankedWorkloadRow[]>('/api/admin/analytics/workloads/by-agent',query(filters,{limit:25})),
  credentials:(filters:AnalyticsFilters)=>coreApiGet<RankedWorkloadRow[]>('/api/admin/analytics/workloads/by-credential',query(filters,{limit:25})),
  sourceSystems:(filters:AnalyticsFilters)=>coreApiGet<RankedWorkloadRow[]>('/api/admin/analytics/workloads/by-source-system',query(filters,{limit:25})),
  security:(filters:AnalyticsFilters)=>coreApiGet<SecuritySummary>('/api/admin/analytics/security/summary',{from:filters.from,to:filters.to,departmentId:filters.departmentId}),
  securityDepartments:(filters:AnalyticsFilters)=>coreApiGet<SecurityOrganizationRow[]>('/api/admin/analytics/security/by-department',{from:filters.from,to:filters.to,limit:100}),
  recent:(filters:AnalyticsFilters,cursor?:string)=>coreApiGet<RecentWorkloadPage>('/api/admin/analytics/workloads/recent',query(filters,{limit:50,cursor})),
  projection:()=>coreApiGet<ProjectionStatus>('/api/admin/analytics/projection/status'),
  performance:()=>coreApiGet<DashboardPerformanceStatus>('/api/admin/analytics/performance/status'),
};

export function analyticsSection<T>(view:EnterpriseAnalyticsOverviewView|undefined,key:string):AnalyticsSection<T>|undefined {
  return view?.sections?.[key] as AnalyticsSection<T>|undefined;
}
