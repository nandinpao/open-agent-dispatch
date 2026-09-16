import type { ReactNode } from 'react';

export function ErrorNotice({ message }: Readonly<{ message: string }>) {
  return message ? <div role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-800">{message}</div> : null;
}

export function SuccessNotice({ message }: Readonly<{ message: string }>) {
  return message ? <div role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">{message}</div> : null;
}

export function TenantRequired() {
  return <section className="rounded-3xl border border-amber-200 bg-amber-50 p-6 text-amber-950"><h2 className="text-lg font-black">Choose a company or business unit</h2><p className="mt-2 text-sm leading-6">Open one administration workspace before managing people, organization, responsibilities or sign-in security.</p><p className="mt-2 text-xs font-semibold text-amber-800">Instance Root can change the current workspace without creating another sign-in session.</p></section>;
}

export function Panel({ title, description, actions, children, className = '' }: Readonly<{ title: string; description?: string; actions?: ReactNode; children: ReactNode; className?: string }>) {
  return <section className={`rounded-3xl border border-slate-200 bg-white p-5 shadow-sm ${className}`}><div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div><h2 className="text-lg font-black text-slate-950">{title}</h2>{description ? <p className="mt-1 text-sm leading-6 text-slate-600">{description}</p> : null}</div>{actions ? <div className="shrink-0">{actions}</div> : null}</div><div className="mt-4">{children}</div></section>;
}

export function EmptyState({ children }: Readonly<{ children: ReactNode }>) {
  return <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-7 text-center text-sm text-slate-500">{children}</div>;
}

export function StatusPill({ value }: Readonly<{ value: string }>) {
  const normalized = value.toUpperCase();
  const className = normalized === 'ACTIVE' || normalized === 'ENABLED'
    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
    : normalized.includes('PENDING') || normalized === 'SUSPENDED'
      ? 'border-amber-200 bg-amber-50 text-amber-800'
      : 'border-slate-200 bg-slate-100 text-slate-700';
  return <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-black ${className}`}>{value || 'UNKNOWN'}</span>;
}

export function AuditReasonInput({ name = 'auditReason', value, onChange }: Readonly<{ name?: string; value?: string; onChange?: (value: string) => void }>) {
  return <textarea name={name} value={value} onChange={onChange ? event => onChange(event.target.value) : undefined} required minLength={12} placeholder="Audit reason (minimum 12 characters)" className="min-h-20 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" />;
}
