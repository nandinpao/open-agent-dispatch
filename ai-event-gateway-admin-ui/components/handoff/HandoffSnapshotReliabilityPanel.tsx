"use client";

import { useMemo, useState } from "react";
import type {
  HandoffReleaseEvidenceView,
  HandoffSnapshotView,
} from "@/lib/handoffContextContract";
import { formatDateTime } from "@/lib/utils/format";

interface Props {
  snapshots: HandoffSnapshotView[];
  evidence: HandoffReleaseEvidenceView[];
  error?: string;
  retryingSnapshotId?: string | null;
  onRetry: (snapshotId: string, reason: string) => Promise<unknown>;
}

function Value({ label, value }: Readonly<{ label: string; value?: string | number | null }>) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-2">
      <div className="text-xs font-bold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-1 break-all text-sm font-semibold text-slate-800">{value ?? "Not available"}</div>
    </div>
  );
}

export function HandoffSnapshotReliabilityPanel({ snapshots, evidence, error, retryingSnapshotId, onRetry }: Props) {
  const [reason, setReason] = useState("Retry approved Handoff Snapshot release after operator review.");
  const latest = snapshots[0];
  const counts = useMemo(() => {
    const values = latest?.fieldDecisions ?? [];
    return {
      allowed: values.filter((value) => value.decision === "ALLOW").length,
      masked: values.filter((value) => value.decision === "MASK").length,
      omitted: values.filter((value) => value.decision === "OMIT").length + (latest?.omittedContentReasons?.length ?? 0),
    };
  }, [latest]);

  if (error) {
    return <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Unable to load Handoff reliability evidence: {error}</div>;
  }
  if (!latest) {
    return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">No Handoff Snapshot is associated with this Task.</div>;
  }

  const retryable = latest.releaseStatus === "FAILED_RETRYABLE" || latest.releaseStatus === "READY";
  return (
    <div className="space-y-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-[0.15em] text-indigo-600">Immutable Handoff Snapshot</div>
          <div className="mt-1 break-all text-base font-black text-slate-900">{latest.snapshotId}</div>
          <p className="mt-1 text-sm text-slate-600">{latest.summary ?? "Approved context projection with no summary."}</p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs font-bold">
          <span className="rounded-full border border-slate-300 bg-white px-3 py-1">Snapshot {latest.status}</span>
          <span className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-indigo-800">Release {latest.releaseStatus ?? "UNKNOWN"}</span>
          <span className="rounded-full border border-slate-300 bg-white px-3 py-1">Row v{latest.rowVersion ?? 0}</span>
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <Value label="Aggregate" value={latest.aggregateId} />
        <Value label="Schema / Policy" value={`v${latest.schemaVersion ?? 1} / v${latest.policyVersion ?? 0}`} />
        <Value label="Source Task / Agent" value={`${latest.sourceTaskId} / ${latest.sourceAgentId ?? "Unassigned"}`} />
        <Value label="Target Task / Agent" value={`${latest.targetTaskId} / ${latest.targetAgentId ?? "Unassigned"}`} />
        <Value label="Target Domain" value={latest.targetDomainId} />
        <Value label="Expiry" value={latest.expiresAt ? formatDateTime(latest.expiresAt) : "No expiry"} />
        <Value label="Approval Evidence" value={latest.approvalEvidenceHash ? "Present" : "Missing"} />
        <Value label="Target Binding" value={latest.targetBindingHash ? "Verified hash present" : "Legacy schema"} />
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Value label="Allowed Fields" value={counts.allowed} />
        <Value label="Masked Fields" value={counts.masked + (latest.redactedFieldPaths?.length ?? 0)} />
        <Value label="Omitted Content" value={counts.omitted} />
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        <Value label="Reconciliation" value={latest.reconciliationClassification ?? "NONE"} />
        <Value label="Attempts" value={latest.reconciliationCount ?? 0} />
        <Value label="Next Reconcile" value={latest.nextReconcileAt ? formatDateTime(latest.nextReconcileAt) : "Not scheduled"} />
      </div>

      {latest.lastReleaseErrorCode ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-900">
          Release blocker: {latest.lastReleaseErrorCode}
        </div>
      ) : null}

      <div className="rounded-xl border border-slate-200 bg-white p-3">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">Latest Release Evidence</div>
        <div className="mt-2 space-y-2">
          {evidence.slice(0, 5).map((item) => (
            <div key={item.evidenceId} className="flex flex-col gap-1 border-b border-slate-100 pb-2 text-xs last:border-b-0">
              <div className="font-bold text-slate-800">{item.evidenceType} · {item.releaseStatus ?? "UNKNOWN"}</div>
              <div className="text-slate-500">{item.reasonCode ?? item.evidenceReference ?? "No reason supplied"} · {item.occurredAt ? formatDateTime(item.occurredAt) : "Time unavailable"}</div>
            </div>
          ))}
          {evidence.length === 0 ? <div className="text-sm text-slate-500">No release evidence has been recorded.</div> : null}
        </div>
      </div>

      {retryable ? (
        <div className="rounded-xl border border-indigo-200 bg-indigo-50 p-3">
          <label className="text-xs font-black uppercase tracking-wide text-indigo-800" htmlFor="handoff-release-reason">Governed retry reason</label>
          <textarea
            id="handoff-release-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            className="mt-2 min-h-20 w-full rounded-lg border border-indigo-200 bg-white px-3 py-2 text-sm text-slate-800"
          />
          <button
            type="button"
            disabled={!reason.trim() || retryingSnapshotId === latest.snapshotId}
            onClick={() => void onRetry(latest.snapshotId, reason.trim())}
            className="mt-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {retryingSnapshotId === latest.snapshotId ? "Retrying release..." : "Retry Handoff release"}
          </button>
        </div>
      ) : null}
    </div>
  );
}
