'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { useI18n } from '@/hooks/useI18n';
import { useAuth } from '@/components/auth/AuthProvider';
import type { CoreAgentSetupRequest, CoreAgentSetupResponse } from '@/lib/types/core';

type AgentPurpose = 'ISSUE_TRACKING' | 'CALLBACK_HANDLER' | 'DATA_SYNC' | 'CUSTOM_TASK';
type RuntimeType = 'Docker' | 'Local Process' | 'Remote Host';

interface AgentSetupDraft {
  tenantId: string;
  agentId: string;
  agentName: string;
  ownerTeam: string;
  description: string;
  purpose: AgentPurpose;
  runtimeType: RuntimeType;
  gatewayUrl: string;
  credentialToken: string;
  autoApprove: boolean;
}

interface PurposeOption {
  value: AgentPurpose;
  label: string;
  description: string;
  capabilities: string[];
}

const purposeOptions: PurposeOption[] = [
  {
    value: 'ISSUE_TRACKING',
    label: 'Create or update issues',
    description: 'Use this for Redmine, GitLab, or other issue-tracking adapter agents.',
    capabilities: ['ISSUE_CREATE', 'ISSUE_UPDATE', 'CALLBACK_HANDLE'],
  },
  {
    value: 'CALLBACK_HANDLER',
    label: 'Handle callbacks',
    description: 'Use this for agents that receive completion callbacks or relay status updates.',
    capabilities: ['CALLBACK_HANDLE', 'STATUS_RELAY'],
  },
  {
    value: 'DATA_SYNC',
    label: 'Sync data',
    description: 'Use this for agents that synchronize external systems or move records between services.',
    capabilities: ['DATA_SYNC', 'STATUS_RELAY'],
  },
  {
    value: 'CUSTOM_TASK',
    label: 'Run custom tasks',
    description: 'Use this when the agent has a custom executor or a task type that is not listed yet.',
    capabilities: ['CUSTOM_EXECUTE'],
  },
];

const runtimeTypes: RuntimeType[] = ['Docker', 'Local Process', 'Remote Host'];

function createToken(): string {
  const random = Math.random().toString(36).slice(2, 12);
  return `dev-${Date.now().toString(36)}-${random}`;
}

function normalizeAgentId(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64);
}

function startCommand(draft: AgentSetupDraft): string {
  const image = 'opendispatch/agent-runtime:local';
  const agentId = draft.agentId || '<agent-id>';
  const gatewayUrl = draft.gatewayUrl || 'http://localhost:18081';
  const token = draft.credentialToken || '<agent-token>';
  if (draft.runtimeType === 'Docker') {
    return [
      'docker run --rm',
      `  -e AGENT_ID=${agentId}`,
      `  -e AGENT_TOKEN=${token}`,
      `  -e GATEWAY_URL=${gatewayUrl}`,
      `  ${image}`,
    ].join(' \\\n');
  }
  return [
    `export AGENT_ID=${agentId}`,
    `export AGENT_TOKEN=${token}`,
    `export GATEWAY_URL=${gatewayUrl}`,
    './bin/start-agent.sh',
  ].join('\n');
}

function setupChecklist(draft: AgentSetupDraft): Array<{ label: string; done: boolean; description: string }> {
  return [
    { label: 'Basic information', done: Boolean(draft.agentId && draft.agentName), description: 'Agent ID and display name are required.' },
    { label: 'Connection settings', done: Boolean(draft.gatewayUrl && draft.credentialToken), description: 'Gateway URL and token are required before runtime connection.' },
    { label: 'Optional capabilities', done: false, description: 'Capabilities are optional. Add them only when a Dispatch Flow explicitly requires specialized execution.' },
    { label: 'Dispatch Flow usage', done: false, description: 'Add this Agent to an active Dispatch Flow to make it a candidate.' },
    { label: 'Real dispatch test', done: false, description: 'Run a real test event from Dispatch Flow after the runtime connects successfully.' },
  ];
}

function buildSetupRequest(draft: AgentSetupDraft, purpose: PurposeOption): CoreAgentSetupRequest {
  return {
    tenantId: draft.tenantId,
    agentId: draft.agentId,
    agentName: draft.agentName,
    ownerTeam: draft.ownerTeam || undefined,
    description: draft.description || `${purpose.label} agent created from first-agent setup.`,
    purpose: draft.purpose,
    runtimeType: draft.runtimeType,
    gatewayUrl: draft.gatewayUrl,
    credentialToken: draft.credentialToken,
    autoApprove: draft.autoApprove,
    createDefaultCapabilities: false,
    createRuntimeBinding: true,
    createSupplyProfile: false,
    createDefaultDispatchRule: false,
    capacityLimit: 1,
    operatorId: 'admin-ui',
    defaultCapabilities: [],
    defaultTaskTypes: [],
    metadata: {
      source: 'ADMIN_UI_FIRST_AGENT_SETUP',
      setupMode: 'EMPTY_DATABASE_ONBOARDING',
    },
  };
}

export function AgentOnboardingPanel() {
  const { t } = useI18n();
  const { selectedTenantId } = useAuth();
  const [draft, setDraft] = useState<AgentSetupDraft>({
    tenantId: selectedTenantId,
    agentId: '',
    agentName: '',
    ownerTeam: '',
    description: '',
    purpose: 'CUSTOM_TASK',
    runtimeType: 'Docker',
    gatewayUrl: 'http://localhost:18081',
    credentialToken: createToken(),
    autoApprove: false,
  });
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createdAgentId, setCreatedAgentId] = useState<string | null>(null);
  const [setupResult, setSetupResult] = useState<CoreAgentSetupResponse | null>(null);

  useEffect(() => {
    if (selectedTenantId) setDraft((current) => ({ ...current, tenantId: selectedTenantId }));
  }, [selectedTenantId]);

  const purpose = useMemo(() => purposeOptions.find((option) => option.value === draft.purpose) ?? purposeOptions[0], [draft.purpose]);
  const checklist = useMemo(() => setupChecklist(draft), [draft]);
  const command = setupResult?.startCommand?.command ?? startCommand(draft);
  const startCommandDetails = setupResult?.startCommand;
  const commandVariants = [
    { label: 'Docker', value: startCommandDetails?.dockerCommand },
    { label: 'Local process', value: startCommandDetails?.localCommand },
    { label: 'Remote host', value: startCommandDetails?.remoteCommand },
    { label: 'Health check', value: startCommandDetails?.healthCheckCommand },
    { label: 'Verify authorization', value: startCommandDetails?.verifyConnectionCommand },
    { label: 'Logs', value: startCommandDetails?.logsCommand },
  ].filter((entry): entry is { label: string; value: string } => Boolean(entry.value));

  function setField<K extends keyof AgentSetupDraft>(key: K, value: AgentSetupDraft[K]) {
    setMessage(null);
    setError(null);
    setSetupResult(null);
    setDraft((current) => ({ ...current, [key]: value }));
  }

  async function submit() {
    if (!draft.agentId.trim() || !draft.agentName.trim()) {
      setError(t('agent.setup.validation.requiredName'));
      return;
    }
    if (draft.autoApprove && !draft.credentialToken.trim()) {
      setError(t('agent.setup.validation.tokenRequired'));
      return;
    }
    setSubmitting(true);
    setMessage(null);
    setError(null);
    try {
      const result = await coreAdminApi.setupAgent(buildSetupRequest(draft, purpose));
      setSetupResult(result);
      setMessage(result.setupStatus === 'READY' ? t('agent.setup.success.approved') : t('agent.setup.success.draft'));
      setCreatedAgentId(result.agentId || draft.agentId);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('agent.setup.error.failed'));
    } finally {
      setSubmitting(false);
    }
  }

  const inputClass = 'mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100';
  const labelClass = 'text-xs font-black uppercase tracking-wide text-slate-500';

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]">
      <section className="space-y-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div>
          <p className="text-xs font-black uppercase tracking-wide text-blue-600">{t('agent.setup.badge')}</p>
          <h2 className="mt-1 text-xl font-black text-slate-950">{t('agent.setup.title')}</h2>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            {t('agent.setup.description')}
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <label>
            <span className={labelClass}>{t('agent.setup.tenantId')}</span>
            <input className={`${inputClass} bg-slate-100`} value={selectedTenantId || draft.tenantId} readOnly aria-label="Workspace Tenant" />
          </label>
          <label>
            <span className={labelClass}>{t('agent.setup.agentId')}</span>
            <input className={inputClass} value={draft.agentId} onChange={(event) => setField('agentId', normalizeAgentId(event.target.value))} />
          </label>
          <label>
            <span className={labelClass}>{t('agent.setup.agentName')}</span>
            <input className={inputClass} value={draft.agentName} onChange={(event) => setField('agentName', event.target.value)} />
          </label>
          <label>
            <span className={labelClass}>{t('agent.setup.ownerTeam')}</span>
            <input className={inputClass} value={draft.ownerTeam} onChange={(event) => setField('ownerTeam', event.target.value)} />
          </label>
        </div>

        <label className="block">
          <span className={labelClass}>{t('agent.setup.descriptionLabel')}</span>
          <textarea className={`${inputClass} min-h-20`} value={draft.description} onChange={(event) => setField('description', event.target.value)} />
        </label>

        <div>
          <div className={labelClass}>{t('agent.setup.purposeQuestion')}</div>
          <div className="mt-2 grid gap-3 md:grid-cols-2">
            {purposeOptions.map((option) => {
              const selected = draft.purpose === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setField('purpose', option.value)}
                  className={`rounded-2xl border p-4 text-left shadow-sm ${selected ? 'border-blue-300 bg-blue-50 text-blue-950' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'}`}
                >
                  <div className="text-sm font-black">{option.label}</div>
                  <p className="mt-1 text-xs leading-5">{option.description}</p>
                </button>
              );
            })}
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <label>
            <span className={labelClass}>{t('agent.setup.runtimeType')}</span>
            <select className={inputClass} value={draft.runtimeType} onChange={(event) => setField('runtimeType', event.target.value as RuntimeType)}>
              {runtimeTypes.map((runtimeType) => <option key={runtimeType} value={runtimeType}>{runtimeType}</option>)}
            </select>
          </label>
          <label>
            <span className={labelClass}>{t('agent.setup.gatewayUrl')}</span>
            <input className={inputClass} value={draft.gatewayUrl} onChange={(event) => setField('gatewayUrl', event.target.value)} />
          </label>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-end">
            <label className="flex-1">
              <span className={labelClass}>{t('agent.setup.connectionToken')}</span>
              <input className={inputClass} value={draft.credentialToken} onChange={(event) => setField('credentialToken', event.target.value)} />
            </label>
            <button type="button" onClick={() => setField('credentialToken', createToken())} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm font-bold text-slate-700 hover:bg-slate-100">
              {t('agent.setup.generateToken')}
            </button>
          </div>
          <label className="mt-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
            <input type="checkbox" checked={draft.autoApprove} onChange={(event) => setField('autoApprove', event.target.checked)} />
            {t('agent.setup.autoApprove')}
          </label>
        </div>

        {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700">{error}</div> : null}
        {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-bold text-emerald-800">{message}</div> : null}
        {setupResult ? (
          <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">
            <div className="font-black">Backend setup contract completed: {setupResult.setupStatus || 'UNKNOWN'}</div>
            <div className="mt-2 grid gap-2">
              {(setupResult.readinessChecks ?? []).map((check) => (
                <div key={check.code || check.label} className="flex items-start justify-between gap-3 rounded-xl bg-white/70 px-3 py-2">
                  <div>
                    <div className="font-bold">{check.label}</div>
                    <div className="text-xs text-blue-800">{check.description}</div>
                  </div>
                  <span className={`rounded-full px-2 py-1 text-[11px] font-black ${check.ready ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                    {check.ready ? 'Ready' : 'Pending'}
                  </span>
                </div>
              ))}
            </div>
          </div>
        ) : null}

        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={submit} disabled={submitting} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50">
            {submitting ? t('agent.setup.creating') : draft.autoApprove ? t('agent.setup.createAndApprove') : t('agent.setup.createEnrollmentDraft')}
          </button>
          {createdAgentId ? (
            <Link href={`/agents/${encodeURIComponent(createdAgentId)}`} className="rounded-xl border border-blue-200 px-4 py-2 text-sm font-bold text-blue-700 hover:bg-blue-50">
              {t('agent.setup.openAgentDetail')}
            </Link>
          ) : null}
          <Link href="/agent-enrollments" className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">
            {t('agent.setup.openEnrollmentReview')}
          </Link>
        </div>
      </section>

      <aside className="space-y-4">
        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-sm font-black text-slate-950">{t('agent.setup.checklist')}</h3>
          <div className="mt-4 space-y-3">
            {checklist.map((item) => (
              <div key={item.label} className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="text-sm font-black text-slate-900">{item.label}</div>
                  <span className={`rounded-full px-2 py-1 text-[11px] font-black ${item.done ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600'}`}>{item.done ? t('agent.setup.ready') : t('agent.setup.pending')}</span>
                </div>
                <p className="mt-1 text-xs leading-5 text-slate-600">{item.description}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-sm font-black text-slate-950">{t('agent.setup.defaultServiceSetup')}</h3>
          <p className="mt-2 text-xs leading-5 text-slate-600">Capabilities are optional specialized abilities. General dispatch does not require a capability; Dispatch Flow selects the Agent, and a required capability is checked only when the Flow declares one.</p>
          <div className="mt-3 text-xs font-black uppercase tracking-wide text-slate-500">{t('agent.setup.capabilities')}</div>
          <div className="mt-2 flex flex-wrap gap-2">
            {purpose.capabilities.map((capability) => <span key={capability} className="rounded-full bg-blue-50 px-2 py-1 text-xs font-bold text-blue-700">{capability}</span>)}
          </div>
          <div className="mt-4 grid gap-2 text-xs">
            <Link href="/dispatch-flows" className="font-bold text-blue-700 hover:underline">Open Dispatch Flows to use this Agent</Link>
          </div>
        </section>

        <section className="rounded-3xl border border-slate-200 bg-slate-950 p-5 text-slate-100 shadow-sm">
          <h3 className="text-sm font-black">{t('agent.setup.startCommand')}</h3>
          <p className="mt-1 text-xs leading-5 text-slate-300">Use the command that matches the runtime host. The backend contract now returns Docker, local, remote, health-check, authorization, and log commands.</p>
          <pre className="mt-3 overflow-auto whitespace-pre-wrap rounded-2xl bg-black/30 p-3 text-xs leading-5 text-slate-100">{command}</pre>
          {commandVariants.length > 0 ? (
            <div className="mt-4 space-y-3">
              {commandVariants.map((entry) => (
                <details key={entry.label} className="rounded-2xl border border-slate-700 bg-slate-900/70 p-3">
                  <summary className="cursor-pointer text-xs font-black text-slate-200">{entry.label}</summary>
                  <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-xl bg-black/30 p-3 text-[11px] leading-5 text-slate-100">{entry.value}</pre>
                </details>
              ))}
            </div>
          ) : null}
          {startCommandDetails?.startupSteps?.length ? (
            <div className="mt-4 rounded-2xl border border-blue-700 bg-blue-950/40 p-3">
              <div className="text-xs font-black text-blue-100">Startup checklist</div>
              <ol className="mt-2 list-decimal space-y-1 pl-5 text-xs leading-5 text-blue-100">
                {startCommandDetails.startupSteps.map((step) => <li key={step}>{step}</li>)}
              </ol>
            </div>
          ) : null}
          {startCommandDetails?.troubleshooting?.length ? (
            <div className="mt-4 rounded-2xl border border-amber-700 bg-amber-950/30 p-3">
              <div className="text-xs font-black text-amber-100">Connection troubleshooting</div>
              <div className="mt-2 space-y-2">
                {startCommandDetails.troubleshooting.map((step) => (
                  <div key={step.code || step.label} className="rounded-xl bg-black/20 p-2 text-xs leading-5 text-amber-50">
                    <div className="font-black">{step.label || step.code}</div>
                    <div>{step.description}</div>
                    {step.command ? <pre className="mt-2 overflow-auto rounded-lg bg-black/30 p-2 text-[11px] text-slate-100">{step.command}</pre> : null}
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </section>
      </aside>
    </div>
  );
}
