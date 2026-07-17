import type { ReactNode } from 'react';
import { AdminPageHeader } from './AdminPageHeader';

export interface AdminListPageProps {
  title: string;
  description?: ReactNode;
  eyebrow?: string;
  actions?: ReactNode;
  summary?: ReactNode;
  filters?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
}

export function AdminListPage({
  title,
  description,
  eyebrow,
  actions,
  summary,
  filters,
  children,
  footer,
  className = '',
}: Readonly<AdminListPageProps>) {
  return (
    <main className={`space-y-6 ${className}`}>
      <AdminPageHeader title={title} description={description} eyebrow={eyebrow} actions={actions} />
      {summary}
      {filters}
      <section className="rounded-3xl border border-slate-200 bg-white shadow-sm">
        {children}
      </section>
      {footer}
    </main>
  );
}
