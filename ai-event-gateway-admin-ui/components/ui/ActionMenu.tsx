'use client';

import type { KeyboardEvent, ReactNode } from 'react';
import { useEffect, useId, useRef, useState } from 'react';
import { buttonClassName } from './Button';

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
  default: 'text-slate-700 hover:bg-slate-50 focus:bg-slate-50',
  primary: 'text-blue-700 hover:bg-blue-50 focus:bg-blue-50',
  warning: 'text-amber-800 hover:bg-amber-50 focus:bg-amber-50',
  danger: 'text-rose-700 hover:bg-rose-50 focus:bg-rose-50',
};

export function ActionMenu({ items, label = 'More', align = 'right', trigger, className = '' }: Readonly<ActionMenuProps>) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const itemRefs = useRef<Array<HTMLElement | null>>([]);
  const menuId = useId();
  const enabledIndexes = items.map((item, index) => item.disabled ? -1 : index).filter((index) => index >= 0);

  function focusIndex(index: number) {
    requestAnimationFrame(() => itemRefs.current[index]?.focus());
  }

  function focusFirst() {
    if (enabledIndexes.length) focusIndex(enabledIndexes[0]);
  }

  function focusLast() {
    if (enabledIndexes.length) focusIndex(enabledIndexes[enabledIndexes.length - 1]);
  }

  function openAndFocus(position: 'first' | 'last') {
    setOpen(true);
    requestAnimationFrame(() => position === 'first' ? focusFirst() : focusLast());
  }

  function closeAndRestoreFocus() {
    setOpen(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  }

  function moveFrom(currentIndex: number, direction: 1 | -1) {
    const position = enabledIndexes.indexOf(currentIndex);
    if (position < 0 || !enabledIndexes.length) return;
    const nextPosition = (position + direction + enabledIndexes.length) % enabledIndexes.length;
    focusIndex(enabledIndexes[nextPosition]);
  }

  function handleItemKeyDown(event: KeyboardEvent<HTMLElement>, index: number) {
    if (event.key === 'ArrowDown') { event.preventDefault(); moveFrom(index, 1); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); moveFrom(index, -1); }
    else if (event.key === 'Home') { event.preventDefault(); focusFirst(); }
    else if (event.key === 'End') { event.preventDefault(); focusLast(); }
    else if (event.key === 'Escape') { event.preventDefault(); closeAndRestoreFocus(); }
    else if (event.key === 'Tab') setOpen(false);
  }

  useEffect(() => {
    if (!open) return undefined;
    const handlePointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') closeAndRestoreFocus();
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const menuAlignment = align === 'right' ? 'right-0' : 'left-0';

  return (
    <div ref={containerRef} className={`relative inline-flex ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-controls={menuId}
        aria-expanded={open}
        onClick={() => { if (open) setOpen(false); else openAndFocus('first'); }}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') { event.preventDefault(); openAndFocus('first'); }
          else if (event.key === 'ArrowUp') { event.preventDefault(); openAndFocus('last'); }
        }}
        className={buttonClassName({ tone: 'secondary', size: 'sm' })}
      >
        {trigger ?? label}
        <span aria-hidden="true" className="text-xs text-slate-400">▾</span>
      </button>

      {open ? (
        <div id={menuId} role="menu" aria-label={label} className={`absolute top-full z-40 mt-2 w-64 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1 shadow-xl ${menuAlignment}`}>
          {items.length === 0 ? (
            <div className="px-3 py-2 text-xs text-slate-500">No actions available.</div>
          ) : items.map((item, index) => {
            const tone = item.tone ?? 'default';
            const baseClass = `block w-full rounded-xl px-3 py-2 text-left text-sm font-semibold outline-none transition focus:ring-2 focus:ring-blue-300 ${toneClassMap[tone]}`;
            const disabledClass = 'cursor-not-allowed opacity-50 hover:bg-transparent';
            const content = <><span>{item.label}</span>{item.description ? <span className="mt-0.5 block text-xs font-normal leading-5 text-slate-500">{item.description}</span> : null}</>;
            if (item.href && !item.disabled) {
              return <a key={item.id} ref={(node) => { itemRefs.current[index] = node; }} role="menuitem" tabIndex={-1} href={item.href} className={baseClass} onKeyDown={(event) => handleItemKeyDown(event, index)} onClick={() => setOpen(false)}>{content}</a>;
            }
            return <button key={item.id} ref={(node) => { itemRefs.current[index] = node; }} type="button" role="menuitem" tabIndex={-1} disabled={item.disabled} onKeyDown={(event) => handleItemKeyDown(event, index)} onClick={() => { if (item.disabled) return; setOpen(false); item.onSelect?.(); }} className={`${baseClass} ${item.disabled ? disabledClass : ''}`}>{content}</button>;
          })}
        </div>
      ) : null}
    </div>
  );
}
