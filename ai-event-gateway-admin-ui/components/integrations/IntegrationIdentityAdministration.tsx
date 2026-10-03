'use client';

import { Button } from '@/components/ui/Button';
import Link from 'next/link';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ConfigurationFeedback,
  ConfigurationImpactPreview,
  ConfigurationJourney,
  ConfigurationLifecycleBoundaryNotice,
  ConfigurationPurposePanel,
  ConfigurationValidationPanel,
  type ConfigurationRisk,
} from '@/components/configuration/ConfigurationConfidence';
import { ProjectMappingGovernancePanel } from '@/components/integrations/ProjectMappingGovernancePanel';
import { IntegrationConnectionNavigator, IntegrationWorkspaceTabs, type IntegrationWorkspaceTab } from '@/components/integrations/IntegrationWorkspaceNavigation';
import { CredentialMetadataImpactPanel } from '@/components/phase7d/CredentialMetadataImpactPanel';
import { IssueConnectionOperationalSummary } from '@/components/phase7d/IssueConnectionOperationalSummary';
import { ResourceGovernancePanel } from '@/components/resource-access/ResourceGovernancePanel';
import {
  addCredential,
  listConnections,
  listCredentials,
  listPrincipals,
  listProjectMappings,
  runPermissionProbe,
  saveConnection,
  savePrincipal,
  type IntegrationConnection,
  type IntegrationCredentialMetadata,
  type IntegrationPrincipal,
  type IntegrationProjectMapping,
  type PermissionProbeResult,
  type ProviderType,
} from '@/lib/api/domains/integrationIdentityApi';

const PROVIDERS: Array<{ value: ProviderType; label: string; defaultDeployment: string; creatable: boolean }> = [
  { value: 'REDMINE', label: 'Redmine', defaultDeployment: 'SELF_HOSTED', creatable: true },
  { value: 'JIRA', label: 'Jira', defaultDeployment: 'CLOUD_OR_SELF_HOSTED', creatable: true },
  { value: 'GITLAB_ISSUES', label: 'GitLab Issues', defaultDeployment: 'CLOUD_OR_SELF_HOSTED', creatable: false },
];

const ACCEPTED_PROBE_VALUES = new Set(['GRANTED', 'PASS', 'PASSED', 'SUPPORTED', 'SUCCESS', 'READY']);

const providerLabel = (provider?: ProviderType | null) => PROVIDERS.find((item) => item.value === provider)?.label ?? provider ?? 'Issue Tracking';
const providerPrefix = (provider: ProviderType) => provider.toLowerCase().replace(/_issues$/, '').replace(/_/g, '-');
const blankConnection = (providerType: ProviderType = 'REDMINE'): IntegrationConnection => ({
  connectionId: '',
  providerType,
  connectionName: '',
  baseUrl: '',
  deploymentType: PROVIDERS.find((item) => item.value === providerType)?.defaultDeployment ?? 'SELF_HOSTED',
  status: 'DRAFT',
  timeoutMs: 10000,
  enabled: false,
});
const blankServiceAccount: IntegrationPrincipal = {
  principalId: '',
  connectionId: '',
  principalName: '',
  principalType: 'SERVICE_ACCOUNT',
  status: 'DRAFT',
  riskLevel: 'UNKNOWN',
};
const blankCredential = (principalId = ''): IntegrationCredentialMetadata => ({
  credentialId: '',
  principalId,
  authType: 'API_TOKEN',
  secretRef: '',
  secretVersion: '',
  status: 'PENDING_VALIDATION',
});

type FeedbackState = {
  tone: 'success' | 'error' | 'info';
  title: string;
  message: string;
  safetyNote?: string;
};

function maskReference(reference?: string | null) {
  if (!reference) return '—';
  const index = reference.lastIndexOf('/');
  return index < 0 ? '••••' : `${reference.slice(0, index + 1)}••••${reference.includes('#') ? `#${reference.split('#').at(-1)}` : ''}`;
}
function errorText(error: unknown) { return error instanceof Error ? error.message : 'The operation failed.'; }
function generatedId(prefix: string, value: string) { const code=value.trim().toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'').slice(0,64); return `${prefix}-${code || 'default'}`; }
function mappingMatches(mapping: IntegrationProjectMapping, sourceSystemId?: string, taskType?: string) {
  if (!sourceSystemId) return false;
  if (String(mapping.sourceSystemId ?? '').toUpperCase() !== sourceSystemId.toUpperCase()) return false;
  if (taskType) return String(mapping.taskType ?? '').toUpperCase() === taskType.toUpperCase();
  return true;
}
function connectionChanged(current: IntegrationConnection | undefined, draft: IntegrationConnection) {
  if (!current) return Boolean(draft.connectionName.trim() || draft.baseUrl.trim());
  return current.connectionName !== draft.connectionName
    || current.baseUrl !== draft.baseUrl
    || Boolean(current.enabled) !== Boolean(draft.enabled)
    || current.deploymentType !== draft.deploymentType
    || Number(current.timeoutMs ?? 10000) !== Number(draft.timeoutMs ?? 10000);
}
function runtimeConnectionChanged(current: IntegrationConnection | undefined, draft: IntegrationConnection) {
  if (!current) return false;
  return current.baseUrl !== draft.baseUrl
    || Boolean(current.enabled) !== Boolean(draft.enabled)
    || Number(current.timeoutMs ?? 10000) !== Number(draft.timeoutMs ?? 10000);
}
function principalChanged(current: IntegrationPrincipal | undefined, draft: IntegrationPrincipal) {
  if (!current) return Boolean(draft.principalName.trim() || String(draft.externalPrincipalIdentifier ?? '').trim());
  return current.principalName !== draft.principalName
    || String(current.externalPrincipalIdentifier ?? '') !== String(draft.externalPrincipalIdentifier ?? '');
}
function uniqueSourceCount(mappings: IntegrationProjectMapping[]) {
  return new Set(mappings.map((value) => value.sourceSystemId).filter(Boolean)).size;
}

export function IntegrationIdentityAdministration({
  initialSourceSystemId,
  initialTaskType,
  initialMappingId,
}: Readonly<{
  initialSourceSystemId?: string;
  initialTaskType?: string;
  initialMappingId?: string;
}>) {
  const { mode, setMode } = useAdminUiMode();
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [projectMappings, setProjectMappings] = useState<IntegrationProjectMapping[]>([]);
  const [selectedConnectionId, setSelectedConnectionId] = useState('');
  const [connectionDraft, setConnectionDraft] = useState<IntegrationConnection>(blankConnection());
  const [serviceAccounts, setServiceAccounts] = useState<IntegrationPrincipal[]>([]);
  const [selectedServiceAccountId, setSelectedServiceAccountId] = useState('');
  const [serviceAccountDraft, setServiceAccountDraft] = useState<IntegrationPrincipal>(blankServiceAccount);
  const [credentials, setCredentials] = useState<IntegrationCredentialMetadata[]>([]);
  const [credentialDraft, setCredentialDraft] = useState<IntegrationCredentialMetadata>(blankCredential());
  const [probe, setProbe] = useState<PermissionProbeResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [validating, setValidating] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [workspaceTab, setWorkspaceTab] = useState<IntegrationWorkspaceTab>(initialMappingId || initialSourceSystemId ? 'mappings' : 'overview');

  const selectedConnection = connections.find((value) => value.connectionId === selectedConnectionId);
  const selectedServiceAccount = serviceAccounts.find((value) => value.principalId === selectedServiceAccountId);
  const selectedProviderLabel = providerLabel(selectedConnection?.providerType ?? connectionDraft.providerType);
  const connectionMappings = useMemo(
    () => projectMappings.filter((value) => value.connectionId === selectedConnectionId),
    [projectMappings, selectedConnectionId],
  );
  const activeConnectionMappings = useMemo(
    () => connectionMappings.filter((value) => value.lifecycleStatus === 'ACTIVE'),
    [connectionMappings],
  );
  const hasConnectionChanges = connectionChanged(selectedConnection, connectionDraft);
  const hasRuntimeConnectionChanges = runtimeConnectionChanged(selectedConnection, connectionDraft);
  const hasPrincipalChanges = principalChanged(selectedServiceAccount, serviceAccountDraft);
  const probeEntries = useMemo(() => Object.entries(probe?.capabilityResults ?? {}), [probe]);
  const probeFailures = useMemo(
    () => probeEntries.filter(([, value]) => !ACCEPTED_PROBE_VALUES.has(String(value).toUpperCase())),
    [probeEntries],
  );
  const probePassed = Boolean(probe) && (
    (probeEntries.length > 0 && probeFailures.length === 0)
    || ACCEPTED_PROBE_VALUES.has(String(probe?.overallStatus ?? '').toUpperCase())
  );

  const refreshConnections = useCallback(async (preferred?: string) => {
    const [values, mappings] = await Promise.all([listConnections(), listProjectMappings()]);
    setConnections(values);
    setProjectMappings(mappings);
    const matchingMapping = initialMappingId
      ? mappings.find((mapping) => mapping.mappingId === initialMappingId)
      : mappings.filter((mapping) => mappingMatches(mapping, initialSourceSystemId, initialTaskType))
          .sort((a, b) => Number(b.lifecycleStatus === 'ACTIVE') - Number(a.lifecycleStatus === 'ACTIVE'))[0];
    setSelectedConnectionId(preferred ?? matchingMapping?.connectionId ?? values[0]?.connectionId ?? '');
  }, [initialSourceSystemId, initialTaskType, initialMappingId]);

  const refreshServiceAccounts = useCallback(async (connectionId: string, preferred?: string) => {
    if (!connectionId) { setServiceAccounts([]); setSelectedServiceAccountId(''); return; }
    const values = (await listPrincipals(connectionId)).filter((value) => ['SERVICE_ACCOUNT', 'TECHNICAL_USER'].includes(value.principalType));
    setServiceAccounts(values);
    setSelectedServiceAccountId(preferred ?? values[0]?.principalId ?? '');
  }, []);

  const refreshCredentials = useCallback(async (principalId: string) => {
    if (!principalId) { setCredentials([]); return; }
    setCredentials(await listCredentials(principalId));
    setCredentialDraft(blankCredential(principalId));
  }, []);

  useEffect(() => {
    setBusy(true);
    refreshConnections()
      .catch((error) => setFeedback({ tone: 'error', title: 'Configuration state could not be loaded', message: errorText(error), safetyNote: 'No configuration change was attempted.' }))
      .finally(() => setBusy(false));
  }, [refreshConnections]);

  useEffect(() => {
    setProbe(null);
    if (!selectedConnectionId) { setConnectionDraft(blankConnection()); setServiceAccounts([]); return; }
    setConnectionDraft(selectedConnection ?? { ...blankConnection(), connectionId: selectedConnectionId });
    setBusy(true);
    refreshServiceAccounts(selectedConnectionId)
      .catch((error) => setFeedback({ tone: 'error', title: 'Technical identity could not be loaded', message: errorText(error), safetyNote: 'No configuration change was attempted.' }))
      .finally(() => setBusy(false));
  }, [selectedConnectionId, selectedConnection, refreshServiceAccounts]);

  useEffect(() => {
    setProbe(null);
    if (!selectedServiceAccountId) { setServiceAccountDraft({ ...blankServiceAccount, connectionId: selectedConnectionId }); setCredentials([]); return; }
    setServiceAccountDraft(selectedServiceAccount ?? { ...blankServiceAccount, connectionId: selectedConnectionId, principalId: selectedServiceAccountId });
    setBusy(true);
    refreshCredentials(selectedServiceAccountId)
      .catch((error) => setFeedback({ tone: 'error', title: 'Credential metadata could not be loaded', message: errorText(error), safetyNote: 'No configuration change was attempted.' }))
      .finally(() => setBusy(false));
  }, [selectedServiceAccountId, selectedServiceAccount, selectedConnectionId, refreshCredentials]);

  async function perform(action: () => Promise<void>, success: string, failureRefresh?: () => Promise<void>) {
    setBusy(true);
    setFeedback(null);
    try {
      await action();
      setFeedback({ tone: 'success', title: 'Change saved', message: success, safetyNote: 'Review the current state and run validation before relying on the new configuration for production work.' });
    } catch (error) {
      try { await failureRefresh?.(); } catch { /* best-effort refresh only */ }
      setFeedback({
        tone: 'error',
        title: 'Change not confirmed',
        message: errorText(error),
        safetyNote: 'OpenDispatch did not treat this request as successfully completed. Current server state was reloaded where possible; review it before retrying.',
      });
    } finally {
      setBusy(false);
    }
  }

  async function runSafeConnectionCheck() {
    if (!selectedServiceAccountId) {
      setFeedback({ tone: 'info', title: 'Technical identity required', message: 'Select or create a Service Account before running a provider permission check.', safetyNote: 'No configuration change was made.' });
      return;
    }
    setBusy(true);
    setValidating(true);
    setFeedback(null);
    setProbe(null);
    try {
      const result = await runPermissionProbe(selectedServiceAccountId);
      setProbe(result);
      const entries = Object.entries(result.capabilityResults ?? {});
      const failures = entries.filter(([, value]) => !ACCEPTED_PROBE_VALUES.has(String(value).toUpperCase()));
      const passed = (entries.length > 0 && failures.length === 0) || ACCEPTED_PROBE_VALUES.has(String(result.overallStatus ?? '').toUpperCase());
      setFeedback({
        tone: passed ? 'success' : 'info',
        title: passed ? 'Validation passed' : 'Validation found items to review',
        message: result.providerResponseSummary || (passed ? 'The provider permission check passed.' : 'One or more provider capabilities were not granted.'),
        safetyNote: 'This validation records probe evidence only. It does not publish a Source/Task mapping or change task routing.',
      });
    } catch (error) {
      setFeedback({ tone: 'error', title: 'Validation could not complete', message: errorText(error), safetyNote: 'The validation attempt did not publish a mapping or change task routing.' });
    } finally {
      setValidating(false);
      setBusy(false);
    }
  }

  const connectionRisk: ConfigurationRisk = selectedConnection && activeConnectionMappings.length > 0 && hasRuntimeConnectionChanges
    ? 'HIGH'
    : selectedConnection && connectionMappings.length > 0 && hasConnectionChanges
      ? 'MEDIUM'
      : 'LOW';

  const journeySteps = [
    { title: 'Choose connection', detail: 'Create or select the Redmine/Jira endpoint.', state: selectedConnection ? 'complete' as const : 'current' as const },
    { title: 'Set technical identity', detail: 'Choose the external Service Account used by the connector.', state: selectedServiceAccount ? 'complete' as const : selectedConnection ? 'current' as const : 'upcoming' as const },
    { title: 'Validate access', detail: 'Probe authentication and provider permissions without publishing routing.', state: probePassed ? 'complete' as const : selectedServiceAccount ? 'current' as const : 'upcoming' as const },
    { title: 'Map Source / Task', detail: 'Save a draft that selects project and issue type.', state: activeConnectionMappings.length > 0 ? 'complete' as const : probePassed ? 'current' as const : 'upcoming' as const },
    { title: 'Publish & observe', detail: 'Publish only after validation, then monitor connector operations.', state: activeConnectionMappings.length > 0 ? 'current' as const : 'upcoming' as const },
  ];

  return <div className="space-y-5">
    <ConfigurationPurposePanel
      title="Connect issue tracking safely"
      purpose="Connect Redmine or Jira so OpenDispatch can create and synchronize external issues for selected Source Systems and Task Types. Choose one provider connection, complete its authentication, then publish governed Source/Task mappings only after validation."
      currentState={<><b>{connections.length} connection{connections.length === 1 ? '' : 's'}</b> configured. {selectedConnection ? <>{selectedProviderLabel} “{selectedConnection.connectionName}” is selected and has <b>{activeConnectionMappings.length} active mapping{activeConnectionMappings.length === 1 ? '' : 's'}</b>.</> : <>Choose an existing connection or add a new Redmine/Jira connection.</>}</>}
      impact={selectedConnection ? <><b>{uniqueSourceCount(activeConnectionMappings)} Source System{uniqueSourceCount(activeConnectionMappings) === 1 ? '' : 's'}</b> currently depend on active mappings for this connection. Endpoint, enabled-state, authentication, or mapping changes can affect subsequent provider operations.</> : <>Creating a connection alone does <b>not</b> route a Source System. Routing starts only after a validated mapping is published.</>}
      validation={<>Use <b>Safe connection check</b> from Authentication before publishing a mapping. Provider authentication/permission evidence and routing activation remain separate steps.</>}
      recovery={<>Unsaved edits can be discarded immediately. Source/Task mappings use draft, validation, immutable versions, and rollback drafts. For a live connection endpoint change, create and validate a replacement connection before moving mappings.</>}
    >
      <ConfigurationJourney steps={journeySteps} />
      {initialSourceSystemId || initialMappingId ? <div className="mt-3 rounded-xl border border-violet-200 bg-violet-50 p-3 text-sm font-semibold text-violet-950">Opened from a recovery context: {initialMappingId ? <>Project Mapping <b>{initialMappingId}</b></> : <>Source <b>{initialSourceSystemId}</b>{initialTaskType ? <> · task <b>{initialTaskType}</b></> : <> · default task mapping</>}</>}.</div> : null}
    </ConfigurationPurposePanel>

    <ConfigurationLifecycleBoundaryNotice
      title="Know when a change becomes active"
      governed={<>Source / Task <b>Project Mappings</b> use Draft → Validate → Impact Review → Publish → Verify. Published versions are immutable, and rollback creates a new draft that must be validated and published.</>}
      directSave={<>Provider <b>Connections</b>, <b>Service Accounts</b>, and credential references still use direct-save APIs. Saving those records can affect subsequent provider operations; they do not currently have a separate draft/publish/rollback lifecycle. Use impact preview, safe validation, and the safer replacement pattern for high-risk production changes.</>}
    />

    {feedback ? <ConfigurationFeedback {...feedback} /> : null}

    <div className="grid gap-5 xl:grid-cols-[310px_minmax(0,1fr)]">
      <IntegrationConnectionNavigator
        connections={connections}
        mappings={projectMappings}
        selectedConnectionId={selectedConnectionId}
        onSelect={(connectionId) => { setSelectedServiceAccountId(''); setSelectedConnectionId(connectionId); setWorkspaceTab(initialMappingId || initialSourceSystemId ? 'mappings' : 'overview'); }}
        onCreate={() => { setSelectedConnectionId(''); setConnectionDraft(blankConnection()); setSelectedServiceAccountId(''); setProbe(null); setWorkspaceTab('overview'); }}
      />

      <section className="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-5 py-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="text-xs font-black uppercase tracking-wide text-slate-500">Integration workspace</div>
              <h2 className="mt-1 text-lg font-black text-slate-950">{selectedConnection ? `${selectedProviderLabel} · ${selectedConnection.connectionName}` : 'Add Issue Tracking connection'}</h2>
              <p className="mt-1 text-sm leading-6 text-slate-600">{selectedConnection ? 'Work on one connection at a time. Authentication, mappings, and runtime evidence stay separated so the purpose and activation boundary remain clear.' : 'Choose the provider first, then create the reusable endpoint. A new connection does not change task routing by itself.'}</p>
            </div>
            {selectedConnection ? <div className="flex flex-wrap gap-2 text-xs font-black">
              <span className={`rounded-full border px-3 py-1 ${selectedConnection.enabled && String(selectedConnection.status ?? '').toUpperCase() === 'ACTIVE' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-slate-200 bg-slate-100 text-slate-700'}`}>{selectedConnection.enabled && String(selectedConnection.status ?? '').toUpperCase() === 'ACTIVE' ? 'Connection enabled' : 'Connection disabled'}</span>
              <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-blue-800">{activeConnectionMappings.length} active mapping{activeConnectionMappings.length === 1 ? '' : 's'}</span>
            </div> : null}
          </div>
        </div>

        {selectedConnection ? <IntegrationWorkspaceTabs value={workspaceTab} onChange={setWorkspaceTab} mappingCount={connectionMappings.length} attention={Boolean(probe && !probePassed) || !selectedConnection.enabled} /> : null}

        <div className="space-y-5 p-5">
          {!selectedConnection || workspaceTab === 'overview' ? <div role={selectedConnection ? 'tabpanel' : undefined} id={selectedConnection ? 'integration-panel-overview' : undefined} aria-labelledby={selectedConnection ? 'integration-tab-overview' : undefined} className="space-y-5">
            {mode === 'basic' && selectedConnection ? <section className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div><div className="font-black">Basic view focuses on the provider endpoint and safe next action.</div><p className="mt-1 text-xs leading-5 text-blue-900">Authentication, mappings, and runtime health each have their own workspace tab. Switch to Advanced only when you need deployment metadata or deeper operational context.</p></div>
                <Button type="button" onClick={() => setMode('advanced')} tone="secondary" size="xs">Show Advanced details</Button>
              </div>
            </section> : null}

            <section className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-xs font-black uppercase tracking-wide text-slate-500">Overview</div>
                  <h3 className="mt-1 font-black text-slate-950">{selectedConnection ? 'Provider endpoint' : 'Choose provider and create endpoint'}</h3>
                  <p className="mt-1 text-xs leading-5 text-slate-500">One provider connection can be reused by multiple Source Systems. Provider type is immutable after creation; create a replacement connection to move between providers safely.</p>
                </div>
                {selectedConnection ? <Button type="button" onClick={() => { setSelectedConnectionId(''); setSelectedServiceAccountId(''); setCredentials([]); setConnectionDraft(blankConnection()); setProbe(null); }} tone="secondary" size="xs">+ Add another</Button> : null}
              </div>

              <div className="mt-4 grid gap-3">
                <label className="text-sm font-bold text-slate-700">Provider<select disabled={Boolean(selectedConnectionId)} value={connectionDraft.providerType} onChange={(event) => { const providerType=event.target.value as ProviderType; setConnectionDraft({ ...blankConnection(providerType), connectionName: connectionDraft.connectionName, baseUrl: connectionDraft.baseUrl }); }} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-normal disabled:bg-slate-100">{PROVIDERS.filter((provider) => provider.creatable || provider.value === connectionDraft.providerType).map((provider) => <option key={provider.value} value={provider.value}>{provider.label}{provider.creatable ? '' : ' · existing only'}</option>)}</select><span className="mt-1 block text-xs font-medium text-slate-500">Provider type cannot be changed after the connection identity is created. Create another connection to change providers safely.</span></label>
                <div className="grid gap-3 md:grid-cols-2">
                  <Field label="Display name" value={connectionDraft.connectionName} onChange={(value) => setConnectionDraft({ ...connectionDraft, connectionName: value })}/>
                  <Field label={`${selectedProviderLabel} base URL`} value={connectionDraft.baseUrl} onChange={(value) => setConnectionDraft({ ...connectionDraft, baseUrl: value })}/>
                </div>
                <label className="flex items-center gap-2 text-sm font-bold"><input type="checkbox" checked={Boolean(connectionDraft.enabled)} onChange={(event) => setConnectionDraft({ ...connectionDraft, enabled: event.target.checked })}/> Enable connection</label>
                {mode === 'basic' ? null : <details className="rounded-xl border border-slate-200 bg-white p-3"><summary className="cursor-pointer text-sm font-black text-slate-700">Advanced connection behavior</summary><div className="mt-3 grid gap-3 md:grid-cols-2"><Field label="Deployment type" value={connectionDraft.deploymentType ?? ''} onChange={(value) => setConnectionDraft({ ...connectionDraft, deploymentType: value })}/><Field label="Timeout (ms)" value={String(connectionDraft.timeoutMs ?? 10000)} onChange={(value) => setConnectionDraft({ ...connectionDraft, timeoutMs: Number(value) || 10000 })}/>{mode === 'developer' ? <Field label="Connection ID" value={connectionDraft.connectionId} onChange={(value) => setConnectionDraft({ ...connectionDraft, connectionId: value })}/> : null}</div></details>}

                {hasConnectionChanges ? <ConfigurationImpactPreview
                  risk={connectionRisk}
                  summary={selectedConnection ? 'You are editing an existing provider connection. Review what currently depends on it before saving.' : 'You are creating a reusable provider connection. It will not route tasks until a Source/Task mapping is published.'}
                  items={[
                    { label: 'Change', value: selectedConnection ? 'Update existing connection' : 'Create new connection', tone: selectedConnection ? 'warning' : 'info' },
                    { label: 'Mappings', value: `${connectionMappings.length} total · ${activeConnectionMappings.length} active`, detail: selectedConnection ? 'Mappings reference this connection by ID.' : 'No mapping references a new connection yet.' },
                    { label: 'Source Systems', value: String(uniqueSourceCount(activeConnectionMappings)), detail: 'Counted from active mappings.' },
                    { label: 'Runtime effect', value: selectedConnection && hasRuntimeConnectionChanges && activeConnectionMappings.length > 0 ? 'Can affect new provider operations after save' : 'No routing activation by this save alone', tone: selectedConnection && hasRuntimeConnectionChanges && activeConnectionMappings.length > 0 ? 'risk' : 'ready' },
                  ]}
                  safetyNote={selectedConnection && activeConnectionMappings.length > 0 && hasRuntimeConnectionChanges ? 'Safer production change: create a new connection, validate its technical identity, then move Source/Task mappings through draft → validate → publish. The current API does not provide a separate draft lifecycle for connection endpoint edits.' : 'After saving, open Authentication and run Safe connection check before publishing a new Source/Task mapping.'}
                /> : null}

                <div className="flex flex-wrap gap-2">
                  <Button disabled={busy || !hasConnectionChanges || !connectionDraft.connectionName.trim() || !connectionDraft.baseUrl} type="button" onClick={() => perform(async () => { const connectionId=connectionDraft.connectionId.trim() || generatedId(providerPrefix(connectionDraft.providerType),connectionDraft.connectionName); const saved = await saveConnection({ ...connectionDraft, connectionId }); await refreshConnections(saved.connectionId); setWorkspaceTab('authentication'); }, `${selectedProviderLabel} connection saved. Continue with Authentication.`, selectedConnectionId ? () => refreshConnections(selectedConnectionId) : undefined)} tone={connectionRisk === 'HIGH' ? 'warning' : 'primary'} size="md">{selectedConnection ? (connectionRisk === 'HIGH' ? 'Save high-impact connection change' : 'Save connection changes') : 'Create connection'}</Button>
                  {selectedConnection && hasConnectionChanges ? <Button disabled={busy} type="button" onClick={() => setConnectionDraft(selectedConnection)} tone="secondary" size="md">Discard unsaved changes</Button> : null}
                  {selectedConnection && !hasConnectionChanges ? <Button type="button" onClick={() => setWorkspaceTab('authentication')} tone="primary" size="md">Continue to Authentication</Button> : null}
                </div>
              </div>
            </section>
          </div> : null}

          {selectedConnection && workspaceTab === 'authentication' ? <div role="tabpanel" id="integration-panel-authentication" aria-labelledby="integration-tab-authentication" className="space-y-5">
            <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5">
              <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Authentication · Technical identity</div>
              <h3 className="mt-1 font-black text-indigo-950">Who does OpenDispatch use at {selectedProviderLabel}?</h3>
              <p className="mt-1 text-sm leading-6 text-indigo-900">Choose the external Service Account used by this connector. Provider-side roles, project membership and issue permissions remain authoritative in the provider.</p>
              <label className="mt-4 block text-sm font-bold text-slate-700">Service Account<select value={selectedServiceAccountId} onChange={(event) => setSelectedServiceAccountId(event.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2"><option value="">New Service Account</option>{serviceAccounts.map((value) => <option key={value.principalId} value={value.principalId}>{value.principalName}</option>)}</select></label>
              <div className="mt-4 grid gap-3">
                <div className="grid gap-3 md:grid-cols-2"><Field label="Display name" value={serviceAccountDraft.principalName} onChange={(value) => setServiceAccountDraft({ ...serviceAccountDraft, principalName: value })}/><Field label="External user identifier" value={serviceAccountDraft.externalPrincipalIdentifier ?? ''} onChange={(value) => setServiceAccountDraft({ ...serviceAccountDraft, externalPrincipalIdentifier: value })}/></div>
                {mode === 'developer' ? <details className="rounded-xl border border-indigo-200 bg-white p-3"><summary className="cursor-pointer text-sm font-black text-indigo-800">Developer identity metadata</summary><div className="mt-3"><Field label="Service Account ID" value={serviceAccountDraft.principalId} onChange={(value) => setServiceAccountDraft({ ...serviceAccountDraft, principalId: value, connectionId: selectedConnectionId, principalType: 'SERVICE_ACCOUNT' })}/></div></details> : null}

                {hasPrincipalChanges ? <ConfigurationImpactPreview
                  title="Before you save the technical identity"
                  risk={activeConnectionMappings.length > 0 ? 'MEDIUM' : 'LOW'}
                  summary="The Service Account controls which provider permissions OpenDispatch receives. Saving it does not publish a Source/Task mapping."
                  items={[
                    { label: 'Connection', value: selectedConnection.connectionName },
                    { label: 'Active mappings', value: String(activeConnectionMappings.length), detail: 'These mappings can depend on provider permissions available to this connection.' },
                    { label: 'Credential refs', value: String(credentials.length), detail: 'Secret material is not displayed by OpenDispatch.' },
                    { label: 'Next safe step', value: 'Configure credential and run Safe connection check', tone: 'info' },
                  ]}
                  safetyNote="Changing the provider user identifier cannot grant permissions by itself; actual privileges are controlled by the external provider. Re-run validation after identity or credential changes."
                /> : null}

                <div className="flex flex-wrap gap-2">
                  <Button disabled={busy || !hasPrincipalChanges || !serviceAccountDraft.principalName.trim()} type="button" onClick={() => perform(async () => { const principalId=serviceAccountDraft.principalId.trim() || generatedId(generatedId('svc',selectedConnectionId),serviceAccountDraft.principalName); const saved = await savePrincipal(selectedConnectionId, { ...serviceAccountDraft, principalId, connectionId: selectedConnectionId, principalType: 'SERVICE_ACCOUNT' }); await refreshServiceAccounts(selectedConnectionId, saved.principalId); }, 'Technical Service Account saved.', () => refreshServiceAccounts(selectedConnectionId, selectedServiceAccountId || undefined))} tone="primary" size="md">{selectedServiceAccount ? 'Save Service Account changes' : 'Create Service Account'}</Button>
                  {selectedServiceAccount && hasPrincipalChanges ? <Button disabled={busy} type="button" onClick={() => setServiceAccountDraft(selectedServiceAccount)} tone="secondary" size="md">Discard unsaved changes</Button> : null}
                </div>
              </div>
            </section>

            {selectedServiceAccountId ? <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
              <div className="text-xs font-black uppercase tracking-wide text-amber-700">Authentication · Credential</div>
              <h3 className="mt-1 font-black text-amber-950">Authentication reference</h3>
              <p className="mt-1 text-sm leading-6 text-amber-900">Point OpenDispatch to credential material stored in your secret system. Secret material is never displayed here. Save the reference, then run Safe connection check before relying on it.</p>
              <div className="mt-3 space-y-2">{credentials.length ? credentials.map((value, index) => <div key={value.credentialId} className="rounded-xl border border-amber-200 bg-white p-3"><div className="font-black text-slate-900">{mode === 'basic' ? `Authentication reference ${index + 1}` : value.credentialId}</div><div className="mt-1 text-xs text-slate-600">{maskReference(value.secretRef)} · {value.authType}{mode === 'basic' ? '' : ` · version ${value.secretVersion || 'current'}`} · {value.status}</div>{mode === 'developer' ? <div className="mt-1 text-xs text-slate-500">Last used: {value.lastUsedAt ? new Date(value.lastUsedAt).toLocaleString() : 'Not observed yet'}</div> : null}</div>) : <p className="text-sm text-amber-900">No authentication reference has been configured.</p>}</div>
              {mode === 'basic' ? null : <div className="mt-4"><CredentialMetadataImpactPanel credentials={credentials} principalCount={serviceAccounts.length}/></div>}
              <details className="mt-4 rounded-xl border border-amber-200 bg-white/80 p-3"><summary className="cursor-pointer text-sm font-black text-amber-900">Add a credential reference</summary><div className="mt-3 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                {mode === 'developer' ? <Field label="Credential ID" value={credentialDraft.credentialId} onChange={(value) => setCredentialDraft({ ...credentialDraft, credentialId: value, principalId: selectedServiceAccountId })}/> : null}
                <label className="text-sm font-bold text-slate-700">Authentication type<select value={credentialDraft.authType} onChange={(event)=>setCredentialDraft({...credentialDraft,authType:event.target.value})} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-normal"><option>API_TOKEN</option><option>PERSONAL_ACCESS_TOKEN</option><option>BASIC_PASSWORD</option><option>OAUTH_CLIENT_CREDENTIALS</option><option>OAUTH_REFRESH_TOKEN</option><option>WEBHOOK_HMAC</option></select></label>
                <Field label="Secret reference" value={credentialDraft.secretRef ?? ''} onChange={(value) => setCredentialDraft({ ...credentialDraft, secretRef: value })} placeholder="vault://mount/path#field"/>
                {mode === 'basic' ? null : <Field label="Secret version" value={credentialDraft.secretVersion ?? ''} onChange={(value) => setCredentialDraft({ ...credentialDraft, secretVersion: value })}/>} 
              </div>
              <ConfigurationImpactPreview
                title="Before you add this credential reference"
                risk="MEDIUM"
                summary="The reference is stored as credential metadata for the selected Service Account. Secret material remains in the configured secret store."
                items={[
                  { label: 'Initial state', value: credentialDraft.status ?? 'PENDING_VALIDATION', tone: 'warning' },
                  { label: 'Secret material', value: 'Not displayed', tone: 'ready' },
                  { label: 'Routing', value: 'Not published by this action' },
                  { label: 'Required next step', value: 'Run Safe connection check', tone: 'info' },
                ]}
                safetyNote="Do not assume a newly stored reference works until provider authentication/permission validation succeeds."
              />
              <Button disabled={busy || !credentialDraft.secretRef} type="button" onClick={() => perform(async () => { const credentialId = credentialDraft.credentialId.trim() || generatedId('cred', `${selectedServiceAccountId}-${credentialDraft.authType}`); await addCredential(selectedServiceAccountId, { ...credentialDraft, credentialId, principalId: selectedServiceAccountId }); await refreshCredentials(selectedServiceAccountId); setProbe(null); }, 'Authentication reference saved in PENDING_VALIDATION state. Run Safe connection check before relying on it.', () => refreshCredentials(selectedServiceAccountId))} className="mt-3" tone="warning" size="md">Add Authentication Reference</Button></details>
            </section> : <section className="rounded-2xl border border-dashed border-indigo-300 bg-indigo-50 p-5 text-sm text-indigo-900">Create or select a Service Account before adding authentication material.</section>}

            <ConfigurationValidationPanel
              title="Safe connection check"
              status={validating ? 'RUNNING' : probe ? (probePassed ? 'PASSED' : 'FAILED') : 'NOT_RUN'}
              summary={probe ? (probe.providerResponseSummary || `Provider probe completed with status ${probe.overallStatus}.`) : selectedServiceAccountId ? 'Run an authentication and permission probe against the current saved connection and Service Account.' : 'Configure a saved technical Service Account before validation.'}
              checks={probeEntries.map(([label, value]) => ({ label, value: String(value), passed: ACCEPTED_PROBE_VALUES.has(String(value).toUpperCase()) }))}
              safetyNote="This check does not publish a Source/Task mapping, activate a new mapping, or change task routing. If it fails, review the provider URL, credential reference, provider-side membership and permissions."
              action={<Button type="button" disabled={busy || !selectedServiceAccountId} onClick={() => void runSafeConnectionCheck()} tone="primary" size="md">Run Safe connection check</Button>}
            />

            {probePassed ? <div className="flex justify-end"><Button type="button" onClick={() => setWorkspaceTab('mappings')} tone="primary" size="md">Continue to Mappings</Button></div> : null}
          </div> : null}

          {selectedConnection && workspaceTab === 'mappings' ? <div role="tabpanel" id="integration-panel-mappings" aria-labelledby="integration-tab-mappings"><ProjectMappingGovernancePanel
            connectionId={selectedConnectionId}
            providerType={selectedConnection.providerType}
            initialSourceSystemId={initialSourceSystemId}
            initialTaskType={initialTaskType}
            initialMappingId={initialMappingId}
            onMappingsChanged={() => refreshConnections(selectedConnectionId)}
          /></div> : null}

          {selectedConnection && workspaceTab === 'runtime' ? <div role="tabpanel" id="integration-panel-runtime" aria-labelledby="integration-tab-runtime" className="space-y-5">
            <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
              <div className="text-xs font-black uppercase tracking-wide text-emerald-700">Runtime Health</div>
              <h3 className="mt-1 font-black text-emerald-950">What can be trusted right now?</h3>
              <p className="mt-1 text-sm leading-6 text-emerald-900">Connection configuration, provider authentication, active mappings, and live connector execution are different states. Use this workspace for evidence and the operations console for runtime recovery.</p>
              <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <RuntimeFact label="Connection" value={selectedConnection.enabled && String(selectedConnection.status ?? '').toUpperCase() === 'ACTIVE' ? 'Enabled' : 'Disabled'} ready={Boolean(selectedConnection.enabled && String(selectedConnection.status ?? '').toUpperCase() === 'ACTIVE')} />
                <RuntimeFact label="Authentication check" value={!probe ? 'Not run this session' : probePassed ? 'Passed' : 'Needs attention'} ready={probePassed} neutral={!probe} />
                <RuntimeFact label="Active mappings" value={String(activeConnectionMappings.length)} ready={activeConnectionMappings.length > 0} neutral={activeConnectionMappings.length === 0} />
                <RuntimeFact label="Source Systems" value={String(uniqueSourceCount(activeConnectionMappings))} ready={uniqueSourceCount(activeConnectionMappings) > 0} neutral={uniqueSourceCount(activeConnectionMappings) === 0} />
              </div>
            </section>

            {mode === 'basic' ? <section className="rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-700"><div className="font-black text-slate-950">Need deeper runtime evidence?</div><p className="mt-1 leading-6">Advanced shows principal/credential operational summary. Developer additionally exposes resource-governance evidence. UI level does not grant any permission.</p><Button type="button" onClick={() => setMode('advanced')} className="mt-3" tone="secondary" size="xs">Show Advanced runtime evidence</Button></section> : <IssueConnectionOperationalSummary connection={selectedConnection} principal={selectedServiceAccount} credentials={credentials} probe={probe}/>} 
            {mode === 'developer' ? <ResourceGovernancePanel resourceType="ISSUE_CONNECTION" resourceId={selectedConnectionId} permissionCode="integration.issue.connection.read" requestedVisibility="STANDARD" compact/> : null}

            <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><div className="text-xs font-black uppercase tracking-wide text-emerald-700">Operational handoff</div><h3 className="mt-1 font-black text-emerald-950">Observe connector operations</h3><p className="mt-1 text-sm leading-6 text-emerald-900">Use runtime evidence to distinguish provider success, permission denial, authentication failure, rate limiting and availability problems. Configuration remains managed here; operational recovery stays separate.</p></div><Link href="/operations/integration-sync" className="shrink-0 rounded-xl bg-emerald-800 px-4 py-2 text-sm font-black text-white hover:bg-emerald-900">Open integration operations</Link></div>
            </section>
          </div> : null}
        </div>
      </section>
    </div>
  </div>;
}

function RuntimeFact({label,value,ready,neutral=false}:{label:string;value:string;ready:boolean;neutral?:boolean}){
  const classes = neutral ? 'border-slate-200 bg-white text-slate-700' : ready ? 'border-emerald-200 bg-white text-emerald-900' : 'border-amber-200 bg-amber-50 text-amber-900';
  return <div className={`rounded-xl border p-3 ${classes}`}><div className="text-[10px] font-black uppercase tracking-wide opacity-70">{label}</div><div className="mt-1 text-sm font-black">{value}</div></div>;
}

function Field({label,value,onChange,placeholder}:{label:string;value:string;onChange:(value:string)=>void;placeholder?:string}){
  return <label className="text-sm font-bold text-slate-700">{label}<input value={value} onChange={(event)=>onChange(event.target.value)} placeholder={placeholder} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-normal"/></label>;
}
