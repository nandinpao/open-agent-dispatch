"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { CoreDispatchContractTraceResponse, CoreTaskRuntimeView } from "@/lib/types/core";

interface Props {
  task: CoreTaskRuntimeView;
  trace?: CoreDispatchContractTraceResponse;
  error?: string;
  retrying?: boolean;
  onRetry?: () => Promise<void> | void;
}

const STANDARD_BLOCKERS = new Set([
  "NO_MATCHING_FLOW",
  "NO_MATCHING_RULE",
  "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
  "RULE_TARGET_POOL_NOT_FOUND",
  "POOL_HAS_NO_ACTIVE_MEMBER",
  "POOL_AGENT_RUNTIME_NOT_FOUND",
  "MISSING_REQUIRED_CAPABILITY",
  "REQUIRED_CAPABILITY_MISSING",
  "NO_AGENT_WITH_REQUIRED_CAPABILITY",
  "AGENT_CAPABILITY_NOT_APPROVED",
  "NO_ELIGIBLE_AGENT_IN_POOL",
  "POOL_AGENT_OFFLINE",
  "POOL_AGENT_CAPACITY_FULL",
  "POOL_AGENT_BACKOFF",
  "NO_FLOW_AGENT",
  "AGENT_OFFLINE",
  "AGENT_CAPACITY_FULL",
  "DISPATCH_DELIVERY_FAILED",
  "RESULT_TIMEOUT",
]);

const DECISION_PATH = [
  "Source Flow",
  "Agent Pool",
  "Required Capability",
  "Runtime Eligibility",
  "Capacity / Backoff",
  "Routing Score",
  "Dispatch",
];

function normalizeCode(value?: string | null): string {
  return String(value ?? "").trim().toUpperCase();
}

function statusTone(status?: string, blocking?: boolean): string {
  const normalized = normalizeCode(status);
  if (blocking || ["BLOCKED", "FAIL", "FAILED", "ERROR"].includes(normalized)) return "border-rose-200 bg-rose-50";
  if (["READY", "PASS", "SUCCEEDED", "COMPLETED"].includes(normalized)) return "border-emerald-200 bg-emerald-50";
  if (["WARN", "WARNING", "PENDING"].includes(normalized)) return "border-amber-200 bg-amber-50";
  return "border-slate-200 bg-slate-50";
}

function detailPreview(details?: Record<string, unknown>) {
  if (!details || Object.keys(details).length === 0) return null;
  return <pre className="mt-3 max-h-36 overflow-auto rounded-xl bg-white/70 p-3 text-xs leading-5 text-slate-600">{JSON.stringify(details, null, 2)}</pre>;
}

function actionFor(code?: string): { href: string; label: string; advice: string } {
  const normalized = normalizeCode(code);
  if (["NO_MATCHING_FLOW", "NO_MATCHING_RULE"].includes(normalized)) return { href: "/dispatch-flows", label: "Open Dispatch Setup", advice: "Create or activate a Source Flow and classification rule that matches this work." };
  if (normalized === "SOURCE_FLOW_HAS_NO_DEFAULT_POOL") return { href: "/dispatch-flows", label: "Set Default Pool", advice: "Assign a default Agent Pool to the matched Source Flow." };
  if (normalized === "RULE_TARGET_POOL_NOT_FOUND") return { href: "/dispatch-flows", label: "Fix Target Pool", advice: "The matched rule targets an Agent Pool that is missing or unavailable. Repair the rule or select an active Pool." };
  if (["POOL_HAS_NO_ACTIVE_MEMBER", "POOL_AGENT_RUNTIME_NOT_FOUND", "NO_FLOW_AGENT"].includes(normalized)) return { href: "/dispatch-flows", label: "Review Agent Pool", advice: "Add at least one approved Agent to the selected Pool, then confirm that the Agent is enabled." };
  if (["MISSING_REQUIRED_CAPABILITY", "REQUIRED_CAPABILITY_MISSING"].includes(normalized)) return { href: "/dispatch-flows", label: "Define Required Capability", advice: "This Task has no canonical Required Capability. Configure the Flow / Task contract so OpenDispatch knows which approved Agent capability is required." };
  if (["NO_AGENT_WITH_REQUIRED_CAPABILITY", "AGENT_CAPABILITY_NOT_APPROVED"].includes(normalized)) return { href: "/agents", label: "Review Agent Capabilities", advice: "Pool membership is not enough. Approve the Task-required Capability for at least one Agent in the selected Pool." };
  if (["POOL_AGENT_OFFLINE", "POOL_AGENT_CAPACITY_FULL", "POOL_AGENT_BACKOFF", "NO_ELIGIBLE_AGENT_IN_POOL"].includes(normalized)) return { href: "/agents", label: "Review Eligible Agents", advice: "The Pool was found, but no member passed Capability, connection, capacity, or backoff eligibility. Review each excluded Agent and its blocking reason." };
  if (["AGENT_OFFLINE", "AGENT_CAPACITY_FULL"].includes(normalized)) return { href: "/agents", label: "Open Agent", advice: "Review the selected Agent connection, credential, capacity, and runtime health." };
  if (["DISPATCH_DELIVERY_FAILED", "RESULT_TIMEOUT"].includes(normalized)) return { href: "/agents", label: "Check Runtime", advice: "Review Netty delivery, Agent transport evidence, callback handling, and runtime logs before retrying Dispatch." };
  return { href: "/dispatch-flows", label: "Open Dispatch Setup", advice: "Follow the decision path below and repair the first blocking stage before retrying Dispatch." };
}

export function TaskDispatchContractTracePanel({ task, trace, error, retrying, onRetry }: Readonly<Props>) {
  const blockingCode = normalizeCode(trace?.firstBlockingCode ?? task.blockedReason ?? task.failureReason);
  const blockingReason = trace?.firstBlockingReason ?? task.failureReason ?? task.lifecycleReason;
  const ready = Boolean(trace?.ready);
  const status = trace?.status ?? (error ? "ERROR" : task.dispatchStatus ?? task.status);
  const action = actionFor(blockingCode);
  const standard = !blockingCode || STANDARD_BLOCKERS.has(blockingCode);

  return (
    <section className="space-y-4 rounded-2xl border border-violet-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-violet-700">Why this Task can or cannot dispatch</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">Dispatch Decision Trace</h2>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">OpenDispatch first finds the Source Flow and Agent Pool, then requires approved Task Capabilities, runtime eligibility, capacity/backoff readiness, and finally applies the Routing Score. Pool membership never substitutes for a required Capability.</p>
        </div>
        <div className="flex flex-wrap gap-2"><StatusBadge status={ready ? "READY" : status ?? "UNKNOWN"} />{blockingCode ? <StatusBadge status={blockingCode} /> : null}</div>
      </div>

      <div className="flex flex-wrap gap-2" aria-label="Dispatch decision order">
        {DECISION_PATH.map((step, index) => <span key={step} className="rounded-full border border-violet-100 bg-violet-50 px-3 py-1 text-xs font-black text-violet-800">{index + 1}. {step}</span>)}
      </div>

      {error ? <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">Unable to load the Dispatch Decision Trace: {error}</div> : null}

      <div className={`rounded-xl border p-4 ${statusTone(status, !ready && Boolean(blockingCode))}`}>
        <div className="text-sm font-black text-slate-950">{trace?.summary ?? (ready ? "All authoritative Dispatch eligibility checks passed." : "Dispatch is blocked; repair the first blocking check below.")}</div>
        {!ready ? <p className="mt-2 text-sm leading-6 text-rose-700"><span className="font-black">Primary blocker:</span> {blockingCode || "UNKNOWN"}{blockingReason ? ` — ${blockingReason}` : ""}</p> : null}
        {!standard ? <p className="mt-2 text-sm font-semibold leading-6 text-amber-800">This blocker is not in the normal operator vocabulary. Review the detailed evidence and Support Debug before changing configuration.</p> : null}
        <p className="mt-2 text-sm font-semibold leading-6 text-blue-800"><span className="font-black">Recommended action:</span> {action.advice}</p>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Source System</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{trace?.sourceSystem ?? task.sourceSystem ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Matched Flow</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{task.matchedFlowId ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Required Capabilities</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{task.requiredCapabilities?.length ? task.requiredCapabilities.join(", ") : "Not declared"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Assigned Agent</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{task.assignedAgentId ?? "Not assigned"}</div></div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Link href={action.href} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">{action.label}</Link>
        <button type="button" onClick={() => void onRetry?.()} disabled={!onRetry || retrying || ready} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">{retrying ? "Retrying..." : "Retry Dispatch"}</button>
      </div>

      <div className="space-y-3">
        {(trace?.checks ?? []).map((check, index) => {
          const code = check.code || check.message || "CHECK";
          const blocking = Boolean(check.blocking) || ["BLOCKED", "FAIL"].includes(normalizeCode(check.status));
          return (
            <div key={`${code}-${index}`} className={`rounded-2xl border p-4 ${statusTone(check.status, blocking)}`}>
              <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                <div>
                  <div className="text-xs font-bold uppercase tracking-wide text-slate-500">{index + 1}. {code}</div>
                  <p className="mt-1 text-sm text-slate-600">{check.message}</p>
                  {check.nextAction ? <p className="mt-2 text-xs font-semibold text-blue-700">Next action: {check.nextAction}</p> : null}
                </div>
                <StatusBadge status={check.status} />
              </div>
              {detailPreview(check.details)}
            </div>
          );
        })}
      </div>
    </section>
  );
}
