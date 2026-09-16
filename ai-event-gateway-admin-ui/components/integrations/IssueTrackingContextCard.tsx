'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { IssueTrackingQuickSetupDialog, type IssueTrackingContextOption } from '@/components/integrations/IssueTrackingQuickSetupDialog';
import { listConnections, listCredentials, listPrincipals, listProjectMappings, type IntegrationConnection, type IntegrationProjectMapping } from '@/lib/api/domains/integrationIdentityApi';

function clean(value?: string | null) { return String(value ?? '').trim(); }
function upper(value?: string | null) { return clean(value).toUpperCase(); }
function matches(mapping: IntegrationProjectMapping, contexts: IssueTrackingContextOption[]) {
  return contexts.some((context) => {
    if (upper(mapping.sourceSystemId) !== upper(context.sourceSystemId)) return false;
    const mappingTaskType = clean(mapping.taskType);
    const contextTaskType = clean(context.taskType);
    // A Source-level mapping (no taskType) is the canonical default and is inherited
    // by every Agent/Flow work type from that Source System. Task-specific mappings
    // remain an Advanced override and only match the same task type.
    return !mappingTaskType || (!!contextTaskType && upper(mappingTaskType) === upper(contextTaskType));
  });
}
function activeLifecycle(mapping: IntegrationProjectMapping) { return mapping.enabled === true && upper(mapping.lifecycleStatus) === 'ACTIVE'; }
function runtimeReady(mapping: IntegrationProjectMapping) {
  return activeLifecycle(mapping)
    && upper(mapping.mappingStatus) === 'VALID'
    && Boolean(clean(mapping.metadataSnapshotId))
    && Boolean(clean(mapping.metadataSchemaHash));
}

export function IssueTrackingContextCard({
  contexts,
  title='Issue Tracking',
  compact=false,
  configure=true,
}: Readonly<{
  contexts: IssueTrackingContextOption[];
  title?: string;
  compact?: boolean;
  configure?: boolean;
}>) {
  const [open, setOpen] = useState(false);
  const [mappings, setMappings] = useState<IntegrationProjectMapping[]>([]);
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [credentialReady, setCredentialReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const validContexts = useMemo(() => contexts.filter((value) => clean(value.sourceSystemId)), [contexts]);
  const activeMappings = useMemo(() => mappings.filter((value) => runtimeReady(value) && matches(value, validContexts)), [mappings, validContexts]);
  const staleActiveMappings = useMemo(() => mappings.filter((value) => activeLifecycle(value) && !runtimeReady(value) && matches(value, validContexts)), [mappings, validContexts]);
  const primary = activeMappings[0];
  const stalePrimary = staleActiveMappings[0];

  const refresh = useCallback(async () => {
    if (!validContexts.length) { setMappings([]); return; }
    setLoading(true); setError('');
    try {
      const [mappingValues, connectionValues] = await Promise.all([listProjectMappings(), listConnections()]);
      setMappings(mappingValues); setConnections(connectionValues.filter((value) => value.providerType === 'REDMINE'));
      const current = mappingValues.find((value) => runtimeReady(value) && matches(value, validContexts))
        ?? mappingValues.find((value) => activeLifecycle(value) && matches(value, validContexts));
      if (!current) { setCredentialReady(false); return; }
      const accounts = (await listPrincipals(current.connectionId)).filter((value) => value.principalType === 'SERVICE_ACCOUNT');
      const explicit = [current.readPrincipalId, current.createPrincipalId, current.commentPrincipalId, current.updatePrincipalId].map(clean).find(Boolean);
      const account = explicit ? accounts.find((value) => value.principalId === explicit) : accounts.length === 1 ? accounts[0] : undefined;
      if (!account) { setCredentialReady(false); return; }
      const credentials = await listCredentials(account.principalId);
      setCredentialReady(credentials.some((value) => ['ACTIVE','GRACE_PERIOD'].includes(upper(value.status))));
    } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
    finally { setLoading(false); }
  }, [validContexts]);

  useEffect(() => { void refresh(); }, [refresh]);

  const displayMapping = primary ?? stalePrimary;
  const connection = connections.find((value) => value.connectionId === displayMapping?.connectionId);
  const ready = Boolean(primary && connection?.enabled && upper(connection?.status) === 'ACTIVE' && credentialReady);

  return <>
    <section className={`rounded-2xl border ${ready ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'} ${compact ? 'p-4' : 'p-5'}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className={`font-black ${ready ? 'text-emerald-950' : 'text-amber-950'}`}>{title}</h3>
          {ready ? <p className="mt-1 text-sm leading-6 text-emerald-900">{configure ? 'Runtime-ready for this Source System. Agents and Flows inherit this Redmine connector context; OpenDispatch Task Issue Policy decides when an Issue operation is requested, and Redmine remains the final permission authority.' : 'Inherited from the Source System. This Agent / Flow uses the Source-level Redmine connector context and does not own separate credentials or project mapping.'}</p> : <p className="mt-1 text-sm leading-6 text-amber-900">{configure ? (stalePrimary ? 'A mapping is marked ACTIVE but is not runtime-ready. Repair the Source System Issue Tracking setup.' : 'Configure Redmine once for this Source System: URL, API Key, Project and Tracker.') : 'No runtime-ready Source System Issue Tracking setup is available for this work yet. Configure it once on the Source System.'}</p>}
        </div>
        {configure ? <button type="button" onClick={()=>setOpen(true)} className={`shrink-0 rounded-xl px-4 py-2 text-sm font-black text-white ${ready ? 'bg-emerald-800' : 'bg-amber-800'}`}>{ready ? 'Review setup' : stalePrimary ? 'Repair setup' : 'Configure once'}</button> : <Link href="/source-systems" className="shrink-0 rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">Open Source Systems →</Link>}
      </div>
      {loading ? <div className="mt-3 text-xs font-bold text-slate-500">Checking Issue Tracking…</div> : null}
      {error ? <div className="mt-3 rounded-xl border border-rose-200 bg-white p-3 text-xs font-bold text-rose-800">{error}</div> : null}
      {(primary || stalePrimary) ? <div className="mt-3 grid gap-2 text-xs sm:grid-cols-4"><div className="rounded-xl bg-white/80 p-3"><b>Status</b><div className="mt-1 font-black">{ready ? 'Active' : 'Needs attention'}</div></div><div className="rounded-xl bg-white/80 p-3"><b>Project</b><div className="mt-1">{(primary || stalePrimary)?.externalProjectKey || (primary || stalePrimary)?.externalProjectId}</div></div><div className="rounded-xl bg-white/80 p-3"><b>Tracker</b><div className="mt-1">{(primary || stalePrimary)?.externalIssueType || (primary || stalePrimary)?.externalTrackerId || 'Default'}</div></div><div className="rounded-xl bg-white/80 p-3"><b>Credential</b><div className="mt-1">{credentialReady ? 'Ready' : 'Needs attention'}</div></div></div> : null}
      {stalePrimary ? <div className="mt-2 rounded-xl border border-amber-300 bg-white p-3 text-xs font-bold text-amber-900">ACTIVE is not enough for runtime use. Mapping status must be VALID and provider metadata snapshot/schema must be present.</div> : null}
      {activeMappings.length > 1 ? <div className="mt-2 text-xs font-bold text-slate-600">{activeMappings.length} runtime-ready mappings cover the available Source / Task contexts.</div> : null}
    </section>
    {configure ? <IssueTrackingQuickSetupDialog open={open} contexts={validContexts} title={title} onClose={()=>setOpen(false)} onSaved={async()=>{await refresh();}} /> : null}
  </>;
}
