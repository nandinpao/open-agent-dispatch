'use client';

import type { ReactNode } from 'react';

export interface AdminTabItem {
  id: string;
  label: string;
  badge?: ReactNode;
  disabled?: boolean;
}

export interface AdminTabLayoutProps {
  tabs: AdminTabItem[];
  activeTab: string;
  onTabChange: (tabId: string) => void;
  children: ReactNode;
  className?: string;
}

export function AdminTabLayout({ tabs, activeTab, onTabChange, children, className = '' }: Readonly<AdminTabLayoutProps>) {
  return (
    <section className={`rounded-3xl border border-slate-200 bg-white shadow-sm ${className}`}>
      <div className="border-b border-slate-200 px-4 pt-4">
        <div className="flex flex-wrap gap-2">
          {tabs.map((tab) => {
            const active = tab.id === activeTab;
            return (
              <button
                key={tab.id}
                type="button"
                disabled={tab.disabled}
                onClick={() => onTabChange(tab.id)}
                className={`inline-flex items-center gap-2 rounded-t-2xl border border-b-0 px-4 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${
                  active
                    ? 'border-blue-200 bg-blue-50 text-blue-800'
                    : 'border-transparent text-slate-600 hover:border-slate-200 hover:bg-slate-50'
                }`}
              >
                <span>{tab.label}</span>
                {tab.badge}
              </button>
            );
          })}
        </div>
      </div>
      <div className="p-5">{children}</div>
    </section>
  );
}
