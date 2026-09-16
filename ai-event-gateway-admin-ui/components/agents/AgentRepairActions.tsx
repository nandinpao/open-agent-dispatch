'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreAgentConnectionRepairAction, CoreAgentConnectionRepairActionResult } from '@/lib/types/core';

export function RepairActionDialog({
  agentId,
  action,
  onCompleted,
}: Readonly<{
  agentId: string;
  action: CoreAgentConnectionRepairAction;
  onCompleted: (result: CoreAgentConnectionRepairActionResult) => void;
}>) {
  const [open, setOpen] = useState(false);
  const [credentialToken, setCredentialToken] = useState('');
  const [reason, setReason] = useState(action.description ?? 'Connection repair action');
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dialogRef = useDialogAccessibility(open, () => setOpen(false));
  const dialogTitleId = `agent-repair-${action.actionCode.replace(/[^A-Za-z0-9_-]/g, '-')}`;

  if (action.actionType === 'NAVIGATE') {
    return (
      <Link href={action.endpoint ?? `/agents/${encodeURIComponent(agentId)}`} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">
        {action.label ?? action.actionCode}
      </Link>
    );
  }

  const execute = async () => {
    setRunning(true);
    setError(null);
    try {
      const result = await coreAdminApi.executeAgentConnectionRepairAction(agentId, action.actionCode, {
        operatorId: 'admin-ui',
        reason,
        credentialToken: credentialToken.trim() || undefined,
        revokeExisting: true,
        enableAfterRepair: true,
      });
      onCompleted(result);
      setOpen(false);
      setCredentialToken('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Repair action failed');
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        disabled={action.enabled === false}
        className={`rounded-lg border px-3 py-2 text-xs font-black disabled:cursor-not-allowed disabled:opacity-50 ${action.highRisk ? 'border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100' : 'border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100'}`}
        title={action.disabledReason}
      >
        {action.label ?? action.actionCode}
      </button>
      {open ? (
        <div ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={dialogTitleId} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4 outline-none">
          <div className="w-full max-w-xl rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 id={dialogTitleId} className="text-base font-black text-slate-950">{action.label ?? action.actionCode}</h3>
                <p className="mt-1 text-sm leading-6 text-slate-600">{action.description}</p>
              </div>
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg border border-slate-200 px-2 py-1 text-xs font-black text-slate-500 hover:bg-slate-50">Close</button>
            </div>
            {action.disabledReason ? <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs font-bold text-amber-800">{action.disabledReason}</div> : null}
            {action.nextStep ? <div className="mt-3 rounded-xl border border-blue-200 bg-blue-50 p-3 text-xs leading-5 text-blue-800">{action.nextStep}</div> : null}
            {action.requiresCredentialToken ? (
              <label className="mt-4 block text-xs font-black uppercase tracking-wide text-slate-500">
                New credential token
                <input
                  type="password"
                  value={credentialToken}
                  onChange={(event) => setCredentialToken(event.target.value)}
                  className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                  placeholder="Paste the new runtime token"
                />
              </label>
            ) : null}
            <label className="mt-4 block text-xs font-black uppercase tracking-wide text-slate-500">
              Repair reason
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                className="mt-2 min-h-20 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
              />
            </label>
            {error ? <div className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs font-bold text-rose-700">{error}</div> : null}
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">Cancel</button>
              <button
                type="button"
                onClick={execute}
                disabled={running || action.enabled === false || (action.requiresCredentialToken && !credentialToken.trim())}
                className="rounded-lg bg-indigo-600 px-3 py-2 text-xs font-black text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {running ? 'Running…' : 'Run repair action'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

export function RepairActionsGrid({ agentId, actions, onCompleted }: Readonly<{ agentId: string; actions?: CoreAgentConnectionRepairAction[]; onCompleted: (result: CoreAgentConnectionRepairActionResult) => void }>) {
  const visibleActions = actions ?? [];
  if (!visibleActions.length) return null;
  return (
    <div className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4">
      <div className="text-sm font-black text-indigo-950">Repair Actions</div>
      <p className="mt-1 text-xs leading-5 text-indigo-800">These actions are generated by the Core backend from the latest runtime authorization failure. Run only the action that matches the verified failure reason.</p>
      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        {visibleActions.map((action) => (
          <div key={action.actionCode} className="rounded-xl border border-white/70 bg-white p-3 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-sm font-black text-slate-950">{action.label ?? action.actionCode}</div>
                {action.description ? <p className="mt-1 text-xs leading-5 text-slate-600">{action.description}</p> : null}
                {action.disabledReason ? <p className="mt-2 text-xs font-bold text-amber-700">{action.disabledReason}</p> : null}
              </div>
              {action.highRisk ? <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-black uppercase text-rose-700">High risk</span> : null}
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <RepairActionDialog agentId={agentId} action={action} onCompleted={onCompleted} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

