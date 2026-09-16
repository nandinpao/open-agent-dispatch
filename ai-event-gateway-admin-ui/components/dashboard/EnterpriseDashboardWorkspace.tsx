'use client';
import { useEffect, useState } from 'react';
import { ScopedOperationalDashboard } from '@/components/dashboard/ScopedOperationalDashboard';
import { EnterpriseAnalyticsDashboard } from '@/components/dashboard/EnterpriseAnalyticsDashboard';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';

type View='work'|'analytics';
export function EnterpriseDashboardWorkspace(){
  const entitlements=useUiEntitlements();
  const canViewAnalytics=actionAllowed(entitlements.value,'dashboard.analytics.view');
  const [view,setView]=useState<View>('work');
  useEffect(()=>{if(!canViewAnalytics&&view==='analytics')setView('work');},[canViewAnalytics,view]);
  return <div className="space-y-5"><nav aria-label="Dashboard views" className="inline-flex rounded-xl border border-slate-200 bg-white p-1 shadow-sm"><button type="button" onClick={()=>setView('work')} aria-current={view==='work'?'page':undefined} className={`rounded-lg px-4 py-2 text-sm font-black ${view==='work'?'bg-slate-950 text-white':'text-slate-600 hover:bg-slate-50'}`}>My Work</button>{canViewAnalytics?<button type="button" onClick={()=>setView('analytics')} aria-current={view==='analytics'?'page':undefined} className={`rounded-lg px-4 py-2 text-sm font-black ${view==='analytics'?'bg-slate-950 text-white':'text-slate-600 hover:bg-slate-50'}`}>Enterprise Analytics</button>:null}</nav>{view==='work'||!canViewAnalytics?<ScopedOperationalDashboard/>:<EnterpriseAnalyticsDashboard/>}</div>;
}
