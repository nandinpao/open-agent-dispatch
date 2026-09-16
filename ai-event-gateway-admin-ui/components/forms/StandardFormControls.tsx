'use client';

import type { ReactNode } from 'react';
import {
  AsyncSearchSelectField,
  FieldLabel,
  PopupMultiSelectField,
  SearchSelectField,
  SelectField,
  type AsyncSelectPage,
  type SelectOption,
} from '@/components/access-management/shared/beginnerUi';

export type { AsyncSelectPage, SelectOption };
export { FieldLabel, SelectField, SearchSelectField, AsyncSearchSelectField, PopupMultiSelectField };

const controlClass = 'w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-600 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-100 disabled:text-slate-500';

export function FormField({
  id,
  label,
  help,
  required = false,
  children,
}: Readonly<{
  id: string;
  label: string;
  help?: string;
  required?: boolean;
  children: ReactNode;
}>) {
  return <FieldLabel htmlFor={id} label={label} help={help} required={required}>{children}</FieldLabel>;
}

export function TextField({
  id,
  value,
  onChange,
  placeholder,
  disabled = false,
  type = 'text',
  autoComplete,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  type?: 'text' | 'url' | 'password' | 'search';
  autoComplete?: string;
}>) {
  return (
    <input
      id={id}
      type={type}
      value={value}
      disabled={disabled}
      autoComplete={autoComplete}
      placeholder={placeholder}
      onChange={(event) => onChange(event.target.value)}
      className={controlClass}
    />
  );
}

export function TextAreaField({
  id,
  value,
  onChange,
  placeholder,
  rows = 4,
  disabled = false,
  technical = false,
}: Readonly<{
  id: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  rows?: number;
  disabled?: boolean;
  technical?: boolean;
}>) {
  return (
    <textarea
      id={id}
      value={value}
      disabled={disabled}
      rows={rows}
      placeholder={placeholder}
      onChange={(event) => onChange(event.target.value)}
      className={`${controlClass} ${technical ? 'font-mono text-xs' : ''}`}
    />
  );
}

export function EntityPicker({
  id,
  value,
  onChange,
  options,
  placeholder,
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
  return (
    <SearchSelectField
      id={id}
      value={value}
      onChange={onChange}
      options={options}
      placeholder={placeholder}
      disabled={disabled}
      required={required}
    />
  );
}

export function MultiSelectPicker({
  id,
  values,
  onChange,
  options,
  placeholder,
  disabled = false,
}: Readonly<{
  id: string;
  values: string[];
  onChange: (values: string[]) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
}>) {
  return (
    <PopupMultiSelectField
      id={id}
      values={values}
      onChange={onChange}
      options={options}
      placeholder={placeholder}
      disabled={disabled}
    />
  );
}

export function AdvancedSection({
  title = 'Advanced settings',
  description,
  children,
  defaultOpen = false,
}: Readonly<{
  title?: string;
  description?: string;
  children: ReactNode;
  defaultOpen?: boolean;
}>) {
  return (
    <details open={defaultOpen || undefined} className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
      <summary className="cursor-pointer list-none text-sm font-black text-slate-800">
        <span>{title}</span>
        {description ? <span className="ml-2 text-xs font-medium text-slate-500">{description}</span> : null}
      </summary>
      <div className="mt-4 border-t border-slate-200 pt-4">{children}</div>
    </details>
  );
}

export function InlineCreateButton({
  onClick,
  label = 'Create',
  disabled = false,
}: Readonly<{
  onClick: () => void;
  label?: string;
  disabled?: boolean;
}>) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex items-center rounded-xl border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-black text-blue-800 hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-50"
    >
      + {label}
    </button>
  );
}

export function BooleanToggle({
  id,
  checked,
  onChange,
  label,
  description,
  disabled = false,
}: Readonly<{
  id: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  description?: string;
  disabled?: boolean;
}>) {
  return (
    <label htmlFor={id} className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 bg-white p-3">
      <input id={id} type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} className="mt-1 size-4 rounded border-slate-300" />
      <span>
        <span className="block text-sm font-black text-slate-900">{label}</span>
        {description ? <span className="mt-1 block text-xs leading-5 text-slate-500">{description}</span> : null}
      </span>
    </label>
  );
}
