'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUiCapabilityContext } from '@/components/ui-capability/UiPageBootstrapProvider';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';

export function StaleResourceDialog({ contextId }: Readonly<{ contextId: string }>) {
  const context = useUiCapabilityContext(contextId);
  const router = useRouter();
  const [dismissedRevision, setDismissedRevision] = useState<number | null>(null);
  const stale = context?.status === 'STALE';
  const revision = context ? `${context.identity.principalEpoch}:${context.identity.catalogRevision}:${context.identity.policyVersion}:${context.identity.resourceVersion ?? ''}` : '';
  const open = stale && dismissedRevision !== hashRevision(revision);
  const dialogRef = useDialogAccessibility<HTMLDivElement>(open, () => setDismissedRevision(hashRevision(revision)));
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4" role="presentation">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="stale-resource-title"
        tabIndex={-1}
        className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl"
      >
        <h2 id="stale-resource-title" className="text-lg font-bold text-slate-950">This page changed while you were working</h2>
        <p className="mt-2 text-sm text-slate-700">Review the latest information and access decision before continuing. Existing action buttons remain disabled.</p>
        <div className="mt-5 flex justify-end gap-3">
          <button type="button" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold" onClick={() => setDismissedRevision(hashRevision(revision))}>Stay on this page</button>
          <button type="button" className="rounded-lg bg-blue-700 px-4 py-2 text-sm font-semibold text-white" onClick={() => router.refresh()}>Review latest</button>
        </div>
      </div>
    </div>
  );
}

function hashRevision(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  return hash;
}
