import type { ReactNode } from 'react';
import { AdminPageHeader } from './AdminPageHeader';

export interface AdminDetailPageProps {
  title: string;
  description?: ReactNode;
  eyebrow?: string;
  status?: ReactNode;
  actions?: ReactNode;
  summary?: ReactNode;
  tabs?: ReactNode;
  children?: ReactNode;
  sidebar?: ReactNode;
  debug?: ReactNode;
  className?: string;
}

export function AdminDetailPage({
  title,
  description,
  eyebrow,
  status,
  actions,
  summary,
  tabs,
  children,
  sidebar,
  debug,
  className = '',
}: Readonly<AdminDetailPageProps>) {
  return (
    <main className={`space-y-6 ${className}`}>
      <AdminPageHeader title={title} description={description} eyebrow={eyebrow} status={status} actions={actions} />
      {summary}
      <div className={sidebar ? 'grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]' : 'space-y-6'}>
        <div className="min-w-0 space-y-6">
          {tabs}
          {children}
          {debug}
        </div>
        {sidebar ? <aside className="space-y-4">{sidebar}</aside> : null}
      </div>
    </main>
  );
}
