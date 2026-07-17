'use client';

import { useEffect, useMemo, useState } from 'react';
import { ConfirmDialog, type ConfirmDialogTone } from '@/components/ui/ConfirmDialog';

export interface TaskActionDialogValues {
  reason: string;
  confirmationPhrase?: string;
  targetAgentId?: string;
}


export function validateTaskActionDialogInput(input: Readonly<{
  reason: string;
  reasonRequired: boolean;
  minimumReasonLength: number;
  requiredPhrase?: string;
  confirmationPhrase: string;
}>): string | null {
  if (input.reasonRequired && input.reason.trim().length < input.minimumReasonLength) {
    return `操作原因至少需要 ${input.minimumReasonLength} 個字元。`;
  }
  if (input.requiredPhrase && input.confirmationPhrase.trim() !== input.requiredPhrase) {
    return `請輸入確認字串：${input.requiredPhrase}`;
  }
  return null;
}

interface TaskActionDialogProps {
  open: boolean;
  title: string;
  target: string;
  description: string;
  confirmLabel: string;
  tone?: ConfirmDialogTone;
  isRunning?: boolean;
  reasonRequired?: boolean;
  minimumReasonLength?: number;
  requiredPhrase?: string;
  allowTargetAgent?: boolean;
  onConfirm: (values: TaskActionDialogValues) => void | Promise<void>;
  onCancel: () => void;
}

export function TaskActionDialog({
  open,
  title,
  target,
  description,
  confirmLabel,
  tone = 'warning',
  isRunning = false,
  reasonRequired = true,
  minimumReasonLength = 12,
  requiredPhrase,
  allowTargetAgent = false,
  onConfirm,
  onCancel,
}: Readonly<TaskActionDialogProps>) {
  const [reason, setReason] = useState('');
  const [confirmationPhrase, setConfirmationPhrase] = useState('');
  const [targetAgentId, setTargetAgentId] = useState('');

  useEffect(() => {
    if (!open) {
      setReason('');
      setConfirmationPhrase('');
      setTargetAgentId('');
    }
  }, [open]);

  const validationMessage = useMemo(() => validateTaskActionDialogInput({
    reason,
    reasonRequired,
    minimumReasonLength,
    requiredPhrase,
    confirmationPhrase,
  }), [confirmationPhrase, minimumReasonLength, reason, reasonRequired, requiredPhrase]);

  return (
    <ConfirmDialog
      open={open}
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      cancelLabel="取消"
      tone={tone}
      isRunning={isRunning}
      onCancel={onCancel}
      onConfirm={() => {
        if (validationMessage) return;
        void onConfirm({
          reason: reason.trim(),
          confirmationPhrase: confirmationPhrase.trim() || undefined,
          targetAgentId: targetAgentId.trim() || undefined,
        });
      }}
    >
      <div className="space-y-4">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
          <div className="text-xs font-bold uppercase tracking-wide text-slate-400">操作對象</div>
          <div className="mt-1 break-all font-semibold text-slate-900">{target}</div>
        </div>

        {allowTargetAgent ? (
          <label className="block text-sm font-semibold text-slate-700">
            指定 Agent（選填）
            <input
              value={targetAgentId}
              onChange={(event) => setTargetAgentId(event.target.value)}
              placeholder="留空時由目前 Dispatch Flow 重新選擇"
              className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
          </label>
        ) : null}

        <label className="block text-sm font-semibold text-slate-700">
          操作原因{reasonRequired ? '（必填）' : '（選填）'}
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={4}
            placeholder="請說明處理原因、已確認的阻擋條件與預期結果"
            className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          />
          {reasonRequired ? (
            <span className="mt-1 block text-xs text-slate-500">至少 {minimumReasonLength} 個字元，會寫入 Core Task timeline。</span>
          ) : null}
        </label>

        {requiredPhrase ? (
          <label className="block text-sm font-semibold text-slate-700">
            確認字串
            <input
              value={confirmationPhrase}
              onChange={(event) => setConfirmationPhrase(event.target.value)}
              placeholder={requiredPhrase}
              className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-2 font-mono text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
          </label>
        ) : null}

        {validationMessage ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-800">
            {validationMessage}
          </div>
        ) : null}
      </div>
    </ConfirmDialog>
  );
}
