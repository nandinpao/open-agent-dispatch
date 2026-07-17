"use client";

import Link from "next/link";
import { type ReactNode, useEffect, useMemo, useState } from "react";
import { DataSourceBadge, dataSourceKindFromFlags } from "@/components/common/DataSourceBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { LiveDataUnavailable } from "@/components/common/LiveDataUnavailable";
import { ErrorBox } from "@/components/common/ErrorBox";
import { ListFilterBar, type SelectFilterConfig } from "@/components/common/ListFilterBar";
import { LoadingBox } from "@/components/common/LoadingBox";
import { PaginationControls } from "@/components/common/PaginationControls";
import { RefreshButton } from "@/components/common/RefreshButton";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useAgentGovernanceList } from "@/hooks/useAgentGovernanceList";
import { getAgentConnectionStatus, getAgentWorkloadStatus, getHeartbeatAgeMs, getHeartbeatStatus } from "@/lib/agents/agentRuntimeDisplay";
import { credentialStatusLabel as governanceCredentialStatusLabel, deriveAgentGovernanceState } from "@/lib/agents/governanceStatus";
import type { AgentDashboardRow } from "@/lib/types/dashboard";
import { formatDateTime, formatDurationMs } from "@/lib/utils/format";
import { paginateItems, recordIncludesQuery, uniqueSortedValues } from "@/lib/utils/list";

const allValue = "ALL";

type DispatchTone = "ELIGIBLE" | "LIMITED" | "BLOCKED" | "UNKNOWN";

function rowConnectionStatus(row: AgentDashboardRow): string {
  return getAgentConnectionStatus(row.runtime);
}

function rowCredentialStatus(row: AgentDashboardRow): string {
  return deriveAgentGovernanceState(row).credentialStatus;
}

function rowDispatchStatus(row: AgentDashboardRow): DispatchTone {
  if (!row.profile || !row.runtime?.connected) return "BLOCKED";
  if (rowCredentialStatus(row) !== "CREDENTIAL_ACTIVE") return "LIMITED";
  if (activeDispatchFlowCount(row) > 0) return "ELIGIBLE";
  return "LIMITED";
}


function rowAgentType(row: AgentDashboardRow): string {
  return row.profile?.agentType ?? row.enrollment?.agentType ?? "UNKNOWN";
}


function activeDispatchFlowCount(row: AgentDashboardRow): number {
  return (row.dispatchFlows ?? []).filter((flow) => String(flow.status ?? '').toUpperCase() === 'ACTIVE').length;
}

function dispatchUsageLabel(row: AgentDashboardRow): string {
  const active = activeDispatchFlowCount(row);
  if (active > 0) return `${active} active Dispatch Flow${active === 1 ? '' : 's'}`;
  const total = row.dispatchFlows?.length ?? 0;
  if (total > 0) return `${total} non-active Dispatch Flow${total === 1 ? '' : 's'}`;
  return 'Not used by any Dispatch Flow';
}

function primaryBlockedReason(row: AgentDashboardRow): string {
  if (!row.profile) return "Missing Core Agent profile.";
  if (rowCredentialStatus(row) !== "CREDENTIAL_ACTIVE") return "Credential is not active.";
  if (!row.runtime?.connected) return "Runtime is offline.";
  if (activeDispatchFlowCount(row) === 0) return "Agent connection is usable. Add this Agent to an active Dispatch Flow to make it a candidate.";
  return "Agent is selected by an active Dispatch Flow. Check Task evidence for runtime-specific blockers.";
}

function nextActionTone(row: AgentDashboardRow): "profile" | "credential" | "runtime" | "eligibility" | "monitor" {
  const action = nextAction(row).toLowerCase();
  if (action.includes("profile")) return "profile";
  if (action.includes("credential")) return "credential";
  if (action.includes("start") || action.includes("reconnect") || action.includes("runtime")) return "runtime";
  if (action.includes("flow")) return "eligibility";
  return "monitor";
}

function nextAction(row: AgentDashboardRow): string {
  const governance = deriveAgentGovernanceState(row);
  if (!row.profile) return row.runtime ? "Review runtime enrollment" : "Create Agent profile";
  if (governance.credentialStatus !== "CREDENTIAL_ACTIVE") return "Rotate or issue credential";
  if (!row.runtime?.connected) return "Start or reconnect Agent";
  if (rowDispatchStatus(row) !== "ELIGIBLE") return "Add to / review Dispatch Flow";
  return "Monitor";
}

function matchesRow(row: AgentDashboardRow, query: string, connectionStatus: string, dispatchStatus: string, credentialStatus: string, agentType: string): boolean {
  if (connectionStatus !== allValue && rowConnectionStatus(row) !== connectionStatus) return false;
  if (dispatchStatus !== allValue && rowDispatchStatus(row) !== dispatchStatus) return false;
  if (credentialStatus !== allValue && rowCredentialStatus(row) !== credentialStatus) return false;
  if (agentType !== allValue && rowAgentType(row) !== agentType) return false;

  return recordIncludesQuery([
    row.agentId,
    row.profile?.agentName,
    row.profile?.tenantId,
    rowAgentType(row),
    rowConnectionStatus(row),
    rowDispatchStatus(row),
    rowCredentialStatus(row),
    dispatchUsageLabel(row),
    nextAction(row),
    ...(row.dispatchFlows ?? []).map((flow) => `${flow.flowName ?? ""} ${flow.flowCode ?? ""} ${flow.status ?? ""}`),
  ], query);
}

function SummaryCard({ label, value, tone = "slate" }: Readonly<{ label: string; value: number; tone?: "slate" | "emerald" | "blue" | "amber" | "rose" }>) {
  const toneClass = {
    slate: "border-slate-200 bg-white text-slate-900",
    emerald: "border-emerald-200 bg-emerald-50 text-emerald-800",
    blue: "border-blue-200 bg-blue-50 text-blue-800",
    amber: "border-amber-200 bg-amber-50 text-amber-800",
    rose: "border-rose-200 bg-rose-50 text-rose-800",
  }[tone];
  return (
    <div className={`rounded-2xl border p-4 shadow-sm ${toneClass}`}>
      <div className="text-xs font-semibold uppercase tracking-wide opacity-75">{label}</div>
      <div className="mt-2 text-2xl font-bold">{value}</div>
    </div>
  );
}

function MiniLine({ label, children }: Readonly<{ label: string; children: ReactNode }>) {
  return (
    <div className="flex items-center gap-2 text-xs text-slate-600">
      <span className="min-w-20 text-slate-400">{label}</span>
      <span className="font-semibold text-slate-700">{children}</span>
    </div>
  );
}

export function AgentTable() {
  const { data, loading, refreshing, error, lastUpdatedAt, refresh } = useAgentGovernanceList();
  const [search, setSearch] = useState("");
  const [connectionFilter, setConnectionFilter] = useState(allValue);
  const [dispatchFilter, setDispatchFilter] = useState(allValue);
  const [credentialFilter, setCredentialFilter] = useState(allValue);
  const [typeFilter, setTypeFilter] = useState(allValue);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const rows = useMemo(() => data?.rows ?? [], [data]);
  const hasSourceErrors = Boolean(data && Object.values(data.sourceErrors).some(Boolean));
  const liveDataUnavailable = Boolean(
    data
    && rows.length === 0
    && (data.sourceErrors.coreAgents || data.sourceErrors.coreEnrollments)
    && data.sourceErrors.nettyAgents
    && data.sourceErrors.nettyClusterAgents
  );
  const dataSource = dataSourceKindFromFlags({
    hasLiveData: Boolean(data && (data.profiles.length > 0 || data.runtimes.length > 0 || data.enrollments.length > 0 || rows.length > 0)),
    hasSourceErrors,
  });
  const filteredRows = useMemo(
    () => rows.filter((row) => matchesRow(row, search, connectionFilter, dispatchFilter, credentialFilter, typeFilter)),
    [connectionFilter, credentialFilter, dispatchFilter, rows, search, typeFilter],
  );
  const pagination = useMemo(() => paginateItems(filteredRows, { page, pageSize }), [filteredRows, page, pageSize]);

  const summary = useMemo(() => ({
    total: rows.length,
    online: rows.filter((row) => row.runtime?.connected).length,
    eligible: rows.filter((row) => rowDispatchStatus(row) === "ELIGIBLE").length,
    limited: rows.filter((row) => rowDispatchStatus(row) === "LIMITED").length,
    blocked: rows.filter((row) => rowDispatchStatus(row) === "BLOCKED").length,
    activeFlowUsage: rows.reduce((count, row) => count + activeDispatchFlowCount(row), 0),
  }), [rows]);

  useEffect(() => {
    setPage(1);
  }, [connectionFilter, credentialFilter, dispatchFilter, pageSize, search, typeFilter]);

  const filters = useMemo<SelectFilterConfig[]>(() => {
    const connectionStatuses = uniqueSortedValues(rows.map(rowConnectionStatus));
    const dispatchStatuses = uniqueSortedValues(rows.map(rowDispatchStatus));
    const credentialStatuses = uniqueSortedValues(rows.map(rowCredentialStatus));
    const agentTypes = uniqueSortedValues(rows.map(rowAgentType));
    return [
      { id: "connection", label: "Online", value: connectionFilter, onChange: setConnectionFilter, options: [{ value: allValue, label: "All Online" }, ...connectionStatuses.map((status) => ({ value: status, label: status }))] },
      { id: "dispatch", label: "Dispatch", value: dispatchFilter, onChange: setDispatchFilter, options: [{ value: allValue, label: "All Dispatch" }, ...dispatchStatuses.map((status) => ({ value: status, label: status }))] },
      { id: "credential", label: "Credential", value: credentialFilter, onChange: setCredentialFilter, options: [{ value: allValue, label: "All Credentials" }, ...credentialStatuses.map((status) => ({ value: status, label: status }))] },
      { id: "type", label: "Type", value: typeFilter, onChange: setTypeFilter, options: [{ value: allValue, label: "All Types" }, ...agentTypes.map((status) => ({ value: status, label: status }))] },
    ];
  }, [connectionFilter, credentialFilter, dispatchFilter, rows, typeFilter]);

  const clearFilters = () => {
    setSearch("");
    setConnectionFilter(allValue);
    setDispatchFilter(allValue);
    setCredentialFilter(allValue);
    setTypeFilter(allValue);
  };

  if (loading && !data) return <LoadingBox label="Loading agents..." />;
  if (error) return <ErrorBox message={`Failed to load agents: ${error}`} />;
  if (liveDataUnavailable) {
    return (
      <LiveDataUnavailable
        title="Agent live data is unavailable"
        description="The Agents page cannot verify whether the database is empty because Core and Gateway runtime APIs are unavailable. It will not display an empty-state setup prompt as if no agents exist."
        details={Object.entries(data?.sourceErrors ?? {}).filter(([, value]) => value).map(([key, value]) => `${key}: ${value}`).join(" | ")}
        action={(
          <button type="button" onClick={() => void refresh()} className="rounded-xl border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-100">
            Retry Live Load
          </button>
        )}
      />
    );
  }
  if (!data || rows.length === 0) {
    return (
      <EmptyState
        title="No agents have been created yet"
        description="Create the first agent to start dispatching tasks. The setup flow will create an enrollment draft, optional approval, optional capability, and a start command."
        action={(
          <Link href="/agents/setup" className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-blue-700">
            Create First Agent
          </Link>
        )}
        tone="info"
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
        <div className="text-sm font-bold text-slate-900">Agent data source</div>
        <DataSourceBadge source={dataSource} detail={hasSourceErrors ? "One or more live APIs returned errors" : "Core + Gateway runtime"} />
      </div>
      <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-5">
        <SummaryCard label="Agents" value={summary.total} />
        <SummaryCard label="Online" value={summary.online} tone="blue" />
        <SummaryCard label="Eligible" value={summary.eligible} tone="emerald" />
        <SummaryCard label="Limited" value={summary.limited} tone="amber" />
        <SummaryCard label="Blocked" value={summary.blocked} tone="rose" />
      </div>

      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="text-sm font-semibold text-slate-900">Agent Operations List</div>
          <div className="mt-1 max-w-4xl text-sm text-slate-600">
            This list shows Agent connection, credential, Dispatch Flow usability, and the next action. Dispatch authority is owned by Dispatch Flows only.
          </div>
          {(data.sourceErrors.coreAgents || data.sourceErrors.coreEnrollments || data.sourceErrors.nettyAgents || data.sourceErrors.nettyClusterAgents) && (
            <div className="mt-2 text-xs text-amber-700">
              {data.sourceErrors.coreAgents ? `Core agents error: ${data.sourceErrors.coreAgents}` : null}
              {data.sourceErrors.coreEnrollments ? ` Core enrollments error: ${data.sourceErrors.coreEnrollments}` : null}
              {data.sourceErrors.nettyAgents ? ` Netty local agents error: ${data.sourceErrors.nettyAgents}` : null}
              {data.sourceErrors.nettyClusterAgents ? ` Netty cluster agents error: ${data.sourceErrors.nettyClusterAgents}` : null}
            </div>
          )}
        </div>
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={refresh} />
      </div>

      <ListFilterBar search={search} searchPlaceholder="Search agent, type, credential, dispatch status, or next action..." onSearchChange={setSearch} filters={filters} onClear={clearFilters} />

      {filteredRows.length === 0 ? (
        <EmptyState title="No matching agents" description="Adjust the search keyword or the Online, Dispatch, Credential, or Type filters." />
      ) : (
        <>
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="whitespace-nowrap px-4 py-3">Agent</th>
                    <th className="whitespace-nowrap px-4 py-3">Online</th>
                    <th className="whitespace-nowrap px-4 py-3">Credential</th>
                    <th className="whitespace-nowrap px-4 py-3">Dispatch</th>
                    <th className="whitespace-nowrap px-4 py-3">Dispatch Usage</th>
                    <th className="whitespace-nowrap px-4 py-3">Next Action</th>
                    <th className="whitespace-nowrap px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {pagination.items.map((row) => {
                    const connection = rowConnectionStatus(row);
                    const dispatch = rowDispatchStatus(row);
                    const credential = rowCredentialStatus(row);
                    const heartbeatAge = getHeartbeatAgeMs(row.runtime);
                    return (
                      <tr key={row.agentId} className="align-top hover:bg-slate-50">
                        <td className="px-4 py-3">
                          <Link href={`/agents/${encodeURIComponent(row.agentId)}`} className="font-semibold text-blue-700 hover:underline">{row.agentId}</Link>
                          <div className="mt-1 text-xs text-slate-500">{row.profile?.agentName ?? row.enrollment?.agentName ?? rowAgentType(row)}</div>
                          <div className="text-xs text-slate-400">tenant: {row.profile?.tenantId ?? row.enrollment?.tenantId ?? "-"}</div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <div className="space-y-1.5">
                            <StatusBadge status={connection} />
                            <MiniLine label="workload"><StatusBadge status={getAgentWorkloadStatus(row.runtime)} /></MiniLine>
                            <MiniLine label="heartbeat"><StatusBadge status={getHeartbeatStatus(row.runtime)} /></MiniLine>
                            <MiniLine label="node">{row.runtime?.gatewayNodeId ?? row.runtime?.nodeId ?? "-"}</MiniLine>
                            <MiniLine label="age">{formatDurationMs(heartbeatAge)}</MiniLine>
                          </div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <StatusBadge status={credential} label={governanceCredentialStatusLabel(credential as never)} />
                          <div className="mt-2 text-xs text-slate-500">expires: {formatDateTime(row.profile?.credential?.expiresAt)}</div>
                          <div className="text-xs text-slate-400">version: {row.profile?.credential?.credentialVersion ?? "-"}</div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <StatusBadge status={dispatch} />
                          <div className="mt-2 max-w-xs text-xs leading-5 text-slate-500">
                            {dispatch === "ELIGIBLE" ? "Selected by an active Dispatch Flow and ready for direct dispatch." : primaryBlockedReason(row)}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="text-xs text-slate-500">standard authority</div>
                          <div className="mt-1 font-semibold text-slate-800">Dispatch Flow</div>
                          <div className="mt-2 text-xs text-slate-500">usage evidence</div>
                          <div className="mt-1 max-w-xs break-words text-xs text-slate-500">
                            {dispatchUsageLabel(row)}
                          </div>
                        </td>
                        <td className="max-w-sm px-4 py-3 text-sm text-slate-700">
                          <div className="font-semibold text-slate-900">{nextAction(row)}</div>
                          <div className="mt-1 text-xs leading-5 text-slate-500">
                            {nextActionTone(row) === "profile" ? "Add this Agent to an active Dispatch Flow or review the Task evidence." : null}
                            {nextActionTone(row) === "credential" ? "Issue or rotate the token/JWT credential." : null}
                            {nextActionTone(row) === "runtime" ? "Start the local simulator or reconnect this Agent runtime." : null}
                            {nextActionTone(row) === "eligibility" ? "Review Dispatch Flow, Agent connection, optional capability, and Task evidence." : null}
                            {nextActionTone(row) === "monitor" ? "No immediate action required." : null}
                          </div>
                          <div className="mt-2 flex flex-wrap gap-2">
                            <Link href={`/agents/${encodeURIComponent(row.agentId)}`} className="inline-block rounded-lg border border-blue-200 px-2 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50">Open Agent Detail</Link>
                            {dispatch !== "ELIGIBLE" ? (
                              <Link href={`/agents/${encodeURIComponent(row.agentId)}#dispatch-summary`} className="inline-block rounded-lg border border-amber-200 px-2 py-1 text-xs font-semibold text-amber-700 hover:bg-amber-50">Fix blocking issue</Link>
                            ) : null}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <Link href={`/agents/${encodeURIComponent(row.agentId)}`} className="inline-block rounded-lg border border-slate-200 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50">Open Detail</Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <PaginationControls page={pagination.page} pageSize={pagination.pageSize} totalItems={pagination.totalItems} totalPages={pagination.totalPages} startItem={pagination.startItem} endItem={pagination.endItem} onPageChange={setPage} onPageSizeChange={setPageSize} />
        </>
      )}
    </div>
  );
}
