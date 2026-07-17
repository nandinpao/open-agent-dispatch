'use client';

import { AgentCredentialIssueDialog } from '@/components/agents/AgentCredentialIssueDialog';
import { EmptyState } from '@/components/common/EmptyState';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { AgentSecurityEvent, CoreAgentProfile } from '@/lib/types/core';
import type { AgentRuntimeSummary } from '@/lib/types/dashboard';
import type { NettyAgentRuntime } from '@/lib/types/nettyRuntime';
import { formatDateTime } from '@/lib/utils/format';
import { duplicateRuntimeEventMode, latestDuplicateRuntimeSecurityEvent } from '@/lib/agents/duplicateRuntimeSecurityEvents';

interface AgentDuplicateRuntimeSecurityPanelProps {
  profile?: CoreAgentProfile;
  runtimes?: NettyAgentRuntime[];
  runtimeSummary?: AgentRuntimeSummary;
  securityEvents?: AgentSecurityEvent[];
  commandRunning: string | null;
  onEnforce: (revokeCredentials?: boolean) => void;
  onResolve: () => void;
  onChanged?: () => Promise<void> | void;
}

function nodeIdOf(runtime: NettyAgentRuntime): string {
  return runtime.gatewayNodeId ?? runtime.nodeId ?? 'unknown';
}

function sessionIdOf(runtime: NettyAgentRuntime): string {
  return runtime.sessionId ?? runtime.connectionId ?? '-';
}

export function AgentDuplicateRuntimeSecurityPanel({
  profile,
  runtimes,
  runtimeSummary,
  securityEvents,
  commandRunning,
  onEnforce,
  onResolve,
  onChanged
}: Readonly<AgentDuplicateRuntimeSecurityPanelProps>) {
  const safeRuntimes = Array.isArray(runtimes) ? runtimes : [];
  const connected = safeRuntimes.filter((runtime) => runtime.connected);
  const duplicateDetected = Boolean(runtimeSummary?.duplicateRuntimeDetected || connected.length > 1 || new Set(connected.map(nodeIdOf)).size > 1);
  const quarantined = profile?.riskStatus === 'QUARANTINED' || profile?.riskStatus === 'COMPROMISED';
  const credentialActive = profile?.credential?.credentialStatus === 'ACTIVE';
  const credentialIssuedAt = Date.parse(profile?.credential?.issuedAt ?? '');
  const quarantineUpdatedAt = Date.parse(profile?.updatedAt ?? '');
  const rotatedAfterQuarantine = Number.isFinite(credentialIssuedAt) && Number.isFinite(quarantineUpdatedAt) && credentialIssuedAt > quarantineUpdatedAt;
  const busy = commandRunning !== null;
  const latestAutoEvent = latestDuplicateRuntimeSecurityEvent(securityEvents);
  const latestAutoEventMode = duplicateRuntimeEventMode(latestAutoEvent);

  return (
    <section className="rounded-2xl border border-rose-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Duplicate Runtime Security Enforcement</h2>
          <p className="mt-1 text-sm text-slate-500">
            同一 Agent ID 出現在多個 runtime sessions 時，應視為 credential reuse / split-brain / 舊 session 未清除的安全事件。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={duplicateDetected ? 'DUPLICATE_RUNTIME' : 'NO_DUPLICATE'} />
          <StatusBadge status={quarantined ? 'QUARANTINED' : profile?.riskStatus ?? 'UNKNOWN'} />
          <StatusBadge status={credentialActive ? 'CREDENTIAL_ACTIVE' : profile?.credential?.credentialStatus ?? 'CREDENTIAL_MISSING'} />
        </div>
      </div>

      {!profile ? (
        <EmptyState title="Core profile missing" description="尚未有 Core Agent profile，無法執行 quarantine 或 credential rotation workflow。" />
      ) : (
        <div className="space-y-4">
          {latestAutoEvent ? (
            <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
              <div className="font-bold">Netty auto-detection event: {latestAutoEvent.eventType}</div>
              <div className="mt-1">
                Mode: {latestAutoEventMode}. Gateway: {latestAutoEvent.gatewayNodeId ?? '-'}。Occurred: {formatDateTime(latestAutoEvent.occurredAt)}。
              </div>
              {latestAutoEvent.reason ? <div className="mt-1 text-blue-800">Reason: {latestAutoEvent.reason}</div> : null}
            </div>
          ) : null}

          {duplicateDetected ? (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
              <div className="font-bold">Duplicate runtime detected: {connected.length} connected sessions</div>
              <div className="mt-1">
                Nodes: {Array.from(new Set(connected.map(nodeIdOf))).join(', ') || '-'}。建議執行安全處置：quarantine、disconnect all、必要時 revoke credentials，然後發行新 credential。
              </div>
            </div>
          ) : (
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
              目前沒有偵測到 duplicate connected sessions。若先前已 quarantine，請確認 credential rotation 已部署後再 resolve。
            </div>
          )}

          <div className="grid gap-3 md:grid-cols-4">
            <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Connected Sessions</div>
              <div className="mt-1 text-lg font-bold text-slate-900">{connected.length}</div>
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Gateway Nodes</div>
              <div className="mt-1 break-all text-sm font-bold text-slate-900">{Array.from(new Set(connected.map(nodeIdOf))).join(', ') || '-'}</div>
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Risk Status</div>
              <div className="mt-1"><StatusBadge status={profile.riskStatus ?? 'UNKNOWN'} /></div>
            </div>
            <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Credential</div>
              <div className="mt-1"><StatusBadge status={profile.credential?.credentialStatus ?? 'MISSING'} /></div>
            </div>
          </div>

          {safeRuntimes.length > 0 ? (
            <div className="overflow-hidden rounded-2xl border border-slate-200">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-3">State</th>
                    <th className="px-4 py-3">Gateway Node</th>
                    <th className="px-4 py-3">Session</th>
                    <th className="px-4 py-3">Heartbeat</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {safeRuntimes.map((runtime, index) => (
                    <tr key={`${nodeIdOf(runtime)}-${sessionIdOf(runtime)}-${index}`}>
                      <td className="px-4 py-3"><StatusBadge status={runtime.connected ? 'CONNECTED' : 'OFFLINE'} /></td>
                      <td className="px-4 py-3 font-semibold text-slate-800">{nodeIdOf(runtime)}</td>
                      <td className="px-4 py-3 text-slate-600">{sessionIdOf(runtime)}</td>
                      <td className="px-4 py-3 text-slate-600">{runtime.lastHeartbeatAt ? formatDateTime(runtime.lastHeartbeatAt) : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            <div className="font-bold">Recommended workflow</div>
            <ol className="mt-2 list-decimal space-y-1 pl-5">
              <li>Enforce Security：Core 將 Agent 風險標記為 QUARANTINED、enabled=false，並執行 disconnect-all。</li>
              <li>Rotate Credential：在下方發行新 token，部署到唯一合法的 OpenClaw runtime。</li>
              <li>Resolve：確認新 credential 的 issuedAt 晚於 quarantine timestamp，且舊 runtime 不再連線後，將風險恢復 NORMAL 並重新 enabled。</li>
            </ol>
          </div>

          <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap">
            <button
              type="button"
              disabled={busy || !duplicateDetected}
              onClick={() => onEnforce(false)}
              className="rounded-xl border border-rose-300 bg-rose-50 px-4 py-2 text-sm font-bold text-rose-800 hover:bg-rose-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-white disabled:text-slate-400"
            >
              {commandRunning === 'ENFORCE_DUPLICATE_SECURITY' ? 'Enforcing...' : 'Quarantine + Disconnect All'}
            </button>
            <button
              type="button"
              disabled={busy || !duplicateDetected}
              onClick={() => onEnforce(true)}
              className="rounded-xl bg-rose-700 px-4 py-2 text-sm font-bold text-white hover:bg-rose-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {commandRunning === 'ENFORCE_DUPLICATE_SECURITY' ? 'Enforcing...' : 'Quarantine + Revoke Credentials'}
            </button>
            <AgentCredentialIssueDialog profile={profile} triggerLabel="Rotate Credential" onSaved={onChanged} />
            <button
              type="button"
              disabled={busy || !quarantined || !credentialActive || !rotatedAfterQuarantine}
              onClick={onResolve}
              className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {commandRunning === 'RESOLVE_DUPLICATE_SECURITY' ? 'Resolving...' : 'Resolve After Rotation'}
            </button>
            {quarantined && credentialActive && !rotatedAfterQuarantine ? (
              <div className="basis-full rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                Resolve 被鎖定：Core 要求 active credential 必須是在 quarantine 之後重新發行，避免使用舊 token 直接解除隔離。
              </div>
            ) : null}
          </div>
        </div>
      )}
    </section>
  );
}
