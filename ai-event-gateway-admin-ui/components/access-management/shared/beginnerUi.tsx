'use client';

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

export interface SelectOption {
  value: string;
  label: string;
  description?: string;
  disabled?: boolean;
}

export function FieldLabel({
  htmlFor,
  label,
  help,
  required = false,
  children,
}: Readonly<{
  htmlFor: string;
  label: string;
  help?: string;
  required?: boolean;
  children: ReactNode;
}>) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-black text-slate-800">
        {label}
        {required ? <span className="ml-1 text-rose-700" aria-hidden="true">*</span> : null}
      </label>
      {help ? <p id={`${htmlFor}-help`} className="text-xs leading-5 text-slate-500">{help}</p> : null}
      {children}
    </div>
  );
}

export function SelectField({
  id,
  value,
  onChange,
  options,
  placeholder = 'Select an option',
  disabled = false,
  required = false,
  describedBy,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  describedBy?: string;
}>) {
  return (
    <select
      id={id}
      value={value}
      disabled={disabled}
      required={required}
      aria-describedby={describedBy}
      onChange={(event) => onChange(event.target.value)}
      className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-semibold text-slate-900 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-100 disabled:text-slate-500"
    >
      <option value="">{placeholder}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value} disabled={option.disabled}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export function SearchSelectField({
  id,
  value,
  onChange,
  options,
  placeholder = 'Search and select',
  disabled = false,
  required = false,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}>) {
  const selected = options.find((option) => option.value === value);
  const [query, setQuery] = useState(selected?.label ?? '');
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setQuery(selected?.label ?? '');
  }, [selected?.label, value]);

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term || selected?.label === query) return options.slice(0, 50);
    return options.filter((option) => `${option.label} ${option.description ?? ''}`.toLowerCase().includes(term)).slice(0, 50);
  }, [options, query, selected?.label]);

  return (
    <div className="relative">
      <input
        id={id}
        type="search"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={`${id}-options`}
        value={query}
        disabled={disabled}
        required={required && !value}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onChange={(event) => {
          setQuery(event.target.value);
          if (value) onChange('');
          setOpen(true);
        }}
        onBlur={() => {
          window.setTimeout(() => {
            setOpen(false);
            setQuery(options.find((option) => option.value === value)?.label ?? '');
          }, 120);
        }}
        className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-semibold text-slate-900 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-100 disabled:text-slate-500"
      />
      {open && !disabled ? (
        <div id={`${id}-options`} role="listbox" className="absolute z-30 mt-1 max-h-64 w-full overflow-y-auto rounded-2xl border border-slate-200 bg-white p-1 shadow-xl">
          {filtered.map((option) => (
            <button
              key={option.value}
              type="button"
              role="option"
              aria-selected={option.value === value}
              disabled={option.disabled}
              onMouseDown={(event: { preventDefault: () => void }) => event.preventDefault()}
              onClick={() => {
                onChange(option.value);
                setQuery(option.label);
                setOpen(false);
              }}
              className={`block w-full rounded-xl px-3 py-2 text-left disabled:opacity-50 ${option.value === value ? 'bg-blue-50 text-blue-950' : 'hover:bg-slate-50'}`}
            >
              <span className="block text-sm font-black">{option.label}</span>
              {option.description ? <span className="mt-0.5 block text-xs text-slate-500">{option.description}</span> : null}
            </button>
          ))}
          {!filtered.length ? <p className="p-3 text-sm text-slate-500">No matching option.</p> : null}
        </div>
      ) : null}
    </div>
  );
}

export interface AsyncSelectPage {
  options: SelectOption[];
  nextCursor?: string;
}

/**
 * Server-backed entity picker for enterprise-scale People / Department / Group catalogs.
 * The browser never needs to preload the full catalog. `selectedOption` hydrates an
 * already-selected identifier even when it is not present in the first search page.
 */
export function AsyncSearchSelectField({
  id,
  value,
  onChange,
  loadOptions,
  selectedOption,
  placeholder = 'Search and select',
  disabled = false,
  required = false,
  emptyMessage = 'No matching option.',
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string, option?: SelectOption) => void;
  loadOptions: (query: string, cursor?: string) => Promise<AsyncSelectPage>;
  selectedOption?: SelectOption | null;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  emptyMessage?: string;
}>) {
  const [query, setQuery] = useState(selectedOption?.label ?? '');
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<SelectOption[]>(selectedOption ? [selectedOption] : []);
  const [nextCursor, setNextCursor] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const requestSequence = useRef(0);

  useEffect(() => {
    if (value && selectedOption?.value === value) setQuery(selectedOption.label);
    if (!value) setQuery('');
  }, [selectedOption?.label, selectedOption?.value, value]);

  useEffect(() => {
    if (!open || disabled) return undefined;
    const sequence = ++requestSequence.current;
    const timer = window.setTimeout(() => {
      setLoading(true);
      void loadOptions(query.trim(), undefined)
        .then((page) => {
          if (requestSequence.current !== sequence) return;
          const merged = selectedOption && value
            ? [selectedOption, ...page.options.filter((option) => option.value !== selectedOption.value)]
            : page.options;
          setOptions(merged);
          setNextCursor(page.nextCursor);
        })
        .catch(() => {
          if (requestSequence.current === sequence) {
            setOptions(selectedOption && value ? [selectedOption] : []);
            setNextCursor(undefined);
          }
        })
        .finally(() => { if (requestSequence.current === sequence) setLoading(false); });
    }, 250);
    return () => window.clearTimeout(timer);
  }, [disabled, loadOptions, open, query, selectedOption, value]);

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await loadOptions(query.trim(), nextCursor);
      setOptions((current) => {
        const seen = new Set(current.map((option) => option.value));
        return [...current, ...page.options.filter((option) => !seen.has(option.value))];
      });
      setNextCursor(page.nextCursor);
    } finally { setLoadingMore(false); }
  }

  return (
    <div className="relative">
      <input
        id={id}
        type="search"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={`${id}-async-options`}
        value={query}
        disabled={disabled}
        required={required && !value}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onChange={(event) => {
          setQuery(event.target.value);
          if (value) onChange('');
          setOpen(true);
        }}
        onBlur={() => {
          window.setTimeout(() => {
            setOpen(false);
            if (value) setQuery(selectedOption?.value === value ? selectedOption.label : options.find((option) => option.value === value)?.label ?? value);
          }, 150);
        }}
        className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-semibold text-slate-900 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-100 disabled:text-slate-500"
      />
      {open && !disabled ? (
        <div id={`${id}-async-options`} role="listbox" className="absolute z-50 mt-1 max-h-72 w-full overflow-y-auto rounded-2xl border border-slate-200 bg-white p-1 shadow-xl">
          {loading ? <p className="p-3 text-sm font-semibold text-slate-500">Searching…</p> : null}
          {!loading && options.map((option) => (
            <button
              key={option.value}
              type="button"
              role="option"
              aria-selected={option.value === value}
              disabled={option.disabled}
              onMouseDown={(event: { preventDefault: () => void }) => event.preventDefault()}
              onClick={() => {
                onChange(option.value, option);
                setQuery(option.label);
                setOpen(false);
              }}
              className={`block w-full rounded-xl px-3 py-2 text-left disabled:opacity-50 ${option.value === value ? 'bg-blue-50 text-blue-950' : 'hover:bg-slate-50'}`}
            >
              <span className="block text-sm font-black">{option.label}</span>
              {option.description ? <span className="mt-0.5 block text-xs text-slate-500">{option.description}</span> : null}
            </button>
          ))}
          {!loading && !options.length ? <p className="p-3 text-sm text-slate-500">{emptyMessage}</p> : null}
          {!loading && nextCursor ? (
            <button type="button" onMouseDown={(event) => event.preventDefault()} onClick={() => { void loadMore(); }} disabled={loadingMore} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-blue-700 hover:bg-blue-50 disabled:text-slate-400">
              {loadingMore ? 'Loading more…' : 'Load more'}
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}


export function PopupMultiSelectField({
  id,
  values,
  onChange,
  options,
  placeholder = 'Select one or more options',
  disabled = false,
  emptyMessage = 'No matching options.',
}: Readonly<{
  id: string;
  values: string[];
  onChange: (values: string[]) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  emptyMessage?: string;
}>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const selectedSet = useMemo(() => new Set(values), [values]);
  const selectedOptions = useMemo(
    () => values.map((value) => options.find((option) => option.value === value) ?? { value, label: value }),
    [options, values],
  );
  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    return options
      .filter((option) => !term || `${option.label} ${option.description ?? ''} ${option.value}`.toLowerCase().includes(term))
      .slice(0, 80);
  }, [options, query]);

  useEffect(() => {
    if (!open) return undefined;
    const closeOutside = (event: MouseEvent) => { if (!containerRef.current?.contains(event.target as Node)) setOpen(false); };
    const closeEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', closeOutside);
    document.addEventListener('keydown', closeEscape);
    return () => { document.removeEventListener('mousedown', closeOutside); document.removeEventListener('keydown', closeEscape); };
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <button
        id={id}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className="flex min-h-11 w-full items-center justify-between gap-3 rounded-xl border border-slate-300 bg-white px-3 py-2 text-left text-sm font-semibold text-slate-900 outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-100 disabled:text-slate-500"
      >
        <span className={selectedOptions.length ? 'text-slate-900' : 'text-slate-400'}>
          {selectedOptions.length === 0
            ? placeholder
            : selectedOptions.length <= 2
              ? selectedOptions.map((option) => option.label).join(', ')
              : `${selectedOptions.slice(0, 2).map((option) => option.label).join(', ')} +${selectedOptions.length - 2}`}
        </span>
        <span aria-hidden="true" className="text-slate-400">▾</span>
      </button>
      {open && !disabled ? (
        <div className="absolute z-40 mt-1 w-full rounded-2xl sm:min-w-[20rem] border border-slate-200 bg-white p-2 shadow-2xl">
          <div className="flex items-center gap-2 border-b border-slate-100 pb-2">
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search options"
              autoFocus
              className="min-w-0 flex-1 rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
            />
            {values.length ? (
              <button type="button" onClick={() => onChange([])} className="rounded-lg px-2 py-1 text-xs font-black text-slate-500 hover:bg-slate-100">Clear</button>
            ) : null}
            <button type="button" onClick={() => setOpen(false)} className="rounded-lg px-2 py-1 text-xs font-black text-blue-700 hover:bg-blue-50">Done</button>
          </div>
          <div id={`${id}-options`} role="listbox" aria-multiselectable="true" className="mt-2 max-h-72 overflow-y-auto">
            {filtered.map((option) => {
              const selected = selectedSet.has(option.value);
              return (
                <label key={option.value} className={`flex cursor-pointer gap-3 rounded-xl px-3 py-2 ${selected ? 'bg-blue-50' : 'hover:bg-slate-50'} ${option.disabled ? 'cursor-not-allowed opacity-50' : ''}`}>
                  <input
                    type="checkbox"
                    checked={selected}
                    disabled={option.disabled}
                    onChange={() => onChange(selected ? values.filter((value) => value !== option.value) : [...values, option.value])}
                    className="mt-1 size-4 rounded border-slate-300"
                  />
                  <span className="min-w-0">
                    <span className="block text-sm font-black text-slate-900">{option.label}</span>
                    {option.description ? <span className="mt-0.5 block text-xs leading-5 text-slate-500">{option.description}</span> : null}
                  </span>
                </label>
              );
            })}
            {!filtered.length ? <p className="p-3 text-sm text-slate-500">{emptyMessage}</p> : null}
          </div>
        </div>
      ) : null}
      {selectedOptions.length ? (
        <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Selected values">
          {selectedOptions.slice(0, 8).map((option) => (
            <span key={option.value} className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{option.label}</span>
          ))}
          {selectedOptions.length > 8 ? <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-500">+{selectedOptions.length - 8} more</span> : null}
        </div>
      ) : null}
    </div>
  );
}


export interface HierarchySelectOption extends SelectOption {
  depth?: number;
}

export function HierarchySelectField({
  id,
  value,
  onChange,
  options,
  placeholder = 'Search hierarchy',
  disabled = false,
  required = false,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  options: HierarchySelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}>) {
  const decorated = options.map((option) => ({
    ...option,
    label: `${option.depth ? `${'— '.repeat(Math.min(option.depth, 5))}` : ''}${option.label}`,
  }));
  return (
    <SearchSelectField
      id={id}
      value={value}
      onChange={onChange}
      options={decorated}
      placeholder={placeholder}
      disabled={disabled}
      required={required}
    />
  );
}

export function SearchField({
  id,
  value,
  onChange,
  placeholder,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}>) {
  return (
    <input
      id={id}
      type="search"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
    />
  );
}

export function MultiSelectCards({
  options,
  selected,
  onChange,
  emptyMessage,
}: Readonly<{
  options: SelectOption[];
  selected: string[];
  onChange: (values: string[]) => void;
  emptyMessage: string;
}>) {
  if (options.length === 0) {
    return <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5 text-sm text-slate-500">{emptyMessage}</div>;
  }
  const selectedSet = new Set(selected);
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {options.map((option) => {
        const checked = selectedSet.has(option.value);
        return (
          <label
            key={option.value}
            className={`flex cursor-pointer gap-3 rounded-2xl border p-3 transition ${checked ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-blue-300'}`}
          >
            <input
              type="checkbox"
              checked={checked}
              disabled={option.disabled}
              onChange={() => {
                const next = checked
                  ? selected.filter((value) => value !== option.value)
                  : [...selected, option.value];
                onChange(next);
              }}
              className="mt-1 size-4 rounded border-slate-300"
            />
            <span>
              <span className="block text-sm font-black text-slate-900">{option.label}</span>
              {option.description ? <span className="mt-1 block text-xs leading-5 text-slate-500">{option.description}</span> : null}
            </span>
          </label>
        );
      })}
    </div>
  );
}

export interface WizardStepDefinition {
  key: string;
  label: string;
  description: string;
}

export function WizardProgress({
  steps,
  currentIndex,
}: Readonly<{
  steps: WizardStepDefinition[];
  currentIndex: number;
}>) {
  return (
    <ol className={`grid gap-2 md:grid-cols-3 ${steps.length <= 5 ? 'xl:grid-cols-5' : 'xl:grid-cols-6'}`} aria-label="Wizard progress">
      {steps.map((step, index) => {
        const complete = index < currentIndex;
        const active = index === currentIndex;
        return (
          <li
            key={step.key}
            aria-current={active ? 'step' : undefined}
            className={`rounded-2xl border p-3 ${active ? 'border-blue-500 bg-blue-50' : complete ? 'border-emerald-300 bg-emerald-50' : 'border-slate-200 bg-slate-50'}`}
          >
            <div className="flex items-center gap-2">
              <span className={`flex size-7 items-center justify-center rounded-full text-xs font-black ${active ? 'bg-blue-700 text-white' : complete ? 'bg-emerald-700 text-white' : 'bg-slate-200 text-slate-700'}`}>
                {complete ? '✓' : index + 1}
              </span>
              <span className="text-sm font-black text-slate-900">{step.label}</span>
            </div>
            <p className="mt-2 text-xs leading-5 text-slate-500">{step.description}</p>
          </li>
        );
      })}
    </ol>
  );
}

export function ContextLink({
  href,
  children,
}: Readonly<{
  href: string;
  children: ReactNode;
}>) {
  return (
    <a href={href} className="inline-flex items-center rounded-lg bg-blue-50 px-2.5 py-1 text-xs font-black text-blue-800 hover:bg-blue-100">
      {children}
      <span aria-hidden="true" className="ml-1">→</span>
    </a>
  );
}

export function HumanStatus({ value }: Readonly<{ value: string }>) {
  const normalized = value.toUpperCase();
  const style = normalized === 'ACTIVE' || normalized === 'ENABLED' || normalized === 'ACCEPTED'
    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
    : normalized.includes('PENDING') || normalized === 'INVITED' || normalized === 'SUSPENDED'
      ? 'border-amber-200 bg-amber-50 text-amber-900'
      : normalized === 'REVOKED' || normalized === 'REMOVED' || normalized === 'DISABLED' || normalized === 'DELETED'
        ? 'border-rose-200 bg-rose-50 text-rose-800'
        : 'border-slate-200 bg-slate-100 text-slate-700';
  const label = value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase());
  return <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-black ${style}`}>{label || 'Unknown'}</span>;
}

export type AuditReasonTier = 'ROUTINE' | 'ELEVATED' | 'HIGH_RISK';

const ROUTINE_AUDIT_REASON_OPTIONS: SelectOption[] = [
  { value: 'New Tenant setup', label: 'New company / business-unit setup' },
  { value: 'New employee onboarding', label: 'New employee onboarding' },
  { value: 'Job responsibility changed', label: 'Job responsibility changed' },
  { value: 'Temporary project access', label: 'Temporary project access' },
  { value: 'Department transfer', label: 'Department transfer' },
  { value: 'Access review correction', label: 'Access review correction' },
  { value: 'Organization structure update', label: 'Organization structure update' },
  { value: 'Other', label: 'Other reason' },
];

const ELEVATED_AUDIT_REASON_OPTIONS: SelectOption[] = [
  { value: 'Access review correction', label: 'Access review correction' },
  { value: 'Job responsibility changed', label: 'Job responsibility changed' },
  { value: 'Security remediation', label: 'Security remediation' },
  { value: 'Service integration setup', label: 'Service integration setup' },
  { value: 'Approved lifecycle change', label: 'Approved lifecycle change' },
  { value: 'Temporary elevated access', label: 'Temporary elevated access' },
  { value: 'Offboarding control', label: 'Offboarding control' },
  { value: 'Other', label: 'Other reason' },
];

const HIGH_RISK_AUDIT_REASON_OPTIONS: SelectOption[] = [
  { value: 'Security remediation', label: 'Security remediation' },
  { value: 'Offboarding', label: 'Offboarding / access removal' },
  { value: 'Privileged access change', label: 'Privileged access change' },
  { value: 'Credential security action', label: 'Credential / session security action' },
  { value: 'Independent approval decision', label: 'Independent approval decision' },
  { value: 'Destructive administration action', label: 'Delete / destructive administration action' },
  { value: 'Other', label: 'Other high-risk reason' },
];

export const AUDIT_REASON_OPTIONS: SelectOption[] = ROUTINE_AUDIT_REASON_OPTIONS;

export function auditReasonOptions(tier: AuditReasonTier): SelectOption[] {
  if (tier === 'HIGH_RISK') return HIGH_RISK_AUDIT_REASON_OPTIONS;
  if (tier === 'ELEVATED') return ELEVATED_AUDIT_REASON_OPTIONS;
  return ROUTINE_AUDIT_REASON_OPTIONS;
}

function splitHighRiskReason(value: string): { category: string; detail: string } {
  const separator = ' — ';
  const at = value.indexOf(separator);
  if (at < 0) return { category: '', detail: '' };
  return { category: value.slice(0, at).trim(), detail: value.slice(at + separator.length).trim() };
}

export function isAuditReasonValid(value: string, tier: AuditReasonTier = 'ROUTINE'): boolean {
  const trimmed = value.trim();
  if (tier === 'HIGH_RISK') {
    const { category, detail } = splitHighRiskReason(trimmed);
    return Boolean(category && detail.length >= 12);
  }
  return trimmed.length >= 12;
}

export function AuditReasonSelector({
  idPrefix,
  value,
  onChange,
  tier = 'ROUTINE',
}: Readonly<{
  idPrefix: string;
  value: string;
  onChange: (value: string) => void;
  tier?: AuditReasonTier;
}>) {
  const options = auditReasonOptions(tier);

  if (tier === 'HIGH_RISK') {
    const parsed = splitHighRiskReason(value);
    const categoryKnown = options.some((option) => option.value !== 'Other' && option.value === parsed.category);
    const selectedCategory = categoryKnown ? parsed.category : parsed.category ? 'Other' : '';
    const customCategory = selectedCategory === 'Other' ? parsed.category : '';
    const setCategory = (next: string) => {
      const category = next === 'Other' ? 'Other high-risk action' : next;
      onChange(`${category} — ${parsed.detail}`);
    };
    return (
      <div className="space-y-3 rounded-2xl border border-rose-200 bg-rose-50/60 p-4">
        <div>
          <p className="text-sm font-black text-rose-950">High-risk business reason</p>
          <p className="mt-1 text-xs leading-5 text-rose-800">Select the control category and describe the concrete business or security justification. Generic audit text is not accepted.</p>
        </div>
        <FieldLabel htmlFor={`${idPrefix}-reason-category`} label="Reason category" required>
          <SelectField
            id={`${idPrefix}-reason-category`}
            value={selectedCategory}
            onChange={setCategory}
            options={options}
            required
            placeholder="Select a high-risk reason"
          />
        </FieldLabel>
        {selectedCategory === 'Other' ? (
          <FieldLabel htmlFor={`${idPrefix}-reason-category-other`} label="High-risk category" required>
            <input
              id={`${idPrefix}-reason-category-other`}
              value={customCategory}
              minLength={6}
              required
              onChange={(event) => onChange(`${event.target.value} — ${parsed.detail}`)}
              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-rose-600 focus:ring-2 focus:ring-rose-100"
            />
          </FieldLabel>
        ) : null}
        <FieldLabel htmlFor={`${idPrefix}-reason-detail`} label="Specific justification" help="Describe what is changing, why it is necessary, and the approval or control being applied." required>
          <textarea
            id={`${idPrefix}-reason-detail`}
            value={parsed.detail}
            minLength={12}
            required
            onChange={(event) => onChange(`${parsed.category || 'Security remediation'} — ${event.target.value}`)}
            placeholder="Example: Revoke access after confirmed role transfer effective today."
            className="min-h-24 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-rose-600 focus:ring-2 focus:ring-rose-100"
          />
        </FieldLabel>
      </div>
    );
  }

  const isPredefined = options.some((option) => option.value !== 'Other' && option.value === value);
  const custom = !isPredefined && value.length > 0;
  const selected = isPredefined ? value : custom ? 'Other' : '';
  const customValue = value.startsWith('Other: ') ? value.slice('Other: '.length) : custom ? value : '';
  return (
    <div className="space-y-2">
      <FieldLabel
        htmlFor={`${idPrefix}-reason`}
        label={tier === 'ELEVATED' ? 'Governance reason' : 'Business reason'}
        help={tier === 'ELEVATED'
          ? 'Choose the approved governance reason. Use Other only when the presets do not describe the change.'
          : 'Choose a normal business reason. No free-text explanation is required when a preset accurately describes the change.'}
        required
      >
        <SelectField
          id={`${idPrefix}-reason`}
          value={selected}
          onChange={(next) => onChange(next === 'Other' ? 'Other: ' : next)}
          options={options}
          required
          placeholder="Select a business reason"
        />
      </FieldLabel>
      {selected === 'Other' ? (
        <FieldLabel htmlFor={`${idPrefix}-reason-other`} label="Describe the reason" required>
          <textarea
            id={`${idPrefix}-reason-other`}
            value={customValue}
            minLength={12}
            required
            onChange={(event) => onChange(`Other: ${event.target.value}`)}
            className="min-h-24 w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100"
          />
        </FieldLabel>
      ) : null}
    </div>
  );
}

export function humanizePermission(code: string): string {
  const action = code.split('.').at(-1) ?? code;
  const words = action.replaceAll('_', ' ');
  return words.replace(/\b\w/g, (character) => character.toUpperCase());
}

export function datePresetToIso(preset: string, customDate: string): string | null {
  if (preset === 'PERMANENT') return null;
  if (preset === 'CUSTOM') return customDate ? new Date(`${customDate}T23:59:59`).toISOString() : null;
  const days = Number(preset);
  if (!Number.isFinite(days) || days <= 0) return null;
  const expires = new Date();
  expires.setDate(expires.getDate() + days);
  return expires.toISOString();
}
