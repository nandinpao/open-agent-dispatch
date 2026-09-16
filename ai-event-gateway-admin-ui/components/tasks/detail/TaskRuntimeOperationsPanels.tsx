"use client";

import Link from "next/link";
import { EmptyState } from "@/components/common/EmptyState";
import { RawDiagnosticsPanel } from "@/components/common/RawDiagnosticsPanel";
import { DispatchUserFacingReason } from "@/components/common/DispatchUserFacingReason";
import { StatusBadge } from "@/components/common/StatusBadge";
import { KeyValue } from "@/components/tasks/detail/TaskDetailPrimitives";
import type { RuntimeAttemptSummary } from "@/lib/dashboard/taskDispatchMerge";
import type {
  CoreDispatchTimelineResponse,
  CoreTaskRuntimeView,
} from "@/lib/types/domains/task";
import { formatDateTime, formatDurationMs } from "@/lib/utils/format";

export function RuntimeAttemptPanel({
  title,
  description,
  attempt,
  warning,
}: Readonly<{
  title: string;
  description: string;
  attempt?: RuntimeAttemptSummary;
  warning?: string;
}>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">{title}</h2>
          <p className="mt-1 text-sm text-slate-500">{description}</p>
        </div>
        <StatusBadge
          status={
            attempt ? "NETTY_OK" : warning ? "NETTY_UNAVAILABLE" : "MISSING"
          }
        />
      </div>
      {warning ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {warning}
        </p>
      ) : null}
      {!warning && !attempt ? (
        <EmptyState
          title="No Task runtime attempts are available"
          description="Netty runtime  delivery / relay No data Core Task does not exist."
        />
      ) : null}
      {attempt ? (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            <KeyValue label="Status" value={attempt.status ?? "-"} />
            <KeyValue
              label="Gateway Node"
              value={attempt.gatewayNodeId ?? "-"}
            />
            <KeyValue label="Session" value={attempt.sessionId ?? "-"} />
            <KeyValue label="Agent" value={attempt.agentId ?? "-"} />
            <KeyValue
              label="Occurred"
              value={
                attempt.occurredAt ? formatDateTime(attempt.occurredAt) : "-"
              }
            />
            <KeyValue
              label="Latency"
              value={formatDurationMs(attempt.latencyMs)}
            />
            <KeyValue label="Reason" value={attempt.reason ?? "-"} />
          </div>
          {attempt.payload ? (
            <div className="mt-4">
              <RawDiagnosticsPanel
                title="Runtime attempt raw payload"
                value={attempt.payload}
              />
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

export function RecoveryVisibilityPanel({
  task,
}: Readonly<{ task: CoreTaskRuntimeView }>) {
  const waiting = Boolean(task.nextDispatchAttemptAt);
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Task-level Delayed Recovery
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            The authoritative Task is waiting for the next state transition or scanner cycle.
            Retry Dispatch.
          </p>
        </div>
        <StatusBadge
          status={waiting ? "DELAYED_RECOVERY_WAITING" : "NO_DELAY"}
        />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <KeyValue label="Waiting Recovery" value={waiting ? "YES" : "NO"} />
        <KeyValue
          label="Task Dispatch Attempts"
          value={task.dispatchAttemptCount ?? 0}
        />
        <KeyValue
          label="Next Attempt At"
          value={
            task.nextDispatchAttemptAt
              ? formatDateTime(task.nextDispatchAttemptAt)
              : "-"
          }
        />
        <KeyValue
          label="Claimed By"
          value={task.dispatchRecoveryClaimedBy ?? "-"}
        />
        <KeyValue
          label="Claim Until"
          value={
            task.dispatchRecoveryClaimUntil
              ? formatDateTime(task.dispatchRecoveryClaimUntil)
              : "-"
          }
        />
        <KeyValue
          label="Reason"
          value={
            task.dispatchWaitReason ? (
              <DispatchUserFacingReason
                value={task.dispatchWaitReason}
                error={task.userFacingDispatchError}
              />
            ) : (
              "-"
            )
          }
        />
      </div>
    </section>
  );
}

export function StandardDispatchActionsPanel({
  task,
  retrying,
  onRetry,
}: Readonly<{
  task: CoreTaskRuntimeView;
  retrying: boolean;
  onRetry: () => void;
}>) {
  const flowHref = task.matchedFlowId ? `/dispatch-flows?flowId=${encodeURIComponent(task.matchedFlowId)}` : "/dispatch-flows";
  const agentHref = task.assignedAgentId ? `/agents/${encodeURIComponent(task.assignedAgentId)}` : "/agents";
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Standard Dispatch Actions</h2>
          <p className="mt-1 text-sm text-slate-500">
            Standard Task CTA: Source Flow → Agent Pool → Core-approved Required Capability → runtime eligibility → routing selection. Runtime-reported Capability is diagnostic evidence only.
          </p>
        </div>
        <StatusBadge status="STANDARD_DISPATCH_ACTIONS" />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <Link href={flowHref} className="rounded-xl border border-blue-200 px-4 py-2 text-center text-sm font-bold text-blue-700 hover:bg-blue-50">Open Source Flow</Link>
        <Link href={task.targetPoolId ? `/agents?poolId=${encodeURIComponent(task.targetPoolId)}` : "/agents"} className="rounded-xl border border-slate-200 px-4 py-2 text-center text-sm font-bold text-slate-700 hover:bg-slate-50">Review Agent Pool</Link>
        <Link href={agentHref} className="rounded-xl border border-violet-200 px-4 py-2 text-center text-sm font-bold text-violet-700 hover:bg-violet-50">Open Agent Runtime</Link>
        <button type="button" onClick={onRetry} disabled={retrying} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300">
          {retrying ? "Retrying..." : "Retry Dispatch"}
        </button>
      </div>
    </section>
  );
}

export function DispatchTimelinePanel({
  timeline,
  error,
}: Readonly<{ timeline?: CoreDispatchTimelineResponse; error?: string }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Dispatch Timeline
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core canonical timeline: Task and dispatch
            attempt,offer,assignment,lease,execution,callback,retry,DLQ
            and audit history.
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "TIMELINE_UNAVAILABLE"
              : timeline?.events?.length
                ? "TIMELINE_READY"
                : "NO_TIMELINE"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && (!timeline?.events || timeline.events.length === 0) ? (
        <EmptyState
          title="No dispatch timeline"
          description="Core has not returned timeline events for this Task."
        />
      ) : null}
      {!error && timeline?.events?.length ? (
        <>
          <div className="mt-4 flex flex-wrap gap-2 text-xs">
            {Object.entries(timeline.counts ?? {}).map(([stage, count]) => (
              <span
                key={stage}
                className="rounded-full bg-slate-100 px-2.5 py-1 font-semibold text-slate-600"
              >
                {stage}: {count}
              </span>
            ))}
            {timeline.generatedAt ? (
              <span className="rounded-full bg-slate-100 px-2.5 py-1 font-semibold text-slate-600">
                Generated: {formatDateTime(timeline.generatedAt)}
              </span>
            ) : null}
          </div>
          <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
            <table className="min-w-full divide-y divide-slate-100 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">#</th>
                  <th className="px-4 py-3">Occurred</th>
                  <th className="px-4 py-3">Details</th>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {timeline.events.map((event) => (
                  <tr
                    key={`${event.sequence}-${event.stage}-${event.action}`}
                    className="align-top hover:bg-slate-50"
                  >
                    <td className="px-4 py-3 text-slate-500">
                      {event.sequence}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                      {event.occurredAt
                        ? formatDateTime(event.occurredAt)
                        : "-"}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={event.stage} />
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={event.action} />
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {event.status ? (
                        <StatusBadge status={event.status} />
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="max-w-2xl px-4 py-3 text-slate-600">
                      <div className="font-medium">{event.message ?? "-"}</div>
                      <div className="mt-1 text-xs text-slate-400">
                        {Object.entries(event.references ?? {})
                          .map(([key, value]) => `${key}=${value}`)
                          .join(" · ")}
                      </div>
                      {event.details && Object.keys(event.details).length ? (
                        <div className="mt-2">
                          <RawDiagnosticsPanel
                            title="Timeline event details"
                            value={event.details}
                          />
                        </div>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </section>
  );
}

