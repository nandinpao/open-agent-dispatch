"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { CoreTaskRuntimeVerificationView } from "@/lib/types/core";

interface Props {
  verification?: CoreTaskRuntimeVerificationView;
  error?: string;
  onRetry?: () => Promise<void> | void;
  retrying?: boolean;
}

function tone(status?: string) {
  const normalized = String(status ?? "").toUpperCase();
  if (["PASS", "READY", "COMPLETED", "SUCCEEDED"].includes(normalized)) return "border-emerald-200 bg-emerald-50";
  if (["BLOCKED", "ERROR", "FAILED", "TIMED_OUT"].includes(normalized)) return "border-rose-200 bg-rose-50";
  if (["PENDING", "IN_PROGRESS", "WAITING"].includes(normalized)) return "border-amber-200 bg-amber-50";
  return "border-slate-200 bg-slate-50";
}

function shortJson(value?: Record<string, unknown>) {
  if (!value || Object.keys(value).length === 0) return null;
  return <pre className="mt-2 max-h-32 overflow-auto rounded-xl bg-white/70 p-3 text-xs text-slate-600">{JSON.stringify(value, null, 2)}</pre>;
}

export function TaskRuntimeVerificationPanel({ verification, error, onRetry, retrying }: Readonly<Props>) {
  if (error) {
    return <section className="rounded-2xl border border-rose-200 bg-rose-50 p-5 text-sm text-rose-700">Runtime E2E Verification 讀取失敗：{error}</section>;
  }
  if (!verification) {
    return <section className="rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-500 shadow-sm">尚未取得 Runtime E2E Verification。</section>;
  }

  const agentHref = verification.selectedAgentId ? `/agents/${encodeURIComponent(verification.selectedAgentId)}?tab=diagnostics` : "/agents";
  const elapsed = verification.elapsedSeconds ?? 0;
  const timeout = verification.timeoutSeconds ?? 90;

  return (
    <section className="space-y-4 rounded-2xl border border-indigo-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-700">P6 Runtime Delivery E2E Verification</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">Contract → Test Task → Routing → Delivery → ACK → RESULT</h2>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">
            以 Task 為中心追蹤 Runtime 實際投遞鏈，逾時或失敗時標出卡在哪一段，並提供 Retry / Recovery / Agent Diagnostics 入口。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={verification.status ?? "UNKNOWN"} />
          {verification.firstBlockingCode ? <StatusBadge status={verification.firstBlockingCode} /> : null}
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
          <div className="text-xs font-semibold text-slate-400">Current Step</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{verification.currentStep ?? "-"}</div>
        </div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
          <div className="text-xs font-semibold text-slate-400">Selected Agent</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{verification.selectedAgentId ?? "-"}</div>
        </div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
          <div className="text-xs font-semibold text-slate-400">Dispatch Request</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{verification.dispatchRequestId ?? "-"}</div>
        </div>
        <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
          <div className="text-xs font-semibold text-slate-400">Elapsed / Timeout</div>
          <div className="mt-1 text-sm font-bold text-slate-800">{elapsed}s / {timeout}s</div>
        </div>
      </div>

      <div className={`rounded-xl border p-4 ${tone(verification.status)}`}>
        <div className="text-sm font-black text-slate-950">{verification.summary ?? "Runtime verification generated."}</div>
        {verification.firstBlockingReason ? <p className="mt-1 text-sm text-rose-700">Blocked at {verification.firstBlockingStep}: {verification.firstBlockingReason}</p> : null}
      </div>

      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={() => void onRetry?.()} disabled={!onRetry || retrying} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">
          {retrying ? "Retrying..." : "Retry Dispatch"}
        </button>
        <Link href={agentHref} className="rounded-xl border border-indigo-200 px-4 py-2 text-sm font-bold text-indigo-700 hover:bg-indigo-50">Open Agent Diagnostics</Link>
        <Link href={`/tasks/${encodeURIComponent(verification.taskId)}`} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">Open Task Evidence</Link>
      </div>

      <div className="space-y-3">
        {(verification.steps ?? []).map((step, index) => (
          <div key={`${step.step}:${index}`} className={`rounded-2xl border p-4 ${tone(step.status)}`}>
            <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
              <div>
                <div className="text-xs font-black uppercase tracking-wide text-slate-500">{index + 1}. {step.step}</div>
                <div className="mt-1 text-sm font-black text-slate-950">{step.title ?? step.step}</div>
                <p className="mt-1 text-sm leading-6 text-slate-600">{step.summary ?? "-"}</p>
                {step.nextAction ? <p className="mt-2 text-xs font-bold text-indigo-700">Next action: {step.nextAction}</p> : null}
              </div>
              <div className="flex flex-wrap gap-2">
                <StatusBadge status={step.status} />
                {step.blockingCode ? <StatusBadge status={step.blockingCode} /> : null}
              </div>
            </div>
            {shortJson(step.details)}
          </div>
        ))}
      </div>
    </section>
  );
}
