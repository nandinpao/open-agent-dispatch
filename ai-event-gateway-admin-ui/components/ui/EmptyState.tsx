import type { ReactNode } from 'react';

export type EmptyStateTone = 'neutral' | 'info' | 'warning' | 'danger' | 'success';

export interface EmptyStateProps {
  title: string;
  description?: ReactNode;
  nextAction?: ReactNode;
  action?: ReactNode;
  tone?: EmptyStateTone;
  compact?: boolean;
  className?: string;
}

const toneClassMap: Record<EmptyStateTone, string> = {
  neutral: 'border-slate-300 bg-white text-slate-700',
  info: 'border-blue-200 bg-blue-50 text-blue-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  danger: 'border-rose-200 bg-rose-50 text-rose-900',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
};

const iconClassMap: Record<EmptyStateTone, string> = {
  neutral: 'bg-slate-100 text-slate-500',
  info: 'bg-blue-100 text-blue-700',
  warning: 'bg-amber-100 text-amber-700',
  danger: 'bg-rose-100 text-rose-700',
  success: 'bg-emerald-100 text-emerald-700',
};

export function EmptyState({
  title,
  description,
  nextAction,
  action,
  tone = 'neutral',
  compact = false,
  className = '',
}: Readonly<EmptyStateProps>) {
  return (
    <div className={`rounded-2xl border border-dashed text-center shadow-sm ${toneClassMap[tone]} ${compact ? 'p-4' : 'p-8'} ${className}`}>
      <div className={`mx-auto flex h-9 w-9 items-center justify-center rounded-full text-sm font-black ${iconClassMap[tone]}`} aria-hidden="true">
        i
      </div>
      <div className="mt-3 text-sm font-black text-slate-950">{title}</div>
      {description ? <div className="mx-auto mt-2 max-w-2xl text-sm leading-6 text-slate-600">{description}</div> : null}
      {nextAction ? <div className="mx-auto mt-3 max-w-2xl text-xs leading-5 text-slate-500">{nextAction}</div> : null}
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </div>
  );
}
