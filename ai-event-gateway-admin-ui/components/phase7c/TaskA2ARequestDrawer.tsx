'use client';

import { useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';

export function TaskA2ARequestDrawer({ taskId }: Readonly<{ taskId: string }>) {
  const [open, setOpen] = useState(false);
  const dialogRef = useDialogAccessibility(open, () => setOpen(false));
  return (
    <>
      <button type="button" className="rounded-xl border border-indigo-200 bg-white px-3 py-2 text-sm font-black text-indigo-700 hover:bg-indigo-50" onClick={() => setOpen(true)}>Delegation model</button>
      {open ? <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/50" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false); }}><div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="delegation-model-title" tabIndex={-1} className="h-full w-full max-w-xl overflow-y-auto bg-white p-6 shadow-2xl outline-none"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-black uppercase tracking-[.18em] text-indigo-700">Capability-first governed delegation</p><h2 id="delegation-model-title" className="mt-2 text-xl font-black text-slate-950">Cross-Agent execution is resolved by Core</h2><p className="mt-2 break-all text-xs text-slate-500">Source Task: {taskId}</p></div><button type="button" onClick={() => setOpen(false)} className="rounded-lg border px-3 py-2 text-sm font-black">Close</button></div><div className="mt-6 space-y-4 text-sm leading-6 text-slate-700"><p>An operator or Agent does not choose a target Domain, Agent Pool, or Agent. The requester expresses the required Capability and operation; Core performs provider discovery, authorization, selection, execution binding, and authoritative child-Task creation.</p><div className="rounded-xl border border-indigo-200 bg-indigo-50 p-4"><p className="font-black text-indigo-950">Operational view</p><p className="mt-1">Use Delegations to inspect governed delegated work, child-Task progress, cancellation state, result acceptance, and reconciliation evidence. The Legacy A2A Archive is read-only historical evidence only.</p></div><p>This Task detail surface is explanatory and does not create a directional A2A request or expose topology selectors.</p></div></div></div> : null}
    </>
  );
}
