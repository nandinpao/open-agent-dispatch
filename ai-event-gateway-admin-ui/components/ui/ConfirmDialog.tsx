'use client';

import type { ReactNode } from 'react';

export type ConfirmDialogTone = 'danger' | 'warning' | 'primary' | 'neutral';

const confirmToneClassMap: Record<ConfirmDialogTone, string> = {
  danger: 'bg-rose-600 text-white hover:bg-rose-700 focus:ring-rose-200',
  warning: 'bg-amber-500 text-white hover:bg-amber-600 focus:ring-amber-200',
  primary: 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-200',
  neutral: 'bg-slate-900 text-white hover:bg-slate-800 focus:ring-slate-200',
};

const iconToneClassMap: Record<ConfirmDialogTone, string> = {
  danger: 'border-rose-200 bg-rose-50 text-rose-700',
  warning: 'border-amber-200 bg-amber-50 text-amber-700',
  primary: 'border-blue-200 bg-blue-50 text-blue-700',
  neutral: 'border-slate-200 bg-slate-50 text-slate-700',
};

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description?: ReactNode;
  children?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: ConfirmDialogTone;
  isRunning?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  children,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'danger',
  isRunning = false,
  onConfirm,
  onCancel,
}: Readonly<ConfirmDialogProps>) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6 backdrop-blur-sm" role="presentation">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl"
      >
        <div className="flex items-start gap-4">
          <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border text-lg font-black ${iconToneClassMap[tone]}`}>
            !
          </div>
          <div className="min-w-0 flex-1">
            <h2 id="confirm-dialog-title" className="text-lg font-bold text-slate-950">
              {title}
            </h2>
            {description ? <div className="mt-2 text-sm leading-6 text-slate-600">{description}</div> : null}
            {children ? <div className="mt-4">{children}</div> : null}
          </div>
        </div>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isRunning}
            className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isRunning}
            className={`rounded-xl px-4 py-2 text-sm font-semibold shadow-sm transition focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:opacity-60 ${confirmToneClassMap[tone]}`}
          >
            {isRunning ? 'Processing…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
