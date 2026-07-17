"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import { useAuth } from "@/components/auth/AuthProvider";
import { GovernedSelect, LegacyValueWarning } from "@/components/governance/StrictSelectionControls";
import { GOVERNED_AGENT_TYPES, GOVERNED_OWNER_TEAMS } from "@/lib/governance/strictSelection";
import { parseScopeCsv } from "@/lib/agents/enrollmentWorkflow";
import type {
  AgentProfileUpdateRequest,
  CoreAgentAuthorizationScope,
  CoreAgentProfile,
} from "@/lib/types/core";

function csvScopes(scopes?: CoreAgentAuthorizationScope[] | null): string {
  return (scopes ?? [])
    .map((scope) =>
      [scope.systemCode ?? "*", scope.taskType ?? "*", scope.siteCode]
        .filter(Boolean)
        .join("/"),
    )
    .join(",");
}

function profileVersionKey(profile: CoreAgentProfile): string {
  return [
    profile.agentId,
    profile.updatedAt,
    (profile as CoreAgentProfile & { policyVersion?: number }).policyVersion,
  ]
    .filter(Boolean)
    .join(":");
}

interface AgentProfileEditDialogProps {
  profile: CoreAgentProfile;
  triggerLabel?: string;
  onSaved?: () => Promise<void> | void;
}

export function AgentProfileEditDialog({
  profile,
  triggerLabel = "Edit Profile",
  onSaved,
}: Readonly<AgentProfileEditDialogProps>) {
  const { selectedTenantId } = useAuth();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loadingLatest, setLoadingLatest] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tenantId, setTenantId] = useState(selectedTenantId || profile.tenantId || "");
  const [agentName, setAgentName] = useState(
    profile.agentName ?? profile.agentId,
  );
  const [agentType, setAgentType] = useState(profile.agentType ?? "UNKNOWN");
  const [ownerTeam, setOwnerTeam] = useState(profile.ownerTeam ?? "");
  const [description, setDescription] = useState(profile.description ?? "");
  const [approvalStatus, setApprovalStatus] = useState(profile.approvalStatus ?? "PENDING_REVIEW");
  const [riskStatus, setRiskStatus] = useState(profile.riskStatus ?? "NORMAL");
  const [enabled, setEnabled] = useState(profile.enabled);
  const [scopes, setScopes] = useState(
    csvScopes(profile.authorizationScopes) || "*/*",
  );

  const profileKey = useMemo(() => profileVersionKey(profile), [profile]);
  const loadedProfileAgentRef = useRef<string | null>(null);

  const loadProfileIntoForm = useCallback((source: CoreAgentProfile) => {
    setTenantId(selectedTenantId || source.tenantId || "");
    setAgentName(source.agentName ?? source.agentId);
    setAgentType(source.agentType ?? "UNKNOWN");
    setOwnerTeam(source.ownerTeam ?? "");
    setDescription(source.description ?? "");
    setApprovalStatus(source.approvalStatus ?? "PENDING_REVIEW");
    setRiskStatus(source.riskStatus ?? "NORMAL");
    setEnabled(source.enabled);
    setScopes(csvScopes(source.authorizationScopes) || "*/*");
  }, [selectedTenantId]);

  useEffect(() => {
    if (selectedTenantId) setTenantId(selectedTenantId);
  }, [selectedTenantId]);

  useEffect(() => {
    if (!open) {
      loadedProfileAgentRef.current = null;
      return;
    }
    if (loadedProfileAgentRef.current === profile.agentId) return;
    loadedProfileAgentRef.current = profile.agentId;
    let cancelled = false;
    setError(null);
    setLoadingLatest(true);
    coreAdminApi
      .getAgent(profile.agentId)
      .then((freshProfile) => {
        if (!cancelled) loadProfileIntoForm(freshProfile);
      })
      .catch(() => {
        if (!cancelled) loadProfileIntoForm(profile);
      })
      .finally(() => {
        if (!cancelled) setLoadingLatest(false);
      });
    return () => {
      cancelled = true;
    };
  }, [loadProfileIntoForm, open, profile, profile.agentId, profileKey]);

  const body = useMemo<AgentProfileUpdateRequest>(
    () => ({
      tenantId,
      agentName,
      agentType,
      ownerTeam,
      description,
      approvalStatus,
      riskStatus,
      enabled,
      scopes: parseScopeCsv(scopes, tenantId),
      reason: "Updated from Admin UI Agent Governance dialog",
    }),
    [
      agentName,
      agentType,
      description,
      enabled,
      approvalStatus,
      ownerTeam,
      riskStatus,
      scopes,
      tenantId,
    ],
  );

  async function save() {
    setSaving(true);
    setError(null);
    try {
      const updated = await coreAdminApi.updateAgentProfile(
        profile.agentId,
        body,
      );
      loadProfileIntoForm(updated);
      await onSaved?.();
      setOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded-lg border border-emerald-200 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50"
      >
        {triggerLabel}
      </button>

      {open ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-lg font-bold text-slate-900">
                  Edit Core Agent Profile
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  Agent {profile.agentId} profile metadata can still be edited after approval. This form does not modify Netty runtime sessions or capability approvals.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="rounded-lg px-3 py-1 text-sm font-semibold text-slate-500 hover:bg-slate-100"
              >
                Close
              </button>
            </div>

            {error ? (
              <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
                {error}
              </div>
            ) : null}

            {loadingLatest ? (
              <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-3 text-sm text-blue-700">
                Loading latest Core profile before editing...
              </div>
            ) : null}

            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <label className="text-sm font-semibold text-slate-700">
                Tenant
                <input
                  value={selectedTenantId || tenantId}
                  readOnly
                  className="mt-1 w-full rounded-xl border border-slate-200 bg-slate-100 px-3 py-2 text-sm"
                />
              </label>
              <label className="text-sm font-semibold text-slate-700">
                Agent Name
                <input
                  value={agentName}
                  onChange={(event) => setAgentName(event.target.value)}
                  className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                />
              </label>
              <GovernedSelect label="Agent Type" value={agentType} options={GOVERNED_AGENT_TYPES} onChange={setAgentType} />
              <GovernedSelect label="Owner Team" value={ownerTeam} options={GOVERNED_OWNER_TEAMS} onChange={setOwnerTeam} />
              <label className="text-sm font-semibold text-slate-700">
                Approval Status
                <select
                  value={approvalStatus}
                  onChange={(event) => setApprovalStatus(event.target.value)}
                  className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                >
                  <option value="PENDING_REVIEW">PENDING_REVIEW</option>
                  <option value="APPROVED">APPROVED</option>
                  <option value="REJECTED">REJECTED</option>
                  <option value="SUSPENDED">SUSPENDED</option>
                  <option value="REVOKED">REVOKED</option>
                </select>
                <p className="mt-1 text-xs font-normal text-slate-400">
                  用於人工審核誤判修正；若改回 APPROVED，仍需有效 credential 才會 Ready。
                </p>
              </label>
              <label className="text-sm font-semibold text-slate-700">
                Risk Status
                <select
                  value={riskStatus}
                  onChange={(event) => setRiskStatus(event.target.value)}
                  className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                >
                  <option value="NORMAL">NORMAL</option>
                  <option value="QUARANTINED">QUARANTINED</option>
                  <option value="SUSPENDED">SUSPENDED</option>
                  <option value="REVOKED">REVOKED</option>
                  <option value="COMPROMISED">COMPROMISED</option>
                </select>
              </label>
              <label className="flex items-center gap-2 pt-7 text-sm font-semibold text-slate-700">
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(event) => setEnabled(event.target.checked)}
                />{" "}
                Enabled
              </label>
              <div className="md:col-span-2 rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
                <div className="font-black text-blue-950">Capabilities are managed from Agent Detail</div>
                <p className="mt-1 leading-6">This edit dialog only updates the Core Agent profile metadata. Use Agent Detail &gt; Capabilities to request, approve, suspend, resume, or revoke capabilities with audit history.</p>
              </div>
              <div className="md:col-span-2"><LegacyValueWarning label="agent type" values={[agentType]} options={GOVERNED_AGENT_TYPES} /><LegacyValueWarning label="owner team" values={[ownerTeam]} options={GOVERNED_OWNER_TEAMS} /></div>
              <label className="md:col-span-2 text-sm font-semibold text-slate-700">
                Authorization Scopes — legacy review only
                <input
                  value={scopes}
                  onChange={(event) => setScopes(event.target.value)}
                  placeholder="SRC_E2E_7F28/*,FACTORY_IOT_01/*"
                  className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                />
                <p className="mt-1 text-xs font-normal text-amber-700">P2-J: scopes remain visible for legacy review only. Dispatch-affecting eligibility must move to Task Definition / Capability / Runtime Feature catalogs.</p>
              </label>
              <label className="md:col-span-2 text-sm font-semibold text-slate-700">
                Description
                <textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  className="mt-1 min-h-24 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
                />
              </label>
            </div>

            <div className="mt-5 flex justify-end gap-2 border-t border-slate-100 pt-4">
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={saving}
                className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void save()}
                disabled={saving}
                className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
              >
                {saving ? "Saving..." : "Save Core Profile"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
