'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { IssueTrackingQuickSetupDialog, type IssueTrackingContextOption } from '@/components/integrations/IssueTrackingQuickSetupDialog';
import { getSourceIssueTrackingReadiness, type IssueTrackingReadinessCheck, type IssueTrackingRuntimeReadiness } from '@/lib/api/domains/integrationIdentityApi';

function clean(value?: string | null) { return String(value ?? '').trim(); }

function priority(status?: string | null) {
  if (status === 'BLOCKED') return 4;
  if (status === 'CHECK_REQUIRED') return 3;
  if (status === 'READY_NOT_CERTIFIED') return 2;
  if (status === 'CERTIFIED') return 1;
  return 5;
}

function checkTone(check: IssueTrackingReadinessCheck) {
  if (check.status === 'READY' || check.status === 'CERTIFIED') return 'border-emerald-200 bg-emerald-50 text-emerald-950';
  if (check.status === 'NOT_CERTIFIED') return 'border-sky-200 bg-sky-50 text-sky-950';
  if (check.status === 'BLOCKED') return 'border-rose-200 bg-rose-50 text-rose-950';
  return 'border-amber-200 bg-amber-50 text-amber-950';
}

function overallCopy(value?: IssueTrackingRuntimeReadiness) {
  if (!value) return { title: 'Checking Issue Tracking…', body: 'Core is evaluating Source-level runtime readiness.', tone: 'border-slate-200 bg-slate-50', button: 'Review setup' };
  if (value.overallStatus === 'CERTIFIED') return { title: 'Runtime ready · live CREATE certified', body: 'The governed connector path is ready and this release carries live provider CREATE certification evidence.', tone: 'border-emerald-200 bg-emerald-50', button: 'Review setup' };
  if (value.overallStatus === 'READY_NOT_CERTIFIED') return { title: 'Runtime ready · live CREATE not certified', body: 'Configuration, Core execution and provider authentication are ready. A successful real provider CREATE has not yet been certified for this release.', tone: 'border-sky-200 bg-sky-50', button: 'Review setup' };
  if (value.overallStatus === 'CHECK_REQUIRED') return { title: 'Runtime configured · authentication check required', body: 'Core can resolve the governed connector path, but no current persisted provider authentication proof is available.', tone: 'border-amber-200 bg-amber-50', button: 'Test connection' };
  return { title: 'Issue Tracking needs attention', body: 'Core found a blocking configuration or runtime condition. Open setup to repair the first blocker.', tone: 'border-rose-200 bg-rose-50', button: 'Repair setup' };
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
  const [readiness, setReadiness] = useState<IssueTrackingRuntimeReadiness[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const validContexts = useMemo(() => contexts.filter((value) => clean(value.sourceSystemId)), [contexts]);
  const primary = useMemo(() => readiness.slice().sort((a, b) => priority(b.overallStatus) - priority(a.overallStatus))[0], [readiness]);
  const copy = overallCopy(primary);

  const refresh = useCallback(async () => {
    if (!validContexts.length) { setReadiness([]); return; }
    setLoading(true); setError('');
    try {
      const values = await Promise.all(validContexts.map((context) => getSourceIssueTrackingReadiness(context.sourceSystemId, context.taskType)));
      setReadiness(values);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally { setLoading(false); }
  }, [validContexts]);

  useEffect(() => { void refresh(); }, [refresh]);

  return <>
    <section className={`rounded-2xl border ${copy.tone} ${compact ? 'p-4' : 'p-5'}`}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Core-authoritative readiness</div>
          <h3 className="mt-1 font-black text-slate-950">{title}</h3>
          <p className="mt-1 text-sm font-black text-slate-900">{copy.title}</p>
          <p className="mt-1 text-sm leading-6 text-slate-700">{configure ? copy.body : 'Inherited from the Source System. Agents and Flows do not own separate Issue credentials or project mappings.'}</p>
          {configure ? <p className="mt-2 text-xs leading-5 text-slate-600">OpenDispatch Task Issue Policy decides when an Issue operation is requested. This card only reports whether the governed provider path is ready to execute that request.</p> : null}
        </div>
        {configure ? <button type="button" onClick={()=>setOpen(true)} className="shrink-0 rounded-xl bg-slate-900 px-4 py-2 text-sm font-black text-white">{copy.button}</button> : <Link href="/source-systems" className="shrink-0 rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50">Open Source Systems →</Link>}
      </div>

      {loading ? <div className="mt-3 text-xs font-bold text-slate-500">Checking server-authoritative Issue Tracking readiness…</div> : null}
      {error ? <div className="mt-3 rounded-xl border border-rose-200 bg-white p-3 text-xs font-bold text-rose-800">{error}</div> : null}

      {primary ? <>
        <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
          {primary.checks.map((check) => <div key={check.code} className={`rounded-xl border p-3 ${checkTone(check)}`}>
            <div className="flex items-start justify-between gap-2"><b className="text-xs uppercase tracking-wide">{check.label}</b><span className="text-[11px] font-black">{check.status}</span></div>
            <p className="mt-2 text-xs leading-5">{check.summary}</p>
            {check.reasonCode ? <p className="mt-2 break-all font-mono text-[10px] opacity-70">{check.reasonCode}</p> : null}
            {check.remediationRoute && !['READY','CERTIFIED'].includes(check.status) ? <Link href={check.remediationRoute} className="mt-2 inline-block text-[11px] font-black underline decoration-dotted">Open remediation →</Link> : null}
          </div>)}
        </div>
        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-slate-600">
          <span>Authority: <b>{primary.executionAuthority}</b></span>
          <span>Mapping: <b>{primary.mappingId || '—'}</b></span>
          <span>Project: <b>{primary.externalProjectKey || primary.externalProjectId || '—'}</b></span>
          <span>Tracker: <b>{primary.externalTrackerId || '—'}</b></span>
          <span>Certification: <b>{primary.liveCreateCertificationStatus}</b></span>
        </div>
        {primary.blockers.length ? <div className="mt-3 rounded-xl border border-rose-200 bg-white/80 p-3 text-xs text-rose-900"><b>Blocking evidence:</b> {primary.blockers.join(' · ')}</div> : null}
      </> : null}
    </section>
    {configure ? <IssueTrackingQuickSetupDialog open={open} contexts={validContexts} title={title} onClose={()=>setOpen(false)} onSaved={async()=>{await refresh();}} /> : null}
  </>;
}
