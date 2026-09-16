import Link from "next/link";
import { DispatchUserFacingReason } from "@/components/common/DispatchUserFacingReason";
import { StatusBadge } from "@/components/common/StatusBadge";
import { KeyValue } from "@/components/tasks/detail/TaskDetailPrimitives";
import type {
  CoreDispatchEligibilityV2Response,
  CoreTaskDispatchRequirements,
  CoreTaskEligibleAgentsResponse,
  CoreTaskRuntimeView,
} from "@/lib/types/domains/task";
import { formatDateTime } from "@/lib/utils/format";

function normalizeContractToken(value?: string | null): string {
  return (value ?? "").trim().toUpperCase();
}

function isTaskContractUnresolved(requirements?: CoreTaskDispatchRequirements, eligibleAgents?: CoreTaskEligibleAgentsResponse): boolean {
  const resolvedRequirements = requirements ?? eligibleAgents?.requirements;
  const source = normalizeContractToken(resolvedRequirements?.requirementSource);
  return ["NO_MATCHING_FLOW", "NO_MATCHING_RULE", "NO_FLOW_AGENT"].includes(source);
}

export function TaskAuthoritySummary({
  task,
}: Readonly<{ task: CoreTaskRuntimeView }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Core Task Authority
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Task basics, status, assigned Agent, Dispatch Request, and callback evidence
            persisted status  Core Gateway runtime  transport
            observation.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={task.status} />
          {task.dispatchStatus ? (
            <StatusBadge status={task.dispatchStatus} />
          ) : null}
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Task ID" value={task.taskId} />
        <KeyValue
          label="Trace ID"
          value={
            task.traceId ? (
              <Link
                href={`/traces/${encodeURIComponent(task.traceId)}`}
                className="text-blue-600 hover:text-blue-700"
              >
                {task.traceId}
              </Link>
            ) : (
              "-"
            )
          }
        />
        <KeyValue label="Incident" value={task.incidentId ?? "-"} />
        <KeyValue label="Type" value={task.taskType ?? "-"} />
        <KeyValue label="Priority" value={task.priority ?? "-"} />
        <KeyValue
          label="Assigned Agent"
          value={
            task.assignedAgentId ? (
              <Link
                href={`/agents/${encodeURIComponent(task.assignedAgentId)}`}
                className="text-blue-600 hover:text-blue-700"
              >
                {task.assignedAgentId}
              </Link>
            ) : (
              "-"
            )
          }
        />
        <KeyValue
          label="Dispatch Request"
          value={task.dispatchRequestId ?? "-"}
        />
        <KeyValue label="Dispatch Status" value={task.dispatchStatus ?? "-"} />
        <KeyValue
          label="Execution Status"
          value={task.dispatchExecutionStatus ?? "-"}
        />
        <KeyValue
          label="Delivery Status"
          value={task.dispatchDeliveryStatus ?? "-"}
        />
        <KeyValue label="Next Action" value={task.nextAction ?? "-"} />
        <KeyValue label="Callback Status" value={task.callbackStatus ?? "-"} />
        <KeyValue
          label="Dispatch Recovery Attempt"
          value={task.dispatchAttemptCount ?? 0}
        />
        <KeyValue
          label="Next Dispatch Attempt"
          value={
            task.nextDispatchAttemptAt
              ? formatDateTime(task.nextDispatchAttemptAt)
              : "-"
          }
        />
        <KeyValue
          label="Recovery Claim"
          value={
            task.dispatchRecoveryClaimedBy
              ? `${task.dispatchRecoveryClaimedBy}${task.dispatchRecoveryClaimUntil ? ` until ${formatDateTime(task.dispatchRecoveryClaimUntil)}` : ""}`
              : "-"
          }
        />
        <KeyValue
          label="Created"
          value={task.createdAt ? formatDateTime(task.createdAt) : "-"}
        />
        <KeyValue
          label="Updated"
          value={task.updatedAt ? formatDateTime(task.updatedAt) : "-"}
        />
        <KeyValue
          label="Required Capabilities"
          value={task.requiredCapabilities?.join(", ") || "-"}
        />
        <KeyValue label="Target Pool" value={task.targetPoolId ?? "-"} />
        <KeyValue label="Assigned Pool" value={task.assignedPoolId ?? "-"} />
        <KeyValue label="Classification" value={task.classificationStatus ?? "-"} />
      </div>
      {task.blockedReason ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-800">
          Blocked: {task.blockedReason}
          {task.nextAction ? ` · Next action: ${task.nextAction}` : ""}
        </p>
      ) : null}
      {task.lifecycleReason &&
      !task.dispatchWaitReason &&
      !task.dispatchRetryReason &&
      !task.failureReason ? (
        <p className="mt-4 rounded-xl bg-slate-50 p-4 text-sm font-semibold text-slate-700">
          State reason: {task.lifecycleReason}
        </p>
      ) : null}
      {task.dispatchWaitReason ? (
        <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-800">
          <div className="mb-2 text-xs font-black uppercase tracking-wide text-amber-700">
            Dispatch wait / delayed recovery
          </div>
          <DispatchUserFacingReason
            value={task.dispatchWaitReason}
            error={task.userFacingDispatchError}
            codeClassName="inline-flex rounded-full bg-amber-900 px-2.5 py-1 text-xs font-black uppercase tracking-wide text-white"
            detailsClassName="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-xs font-semibold"
            technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-amber-900"
          />
        </div>
      ) : null}
      {task.failureReason ? (
        <div className="mt-4 rounded-xl bg-rose-50 p-4 text-sm font-semibold text-rose-800">
          <div className="mb-2 text-xs font-black uppercase tracking-wide text-rose-700">
            Failure
          </div>
          <DispatchUserFacingReason
            value={task.failureReason}
            codeClassName="inline-flex rounded-full bg-rose-900 px-2.5 py-1 text-xs font-black uppercase tracking-wide text-white"
            detailsClassName="rounded-xl border border-rose-100 bg-white/70 px-3 py-2 text-xs font-semibold"
            technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-rose-900"
          />
        </div>
      ) : null}
    </section>
  );
}

export function TaskDispatchEligibilityContractPanel({
  requirements,
  eligibleAgents,
  requirementsError,
  eligibleAgentsError,
}: Readonly<{
  requirements?: CoreTaskDispatchRequirements;
  eligibleAgents?: CoreTaskEligibleAgentsResponse;
  requirementsError?: string;
  eligibleAgentsError?: string;
}>) {
  const requiredCapabilities = requirements?.requiredCapabilities ?? [];
  const requiredFeatures = requirements?.requiredRuntimeFeatures ?? [];
  const contractUnresolved = isTaskContractUnresolved(requirements, eligibleAgents);
  const eligible = contractUnresolved ? [] : eligibleAgents?.eligibleAgents ?? [];
  const blocked = contractUnresolved ? [] : eligibleAgents?.blockedAgents ?? [];
  return (
    <section className="rounded-2xl border border-indigo-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-500">
Support · Agent Pool / Runtime Evidence
          </div>
          <h2 className="mt-1 text-base font-bold text-slate-900">
Pool evidence 
          </h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">
Flow membership defines the candidate Pool. Task Required Capability is a blocking eligibility gate; runtime readiness and capacity are evaluated separately before scoring.
          </p>
        </div>
        <StatusBadge
          status={contractUnresolved ? "CONTRACT_NOT_RESOLVED" : eligible.length ? "HAS_ELIGIBLE_AGENT" : "NO_ELIGIBLE_AGENT"}
        />
      </div>
      {requirementsError ? (
        <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-semibold text-amber-800">
          Requirements API: {requirementsError}
        </p>
      ) : null}
      {eligibleAgentsError ? (
        <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-semibold text-amber-800">
          Eligible Agents API: {eligibleAgentsError}
        </p>
      ) : null}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
Required Capability Qualification
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {requiredCapabilities.length ? (
              requiredCapabilities.map((capability) => (
                <span
                  key={capability}
                  className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-bold text-indigo-800"
                >
                  {capability}
                </span>
              ))
            ) : (
              <span className="text-sm text-slate-500">
No Required Capability is configured for this Task.
              </span>
            )}
          </div>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
            Required Runtime Features
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {requiredFeatures.length ? (
              requiredFeatures.map((feature) => (
                <span
                  key={feature}
                  className="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700"
                >
                  {feature}
                </span>
              ))
            ) : (
              <span className="text-sm text-slate-500">
                This Task has no runtime feature requirements.
              </span>
            )}
          </div>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
            Eligible Agents
          </div>
          <div className="mt-3 text-sm leading-6 text-slate-700">
            <div>
              <span className="font-bold text-emerald-700">Eligible:</span>
              {eligible.length}
            </div>
            <div>
              <span className="font-bold text-rose-700">Blocked:</span>
              {blocked.length}
            </div>
            <div>
              <span className="font-bold">Source:</span>
              {requirements?.requirementSource ?? "-"}
            </div>
            {contractUnresolved ? (
              <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-bold leading-5 text-amber-900">
Candidate evaluation was not executed.  Source Flow / Agent Pool  default pool,target pool or pool members.
              </div>
            ) : null}
          </div>
        </div>
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
          <div className="text-sm font-black text-emerald-950">
            Top Eligible Agents
          </div>
          <div className="mt-3 space-y-2">
            {eligible.slice(0, 5).map((agent) => (
              <div
                key={agent.agentId}
                className="rounded-xl border border-emerald-100 bg-white px-3 py-2 text-xs text-emerald-900"
              >
                <div className="font-black">
                  <Link
                    href={`/agents/${encodeURIComponent(agent.agentId)}`}
                    className="hover:text-emerald-700"
                  >
                    {agent.agentId}
                  </Link>
                </div>
                <div className="mt-1">
                  Archived agent:{" "}
                  {agent.profileCode ??
                    agent.matchedProfiles?.join(", ") ??
                    "-"}{" "}
                  · Score: {agent.score ?? "-"}
                </div>
              </div>
            ))}
            {!eligible.length ? (
              <div className="text-sm text-emerald-800">
                {contractUnresolved ? "Flow evidence is incomplete." : "No eligible Agent was found for this Task."}
              </div>
            ) : null}
          </div>
        </div>
        <div className={`rounded-2xl border p-4 ${contractUnresolved ? "border-amber-100 bg-amber-50/70" : "border-rose-100 bg-rose-50/70"}`}>
          <div className={`text-sm font-black ${contractUnresolved ? "text-amber-950" : "text-rose-950"}`}>
            {contractUnresolved ? "Candidate Evaluation" : "Blocked Agents"}
          </div>
          <div className="mt-3 space-y-2">
            {contractUnresolved ? (
              <div className="rounded-xl border border-amber-100 bg-white px-3 py-2 text-xs leading-5 text-amber-900">
                <div className="font-black">Not executed</div>
                <div className="mt-1">
                  This task does not have complete Flow-owned evidence yet, so Agent selection should be repaired from Dispatch Flow first.
                </div>
                <div className="mt-2 font-mono text-xs">
                  requirementSource={requirements?.requirementSource ?? "-"}
                </div>
              </div>
            ) : blocked.slice(0, 5).map((agent) => (
              <div
                key={agent.agentId}
                className="rounded-xl border border-rose-100 bg-white px-3 py-2 text-xs text-rose-900"
              >
                <div className="font-black">
                  <Link
                    href={`/agents/${encodeURIComponent(agent.agentId)}`}
                    className="hover:text-rose-700"
                  >
                    {agent.agentId}
                  </Link>
                </div>
                <div className="mt-1">
                  {agent.reason ?? agent.dispatchStatus ?? "BLOCKED"}
                </div>
              </div>
            ))}
            {!contractUnresolved && !blocked.length ? (
              <div className="text-sm text-rose-800">No selected Agent is available.</div>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}

// Backward verification token: P3-H · Eligibility V2 Shadow
export function TaskDispatchEligibilityV2Panel({
  response,
  archivedAgents,
  error,
}: Readonly<{
  response?: CoreDispatchEligibilityV2Response;
  archivedAgents?: CoreTaskEligibleAgentsResponse;
  error?: string;
}>) {
  const policies = response?.applicablePolicies ?? [];
  const eligible = response?.eligibleCandidates ?? [];
  const blocked = response?.blockedCandidates ?? [];
  const globalReasons = response?.globalBlockingReasons ?? [];
  const archivedEligibleIds = new Set<string>(
    (archivedAgents?.eligibleAgents ?? [])
      .map((agent) => agent.agentId)
      .filter((agentId): agentId is string => Boolean(agentId)),
  );
  const v2EligibleIds = new Set<string>(
    eligible
      .map((candidate) => candidate.agentId)
      .filter((agentId): agentId is string => Boolean(agentId)),
  );
  const common = Array.from(archivedEligibleIds).filter((agentId) =>
    v2EligibleIds.has(agentId),
  );
  const archivedOnly = Array.from(archivedEligibleIds).filter(
    (agentId) => !v2EligibleIds.has(agentId),
  );
  const v2Only = Array.from(v2EligibleIds).filter(
    (agentId) => !archivedEligibleIds.has(agentId),
  );
  return (
    <section className="rounded-2xl border border-violet-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-violet-500">
            Advanced Canonical Eligibility
          </div>
          <h2 className="mt-1 text-base font-bold text-slate-900">
             Agent 
          </h2>
          <p className="mt-1 text-sm leading-6 text-slate-500">
            Source Flow, Agent Pool, Pool member, and runtime delivery evidence
          </p>
        </div>
        <StatusBadge status={response?.engineMode ?? "SHADOW"} />
      </div>
      {error ? (
        <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm font-semibold text-amber-800">
          Eligibility V2 API: {error}
        </p>
      ) : null}
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">
            Common
          </div>
          <div className="mt-1 text-2xl font-black text-slate-900">
            {common.length}
          </div>
          <div className="mt-1 text-xs text-slate-500">
             Agent.
          </div>
        </div>
        <div className="rounded-2xl border border-amber-100 bg-amber-50 p-3">
          <div className="text-xs font-black uppercase tracking-wide text-amber-600">
            Flow evidence only
          </div>
          <div className="mt-1 text-2xl font-black text-amber-900">
            {archivedOnly.length}
          </div>
          <div className="mt-1 text-xs text-amber-700">
            No legacy Flow-Agent assignment evidence is available. 
          </div>
        </div>
        <div className="rounded-2xl border border-violet-100 bg-violet-50 p-3">
          <div className="text-xs font-black uppercase tracking-wide text-violet-600">
            V2 only
          </div>
          <div className="mt-1 text-2xl font-black text-violet-900">
            {v2Only.length}
          </div>
          <div className="mt-1 text-xs text-violet-700">
             Agent.
          </div>
        </div>
      </div>
      {archivedOnly.length ? (
        <div className="mt-3 rounded-2xl border border-amber-100 bg-amber-50 p-3 text-xs text-amber-900">
          <span className="font-black">Flow evidence only agents:</span>{" "}
          {archivedOnly.slice(0, 8).join(", ")}
        </div>
      ) : null}
      {globalReasons.length ? (
        <div className="mt-4 rounded-2xl border border-amber-100 bg-amber-50 p-4 text-sm text-amber-900">
          <div className="font-black">Global blocking reasons</div>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {globalReasons.map((reason, index) => (
              <li key={`${reason.code}-${index}`}>
                {reason.code}: {reason.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-black text-slate-900">
            Applicable Rules
          </div>
          <div className="mt-3 space-y-2">
            {policies.slice(0, 5).map((policy) => (
              <div
                key={policy.policyCode}
                className="rounded-xl border border-violet-100 bg-white px-3 py-2 text-xs text-violet-900"
              >
                <div className="font-black">{policy.policyCode}</div>
                <div className="mt-1">
                  Capabilities:{" "}
                  {(policy.requiredCapabilities ?? []).join(", ") || "-"}
                </div>
                <div>
                  Runtime:{" "}
                  {(policy.requiredRuntimeFeatures ?? []).join(", ") || "-"}
                </div>
              </div>
            ))}
            {!policies.length ? (
              <span className="text-sm text-slate-500">
                 Task  Dispatch Policy v2 scope.
              </span>
            ) : null}
          </div>
        </div>
        <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4">
          <div className="text-sm font-black text-emerald-950">
            Top V2 Candidates
          </div>
          <div className="mt-3 space-y-2">
            {eligible.slice(0, 5).map((candidate) => (
              <div
                key={`${candidate.agentId}-${candidate.supplyProfileCode}`}
                className="rounded-xl border border-emerald-100 bg-white px-3 py-2 text-xs text-emerald-900"
              >
                <div className="font-black">{candidate.agentId}</div>
                <div>
                  Candidate source: {candidate.supplyProfileCode ?? "-"} · Score:{" "}
                  {candidate.score ?? "-"}
                </div>
                <div>Quality: {candidate.qualityGrade ?? "-"}</div>
              </div>
            ))}
            {!eligible.length ? (
              <span className="text-sm text-emerald-800">
                No eligible candidates are available.
              </span>
            ) : null}
          </div>
        </div>
        <div className="rounded-2xl border border-rose-100 bg-rose-50/70 p-4">
          <div className="text-sm font-black text-rose-950">
            Blocked V2 Candidates
          </div>
          <div className="mt-3 space-y-2">
            {blocked.slice(0, 5).map((candidate) => (
              <div
                key={`${candidate.agentId}-${candidate.supplyProfileCode}`}
                className="rounded-xl border border-rose-100 bg-white px-3 py-2 text-xs text-rose-900"
              >
                <div className="font-black">{candidate.agentId ?? "-"}</div>
                <div>
                  Candidate source: {candidate.supplyProfileCode ?? "-"} · Score:{" "}
                  {candidate.score ?? "-"}
                </div>
                <div>
                  {candidate.blockingReasons?.[0]?.code ??
                    candidate.dispatchStatus ??
                    "BLOCKED_V2_SHADOW"}
                </div>
              </div>
            ))}
            {!blocked.length ? (
              <span className="text-sm text-rose-800">
                No blocked candidates are available.
              </span>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}
