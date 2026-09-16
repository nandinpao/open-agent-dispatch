'use client';

import type { ReactNode } from 'react';
import { useUiCapability } from '@/components/ui-capability/UiPageBootstrapProvider';
import type { UiVisibilityLevel } from '@/lib/ui-capability/contracts';

const RANK: Record<UiVisibilityLevel, number> = {
  NONE: 0,
  METADATA: 1,
  SUMMARY: 2,
  STANDARD: 3,
  SENSITIVE: 4,
  FULL: 5,
  SECRET_METADATA: 6,
};

export function RestrictedField({
  contextId,
  uiActionId,
  requiredVisibility,
  children,
  placeholder = 'Restricted',
}: Readonly<{
  contextId: string;
  uiActionId: string;
  requiredVisibility: UiVisibilityLevel;
  children: ReactNode;
  placeholder?: ReactNode;
}>) {
  const decision = useUiCapability(contextId, uiActionId);
  const capability = decision.capability;
  if (!capability || capability.displayMode === 'HIDE') return null;
  if (decision.contextStatus !== 'READY') return <span className="italic text-slate-500" aria-label="Restricted field">{placeholder}</span>;
  if (RANK[capability.visibilityCeiling] < RANK[requiredVisibility]) {
    return <span className="italic text-slate-500" aria-label="Restricted field">{placeholder}</span>;
  }
  return children;
}
