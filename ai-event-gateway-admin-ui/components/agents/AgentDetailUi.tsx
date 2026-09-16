'use client';

import Link from 'next/link';
import type { ReactNode } from 'react';
import { useI18n } from '@/hooks/useI18n';
import type { CoreAgentCapabilityAssignment } from '@/lib/types/core';

export type AgentDetailTab = 'overview' | 'capabilities' | 'dispatch-access' | 'issue-tracking' | 'pools' | 'work' | 'advanced';

export const buttonBaseClassName = 'rounded-lg border px-3 py-2 text-xs font-black disabled:cursor-not-allowed disabled:opacity-50';

export function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_').replace(/^_+|_+$/g, '');
}

export function normalizeLifecycleStatus(value?: string | null): string {
  return (value ?? '').trim().toUpperCase();
}

const NON_DISPATCH_TASK_ALIAS_CAPABILITIES = new Set(['INCIDENT_RESPONSE', 'INCIDENT_ESCALATION', 'INCIDENT_ANALYSIS']);

export function isOperatorRuntimeCapability(value?: string | null): boolean {
  const code = normalizeCode(value ?? '');
  return code.length > 0 && !NON_DISPATCH_TASK_ALIAS_CAPABILITIES.has(code);
}

export function activeCapabilityAssignments(assignments: CoreAgentCapabilityAssignment[]): CoreAgentCapabilityAssignment[] {
  return assignments.filter((assignment) => normalizeLifecycleStatus(assignment.status) !== 'REVOKED' && isOperatorRuntimeCapability(assignment.capabilityCode));
}

export function firstNonBlankValue(...values: Array<string | undefined | null>): string | undefined {
  return values.find((value) => typeof value === 'string' && value.trim().length > 0)?.trim();
}

export function agentReturnHref(agentId: string): string {
  return `/agents/${encodeURIComponent(agentId)}`;
}

export function managementHref(path: string, agentId: string): string {
  const params = new URLSearchParams({ returnTo: agentReturnHref(agentId), agentId });
  return `${path}?${params.toString()}`;
}

export function Panel({ title, description, children, action }: Readonly<{ title: ReactNode; description?: ReactNode; children: ReactNode; action?: ReactNode }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">{title}</h2>
          {description ? <p className="mt-1 text-sm leading-6 text-slate-500">{description}</p> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      {children}
    </section>
  );
}

export function StatCard({ label, value, tone = 'neutral' }: Readonly<{ label: string; value: ReactNode; tone?: 'neutral' | 'good' | 'warn' | 'bad' }>) {
  const toneClassName = {
    neutral: 'border-slate-200 bg-slate-50 text-slate-900',
    good: 'border-emerald-200 bg-emerald-50 text-emerald-950',
    warn: 'border-amber-200 bg-amber-50 text-amber-950',
    bad: 'border-rose-200 bg-rose-50 text-rose-950',
  }[tone];
  return (
    <div className={`rounded-2xl border px-4 py-3 ${toneClassName}`}>
      <div className="text-xs font-black uppercase tracking-wide opacity-60">{label}</div>
      <div className="mt-2 break-all text-sm font-black">{value}</div>
    </div>
  );
}

export function InlineManageLink({ href, children }: Readonly<{ href: string; children: ReactNode }>) {
  return <Link href={href} className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700 hover:bg-slate-50">{children}</Link>;
}

export function ModalShell({ title, description, children, onClose }: Readonly<{ title: string; description: string; children: ReactNode; onClose: () => void }>) {
  const { t } = useI18n();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-3xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
          <div><h2 className="text-lg font-black text-slate-950">{title}</h2><p className="mt-1 text-sm leading-6 text-slate-500">{description}</p></div>
          <button type="button" onClick={onClose} className="rounded-full border border-slate-200 px-3 py-1 text-sm font-black text-slate-500 hover:bg-slate-50" aria-label={t('agent.detail.dialog.close')}>×</button>
        </div>
        <div className="mt-5">{children}</div>
      </div>
    </div>
  );
}

export function FormTextArea({ label, value, onChange, placeholder, rows = 3 }: Readonly<{ label: string; value: string; onChange: (value: string) => void; placeholder?: string; rows?: number }>) {
  return (
    <label className="text-sm font-bold text-slate-700">
      {label}
      <textarea value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} rows={rows} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal text-slate-800 placeholder:text-slate-300" />
    </label>
  );
}

export function DialogNotice({ tone, children }: Readonly<{ tone: 'success' | 'error' | 'info'; children: ReactNode }>) {
  const className = { success: 'border-emerald-200 bg-emerald-50 text-emerald-800', error: 'border-rose-200 bg-rose-50 text-rose-800', info: 'border-blue-200 bg-blue-50 text-blue-800' }[tone];
  return <div className={`rounded-2xl border px-4 py-3 text-sm font-bold ${className}`}>{children}</div>;
}
