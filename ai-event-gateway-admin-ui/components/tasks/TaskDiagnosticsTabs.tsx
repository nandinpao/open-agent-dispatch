'use client';

import { useMemo, useState, type ReactNode } from 'react';

export interface TaskDiagnosticsTab {
  id: string;
  label: string;
  description: string;
  badge?: string;
  content: ReactNode;
}

export function TaskDiagnosticsTabs({ tabs }: Readonly<{ tabs: TaskDiagnosticsTab[] }>) {
  const firstTab = tabs[0]?.id ?? '';
  const [active, setActive] = useState(firstTab);
  const selected = useMemo(() => tabs.find((tab) => tab.id === active) ?? tabs[0], [active, tabs]);

  if (!selected) return null;

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-400">Engineer diagnostics</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">Timeline Event Log 與進階診斷</h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">一般使用者先看上方 Lifecycle Stepper；工程師再依需要切換 lifecycle、runtime、recovery、raw diagnostics。</p>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2 border-b border-slate-200 pb-3">
        {tabs.map((tab) => {
          const selectedTab = tab.id === selected.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActive(tab.id)}
              className={`rounded-xl px-3 py-2 text-sm font-bold transition ${selectedTab ? 'bg-slate-900 text-white shadow-sm' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
            >
              {tab.label}
              {tab.badge ? <span className={`ml-2 rounded-full px-2 py-0.5 text-[11px] ${selectedTab ? 'bg-white/20 text-white' : 'bg-white text-slate-500'}`}>{tab.badge}</span> : null}
            </button>
          );
        })}
      </div>
      <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm leading-6 text-slate-600">{selected.description}</div>
      <div className="mt-5 space-y-5">{selected.content}</div>
    </section>
  );
}
