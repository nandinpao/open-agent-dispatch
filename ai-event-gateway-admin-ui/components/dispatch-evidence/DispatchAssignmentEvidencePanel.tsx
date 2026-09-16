import Link from "next/link";
import { EmptyState } from "@/components/common/EmptyState";
import { normalizeOperatorDispatchFailureReason } from "@/lib/dispatch-evidence/operatorFailureReasons";
import { StatusBadge } from "@/components/common/StatusBadge";
import { formatDateTime } from "@/lib/utils/format";
import { buildDispatchAssignmentEvidence, type DispatchAssignmentEvidencePanelProps } from "@/components/dispatch-evidence/dispatchAssignmentEvidenceModel";
import { ChipList, DeliveryStep, EvidenceCard } from "@/components/dispatch-evidence/DispatchEvidencePrimitives";
import { OperatorFailureReasonCard } from "@/components/dispatch-evidence/OperatorFailureReasonCard";

export { buildDispatchAssignmentEvidence } from "@/components/dispatch-evidence/dispatchAssignmentEvidenceModel";

export function DispatchAssignmentEvidencePanel(
  props: Readonly<DispatchAssignmentEvidencePanelProps>,
) {
  const evidence = buildDispatchAssignmentEvidence(props);
  const dispatchStatus =
    evidence.latestRequest?.eligibilityStatus ??
    evidence.latestRequest?.status ??
    props.task?.dispatchStatus;
  const issueLabel = evidence.issue?.issueId
    ? `${evidence.issue.issueVendor ?? "Issue"} ${evidence.issue.issueId}`
    : undefined;
  const operatorReason = normalizeOperatorDispatchFailureReason({
    task: props.task,
    latestDecision: evidence.latestDecision,
    latestRequest: evidence.latestRequest,
    selectedAgentId: evidence.selectedAgentId,
    rawRequirements: evidence.rawRequirements,
    effectiveCapabilities: evidence.effectiveCapabilities,
    runtimeCapabilities: evidence.runtimeCapabilities,
    setupReadiness: props.setupReadiness,
  });

  if (props.compact) {
    return (
      <div className="space-y-2 rounded-2xl border border-slate-200 bg-slate-50 p-3 text-xs">
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full bg-white px-2 py-0.5 font-black uppercase tracking-wide text-slate-600">
            {operatorReason.code}
          </span>
          <span className="font-bold text-slate-700">
            {operatorReason.title}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Raw</span>
          <ChipList values={evidence.rawRequirements} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Effective</span>
          <ChipList values={evidence.effectiveCapabilities} />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Selected</span>
          {evidence.selectedAgentId ? (
            <Link
              href={`/agents/${encodeURIComponent(evidence.selectedAgentId)}`}
              className="font-black text-blue-700 hover:text-blue-800"
            >
              {evidence.selectedAgentId}
            </Link>
          ) : (
            <span className="text-slate-400">-</span>
          )}
          {dispatchStatus ? <StatusBadge status={dispatchStatus} /> : null}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-black text-slate-700">Delivery</span>
          <StatusBadge
            status={
              evidence.deliveryConfirmation.delivered ? "DELIVERED" : "PENDING"
            }
          />
          <StatusBadge
            status={
              evidence.deliveryConfirmation.terminal ? "RESULT" : "NO_RESULT"
            }
          />
        </div>
      </div>
    );
  }

  const hasEvidence =
    evidence.rawRequirements.length ||
    evidence.effectiveCapabilities.length ||
    evidence.selectedAgentId ||
    evidence.latestRequest ||
    evidence.issue;

  return (
    <section className="rounded-2xl border border-blue-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-blue-600">
            Dispatch Assignment Evidence
          </div>
          <h2 className="mt-1 text-base font-black text-slate-950">
            {props.title ?? "Why this task was assigned or blocked"}
          </h2>
          <p className="mt-1 text-sm leading-6 text-slate-600">
            {props.description ??
              "Evidence from Source Flow and Agent Pool through Core-approved Required Capability qualification, runtime eligibility, routing selection, dispatch delivery, callbacks, and issue tracking. Runtime-reported capability values are shown separately as diagnostics only."}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className="rounded-full border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-black uppercase tracking-wide text-indigo-700">Capability qualification: canonical</span>
          {evidence.latestDecision?.status ? (
            <StatusBadge status={evidence.latestDecision.status} />
          ) : null}
          {dispatchStatus ? <StatusBadge status={dispatchStatus} /> : null}
        </div>
      </div>

      <OperatorFailureReasonCard reason={operatorReason} />

      {!hasEvidence ? (
        <div className="mt-4">
          <EmptyState
            title="No dispatch assignment evidence yet"
            description="Core has not created a routing decision, assignment, dispatch request, or issue link for this task yet."
          />
        </div>
      ) : null}

      {hasEvidence ? (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <EvidenceCard
              label="Task Required Capabilities"
              tone={evidence.rawRequirements.length ? "good" : "neutral"}
            >
              <ChipList values={evidence.rawRequirements} empty="No Required Capability configured for this task" />
            </EvidenceCard>
            <EvidenceCard
              label="Canonical Capability Qualification"
              tone={evidence.effectiveCapabilities.length ? "good" : "warn"}
            >
              <ChipList
                values={evidence.effectiveCapabilities}
                empty="No canonical capability qualification evidence yet"
              />
            </EvidenceCard>
            <EvidenceCard
              label="Selected Agent"
              tone={evidence.selectedAgentId ? "good" : "warn"}
            >
              {evidence.selectedAgentId ? (
                <Link
                  href={`/agents/${encodeURIComponent(evidence.selectedAgentId)}`}
                  className="text-blue-700 hover:text-blue-800"
                >
                  {evidence.selectedAgentId}
                </Link>
              ) : (
                "-"
              )}
              {evidence.latestDecision?.selectedScore !== undefined ? (
                <div className="mt-1 text-xs text-slate-500">
                  score: {evidence.latestDecision.selectedScore}
                </div>
              ) : null}
            </EvidenceCard>
            <EvidenceCard
              label="Runtime diagnostics"
              tone={evidence.runtimeCapabilities.length ? "good" : "warn"}
            >
              <ChipList
                values={evidence.runtimeCapabilities}
                empty="No runtime capability diagnostic evidence"
              />
            </EvidenceCard>
            <EvidenceCard
              label="Dispatch Request"
              tone={
                dispatchStatus && !String(dispatchStatus).includes("BLOCK")
                  ? "good"
                  : "warn"
              }
            >
              <div className="break-all">
                {evidence.latestRequest?.dispatchRequestId ??
                  props.task?.dispatchRequestId ??
                  "-"}
              </div>
              <div className="mt-1 flex flex-wrap gap-1.5">
                {dispatchStatus ? (
                  <StatusBadge status={dispatchStatus} />
                ) : null}
                {evidence.latestRequest?.status ? (
                  <StatusBadge status={evidence.latestRequest.status} />
                ) : null}
              </div>
            </EvidenceCard>
            <EvidenceCard
              label="Issue Link"
              tone={
                evidence.issue?.issueUrl || evidence.issue?.issueId
                  ? "good"
                  : "neutral"
              }
            >
              {evidence.issue?.issueUrl ? (
                <a
                  href={evidence.issue.issueUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="break-all text-blue-700 hover:text-blue-800"
                >
                  {issueLabel ?? evidence.issue.issueUrl}
                </a>
              ) : issueLabel ? (
                <span>{issueLabel}</span>
              ) : (
                "-"
              )}
            </EvidenceCard>

            <EvidenceCard
              label="Durable outbox"
              tone={
                evidence.latestRequest?.outboxStatus === "ACKNOWLEDGED"
                  ? "good"
                  : evidence.latestRequest?.outboxStatus === "DEAD_LETTER"
                    ? "warn"
                    : "neutral"
              }
            >
              <StatusBadge status={evidence.latestRequest?.outboxStatus ?? "PENDING"} />
              <div className="mt-1 text-xs text-slate-500">
                row version: {evidence.latestRequest?.rowVersion ?? "-"}
              </div>
            </EvidenceCard>
            <EvidenceCard label="Worker claim / lease" tone={evidence.latestRequest?.claimedBy ? "good" : "neutral"}>
              <div className="break-all">{evidence.latestRequest?.claimedBy ?? "No active claim"}</div>
              <div className="mt-1 text-xs text-slate-500">
                lease: {evidence.latestRequest?.claimUntil ? formatDateTime(evidence.latestRequest.claimUntil) : "-"}
              </div>
              <div className="mt-1 text-xs text-slate-500">
                heartbeat: {evidence.latestRequest?.claimHeartbeatAt ? formatDateTime(evidence.latestRequest.claimHeartbeatAt) : "-"}
              </div>
            </EvidenceCard>
            <EvidenceCard label="Assignment evidence" tone={evidence.latestRequest?.dispatchTokenHash ? "good" : "neutral"}>
              <div>attempt: {evidence.latestRequest?.attemptCount ?? "-"}</div>
              <div className="mt-1 text-xs text-slate-500">dispatch token hash: {evidence.latestRequest?.dispatchTokenHash ? "recorded" : "-"}</div>
              <div className="mt-1 text-xs text-slate-500">fencing token hash: {evidence.latestRequest?.fencingTokenHash ? "recorded" : "-"}</div>
              <div className="mt-1 break-all text-xs text-slate-500">ACK evidence: {evidence.latestRequest?.ackEvidenceId ?? "-"}</div>
            </EvidenceCard>
            <EvidenceCard
              label="Recovery / reconciliation"
              tone={evidence.latestRequest?.recoveryClassification && evidence.latestRequest.recoveryClassification !== "NONE" ? "warn" : "good"}
            >
              <StatusBadge status={evidence.latestRequest?.recoveryClassification ?? "NONE"} />
              <div className="mt-1 text-xs text-slate-500">reconciliations: {evidence.latestRequest?.reconciliationCount ?? 0}</div>
              <div className="mt-2">
                <Link
                  href={`/tasks/failure-queue?dispatchRequestId=${encodeURIComponent(evidence.latestRequest?.dispatchRequestId ?? "")}`}
                  className="font-bold text-blue-700 hover:text-blue-800"
                >
                  Open governed retry / repair
                </Link>
              </div>
            </EvidenceCard>
            <EvidenceCard label="Updated" tone="neutral">
              {evidence.latestRequest?.updatedAt ||
              evidence.latestDecision?.createdAt ||
              props.task?.updatedAt
                ? formatDateTime(
                    evidence.latestRequest?.updatedAt ??
                      evidence.latestDecision?.createdAt ??
                      props.task?.updatedAt,
                  )
                : "-"}
            </EvidenceCard>
          </div>

          <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4">
            <div className="flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="text-xs font-black uppercase tracking-wide text-slate-500">
                  Runtime Delivery Confirmation
                </div>
                <h3 className="mt-1 text-sm font-black text-slate-950">
                  Dispatch request → gateway delivery → agent ACK → agent RESULT
                </h3>
                <p className="mt-1 text-xs font-semibold leading-5 text-slate-600">
                  This confirms the assignment did not stop at Core selection.
                  Delivery and callback truth are confirmed by Core dispatch
                  state and persisted callback inbox records.
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <StatusBadge
                  status={
                    evidence.deliveryConfirmation.delivered
                      ? "DELIVERED"
                      : "DELIVERY_PENDING"
                  }
                />
                <StatusBadge
                  status={
                    evidence.deliveryConfirmation.terminal
                      ? "RESULT_RECEIVED"
                      : "NO_RESULT"
                  }
                />
              </div>
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-4">
              <DeliveryStep
                label="Gateway delivered"
                done={evidence.deliveryConfirmation.delivered}
                detail={
                  props.deliveryAttempt?.reason ??
                  evidence.latestRequest?.reason ??
                  props.task?.dispatchDeliveryStatus ??
                  undefined
                }
              />
              <DeliveryStep
                label="Agent ACK"
                done={evidence.deliveryConfirmation.acked}
                detail={
                  evidence.deliveryConfirmation.callbackCount
                    ? `${evidence.deliveryConfirmation.callbackCount} callback record(s)`
                    : "No ACK callback persisted yet"
                }
              />
              <DeliveryStep
                label="Agent RESULT"
                done={evidence.deliveryConfirmation.terminal}
                detail={
                  evidence.deliveryConfirmation.latestTerminal?.callbackId ??
                  props.callbackInboxSummary?.latestCallbackId ??
                  "No terminal callback persisted yet"
                }
              />
              <DeliveryStep
                label="Task completed"
                done={evidence.deliveryConfirmation.completed}
                detail={
                  props.task?.status ??
                  evidence.deliveryConfirmation.latestTerminal?.newTaskStatus ??
                  undefined
                }
              />
            </div>
          </div>

          {evidence.latestDecision?.decisionReason ? (
            <div className="mt-4 rounded-2xl border border-blue-100 bg-blue-50 px-4 py-3 text-sm font-semibold text-blue-900">
              <div className="text-xs font-black uppercase tracking-wide text-blue-600">
                Routing reason
              </div>
              <div className="mt-1 break-words">
                {evidence.latestDecision.decisionReason}
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
