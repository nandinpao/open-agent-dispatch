'use client';

import type { ReactNode } from 'react';
import type { GovernedOption } from '@/lib/governance/strictSelection';
import { findLegacyValues, hasOption, normalizeGovernedCode } from '@/lib/governance/strictSelection';

const selectClass = 'mt-1 w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 disabled:bg-slate-100 disabled:text-slate-500';

export function GovernedSelect({
  label,
  value,
  options,
  onChange,
  disabled = false,
  helper,
  placeholder = 'Select governed value…',
}: Readonly<{
  label: string;
  value?: string;
  options: readonly GovernedOption[];
  onChange: (value: string) => void;
  disabled?: boolean;
  helper?: ReactNode;
  placeholder?: string;
}>) {
  const normalized = normalizeGovernedCode(value);
  const includesCurrent = !normalized || hasOption(options, normalized);
  return (
    <label className="block text-xs font-bold text-slate-600">
      {label}
      <select value={normalized} onChange={(event) => onChange(event.target.value)} disabled={disabled} className={selectClass}>
        <option value="">{placeholder}</option>
        {!includesCurrent ? <option value={normalized}>Legacy value: {normalized}</option> : null}
        {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
      {helper ? <div className="mt-1 text-xs font-normal leading-5 text-slate-500">{helper}</div> : null}
    </label>
  );
}

export function ReadonlyGovernedField({ label, value, helper }: Readonly<{ label: string; value?: string; helper?: ReactNode }>) {
  return (
    <label className="block text-xs font-bold text-slate-600">
      {label}
      <input value={value ?? ''} readOnly className="mt-1 w-full rounded-xl border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-600" />
      {helper ? <div className="mt-1 text-xs font-normal leading-5 text-slate-500">{helper}</div> : null}
    </label>
  );
}

export function LegacyValueWarning({
  label,
  values,
  options,
  message,
}: Readonly<{
  label: string;
  values: readonly string[];
  options: readonly GovernedOption[];
  message?: string;
}>) {
  const legacyValues = findLegacyValues(values, options);
  if (!legacyValues.length) return null;
  return (
    <div className="rounded-2xl border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-900">
      <div className="font-black">Legacy {label} detected</div>
      <div>{message ?? 'This value is not in the governed catalog. Keep it only for review; it should not become a new dispatch-affecting input.'}</div>
      <div className="mt-2 flex flex-wrap gap-1">
        {legacyValues.map((value) => <span key={value} className="rounded-full bg-white px-2 py-1 font-mono text-[11px] font-bold text-amber-800">{value}</span>)}
      </div>
    </div>
  );
}

export function GovernedCheckboxCards({
  title,
  values,
  options,
  onChange,
  disabled = false,
  description,
}: Readonly<{
  title: string;
  values: readonly string[];
  options: readonly GovernedOption[];
  onChange: (values: string[]) => void;
  disabled?: boolean;
  description?: string;
}>) {
  const selected = new Set(values.map(normalizeGovernedCode));
  const toggle = (value: string) => {
    if (disabled) return;
    const next = new Set(selected);
    const normalized = normalizeGovernedCode(value);
    if (next.has(normalized)) next.delete(normalized);
    else next.add(normalized);
    onChange(Array.from(next).sort());
  };
  return (
    <div className="space-y-2">
      <div>
        <div className="text-xs font-black uppercase tracking-wide text-slate-600">{title}</div>
        {description ? <div className="mt-1 text-xs leading-5 text-slate-500">{description}</div> : null}
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        {options.map((option) => {
          const checked = selected.has(normalizeGovernedCode(option.value));
          return (
            <button
              key={option.value}
              type="button"
              disabled={disabled}
              onClick={() => toggle(option.value)}
              className={`rounded-2xl border p-3 text-left text-sm transition ${checked ? 'border-blue-300 bg-blue-50 text-blue-950' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'} disabled:opacity-60`}
            >
              <div className="flex items-start gap-2">
                <span className={`mt-0.5 inline-flex h-4 w-4 items-center justify-center rounded-full border text-[10px] ${checked ? 'border-blue-500 bg-blue-600 text-white' : 'border-slate-300 bg-white text-transparent'}`}>✓</span>
                <span><span className="font-black">{option.label}</span>{option.description ? <span className="mt-1 block text-xs text-slate-500">{option.description}</span> : null}</span>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

export function CatalogStatusGuard({ status, catalogName }: Readonly<{ status?: string; catalogName: string }>) {
  const normalized = normalizeGovernedCode(status || 'ACTIVE');
  if (normalized === 'ACTIVE') return null;
  return (
    <div className="rounded-2xl border border-rose-200 bg-rose-50 p-3 text-xs leading-5 text-rose-800">
      <span className="font-black">{catalogName} is {normalized}.</span> Disabled / retired / needs-review records cannot be selected for new active governance changes.
    </div>
  );
}
