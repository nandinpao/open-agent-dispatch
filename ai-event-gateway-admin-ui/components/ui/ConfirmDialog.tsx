'use client';

import type { ReactNode } from 'react';
import { useId } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { Button, type ButtonTone } from './Button';

export type ConfirmDialogTone = 'danger' | 'warning' | 'primary' | 'neutral';

const buttonToneMap: Record<ConfirmDialogTone, ButtonTone> = {
  danger: 'danger', warning: 'warning', primary: 'primary', neutral: 'secondary',
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

export function ConfirmDialog({ open, title, description, children, confirmLabel = 'Confirm', cancelLabel = 'Cancel', tone = 'danger', isRunning = false, onConfirm, onCancel }: Readonly<ConfirmDialogProps>) {
  const dialogRef = useDialogAccessibility(open, onCancel);
  const titleId = useId();
  const descriptionId = useId();
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-6 backdrop-blur-sm" role="presentation">
      <div ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined} className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl outline-none">
        <div className="flex items-start gap-4">
          <div aria-hidden="true" className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border text-lg font-black ${iconToneClassMap[tone]}`}>!</div>
          <div className="min-w-0 flex-1">
            <h2 id={titleId} className="text-lg font-bold text-slate-950">{title}</h2>
            {description ? <div id={descriptionId} className="mt-2 text-sm leading-6 text-slate-600">{description}</div> : null}
            {children ? <div className="mt-4">{children}</div> : null}
          </div>
        </div>
        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <Button onClick={onCancel} disabled={isRunning} tone="secondary" size="md">{cancelLabel}</Button>
          <Button onClick={onConfirm} tone={buttonToneMap[tone]} size="md" busy={isRunning} busyLabel="Processing…">{confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}
