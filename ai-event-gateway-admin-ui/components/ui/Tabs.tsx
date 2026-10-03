'use client';

import type { ReactNode } from 'react';
import { useRef } from 'react';

export interface TabDefinition<T extends string> {
  id: T;
  label: ReactNode;
  description?: ReactNode;
  badge?: ReactNode;
  attentionLabel?: string;
}

export interface TabsProps<T extends string> {
  tabs: ReadonlyArray<TabDefinition<T>>;
  value: T;
  onChange: (value: T) => void;
  ariaLabel: string;
  idPrefix: string;
  className?: string;
}

export function Tabs<T extends string,>({ tabs, value, onChange, ariaLabel, idPrefix, className = '' }: Readonly<TabsProps<T>>) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  function moveFocus(index: number) {
    const tab = tabs[index];
    if (!tab) return;
    onChange(tab.id);
    requestAnimationFrame(() => refs.current[index]?.focus());
  }

  return (
    <div className={`border-b border-slate-200 ${className}`} role="tablist" aria-label={ariaLabel}>
      <div className="flex gap-1 overflow-x-auto px-1">
        {tabs.map((tab, index) => {
          const active = tab.id === value;
          return (
            <button
              key={tab.id}
              ref={(node) => { refs.current[index] = node; }}
              id={`${idPrefix}-tab-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={active}
              aria-controls={`${idPrefix}-panel-${tab.id}`}
              tabIndex={active ? 0 : -1}
              onClick={() => onChange(tab.id)}
              onKeyDown={(event) => {
                if (event.key === 'ArrowRight') { event.preventDefault(); moveFocus((index + 1) % tabs.length); }
                else if (event.key === 'ArrowLeft') { event.preventDefault(); moveFocus((index - 1 + tabs.length) % tabs.length); }
                else if (event.key === 'Home') { event.preventDefault(); moveFocus(0); }
                else if (event.key === 'End') { event.preventDefault(); moveFocus(tabs.length - 1); }
              }}
              className={`relative min-w-max border-b-2 px-4 py-3 text-left transition ${active ? 'border-slate-950 text-slate-950' : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800'}`}
            >
              <div className="flex items-center gap-2 text-sm font-black">
                {tab.label}
                {tab.badge}
                {tab.attentionLabel ? <span className="h-2 w-2 rounded-full bg-amber-500" aria-label={tab.attentionLabel} /> : null}
              </div>
              {tab.description ? <div className="mt-0.5 text-[10px] font-medium text-slate-400">{tab.description}</div> : null}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export function TabPanel({ idPrefix, tabId, active, children, className = '' }: Readonly<{ idPrefix: string; tabId: string; active: boolean; children: ReactNode; className?: string }>) {
  if (!active) return null;
  return (
    <section
      id={`${idPrefix}-panel-${tabId}`}
      role="tabpanel"
      aria-labelledby={`${idPrefix}-tab-${tabId}`}
      tabIndex={0}
      className={`outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 ${className}`}
    >
      {children}
    </section>
  );
}
