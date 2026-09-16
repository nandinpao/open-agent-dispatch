'use client';

import { useEffect, useMemo, useState } from 'react';
import { ConfirmDialog, type ConfirmDialogTone } from '@/components/ui/ConfirmDialog';
import { EntityPicker, FormField, TextAreaField, TextField, type SelectOption } from '@/components/forms';

export interface TaskActionDialogValues {
  reason: string;
  confirmationPhrase?: string;
  targetAgentId?: string;
  targetPoolId?: string;
}


export function validateTaskActionDialogInput(input: Readonly<{
  reason: string;
  reasonRequired: boolean;
  minimumReasonLength: number;
  requiredPhrase?: string;
  confirmationPhrase: string;
}>): string | null {
  if (input.reasonRequired && input.reason.trim().length < input.minimumReasonLength) {
    return `Actions ${input.minimumReasonLength} `;
  }
  if (input.requiredPhrase && input.confirmationPhrase.trim() !== input.requiredPhrase) {
    return `Enter ${input.requiredPhrase}`;
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
  allowTargetPool?: boolean;
  targetAgentOptions?: SelectOption[];
  targetPoolOptions?: SelectOption[];
  targetOptionsLoading?: boolean;
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
  allowTargetPool = false,
  targetAgentOptions = [],
  targetPoolOptions = [],
  targetOptionsLoading = false,
  onConfirm,
  onCancel,
}: Readonly<TaskActionDialogProps>) {
  const [reason, setReason] = useState('');
  const [confirmationPhrase, setConfirmationPhrase] = useState('');
  const [targetAgentId, setTargetAgentId] = useState('');
  const [targetPoolId, setTargetPoolId] = useState('');

  useEffect(() => {
    if (!open) {
      setReason('');
      setConfirmationPhrase('');
      setTargetAgentId('');
      setTargetPoolId('');
    }
  }, [open]);

  const validationMessage = useMemo(() => {
    const base = validateTaskActionDialogInput({ reason, reasonRequired, minimumReasonLength, requiredPhrase, confirmationPhrase });
    if (base) return base;
    if (allowTargetPool && !targetPoolId.trim()) return targetPoolOptions.length ? 'Select a governed Agent Pool.' : 'No governed Agent Pool is available for this Task context.';
    if (allowTargetAgent && !targetAgentId.trim()) return targetAgentOptions.length ? 'Select a governed Agent.' : 'No governed Agent is available for this Task context.';
    return null;
  }, [allowTargetAgent, allowTargetPool, confirmationPhrase, minimumReasonLength, reason, reasonRequired, requiredPhrase, targetAgentId, targetAgentOptions.length, targetPoolId, targetPoolOptions.length]);

  return (
    <ConfirmDialog
      open={open}
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      cancelLabel="Cancel"
      tone={tone}
      isRunning={isRunning}
      onCancel={onCancel}
      onConfirm={() => {
        if (validationMessage) return;
        void onConfirm({
          reason: reason.trim(),
          confirmationPhrase: confirmationPhrase.trim() || undefined,
          targetAgentId: targetAgentId.trim() || undefined,
          targetPoolId: targetPoolId.trim() || undefined,
        });
      }}
    >
      <div className="space-y-4">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
          <div className="text-xs font-bold uppercase tracking-wide text-slate-400">Actions</div>
          <div className="mt-1 break-all font-semibold text-slate-900">{target}</div>
        </div>

        {allowTargetAgent ? (
          <FormField id="task-action-agent" label="Assign Agent" help="Choose from Core-governed Agent options instead of typing an Agent ID.">
            <EntityPicker id="task-action-agent" value={targetAgentId} onChange={setTargetAgentId} options={targetAgentOptions} disabled={targetOptionsLoading || !targetAgentOptions.length} placeholder={targetOptionsLoading ? 'Loading governed Agents…' : targetAgentOptions.length ? 'Select an Agent' : 'No governed Agent choice is available'} />
          </FormField>
        ) : null}

        {allowTargetPool ? (
          <FormField id="task-action-pool" label="Target Agent Pool" help="Choose a governed Agent Pool. Manual Pool IDs are not accepted in the standard workflow." required>
            <EntityPicker id="task-action-pool" value={targetPoolId} onChange={setTargetPoolId} options={targetPoolOptions} disabled={targetOptionsLoading || !targetPoolOptions.length} placeholder={targetOptionsLoading ? 'Loading Agent Pools…' : targetPoolOptions.length ? 'Select an Agent Pool' : 'No governed Agent Pool is available'} required />
          </FormField>
        ) : null}

        <FormField id="task-action-reason" label={`Action reason${reasonRequired ? '' : ' (optional)'}`} help={reasonRequired ? `At least ${minimumReasonLength} characters. Core records this reason in the Task timeline.` : 'Core records this reason in the Task timeline.'} required={reasonRequired}>
          <TextAreaField id="task-action-reason" value={reason} onChange={setReason} rows={4} placeholder="Describe why this governed action is needed." />
        </FormField>

        {requiredPhrase ? (
          <FormField id="task-action-confirmation" label="Confirmation phrase" help={`Enter exactly ${requiredPhrase} to confirm this higher-risk action.`} required>
            <TextField id="task-action-confirmation" value={confirmationPhrase} onChange={setConfirmationPhrase} placeholder={requiredPhrase} />
          </FormField>
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
