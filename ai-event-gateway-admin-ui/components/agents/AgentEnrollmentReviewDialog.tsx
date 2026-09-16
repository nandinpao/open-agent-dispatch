"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useDialogAccessibility } from "@/hooks/useDialogAccessibility";
import { IsoDateTimePicker } from "@/components/common/IsoDateTimePicker";
import { CredentialTokenInput } from "@/components/agents/CredentialTokenInput";
import { CapabilityCardSelector } from "@/components/agents/CapabilityCardSelector";
import { GovernedSelect, LegacyValueWarning } from "@/components/governance/StrictSelectionControls";
import { GOVERNED_AGENT_TYPES, GOVERNED_OWNER_TEAMS } from "@/lib/governance/strictSelection";
import { coreAdminApi } from "@/lib/api/coreAdminApi";
import { AgentOwnershipAsyncFields } from "@/components/agents/AgentOwnershipAsyncFields";
import { getCoreTenantContext, requireCoreTenantContext } from "@/lib/api/coreClient";
import { useAuth } from "@/components/auth/AuthProvider";
import { canApproveEnrollmentForRow, isCorrectableEnrollmentStatus, isOpenEnrollmentStatus, isRejectedEnrollmentStatus } from "@/lib/agents/governanceStatus";
import {
  buildDefaultApprovalRequest,
  isRuntimeObservedEnrollment,
  parseCapabilitiesCsv,
  rowToEnrollmentRequest,
} from "@/lib/agents/enrollmentWorkflow";
import type { AgentDashboardRow } from "@/lib/types/dashboard";
import type {
  AgentEnrollmentApprovalRequest,
  AgentEnrollmentCreateRequest,
  CoreAgentCapabilityCatalog,
  CoreAgentAuthorizationScope,
} from "@/lib/types/core";

interface AgentApprovalDraft {
  agentId: string;
  agentName: string;
  agentType: string;
  tenantId: string;
  ownerTeam: string;
  ownerDepartmentId: string;
  ownerGroupId: string;
  businessOwnerUserId: string;
  technicalStewardUserId: string;
  responsibilityRoleId: string;
  description: string;
  capabilitiesCsv: string;
  scopes: CoreAgentAuthorizationScope[];
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
  // Runtime-observed Agents must render even before a Tenant is selected. Governance mutation
  // functions below remain fail-closed and require a Tenant at submit time.
  const tenantId = String(defaults.tenantId ?? row.profile?.tenantId ?? getCoreTenantContext() ?? "").trim();
  return {
    agentId: defaults.agentId ?? row.agentId,
    agentName: defaults.agentName ?? row.profile?.agentName ?? row.agentId,
    agentType: defaults.agentType ?? row.profile?.agentType ?? "UNKNOWN",
    tenantId,
    ownerTeam: defaults.ownerTeam ?? row.profile?.ownerTeam ?? "",
    ownerDepartmentId: defaults.ownerDepartmentId ?? row.profile?.ownerDepartmentId ?? "",
    ownerGroupId: defaults.ownerGroupId ?? row.profile?.ownerGroupId ?? "",
    businessOwnerUserId: defaults.businessOwnerUserId ?? row.profile?.businessOwnerUserId ?? "",
    technicalStewardUserId: defaults.technicalStewardUserId ?? row.profile?.technicalStewardUserId ?? "",
    responsibilityRoleId: defaults.responsibilityRoleId ?? row.profile?.responsibilityRoleId ?? "",
    description: defaults.description ?? row.profile?.description ?? "",
    capabilitiesCsv: (
      defaults.capabilities ??
      row.profile?.capabilities?.map(
        (capability) => capability.capabilityCode,
      ) ??
      []
    ).join(","),
    scopes: [...(defaults.scopes ?? row.profile?.authorizationScopes ?? [])],
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
    ownerDepartmentId: draft.ownerDepartmentId || undefined,
    ownerGroupId: draft.ownerGroupId || undefined,
    businessOwnerUserId: draft.businessOwnerUserId || undefined,
    technicalStewardUserId: draft.technicalStewardUserId || undefined,
    responsibilityRoleId: draft.responsibilityRoleId || undefined,
    description: draft.description.trim() || undefined,
    capabilities: parseCapabilitiesCsv(draft.capabilitiesCsv),
    scopes: draft.scopes,
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
    submittedMetadata: {
      source: row.enrollment?.enrollmentId?.startsWith("runtime:")
        ? "NETTY_RUNTIME_OBSERVATION"
        : "ADMIN_DRAFT_EDIT",
      originalEnrollmentId: row.enrollment?.enrollmentId,
      gatewayNodeId: row.runtime?.gatewayNodeId ?? row.runtime?.nodeId,
      sessionId: row.runtime?.sessionId,
      authorizationState: row.runtime?.authorizationState,
      ownerDepartmentId: draft.ownerDepartmentId || undefined,
      ownerGroupId: draft.ownerGroupId || undefined,
      businessOwnerUserId: draft.businessOwnerUserId || undefined,
      technicalStewardUserId: draft.technicalStewardUserId || undefined,
      responsibilityRoleId: draft.responsibilityRoleId || undefined,
      ownerTeam: draft.ownerTeam.trim() || undefined,
    },
    evidence: row.runtime?.payload ?? row.runtime ?? {},
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
  const { activeTenantId: selectedTenantId } = useAuth();
  const [open, setOpen] = useState(false);
  const dialogRef = useDialogAccessibility(open, () => setOpen(false));
  const [draft, setDraft] = useState<AgentApprovalDraft>(() => buildDraft(row));
  const [businessOwnerEligible, setBusinessOwnerEligible] = useState(true);
  const [submitting, setSubmitting] = useState<
    "save" | "approve" | "reject" | null
  >(null);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);
  const [approvalCompleted, setApprovalCompleted] = useState(false);
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
    if (!selectedTenantId) return;
    setDraft((current) => current.tenantId === selectedTenantId ? current : ({
      ...current,
      tenantId: selectedTenantId,
      ownerDepartmentId: "",
      ownerGroupId: "",
      businessOwnerUserId: "",
      technicalStewardUserId: "",
      responsibilityRoleId: "",
    }));
    setBusinessOwnerEligible(true);
  }, [selectedTenantId]);

  useEffect(() => {
    if (!open) {
      loadedDraftAgentRef.current = null;
      return;
    }
    const nextDraft = buildDraft(row);
    const loadKey = `${selectedTenantId || nextDraft.tenantId}:${row.agentId}`;
    if (loadedDraftAgentRef.current === loadKey) return;
    loadedDraftAgentRef.current = loadKey;
    setDraft(selectedTenantId && nextDraft.tenantId !== selectedTenantId ? ({
      ...nextDraft,
      tenantId: selectedTenantId,
      ownerDepartmentId: "",
      ownerGroupId: "",
      businessOwnerUserId: "",
      technicalStewardUserId: "",
      responsibilityRoleId: "",
    }) : nextDraft);
    setBusinessOwnerEligible(true);
    setError(null);
    setSavedMessage(null);
    setApprovalCompleted(false);
  }, [open, row, selectedTenantId]);

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
    return () => {
      cancelled = true;
    };
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

  async function activateObservedRuntimeBinding(agentId: string, tenantId: string, agentType: string) {
    const runtime = row.runtime;
    if (!runtime || runtime.connected === false) return null;
    const gatewayNodeId = runtime.gatewayNodeId ?? runtime.nodeId ?? "gateway-node-unknown";
    const runtimeCode = `${agentId}-${gatewayNodeId}-runtime`
      .trim()
      .replace(/[^A-Za-z0-9]+/g, "_")
      .replace(/^_+|_+$/g, "")
      .toUpperCase();
    const runtimeId = `runtime-${runtimeCode.toLowerCase().replace(/_/g, "-")}`;
    const capacityLimit = Math.max(1, runtime.activeTaskCount === undefined ? 1 : runtime.activeTaskCount + 1);
    await coreAdminApi.upsertRuntimeResource(runtimeId, {
      tenantId,
      runtimeId,
      runtimeCode,
      runtimeName: `${agentId} runtime on ${gatewayNodeId}`,
      runtimeType: agentType || "AGENT_RUNTIME",
      connectorType: "GATEWAY_RUNTIME",
      executionHost: gatewayNodeId,
      environment: "runtime-observation",
      trustStatus: "TRUSTED",
      status: "ACTIVE",
      capacityLimit,
      metadata: {
        source: "Agent Governance approval",
        gatewayNodeId,
        agentSessionId: runtime.sessionId,
        dispatchAuthority: "ACTIVE_RUNTIME_BINDING",
      },
    }, tenantId);
    return coreAdminApi.upsertAgentRuntimeBinding(agentId, {
      tenantId,
      agentId,
      runtimeId,
      runtimeCode,
      bindingStatus: "ACTIVE",
      verifiedBy: "admin-ui",
      capacityLimit,
      dataScope: "STANDARD",
      riskLimit: "MIDDLE",
      metadata: {
        source: "Agent Governance approval",
        gatewayNodeId,
        agentSessionId: runtime.sessionId,
        dispatchAuthority: "ACTIVE_RUNTIME_BINDING",
      },
    });
  }

  async function approve() {
    if (!canApproveEnrollment) {
      setError("Enrollment approval cannot restore a blocked Core Agent profile. Use Restore Approve with new credential material.");
      return;
    }
    if (!draft.ownerDepartmentId || !draft.businessOwnerUserId || !draft.responsibilityRoleId) {
      setError("Owner Department, Business Owner and Agent Responsibility are required before approval.");
      return;
    }
    if (!businessOwnerEligible) {
      setError("The selected Business Owner is not an active Tenant member and active member of the selected Owner Department. Choose an eligible Department member before approval.");
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
      const approved = await coreAdminApi.approveAgentEnrollment(enrollmentId, approvalRequest);
      setApprovalCompleted(true);
      let bindingError: string | null = null;
      try {
        await activateObservedRuntimeBinding(
          approved.agentId ?? draft.agentId,
          requireCoreTenantContext(approved.tenantId ?? draft.tenantId),
          approved.agentType ?? draft.agentType,
        );
      } catch (bindingErr) {
        bindingError = bindingErr instanceof Error ? bindingErr.message : String(bindingErr);
      }
      await onChanged?.();
      if (bindingError) {
        setSavedMessage("Agent Governance approval succeeded. The runtime binding still needs attention before dispatch can become active.");
        setError(`Governance approval succeeded, but runtime binding activation failed: ${bindingError}`);
        return;
      }
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
          ref={dialogRef}
          tabIndex={-1}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4 outline-none"
          role="dialog"
          aria-modal="true"
          aria-label="Review Agent enrollment"
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
                   Agent draft, again run Approve / Reject.
                  {isObservedOnly
                    ? "this records is runtime observation fallback;Healthy Agent  Core Create enrollment."
                    : openEnrollment
                      ? "this recordshas Core enrollment"
                      : correctableEnrollment
                        ? " Rejected credential  Approve."
                        : "Error, canuse Edit "}
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
              <AgentOwnershipAsyncFields
                tenantId={selectedTenantId || draft.tenantId}
                idPrefix={`enrollment-${row.agentId}`}
                values={draft}
                onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
                onBusinessOwnerEligibilityChange={setBusinessOwnerEligible}
                disabled={Boolean(submitting)}
              />
              <CredentialTokenInput
                className="space-y-1 text-sm font-semibold text-slate-700 xl:col-span-2"
                inputClassName={inputClass}
                value={draft.credentialToken}
                onChange={(value) => setField("credentialToken", value)}
                placeholder="required before Approve / Approve Again"
                helperText="Generate Token  Web Crypto  256-bit tokenReview the configuration and try again. Agent  AGENT_ONBOARDING_TOKEN  credential configuration."
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
              <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">
                <div className="font-black">Dispatch Access is configured after approval</div>
                <p className="mt-1 leading-6">Enrollment review establishes identity, ownership, credential and Capability approval. Use Agent Detail &gt; Dispatch Access afterward to grant canonical Source System + Task Type access. Existing access is preserved during re-approval.</p>
              </div>
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
                disabled={Boolean(submitting) || approvalCompleted || !canApproveEnrollment || !draft.agentId.trim() || !draft.ownerDepartmentId || !draft.businessOwnerUserId || !businessOwnerEligible || !draft.responsibilityRoleId || !draft.credentialToken.trim()}
                title={!canApproveEnrollment ? "Enrollment approval cannot restore a blocked Core Agent profile. Use Restore Approve with new credential material." : !businessOwnerEligible ? "Business Owner must be an active member of the selected Owner Department" : !draft.credentialToken.trim() ? "Credential Token is required before Approve" : undefined}
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
