'use client';

import type { ReactNode } from 'react';

export interface RightDrawerProps {
  open: boolean;
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  widthClassName?: string;
  onClose: () => void;
}

export function RightDrawer({
  open,
  title,
  description,
  children,
  footer,
  widthClassName = 'max-w-2xl',
  onClose,
}: Readonly<RightDrawerProps>) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/40 backdrop-blur-sm" role="presentation">
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="right-drawer-title"
        className={`flex h-full w-full ${widthClassName} flex-col border-l border-slate-200 bg-white shadow-2xl`}
      >
        <div className="border-b border-slate-200 px-6 py-5">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <h2 id="right-drawer-title" className="text-lg font-bold text-slate-950">{title}</h2>
              {description ? <div className="mt-1 text-sm leading-6 text-slate-600">{description}</div> : null}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-100"
            >
              Close
            </button>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer ? <div className="border-t border-slate-200 bg-slate-50 px-6 py-4">{footer}</div> : null}
      </aside>
    </div>
  );
}
