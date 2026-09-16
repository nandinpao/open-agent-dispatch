'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { SelectField, type SelectOption } from '@/components/access-management/shared/beginnerUi';
import {
  analyticsSection,
  enterpriseAnalyticsApi,
  type AnalyticsFilters,
  type AnalyticsOrganizationPage,
  type AnalyticsScopeOption,
  type BreakdownRow,
  type DashboardPerformanceStatus,
  type EnterpriseAnalyticsOverviewView,
  type OrganizationRow,
  type ProjectionStatus,
  type RankedWorkloadRow,
  type RecentWorkloadPage,
  type SecurityOrganizationRow,
  type SecuritySummary,
  type TrendRow,
  type WorkloadSummary,
} from '@/lib/api/domains/enterpriseAnalyticsApi';

const RANGE_OPTIONS:SelectOption[]=[{value:'1',label:'Last 24 hours'},{value:'7',label:'Last 7 days'},{value:'30',label:'Last 30 days'},{value:'90',label:'Last 90 days'}];
function fromRange(days:number){const to=new Date();const from=new Date(to.getTime()-days*86400000);return {from:from.toISOString(),to:to.toISOString()};}
function n(value:number){return new Intl.NumberFormat().format(value);}
function duration(value?:number|null){if(value==null)return '—';if(value<1000)return `${Math.round(value)} ms`;if(value<60000)return `${(value/1000).toFixed(1)} s`;return `${(value/60000).toFixed(1)} min`;}
function failureRate(total:number,failed:number){return total?`${(failed*100/total).toFixed(2)}%`:'0.00%';}
function Metric({label,value,detail}:{label:string;value:string;detail?:string}){return <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-2 text-2xl font-black text-slate-950">{value}</div>{detail?<div className="mt-1 text-xs font-semibold text-slate-500">{detail}</div>:null}</div>}
function MiniBars({rows}:{rows:TrendRow[]}){const max=Math.max(1,...rows.map(r=>r.taskCount));return <><div className="flex h-36 items-end gap-1 overflow-hidden rounded-xl border border-slate-100 bg-slate-50 p-3" aria-label="Workload trend chart">{rows.slice(-45).map(row=><div key={row.bucketStart} className="group relative h-full min-w-1 flex-1" title={`${new Date(row.bucketStart).toLocaleString()} · ${n(row.taskCount)} tasks · ${n(row.failureCount)} failed`}><div className="absolute bottom-0 left-0 w-full rounded-t bg-blue-600" style={{height:`${Math.max(2,row.taskCount/max*100)}%`}}/><div className="absolute bottom-0 left-0 w-full rounded-t bg-rose-500/80" style={{height:`${Math.max(0,row.failureCount/max*100)}%`}}/></div>)}</div><details className="mt-2 text-xs text-slate-600"><summary className="cursor-pointer font-black">View trend as data</summary><div className="mt-2 max-h-52 overflow-auto"><table className="w-full text-left"><thead><tr><th className="py-1">Time</th><th>Tasks</th><th>Failed</th></tr></thead><tbody>{rows.slice(-45).map(row=><tr key={row.bucketStart}><td className="py-1 pr-3">{new Date(row.bucketStart).toLocaleString()}</td><td>{n(row.taskCount)}</td><td>{n(row.failureCount)}</td></tr>)}</tbody></table></div></details></>}
function Ranking({title,rows,empty='No matching workload.'}:{title:string;rows:RankedWorkloadRow[];empty?:string}){return <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><h3 className="font-black text-slate-950">{title}</h3><div className="mt-3 divide-y divide-slate-100">{rows.length?rows.slice(0,8).map(row=><div key={row.key} className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 py-2.5"><div className="min-w-0"><div className="truncate text-sm font-black text-slate-900">{row.label||row.key}</div><div className="mt-1 text-xs text-slate-500">{n(row.failureCount)} failures · {row.failureRatePercent.toFixed(2)}% failure rate</div></div><div className="text-right"><div className="text-sm font-black text-slate-900">{n(row.taskCount)}</div><div className="text-xs font-bold text-slate-400">tasks</div></div></div>):<p className="py-6 text-sm text-slate-500">{empty}</p>}</div></section>}
function Breakdown({title,rows}:{title:string;rows:BreakdownRow[]}){return <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><h3 className="font-black text-slate-950">{title}</h3><div className="mt-3 space-y-2">{rows.map(row=><div key={row.key} className="rounded-xl bg-slate-50 px-3 py-2"><div className="flex items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">{row.key}</span><span className="text-sm font-black text-slate-950">{n(row.taskCount)}</span></div><div className="mt-1 text-xs text-slate-500">{n(row.failureCount)} failed · {n(row.criticalCount)} critical</div></div>)}</div></section>}
function PanelUnavailable({title,code}:{title:string;code?:string}){return <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4"><h3 className="font-black text-amber-950">{title}</h3><p className="mt-1 text-sm text-amber-900">This analytics section is temporarily unavailable or outside your current scope.{code?` (${code})`:''}</p></section>}

export function EnterpriseAnalyticsDashboard(){
  const [range,setRange]=useState('7');
  const [departmentId,setDepartmentId]=useState('');
  const [departmentLabel,setDepartmentLabel]=useState('');
  const [groupId,setGroupId]=useState('');
  const [groupLabel,setGroupLabel]=useState('');
  const [overview,setOverview]=useState<EnterpriseAnalyticsOverviewView|null>(null);
  const [organization,setOrganization]=useState<AnalyticsOrganizationPage|null>(null);
  const [organizationSearch,setOrganizationSearch]=useState('');
  const [organizationPage,setOrganizationPage]=useState(0);
  const [recent,setRecent]=useState<RecentWorkloadPage|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState<string|null>(null);
  const [loadingMore,setLoadingMore]=useState(false);

  const filters=useMemo<AnalyticsFilters>(()=>({...fromRange(Number(range)),departmentId:departmentId||undefined,groupId:groupId||undefined}),[range,departmentId,groupId]);
  const bucket = Number(range);
  const load=useCallback(async()=>{
    setLoading(true);setError(null);
    try{
      const value=await enterpriseAnalyticsApi.overview(filters,bucket<=1?'HOUR':'DAY');
      setOverview(value);
      const recentSection=analyticsSection<RecentWorkloadPage>(value,'recent');
      setRecent(recentSection?.status==='READY'?(recentSection.data??null):null);
      if(!departmentId&&!groupId&&!value.access.tenantWide&&value.access.defaultScopeId){
        if(value.access.defaultScopeType==='DEPARTMENT'){
          const option=value.access.departments.find(item=>item.id===value.access.defaultScopeId);
          setDepartmentId(value.access.defaultScopeId);setDepartmentLabel(option?.label||value.access.defaultScopeId);
        } else if(value.access.defaultScopeType==='GROUP'){
          const option=value.access.groups.find(item=>item.id===value.access.defaultScopeId);
          setGroupId(value.access.defaultScopeId);setGroupLabel(option?.label||value.access.defaultScopeId);
        }
      }
    }catch(e){setError(e instanceof Error?e.message:'Enterprise analytics is unavailable for your current Responsibility.');}
    finally{setLoading(false);}
  },[filters,bucket,departmentId,groupId]);
  useEffect(()=>{void load();},[load]);

  useEffect(()=>{
    if(!overview||overview.access.requiresScopeSelection&& !departmentId&&!groupId){setOrganization(null);return;}
    let cancelled=false;
    const timer=window.setTimeout(()=>{
      void enterpriseAnalyticsApi.organization(filters,departmentId?'GROUP':'DEPARTMENT',organizationSearch,organizationPage,25)
        .then(page=>{if(!cancelled)setOrganization(page);})
        .catch(()=>{if(!cancelled)setOrganization(null);});
    },200);
    return()=>{cancelled=true;window.clearTimeout(timer);};
  },[overview,filters,departmentId,groupId,organizationSearch,organizationPage]);

  async function more(){if(!recent?.nextCursor)return;setLoadingMore(true);try{const next=await enterpriseAnalyticsApi.recent(filters,recent.nextCursor);setRecent({items:[...recent.items,...next.items],nextCursor:next.nextCursor,hasMore:next.hasMore});}finally{setLoadingMore(false);}}
  function chooseScope(option:AnalyticsScopeOption){if(option.scopeType==='GROUP'){setDepartmentId('');setDepartmentLabel('');setGroupId(option.id);setGroupLabel(option.label);}else{setDepartmentId(option.id);setDepartmentLabel(option.label);setGroupId('');setGroupLabel('');}setOrganizationSearch('');setOrganizationPage(0);}
  function chooseOrganization(row:OrganizationRow){if(row.departmentId==='UNASSIGNED')return;if(departmentId){setGroupId(row.departmentId);setGroupLabel(row.departmentName);}else{setDepartmentId(row.departmentId);setDepartmentLabel(row.departmentName);setGroupId('');setGroupLabel('');}setOrganizationSearch('');setOrganizationPage(0);}
  function clearDepartment(){setDepartmentId('');setDepartmentLabel('');setGroupId('');setGroupLabel('');setOrganizationPage(0);setOrganizationSearch('');}

  if(loading&&!overview)return <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm font-semibold text-slate-600">Loading enterprise analytics projection…</div>;
  if(error&&!overview)return <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5"><h3 className="font-black text-amber-950">Enterprise analytics is not available in this scope</h3><p className="mt-2 text-sm leading-6 text-amber-900">{error} Your daily Dashboard remains available; analytics never broadens your current Resource Access.</p></div>;
  if(!overview)return null;

  if(overview.access.requiresScopeSelection&&!departmentId&&!groupId){const options=[...overview.access.departments,...overview.access.groups];return <div className="space-y-5"><section className="rounded-3xl border border-indigo-200 bg-indigo-50 p-5"><div className="text-xs font-black uppercase tracking-wide text-indigo-700">Authorized analytics scope</div><h2 className="mt-1 text-xl font-black text-slate-950">Choose the organization scope you want to review</h2><p className="mt-2 text-sm text-slate-600">Your Responsibility grants multiple Department or Group scopes but not Tenant-wide analytics. OpenDispatch will not attempt a Tenant-wide query first.</p><div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-3">{options.map(option=><button key={`${option.scopeType}:${option.id}`} type="button" onClick={()=>chooseScope(option)} className="rounded-xl border border-indigo-200 bg-white p-3 text-left hover:border-indigo-500"><span className="block text-xs font-black text-indigo-700">{option.scopeType}</span><span className="mt-1 block font-black text-slate-900">{option.label}</span></button>)}</div></section></div>}

  const summary=analyticsSection<WorkloadSummary>(overview,'summary');
  const trend=analyticsSection<TrendRow[]>(overview,'trend');
  const origins=analyticsSection<BreakdownRow[]>(overview,'origin');
  const failures=analyticsSection<BreakdownRow[]>(overview,'failureDomain');
  const agents=analyticsSection<RankedWorkloadRow[]>(overview,'agents');
  const credentials=analyticsSection<RankedWorkloadRow[]>(overview,'credentials');
  const sources=analyticsSection<RankedWorkloadRow[]>(overview,'sourceSystems');
  const security=analyticsSection<SecuritySummary>(overview,'security');
  const securityDepartments=analyticsSection<SecurityOrganizationRow[]>(overview,'securityDepartments');
  const projection=analyticsSection<ProjectionStatus>(overview,'projection');
  const performance=analyticsSection<DashboardPerformanceStatus>(overview,'performance');
  const freshnessNeedsAttention = (projection?.data?.failedEvents??0)>0 || (projection?.data?.lagSeconds??0)>300 || (performance?.data?.dirtyRollupBuckets??0)>48;
  const s=summary?.data;

  return <div className="space-y-5">
    <section className="rounded-3xl border border-indigo-200 bg-gradient-to-br from-indigo-50 to-white p-5 shadow-sm"><div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between"><div><div className="text-xs font-black uppercase tracking-[.16em] text-indigo-700">Enterprise workload intelligence</div><h2 className="mt-1 text-xl font-black text-slate-950">Organization → workload → risk</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Analytics is a rebuildable read projection. Current RBAC and Resource Access determine every organization scope; this view never becomes authorization authority.</p></div><div className="w-full max-w-xs"><label htmlFor="analytics-range" className="text-xs font-black uppercase tracking-wide text-slate-500">Time window</label><SelectField id="analytics-range" value={range} onChange={(v)=>{setRange(v);setOrganizationPage(0);}} options={RANGE_OPTIONS}/></div></div>{departmentId||groupId?<div className="mt-4 flex flex-wrap items-center gap-2 text-xs font-bold">{overview.access.tenantWide?<button type="button" onClick={clearDepartment} className="rounded-full border border-slate-300 bg-white px-3 py-1.5 text-slate-700">All departments</button>:null}{departmentId?<><span>→</span><button type="button" onClick={()=>{setGroupId('');setGroupLabel('');}} className="rounded-full bg-indigo-700 px-3 py-1.5 text-white">{departmentLabel||departmentId}</button></>:null}{groupId?<><span>→</span><span className="rounded-full bg-slate-900 px-3 py-1.5 text-white">{groupLabel||groupId}</span></>:null}</div>:null}</section>

    {summary?.status==='READY'&&s?<div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6"><Metric label="Tasks" value={n(s.totalTasks)}/><Metric label="Failure rate" value={failureRate(s.totalTasks,s.failedTasks)} detail={`${n(s.failedTasks)} failed`}/><Metric label="Critical" value={n(s.criticalTasks)}/><Metric label="Agents" value={n(s.distinctAgents)}/><Metric label="Credentials" value={n(s.distinctCredentials)}/><Metric label="Avg duration" value={duration(s.averageDurationMs)}/></div>:<PanelUnavailable title="Workload summary" code={summary?.errorCode}/>} 

    {trend?.status==='READY'?<section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black text-slate-950">Workload trend</h3><p className="mt-1 text-sm text-slate-500">Revision {trend.revision}. Section refreshes independently from the rest of the dashboard.</p><div className="mt-4"><MiniBars rows={trend.data??[]}/></div></section>:<PanelUnavailable title="Workload trend" code={trend?.errorCode}/>} 

    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between"><div><h3 className="font-black text-slate-950">Organization drill-down</h3><p className="mt-1 text-sm text-slate-500">Search and page through the authorized organization scope. Results are server filtered; the browser does not preload the Tenant catalog.</p></div><input type="search" value={organizationSearch} onChange={event=>{setOrganizationSearch(event.target.value);setOrganizationPage(0);}} placeholder={departmentId?'Search Groups':'Search Departments'} className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm md:max-w-xs"/></div><div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-3">{organization?.items.map(row=><button key={row.departmentId} type="button" onClick={()=>chooseOrganization(row)} className="rounded-xl border border-slate-200 p-3 text-left hover:border-indigo-300 hover:bg-indigo-50"><div className="font-black text-slate-900">{row.departmentName}</div><div className="mt-1 text-xs text-slate-500">{n(row.taskCount)} tasks · {failureRate(row.taskCount,row.failureCount)} failed · {n(row.distinctAgents)} agents</div></button>)}</div>{!organization?.items.length?<p className="mt-4 text-sm text-slate-500">No matching organization workload in this time window.</p>:null}<div className="mt-4 flex items-center justify-between"><button type="button" disabled={!organization||organization.page===0} onClick={()=>setOrganizationPage(page=>Math.max(0,page-1))} className="rounded-xl border border-slate-300 px-3 py-2 text-xs font-black disabled:opacity-40">Previous</button><span className="text-xs font-bold text-slate-500">Page {(organization?.page??0)+1}</span><button type="button" disabled={!organization?.hasMore} onClick={()=>setOrganizationPage(page=>page+1)} className="rounded-xl border border-slate-300 px-3 py-2 text-xs font-black disabled:opacity-40">Next</button></div></section>

    <div className="grid gap-4 xl:grid-cols-2">{origins?.status==='READY'?<Breakdown title="Origin principal type" rows={origins.data??[]}/>:<PanelUnavailable title="Origin principal type" code={origins?.errorCode}/>} {failures?.status==='READY'?<Breakdown title="Failure domain" rows={failures.data??[]}/>:<PanelUnavailable title="Failure domain" code={failures?.errorCode}/>}</div>
    <div className="grid gap-4 xl:grid-cols-3">{agents?.status==='READY'?<Ranking title="Agents with the most failures" rows={agents.data??[]}/>:<PanelUnavailable title="Agents with the most failures" code={agents?.errorCode}/>} {credentials?.status==='READY'?<Ranking title="Credentials to investigate" rows={credentials.data??[]}/>:<PanelUnavailable title="Credentials to investigate" code={credentials?.errorCode}/>} {sources?.status==='READY'?<Ranking title="Source Systems" rows={sources.data??[]}/>:<PanelUnavailable title="Source Systems" code={sources?.errorCode}/>}</div>

    {security?.status==='READY'?<section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-black text-slate-950">Security incident performance</h3><p className="mt-1 text-sm text-slate-500">Read-only analytics; incident controls remain in Issues & Events.</p></div><Link href="/issues-events?view=incidents" className="rounded-xl border border-slate-300 px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">Open Security Incidents</Link></div><div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5"><Metric label="Incidents" value={n(security.data?.incidentCount??0)}/><Metric label="Open" value={n(security.data?.openIncidentCount??0)}/><Metric label="Critical" value={n(security.data?.criticalIncidentCount??0)}/><Metric label="Avg contain" value={duration(security.data?.averageContainmentMs)}/><Metric label="Avg resolve" value={duration(security.data?.averageResolutionMs)}/></div>{securityDepartments?.status==='READY'?<div className="mt-4"><div className="mb-2 text-xs font-black uppercase tracking-wide text-slate-500">Top 8 departments by incident pressure</div><div className="divide-y divide-slate-100">{(securityDepartments.data??[]).slice(0,8).map(row=><div key={row.departmentId} className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 py-2.5"><div><div className="text-sm font-black text-slate-900">{row.departmentName}</div><div className="mt-1 text-xs text-slate-500">{n(row.openIncidentCount)} open · {n(row.criticalIncidentCount)} critical · contain {duration(row.averageContainmentMs)}</div></div><div className="text-sm font-black text-slate-900">{n(row.incidentCount)}</div></div>)}</div></div>:null}</section>:security?.status==='OMITTED'?null:<PanelUnavailable title="Security incident performance" code={security?.errorCode}/>} 

    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black text-slate-950">Recent workload evidence</h3><div className="mt-3 divide-y divide-slate-100">{recent?.items.map(row=><div key={row.taskId} className="grid gap-1 py-3 md:grid-cols-[minmax(0,1fr)_auto]"><div><div className="font-black text-slate-900">{row.taskId}</div><div className="text-xs text-slate-500">{new Date(row.occurredAt).toLocaleString()} · {row.originPrincipalType||'UNKNOWN'} · {row.sourceSystem||'—'} · {row.failureDomain||'NONE'}</div></div><div className="text-sm font-black text-slate-700">{row.status}</div></div>)}</div>{recent?.hasMore?<button type="button" onClick={()=>{void more();}} disabled={loadingMore} className="mt-4 rounded-xl border border-slate-300 px-4 py-2 text-sm font-black text-slate-700">{loadingMore?'Loading…':'Load next 50'}</button>:null}</section>

    {(projection?.status==='READY'||performance?.status==='READY')?<section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black text-slate-950">Projection health</h3><p className="mt-1 text-sm text-slate-500">Tenant-wide projection health is shown only when your current Resource Access grants Tenant-wide analytics authority.</p>{freshnessNeedsAttention?<div role="status" className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-bold text-amber-900">Analytics freshness requires attention. Review projection lag, failed events, or dirty rollup backlog before relying on the newest interval.</div>:null}<div className="mt-4 grid gap-3 md:grid-cols-2">{projection?.status==='READY'?<Metric label="Projection lag" value={duration((projection.data?.lagSeconds??0)*1000)} detail={`${n(projection.data?.pendingEvents??0)} pending · ${n(projection.data?.failedEvents??0)} failed`}/>:null}{performance?.status==='READY'?<Metric label="Dirty rollup buckets" value={n(performance.data?.dirtyRollupBuckets??0)} detail={`${n(performance.data?.rollupRows??0)} rollup rows`}/>:null}</div></section>:null}
  </div>;
}
