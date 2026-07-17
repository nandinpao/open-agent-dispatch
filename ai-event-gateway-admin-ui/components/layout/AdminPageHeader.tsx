import type { ReactNode } from 'react';

export interface AdminPageHeaderProps {
  title: string;
  description?: ReactNode;
  eyebrow?: string;
  status?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

export function AdminPageHeader({ title, description, eyebrow, status, actions, className = '' }: Readonly<AdminPageHeaderProps>) {
  return (
    <header className={`flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between ${className}`}>
      <div className="min-w-0">
        {eyebrow ? <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600">{eyebrow}</p> : null}
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold tracking-tight text-slate-950">{title}</h1>
          {status}
        </div>
        {description ? <div className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">{description}</div> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center justify-start gap-2 lg:justify-end">{actions}</div> : null}
    </header>
  );
}
