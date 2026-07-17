'use client';

import { LegacyValueWarning } from '@/components/governance/StrictSelectionControls';
import type { CoreRuntimeFeatureCatalog } from '@/lib/types/core';

function normalize(value: string): string {
  return value.trim().toUpperCase();
}

export function RuntimeFeatureCardSelector({
  features,
  selectedCode,
  onChange,
  loading = false,
  disabled = false,
}: Readonly<{
  features: CoreRuntimeFeatureCatalog[];
  selectedCode?: string;
  onChange: (featureCode: string) => void;
  loading?: boolean;
  disabled?: boolean;
}>) {
  const activeFeatures = features.filter((feature) => (feature.status ?? 'ACTIVE') === 'ACTIVE');
  const options = activeFeatures.map((feature) => ({ value: feature.featureCode, label: feature.featureName ?? feature.featureCode }));
  return (
    <div className="space-y-3">
      <div>
        <div className="text-sm font-black text-slate-900">Runtime Feature Cards</div>
        <p className="mt-1 text-xs leading-5 text-slate-500">Select only ACTIVE runtime feature catalog records. Observing a feature does not make it trusted; verify/trust lifecycle still applies.</p>
      </div>
      <LegacyValueWarning label="runtime feature" values={selectedCode ? [selectedCode] : []} options={options} message="Selected runtime feature is not ACTIVE in the catalog. It cannot be submitted for new trust workflow." />
      {loading ? <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">Loading runtime feature catalog...</div> : null}
      {!loading && !activeFeatures.length ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">No ACTIVE runtime feature catalog records are available.</div> : null}
      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        {activeFeatures.map((feature) => {
          const checked = normalize(feature.featureCode) === normalize(selectedCode ?? '');
          return (
            <button
              key={feature.featureCode}
              type="button"
              disabled={disabled}
              onClick={() => onChange(checked ? '' : feature.featureCode)}
              className={`rounded-2xl border p-3 text-left transition ${checked ? 'border-blue-300 bg-blue-50' : 'border-slate-200 bg-white hover:bg-slate-50'} disabled:opacity-60`}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="text-sm font-black text-slate-900">{feature.featureName || feature.featureCode}</div>
                  <div className="mt-1 font-mono text-xs text-slate-500">{feature.featureCode}</div>
                </div>
                <span className={`rounded-full px-2 py-1 text-[10px] font-black ${checked ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-500'}`}>{checked ? 'SELECTED' : feature.status ?? 'ACTIVE'}</span>
              </div>
              <div className="mt-2 grid gap-1 text-xs text-slate-600">
                <div>Category: {feature.category || '-'}</div>
                <div>Probe: {feature.requiresProbe === false ? 'No' : 'Required'}</div>
                <div>Trust Approval: {feature.requiresTrustApproval === false ? 'No' : 'Required'}</div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
