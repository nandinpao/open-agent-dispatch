import type { ReactNode } from 'react';

export type StatusBadgeTone = 'success' | 'warning' | 'danger' | 'neutral' | 'info' | 'purple';

export interface StatusBadgeProps {
  children: ReactNode;
  tone?: StatusBadgeTone;
  title?: string;
  className?: string;
}

const toneClassMap: Record<StatusBadgeTone, string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  warning: 'border-amber-200 bg-amber-50 text-amber-700',
  danger: 'border-rose-200 bg-rose-50 text-rose-700',
  neutral: 'border-slate-200 bg-slate-100 text-slate-700',
  info: 'border-blue-200 bg-blue-50 text-blue-700',
  purple: 'border-purple-200 bg-purple-50 text-purple-700',
};

export function StatusBadge({ children, tone = 'neutral', title, className = '' }: Readonly<StatusBadgeProps>) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold ${toneClassMap[tone]} ${className}`}
    >
      {children}
    </span>
  );
}

export function statusToneFromValue(value: string | undefined | null): StatusBadgeTone {
  const normalized = (value ?? '').trim().toUpperCase();
  if (['ACTIVE', 'APPROVED', 'READY', 'ONLINE', 'SUCCESS', 'COMPLETED', 'ELIGIBLE', 'HEALTHY'].includes(normalized)) return 'success';
  if (['PENDING', 'WAITING', 'SUSPENDED', 'DEGRADED', 'WARNING', 'LIMITED', 'RETRY_WAIT'].includes(normalized)) return 'warning';
  if (['FAILED', 'ERROR', 'BLOCKED', 'REVOKED', 'EXPIRED', 'OFFLINE', 'DENIED', 'DEAD_LETTER'].includes(normalized)) return 'danger';
  if (['INFO', 'RUNNING', 'DISPATCHED', 'ASSIGNED', 'CONNECTED'].includes(normalized)) return 'info';
  return 'neutral';
}
