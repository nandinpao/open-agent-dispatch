import type { ReactNode } from 'react';

export interface DebugCollapseProps {
  title?: string;
  summary?: ReactNode;
  children: ReactNode;
  defaultOpen?: boolean;
  className?: string;
}

export function DebugCollapse({
  title = 'Debug details',
  summary,
  children,
  defaultOpen = false,
  className = '',
}: Readonly<DebugCollapseProps>) {
  return (
    <details open={defaultOpen} className={`rounded-2xl border border-slate-200 bg-slate-50 ${className}`}>
      <summary className="cursor-pointer list-none rounded-2xl px-4 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-100">
        <span className="inline-flex items-center gap-2">
          <span aria-hidden="true">▸</span>
          {title}
          {summary ? <span className="text-xs font-normal text-slate-500">{summary}</span> : null}
        </span>
      </summary>
      <div className="border-t border-slate-200 p-4 text-sm text-slate-700">{children}</div>
    </details>
  );
}
