'use client';

import { useEffect, useMemo, useState } from 'react';
import { businessEventsAdminApi, type BusinessEventPayloadView, type BusinessEventView } from '@/lib/api/domains/businessEventsAdminApi';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import { RefreshButton } from '@/components/common/RefreshButton';
import { LoadingBox } from '@/components/common/LoadingBox';
import { EmptyState } from '@/components/common/EmptyState';
import { formatDateTime } from '@/lib/utils/format';
import { BeginnerGuideButton, OwnershipAccessCard } from '@/components/resource-scope/EnterpriseAccessUi';
import { RightDrawer } from '@/components/ui/RightDrawer';
import { SearchField } from '@/components/access-management/shared/beginnerUi';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Department, Group } from '@/lib/iam/types';

function eventScopeKey(event: BusinessEventView): string {
  if (event.ownerDepartmentId) return `DEPARTMENT:${event.ownerDepartmentId}`;
  if (event.ownerGroupId) return `GROUP:${event.ownerGroupId}`;
  return event.scopeStatus === 'TENANT_OWNED' ? 'TENANT' : 'UNRESOLVED';
}

export function BusinessEventConsole() {
  const entitlements = useUiEntitlements();
  const { activeTenantId: selectedTenantId } = useAuth();
  const canReadPayload = actionAllowed(entitlements.value, 'business-events.payload');
  const [events, setEvents] = useState<BusinessEventView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string>();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [query, setQuery] = useState('');
  const [scopeFilter, setScopeFilter] = useState('ALL');
  const [payload, setPayload] = useState<BusinessEventPayloadView | null>(null);
  const [payloadEvent, setPayloadEvent] = useState<BusinessEventView | null>(null);
  const [payloadBusy, setPayloadBusy] = useState(false);

  const emptyDepartmentPage = { items: [] as Department[], page: 0, size: 0, hasMore: false, totalCount: 0 };
  const emptyGroupPage = { items: [] as Group[], page: 0, size: 0, hasMore: false, totalCount: 0 };

  const departmentName = (id?: string | null) => departments.find((item) => item.departmentId === id)?.name ?? id ?? 'Unknown Department';
  const groupName = (id?: string | null) => groups.find((item) => item.groupId === id)?.name ?? id ?? 'Unknown Group';
  const scopeLabel = (event: BusinessEventView): string => event.ownerDepartmentId ? `Department · ${departmentName(event.ownerDepartmentId)}` : event.ownerGroupId ? `Group · ${groupName(event.ownerGroupId)}` : event.scopeStatus === 'TENANT_OWNED' ? 'Tenant-wide' : 'Scope unresolved';

  async function reload() {
    setLoading(true); setError(null);
    try {
      const [eventRows, departmentPage, groupPage] = await Promise.all([
        businessEventsAdminApi.list({ limit: 200 }),
        selectedTenantId ? accessManagementApi.departments(selectedTenantId, 0, 200, '', 'ACTIVE').catch(() => emptyDepartmentPage) : Promise.resolve(emptyDepartmentPage),
        selectedTenantId ? accessManagementApi.groups(selectedTenantId, 0, 200, '', '', 'ACTIVE').catch(() => emptyGroupPage) : Promise.resolve(emptyGroupPage),
      ]);
      setEvents(eventRows); setDepartments(departmentPage.items ?? []); setGroups(groupPage.items ?? []); setLastUpdatedAt(new Date().toISOString());
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Failed to load Business Events.'); }
    finally { setLoading(false); }
  }

  async function openPayload(event: BusinessEventView) {
    setPayloadEvent(event); setPayload(null); setPayloadBusy(true); setError(null);
    try { setPayload(await businessEventsAdminApi.payload(event.eventId)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : 'Payload access was denied or unavailable.'); }
    finally { setPayloadBusy(false); }
  }

  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const scopeOptions = useMemo(() => {
    const values = new Map<string,string>();
    for (const event of events) values.set(eventScopeKey(event), scopeLabel(event));
    return [...values.entries()].filter(([key]) => key !== 'UNRESOLVED').sort((a,b)=>a[1].localeCompare(b[1]));
  }, [events, departments, groups]); // eslint-disable-line react-hooks/exhaustive-deps

  const visibleEvents = useMemo(() => {
    const q = query.trim().toLowerCase();
    return events.filter((event) => (scopeFilter === 'ALL' || eventScopeKey(event) === scopeFilter) && (!q || [event.eventType,event.sourceSystem,event.normalizedMessage,event.correlationId].some((value)=>String(value??'').toLowerCase().includes(q))));
  }, [events, query, scopeFilter]);

  return <main className="space-y-5">
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"><div>
        <div className="text-xs font-black uppercase tracking-wide text-blue-700">Canonical Business Events</div>
        <h1 className="mt-1 text-2xl font-black text-slate-950">Business Events</h1>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Work from one authorized Event list. Payload, ownership explanation and related access are opened in-place so a new operator does not need to navigate between diagnostic pages.</p>
      </div><div className="flex flex-wrap gap-2"><BeginnerGuideButton title="Business Events in three steps" description="The list is already filtered by your effective Responsibility scope." steps={[{title:'Find the business event',description:'Search by type, Source System or message, and optionally narrow to one accessible organizational scope.'},{title:'Review ownership before content',description:'The ownership card explains why the event is visible. Payload remains a separate permission.'},{title:'Open payload only when needed',description:'Payload opens in a side panel and never appears in the list response.'}]} /><RefreshButton refreshing={loading} lastUpdatedAt={lastUpdatedAt} onRefresh={() => void reload()} /></div></div>
      <div className="mt-5 grid gap-3 md:grid-cols-[minmax(0,1fr)_20rem]"><SearchField id="business-event-search" value={query} onChange={setQuery} placeholder="Search event type, source, message or correlation"/><select value={scopeFilter} onChange={(e)=>setScopeFilter(e.target.value)} className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-semibold"><option value="ALL">All accessible scopes</option>{scopeOptions.map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></div>
    </section>
    {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
    {loading ? <LoadingBox label="Loading authorized Business Events..." /> : null}
    {!loading && visibleEvents.length === 0 ? <EmptyState title="No Business Events in this view" description="Try another accessible scope or search. Events with unresolved Source ownership remain fail-closed until remediated." /> : null}
    <section className="space-y-3">
      {visibleEvents.map((event) => <article key={event.eventId} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 className="font-black text-slate-950">{event.eventType || 'Business Event'}</h2><span className="rounded-full bg-violet-50 px-3 py-1 text-xs font-bold text-violet-800">{scopeLabel(event)}</span>{event.sourceSystem ? <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-700">Source · {event.sourceSystem}</span> : null}</div><p className="mt-2 break-words text-sm leading-6 text-slate-600">{event.normalizedMessage || 'No normalized message.'}</p><div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-500">{event.eventStage ? <span>Stage: {event.eventStage}</span> : null}{event.decisionType ? <span>Decision: {event.decisionType}</span> : null}{event.occurredAt ? <span>Occurred: {formatDateTime(event.occurredAt)}</span> : null}</div></div>{canReadPayload ? <button type="button" onClick={() => void openPayload(event)} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">View payload</button> : null}</div>
        <div className="mt-4"><OwnershipAccessCard compact resourceType="EVENT" resourceId={event.eventId} permissionCode="admin.business.event.detail" requestedVisibility="METADATA" purpose="Explain Business Event access from the unified Events workspace." primaryOwner={scopeLabel(event)} provenance={`Inherited from Source System ${event.sourceSystem || 'unknown'} at event intake. Historical ownership does not drift when the Source is re-scoped.`} managementHref="/source-systems" managementLabel="Open Source Systems" /></div>
      </article>)}
    </section>
    <RightDrawer open={Boolean(payloadEvent)} onClose={()=>{setPayloadEvent(null);setPayload(null);}} title={payloadEvent ? `${payloadEvent.eventType || 'Business Event'} payload` : 'Event payload'} description="Payload is protected by a separate permission and opens only when explicitly requested." widthClassName="max-w-3xl">
      {payloadBusy ? <LoadingBox label="Loading governed payload…" /> : payload ? <pre className="max-h-[70vh] overflow-auto rounded-2xl bg-slate-950 p-4 text-xs leading-5 text-slate-100">{JSON.stringify(payload.payload, null, 2)}</pre> : <div className="rounded-2xl bg-slate-50 p-4 text-sm text-slate-600">No payload is available.</div>}
    </RightDrawer>
  </main>;
}
