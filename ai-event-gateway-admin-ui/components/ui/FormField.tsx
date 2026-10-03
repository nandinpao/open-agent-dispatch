import type { ReactNode } from 'react';

export interface FormFieldProps {
  label: ReactNode;
  htmlFor: string;
  hint?: ReactNode;
  error?: ReactNode;
  required?: boolean;
  children: ReactNode;
  className?: string;
}

export function FormField({ label, htmlFor, hint, error, required = false, children, className = '' }: Readonly<FormFieldProps>) {
  return (
    <div className={className}>
      <label htmlFor={htmlFor} className="block text-sm font-bold text-slate-700">
        {label}{required ? <span className="ml-1 text-rose-700" aria-hidden="true">*</span> : null}
        {required ? <span className="sr-only"> required</span> : null}
      </label>
      <div className="mt-1">{children}</div>
      {error ? <div id={`${htmlFor}-error`} role="alert" className="mt-1 text-xs font-semibold text-rose-700">{error}</div> : null}
      {!error && hint ? <div id={`${htmlFor}-hint`} className="mt-1 text-xs leading-5 text-slate-500">{hint}</div> : null}
    </div>
  );
}

export function fieldDescriptionId(controlId: string, hasError: boolean, hasHint: boolean): string | undefined {
  if (hasError) return `${controlId}-error`;
  if (hasHint) return `${controlId}-hint`;
  return undefined;
}
