'use client';

import dynamic from 'next/dynamic';
import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PopupMultiSelectField, SelectField, FieldLabel, type SelectOption } from '@/components/access-management/shared/beginnerUi';
import { BeginnerGuideButton, FieldAssist } from '@/components/resource-scope/EnterpriseAccessUi';
import { RightDrawer } from '@/components/ui/RightDrawer';

const DualPlaneDashboard = dynamic(() => import('@/components/dashboard/DualPlaneDashboard').then((module) => module.DualPlaneDashboard), { ssr: false });

type ResourceType = 'TASK'|'AGENT'|'SOURCE_SYSTEM'|'EVENT'|'ISSUE'|'INCIDENT';
type Hit = { type:ResourceType; id:string; title:string; subtitle?:string; status?:string; ownerDepartmentId?:string; ownerGroupId?:string; updatedAt?:string; href:string };
type Snapshot = { counts:Array<{type:ResourceType;value:number;capped:boolean}>; recent:Hit[]; accessibleTypes:ResourceType[]; tenantWideRuntimeDiagnosticsAllowed:boolean; partial:boolean };

const TYPE_OPTIONS: SelectOption[] = [
  {value:'TASK',label:'Tasks',description:'Work items you are allowed to see'},
  {value:'AGENT',label:'Agents',description:'Agents in your authorized organizational scope'},
  {value:'SOURCE_SYSTEM',label:'Source systems',description:'Governed event sources you can access'},
  {value:'EVENT',label:'Business events',description:'Scoped event metadata; payload access is separate'},
  {value:'ISSUE',label:'Business issues',description:'Issue projections in your authorized scope'},
  {value:'INCIDENT',label:'Incidents',description:'Operational incidents in your authorized scope'},
];
const FORMAT_OPTIONS: SelectOption[] = [{value:'CSV',label:'CSV · spreadsheet-friendly'},{value:'JSON',label:'JSON · system-friendly'}];
const LIMIT_OPTIONS: SelectOption[] = [{value:'100',label:'100 rows'},{value:'250',label:'250 rows'},{value:'500',label:'500 rows'}];
const TYPE_LABEL = Object.fromEntries(TYPE_OPTIONS.map((option)=>[option.value,option.label])) as Record<ResourceType,string>;

function Card({ label, value, href }: Readonly<{label:string;value:string;href:string}>) {
  return <Link href={href} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition hover:border-blue-300 hover:bg-blue-50">
    <div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div>
    <div className="mt-2 text-3xl font-black text-slate-950">{value}</div>
    <div className="mt-2 text-xs font-bold text-blue-700">Open workspace →</div>
  </Link>;
}
function hrefForType(type:ResourceType): string {
  if (type==='TASK') return '/tasks'; if (type==='AGENT') return '/agents'; if (type==='SOURCE_SYSTEM') return '/source-systems';
  return type==='EVENT' ? '/issues-events?view=events' : type==='ISSUE' ? '/issues-events?view=issues' : '/issues-events?view=incidents';
}
function friendlyTime(value?:string): string { if (!value) return 'Recently updated'; const date=new Date(value); return Number.isNaN(date.getTime())?'Recently updated':date.toLocaleString(); }

export function ScopedOperationalDashboard() {
  const [snapshot,setSnapshot]=useState<Snapshot|null>(null); const [loading,setLoading]=useState(true); const [error,setError]=useState<string|null>(null);
  const [searchOpen,setSearchOpen]=useState(false); const [exportOpen,setExportOpen]=useState(false); const [runtimeOpen,setRuntimeOpen]=useState(false);
  const [query,setQuery]=useState(''); const [searchTypes,setSearchTypes]=useState<string[]>([]); const [results,setResults]=useState<Hit[]>([]); const [searching,setSearching]=useState(false);
  const [exportType,setExportType]=useState('TASK'); const [exportFormat,setExportFormat]=useState('CSV'); const [exportLimit,setExportLimit]=useState('100'); const [exportQuery,setExportQuery]=useState('');
  const load=useCallback(async()=>{ setLoading(true); setError(null); try { const response=await fetch('/api/operational-scope/dashboard',{cache:'no-store'}); if(!response.ok) throw new Error('Your accessible dashboard is unavailable.'); setSnapshot(await response.json() as Snapshot); } catch(caught){setError(caught instanceof Error?caught.message:'Your accessible dashboard is unavailable.');} finally{setLoading(false);} },[]);
  useEffect(()=>{void load();},[load]);
  const accessibleOptions=useMemo(()=>TYPE_OPTIONS.filter((option)=>snapshot?.accessibleTypes.includes(option.value as ResourceType)),[snapshot?.accessibleTypes]);
  useEffect(()=>{ if(accessibleOptions.length && !accessibleOptions.some((option)=>option.value===exportType)) setExportType(accessibleOptions[0].value); },[accessibleOptions,exportType]);
  async function search(){setSearching(true);try{const params=new URLSearchParams({q:query,limit:'60'});searchTypes.forEach((type)=>params.append('type',type));const response=await fetch(`/api/operational-scope/search?${params.toString()}`,{cache:'no-store'});if(!response.ok)throw new Error();const payload=await response.json() as {items:Hit[]};setResults(payload.items);}catch{setResults([]);}finally{setSearching(false);}}
  function startExport(){const params=new URLSearchParams({type:exportType,format:exportFormat,limit:exportLimit});if(exportQuery.trim())params.set('q',exportQuery.trim());window.location.assign(`/api/operational-scope/export?${params.toString()}`);}

  if(loading) return <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm font-semibold text-slate-600">Loading only the work your current Responsibility can access…</div>;
  if(error) return <div className="rounded-2xl border border-rose-200 bg-rose-50 p-5"><div className="font-black text-rose-950">Dashboard unavailable</div><p className="mt-1 text-sm text-rose-800">{error}</p><button type="button" onClick={()=>void load()} className="mt-3 rounded-xl border border-rose-200 bg-white px-3 py-2 text-sm font-black text-rose-800">Try again</button></div>;
  if(!snapshot) return null;

  return <div className="space-y-5">
    <section className="rounded-3xl border border-blue-200 bg-gradient-to-br from-blue-50 to-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div><div className="text-xs font-black uppercase tracking-wide text-blue-700">My accessible operations</div><h2 className="mt-1 text-xl font-black text-slate-950">Start here</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Counts, recent work, search, export, and realtime notifications use the same Tenant, Responsibility, Department/Group, and Resource Scope rules as the underlying resource pages.</p>{snapshot.partial?<p className="mt-2 text-xs font-bold text-amber-800">Some authorized data sources are temporarily unavailable; no broader fallback data was used.</p>:null}</div>
        <div className="flex flex-wrap gap-2"><button type="button" onClick={()=>setSearchOpen(true)} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800">Search my work</button><button type="button" onClick={()=>setExportOpen(true)} className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-800 hover:bg-slate-50">Export</button><BeginnerGuideButton title="Dashboard guide" description="Use one page for everyday work; open deeper tools only when you need them." steps={[{title:'Review your accessible work',description:'The numbers only include resources your current Responsibility can read.'},{title:'Search without leaving the page',description:'Use the Search drawer and choose resource types instead of typing internal IDs.'},{title:'Export a safe summary',description:'Choose a resource type, format, and row limit. Export re-checks your current access when it starts.'},{title:'Open runtime diagnostics only when needed',description:'Tenant-wide diagnostic tools stay behind a separate drawer and are only offered when your current Responsibility allows them.'}]}/></div>
      </div>
    </section>

    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{snapshot.counts.filter((count)=>snapshot.accessibleTypes.includes(count.type)).map((count)=><Card key={count.type} label={TYPE_LABEL[count.type]} value={`${count.value}${count.capped?'+':''}`} href={hrefForType(count.type)}/>)}</div>

    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-black text-slate-950">Recent accessible work</h3><p className="mt-1 text-sm text-slate-500">One combined list keeps daily work on this main page. Open the domain workspace only when you need more detail.</p></div><FieldAssist help="Ownership and permissions are evaluated by the backend before these rows reach the dashboard." href="/access-management" linkLabel="Review my access" /></div>
      <div className="mt-4 divide-y divide-slate-100">{snapshot.recent.length?snapshot.recent.slice(0,12).map((item)=><Link key={`${item.type}-${item.id}`} href={item.href} className="flex flex-col gap-1 py-3 hover:bg-slate-50 sm:flex-row sm:items-center sm:justify-between sm:px-2"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-black text-slate-600">{TYPE_LABEL[item.type]}</span><span className="truncate text-sm font-black text-slate-900">{item.title}</span>{item.status?<span className="text-xs font-bold text-slate-500">{item.status}</span>:null}</div>{item.subtitle?<p className="mt-1 truncate text-xs text-slate-500">{item.subtitle}</p>:null}</div><span className="shrink-0 text-xs text-slate-400">{friendlyTime(item.updatedAt)}</span></Link>):<div className="rounded-xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">No accessible recent work yet. Create or receive work from a Source System to get started.</div>}</div>
    </section>

    <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex flex-wrap items-center justify-between gap-3"><div><div className="text-sm font-black text-slate-900">More tools</div><p className="mt-1 text-xs leading-5 text-slate-500">Keep advanced runtime details out of the everyday workspace.</p></div>{snapshot.tenantWideRuntimeDiagnosticsAllowed?<button type="button" onClick={()=>setRuntimeOpen(true)} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm font-black text-slate-700">Runtime diagnostics</button>:<span className="text-xs font-semibold text-slate-500">Runtime diagnostics require Tenant-wide authority.</span>}</div></section>

    <RightDrawer open={searchOpen} onClose={()=>setSearchOpen(false)} title="Search my accessible work" description="Search across only the resource types your current Responsibility can access. Your company workspace is resolved automatically." widthClassName="max-w-3xl">
      <div className="space-y-4"><FieldLabel htmlFor="scoped-search" label="Search" help="Search by business name, status, task/issue key, or known resource ID."><input id="scoped-search" type="search" value={query} onChange={(event)=>setQuery(event.target.value)} onKeyDown={(event)=>{if(event.key==='Enter')void search();}} placeholder="Example: payment, failed, FIN-1024" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm font-semibold outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" /></FieldLabel><FieldLabel htmlFor="scoped-search-types" label="Resource types" help="Choose one or more types. Leave empty to search every resource type you can access."><PopupMultiSelectField id="scoped-search-types" values={searchTypes} onChange={setSearchTypes} options={accessibleOptions} placeholder="All accessible resource types" /></FieldLabel><button type="button" onClick={()=>void search()} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">{searching?'Searching…':'Search'}</button><div className="divide-y divide-slate-100">{results.map((item)=><Link key={`${item.type}-${item.id}`} href={item.href} onClick={()=>setSearchOpen(false)} className="block py-3 hover:bg-slate-50"><div className="text-xs font-black text-blue-700">{TYPE_LABEL[item.type]}</div><div className="mt-1 text-sm font-black text-slate-900">{item.title}</div><div className="mt-1 text-xs text-slate-500">{[item.subtitle,item.status].filter(Boolean).join(' · ')}</div></Link>)}{!searching&&results.length===0?<p className="py-6 text-sm text-slate-500">Enter a search term or choose resource types, then select Search.</p>:null}</div></div>
    </RightDrawer>

    <RightDrawer open={exportOpen} onClose={()=>setExportOpen(false)} title="Export an authorized summary" description="Export is re-authorized when it starts and contains only safe summary fields from resources you can currently read." widthClassName="max-w-xl">
      <div className="space-y-4"><FieldLabel htmlFor="export-type" label="Resource type" help="Choose the business resource to export. Internal resource codes are not typed manually."><SelectField id="export-type" value={exportType} onChange={setExportType} options={accessibleOptions} /></FieldLabel><FieldLabel htmlFor="export-format" label="Format"><SelectField id="export-format" value={exportFormat} onChange={setExportFormat} options={FORMAT_OPTIONS} /></FieldLabel><FieldLabel htmlFor="export-limit" label="Maximum rows" help="Use a smaller export for everyday review; choose 500 only when needed."><SelectField id="export-limit" value={exportLimit} onChange={setExportLimit} options={LIMIT_OPTIONS} /></FieldLabel><FieldLabel htmlFor="export-query" label="Optional filter" help="Free text is appropriate here because it narrows search results rather than selecting a governed ID."><input id="export-query" type="search" value={exportQuery} onChange={(event)=>setExportQuery(event.target.value)} placeholder="Example: failed" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm font-semibold outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" /></FieldLabel><div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950">This quick export contains summary fields only. Sensitive payloads, credentials, attachment contents, and provider direct URLs are never included here. Use the relevant domain workflow when a sensitive export requires its own permission.</div><button type="button" onClick={startExport} disabled={!exportType} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white disabled:opacity-50">Start export</button></div>
    </RightDrawer>

    <RightDrawer open={runtimeOpen&&snapshot.tenantWideRuntimeDiagnosticsAllowed} onClose={()=>setRuntimeOpen(false)} title="Tenant-wide runtime diagnostics" description="Advanced Core and Gateway diagnostics. This panel loads only after Tenant-wide authority has been verified." widthClassName="max-w-[96vw]">
      {runtimeOpen&&snapshot.tenantWideRuntimeDiagnosticsAllowed?<DualPlaneDashboard/>:null}
    </RightDrawer>
  </div>;
}
