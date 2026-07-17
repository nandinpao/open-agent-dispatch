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
  "NO_FLOW_AGENT",
  "MISSING_REQUIRED_CAPABILITY",
  "AGENT_OFFLINE",
  "AGENT_CAPACITY_FULL",
  "DISPATCH_DELIVERY_FAILED",
  "RESULT_TIMEOUT",
]);

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
  if (normalized === "NO_MATCHING_FLOW" || normalized === "NO_MATCHING_RULE") return { href: "/dispatch-flows", label: "Open Dispatch Flow", advice: "建立或啟用符合此事件條件的 Dispatch Flow / Flow Rule。" };
  if (normalized === "NO_FLOW_AGENT") return { href: "/dispatch-flows", label: "Select Flow Agent", advice: "在 Dispatch Flow 中選擇至少一個已核准 Agent。" };
  if (normalized === "MISSING_REQUIRED_CAPABILITY") return { href: "/dispatch-flows", label: "Review Capability", advice: "確認 Flow Rule 的 Required Capability，或核准具備該能力的 Agent。" };
  if (normalized === "AGENT_OFFLINE" || normalized === "AGENT_CAPACITY_FULL") return { href: "/agents", label: "Open Agent", advice: "檢查 Agent runtime、heartbeat、credential 與容量。" };
  if (normalized === "DISPATCH_DELIVERY_FAILED" || normalized === "RESULT_TIMEOUT") return { href: "/agents", label: "Check Runtime", advice: "檢查 Netty delivery、Agent callback relay 與 runtime log 後重新派工。" };
  return { href: "/dispatch-flows", label: "Open Dispatch Flow", advice: "依 Runtime Decision Chain 的第一個標準 blocker 修正後重新派工。" };
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
          <div className="text-xs font-black uppercase tracking-wide text-violet-700">Runtime Decision Chain</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">標準派工主因</h2>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">此區只顯示 Flow、Rule、Flow Agent、Required Capability、Runtime、Capacity、DispatchRequest、ACK 與 RESULT 的正式證據。標準 Task 診斷不再使用舊式派工模型 或獨立診斷工具。</p>
        </div>
        <div className="flex flex-wrap gap-2"><StatusBadge status={ready ? "READY" : status ?? "UNKNOWN"} />{blockingCode ? <StatusBadge status={blockingCode} /> : null}</div>
      </div>

      {error ? <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">Runtime Decision Chain 讀取失敗：{error}</div> : null}

      <div className={`rounded-xl border p-4 ${statusTone(status, !ready && Boolean(blockingCode))}`}>
        <div className="text-sm font-black text-slate-950">{trace?.summary ?? (ready ? "Dispatch path is ready." : "尚未回傳完整派工證據；請依下方標準主因處理。")}</div>
        {!ready ? <p className="mt-2 text-sm leading-6 text-rose-700">主要原因：{blockingCode || "UNKNOWN"} — {blockingReason ?? "尚未回傳明確原因"}</p> : null}
        {!standard ? <p className="mt-2 text-sm font-semibold leading-6 text-amber-800">此原因不是 Phase 8 標準 blocker；請以 Runtime Decision Chain 轉換後的標準原因為準。</p> : null}
        <p className="mt-2 text-sm font-semibold leading-6 text-blue-800">建議修復：{action.advice}</p>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Source System</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{trace?.sourceSystem ?? task.sourceSystem ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Matched Flow</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{task.matchedFlowId ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Matched Rule</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{task.matchedRuleId ?? "-"}</div></div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3"><div className="text-xs font-semibold text-slate-400">Assigned Agent</div><div className="mt-1 break-all text-sm font-bold text-slate-800">{task.assignedAgentId ?? "尚未指派"}</div></div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Link href={action.href} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">{action.label}</Link>
        <button type="button" onClick={() => void onRetry?.()} disabled={!onRetry || retrying} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">{retrying ? "Retrying..." : "Retry Dispatch"}</button>
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
