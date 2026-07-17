"use client";

import { useState } from "react";
import { IsoDateTimePicker } from "@/components/common/IsoDateTimePicker";
import { CredentialTokenInput } from "@/components/agents/CredentialTokenInput";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import type { AgentProfileApprovalRequest, CoreAgentProfile } from "@/lib/types/core";

interface AgentProfileApprovalDialogProps {
  profile: CoreAgentProfile;
  triggerLabel?: string;
  onSaved?: () => Promise<void> | void;
}

function upper(value?: string): string {
  return (value ?? "").trim().toUpperCase();
}

function needsNewCredential(profile: CoreAgentProfile): boolean {
  const status = upper(profile.credential?.credentialStatus);
  return status !== "ACTIVE";
}

function defaultReason(profile: CoreAgentProfile): string {
  const status = upper(profile.approvalStatus);
  if (status === "REVOKED") return "Restore mistakenly revoked Agent profile to APPROVED from Admin UI";
  if (status === "REJECTED") return "Approve mistakenly rejected Agent profile from Admin UI";
  if (status === "SUSPENDED") return "Restore suspended Agent profile to APPROVED from Admin UI";
  return "Approve Agent profile from Admin UI";
}

export function AgentProfileApprovalDialog({
  profile,
  triggerLabel = "Approve / Restore",
  onSaved,
}: Readonly<AgentProfileApprovalDialogProps>) {
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [credentialType, setCredentialType] = useState("TOKEN");
  const [credentialToken, setCredentialToken] = useState("");
  const [credentialHash, setCredentialHash] = useState("");
  const [publicKeyFingerprint, setPublicKeyFingerprint] = useState("");
  const [credentialExpiresAt, setCredentialExpiresAt] = useState("");
  const [operatorId, setOperatorId] = useState("admin-ui");
  const [reason, setReason] = useState(defaultReason(profile));
  const [enabled, setEnabled] = useState(true);
  const [revokeExisting, setRevokeExisting] = useState(true);

  const credentialRequired = needsNewCredential(profile);
  const hasCredentialMaterial = Boolean(credentialToken.trim() || credentialHash.trim() || publicKeyFingerprint.trim());
  const saveDisabled = saving || (credentialRequired && !hasCredentialMaterial);

  function resetDraft() {
    setCredentialType("TOKEN");
    setCredentialToken("");
    setCredentialHash("");
    setPublicKeyFingerprint("");
    setCredentialExpiresAt("");
    setOperatorId("admin-ui");
    setReason(defaultReason(profile));
    setEnabled(true);
    setRevokeExisting(true);
    setError(null);
  }

  async function save() {
    if (credentialRequired && !hasCredentialMaterial) {
      setError("This Agent has no active credential. Use Credential Token for the normal TOKEN / OpenClaw / local simulator flow. Advanced hash or public key fingerprint material should only be used for explicitly configured non-default Core contracts.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const body: AgentProfileApprovalRequest = {
        operatorId: operatorId.trim() || undefined,
        reason: reason.trim() || undefined,
        enabled,
        riskStatus: "NORMAL",
        credentialType,
        credentialToken: credentialToken.trim() || undefined,
        credentialHash: credentialHash.trim() || undefined,
        publicKeyFingerprint: publicKeyFingerprint.trim() || undefined,
        credentialExpiresAt: credentialExpiresAt.trim() || undefined,
        revokeExisting,
      };
      await coreAdminApi.approveAgent(profile.agentId, body);
      await onSaved?.();
      setOpen(false);
      resetDraft();
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
        onClick={() => {
          resetDraft();
          setOpen(true);
        }}
        className="rounded-lg border border-emerald-200 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50"
        title="Restore REJECTED, SUSPENDED, or REVOKED Core Agent profiles back to APPROVED with audit trail."
      >
        {triggerLabel}
      </button>

      {open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-auto rounded-2xl bg-white p-5 shadow-xl">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-slate-900">{triggerLabel}</h2>
                <p className="mt-1 text-sm text-slate-600">
                  Agent <span className="font-semibold">{profile.agentId}</span> will be restored to <span className="font-semibold">APPROVED</span>, risk <span className="font-semibold">NORMAL</span>, and optionally enabled.
                </p>
              </div>
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg border px-2 py-1 text-xs text-slate-600 hover:bg-slate-50">
                Close
              </button>
            </div>

            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
              {credentialRequired
                ? "This profile does not have an active credential. Use a real token for the normal TOKEN / OpenClaw / local simulator flow; Core will not create fake credential material."
                : "This profile already has an active credential. Credential fields are optional; provide a token only when rotating during approval. Hash/fingerprint fields are advanced compatibility material."}
            </div>

            <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-3 text-xs leading-5 text-blue-800">
              P2B default flow is token-first. Local simulator, cluster-run-many-agents.sh, and OpenClaw plugin static/JWT-file modes present bearer token material to Core. Public key fingerprint is reserved for Advanced Security and is not part of the normal approval path.
            </div>

            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <IsoDateTimePicker value={credentialExpiresAt} onChange={setCredentialExpiresAt} />
              <CredentialTokenInput
                value={credentialToken}
                onChange={setCredentialToken}
                required={credentialRequired}
                onGenerateError={setError}
                label="Credential Token / Bearer Token"
                helperText="Normal OpenDispatch / OpenClaw flow: issue a token here, then deploy the same value as AGENT_ONBOARDING_TOKEN, OPENSOCKET_AGENT_TOKEN, or the token loaded by the OpenClaw JWT credential file. Core stores only the hash."
              />
              <details className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm md:col-span-2">
                <summary className="cursor-pointer font-black text-slate-800">Advanced Security · Hash / Public Key Fingerprint</summary>
                <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
                  Public-key / certificate fingerprint material is not used by the local TCP simulator or current OpenClaw plugin challenge/signature flow. Use these fields only when a non-default Core credential contract explicitly requires them.
                </div>
                <div className="mt-3 grid gap-3 md:grid-cols-2">
                  <label className="space-y-1 text-sm">
                    <span className="font-medium text-slate-700">Credential Type</span>
                    <select className="w-full rounded-lg border px-3 py-2" value={credentialType} onChange={(event) => setCredentialType(event.target.value)}>
                      <option value="TOKEN">TOKEN</option>
                      <option value="PUBLIC_KEY">PUBLIC_KEY</option>
                      <option value="CERTIFICATE">CERTIFICATE</option>
                    </select>
                  </label>
                  <label className="space-y-1 text-sm md:col-span-2">
                    <span className="font-medium text-slate-700">Credential Hash {credentialRequired ? "*" : ""}</span>
                    <input className="w-full rounded-lg border px-3 py-2" value={credentialHash} onChange={(event) => setCredentialHash(event.target.value)} placeholder="precomputed SHA-256 hash; optional when token is supplied" />
                  </label>
                  <label className="space-y-1 text-sm md:col-span-2">
                    <span className="font-medium text-slate-700">Public Key Fingerprint {credentialRequired ? "*" : ""}</span>
                    <input className="w-full rounded-lg border px-3 py-2" value={publicKeyFingerprint} onChange={(event) => setPublicKeyFingerprint(event.target.value)} placeholder="advanced only; not used by current OpenClaw/local simulator flow" />
                  </label>
                </div>
              </details>
              <label className="space-y-1 text-sm">
                <span className="font-medium text-slate-700">Operator</span>
                <input className="w-full rounded-lg border px-3 py-2" value={operatorId} onChange={(event) => setOperatorId(event.target.value)} />
              </label>
              <label className="flex items-center gap-2 pt-7 text-sm text-slate-700">
                <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
                Enable profile after approval
              </label>
              <label className="flex items-center gap-2 text-sm text-slate-700 md:col-span-2">
                <input type="checkbox" checked={revokeExisting} onChange={(event) => setRevokeExisting(event.target.checked)} />
                Revoke existing active credentials when new credential material is supplied
              </label>
              <label className="space-y-1 text-sm md:col-span-2">
                <span className="font-medium text-slate-700">Reason</span>
                <textarea className="min-h-20 w-full rounded-lg border px-3 py-2" value={reason} onChange={(event) => setReason(event.target.value)} />
              </label>
            </div>

            {error ? <div className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</div> : null}

            <div className="mt-5 flex justify-end gap-2">
              <button type="button" onClick={() => setOpen(false)} className="rounded-lg border px-4 py-2 text-sm text-slate-700 hover:bg-slate-50">
                Cancel
              </button>
              <button
                type="button"
                disabled={saveDisabled}
                onClick={() => void save()}
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saving ? "Saving..." : triggerLabel}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
