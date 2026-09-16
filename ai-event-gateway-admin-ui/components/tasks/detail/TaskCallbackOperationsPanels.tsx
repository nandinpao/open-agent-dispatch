"use client";

import Link from "next/link";
import { EmptyState } from "@/components/common/EmptyState";
import { RawDiagnosticsPanel } from "@/components/common/RawDiagnosticsPanel";
import { StatusBadge } from "@/components/common/StatusBadge";
import { KeyValue } from "@/components/tasks/detail/TaskDetailPrimitives";
import type {
  CoreCallbackInboxEntry,
  CoreCallbackInboxSummary,
  CoreDispatchAttemptHistoryRecord,
  CoreDispatchAttemptLedger,
} from "@/lib/types/domains/task";
import { formatDateTime } from "@/lib/utils/format";

/**
 * Callback inbox, dispatch attempt ledger and append-only attempt history.
 */
function payloadFromHistory(
  record: CoreDispatchAttemptHistoryRecord,
): unknown | undefined {
  if (!record.payloadJson) return undefined;
  try {
    return JSON.parse(record.payloadJson);
  } catch {
    return record.payloadJson;
  }
}

export function CallbackInboxPanel({
  entries,
  summary,
  error,
}: Readonly<{
  entries?: CoreCallbackInboxEntry[];
  summary?: CoreCallbackInboxSummary;
  error?: string;
}>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Durable Callback Inbox
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core persisted callback inbox:ACK / PROGRESS / RESULT / ERROR
             Gateway relay  Core callback receiving and
            idempotent processing  truth Gateway node 
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "CALLBACK_INBOX_UNAVAILABLE"
              : entries?.length
                ? "CALLBACK_INBOX_READY"
                : "NO_CALLBACK"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && summary ? (
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <KeyValue
            label="Total callbacks"
            value={summary.totalCallbacks ?? 0}
          />
          <KeyValue label="Accepted" value={summary.acceptedCallbacks ?? 0} />
          <KeyValue label="Rejected" value={summary.rejectedCallbacks ?? 0} />
          <KeyValue label="Duplicate" value={summary.duplicateCallbacks ?? 0} />
          <KeyValue
            label="Latest callback"
            value={summary.latestCallbackId ?? "-"}
          />
          <KeyValue
            label="Latest type"
            value={summary.latestCallbackType ?? "-"}
          />
          <KeyValue
            label="Process status"
            value={summary.latestProcessStatus ?? "-"}
          />
          <KeyValue label="Next action" value={summary.nextAction ?? "-"} />
        </div>
      ) : null}
      <div className="mt-4 rounded-xl border border-indigo-100 bg-indigo-50 p-3 text-xs leading-5 text-indigo-900">
        Callback Inbox is Core truth.if cluster node Nginx 
        cluster/single Agent  Gateway 
        callback;Core  callbackId / idempotencyKey 
      </div>
      {!error && (!entries || entries.length === 0) ? (
        <EmptyState
          title="No callback inbox record"
          description="Core has not received an ACK, RESULT, or ERROR callback for this Task. Check the Gateway, Agent worker, process-result flow, and reconnect replay."
        />
      ) : null}
      {!error && entries?.length ? (
        <div className="mt-4 overflow-hidden rounded-xl border border-slate-100 bg-white">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Processed</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Received by</th>
                <th className="px-4 py-3">Message</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {entries.map((entry, index) => (
                <tr
                  key={`${entry.callbackId ?? entry.idempotencyKey ?? index}`}
                  className="align-top hover:bg-slate-50"
                >
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                    {entry.processedAt
                      ? formatDateTime(entry.processedAt)
                      : entry.receivedAt
                        ? formatDateTime(entry.receivedAt)
                        : "-"}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={entry.callbackType ?? "UNKNOWN"} />
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge
                      status={
                        entry.processStatus ??
                        (entry.accepted ? "ACCEPTED" : "REJECTED")
                      }
                    />
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>{entry.receivedByGatewayNodeId ?? "-"}</div>
                    <div className="mt-1 text-xs text-slate-400">
                      session={entry.receivedAgentSessionId ?? "-"}
                    </div>
                  </td>
                  <td className="max-w-xl px-4 py-3 text-slate-600">
                    <div className="font-medium">
                      {entry.message ??
                        entry.ignoredReason ??
                        entry.errorMessage ??
                        "-"}
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      callback={entry.callbackId ?? "-"} · idem=
                      {entry.idempotencyKey ?? "-"}
                    </div>
                    {entry.payload && Object.keys(entry.payload).length ? (
                      <div className="mt-2">
                        <RawDiagnosticsPanel
                          title="Callback payload"
                          value={entry.payload}
                        />
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

export function DispatchLedgerPanel({
  ledger,
  error,
}: Readonly<{ ledger?: CoreDispatchAttemptLedger[]; error?: string }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Durable Dispatch Attempt Ledger
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core authoritative ledger: by persisted dispatch request and
            persisted callback inbox records  Gateway node
            memory;single / cluster 
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "LEDGER_UNAVAILABLE"
              : ledger?.length
                ? "LEDGER_READY"
                : "NO_LEDGER"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && (!ledger || ledger.length === 0) ? (
        <EmptyState
          title="No dispatch ledger"
          description="Core has not created a Dispatch Request for this Task. Review dispatch evidence, Gateway diagnostics, and the ledger."
        />
      ) : null}
      {!error && ledger?.length ? (
        <div className="mt-4 space-y-4">
          {ledger.map((item) => (
            <div
              key={item.dispatchRequestId}
              className="rounded-xl border border-slate-100 bg-slate-50 p-4"
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                    Dispatch Request
                  </div>
                  <div className="mt-1 font-mono text-sm font-bold text-slate-900">
                    {item.dispatchRequestId}
                  </div>
                  <div className="mt-1 text-xs text-slate-500">
                    Task {item.taskId} · Agent {item.agentId ?? "-"} · Attempt{" "}
                    {item.attemptNo ?? "-"}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <StatusBadge
                    status={
                      item.deliveryState ?? item.dispatchStatus ?? "UNKNOWN"
                    }
                  />
                  <StatusBadge status={item.callbackState ?? "NO_CALLBACK"} />
                  <StatusBadge status={item.resultState ?? "PENDING"} />
                </div>
              </div>
              <div className="mt-4 grid gap-3 md:grid-cols-4">
                <KeyValue
                  label="Gateway"
                  value={item.lastKnownGatewayNodeId ?? "-"}
                />
                <KeyValue
                  label="Session"
                  value={item.lastKnownAgentSessionId ?? "-"}
                />
                <KeyValue
                  label="Last callback"
                  value={item.lastCallbackId ?? "-"}
                />
                <KeyValue label="Next action" value={item.nextAction ?? "-"} />
                <KeyValue
                  label="Dispatched"
                  value={
                    item.dispatchedAt ? formatDateTime(item.dispatchedAt) : "-"
                  }
                />
                <KeyValue
                  label="ACK"
                  value={
                    item.ackReceivedAt
                      ? formatDateTime(item.ackReceivedAt)
                      : "-"
                  }
                />
                <KeyValue
                  label="Result"
                  value={
                    item.resultReceivedAt
                      ? formatDateTime(item.resultReceivedAt)
                      : "-"
                  }
                />
                <KeyValue
                  label="Recovery"
                  value={item.recoveryRequired ? "Required" : "Not required"}
                />
              </div>
              <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-3 text-xs leading-5 text-blue-900">
                Dispatch Ledger is Core truth;Gateway node diagnostics
                 live transport / relay Issue callback
                recovery 
              </div>
              {item.events?.length ? (
                <div className="mt-4 overflow-hidden rounded-xl border border-slate-100 bg-white">
                  <table className="min-w-full divide-y divide-slate-100 text-sm">
                    <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="px-4 py-3">Occurred</th>
                        <th className="px-4 py-3">Source</th>
                        <th className="px-4 py-3">Event</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3">Reason</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {item.events.map((event, index) => (
                        <tr
                          key={`${event.eventId ?? event.eventType}-${index}`}
                          className="align-top hover:bg-slate-50"
                        >
                          <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                            {event.occurredAt
                              ? formatDateTime(event.occurredAt)
                              : "-"}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={event.source ?? "UNKNOWN"} />
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={event.eventType} />
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={event.status ?? "-"} />
                          </td>
                          <td className="max-w-xl px-4 py-3 text-slate-600">
                            <div className="font-medium">
                              {event.reason ?? event.errorMessage ?? "-"}
                            </div>
                            <div className="mt-1 text-xs text-slate-400">
                              callback={event.callbackId ?? "-"} · idem=
                              {event.idempotencyKey ?? "-"}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export function AttemptHistoryPanel({
  history,
  error,
}: Readonly<{ history?: CoreDispatchAttemptHistoryRecord[]; error?: string }>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Core Dispatch Attempt History
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Core append-only timeline:assignment,dispatch request,Netty
            delivery,runtime backoff,delayed requeue and scanner claim.
          </p>
        </div>
        <StatusBadge
          status={
            error
              ? "HISTORY_UNAVAILABLE"
              : history?.length
                ? "HISTORY_READY"
                : "NO_HISTORY"
          }
        />
      </div>
      {error ? (
        <p className="mt-4 rounded-xl bg-amber-50 p-4 text-sm font-semibold text-amber-700">
          {error}
        </p>
      ) : null}
      {!error && (!history || history.length === 0) ? (
        <EmptyState
          title="No dispatch attempt history"
          description="No historical timeline instrumentation is available for this Task."
        />
      ) : null}
      {!error && history?.length ? (
        <div className="mt-4 overflow-hidden rounded-xl border border-slate-100">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Occurred</th>
                <th className="px-4 py-3">Event</th>
                <th className="px-4 py-3">Agent / Assignment</th>
                <th className="px-4 py-3">Attempt</th>
                <th className="px-4 py-3">Next / Backoff</th>
                <th className="px-4 py-3">Reason</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {history.map((item) => (
                <tr
                  key={item.historyId}
                  className="align-top hover:bg-slate-50"
                >
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                    {item.occurredAt ? formatDateTime(item.occurredAt) : "-"}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={item.eventType} />
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>
                      {item.agentId ? (
                        <Link
                          href={`/agents/${encodeURIComponent(item.agentId)}`}
                          className="font-semibold text-blue-600 hover:text-blue-700"
                        >
                          {item.agentId}
                        </Link>
                      ) : (
                        "-"
                      )}
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      {item.assignmentId ??
                        item.dispatchRequestId ??
                        item.routingDecisionId ??
                        "-"}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>dispatch: {item.attemptNo ?? "-"}</div>
                    <div className="text-xs text-slate-400">
                      task recovery: {item.taskDispatchAttemptNo ?? "-"}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    <div>
                      {item.nextAttemptAt
                        ? `next ${formatDateTime(item.nextAttemptAt)}`
                        : "-"}
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      {item.runtimeBackoffUntil
                        ? `backoff ${formatDateTime(item.runtimeBackoffUntil)}`
                        : ""}
                    </div>
                  </td>
                  <td className="max-w-xl px-4 py-3 text-slate-600">
                    <div className="break-words font-medium">
                      {item.reason ?? item.errorMessage ?? "-"}
                    </div>
                    {item.errorCode ? (
                      <div className="mt-1 text-xs font-semibold text-rose-500">
                        {item.errorCode}
                      </div>
                    ) : null}
                    {item.payloadJson ? (
                      <div className="mt-2">
                        <RawDiagnosticsPanel
                          title="Attempt payload"
                          value={payloadFromHistory(item)}
                        />
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
