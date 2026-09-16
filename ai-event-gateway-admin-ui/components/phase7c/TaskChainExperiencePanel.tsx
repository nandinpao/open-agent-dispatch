'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { taskAdminApi } from '@/lib/api/domains/taskAdminApi';
import type { TaskChainView } from '@/lib/phase7c/taskA2aUx';

function restricted(node: TaskChainView['nodes'][number]): boolean {
  return node.taskKey === 'RESTRICTED' || node.title === 'Restricted task';
}

export function TaskChainExperiencePanel({ taskId }: Readonly<{ taskId: string }>) {
  const [value, setValue] = useState<TaskChainView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    void taskAdminApi.getTaskChain(taskId, 100).then((next) => {
      if (active) setValue(next);
    }).catch((cause) => {
      if (active) setError(cause instanceof Error ? cause.message : 'Unable to load the Task chain.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [taskId]);
  const nodes = useMemo(() => [...(value?.nodes ?? [])].sort((a, b) => a.depth - b.depth || a.taskId.localeCompare(b.taskId)), [value]);
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="Task chain graph and timeline">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><p className="text-xs font-black uppercase tracking-[0.18em] text-indigo-700">Task chain</p><h3 className="mt-1 text-lg font-black text-slate-950">Root, current, child, and restricted nodes</h3></div>
        <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-700">{nodes.length} nodes</span>
      </div>
      {loading ? <p className="mt-4 rounded-xl bg-slate-50 p-4 text-sm">Loading authorized chain…</p> : null}
      {error ? <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-900">{error}</p> : null}
      {!loading && !error && nodes.length === 0 ? <p className="mt-4 rounded-xl border border-dashed p-4 text-sm text-slate-500">No chain nodes are visible in the current resource scope.</p> : null}
      <ol className="mt-5 space-y-3">
        {nodes.map((node, index) => {
          const isRestricted = restricted(node);
          const current = node.taskId === taskId;
          return (
            <li key={`${node.taskId}:${node.version}`} className="relative pl-8">
              {index < nodes.length - 1 ? <span aria-hidden className="absolute left-[11px] top-7 h-[calc(100%+4px)] w-px bg-slate-200" /> : null}
              <span aria-hidden className={`absolute left-0 top-2 h-6 w-6 rounded-full border-4 border-white ${current ? 'bg-indigo-600' : isRestricted ? 'bg-slate-500' : 'bg-emerald-500'}`} />
              <div className={`rounded-2xl border p-4 ${current ? 'border-indigo-300 bg-indigo-50' : 'border-slate-200 bg-slate-50'}`}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-black uppercase tracking-wide text-slate-500">Depth {node.depth}{current ? ' · Current Task' : ''}</p>
                    {isRestricted ? <h4 className="mt-1 font-black text-slate-900">Restricted task</h4> : <Link href={`/tasks/${encodeURIComponent(node.taskId)}`} className="mt-1 block font-black text-indigo-700 hover:underline">{node.title || node.taskKey || node.taskId}</Link>}
                  </div>
                  <span className="rounded-full bg-white px-2.5 py-1 text-xs font-black text-slate-700">{node.status || 'UNKNOWN'}</span>
                </div>
                {isRestricted ? <p className="mt-2 text-sm text-slate-600">This node is visible only as a placeholder. Its title, owner, domain, and identifiers are not disclosed.</p> : (
                  <div className="mt-3 grid gap-2 text-xs text-slate-600 sm:grid-cols-3">
                    <span>Parent: {node.parentTaskId || 'Root'}</span><span>Owner: {node.ownerDepartmentId || 'Not provided'}</span><span>Executor domain: {node.executorDomainId || 'Not provided'}</span>
                  </div>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
