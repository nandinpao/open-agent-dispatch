'use client';

import Link from 'next/link';
import { useState } from 'react';
import { ApiError } from '@/lib/api/client';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import type { A2AGovernedAction } from '@/lib/api/domains/a2aOperationsApi';
import {
  cancelA2ARequest,
  rejectA2ARequest,
  resolveA2AQuarantine,
  resolveA2AReconciliation,
} from '@/lib/api/domains/a2aOperationsApi';

interface Props {
  requestId: string;
  actions: A2AGovernedAction[];
  reconciliationCaseId?: string | null;
  quarantineId?: string | null;
  onCompleted: () => Promise<void> | void;
}
type DialogState = {
  action: A2AGovernedAction;
  reason: string;
  decision: 'RESOLVED' | 'ACCEPT_AS_GOVERNANCE_EVIDENCE' | 'REJECT';
} | null;

function safeActionError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'You are not eligible to perform this governed action. Resource scope and the current authority state are rechecked by the backend.';
    if (error.status === 409) return 'The delegation changed or is no longer in the required state. Refresh the evidence before continuing.';
    if (error.status === 412) return 'The request version or precondition is stale. Reload the latest approval state before retrying.';
  }
  return error instanceof Error ? error.message : 'The governed action failed.';
}

export function GovernedActionsPanel({ requestId, actions, reconciliationCaseId, quarantineId, onCompleted }: Props) {
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [dialog, setDialog] = useState<DialogState>(null);
  const dialogRef = useDialogAccessibility(Boolean(dialog), () => setDialog(null));

  function open(action: A2AGovernedAction) {
    setDialog({ action, reason: '', decision: action.code === 'RESOLVE_QUARANTINE' ? 'REJECT' : 'RESOLVED' });
    setMessage(null);
  }

  async function submit() {
    if (!dialog) return;
    const { action, reason, decision } = dialog;
    setBusy(action.code);
    setMessage(null);
    try {
      switch (action.code) {
        case 'REJECT':
          if (!reason.trim()) throw new Error('Reason is required.');
          await rejectA2ARequest(requestId, 'OPERATOR_REJECTED', reason.trim());
          break;
        case 'REQUEST_CANCELLATION':
          if (!reason.trim()) throw new Error('Reason is required.');
          await cancelA2ARequest(requestId, reason.trim());
          break;
        case 'RESOLVE_RECONCILIATION':
          if (!reconciliationCaseId) throw new Error('Reconciliation case evidence is unavailable.');
          if (!reason.trim()) throw new Error('Reason is required.');
          await resolveA2AReconciliation(reconciliationCaseId, 'RESOLVED', reason.trim());
          break;
        case 'RESOLVE_QUARANTINE':
          if (!quarantineId) throw new Error('Quarantine evidence is unavailable.');
          if (!reason.trim()) throw new Error('Reason is required.');
          await resolveA2AQuarantine(quarantineId, decision === 'ACCEPT_AS_GOVERNANCE_EVIDENCE' ? 'ACCEPT_AS_GOVERNANCE_EVIDENCE' : 'REJECT', reason.trim());
          break;
        default: throw new Error(`Unsupported governed action: ${action.code}`);
      }
      setDialog(null);
      setMessage('Governed action completed. The latest authority state has been reloaded.');
      await onCompleted();
    } catch (error) {
      setMessage(safeActionError(error));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-black uppercase tracking-[0.18em] text-slate-500">Governed actions</p>
      <h2 className="mt-1 text-lg font-black text-slate-900">Safe operator controls</h2>
      <p className="mt-2 text-sm leading-6 text-slate-600">Every mutation remains behind the canonical authority. Legacy directional requests cannot be approved into new execution. Force Complete, fencing bypass, self-approval, and direct state editing are intentionally unavailable.</p>
      <div className="mt-4 space-y-3">
        {actions.length === 0 ? <p className="rounded-xl bg-slate-50 p-3 text-sm text-slate-600">No governed action is currently available. A pending approval or state transition may be in progress.</p> : actions.map((action) => (
          <div key={action.code} className="rounded-xl border border-slate-200 p-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div><p className="text-sm font-black text-slate-900">{action.label}</p><p className="mt-1 text-xs leading-5 text-slate-600">{action.guardrail}</p><p className="mt-1 text-xs font-bold text-slate-400">Backend authorization and current resource state are checked on submission.</p></div>
              {action.method === 'GET' ? <Link href={action.endpoint} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">Open</Link> : ['REJECT', 'REQUEST_CANCELLATION', 'RESOLVE_RECONCILIATION', 'RESOLVE_QUARANTINE'].includes(action.code) ? <button type="button" disabled={busy !== null} onClick={() => open(action)} className="rounded-lg bg-indigo-700 px-3 py-2 text-xs font-black text-white disabled:opacity-50">{action.label}</button> : <span className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-black text-slate-400">Unavailable</span>}
            </div>
          </div>
        ))}
      </div>
      {message ? <p className="mt-4 rounded-xl bg-slate-100 px-3 py-2 text-sm font-bold text-slate-700" role="status">{message}</p> : null}
      {dialog ? (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/50 p-4" role="presentation">
          <div ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="a2a-governed-action-title" className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl outline-none">
            <p className="text-xs font-black uppercase tracking-[.18em] text-indigo-700">Governed action</p><h3 id="a2a-governed-action-title" className="mt-1 text-xl font-black text-slate-950">{dialog.action.label}</h3>
            <div className="mt-3 rounded-xl bg-slate-50 p-3 text-sm text-slate-700"><b>Guardrail:</b> {dialog.action.guardrail}<br /><b>Request:</b> {requestId}<br /><b>State handling:</b> 403, 409, and 412 stop the action and require fresh authority evidence.</div>
            {dialog.action.code === 'RESOLVE_QUARANTINE' ? <fieldset className="mt-4"><legend className="text-xs font-black uppercase tracking-wide text-slate-500">Decision</legend><div className="mt-2 flex gap-4 text-sm"><label><input type="radio" checked={dialog.decision === 'REJECT'} onChange={() => setDialog({ ...dialog, decision: 'REJECT' })} /> Reject</label><label><input type="radio" checked={dialog.decision === 'ACCEPT_AS_GOVERNANCE_EVIDENCE'} onChange={() => setDialog({ ...dialog, decision: 'ACCEPT_AS_GOVERNANCE_EVIDENCE' })} /> Accept as governance evidence</label></div></fieldset> : null}
            <label htmlFor="a2a-governed-reason" className="mt-4 block text-xs font-black uppercase tracking-wide text-slate-500">Audit reason</label>
            <textarea id="a2a-governed-reason" value={dialog.reason} onChange={(event) => setDialog({ ...dialog, reason: event.target.value })} placeholder="Describe the evidence reviewed and intended outcome." className="mt-2 min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" />
            <div className="mt-4 flex justify-end gap-2"><button type="button" onClick={() => setDialog(null)} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-bold">Cancel</button><button type="button" disabled={busy !== null || !dialog.reason.trim()} onClick={() => void submit()} className="rounded-lg bg-indigo-700 px-4 py-2 text-sm font-black text-white disabled:opacity-50">{busy ? 'Working…' : 'Confirm governed action'}</button></div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
