'use client';

import Link from 'next/link';
import type { A2AOperationsDetail } from '@/lib/api/domains/a2aOperationsApi';
import { A2ACancellationReliabilityPanel } from '@/components/a2a/A2ACancellationReliabilityPanel';
import { A2AApprovalExperiencePanel } from '@/components/phase7c/A2AApprovalExperiencePanel';
import { A2AResultLanesPanel } from '@/components/phase7c/A2AResultLanesPanel';
import { humanizeA2ACode } from '@/lib/a2a/contracts';
import { A2AStatusBadge } from './A2AStatusBadge';
import { A2ATopologyPanel } from './A2ATopologyPanel';
import { A2AUnifiedReconciliationPanel } from './A2AUnifiedReconciliationPanel';
import { AuthorityEvidencePanel } from './AuthorityEvidencePanel';
import { BlockerDiagnosisPanel } from './BlockerDiagnosisPanel';
import { EvidenceTimeline } from './EvidenceTimeline';
import { GovernedActionsPanel } from './GovernedActionsPanel';

export function A2AOperationsDetailView({ detail, domainNames = {}, onRefresh }: { detail: A2AOperationsDetail; domainNames?: Record<string, string>; onRefresh: () => Promise<void> | void }) {
  const { summary } = detail;
  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.18em] text-amber-700">Legacy directional A2A evidence</p>
            <h1 className="mt-1 text-2xl font-black text-slate-950">{humanizeA2ACode(summary.requestedTaskType || 'A2A_REQUEST')}</h1>
            <p className="mt-2 max-w-2xl text-sm text-slate-600">This record predates capability-first delegation. Directional domain identifiers are retained only as historical evidence and do not represent current provider-routing authority.</p>
          </div>
          <div className="flex flex-wrap gap-2"><A2AStatusBadge value={summary.requestStatus} /><A2AStatusBadge value={summary.blockerCode} /></div>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {detail.stages.map((stage) => (
            <div key={stage.stage} className="rounded-xl border border-slate-200 bg-slate-50 p-3">
              <p className="text-xs font-black uppercase tracking-wide text-slate-500">{humanizeA2ACode(stage.stage)}</p>
              <div className="mt-2"><A2AStatusBadge value={stage.status} /></div>
              <p className="mt-2 text-xs font-bold text-slate-500">Source of truth: {humanizeA2ACode(stage.authority)}</p>
            </div>
          ))}
        </div>

        <details className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm">
          <summary className="cursor-pointer font-black text-slate-800">Technical identity</summary>
          <p className="mt-2 text-xs leading-5 text-slate-600">IDs and evidence references are available for support and engineering investigation; they are not required for normal operational decisions.</p>
          <dl className="mt-3 grid gap-x-6 gap-y-2 text-xs sm:grid-cols-2">
            <div><dt className="font-black text-slate-500">Delegation ID</dt><dd className="mt-1 break-all font-mono">{summary.requestId}</dd></div>
            <div><dt className="font-black text-slate-500">Version</dt><dd className="mt-1 font-mono">{summary.version}</dd></div>
            <div><dt className="font-black text-slate-500">Legacy source domain</dt><dd className="mt-1 break-all">{domainNames[summary.sourceDomainId || ''] || summary.sourceDomainId || '—'}</dd></div>
            <div><dt className="font-black text-slate-500">Legacy target domain</dt><dd className="mt-1 break-all">{domainNames[summary.targetDomainId || ''] || summary.targetDomainId || '—'}</dd></div>
            {summary.sourceTaskId ? <div><dt className="font-black text-slate-500">Parent Task ID</dt><dd className="mt-1 break-all font-mono">{summary.sourceTaskId}</dd></div> : null}
            {summary.childTaskId ? <div><dt className="font-black text-slate-500">Child Task ID</dt><dd className="mt-1 break-all font-mono">{summary.childTaskId}</dd></div> : null}
          </dl>
          <div className="mt-3 space-y-1">{detail.stages.filter((stage) => stage.evidenceReference).map((stage) => <p key={`${stage.stage}-evidence`} className="break-all font-mono text-xs text-slate-500">{stage.stage}: {stage.evidenceReference}</p>)}</div>
        </details>
      </section>

      <BlockerDiagnosisPanel blocker={detail.blocker} />
      <A2AApprovalExperiencePanel request={detail.requestRecord} />
      <A2AResultLanesPanel detail={detail} />

      {detail.reconciliationCase?.evidenceId ? <A2AUnifiedReconciliationPanel caseId={detail.reconciliationCase.evidenceId} onChanged={onRefresh} /> : null}
      {detail.cancellation?.evidenceId ? <A2ACancellationReliabilityPanel cancellationId={detail.cancellation.evidenceId} onChanged={onRefresh} /> : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <EvidenceTimeline entries={detail.timeline} />
        <div className="space-y-5">
          <GovernedActionsPanel
            requestId={summary.requestId}
            actions={detail.actions}
            reconciliationCaseId={detail.reconciliationCase?.evidenceId}
            quarantineId={detail.quarantine?.evidenceId}
            onCompleted={onRefresh}
          />
          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Related work</p>
            <div className="mt-3 space-y-2 text-sm font-bold">
              {summary.sourceTaskId ? <Link className="block rounded-lg bg-slate-50 px-3 py-2 text-indigo-700 hover:bg-indigo-50" href={`/tasks/${encodeURIComponent(summary.sourceTaskId)}`}>Open Parent Task</Link> : null}
              {summary.childTaskId ? <Link className="block rounded-lg bg-slate-50 px-3 py-2 text-indigo-700 hover:bg-indigo-50" href={`/tasks/${encodeURIComponent(summary.childTaskId)}`}>Open Child Task</Link> : null}
              <Link className="block rounded-lg bg-slate-50 px-3 py-2 text-indigo-700 hover:bg-indigo-50" href="/tasks/failure-queue">Open Failure Queue</Link>
            </div>
          </section>
        </div>
      </div>

      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-900">Technical evidence & routing context</summary>
        <p className="mt-2 text-sm leading-6 text-slate-600">Open this section when support or engineering needs topology, authority records, fencing or protocol-level evidence. Operational status and governed actions remain above.</p>
        <div className="mt-5 space-y-5">
          <A2ATopologyPanel value={detail.topology} />
          <AuthorityEvidencePanel records={[
            detail.request,
            detail.result,
            detail.cancellation,
            detail.reconciliationCase,
            detail.quarantine,
            detail.aggregation,
          ]} />
        </div>
      </details>
    </div>
  );
}
