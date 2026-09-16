'use client';

import { useRouter } from 'next/navigation';
import type { ReactNode } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';

export function InterceptedRouteModal({ children }: Readonly<{ children: ReactNode }>) {
  const router = useRouter();
  const close = () => router.back();
  const dialogRef = useDialogAccessibility<HTMLDivElement>(true, close);

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-950/55 p-4 sm:p-8" role="presentation">
      <div
        ref={dialogRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Task detail"
        className="w-full max-w-7xl overflow-hidden rounded-2xl bg-slate-50 shadow-2xl outline-none"
      >
        <div className="sticky top-0 z-10 flex justify-end border-b border-slate-200 bg-white px-4 py-3">
          <button type="button" onClick={close} className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-bold text-slate-700" aria-label="Close task detail">Close</button>
        </div>
        <div className="p-4 sm:p-6">{children}</div>
      </div>
    </div>
  );
}
