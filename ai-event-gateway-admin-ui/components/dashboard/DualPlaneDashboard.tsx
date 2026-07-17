'use client';

import { DataSourceBadge, dataSourceKindFromFlags } from '@/components/common/DataSourceBadge';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { LiveDataUnavailable } from '@/components/common/LiveDataUnavailable';
import { LoadingBox } from '@/components/common/LoadingBox';
import { MetricCard } from '@/components/common/MetricCard';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';
import { useDualDashboard, type DualDashboardData } from '@/hooks/useDualDashboard';
import { getPublicEnv } from '@/lib/constants/env';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { buildRuntimeEventCenterSummary, getRuntimeEventDisplay } from '@/lib/realtime/runtimeEventCenter';
import { latestRejectedConnectionsEmptyText, rejectedConnectionMetricSubtitle, rejectedConnectionSemantics } from '@/lib/runtime/rejectedConnectionSemantics';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { AgentSecurityEvent, CoreRecoveryApprovalRequest, CoreRecoveryOperationMetricsSnapshot, CoreRecoveryOperatorRunbook, CoreTaskRuntimeView } from '@/lib/types/core';
import type { NettyRejectedConnection } from '@/lib/types/nettyRuntime';
import { formatDateTime, formatDurationMs, formatNumber } from '@/lib/utils/format';

type PlaneStatus = 'OK' | 'PARTIAL' | 'UNAVAILABLE';

function hasErrors(errors: Record<string, string | undefined>, prefix: 'core' | 'netty'): boolean {
  return Object.entries(errors).some(([key, value]) => key.startsWith(prefix) && Boolean(value));
}

function planeStatus(data: DualDashboardData, plane: 'core' | 'netty'): PlaneStatus {
  if (plane === 'core') {
    if (!data.coreSnapshot && data.profiles.length === 0 && data.tasks.length === 0 && data.securityEvents.length === 0) return 'UNAVAILABLE';
    return hasErrors(data.sourceErrors, 'core') ? 'PARTIAL' : 'OK';
  }

  if (!data.nettySnapshot && data.runtimes.length === 0 && data.rejectedConnections.length === 0 && !data.delivery && !data.callbackRelay) return 'UNAVAILABLE';
  return hasErrors(data.sourceErrors, 'netty') ? 'PARTIAL' : 'OK';
}

function recentSecurityEvents(events: AgentSecurityEvent[]): AgentSecurityEvent[] {
  return [...events]
    .sort((left, right) => new Date(right.occurredAt).getTime() - new Date(left.occurredAt).getTime())
    .slice(0, 5);
}

function recentTasks(tasks: CoreTaskRuntimeView[]): CoreTaskRuntimeView[] {
  return [...tasks]
    .sort((left, right) => new Date(right.updatedAt ?? right.createdAt ?? 0).getTime() - new Date(left.updatedAt ?? left.createdAt ?? 0).getTime())
    .slice(0, 5);
}

function riskyAgentRows(rows: AgentDashboardRow[]): AgentDashboardRow[] {
  return rows.filter((row) => (
    row.source.profile === 'MISSING'
    || (row.runtime?.connected && row.profile && row.profile.approvalStatus !== 'APPROVED')
    || (row.runtime?.connected && row.profile && !row.profile.enabled)
  )).slice(0, 5);
}

function latestRejectedConnections(items: NettyRejectedConnection[]): NettyRejectedConnection[] {
  return [...items]
    .sort((left, right) => new Date(right.lastSeenAt ?? right.occurredAt ?? 0).getTime() - new Date(left.lastSeenAt ?? left.occurredAt ?? 0).getTime())
    .slice(0, 5);
}

function SourceErrorList({ errors }: Readonly<{ errors: Record<string, string | undefined> }>) {
  const entries = Object.entries(errors).filter(([, value]) => value);
  if (entries.length === 0) return null;

  return (
    <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
      <div className="font-bold">Some live data sources failed. The dashboard is showing only the live data that was returned.</div>
      <ul className="mt-2 list-disc space-y-1 pl-5">
        {entries.map(([key, value]) => <li key={key}>{key}: {value}</li>)}
      </ul>
    </div>
  );
}

function RealtimeSummary() {
  const { connection, events, lastMetricsAt } = useAdminRealtime();
  const summary = buildRuntimeEventCenterSummary(events);
  const latestEvents = events.slice(0, 8);

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-950">Realtime Event Center</div>
          <div className="mt-1 text-xs text-slate-500">Netty runtime stream 的摘要：Agent authorization、delivery、callback relay 與 security event。這不是 Core 權威狀態。</div>
        </div>
        <StatusBadge status={connection.status} />
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        <MiniStat label="Buffered Events" value={summary.totalEvents} />
        <MiniStat label="Security/Auth" value={summary.securityEvents + summary.deniedAuthorizations} />
        <MiniStat label="Delivery" value={summary.deliveryEvents} />
        <MiniStat label="Callback" value={summary.callbackEvents} />
        <MiniStat label="Risk / Failed" value={summary.failedOrRiskEvents} />
      </div>
      <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-500">
        Last message：{connection.lastMessageAt ? formatDateTime(connection.lastMessageAt) : '-'}；Last metrics：{lastMetricsAt ? formatDateTime(lastMetricsAt) : '-'}
      </div>
      <div className="space-y-2">
        {latestEvents.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-300 p-4 text-center text-sm text-slate-500">尚未收到 realtime event。</div>
        ) : latestEvents.map((event, index) => {
          const display = getRuntimeEventDisplay(event);
          return (
            <div key={`${event.timestamp}-${event.eventType}-${index}`} className="rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-xs font-bold text-slate-900">{display.title}</div>
                  <div className="mt-1 text-xs text-slate-500">{event.eventType} · {formatDateTime(event.timestamp)}</div>
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  <StatusBadge status={display.severity} />
                  <StatusBadge status={display.category} />
                </div>
              </div>
              <div className="mt-2 grid grid-cols-1 gap-1 text-xs text-slate-600 md:grid-cols-4">
                <div>Node：{event.nodeId ?? '-'}</div>
                <div>Agent：{event.agentId ?? '-'}</div>
                <div>Task：{event.taskId ?? '-'}</div>
                <div>Trace：{event.traceId ?? '-'}</div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function MiniStat({ label, value, suffix }: Readonly<{ label: string; value: number | string; suffix?: string }>) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
      <div className="text-xs font-medium text-slate-500">{label}</div>
      <div className="mt-1 text-xl font-bold text-slate-950">{typeof value === 'number' ? formatNumber(value) : value}</div>
      {suffix ? <div className="mt-1 text-xs text-slate-500">{suffix}</div> : null}
    </div>
  );
}

function SectionTitle({ title, description, status }: Readonly<{ title: string; description: string; status?: string }>) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h2 className="text-base font-bold text-slate-950">{title}</h2>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      </div>
      {status ? <StatusBadge status={status} /> : null}
    </div>
  );
}


function recordValue(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, segment) => {
    if (typeof current !== 'object' || current === null || Array.isArray(current)) return undefined;
    return (current as Record<string, unknown>)[segment];
  }, value);
}

function metricNumber(value: unknown, path: string): number | undefined {
  const raw = recordValue(value, path);
  if (typeof raw === 'number' && Number.isFinite(raw)) return raw;
  if (typeof raw === 'string' && raw.trim()) {
    const parsed = Number(raw);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

function metricStatus(value: unknown, path: string): string {
  const raw = recordValue(value, path);
  return typeof raw === 'string' && raw.trim() ? raw : 'UNKNOWN';
}

function formatRatio(value: number | undefined): string {
  if (value === undefined) return '-';
  return `${(value * 100).toFixed(1)}%`;
}

function SloObjectiveCard({ title, status, value, subtitle }: Readonly<{ title: string; status: string; value: string; subtitle: string }>) {
  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="text-xs font-bold uppercase tracking-wide text-slate-500">{title}</div>
        <StatusBadge status={status} />
      </div>
      <div className="mt-2 text-2xl font-bold text-slate-950">{value}</div>
      <div className="mt-1 text-xs text-slate-500">{subtitle}</div>
    </div>
  );
}

function OperationalSloPanel({ slo }: Readonly<{ slo?: Record<string, unknown> }>) {
  if (!slo) return null;
  const status = metricStatus(slo, 'status');
  const callbackMax = metricNumber(slo, 'callbackLag.maxSeconds');
  const callbackP95 = metricNumber(slo, 'callbackLag.p95Seconds');
  const retryWaiting = metricNumber(slo, 'dispatchReliability.retryWaiting');
  const deadLetter = metricNumber(slo, 'dispatchReliability.deadLetter');
  const adapterFailureRatio = metricNumber(slo, 'adapterExecutor.failureRatio');
  const noCandidateRatio = metricNumber(slo, 'routingNoCandidate.noCandidateRatio');
  const alerts = Array.isArray((slo as Record<string, unknown>).alerts) ? (slo as Record<string, unknown>).alerts as unknown[] : [];

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-900">Operational SLO Snapshot</div>
          <div className="mt-1 text-xs text-slate-500">Core 權威營運指標：callback lag、dispatch retry/dead-letter、adapter failure、routing no-candidate。</div>
        </div>
        <StatusBadge status={status} />
      </div>
      <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <SloObjectiveCard title="Callback Lag" status={metricStatus(slo, 'callbackLag.status')} value={`${formatNumber(callbackMax)} s`} subtitle={`p95 ${formatNumber(callbackP95)} s`} />
        <SloObjectiveCard title="Dispatch Retry / DLQ" status={metricStatus(slo, 'dispatchReliability.status')} value={`${formatNumber(retryWaiting)} / ${formatNumber(deadLetter)}`} subtitle="retry waiting / dead-letter" />
        <SloObjectiveCard title="Adapter Failure" status={metricStatus(slo, 'adapterExecutor.status')} value={formatRatio(adapterFailureRatio)} subtitle="failed + retrying / sample" />
        <SloObjectiveCard title="No Candidate" status={metricStatus(slo, 'routingNoCandidate.status')} value={formatRatio(noCandidateRatio)} subtitle="routing no-candidate ratio" />
      </div>
      {alerts.length > 0 ? <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">Active SLO alerts：{alerts.length}</div> : null}
    </div>
  );
}

function ControlPlaneSection({ data }: Readonly<{ data: DualDashboardData }>) {
  const summary = data.summaries.control;
  const tasks = recentTasks(data.tasks);

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <SectionTitle
        title="Business Truth / Core Authority"
        description="Core 權威資料：Incident、Task、Agent 審核、Skill approval、Security 與 operator decision。"
        status={planeStatus(data, 'core')}
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title="Open Incidents" value={formatNumber(summary.openIncidents)} subtitle="Core incident authority" />
        <MetricCard title="Pending Tasks" value={formatNumber(summary.pendingTasks)} subtitle="Core task state" />
        <MetricCard title="Failed Dispatch" value={formatNumber(summary.failedDispatches)} subtitle="Delivery failed / timeout" />
        <MetricCard title="Dead-letter" value={formatNumber(summary.deadLetterDispatches)} subtitle="Needs operator recovery" />
        <MetricCard title="Pending Approvals" value={formatNumber(summary.pendingAgentApprovals)} subtitle="Agent enrollment review" />
        <MetricCard title="Approved Agents" value={formatNumber(summary.approvedAgents)} subtitle="Core trusted profiles" />
        <MetricCard title="Suspended / Revoked" value={formatNumber(summary.suspendedOrRevokedAgents)} subtitle="Risk-controlled agents" />
        <MetricCard title="Core Profiles" value={formatNumber(data.profiles.length)} subtitle="Agent profile rows" />
      </div>
      <OperationalSloPanel slo={data.coreSnapshot?.operationalSlo} />
      <div>
        <div className="mb-3 text-sm font-bold text-slate-900">Recent Core Tasks</div>
        {tasks.length === 0 ? <div className="rounded-xl border border-dashed border-slate-300 p-4 text-center text-sm text-slate-500">尚無 Core task runtime-view 資料。</div> : (
          <div className="overflow-hidden rounded-xl border border-slate-200">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Task</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Agent</th>
                  <th className="px-4 py-3">Dispatch</th>
                  <th className="px-4 py-3">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {tasks.map((task) => (
                  <tr key={task.taskId}>
                    <td className="px-4 py-3 font-medium text-slate-900">{task.taskId}</td>
                    <td className="px-4 py-3"><StatusBadge status={String(task.status)} /></td>
                    <td className="px-4 py-3 text-slate-600">{task.assignedAgentId ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-600">{task.dispatchStatus ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-500">{formatDateTime(task.updatedAt ?? task.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}


function DispatchTruthSection({ data }: Readonly<{ data: DualDashboardData }>) {
  const tasks = data.tasks;
  const awaitingCallback = tasks.filter((task) => String(task.callbackStatus ?? '').toUpperCase().includes('WAIT')).length;
  const retrying = tasks.filter((task) => Boolean(task.nextDispatchAttemptAt) || String(task.dispatchStatus ?? '').toUpperCase().includes('RETRY')).length;
  const deadLetter = tasks.filter((task) => String(task.dispatchStatus ?? '').toUpperCase() === 'DEAD_LETTER' || String(task.status ?? '').toUpperCase() === 'DEAD_LETTER').length;
  const withDispatchRequest = tasks.filter((task) => Boolean(task.dispatchRequestId)).length;

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <SectionTitle
        title="Dispatch Truth / Callback Ledger"
        description="Core persisted dispatch view：用 Dispatch Request、Timeline、Attempt Ledger 與 Callback Inbox 解釋任務卡在哪一步。"
        status={planeStatus(data, 'core')}
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title="Tasks With Dispatch" value={formatNumber(withDispatchRequest)} subtitle="Core dispatch request linked" />
        <MetricCard title="Awaiting Callback" value={formatNumber(awaitingCallback)} subtitle="Callback not authoritative yet" />
        <MetricCard title="Retry / Recovery" value={formatNumber(retrying)} subtitle="Next attempt or retry state" />
        <MetricCard title="Dead-letter" value={formatNumber(deadLetter)} subtitle="Operator recovery required" />
      </div>
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">1. Routing</div>
          <div className="mt-2 text-sm font-bold text-slate-900">Core 找候選 Agent</div>
          <div className="mt-1 text-xs leading-5 text-slate-500">看 Skill、Agent approval、capability、policy 與 no-candidate reason。</div>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">2. Delivery</div>
          <div className="mt-2 text-sm font-bold text-slate-900">Netty 送 command</div>
          <div className="mt-1 text-xs leading-5 text-slate-500">delivery event 只能證明 transport 嘗試，不代表任務完成。</div>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">3. Callback</div>
          <div className="mt-2 text-sm font-bold text-slate-900">Core inbox 接收結果</div>
          <div className="mt-1 text-xs leading-5 text-slate-500">ACK / progress / result / error 需進入 Core callback inbox 才可更新 lifecycle。</div>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">4. Recovery</div>
          <div className="mt-2 text-sm font-bold text-slate-900">Retry 或 dead-letter</div>
          <div className="mt-1 text-xs leading-5 text-slate-500">延遲重派、manual retry、DLQ 與 issue sync 都應回到 Core ledger 判斷。</div>
        </div>
      </div>
    </section>
  );
}

function RuntimePlaneSection({ data }: Readonly<{ data: DualDashboardData }>) {
  const summary = data.summaries.runtime;

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <SectionTitle
        title="Runtime Diagnostics"
        description="Netty 即時資料：Agent session、Gateway runtime、delivery、callback relay 與 rejected connections；只解釋 transport 現象。"
        status={planeStatus(data, 'netty')}
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title="Online Agents" value={formatNumber(summary.onlineAgents)} subtitle="Netty connected runtime" />
        <MetricCard title="Authorized Sessions" value={formatNumber(summary.authorizedAgents)} subtitle="Core-authorized Netty sessions" />
        <MetricCard title="Runtime-only Agents" value={formatNumber(summary.runtimeOnlyAgents)} subtitle="Runtime exists, Core profile missing" />
        <MetricCard title="Gateway Nodes" value={formatNumber(summary.gatewayNodeCount)} subtitle="Runtime / cluster snapshot" />
        <MetricCard title="Rejected Connections" value={formatNumber(summary.rejectedConnections)} subtitle={rejectedConnectionMetricSubtitle()} />
        <MetricCard title="Delivery Failures" value={formatNumber(summary.deliveryFailures)} subtitle="Netty delivery runtime" />
        <MetricCard title="Callback Failures" value={formatNumber(summary.callbackRelayFailures)} subtitle="Callback relay runtime" />
        <MetricCard title="Inflight Delivery" value={formatNumber(summary.inflightDeliveries)} subtitle="Current command delivery" />
      </div>
      {data.runtimeSlo ? (
        <div className="rounded-2xl border border-slate-200 p-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="text-sm font-bold text-slate-900">Gateway Runtime SLO Snapshot</div>
              <div className="mt-1 text-xs text-slate-500">Netty local runtime 指標：delivery backlog、gateway relay backlog、callback relay failure ratio。這不是 Core 任務真相。</div>
            </div>
            <StatusBadge status={data.runtimeSlo.status ?? 'UNKNOWN'} />
          </div>
          <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
            <SloObjectiveCard title="Delivery Backlog" status={metricStatus(data.runtimeSlo, 'deliveryBacklog.status')} value={formatNumber(metricNumber(data.runtimeSlo, 'deliveryBacklog.value'))} subtitle="active deliveries" />
            <SloObjectiveCard title="Relay Backlog" status={metricStatus(data.runtimeSlo, 'gatewayRelayBacklog.status')} value={formatNumber(metricNumber(data.runtimeSlo, 'gatewayRelayBacklog.value'))} subtitle="gateway relay backlog" />
            <SloObjectiveCard title="Callback Relay Failure" status={metricStatus(data.runtimeSlo, 'callbackRelay.status')} value={formatRatio(metricNumber(data.runtimeSlo, 'callbackRelay.ratio'))} subtitle="failed + rejected / relay total" />
          </div>
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-bold text-slate-900">Delivery Runtime</div>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <MiniStat label="Success" value={formatNumber(data.delivery?.successCount ?? '-')} />
            <MiniStat label="Failed" value={formatNumber(data.delivery?.failedCount ?? '-')} />
            <MiniStat label="Inflight" value={formatNumber(data.delivery?.inFlightCount ?? '-')} />
            <MiniStat label="Avg Latency" value={formatDurationMs(data.delivery?.averageLatencyMs)} />
          </div>
        </div>
        <div className="rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-bold text-slate-900">Callback Relay Runtime</div>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <MiniStat label="Success" value={formatNumber(data.callbackRelay?.successCount ?? '-')} />
            <MiniStat label="Failed" value={formatNumber(data.callbackRelay?.failedCount ?? '-')} />
            <MiniStat label="Avg Latency" value={formatDurationMs(data.callbackRelay?.averageLatencyMs)} />
            <MiniStat label="Attempts" value={formatNumber(Array.isArray(data.callbackRelay?.recentAttempts) ? data.callbackRelay.recentAttempts.length : 0)} />
          </div>
        </div>
      </div>
    </section>
  );
}


function RecoveryOperationsSection({ metrics, runbook, approvals, error, runbookError, approvalError, onRefresh }: Readonly<{ metrics: CoreRecoveryOperationMetricsSnapshot | null; runbook: CoreRecoveryOperatorRunbook | null; approvals: CoreRecoveryApprovalRequest[]; error?: string; runbookError?: string; approvalError?: string; onRefresh: () => Promise<void> | void }>) {
  const totals = metrics?.totals;
  const alerts = metrics?.alerts ?? [];
  const activeAlerts = alerts.filter((alert) => alert.severity !== 'OK');
  const latestCritical = metrics?.recentCriticalEvents ?? [];

  async function decideApproval(approval: CoreRecoveryApprovalRequest, decision: 'approve' | 'reject') {
    const reason = window.prompt(`請輸入 ${decision === 'approve' ? '核准' : '拒絕'} approval ${approval.approvalId} 的原因，至少 12 個字元。`)?.trim();
    if (!reason || reason.length < 12) {
      window.alert('原因太短；P10.7 要求至少 12 個字元。');
      return;
    }
    const phrase = runbook?.policy?.approvalConfirmationPhrase ?? 'CONFIRM_DUAL_CONTROL_APPROVAL';
    const confirmed = window.confirm(`${decision === 'approve' ? '核准並執行' : '拒絕'}是雙人覆核操作。請確認你不是原申請人，且已檢查 dispatch timeline。`);
    if (!confirmed) return;
    const supplied = window.prompt(`請輸入核准確認字串：${phrase}`)?.trim();
    if (supplied !== phrase) {
      window.alert(`確認字串不正確。必須輸入：${phrase}`);
      return;
    }
    const body = {
      operatorId: 'admin-ui-approver',
      reason,
      riskAcknowledged: true,
      confirmationPhrase: phrase,
      requestId: `admin-ui-recovery-approval-${decision}-${Date.now()}`
    };
    if (decision === 'approve') {
      await coreAdminApi.approveRecoveryApprovalRequest(approval.approvalId, body);
    } else {
      await coreAdminApi.rejectRecoveryApprovalRequest(approval.approvalId, body);
    }
    await onRefresh();
  }

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <SectionTitle
        title="Recovery Operations / Alerting"
        description="P10.4 指標：以 Core dispatch attempt history 統計 delayed requeue、runtime backoff、dead-letter 與 scanner failed，支援 SLA/SLO 與告警判斷。"
        status={error ? 'UNAVAILABLE' : metrics?.status ?? 'NO_DATA'}
      />
      {error ? <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm font-semibold text-amber-800">{error}</div> : null}
      {!error && !metrics ? <div className="rounded-xl border border-dashed border-slate-300 p-4 text-center text-sm text-slate-500">Core 尚未回傳 recovery metrics。</div> : null}
      {metrics ? (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title="Window Events" value={formatNumber(metrics.totalEvents)} subtitle={`Since ${formatDateTime(metrics.windowStart)}`} />
            <MetricCard title="Runtime Failures" value={formatNumber(totals?.runtimeDeliveryFailed ?? 0)} subtitle="Trigger requeue/backoff" />
            <MetricCard title="Runtime Backoff" value={formatNumber(totals?.runtimeBackoffApplied ?? 0)} subtitle="Failed agent excluded" />
            <MetricCard title="Task Requeued" value={formatNumber(totals?.taskRequeued ?? 0)} subtitle="Same task rerouted" />
            <MetricCard title="Delayed Requeue" value={formatNumber(totals?.delayedRequeueScheduled ?? 0)} subtitle="No candidate available" />
            <MetricCard title="Scanner Failed" value={formatNumber(totals?.delayedRequeueFailed ?? 0)} subtitle="Recovery worker error" />
            <MetricCard title="Recovery Exhausted" value={formatNumber(totals?.recoveryExhausted ?? 0)} subtitle="Max attempts reached" />
            <MetricCard title="Dead-letter" value={formatNumber(totals?.deadLettered ?? 0)} subtitle="Operator action needed" />
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <div className="rounded-xl border border-slate-200 p-4 xl:col-span-2">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-sm font-bold text-slate-900">Alert Policy Evaluation</div>
                  <div className="mt-1 text-xs text-slate-500">Window：{metrics.window}；History limit：{formatNumber(metrics.historyLimit)}</div>
                </div>
                <StatusBadge status={metrics.status} />
              </div>
              <div className="mt-3 space-y-2">
                {alerts.length === 0 ? <div className="text-sm text-slate-500">沒有設定告警規則。</div> : alerts.map((alert) => (
                  <div key={alert.code} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-sm">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <div className="font-bold text-slate-900">{alert.code}</div>
                        <div className="mt-1 text-xs text-slate-500">{alert.message}</div>
                      </div>
                      <StatusBadge status={alert.severity} />
                    </div>
                    <div className="mt-2 grid grid-cols-3 gap-2 text-xs text-slate-600">
                      <div>Observed：<span className="font-bold">{formatNumber(alert.observed)}</span></div>
                      <div>Warning：{formatNumber(alert.warningThreshold)}</div>
                      <div>Critical：{formatNumber(alert.criticalThreshold)}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 p-4">
              <div className="text-sm font-bold text-slate-900">Top Recovery Agents</div>
              <div className="mt-3 space-y-2">
                {(metrics.byAgent ?? []).slice(0, 6).length === 0 ? <div className="text-sm text-slate-500">沒有 Agent recovery 事件。</div> : metrics.byAgent.slice(0, 6).map((bucket) => (
                  <div key={bucket.key} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold text-slate-900">{bucket.key}</span>
                      <span>{formatNumber(bucket.count)}</span>
                    </div>
                    <div className="mt-1 text-slate-500">Latest：{bucket.latestOccurredAt ? formatDateTime(bucket.latestOccurredAt) : '-'}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {activeAlerts.length > 0 ? (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              <div className="font-bold">Active recovery alerts：{activeAlerts.map((alert) => `${alert.code}=${alert.severity}`).join('；')}</div>
            </div>
          ) : null}

          <div className="rounded-xl border border-slate-200 p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-slate-900">P10.7 Operator Runbook</div>
                <div className="mt-1 text-xs text-slate-500">Runbook 由 Core /admin/recovery/runbook 提供；包含 P10.7 RBAC、reason、confirmation 與 dual-control policy。</div>
              </div>
              <StatusBadge status={runbookError ? 'RUNBOOK_UNAVAILABLE' : runbook?.version ?? 'NO_RUNBOOK'} />
            </div>
            {runbookError ? <div className="mt-3 rounded-xl bg-amber-50 p-3 text-xs font-semibold text-amber-700">{runbookError}</div> : null}
            {runbook?.policy ? (
              <div className="mt-3 grid gap-2 rounded-xl border border-blue-100 bg-blue-50 p-3 text-xs text-blue-900 md:grid-cols-3">
                <div><span className="font-bold">Reason:</span> {runbook.policy.requireReason ? `required, min ${runbook.policy.minReasonLength ?? 0}` : 'optional'}</div>
                <div><span className="font-bold">Moderate:</span> {runbook.policy.recoveryOperatorRole ?? 'RECOVERY_OPERATOR'} / {runbook.policy.moderateConfirmationPhrase ?? '-'}</div>
                <div><span className="font-bold">High:</span> {runbook.policy.recoveryAdminRole ?? 'RECOVERY_ADMIN'} / {runbook.policy.highRiskConfirmationPhrase ?? '-'}</div>
                <div><span className="font-bold">Approver:</span> {runbook.policy.recoveryApproverRole ?? 'RECOVERY_APPROVER'} / {runbook.policy.approvalConfirmationPhrase ?? '-'}</div>
              </div>
            ) : null}
            <div className="mt-3 grid gap-3 xl:grid-cols-2">
              {(runbook?.entries ?? []).slice(0, 6).map((entry) => (
                <div key={entry.alertCode} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                  <div className="flex items-start justify-between gap-2">
                    <div className="font-bold text-slate-900">{entry.title}</div>
                    <StatusBadge status={entry.alertCode} />
                  </div>
                  <div className="mt-2 font-semibold text-slate-600">Safe actions</div>
                  <ul className="mt-1 list-disc space-y-1 pl-4">
                    {entry.safeActions.slice(0, 2).map((action) => <li key={action}>{action}</li>)}
                  </ul>
                </div>
              ))}
              {!runbookError && (!runbook || runbook.entries.length === 0) ? <div className="text-sm text-slate-500">Core 尚未回傳 runbook。</div> : null}
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-slate-900">P10.7 Pending Recovery Approvals</div>
                <div className="mt-1 text-xs text-slate-500">High-risk recovery actions now require request → second-person approval → execution.</div>
              </div>
              <StatusBadge status={approvalError ? 'APPROVAL_QUEUE_UNAVAILABLE' : approvals.length > 0 ? `${approvals.length}_PENDING` : 'NO_PENDING_APPROVALS'} />
            </div>
            {approvalError ? <div className="mt-3 rounded-xl bg-amber-50 p-3 text-xs font-semibold text-amber-700">{approvalError}</div> : null}
            <div className="mt-3 space-y-2">
              {approvals.length === 0 ? <div className="text-sm text-slate-500">目前沒有 pending recovery approvals。</div> : approvals.slice(0, 8).map((approval) => (
                <div key={approval.approvalId} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                  <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                      <div className="font-bold text-slate-900">{approval.action}</div>
                      <div className="mt-1">Approval：{approval.approvalId}</div>
                      <div className="mt-1">Task：{approval.taskId ?? '-'}；Dispatch：{approval.dispatchRequestId ?? '-'}</div>
                      <div className="mt-1">Requested by：{approval.requestedBy ?? '-'}；Expires：{approval.expiresAt ? formatDateTime(approval.expiresAt) : '-'}</div>
                      <div className="mt-1 text-slate-500">Reason：{approval.requestReason ?? '-'}</div>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <button type="button" onClick={() => void decideApproval(approval, 'approve')} className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-bold text-white hover:bg-emerald-700">Approve & Execute</button>
                      <button type="button" onClick={() => void decideApproval(approval, 'reject')} className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-bold text-rose-700 hover:bg-rose-100">Reject</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 p-4">
            <div className="text-sm font-bold text-slate-900">Recent Critical Recovery Events</div>
            <div className="mt-3 space-y-2">
              {latestCritical.length === 0 ? <div className="text-sm text-slate-500">目前沒有 critical recovery events。</div> : latestCritical.map((event) => (
                <div key={event.historyId} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <div className="font-bold text-slate-900">{event.eventType}</div>
                      <div className="mt-1">Task：{event.taskId ?? '-'}；Agent：{event.agentId ?? '-'}</div>
                      <div className="mt-1 text-slate-500">{event.reason ?? event.errorMessage ?? '-'}</div>
                    </div>
                    <StatusBadge status={event.errorCode ?? event.status ?? event.eventType} />
                  </div>
                  <div className="mt-2 text-slate-500">Occurred：{event.occurredAt ? formatDateTime(event.occurredAt) : '-'}</div>
                </div>
              ))}
            </div>
          </div>
        </>
      ) : null}
    </section>
  );
}

function TrustPlaneSection({ data }: Readonly<{ data: DualDashboardData }>) {
  const summary = data.summaries.trust;
  const riskyRows = riskyAgentRows(data.agentRows);
  const securityEvents = recentSecurityEvents(data.securityEvents);
  const rejectedConnections = latestRejectedConnections(data.rejectedConnections);

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <SectionTitle
        title="Security / Trust"
        description="以 Core 的治理狀態與 Netty 的拒絕連線觀測交叉比對，避免未知或未授權 Agent 被誤認為可派工。"
        status={summary.criticalSecurityEvents > 0 || summary.connectedButNotApproved > 0 || summary.connectedButDisabled > 0 || summary.profileMissingRuntimePresent > 0 ? 'WARNING' : 'OK'}
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title="Security Events" value={formatNumber(summary.recentSecurityEvents)} subtitle="Core security events" />
        <MetricCard title="Critical Events" value={formatNumber(summary.criticalSecurityEvents)} subtitle="ERROR / CRITICAL" />
        <MetricCard title="Connected Not Approved" value={formatNumber(summary.connectedButNotApproved)} subtitle="Runtime online but Core not approved" />
        <MetricCard title="Connected Disabled" value={formatNumber(summary.connectedButDisabled)} subtitle="Runtime online but Core disabled" />
        <MetricCard title="Runtime Without Profile" value={formatNumber(summary.profileMissingRuntimePresent)} subtitle="Potential unknown Agent" />
        <MetricCard title="Approved Offline" value={formatNumber(summary.approvedButOffline)} subtitle="Trusted but not connected" />
        <MetricCard title="Rejected Runtime" value={formatNumber(data.rejectedConnections.length)} subtitle={rejectedConnectionMetricSubtitle()} />
        <MetricCard title="Merged Agent Rows" value={formatNumber(data.agentRows.length)} subtitle="Core + Netty merge view" />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-bold text-slate-900">Agent Trust Mismatches</div>
          <div className="mt-3 space-y-2">
            {riskyRows.length === 0 ? <div className="text-sm text-slate-500">目前沒有明顯 Core / Netty Agent mismatch。</div> : riskyRows.map((row) => (
              <div key={row.agentId} className="rounded-xl border border-amber-100 bg-amber-50 p-3 text-xs text-amber-900">
                <div className="font-bold">{row.agentId}</div>
                <div className="mt-1">Profile：{row.source.profile}；Runtime：{row.source.runtime}</div>
                <div className="mt-1">Approval：{row.profile?.approvalStatus ?? '-'}；Enabled：{row.profile ? String(row.profile.enabled) : '-'}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-bold text-slate-900">Recent Security Events</div>
          <div className="mt-3 space-y-2">
            {securityEvents.length === 0 ? <div className="text-sm text-slate-500">目前沒有 Core security event。</div> : securityEvents.map((event) => (
              <div key={event.eventId} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                <div className="flex items-start justify-between gap-2">
                  <div className="font-bold text-slate-900">{event.eventType}</div>
                  {event.severity ? <StatusBadge status={event.severity} /> : null}
                </div>
                <div className="mt-1">Agent：{event.agentId ?? event.claimedAgentId ?? '-'}</div>
                <div className="mt-1">{formatDateTime(event.occurredAt)}</div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-bold text-slate-900">Latest Rejected Connections</div>
          <div className="mt-1 text-xs text-slate-500">{rejectedConnectionSemantics().title}；manual disconnect 請看 Runtime Events。</div>
          <div className="mt-3 space-y-2">
            {rejectedConnections.length === 0 ? <div className="text-sm text-slate-500">{latestRejectedConnectionsEmptyText()}</div> : rejectedConnections.map((item, index) => (
              <div key={item.id ?? item.eventId ?? `${item.claimedAgentId}-${index}`} className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
                <div className="flex items-start justify-between gap-2">
                  <div className="font-bold text-slate-900">{item.claimedAgentId ?? item.agentId ?? '-'}</div>
                  {item.authorizationState ? <StatusBadge status={item.authorizationState} /> : null}
                </div>
                <div className="mt-1">Reason：{item.reason ?? '-'}</div>
                <div className="mt-1">Last：{formatDateTime(item.lastSeenAt ?? item.occurredAt)}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export function DualPlaneDashboard() {
  const env = getPublicEnv();
  const { mode } = useAdminUiMode();
  const dashboard = useDualDashboard();

  if (dashboard.loading && !dashboard.data) return <LoadingBox label="Loading Core / Gateway dashboard snapshot..." />;
  if (!dashboard.data && dashboard.error) return <ErrorBox message={dashboard.error} />;
  if (!dashboard.data) return <ErrorBox message="Dashboard data is empty." />;

  const data = dashboard.data;
  const coreStatus = planeStatus(data, 'core');
  const nettyStatus = planeStatus(data, 'netty');
  const hasSourceErrors = Object.values(data.sourceErrors).some(Boolean);
  const hasAnyLiveData = Boolean(
    data.coreSnapshot
    || data.nettySnapshot
    || data.profiles.length
    || data.runtimes.length
    || data.tasks.length
    || data.securityEvents.length
    || data.rejectedConnections.length
    || data.delivery
    || data.callbackRelay
  );
  const dashboardSource = dataSourceKindFromFlags({ hasLiveData: hasAnyLiveData, hasSourceErrors });
  if (coreStatus === 'UNAVAILABLE' && nettyStatus === 'UNAVAILABLE' && hasSourceErrors) {
    return (
      <LiveDataUnavailable
        title="Dashboard live data is unavailable"
        description="Neither Core nor Gateway returned usable production data. The dashboard is fail-closed and will not display empty metrics as if the system had no work."
        details={Object.entries(data.sourceErrors).filter(([, value]) => value).map(([key, value]) => `${key}: ${value}`).join(' | ')}
        action={(
          <button type="button" onClick={() => void dashboard.refresh()} className="rounded-xl border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-100">
            Retry Live Load
          </button>
        )}
      />
    );
  }

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-sm font-medium text-slate-500">Three-layer Operations Dashboard</div>
            <div className="mt-1 text-xl font-bold text-slate-950">Business Truth + Dispatch Truth + Runtime Diagnostics</div>
            <div className="mt-1 text-xs text-slate-500">
              Mode：{env.adminBackendMode}；Core：{env.coreApiBaseUrl}；Netty：{env.nettyApiBaseUrl}；WS：{env.nettyRuntimeWsUrl}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={`CORE_${coreStatus}`} />
            <StatusBadge status={`NETTY_${nettyStatus}`} />
            <DataSourceBadge source={dashboardSource} detail={hasSourceErrors ? 'One or more live APIs returned errors' : 'Core + Gateway runtime'} />
            <RefreshButton refreshing={dashboard.refreshing} lastUpdatedAt={dashboard.lastUpdatedAt} onRefresh={dashboard.refresh} />
          </div>
        </div>
      </div>

      <SourceErrorList errors={data.sourceErrors} />

      <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
        <div className="font-bold">Data semantics</div>
        <div className="mt-1">Business Truth and Dispatch Truth use Core persisted state as the authority. Runtime Diagnostics only describe Gateway session, delivery, and callback relay observations. Agent online does not mean approved or dispatch-ready, and a delivery event does not mean Core accepted the callback.</div>
      </div>

      <ControlPlaneSection data={data} />
      <DispatchTruthSection data={data} />
      <RuntimePlaneSection data={data} />
      <TrustPlaneSection data={data} />
      <RecoveryOperationsSection metrics={data.recoveryMetrics} runbook={data.recoveryRunbook} approvals={data.recoveryApprovals} error={data.sourceErrors.coreRecoveryMetrics} runbookError={data.sourceErrors.coreRecoveryRunbook} approvalError={data.sourceErrors.coreRecoveryApprovals} onRefresh={dashboard.refresh} />
      <RealtimeSummary />

      {mode === 'developer' ? (
        <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <summary className="cursor-pointer text-sm font-bold text-slate-900">Raw snapshot debug payload</summary>
          <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
            <div>
              <div className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">Core snapshot</div>
              <JsonViewer value={data.coreSnapshot} />
            </div>
            <div>
              <div className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">Gateway snapshot</div>
              <JsonViewer value={data.nettySnapshot} />
            </div>
          </div>
        </details>
      ) : null}
    </div>
  );
}
