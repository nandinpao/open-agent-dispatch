'use client';

import { useEffect, useMemo, useState } from 'react';
import { StatusBadge } from '@/components/common/StatusBadge';
import type { CoreAgentSecurityEnforcementPolicy, CoreAgentSecurityEnforcementPolicyUpdateRequest, CoreSecurityEnforcementMode } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

interface AgentSecurityEnforcementPolicyPanelProps {
  policy?: CoreAgentSecurityEnforcementPolicy;
  commandRunning: string | null;
  onSave: (request: CoreAgentSecurityEnforcementPolicyUpdateRequest) => Promise<unknown> | void;
}

const modes: CoreSecurityEnforcementMode[] = [
  'ALERT_ONLY',
  'QUARANTINE',
  'QUARANTINE_AND_DISCONNECT',
  'QUARANTINE_REVOKE_AND_DISCONNECT'
];

function splitCsv(value: string): string[] {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function joinCsv(values?: string[]): string {
  return Array.isArray(values) ? values.join(',') : '';
}

function modeDescription(mode?: string): string {
  switch (mode) {
    case 'ALERT_ONLY':
      return '只記錄 security event 與通知，不自動變更 Agent 狀態。適合開發或低風險 Agent。';
    case 'QUARANTINE':
      return '自動 riskStatus=QUARANTINED、enabled=false，要求 credential rotation，但不主動中斷 runtime sessions。';
    case 'QUARANTINE_AND_DISCONNECT':
      return '自動 quarantine 並對 observed gateway nodes 執行 disconnect-all。';
    case 'QUARANTINE_REVOKE_AND_DISCONNECT':
      return '最嚴格模式：quarantine、revoke active credentials、disconnect-all。適合高權限 Agent。';
    default:
      return '尚未設定模式，Core 會使用 default policy 或 ALERT_ONLY。';
  }
}

export function AgentSecurityEnforcementPolicyPanel({ policy, commandRunning, onSave }: Readonly<AgentSecurityEnforcementPolicyPanelProps>) {
  const [enabled, setEnabled] = useState(policy?.enabled ?? true);
  const [mode, setMode] = useState<CoreSecurityEnforcementMode>(policy?.duplicateRuntimeMode ?? 'ALERT_ONLY');
  const [requireRotation, setRequireRotation] = useState(policy?.requireCredentialRotation ?? true);
  const [notifyEmail, setNotifyEmail] = useState(policy?.notifyEmail ?? false);
  const [notifySlack, setNotifySlack] = useState(policy?.notifySlack ?? false);
  const [notifySiem, setNotifySiem] = useState(policy?.notifySiem ?? false);
  const [emailRecipients, setEmailRecipients] = useState(joinCsv(policy?.emailRecipients));
  const [slackChannels, setSlackChannels] = useState(joinCsv(policy?.slackChannels));
  const [siemTopics, setSiemTopics] = useState(joinCsv(policy?.siemTopics));

  useEffect(() => {
    setEnabled(policy?.enabled ?? true);
    setMode(policy?.duplicateRuntimeMode ?? 'ALERT_ONLY');
    setRequireRotation(policy?.requireCredentialRotation ?? true);
    setNotifyEmail(policy?.notifyEmail ?? false);
    setNotifySlack(policy?.notifySlack ?? false);
    setNotifySiem(policy?.notifySiem ?? false);
    setEmailRecipients(joinCsv(policy?.emailRecipients));
    setSlackChannels(joinCsv(policy?.slackChannels));
    setSiemTopics(joinCsv(policy?.siemTopics));
  }, [policy]);

  const saveRequest = useMemo<CoreAgentSecurityEnforcementPolicyUpdateRequest>(() => ({
    enabled,
    duplicateRuntimeMode: mode,
    requireCredentialRotation: requireRotation,
    notifyEmail,
    notifySlack,
    notifySiem,
    emailRecipients: splitCsv(emailRecipients),
    slackChannels: splitCsv(slackChannels),
    siemTopics: splitCsv(siemTopics),
    operatorId: 'admin-ui'
  }), [enabled, mode, requireRotation, notifyEmail, notifySlack, notifySiem, emailRecipients, slackChannels, siemTopics]);

  const busy = commandRunning === 'UPDATE_SECURITY_POLICY';

  return (
    <section className="rounded-2xl border border-indigo-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Per-Agent Security Enforcement Policy</h2>
          <p className="mt-1 text-sm text-slate-500">
            針對 duplicate runtime 自動偵測事件，設定此 Agent 的處置策略與通知 hook。Core 會優先使用本 Agent policy；沒有設定時才使用 default policy。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={enabled ? 'POLICY_ENABLED' : 'POLICY_DISABLED'} />
          <StatusBadge status={mode} />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <label className="rounded-xl border border-slate-200 p-3 text-sm">
          <span className="block text-xs font-semibold uppercase tracking-wide text-slate-400">Duplicate Runtime Mode</span>
          <select value={mode} onChange={(event) => setMode(event.target.value)} className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2">
            {modes.map((candidate) => <option key={candidate} value={candidate}>{candidate}</option>)}
          </select>
          <span className="mt-2 block text-xs text-slate-500">{modeDescription(mode)}</span>
        </label>

        <div className="rounded-xl border border-slate-200 p-3 text-sm">
          <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Policy Metadata</div>
          <div className="mt-2 space-y-1 text-slate-700">
            <div>Policy ID: <span className="font-mono text-xs">{policy?.policyId ?? '-'}</span></div>
            <div>Agent: <span className="font-mono text-xs">{policy?.agentId ?? '-'}</span></div>
            <div>Updated: {policy?.updatedAt ? formatDateTime(policy.updatedAt) : '-'}</div>
          </div>
        </div>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-2 lg:grid-cols-4">
        <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-sm font-semibold text-slate-700">
          <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /> Enabled
        </label>
        <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-sm font-semibold text-slate-700">
          <input type="checkbox" checked={requireRotation} onChange={(event) => setRequireRotation(event.target.checked)} /> Require Rotation
        </label>
        <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-sm font-semibold text-slate-700">
          <input type="checkbox" checked={notifyEmail} onChange={(event) => setNotifyEmail(event.target.checked)} /> Email Hook
        </label>
        <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-sm font-semibold text-slate-700">
          <input type="checkbox" checked={notifySlack} onChange={(event) => setNotifySlack(event.target.checked)} /> Slack Hook
        </label>
        <label className="flex items-center gap-2 rounded-xl border border-slate-200 p-3 text-sm font-semibold text-slate-700">
          <input type="checkbox" checked={notifySiem} onChange={(event) => setNotifySiem(event.target.checked)} /> SIEM Hook
        </label>
      </div>

      <div className="mt-4 grid gap-3 lg:grid-cols-3">
        <label className="text-sm">
          <span className="font-semibold text-slate-600">Email Recipients CSV</span>
          <input value={emailRecipients} onChange={(event) => setEmailRecipients(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="secops@example.com,audit@example.com" />
        </label>
        <label className="text-sm">
          <span className="font-semibold text-slate-600">Slack Channels CSV</span>
          <input value={slackChannels} onChange={(event) => setSlackChannels(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="#secops,#agent-alerts" />
        </label>
        <label className="text-sm">
          <span className="font-semibold text-slate-600">SIEM Topics CSV</span>
          <input value={siemTopics} onChange={(event) => setSiemTopics(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="opensocket.agent.security" />
        </label>
      </div>

      <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        P8 目前提供 notification / audit hook：Core 會寫入 SECURITY_NOTIFICATION_QUEUED security event。實際 Email / Slack / SIEM publisher 可在後續 P9 接 outbox worker 或企業既有通知服務。
      </div>

      <div className="mt-4 flex justify-end">
        <button
          type="button"
          disabled={busy}
          onClick={() => void onSave(saveRequest)}
          className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-300"
        >
          {busy ? 'Saving...' : 'Save Security Policy'}
        </button>
      </div>
    </section>
  );
}
