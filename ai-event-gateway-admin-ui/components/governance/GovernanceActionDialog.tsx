'use client';

import { useEffect, useMemo, useState } from 'react';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import type { GovernanceActionSpec } from '@/lib/governance/approvalLifecycle';

export interface GovernanceActionResult {
  operatorId: string;
  reason: string;
  evidenceRef?: string;
  confirmationPhrase?: string;
  riskAcknowledged?: boolean;
}

export interface GovernanceActionDialogProps {
  open: boolean;
  spec: GovernanceActionSpec | null;
  targetCode?: string;
  currentStatus?: string;
  isRunning?: boolean;
  operatorId?: string;
  onCancel: () => void;
  onConfirm: (result: GovernanceActionResult) => void;
}

export function GovernanceActionDialog({
  open,
  spec,
  targetCode,
  currentStatus,
  isRunning = false,
  operatorId = 'admin-ui',
  onCancel,
  onConfirm,
}: Readonly<GovernanceActionDialogProps>) {
  const [reason, setReason] = useState('');
  const [evidenceRef, setEvidenceRef] = useState('');
  const [confirmationPhrase, setConfirmationPhrase] = useState('');
  const [riskAcknowledged, setRiskAcknowledged] = useState(false);

  useEffect(() => {
    if (open) {
      setReason('');
      setEvidenceRef('');
      setConfirmationPhrase('');
      setRiskAcknowledged(false);
    }
  }, [open, spec?.title]);

  const validationError = useMemo(() => {
    if (!spec) return 'Missing action spec.';
    if (reason.trim().length < spec.minReasonLength) return `Reason must be at least ${spec.minReasonLength} characters.`;
    if (spec.requiresEvidence && evidenceRef.trim().length < 3) return 'Evidence reference is required for this governance action.';
    if (spec.confirmationPhrase && confirmationPhrase.trim() !== spec.confirmationPhrase) return `Confirmation phrase must be: ${spec.confirmationPhrase}`;
    if ((spec.tone === 'danger' || spec.kind === 'trust' || spec.kind === 'execute') && !riskAcknowledged) return 'Risk acknowledgement is required.';
    return '';
  }, [confirmationPhrase, evidenceRef, reason, riskAcknowledged, spec]);

  if (!spec) return null;

  return (
    <ConfirmDialog
      open={open}
      title={spec.title}
      description={spec.description}
      confirmLabel={spec.confirmLabel}
      cancelLabel="Cancel"
      tone={spec.tone}
      isRunning={isRunning}
      onCancel={onCancel}
      onConfirm={() => {
        if (validationError) return;
        onConfirm({
          operatorId,
          reason: reason.trim(),
          evidenceRef: evidenceRef.trim() || undefined,
          confirmationPhrase: confirmationPhrase.trim() || undefined,
          riskAcknowledged,
        });
      }}
    >
      <div className="space-y-4">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
          <div><span className="font-black text-slate-800">Target:</span> {targetCode ?? '-'}</div>
          <div className="mt-1"><span className="font-black text-slate-800">Current status:</span> {currentStatus ?? '-'}</div>
          <div className="mt-1"><span className="font-black text-slate-800">Operator:</span> {operatorId}</div>
        </div>

        <label className="block text-sm font-bold text-slate-800">
          {spec.reasonLabel}
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={4}
            className="mt-2 w-full rounded-2xl border border-slate-200 px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            placeholder={spec.reasonPlaceholder}
            disabled={isRunning}
          />
        </label>

        <label className="block text-sm font-bold text-slate-800">
          {spec.evidenceLabel ?? 'Evidence reference'} {spec.requiresEvidence ? <span className="text-rose-600">*</span> : <span className="text-slate-400">optional</span>}
          <input
            value={evidenceRef}
            onChange={(event) => setEvidenceRef(event.target.value)}
            className="mt-2 w-full rounded-2xl border border-slate-200 px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            placeholder="ticket / runbook / probe / approval reference"
            disabled={isRunning}
          />
        </label>

        {spec.confirmationPhrase ? (
          <label className="block text-sm font-bold text-slate-800">
            Confirmation phrase
            <input
              value={confirmationPhrase}
              onChange={(event) => setConfirmationPhrase(event.target.value)}
              className="mt-2 w-full rounded-2xl border border-slate-200 px-3 py-2 font-mono text-sm text-slate-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              placeholder={spec.confirmationPhrase}
              disabled={isRunning}
            />
          </label>
        ) : null}

        <label className="flex items-start gap-3 rounded-2xl border border-amber-100 bg-amber-50 p-3 text-sm text-amber-950">
          <input
            type="checkbox"
            checked={riskAcknowledged}
            onChange={(event) => setRiskAcknowledged(event.target.checked)}
            className="mt-1 h-4 w-4 rounded border-amber-300"
            disabled={isRunning}
          />
          <span>我已檢查 impact / runbook / rollback 條件，了解此操作會寫入治理歷史，且不會用 delete 取代 revoke。</span>
        </label>

        {validationError ? <div className="rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-xs font-bold text-rose-700">{validationError}</div> : null}
      </div>
    </ConfirmDialog>
  );
}
