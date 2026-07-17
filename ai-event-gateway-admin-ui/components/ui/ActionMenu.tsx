'use client';

import type { ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';

export type ActionMenuItemTone = 'default' | 'primary' | 'warning' | 'danger';

export interface ActionMenuItem {
  id: string;
  label: string;
  description?: string;
  href?: string;
  disabled?: boolean;
  tone?: ActionMenuItemTone;
  onSelect?: () => void;
}

export interface ActionMenuProps {
  items: ActionMenuItem[];
  label?: string;
  align?: 'left' | 'right';
  trigger?: ReactNode;
  className?: string;
}

const toneClassMap: Record<ActionMenuItemTone, string> = {
  default: 'text-slate-700 hover:bg-slate-50',
  primary: 'text-blue-700 hover:bg-blue-50',
  warning: 'text-amber-700 hover:bg-amber-50',
  danger: 'text-rose-700 hover:bg-rose-50',
};

export function ActionMenu({ items, label = 'More', align = 'right', trigger, className = '' }: Readonly<ActionMenuProps>) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return undefined;

    const handlePointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const menuAlignment = align === 'right' ? 'right-0' : 'left-0';

  return (
    <div ref={containerRef} className={`relative inline-flex ${className}`}>
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-100"
      >
        {trigger ?? label}
        <span aria-hidden="true" className="text-[10px] text-slate-400">▾</span>
      </button>

      {open ? (
        <div
          role="menu"
          className={`absolute top-full z-40 mt-2 w-64 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-xl ${menuAlignment}`}
        >
          {items.length === 0 ? (
            <div className="px-3 py-2 text-xs text-slate-500">No actions available.</div>
          ) : (
            items.map((item) => {
              const tone = item.tone ?? 'default';
              const baseClass = `block w-full rounded-xl px-3 py-2 text-left text-sm font-semibold transition ${toneClassMap[tone]}`;
              const disabledClass = 'cursor-not-allowed opacity-50 hover:bg-transparent';
              const content = (
                <>
                  <span>{item.label}</span>
                  {item.description ? <span className="mt-0.5 block text-xs font-normal leading-5 text-slate-500">{item.description}</span> : null}
                </>
              );

              if (item.href && !item.disabled) {
                return (
                  <a key={item.id} role="menuitem" href={item.href} className={baseClass} onClick={() => setOpen(false)}>
                    {content}
                  </a>
                );
              }

              return (
                <button
                  key={item.id}
                  type="button"
                  role="menuitem"
                  disabled={item.disabled}
                  onClick={() => {
                    if (item.disabled) return;
                    setOpen(false);
                    item.onSelect?.();
                  }}
                  className={`${baseClass} ${item.disabled ? disabledClass : ''}`}
                >
                  {content}
                </button>
              );
            })
          )}
        </div>
      ) : null}
    </div>
  );
}
