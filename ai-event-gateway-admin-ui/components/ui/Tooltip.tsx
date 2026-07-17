'use client';

import type { ReactNode } from 'react';
import { useId } from 'react';

export interface TooltipProps {
  content: ReactNode;
  children?: ReactNode;
  label?: string;
  className?: string;
}

export function Tooltip({ content, children, label = 'ⓘ', className = '' }: Readonly<TooltipProps>) {
  const tooltipId = useId();

  return (
    <span className={`group relative inline-flex items-center ${className}`}>
      {children ?? (
        <button
          type="button"
          aria-describedby={tooltipId}
          className="inline-flex h-5 w-5 items-center justify-center rounded-full border border-slate-200 bg-white text-[11px] font-bold text-slate-500 shadow-sm transition hover:border-blue-300 hover:text-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-100"
        >
          {label}
        </button>
      )}
      <span
        id={tooltipId}
        role="tooltip"
        className="pointer-events-none absolute left-1/2 top-full z-50 mt-2 hidden w-72 -translate-x-1/2 rounded-xl border border-slate-200 bg-slate-950 px-3 py-2 text-left text-xs font-medium leading-5 text-white shadow-xl group-hover:block group-focus-within:block"
      >
        {content}
      </span>
    </span>
  );
}

export function HelpTooltip({ content, className }: Readonly<{ content: ReactNode; className?: string }>) {
  return <Tooltip content={content} className={className} />;
}
