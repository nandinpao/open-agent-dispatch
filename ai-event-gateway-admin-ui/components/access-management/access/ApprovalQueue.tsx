'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { RbacCriticalApproval } from '@/lib/iam/types';
import { EmptyState, ErrorNotice, SuccessNotice } from '../ui';
import { AuditReasonSelector, HumanStatus, SelectField, isAuditReasonValid } from '../shared/beginnerUi';
import { WorkspaceModal } from '../shared/workspaceUi';

export function ApprovalQueue({ tenantId, canApprove, refreshKey, onChanged }: Readonly<{ tenantId: string; canApprove: boolean; refreshKey: number; onChanged: () => void }>) {
  const { user } = useAuth();
  const [items, setItems] = useState<RbacCriticalApproval[]>([]);
  const [status, setStatus] = useState('PENDING');
  const [selected, setSelected] = useState<RbacCriticalApproval | null>(null);
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [reason, setReason] = useState('Access review correction');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setItems(await accessManagementApi.rbacApprovals(tenantId, status, 250)); }
    catch (cause) { setError(formatIamError(cause, 'Unable to load the access approval queue.')); }
    finally { setLoading(false); }
  }, [status, tenantId]);
  useEffect(() => { void load(); }, [load, refreshKey]);

  const pendingCount = useMemo(() => items.filter((item) => item.status === 'PENDING').length, [items]);

  async function decide() {
    if (!selected) return;
    if (selected.requesterId === user?.userId) { setError('The requester cannot approve their own access change. A different authorized person is required.'); return; }
    if (!isAuditReasonValid(reason, 'HIGH_RISK')) { setError('Enter the specific justification for this independent decision.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      if (decision === 'APPROVE') await accessManagementApi.approveRbacChange(tenantId, selected.approvalId, reason);
      else await accessManagementApi.rejectRbacChange(tenantId, selected.approvalId, reason);
      setNotice(decision === 'APPROVE' ? 'The high-risk access request was approved.' : 'The high-risk access request was rejected.');
      setSelected(null); await load(); onChanged();
    } catch (cause) { setError(formatIamError(cause, 'Unable to record the approval decision.')); }
    finally { setBusy(false); }
  }

  return (
    <div className="space-y-5">
      <ErrorNotice message={error} /><SuccessNotice message={notice} />
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div><p className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Two-person control</p><h2 className="mt-1 text-lg font-black text-slate-950">Approval queue</h2><p className="mt-2 text-sm text-slate-600">High-risk access and self-escalation-sensitive changes require an independent authorized decision.</p></div>
          <div className="min-w-48"><label htmlFor="approval-status" className="mb-1.5 block text-sm font-black text-slate-800">Status</label><SelectField id="approval-status" value={status} onChange={setStatus} options={[{ value: 'PENDING', label: 'Pending' }, { value: 'APPROVED', label: 'Approved' }, { value: 'REJECTED', label: 'Rejected' }, { value: 'CONSUMED', label: 'Used' }, { value: 'EXPIRED', label: 'Expired' }]} placeholder="All statuses" /></div>
        </div>
        {status === 'PENDING' ? <p className="mt-4 text-sm font-black text-amber-900">{pendingCount} request{pendingCount === 1 ? '' : 's'} awaiting an independent decision.</p> : null}
      </section>

      {loading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Loading approval requests…</div> : null}
      {!loading && !items.length ? <EmptyState>No approval request matches this status.</EmptyState> : null}
      <div className="space-y-3">
        {items.map((approval) => {
          const selfApproval = approval.requesterId === user?.userId;
          return <article key={approval.approvalId} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-black text-slate-950">{operationLabel(approval.operation)}</p><p className="mt-1 text-sm text-slate-700">Requested by {approval.requesterId}</p><p className="mt-1 text-xs text-slate-500">Expires {new Date(approval.expiresAt).toLocaleString()}</p></div><HumanStatus value={approval.status} /></div>
            {selfApproval && approval.status === 'PENDING' ? <div className="mt-3 rounded-2xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">You requested this change. Another authorized person must review it.</div> : null}
            {approval.decisionReason ? <p className="mt-3 rounded-2xl bg-slate-50 p-3 text-sm text-slate-700">Decision reason: {approval.decisionReason}</p> : null}
            <details className="mt-3 rounded-2xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Technical evidence</summary><dl className="mt-2 grid gap-1 sm:grid-cols-2"><div><dt className="font-bold">Approval ID</dt><dd className="break-all">{approval.approvalId}</dd></div><div><dt className="font-bold">Request hash</dt><dd className="break-all">{approval.requestHash}</dd></div><div><dt className="font-bold">Target</dt><dd>{approval.targetType}:{approval.targetId}</dd></div><div><dt className="font-bold">Approver</dt><dd>{approval.approverId || 'Not decided'}</dd></div></dl></details>
            {approval.status === 'PENDING' && canApprove ? <button type="button" disabled={selfApproval} onClick={() => { setSelected(approval); setDecision('APPROVE'); setReason(''); }} className="mt-4 rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Review request</button> : null}
          </article>;
        })}
      </div>

      {selected ? <WorkspaceModal open title="Review high-risk access request" onClose={() => setSelected(null)}><div className="space-y-4"><div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700"><b>{operationLabel(selected.operation)}</b><br />Requested by {selected.requesterId}. The requester and approver must be different identities.</div><div><label htmlFor="approval-decision" className="mb-1.5 block text-sm font-black text-slate-800">Decision</label><SelectField id="approval-decision" value={decision} onChange={(value) => setDecision(value as 'APPROVE' | 'REJECT')} options={[{ value: 'APPROVE', label: 'Approve' }, { value: 'REJECT', label: 'Reject' }]} required /></div><AuditReasonSelector idPrefix="approval-decision" value={reason} onChange={setReason} tier="HIGH_RISK" /><button type="button" disabled={busy || !isAuditReasonValid(reason, 'HIGH_RISK')} onClick={() => { void decide(); }} className="w-full rounded-xl bg-slate-950 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">Record decision</button></div></WorkspaceModal> : null}
    </div>
  );
}
function operationLabel(operation: string): string { return operation.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (value) => value.toUpperCase()); }
