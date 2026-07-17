"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { CoreTaskDispatchEvidenceView } from "@/lib/types/core";

interface Props {
  evidence?: CoreTaskDispatchEvidenceView;
  error?: string;
  retrying?: boolean;
  onRetry?: () => Promise<void> | void;
}

const STANDARD_BLOCKERS = new Set([
  "NO_MATCHING_FLOW",
  "NO_MATCHING_RULE",
  "NO_FLOW_AGENT",
  "MISSING_REQUIRED_CAPABILITY",
  "AGENT_OFFLINE",
  "AGENT_CAPACITY_FULL",
  "DISPATCH_DELIVERY_FAILED",
  "RESULT_TIMEOUT",
]);

function toneClass(status?: string) {
  const normalized = String(status ?? "").toUpperCase();
  if (["PASS", "READY", "SUCCEEDED", "COMPLETED"].includes(normalized)) return "border-emerald-200 bg-emerald-50";
  if (["BLOCKED", "ERROR", "FAILED"].includes(normalized)) return "border-rose-200 bg-rose-50";
  if (["WARN", "WARNING", "PENDING"].includes(normalized)) return "border-amber-200 bg-amber-50";
  return "border-slate-200 bg-slate-50";
}

function detailPreview(details?: Record<string, unknown>) {
  if (!details || Object.keys(details).length === 0) return null;
  return (
    <pre className="mt-3 max-h-36 overflow-auto rounded-xl bg-white/70 p-3 text-xs text-slate-600">
      {JSON.stringify(details, null, 2)}
    </pre>
  );
}

function actionFor(code?: string): { href: string; label: string } {
  const normalized = String(code ?? "").toUpperCase();
  if (normalized === "AGENT_OFFLINE" || normalized === "AGENT_CAPACITY_FULL") return { href: "/agents", label: "Open Agent" };
  if (normalized === "MISSING_REQUIRED_CAPABILITY") return { href: "/dispatch-flows", label: "Review Capability" };
  if (normalized === "DISPATCH_DELIVERY_FAILED" || normalized === "RESULT_TIMEOUT") return { href: "/agents", label: "Check Runtime" };
  return { href: "/dispatch-flows", label: "Open Dispatch Flow" };
}

export function TaskDispatchEvidenceTimelinePanel({
  evidence,
  error,
  retrying,
  onRetry,
}: Readonly<Props>) {
  if (error) {
    return <section className="rounded-2xl border border-rose-200 bg-rose-50 p-5 text-sm text-rose-700">Task Dispatch Evidence 讀取失敗：{error}</section>;
  }
  if (!evidence) {
    return <section className="rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-500 shadow-sm">尚未取得 Task Dispatch Evidence。</section>;
  }

  const sourceSystem = evidence.task?.sourceSystem;
  const blockingCode = String(evidence.firstBlockingCode ?? "").toUpperCase();
  const action = actionFor(blockingCode);
  const standard = !blockingCode || STANDARD_BLOCKERS.has(blockingCode);

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-bold uppercase tracking-wide text-blue-600">Task Runtime Decision Chain</div>
          <h2 className="mt-1 text-lg font-bold text-slate-900">標準派工主因與處理建議</h2>
          <p className="mt-1 text-sm text-slate-500">只使用正式 Event、Task、Flow、Agent、Assignment、DispatchRequest、Netty delivery、ACK 與 RESULT 證據；不使用舊式派工模型 或獨立診斷修復。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={evidence.status ?? "UNKNOWN"} />
          {evidence.firstBlockingCode ? <StatusBadge status={evidence.firstBlockingCode} /> : null}
        </div>
      </div>

      <div className={`rounded-xl border p-4 ${standard ? "border-slate-100 bg-slate-50" : "border-amber-200 bg-amber-50"}`}>
        <div className="text-sm font-bold text-slate-900">{evidence.summary ?? "Task dispatch evidence chain generated."}</div>
        {evidence.firstBlockingReason ? <p className="mt-1 text-sm text-rose-700">主要原因：{blockingCode || "UNKNOWN"} — {evidence.firstBlockingReason}</p> : null}
        {!standard ? <p className="mt-2 text-sm font-semibold text-amber-800">此原因已被 Phase 8 標準化層攔截；標準 UI 只允許八種派工 blocker。請查看 Runtime Decision Chain 的第一個標準 blocker。</p> : null}
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Source System</div><div className="mt-1 text-sm font-bold text-slate-800">{sourceSystem ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Matched Flow</div><div className="mt-1 text-sm font-bold text-slate-800">{evidence.task?.matchedFlowId ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Matched Rule</div><div className="mt-1 text-sm font-bold text-slate-800">{evidence.task?.matchedRuleId ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Selected Agent</div><div className="mt-1 text-sm font-bold text-slate-800">{evidence.task?.assignedAgentId ?? "-"}</div></div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Link href={action.href} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">{action.label}</Link>
        <button type="button" onClick={() => void onRetry?.()} disabled={!onRetry || retrying} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">{retrying ? "Retrying..." : "Retry Dispatch"}</button>
      </div>

      <div className="space-y-3">
        {(evidence.stages ?? []).map((stage, index) => (
          <div key={`${stage.stage}-${index}`} className={`rounded-2xl border p-4 ${toneClass(stage.status)}`}>
            <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
              <div>
                <div className="text-xs font-bold uppercase tracking-wide text-slate-500">{index + 1}. {stage.stage}</div>
                <div className="mt-1 text-sm font-bold text-slate-900">{stage.title ?? stage.stage}</div>
                <p className="mt-1 text-sm text-slate-600">{stage.summary}</p>
                {stage.nextAction ? <p className="mt-2 text-xs font-semibold text-blue-700">Next action: {stage.nextAction}</p> : null}
              </div>
              <div className="flex flex-wrap gap-2"><StatusBadge status={stage.status} />{stage.blockingCode ? <StatusBadge status={stage.blockingCode} /> : null}</div>
            </div>
            {detailPreview(stage.details)}
          </div>
        ))}
      </div>
    </section>
  );
}
