"use client";

import { LegacyValueWarning } from "@/components/governance/StrictSelectionControls";
import type { CoreAgentCapabilityCatalog } from "@/lib/types/core";

interface CapabilityCardSelectorProps {
  capabilities: CoreAgentCapabilityCatalog[];
  selectedCodes: string[];
  onChange: (codes: string[]) => void;
  loading?: boolean;
  disabled?: boolean;
  title?: string;
  description?: string;
}

function normalize(code: string): string {
  return code.trim().toUpperCase();
}

export function CapabilityCardSelector({
  capabilities,
  selectedCodes,
  onChange,
  loading = false,
  disabled = false,
  title = "Capability Catalog",
  description = "Select reusable capabilities from the Capability Catalog. Capability approvals are managed from Agent Detail.",
}: Readonly<CapabilityCardSelectorProps>) {
  const selected = new Set(selectedCodes.map(normalize));

  function toggle(code: string) {
    const normalized = normalize(code);
    if (!normalized || disabled) return;
    const next = new Set(selected);
    if (next.has(normalized)) next.delete(normalized);
    else next.add(normalized);
    onChange(Array.from(next).sort());
  }

  const activeCapabilities = capabilities.filter((capability) => (capability.status ?? "ACTIVE") === "ACTIVE");
  const capabilityOptions = activeCapabilities.map((capability) => ({ value: capability.capabilityCode, label: capability.capabilityName ?? capability.capabilityCode }));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-sm font-bold text-slate-800">{title}</div>
          <p className="mt-1 text-xs text-slate-500">{description}</p>
        </div>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600">
          {selected.size} selected
        </span>
      </div>
      <LegacyValueWarning label="capability" values={selectedCodes} options={capabilityOptions} message="Selected capability values are not ACTIVE catalog entries. Remove or replace them before relying on dispatch eligibility." />
      {loading ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">Loading capability catalog...</div>
      ) : activeCapabilities.length === 0 ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          No ACTIVE capability catalog entries are available. Create or activate capabilities before assigning Agent capabilities.
        </div>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {activeCapabilities.map((capability) => {
            const code = normalize(capability.capabilityCode);
            const checked = selected.has(code);
            return (
              <button
                key={code}
                type="button"
                disabled={disabled}
                onClick={() => toggle(code)}
                className={`rounded-2xl border p-4 text-left transition ${
                  checked
                    ? "border-blue-300 bg-blue-50 shadow-sm"
                    : "border-slate-200 bg-white hover:border-blue-200 hover:bg-blue-50/40"
                } disabled:cursor-not-allowed disabled:opacity-60`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-sm font-extrabold text-slate-900">{capability.capabilityName || code}</div>
                    <div className="mt-1 font-mono text-xs text-slate-500">{code}</div>
                  </div>
                  <span className={`rounded-full px-2 py-1 text-[11px] font-bold ${checked ? "bg-blue-600 text-white" : "bg-slate-100 text-slate-600"}`}>
                    {checked ? "Selected" : "Select"}
                  </span>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-600">
                  <div><span className="font-semibold text-slate-500">Source:</span> {capability.sourceSystem || "ANY"}</div>
                  <div><span className="font-semibold text-slate-500">Task:</span> {capability.taskType || "ANY"}</div>
                  <div><span className="font-semibold text-slate-500">Risk:</span> {capability.riskLevel || "MIDDLE"}</div>
                  <div><span className="font-semibold text-slate-500">Approval:</span> {capability.requiresApproval === false ? "No" : "Required"}</div>
                </div>
                {capability.requiresCertification || capability.requiresRuntimeProbe ? (
                  <div className="mt-3 rounded-xl bg-white/70 px-3 py-2 text-xs text-slate-600">
                    {capability.requiresCertification ? "Requires certification. " : ""}
                    {capability.requiresRuntimeProbe ? "Requires runtime probe." : ""}
                  </div>
                ) : null}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
