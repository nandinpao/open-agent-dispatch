"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { IsoDateTimePicker } from "@/components/common/IsoDateTimePicker";
import { CredentialTokenInput } from "@/components/agents/CredentialTokenInput";
import { CapabilityCardSelector } from "@/components/agents/CapabilityCardSelector";
import { GovernedSelect, LegacyValueWarning } from "@/components/governance/StrictSelectionControls";
import { GOVERNED_AGENT_TYPES, GOVERNED_OWNER_TEAMS } from "@/lib/governance/strictSelection";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import { requireCoreTenantContext } from "@/lib/api/coreClient";
import { useAuth } from "@/components/auth/AuthProvider";
import { canApproveEnrollmentForRow, isCorrectableEnrollmentStatus, isOpenEnrollmentStatus, isRejectedEnrollmentStatus } from "@/lib/agents/governanceStatus";
import {
  buildDefaultApprovalRequest,
  isRuntimeObservedEnrollment,
  parseCapabilitiesCsv,
  parseScopeCsv,
  rowToEnrollmentRequest,
} from "@/lib/agents/enrollmentWorkflow";
import type { AgentDashboardRow } from "@/lib/types/dashboard";
import type {
  AgentEnrollmentApprovalRequest,
  AgentEnrollmentCreateRequest,
  CoreAgentCapabilityCatalog,
} from "@/lib/types/core";

interface AgentApprovalDraft {
  agentId: string;
  agentName: string;
  agentType: string;
  tenantId: string;
  ownerTeam: string;
  description: string;
  capabilitiesCsv: string;
  scopesCsv: string;
  credentialToken: string;
  credentialExpiresAt: string;
  comment: string;
}

interface AgentEnrollmentReviewDialogProps {
  row: AgentDashboardRow;
  triggerLabel?: string;
  intent?: "edit" | "approve" | "reject";
  onChanged?: () => Promise<void> | void;
}

function buildDraft(row: AgentDashboardRow): AgentApprovalDraft {
  const enrollment = row.enrollment;
  const defaults = enrollment ? buildDefaultApprovalRequest(enrollment) : {};
  const tenantId = requireCoreTenantContext(defaults.tenantId ?? row.profile?.tenantId);
  return {
    agentId: defaults.agentId ?? row.agentId,
    agentName: defaults.agentName ?? row.profile?.agentName ?? row.agentId,
    agentType: defaults.agentType ?? row.profile?.agentType ?? "UNKNOWN",
    tenantId,
    ownerTeam: defaults.ownerTeam ?? row.profile?.ownerTeam ?? "",
    description: defaults.description ?? row.profile?.description ?? "",
    capabilitiesCsv: (
      defaults.capabilities ??
      row.profile?.capabilities?.map(
        (capability) => capability.capabilityCode,
      ) ??
      []
    ).join(","),
    scopesCsv: (
      defaults.scopes ??
      row.profile?.authorizationScopes ?? [
        { systemCode: "*", taskType: "*", tenantId, enabled: true },
      ]
    )
      .map((scope) =>
        [scope.systemCode ?? "*", scope.taskType ?? "*", scope.siteCode]
          .filter(Boolean)
          .join("/"),
      )
      .join(","),
    credentialToken: defaults.credentialToken ?? "",
    credentialExpiresAt: defaults.credentialExpiresAt ?? "",
    comment: defaults.comment ?? "",
  };
}

function draftToApprovalRequest(
  draft: AgentApprovalDraft,
): AgentEnrollmentApprovalRequest {
  const tenantId = requireCoreTenantContext(draft.tenantId);
  const agentId = draft.agentId.trim();
  return {
    agentId,
    tenantId,
    agentName: draft.agentName.trim() || agentId,
    agentType: draft.agentType.trim() || "UNKNOWN",
    ownerTeam: draft.ownerTeam.trim() || undefined,
    description: draft.description.trim() || undefined,
    capabilities: parseCapabilitiesCsv(draft.capabilitiesCsv),
    scopes: parseScopeCsv(draft.scopesCsv || "*/*", tenantId),
    credentialToken: draft.credentialToken.trim() || undefined,
    credentialExpiresAt: draft.credentialExpiresAt.trim() || undefined,
    comment: draft.comment.trim() || undefined,
  };
}

function draftToEnrollmentRequest(
  draft: AgentApprovalDraft,
  row: AgentDashboardRow,
): AgentEnrollmentCreateRequest {
  const tenantId = requireCoreTenantContext(draft.tenantId);
  const agentId = draft.agentId.trim() || row.agentId;
  return {
    claimedAgentId: agentId,
    tenantId,
    agentName: draft.agentName.trim() || agentId,
    agentType: draft.agentType.trim() || "UNKNOWN",
    submittedMetadataJson: {
      source: row.enrollment?.enrollmentId?.startsWith("runtime:")
        ? "NETTY_RUNTIME_OBSERVATION"
        : "ADMIN_DRAFT_EDIT",
      originalEnrollmentId: row.enrollment?.enrollmentId,
      gatewayNodeId: row.runtime?.gatewayNodeId ?? row.runtime?.nodeId,
      sessionId: row.runtime?.sessionId,
      authorizationState: row.runtime?.authorizationState,
    },
    evidenceJson: row.runtime?.payload ?? row.runtime ?? {},
    fingerprint: row.enrollment?.fingerprint,
    remoteAddress: row.enrollment?.remoteAddress ?? row.runtime?.remoteAddress,
    submittedAt:
      row.enrollment?.submittedAt ??
      row.runtime?.connectedAt ??
      row.runtime?.lastSeenAt,
  };
}

export function AgentEnrollmentReviewDialog({
  row,
  triggerLabel = "Review / Approve",
  intent = "edit",
  onChanged,
}: Readonly<AgentEnrollmentReviewDialogProps>) {
  const { selectedTenantId } = useAuth();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<AgentApprovalDraft>(() => buildDraft(row));
  const [submitting, setSubmitting] = useState<
    "save" | "approve" | "reject" | null
  >(null);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);
  const [capabilityCatalog, setCapabilityCatalog] = useState<CoreAgentCapabilityCatalog[]>([]);
  const [loadingCapabilities, setLoadingCapabilities] = useState(false);
  const loadedDraftAgentRef = useRef<string | null>(null);

  const enrollment = row.enrollment;
  const canReview = Boolean(enrollment || (!row.profile && row.runtime));
  const isObservedOnly = enrollment
    ? isRuntimeObservedEnrollment(enrollment)
    : false;
  const openEnrollment = isOpenEnrollmentStatus(enrollment?.status);
  const correctableEnrollment = isCorrectableEnrollmentStatus(enrollment?.status);
  const canApproveEnrollment = canApproveEnrollmentForRow(row);
  const triggerClass =
    intent === "reject"
      ? "rounded-lg border border-rose-200 px-2 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-50"
      : intent === "approve"
        ? "rounded-lg border border-emerald-200 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50"
        : "rounded-lg border border-amber-200 px-2 py-1 text-xs font-semibold text-amber-700 hover:bg-amber-50";

  useEffect(() => {
    if (selectedTenantId) setDraft((current) => ({ ...current, tenantId: selectedTenantId }));
  }, [selectedTenantId]);

  useEffect(() => {
    if (!open) {
      loadedDraftAgentRef.current = null;
      return;
    }
    if (loadedDraftAgentRef.current === row.agentId) return;
    loadedDraftAgentRef.current = row.agentId;
    setDraft(buildDraft(row));
    setError(null);
    setSavedMessage(null);
  }, [open, row]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoadingCapabilities(true);
    coreAdminApi.getCapabilities("ACTIVE", undefined, selectedTenantId)
      .then((items) => {
        if (!cancelled) setCapabilityCatalog(items);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      })
      .finally(() => {
        if (!cancelled) setLoadingCapabilities(false);
      });
    return () => { cancelled = true; };
  }, [open, selectedTenantId]);

  const approvalRequest = useMemo(() => draftToApprovalRequest(draft), [draft]);

  function setField<K extends keyof AgentApprovalDraft>(
    key: K,
    value: AgentApprovalDraft[K],
  ) {
    setSavedMessage(null);
    setDraft((current) => ({ ...current, [key]: value }));
  }

  async function resolveCoreEnrollmentId(): Promise<string> {
    if (!enrollment) {
      const request = rowToEnrollmentRequest(row);
      if (!request)
        throw new Error(
          "Cannot create enrollment without Netty runtime observation.",
        );
      const created = await coreAdminApi.createAgentEnrollment(request);
      return created.enrollmentId;
    }

    if (isRuntimeObservedEnrollment(enrollment)) {
      const request = rowToEnrollmentRequest(row);
      if (!request)
        throw new Error(
          "Cannot create Core enrollment from runtime observation.",
        );
      const created = await coreAdminApi.createAgentEnrollment(request);
      return created.enrollmentId;
    }

    return enrollment.enrollmentId;
  }

  async function saveDraft() {
    setSubmitting("save");
    setError(null);
    try {
      await coreAdminApi.createAgentEnrollment(
        draftToEnrollmentRequest(draft, row),
      );
      setSavedMessage(
        "Edit saved to Core enrollment draft. You can keep editing, approve, or reject.",
      );
      await onChanged?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(null);
    }
  }

  async function approve() {
    if (!canApproveEnrollment) {
      setError("Enrollment approval cannot restore a blocked Core Agent profile. Use Restore Approve with new credential material.");
      return;
    }
    if (!draft.credentialToken.trim()) {
      setError("Credential Token is required before approving an Agent. Approved Agents must be immediately connectable through Core authorization.");
      return;
    }
    setSubmitting("approve");
    setError(null);
    try {
      const enrollmentId = await resolveCoreEnrollmentId();
      await coreAdminApi.approveAgentEnrollment(enrollmentId, approvalRequest);
      await onChanged?.();
      setOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(null);
    }
  }

  async function reject() {
    setSubmitting("reject");
    setError(null);
    try {
      const enrollmentId = await resolveCoreEnrollmentId();
      await coreAdminApi.rejectAgentEnrollment(enrollmentId, {
        rejectedBy: "admin-ui",
        reason: draft.comment.trim() || "Rejected from Agent Governance Console",
      });
      await onChanged?.();
      setOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(null);
    }
  }

  if (!canReview) return null;

  const inputClass =
    "mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100";

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={triggerClass}
      >
        {triggerLabel}
      </button>

      {open ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-lg font-bold text-slate-900">
                  {intent === "reject"
                    ? "Reject Agent Enrollment"
                    : intent === "approve"
                      ? "Approve Agent Enrollment"
                      : "Edit Agent Governance Draft"}
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  先確認或修正 Agent draft，再執行 Approve / Reject。
                  {isObservedOnly
                    ? "此筆為 runtime observation fallback；正常情況下 Agent 連線時 Core 應已建立 enrollment。"
                    : openEnrollment
                      ? "此筆已有 Core enrollment，可直接審核。"
                      : correctableEnrollment
                        ? "此筆先前已被 Rejected；若為人工審核誤判，可補 credential 後直接 Approve。"
                        : "此筆已審核完成；若資料輸入錯誤，可使用 Edit 修正。"}
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
            {savedMessage ? (
              <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">
                {savedMessage}
              </div>
            ) : null}

            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <label className="text-sm font-semibold text-slate-700">
                Agent ID
                <input
                  value={draft.agentId}
                  onChange={(event) => setField("agentId", event.target.value)}
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-semibold text-slate-700">
                Agent Name
                <input
                  value={draft.agentName}
                  onChange={(event) =>
                    setField("agentName", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <GovernedSelect label="Agent Type" value={draft.agentType} options={GOVERNED_AGENT_TYPES} onChange={(value) => setField("agentType", value)} />
              <label className="text-sm font-semibold text-slate-700">
                Tenant
                <input
                  value={selectedTenantId || draft.tenantId}
                  readOnly
                  className={inputClass}
                />
              </label>
              <GovernedSelect label="Owner Team" value={draft.ownerTeam} options={GOVERNED_OWNER_TEAMS} onChange={(value) => setField("ownerTeam", value)} />
              <CredentialTokenInput
                className="space-y-1 text-sm font-semibold text-slate-700 xl:col-span-2"
                inputClassName={inputClass}
                value={draft.credentialToken}
                onChange={(value) => setField("credentialToken", value)}
                placeholder="required before Approve / Approve Again"
                helperText="Generate Token 會使用瀏覽器 Web Crypto 產生 256-bit token；請同步更新實際 Agent 的 AGENT_ONBOARDING_TOKEN 或對應 credential 設定。"
                onGenerateError={setError}
              />
              <IsoDateTimePicker
                className="space-y-1 text-sm font-semibold text-slate-700"
                inputClassName={inputClass}
                value={draft.credentialExpiresAt}
                onChange={(value) => setField("credentialExpiresAt", value)}
              />
              <div className="xl:col-span-2 space-y-2">
                <LegacyValueWarning label="agent type" values={[draft.agentType]} options={GOVERNED_AGENT_TYPES} />
                <LegacyValueWarning label="owner team" values={[draft.ownerTeam]} options={GOVERNED_OWNER_TEAMS} />
                <CapabilityCardSelector
                  capabilities={capabilityCatalog}
                  selectedCodes={parseCapabilitiesCsv(draft.capabilitiesCsv)}
                  onChange={(codes) => setField("capabilitiesCsv", codes.join(","))}
                  loading={loadingCapabilities}
                  title="Approved capability cards"
                  description="Select only governed ACTIVE capabilities from the catalog. Free-form CSV is no longer a trusted dispatch input."
                />
              </div>
              <label className="text-sm font-semibold text-slate-700">
                Scopes CSV
                <input
                  value={draft.scopesCsv}
                  onChange={(event) =>
                    setField("scopesCsv", event.target.value)
                  }
                  placeholder="SRC_E2E_7F28/*,FACTORY_IOT_01/*"
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-semibold text-slate-700 xl:col-span-2">
                Description
                <textarea
                  value={draft.description}
                  onChange={(event) =>
                    setField("description", event.target.value)
                  }
                  className={`${inputClass} min-h-20`}
                />
              </label>
              <label className="text-sm font-semibold text-slate-700">
                Review Comment
                <textarea
                  value={draft.comment}
                  onChange={(event) => setField("comment", event.target.value)}
                  className={`${inputClass} min-h-20`}
                />
              </label>
            </div>

            <div className="mt-5 flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={Boolean(submitting)}
                className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void saveDraft()}
                disabled={Boolean(submitting) || !draft.agentId.trim()}
                className="rounded-xl border border-amber-200 px-4 py-2 text-sm font-bold text-amber-700 hover:bg-amber-50 disabled:opacity-50"
              >
                {submitting === "save" ? "Saving..." : "Save Edit"}
              </button>
              <button
                type="button"
                onClick={() => void reject()}
                disabled={Boolean(submitting)}
                className="rounded-xl border border-rose-200 px-4 py-2 text-sm font-bold text-rose-700 hover:bg-rose-50 disabled:opacity-50"
              >
                {submitting === "reject" ? "Rejecting..." : "Reject"}
              </button>
              <button
                type="button"
                onClick={() => void approve()}
                disabled={Boolean(submitting) || !canApproveEnrollment || !draft.agentId.trim() || !draft.credentialToken.trim()}
                title={!canApproveEnrollment ? "Enrollment approval cannot restore a blocked Core Agent profile. Use Restore Approve with new credential material." : !draft.credentialToken.trim() ? "Credential Token is required before Approve" : undefined}
                className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300"
              >
                {submitting === "approve" ? "Approving..." : isRejectedEnrollmentStatus(enrollment?.status) ? "Approve Again" : "Approve"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
