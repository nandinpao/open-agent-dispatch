'use client';

import { useState } from 'react';
import { CanonicalCapabilityCreateDialog } from '@/components/capabilities/CanonicalCapabilityCreateDialog';
import type { CoreCanonicalCapabilityDefinition } from '@/lib/types/core';

export function CanonicalCapabilityPicker({
  tenantId,
  capabilities,
  value,
  onChange,
  onCreated,
  disabled = false,
  label = 'Capability',
  emptyLabel = 'Select a capability',
}: Readonly<{
  tenantId: string;
  capabilities: CoreCanonicalCapabilityDefinition[];
  value: string;
  onChange: (code: string) => void;
  onCreated?: (capability: CoreCanonicalCapabilityDefinition) => Promise<void> | void;
  disabled?: boolean;
  label?: string;
  emptyLabel?: string;
}>) {
  const [createOpen, setCreateOpen] = useState(false);
  return (
    <>
      <label className="block text-sm font-bold text-slate-700">
        {label}
        <div className="mt-1 flex gap-2">
          <select value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled} className="min-w-0 flex-1 rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal text-slate-800 disabled:bg-slate-50">
            <option value="">{emptyLabel}</option>
            {capabilities.filter((item) => String(item.status ?? '').toUpperCase() === 'ACTIVE').map((item) => <option key={item.capabilityCode} value={item.capabilityCode}>{item.displayName} ({item.capabilityCode})</option>)}
          </select>
          <button type="button" onClick={() => setCreateOpen(true)} disabled={disabled || !tenantId} className="shrink-0 rounded-xl border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-black text-blue-700 hover:bg-blue-100 disabled:opacity-50">+ Create</button>
        </div>
      </label>
      <CanonicalCapabilityCreateDialog
        open={createOpen}
        tenantId={tenantId}
        onClose={() => setCreateOpen(false)}
        onCreated={async (created) => {
          await onCreated?.(created);
          onChange(created.capabilityCode);
        }}
      />
    </>
  );
}
