import { humanizeA2ACode } from '@/lib/a2a/contracts';

interface Props { value?: string | null; }

function className(value?: string | null): string {
  const normalized = String(value ?? 'UNKNOWN').toUpperCase();
  if (['COMPLETED', 'ACKNOWLEDGED', 'APPROVED', 'RELEASED', 'RESOLVED', 'NONE'].includes(normalized)) {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  }
  if (['FAILED', 'REJECTED', 'DEAD_LETTER', 'TIMED_OUT', 'QUARANTINED'].includes(normalized)) {
    return 'border-rose-200 bg-rose-50 text-rose-700';
  }
  if (normalized.includes('WAIT') || normalized.includes('PENDING') || normalized.includes('RETRY') || normalized.includes('REQUIRED')) {
    return 'border-amber-200 bg-amber-50 text-amber-700';
  }
  if (normalized.includes('RUN') || normalized.includes('DISPATCH') || normalized.includes('ASSIGN') || normalized.includes('DELIVER')) {
    return 'border-blue-200 bg-blue-50 text-blue-700';
  }
  return 'border-slate-200 bg-slate-50 text-slate-700';
}

export function A2AStatusBadge({ value }: Props) {
  return <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-black tracking-wide ${className(value)}`}>{humanizeA2ACode(value)}</span>;
}
