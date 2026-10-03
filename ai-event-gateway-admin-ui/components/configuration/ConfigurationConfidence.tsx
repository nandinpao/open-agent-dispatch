import type { ReactNode } from 'react';

export type ConfigurationTone = 'neutral' | 'ready' | 'warning' | 'risk' | 'info';
export type ConfigurationRisk = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface ConfigurationSummaryItem {
  label: string;
  value: string;
  detail?: string;
  tone?: ConfigurationTone;
}

const toneClasses: Record<ConfigurationTone, string> = {
  neutral: 'border-slate-200 bg-slate-50 text-slate-800',
  ready: 'border-emerald-200 bg-emerald-50 text-emerald-950',
  warning: 'border-amber-200 bg-amber-50 text-amber-950',
  risk: 'border-red-200 bg-red-50 text-red-950',
  info: 'border-blue-200 bg-blue-50 text-blue-950',
};

const riskClasses: Record<ConfigurationRisk, string> = {
  LOW: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  MEDIUM: 'border-amber-200 bg-amber-50 text-amber-900',
  HIGH: 'border-orange-200 bg-orange-50 text-orange-900',
  CRITICAL: 'border-red-200 bg-red-50 text-red-900',
};

export function ConfigurationPurposePanel({
  title,
  purpose,
  currentState,
  impact,
  validation,
  recovery,
  children,
}: Readonly<{
  title: string;
  purpose: string;
  currentState: ReactNode;
  impact: ReactNode;
  validation: ReactNode;
  recovery: ReactNode;
  children?: ReactNode;
}>) {
  return (
    <section className="rounded-2xl border border-blue-200 bg-blue-50 p-5" aria-labelledby="configuration-purpose-title">
      <div className="text-xs font-black uppercase tracking-wide text-blue-700">Configuration purpose</div>
      <h2 id="configuration-purpose-title" className="mt-1 text-xl font-black text-blue-950">{title}</h2>
      <p className="mt-2 max-w-5xl text-sm leading-6 text-blue-900">{purpose}</p>
      <div className="mt-4 grid gap-3 lg:grid-cols-4">
        <PurposeCell title="Current state">{currentState}</PurposeCell>
        <PurposeCell title="What changes can affect">{impact}</PurposeCell>
        <PurposeCell title="How to validate safely">{validation}</PurposeCell>
        <PurposeCell title="How to recover">{recovery}</PurposeCell>
      </div>
      {children ? <div className="mt-4">{children}</div> : null}
    </section>
  );
}

function PurposeCell({ title, children }: Readonly<{ title: string; children: ReactNode }>) {
  return (
    <div className="rounded-xl border border-blue-200 bg-white/90 p-4">
      <div className="text-xs font-black uppercase tracking-wide text-blue-700">{title}</div>
      <div className="mt-2 text-sm leading-6 text-slate-700">{children}</div>
    </div>
  );
}

export function ConfigurationJourney({
  steps,
}: Readonly<{
  steps: Array<{ title: string; detail: string; state?: 'current' | 'complete' | 'upcoming' }>;
}>) {
  return (
    <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-5" aria-label="Recommended configuration journey">
      {steps.map((step, index) => {
        const state = step.state ?? 'upcoming';
        const style = state === 'complete'
          ? 'border-emerald-200 bg-emerald-50'
          : state === 'current'
            ? 'border-blue-300 bg-blue-100'
            : 'border-slate-200 bg-white';
        return (
          <div key={`${index}-${step.title}`} className={`rounded-xl border p-3 ${style}`}>
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Step {index + 1}</div>
            <div className="mt-1 text-sm font-black text-slate-950">{step.title}</div>
            <div className="mt-1 text-xs leading-5 text-slate-600">{step.detail}</div>
          </div>
        );
      })}
    </div>
  );
}

export function ConfigurationImpactPreview({
  title = 'Before you save',
  risk,
  summary,
  items,
  safetyNote,
  children,
}: Readonly<{
  title?: string;
  risk: ConfigurationRisk;
  summary: string;
  items: ConfigurationSummaryItem[];
  safetyNote?: string;
  children?: ReactNode;
}>) {
  return (
    <section className="rounded-xl border border-slate-200 bg-slate-50 p-4" aria-label={title}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Change impact</div>
          <h4 className="mt-1 font-black text-slate-950">{title}</h4>
          <p className="mt-1 text-sm leading-6 text-slate-600">{summary}</p>
        </div>
        <span className={`rounded-full border px-3 py-1 text-xs font-black ${riskClasses[risk]}`}>{risk} RISK</span>
      </div>
      <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {items.map((item) => <ConfigurationSummaryCard key={`${item.label}-${item.value}`} item={item} />)}
      </div>
      {safetyNote ? <p className="mt-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold leading-5 text-slate-600">{safetyNote}</p> : null}
      {children ? <div className="mt-3">{children}</div> : null}
    </section>
  );
}

function ConfigurationSummaryCard({ item }: Readonly<{ item: ConfigurationSummaryItem }>) {
  const tone = item.tone ?? 'neutral';
  return (
    <div className={`rounded-lg border p-3 ${toneClasses[tone]}`}>
      <div className="text-[11px] font-black uppercase tracking-wide opacity-70">{item.label}</div>
      <div className="mt-1 break-words text-sm font-black">{item.value}</div>
      {item.detail ? <div className="mt-1 text-xs leading-5 opacity-80">{item.detail}</div> : null}
    </div>
  );
}

export function ConfigurationValidationPanel({
  title = 'Safe validation',
  status,
  summary,
  checks,
  safetyNote,
  action,
}: Readonly<{
  title?: string;
  status: 'NOT_RUN' | 'RUNNING' | 'PASSED' | 'FAILED' | 'PARTIAL';
  summary: string;
  checks?: Array<{ label: string; value: string; passed?: boolean }>;
  safetyNote: string;
  action?: ReactNode;
}>) {
  const tone: ConfigurationTone = status === 'PASSED' ? 'ready' : status === 'FAILED' ? 'risk' : status === 'PARTIAL' ? 'warning' : 'info';
  return (
    <section className={`rounded-xl border p-4 ${toneClasses[tone]}`} aria-live="polite">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs font-black uppercase tracking-wide opacity-70">Validation</div>
          <h4 className="mt-1 font-black">{title}</h4>
          <p className="mt-1 text-sm leading-6 opacity-90">{summary}</p>
        </div>
        <span className="rounded-full border border-current/20 bg-white/70 px-3 py-1 text-xs font-black">{status.replace('_', ' ')}</span>
      </div>
      {checks?.length ? (
        <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
          {checks.map((check) => {
            const checkStyle = check.passed === true
              ? 'border-emerald-200 bg-emerald-50 text-emerald-950'
              : check.passed === false
                ? 'border-red-200 bg-red-50 text-red-950'
                : 'border-current/15 bg-white/75';
            return (
              <div key={`${check.label}-${check.value}`} className={`rounded-lg border p-3 text-xs ${checkStyle}`}>
                <div className="font-black">{check.label}</div>
                <div className="mt-1 break-words leading-5 opacity-85">{check.value}</div>
              </div>
            );
          })}
        </div>
      ) : null}
      <p className="mt-3 rounded-lg border border-current/15 bg-white/75 px-3 py-2 text-xs font-semibold leading-5">{safetyNote}</p>
      {action ? <div className="mt-3">{action}</div> : null}
    </section>
  );
}



export type GovernedConfigurationState = 'UNSAVED' | 'DRAFT' | 'VALIDATING' | 'VALIDATED' | 'ACTIVE' | 'SUPERSEDED' | 'FAILED';

export interface ConfigurationLifecycleStep {
  key: GovernedConfigurationState;
  label: string;
  detail: string;
  state: 'complete' | 'current' | 'upcoming' | 'blocked';
}

const lifecycleStateClasses: Record<ConfigurationLifecycleStep['state'], string> = {
  complete: 'border-emerald-200 bg-emerald-50 text-emerald-950',
  current: 'border-blue-300 bg-blue-50 text-blue-950 ring-2 ring-blue-100',
  upcoming: 'border-slate-200 bg-white text-slate-700',
  blocked: 'border-red-200 bg-red-50 text-red-950',
};

export function ConfigurationLifecyclePanel({
  title = 'Governed configuration lifecycle',
  state,
  summary,
  steps,
  activeVersion,
  workingVersion,
  children,
}: Readonly<{
  title?: string;
  state: GovernedConfigurationState;
  summary: string;
  steps: ConfigurationLifecycleStep[];
  activeVersion?: string;
  workingVersion?: string;
  children?: ReactNode;
}>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5" aria-label={title}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Configuration lifecycle</div>
          <h4 className="mt-1 text-lg font-black text-slate-950">{title}</h4>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">{summary}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs font-black">
          {activeVersion ? <span className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-emerald-900">Active {activeVersion}</span> : null}
          {workingVersion ? <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-blue-900">Working {workingVersion}</span> : null}
          <span className="rounded-full border border-slate-300 bg-slate-100 px-3 py-1 text-slate-800">{state}</span>
        </div>
      </div>
      <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-5">
        {steps.map((step, index) => (
          <div key={`${step.key}-${index}`} className={`rounded-xl border p-3 ${lifecycleStateClasses[step.state]}`}>
            <div className="flex items-center justify-between gap-2">
              <div className="text-[11px] font-black uppercase tracking-wide opacity-70">{index + 1}. {step.label}</div>
              <span aria-hidden="true">{step.state === 'complete' ? '✓' : step.state === 'blocked' ? '!' : step.state === 'current' ? '●' : '○'}</span>
            </div>
            <div className="mt-1 text-xs leading-5 opacity-85">{step.detail}</div>
          </div>
        ))}
      </div>
      {children ? <div className="mt-4">{children}</div> : null}
    </section>
  );
}

export function ConfigurationLifecycleBoundaryNotice({
  title,
  governed,
  directSave,
}: Readonly<{
  title?: string;
  governed: ReactNode;
  directSave: ReactNode;
}>) {
  return (
    <section className="rounded-xl border border-amber-200 bg-amber-50 p-4" aria-label={title ?? 'Configuration lifecycle boundary'}>
      <div className="text-xs font-black uppercase tracking-wide text-amber-700">Lifecycle boundary</div>
      <h4 className="mt-1 font-black text-amber-950">{title ?? 'Not every setting has the same activation model'}</h4>
      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        <div className="rounded-lg border border-emerald-200 bg-white p-3 text-sm leading-6 text-slate-700"><div className="font-black text-emerald-900">Governed publish</div><div className="mt-1">{governed}</div></div>
        <div className="rounded-lg border border-amber-200 bg-white p-3 text-sm leading-6 text-slate-700"><div className="font-black text-amber-900">Direct save</div><div className="mt-1">{directSave}</div></div>
      </div>
    </section>
  );
}

export function ConfigurationOperationOutcome({
  outcome,
  title,
  message,
  safetyNote,
  details,
}: Readonly<{
  outcome: 'VALIDATION_FAILED' | 'APPLY_FAILED' | 'APPLIED' | 'ACTIVE_NEEDS_ATTENTION' | 'INFO';
  title: string;
  message: string;
  safetyNote: string;
  details?: ReactNode;
}>) {
  const style = outcome === 'APPLIED'
    ? toneClasses.ready
    : outcome === 'ACTIVE_NEEDS_ATTENTION' || outcome === 'APPLY_FAILED' || outcome === 'VALIDATION_FAILED'
      ? toneClasses.risk
      : toneClasses.info;
  const label = outcome === 'VALIDATION_FAILED'
    ? 'Validation failed · production unchanged'
    : outcome === 'APPLY_FAILED'
      ? 'Apply failed · confirm active state before retrying'
      : outcome === 'ACTIVE_NEEDS_ATTENTION'
        ? 'Applied · post-apply verification needs attention'
        : outcome === 'APPLIED'
          ? 'Applied and verified'
          : 'Configuration information';
  return (
    <div role={outcome === 'INFO' || outcome === 'APPLIED' ? 'status' : 'alert'} className={`rounded-xl border p-4 ${style}`}>
      <div className="text-xs font-black uppercase tracking-wide opacity-70">{label}</div>
      <div className="mt-1 font-black">{title}</div>
      <div className="mt-1 text-sm leading-6">{message}</div>
      {details ? <div className="mt-3">{details}</div> : null}
      <div className="mt-3 rounded-lg border border-current/15 bg-white/75 px-3 py-2 text-xs font-semibold leading-5">{safetyNote}</div>
    </div>
  );
}

export function ConfigurationFeedback({
  tone,
  title,
  message,
  safetyNote,
}: Readonly<{
  tone: 'success' | 'error' | 'info';
  title: string;
  message: string;
  safetyNote?: string;
}>) {
  const style = tone === 'success'
    ? toneClasses.ready
    : tone === 'error'
      ? toneClasses.risk
      : toneClasses.info;
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-xl border p-4 ${style}`}>
      <div className="font-black">{title}</div>
      <div className="mt-1 text-sm leading-6">{message}</div>
      {safetyNote ? <div className="mt-2 rounded-lg border border-current/15 bg-white/70 px-3 py-2 text-xs font-semibold leading-5">{safetyNote}</div> : null}
    </div>
  );
}
