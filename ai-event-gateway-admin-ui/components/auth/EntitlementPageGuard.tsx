'use client';

import type { ReactNode } from 'react';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { featureDisplayMode } from '@/lib/navigation/uiEntitlements';

export function EntitlementPageGuard({ featureId, children }: Readonly<{ featureId: string; children: ReactNode }>) {
  const entitlements = useUiEntitlements();
  if (entitlements.loading) {
    return <main className="mx-auto max-w-2xl rounded-3xl border border-slate-200 bg-white p-7 shadow-sm" aria-busy="true"><p className="text-sm font-semibold text-slate-600">Checking workspace access…</p></main>;
  }
  if (entitlements.error) {
    return <main className="mx-auto max-w-2xl rounded-3xl border border-amber-200 bg-amber-50 p-7 text-amber-950 shadow-sm" role="alert"><p className="text-xs font-black uppercase tracking-[.18em]">Access check unavailable</p><h1 className="mt-2 text-2xl font-black">This page cannot be opened safely</h1><p className="mt-3 text-sm leading-6">{entitlements.error}</p><button type="button" onClick={entitlements.refresh} className="mt-4 rounded-xl border border-amber-400 px-4 py-2 text-sm font-black">Retry</button></main>;
  }
  const page = entitlements.value?.pages?.[featureId];
  const mode = featureDisplayMode(entitlements.value, featureId);
  if (mode === 'ENABLED') return <>{children}</>;
  if (mode === 'READ_ONLY') {
    return <><div className="mb-4 rounded-2xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-950" role="status"><b>Read-only access.</b> You can review this area, but your current responsibilities do not grant its managed actions.</div>{children}</>;
  }
  return <main className="mx-auto max-w-2xl rounded-3xl border border-amber-200 bg-amber-50 p-7 text-amber-950 shadow-sm" role="alert"><p className="text-xs font-black uppercase tracking-[.18em]">Access not granted</p><h1 className="mt-2 text-2xl font-black">This page is not available in your current workspace</h1><p className="mt-3 text-sm leading-6">{page?.denialReason || 'Your effective access does not include this feature for the active Tenant.'}</p></main>;
}
