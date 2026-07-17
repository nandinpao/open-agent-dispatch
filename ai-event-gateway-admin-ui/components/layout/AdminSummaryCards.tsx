import type { ReactNode } from 'react';

export type AdminSummaryCardTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'purple';

export interface AdminSummaryCardItem {
  id: string;
  label: string;
  value: ReactNode;
  description?: ReactNode;
  tone?: AdminSummaryCardTone;
  footer?: ReactNode;
}

export interface AdminSummaryCardsProps {
  items: AdminSummaryCardItem[];
  columnsClassName?: string;
  className?: string;
}

const toneClassMap: Record<AdminSummaryCardTone, string> = {
  neutral: 'border-slate-200 bg-white',
  success: 'border-emerald-200 bg-emerald-50/60',
  warning: 'border-amber-200 bg-amber-50/60',
  danger: 'border-rose-200 bg-rose-50/60',
  info: 'border-blue-200 bg-blue-50/60',
  purple: 'border-purple-200 bg-purple-50/60',
};

export function AdminSummaryCards({
  items,
  columnsClassName = 'grid-cols-1 md:grid-cols-2 xl:grid-cols-4',
  className = '',
}: Readonly<AdminSummaryCardsProps>) {
  if (items.length === 0) return null;

  return (
    <section className={`grid gap-4 ${columnsClassName} ${className}`}>
      {items.map((item) => {
        const tone = item.tone ?? 'neutral';
        return (
          <article key={item.id} className={`rounded-2xl border p-4 shadow-sm ${toneClassMap[tone]}`}>
            <p className="text-xs font-bold uppercase tracking-wide text-slate-500">{item.label}</p>
            <div className="mt-2 text-2xl font-bold text-slate-950">{item.value}</div>
            {item.description ? <div className="mt-1 text-sm leading-5 text-slate-600">{item.description}</div> : null}
            {item.footer ? <div className="mt-3 border-t border-slate-200 pt-3 text-xs text-slate-500">{item.footer}</div> : null}
          </article>
        );
      })}
    </section>
  );
}
