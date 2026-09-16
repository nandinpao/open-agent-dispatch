'use client';

import type { ReactNode } from 'react';
import { useI18n } from '@/hooks/useI18n';

export const TASK_DETAIL_SECTIONS = [
  { id: 'task-overview', labelKey: 'task.sections.overview' },
  { id: 'task-ownership', labelKey: 'task.sections.ownership' },
  { id: 'task-assignment', labelKey: 'task.sections.assignment' },
  { id: 'task-a2a', labelKey: 'task.sections.a2a' },
  { id: 'task-evidence', labelKey: 'task.sections.evidence' },
] as const;

export type TaskDetailSectionId = (typeof TASK_DETAIL_SECTIONS)[number]['id'];

type TaskDetailSectionLabelKey = (typeof TASK_DETAIL_SECTIONS)[number]['labelKey'];

function sectionLabelKey(id: TaskDetailSectionId): TaskDetailSectionLabelKey {
  return TASK_DETAIL_SECTIONS.find((section) => section.id === id)?.labelKey ?? 'task.sections.overview';
}

export function TaskDetailSectionNavigation() {
  const { t } = useI18n();
  return (
    <nav className="sticky top-4 z-20 overflow-x-auto rounded-2xl border border-slate-200 bg-white/95 p-2 shadow-sm backdrop-blur" aria-label="Task detail sections">
      <ul className="flex min-w-max gap-1">
        {TASK_DETAIL_SECTIONS.map(({ id, labelKey }) => (
          <li key={id}>
            <a href={`#${id}`} className="block rounded-xl px-3 py-2 text-xs font-black text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 focus:outline-none focus:ring-2 focus:ring-blue-200">{t(labelKey)}</a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function TaskProductSection({ id, description, children }: Readonly<{ id: TaskDetailSectionId; description?: string; children: ReactNode }>) {
  const { t } = useI18n();
  const title = t(sectionLabelKey(id));
  return (
    <section id={id} className="scroll-mt-24 space-y-3" aria-labelledby={`${id}-heading`}>
      <div>
        <h2 id={`${id}-heading`} className="text-lg font-black text-slate-950">{title}</h2>
        {description ? <p className="mt-1 text-sm text-slate-600">{description}</p> : null}
      </div>
      {children}
    </section>
  );
}
