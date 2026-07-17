'use client';

import { useMemo, useState, type ReactNode } from 'react';
import { AdminTabLayout } from '@/components/layout';

export interface TaskControlConsoleTab {
  id: string;
  label: string;
  description?: ReactNode;
  badge?: ReactNode;
  content: ReactNode;
}

export function TaskControlConsoleTabs({ tabs }: Readonly<{ tabs: TaskControlConsoleTab[] }>) {
  const firstTab = tabs[0]?.id ?? '';
  const [activeTab, setActiveTab] = useState(firstTab);
  const selected = useMemo(() => tabs.find((tab) => tab.id === activeTab) ?? tabs[0], [activeTab, tabs]);

  if (!selected) return null;

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-blue-500">Task diagnosis center</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">Task Control Console</h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            依操作目的切換 Overview、Dispatch Lifecycle、Agent Selection、Issue/Result、Troubleshooting 與 Debug；避免把派工判斷、修復與 raw diagnostics 全部攤在同一頁。
          </p>
        </div>
      </div>
      <AdminTabLayout
        className="mt-4"
        activeTab={selected.id}
        onTabChange={setActiveTab}
        tabs={tabs.map((tab) => ({ id: tab.id, label: tab.label, badge: tab.badge }))}
      >
        {selected.description ? <div className="mb-5 rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-900">{selected.description}</div> : null}
        <div className="space-y-5">{selected.content}</div>
      </AdminTabLayout>
    </section>
  );
}
