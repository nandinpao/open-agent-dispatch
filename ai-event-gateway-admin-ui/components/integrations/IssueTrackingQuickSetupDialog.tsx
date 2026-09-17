'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { IssueTrackingSetupProgress } from '@/components/integrations/IssueTrackingSetupProgress';
import { AdvancedSection, EntityPicker, FormField, InlineCreateButton, SelectField, TextField } from '@/components/forms';
import {
  activateSourceIssueTracking,
  addCredential,
  discoverProviderMetadata,
  getSourceIssueTrackingReadiness,
  probeSourceIssueTrackingReadiness,
  listConnections,
  listCredentials,
  listPrincipals,
  listProjectMappings,
  runPermissionProbe,
  saveConnection,
  savePrincipal,
  saveRedmineApiKeyAndTest,
  type IntegrationConnection,
  type IntegrationCredentialMetadata,
  type IntegrationPrincipal,
  type IntegrationProjectMapping,
  type IssueTrackingRuntimeReadiness,
  type PermissionProbeResult,
  type ProviderMetadataSnapshot,
} from '@/lib/api/domains/integrationIdentityApi';

export interface IssueTrackingContextOption {
  sourceSystemId: string;
  taskType?: string | null;
  label?: string;
}

interface Props {
  open: boolean;
  contexts: IssueTrackingContextOption[];
  title?: string;
  onClose: () => void;
  onSaved?: () => void | Promise<void>;
}

type SecretMode = 'VAULT' | 'ENV' | 'FILE';

function clean(value?: string | null) { return String(value ?? '').trim(); }
function upper(value?: string | null) { return clean(value).toUpperCase(); }
function safeId(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 64) || 'default';
}
function contextKey(value: IssueTrackingContextOption) { return `${upper(value.sourceSystemId)}|${upper(value.taskType)}`; }
function normalizeContexts(values: IssueTrackingContextOption[]) {
  const seen = new Set<string>();
  return values.filter((value) => {
    const source = clean(value.sourceSystemId);
    if (!source) return false;
    const key = contextKey(value);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
function errorText(error: unknown) { return error instanceof Error ? error.message : String(error || 'The operation failed.'); }
function activeCredential(values: IntegrationCredentialMetadata[]) {
  return values.find((value) => ['ACTIVE', 'GRACE_PERIOD'].includes(upper(value.status)));
}
function credentialReady(values: IntegrationCredentialMetadata[]) { return Boolean(activeCredential(values)); }
function activeLifecycleMapping(value: IntegrationProjectMapping) { return value.enabled === true && upper(value.lifecycleStatus) === 'ACTIVE'; }
function runtimeReadyMapping(value: IntegrationProjectMapping) {
  return activeLifecycleMapping(value)
    && upper(value.mappingStatus) === 'VALID'
    && Boolean(clean(value.metadataSnapshotId))
    && Boolean(clean(value.metadataSchemaHash));
}
function matchesContext(mapping: IntegrationProjectMapping, context: IssueTrackingContextOption) {
  if (upper(mapping.sourceSystemId) !== upper(context.sourceSystemId)) return false;
  const expectedTask = upper(context.taskType);
  const actualTask = upper(mapping.taskType);
  // Source-level default mapping applies to every work type under the Source.
  return !actualTask || (!!expectedTask && expectedTask === actualTask);
}
function credentialReference(mode: SecretMode, location: string, field: string) {
  const value = location.trim();
  if (!value) return '';
  if (mode === 'VAULT') return `vault://${value}${field.trim() ? `#${field.trim()}` : ''}`;
  if (mode === 'ENV') return `env://${value}`;
  return `file://${value}`;
}

export function IssueTrackingQuickSetupDialog({ open, contexts, title='Configure Issue Tracking', onClose, onSaved }: Readonly<Props>) {
  const dialogRef = useDialogAccessibility(open, onClose);
  // Parent pages often rebuild the contexts array on every render. Convert it to a
  // value signature first so opening this dialog does not repeatedly reset/reload.
  const contextSignature = JSON.stringify(contexts.map((value) => ({ sourceSystemId: clean(value.sourceSystemId), taskType: clean(value.taskType) || null, label: clean(value.label) || null })));
  const normalizedContexts = useMemo(() => normalizeContexts(JSON.parse(contextSignature) as IssueTrackingContextOption[]), [contextSignature]);

  const [contextIndex, setContextIndex] = useState(0);
  const context = normalizedContexts[Math.min(contextIndex, Math.max(0, normalizedContexts.length - 1))] ?? { sourceSystemId:'', taskType:null };
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [connectionId, setConnectionId] = useState('');
  const [principals, setPrincipals] = useState<IntegrationPrincipal[]>([]);
  const [principalId, setPrincipalId] = useState('');
  const [credentials, setCredentials] = useState<IntegrationCredentialMetadata[]>([]);
  const [mappings, setMappings] = useState<IntegrationProjectMapping[]>([]);
  const [metadata, setMetadata] = useState<ProviderMetadataSnapshot | null>(null);
  const [probe, setProbe] = useState<PermissionProbeResult | null>(null);
  const [serverReadiness, setServerReadiness] = useState<IssueTrackingRuntimeReadiness | null>(null);
  const [projectId, setProjectId] = useState('');
  const [trackerId, setTrackerId] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const [showNewConnection, setShowNewConnection] = useState(false);
  const [newConnectionName, setNewConnectionName] = useState('Redmine Production');
  const [newConnectionUrl, setNewConnectionUrl] = useState('');
  const [showNewPrincipal, setShowNewPrincipal] = useState(false);
  const [newPrincipalName, setNewPrincipalName] = useState('OpenDispatch Issue Service');
  const [externalPrincipalId, setExternalPrincipalId] = useState('');
  const [secretMode, setSecretMode] = useState<SecretMode>('VAULT');
  const [secretLocation, setSecretLocation] = useState('');
  const [secretField, setSecretField] = useState('apiKey');
  const [credentialId, setCredentialId] = useState('');
  const [redmineApiKey, setRedmineApiKey] = useState('');

  const selectedConnection = connections.find((value) => value.connectionId === connectionId);
  const currentMappings = mappings.filter((value) => matchesContext(value, context));
  const currentMappingReady = currentMappings.find(runtimeReadyMapping);
  const staleActive = currentMappings.find((value) => activeLifecycleMapping(value) && !runtimeReadyMapping(value));
  const mutableDraft = currentMappings.find((value) => !activeLifecycleMapping(value) && ['DRAFT','VALIDATING','VALID'].includes(upper(value.lifecycleStatus)));
  const projectOptions = metadata?.projects?.filter((value) => value.accessible) ?? [];
  const trackerOptions = metadata?.issueTypes ?? [];
  const credentialMaterialUnavailable = Boolean(serverReadiness?.blockers?.some((value) => /ISSUE_CREDENTIAL_SECRET|SECRET_REFERENCE/i.test(value)));
  const serverRuntimeReady = Boolean(serverReadiness?.runtimeReady && serverReadiness.providerAuthenticated);
  const activationBlocker = !context.sourceSystemId
    ? 'Open this setup from a Source System. Issue Tracking is configured once per Source System.'
    : !selectedConnection?.enabled || upper(selectedConnection?.status) !== 'ACTIVE'
      ? 'Choose an enabled ACTIVE Redmine connection.'
    : !credentialReady(credentials)
      ? 'Save and successfully test the Redmine API Key first.'
    : credentialMaterialUnavailable
      ? 'The saved Redmine API Key material is no longer resolvable by Core. Replace the API Key and test the connection again before activation.'
      : !projectId
        ? 'Choose a Redmine project.'
        : !trackerId
          ? 'Choose a Redmine tracker.'
          : '';
  const currentActive = serverRuntimeReady
    && currentMappingReady
    && serverReadiness?.mappingId === currentMappingReady.mappingId
      ? currentMappingReady
      : undefined;

  async function refreshConnections(preferred?: string) {
    const values = (await listConnections()).filter((value) => value.providerType === 'REDMINE');
    setConnections(values);
    setConnectionId(preferred || connectionId || values[0]?.connectionId || '');
  }
  async function refreshPrincipals(targetConnectionId: string, preferred?: string) {
    if (!targetConnectionId) { setPrincipals([]); setPrincipalId(''); return; }
    const values = (await listPrincipals(targetConnectionId)).filter((value) => value.principalType === 'SERVICE_ACCOUNT');
    setPrincipals(values);
    setPrincipalId(preferred ?? values[0]?.principalId ?? '');
  }
  async function refreshCredentials(targetPrincipalId: string) {
    if (!targetPrincipalId) { setCredentials([]); return; }
    setCredentials(await listCredentials(targetPrincipalId));
  }
  async function refreshMappings() { setMappings(await listProjectMappings()); }
  async function refreshServerReadiness(liveProbe=false) {
    if (!context.sourceSystemId) { setServerReadiness(null); return null; }
    const value = liveProbe
      ? await probeSourceIssueTrackingReadiness(context.sourceSystemId, context.taskType ?? null)
      : await getSourceIssueTrackingReadiness(context.sourceSystemId, context.taskType ?? null);
    setServerReadiness(value);
    return value;
  }

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setContextIndex(0); setError(''); setMessage(''); setMetadata(null); setProbe(null); setServerReadiness(null); setProjectId(''); setTrackerId('');
    setBusy(true);
    Promise.all([listConnections(), listProjectMappings()])
      .then(async ([allConnections, allMappings]) => {
        if (cancelled) return;
        const redmine = allConnections.filter((value) => value.providerType === 'REDMINE');
        setConnections(redmine); setMappings(allMappings);
        const preferredMapping = allMappings.find((value) => normalizedContexts.some((item) => matchesContext(value, item)) && runtimeReadyMapping(value))
          ?? allMappings.find((value) => normalizedContexts.some((item) => matchesContext(value, item)) && activeLifecycleMapping(value));
        const nextConnection = preferredMapping?.connectionId ?? redmine[0]?.connectionId ?? '';
        setConnectionId(nextConnection);
        if (nextConnection) {
          const accounts = (await listPrincipals(nextConnection)).filter((value) => value.principalType === 'SERVICE_ACCOUNT');
          if (cancelled) return;
          setPrincipals(accounts);
          const explicit = preferredMapping ? [preferredMapping.readPrincipalId, preferredMapping.createPrincipalId, preferredMapping.commentPrincipalId, preferredMapping.updatePrincipalId].find(Boolean) : null;
          setPrincipalId(clean(explicit) || accounts[0]?.principalId || '');
        }
      })
      .catch((reason) => { if (!cancelled) setError(errorText(reason)); })
      .finally(() => { if (!cancelled) setBusy(false); });
    return () => { cancelled = true; };
  }, [open, normalizedContexts]);

  useEffect(() => {
    if (!open || !connectionId) return;
    let cancelled = false;
    setMetadata(null); setProjectId(''); setTrackerId(''); setProbe(null); setServerReadiness(null);
    refreshPrincipals(connectionId).catch((reason) => { if (!cancelled) setError(errorText(reason)); });
    return () => { cancelled = true; };
  }, [connectionId, open]);

  useEffect(() => { if (open) void refreshCredentials(principalId).catch((reason) => setError(errorText(reason))); }, [principalId, open]);

  useEffect(() => {
    if (!open || !context.sourceSystemId) { setServerReadiness(null); return; }
    let cancelled = false;
    getSourceIssueTrackingReadiness(context.sourceSystemId, context.taskType ?? null)
      .then((value) => { if (!cancelled) setServerReadiness(value); })
      .catch((reason) => { if (!cancelled) setError(errorText(reason)); });
    return () => { cancelled = true; };
  }, [open, context.sourceSystemId, context.taskType, currentMappingReady?.mappingId, currentMappingReady?.mappingVersion, credentials.map((value)=>`${value.credentialId}:${value.secretVersion}:${value.status}`).join('|')]);

  useEffect(() => {
    const active = currentMappingReady ?? staleActive ?? mutableDraft;
    if (!active) return;
    setConnectionId(active.connectionId);
    setProjectId(clean(active.externalProjectId));
    setTrackerId(clean(active.externalIssueType || active.externalTrackerId));
  }, [currentMappingReady, staleActive, mutableDraft]);

  async function createConnection() {
    const baseUrl = newConnectionUrl.trim().replace(/\/+$/, '');
    if (!baseUrl) { setError('Enter the Redmine base URL.'); return; }
    const id = `redmine-${safeId(newConnectionName || new URL(baseUrl).hostname)}`;
    setBusy(true); setError('');
    try {
      const saved = await saveConnection({ connectionId:id, providerType:'REDMINE', connectionName:newConnectionName.trim() || 'Redmine', baseUrl, deploymentType:'SELF_HOSTED', status:'ACTIVE', timeoutMs:10000, enabled:true });
      await refreshConnections(saved.connectionId);
      const principalName = 'OpenDispatch Redmine Service';
      const principalKey = `${safeId(saved.connectionId)}-${safeId(principalName)}-svc`;
      const serviceAccount = await savePrincipal(saved.connectionId, { principalId:principalKey, connectionId:saved.connectionId, principalName, principalType:'SERVICE_ACCOUNT', externalPrincipalIdentifier:null, status:'DRAFT', riskLevel:'UNKNOWN' });
      await refreshPrincipals(saved.connectionId, serviceAccount.principalId);
      setShowNewConnection(false); setMessage('Redmine connection created. Add the API credential and test the connection.');
    } catch (reason) { setError(errorText(reason)); } finally { setBusy(false); }
  }

  async function createPrincipal() {
    if (!connectionId || !newPrincipalName.trim()) return;
    const id = `${safeId(connectionId)}-${safeId(newPrincipalName)}-svc`;
    setBusy(true); setError('');
    try {
      const saved = await savePrincipal(connectionId, { principalId:id, connectionId, principalName:newPrincipalName.trim(), principalType:'SERVICE_ACCOUNT', externalPrincipalIdentifier:externalPrincipalId.trim() || null, status:'DRAFT', riskLevel:'UNKNOWN' });
      await refreshPrincipals(connectionId, saved.principalId); setShowNewPrincipal(false); setMessage('Secure connection identity prepared. Add the API credential next.');
    } catch (reason) { setError(errorText(reason)); } finally { setBusy(false); }
  }

  async function saveRedmineApiKey() {
    if (!principalId) { setError('Prepare the Redmine connection identity first.'); return; }
    if (!redmineApiKey.trim()) { setError('Enter the Redmine API Key.'); return; }
    setBusy(true); setError(''); setMessage(''); setProbe(null);
    try {
      const result = await saveRedmineApiKeyAndTest(principalId, redmineApiKey.trim());
      setRedmineApiKey('');
      setProbe(result.probe);
      await refreshCredentials(principalId);
      await refreshServerReadiness(false);
      if (result.authenticated) {
        setMessage(`Redmine authentication succeeded. API Key saved securely (${result.storageMode}).`);
      } else {
        setError(result.probe.providerResponseSummary || 'Redmine rejected this API Key. Verify the key belongs to an account that can access the expected projects.');
      }
    } catch (reason) {
      setRedmineApiKey('');
      setError(errorText(reason));
    } finally { setBusy(false); }
  }

  async function saveCredentialAndTest() {
    if (!principalId) return;
    const reference = credentialReference(secretMode, secretLocation, secretField);
    if (!reference) { setError('Configure the secret location first.'); return; }
    const id = credentialId.trim() || `${safeId(principalId)}-api-token`;
    setBusy(true); setError(''); setProbe(null);
    try {
      await addCredential(principalId, { credentialId:id, principalId, authType:'API_TOKEN', secretRef:reference, secretVersion:'1', status:'PENDING_VALIDATION' });
      const result = await runPermissionProbe(principalId);
      setProbe(result);
      await refreshCredentials(principalId);
      await refreshServerReadiness(false);
      const authenticated = upper(result.capabilityResults?.Authentication || result.capabilityResults?.AUTHENTICATE) === 'GRANTED';
      setMessage(authenticated ? 'Credential reference resolved and Redmine authentication succeeded.' : 'Credential saved, but Redmine authentication did not pass. Review the secret reference or Redmine account.');
    } catch (reason) { setError(errorText(reason)); } finally { setBusy(false); }
  }

  async function discover() {
    if (!connectionId || !principalId) return;
    setBusy(true); setError(''); setMessage('');
    try {
      const snapshot = await discoverProviderMetadata(connectionId, principalId);
      setMetadata(snapshot);
      if (upper(snapshot.cacheStatus) === 'FAILED' || upper(snapshot.permissions?.METADATA) === 'ERROR') {
        throw new Error(snapshot.providerSummary || 'Redmine metadata discovery failed. Verify REST API access, the API credential, and the Redmine base URL.');
      }
      const current = currentMappingReady ?? staleActive ?? mutableDraft;
      const project = snapshot.projects.find((value) => clean(value.projectId) === clean(current?.externalProjectId) || clean(value.projectKey) === clean(current?.externalProjectKey)) ?? snapshot.projects.find((value) => value.accessible);
      const tracker = snapshot.issueTypes.find((value) => clean(value.issueTypeId) === clean(current?.externalIssueType) || clean(value.issueTypeKey) === clean(current?.externalIssueType)) ?? snapshot.issueTypes[0];
      setProjectId(clean(project?.projectId)); setTrackerId(clean(tracker?.issueTypeId));
      setMessage(snapshot.providerSummary || `Redmine metadata loaded: ${snapshot.projects.length} projects and ${snapshot.issueTypes.length} trackers.`);
    } catch (reason) { setError(errorText(reason)); } finally { setBusy(false); }
  }

  async function saveAndActivate() {
    if (!connectionId || !principalId || !projectId || !trackerId || !context.sourceSystemId) { setError('Redmine connection, API Key, Source System, Project and Tracker are required.'); return; }
    setBusy(true); setError(''); setMessage('');
    try {
      const selectedProject = projectOptions.find((value) => value.projectId === projectId);
      const selectedTracker = trackerOptions.find((value) => value.issueTypeId === trackerId);
      const result = await activateSourceIssueTracking(context.sourceSystemId, {
        connectionId,
        principalId,
        projectId,
        projectKey: selectedProject?.projectKey ?? null,
        trackerId,
      });
      if (result.metadata) setMetadata(result.metadata);
      if (result.probe) setProbe(result.probe);
      setMappings((previous) => [result.mapping, ...previous.filter((value) => value.mappingId !== result.mapping.mappingId)]);
      const authoritativeReadiness = await refreshServerReadiness(false);
      const projectLabel = selectedProject?.displayName || selectedProject?.projectKey || projectId;
      const trackerLabel = selectedTracker?.displayName || trackerId;
      const retiredNote = result.retiredLegacyMappings > 0 ? ` ${result.retiredLegacyMappings} old Agent/Flow-specific mapping(s) were retired automatically.` : '';
      setMessage(result.alreadyActive
        ? `Already active. ${context.sourceSystemId} uses Redmine project “${projectLabel}” with tracker “${trackerLabel}”. Server readiness: ${authoritativeReadiness?.overallStatus ?? 'CHECK_REQUIRED'}.`
        : `Activated successfully. ${context.sourceSystemId} now uses Redmine project “${projectLabel}” with tracker “${trackerLabel}”. Server readiness: ${authoritativeReadiness?.overallStatus ?? 'CHECK_REQUIRED'}. Agents and Flows inherit this connector context. OpenDispatch Task Issue Policy decides when a provider operation is requested.${retiredNote}`);
      try { await onSaved?.(); } catch { /* parent refresh is non-authoritative */ }
    } catch (reason) { setError(errorText(reason)); } finally { setBusy(false); }
  }

  if (!open) return null;
  return (
    <div ref={dialogRef} tabIndex={-1} className="fixed inset-0 z-[90] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 outline-none sm:p-8" role="dialog" aria-modal="true" aria-label={title}>
      <div className="w-full max-w-4xl rounded-3xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div><div className="text-xs font-black uppercase tracking-wide text-emerald-700">Issue Tracking</div><h2 className="mt-1 text-xl font-black text-slate-950">{title}</h2><p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">Configure Redmine once for this Source System: connect, enter the API Key, choose the project and activate. Redmine remains the authority for project membership, roles, workflow, visibility and issue permissions.</p></div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600">×</button>
        </div>
        <div className="space-y-5 p-6">
          {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{message}</div> : null}

          <IssueTrackingSetupProgress connectionReady={Boolean(selectedConnection?.enabled && upper(selectedConnection?.status) === 'ACTIVE')} credentialReady={credentialReady(credentials)} mappingReady={Boolean(projectId && trackerId)} active={Boolean(currentActive)} />

          {serverReadiness ? <section className="rounded-2xl border border-slate-200 bg-white p-4">
            <div className="flex flex-wrap items-start justify-between gap-2"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Runtime readiness</div><h3 className="mt-1 font-black text-slate-950">Core authority · {serverReadiness.overallStatus}</h3><p className="mt-1 text-sm leading-6 text-slate-600">Configured, executable runtime, provider authentication and live CREATE certification are separate states. “Active” alone is not runtime proof.</p></div><button type="button" disabled={busy} onClick={()=>void refreshServerReadiness(true)} className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700 disabled:opacity-50">Run live auth check</button></div>
            <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">{serverReadiness.checks.map((check)=><div key={check.code} className={`rounded-xl border p-3 ${['READY','CERTIFIED'].includes(check.status)?'border-emerald-200 bg-emerald-50':['BLOCKED'].includes(check.status)?'border-rose-200 bg-rose-50':['NOT_CERTIFIED'].includes(check.status)?'border-sky-200 bg-sky-50':'border-amber-200 bg-amber-50'}`}><div className="flex justify-between gap-2 text-xs font-black"><span>{check.label}</span><span>{check.status}</span></div><p className="mt-2 text-xs leading-5 text-slate-700">{check.summary}</p><p className="mt-2 break-all font-mono text-[10px] text-slate-500">{check.reasonCode || '—'}</p></div>)}</div>
            {serverReadiness.blockers.length ? <div className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs font-bold text-rose-900">First repair target: {serverReadiness.blockers[0]}{serverReadiness.blockers.length > 1 ? ` · ${serverReadiness.blockers.slice(1).join(' · ')}` : ''}</div> : null}
          </section> : null}

          <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Applies to</div>
            {normalizedContexts.length === 0 ? <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-900">Open Issue Tracking from a Source System. Agent and Dispatch Flow pages inherit this setup and cannot create another one.</div> : normalizedContexts.length === 1 ? <div className="mt-2 text-sm font-black text-slate-900">{context.label || context.sourceSystemId}{context.taskType ? ` · ${context.taskType}` : ''}</div> : <div className="mt-2"><SelectField id="issue-context" value={String(contextIndex)} onChange={(value)=>setContextIndex(Number(value))} options={normalizedContexts.map((value,index)=>({ value:String(index), label:value.label || `${value.sourceSystemId}${value.taskType ? ` · ${value.taskType}` : ''}` }))} /></div>}
          </section>

          <section className="rounded-2xl border border-blue-200 bg-blue-50 p-4">
            <div className="flex items-center justify-between gap-3"><div><h3 className="font-black text-blue-950">1. Redmine connection</h3><p className="mt-1 text-sm text-blue-900">Choose an existing Redmine connector or create one here.</p></div><InlineCreateButton onClick={()=>setShowNewConnection((value)=>!value)} label={showNewConnection?'Cancel new connection':'New connection'} /></div>
            <div className="mt-3"><EntityPicker id="issue-redmine-connection" value={connectionId} onChange={setConnectionId} placeholder="Select Redmine connection" options={connections.map((value)=>({value:value.connectionId,label:value.connectionName,description:`${value.baseUrl} · ${value.status || 'DRAFT'}`}))} /></div>
            {showNewConnection ? <div className="mt-3 grid gap-3 md:grid-cols-2"><FormField id="issue-new-connection-name" label="Display name"><TextField id="issue-new-connection-name" value={newConnectionName} onChange={setNewConnectionName} /></FormField><FormField id="issue-new-connection-url" label="Redmine base URL" required><TextField id="issue-new-connection-url" type="url" value={newConnectionUrl} onChange={setNewConnectionUrl} placeholder="https://redmine.example.com" /></FormField><button type="button" disabled={busy||!newConnectionUrl.trim()} onClick={()=>void createConnection()} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white disabled:opacity-50 md:col-span-2">Create connection</button></div>:null}
            {selectedConnection?<div className="mt-3 rounded-xl bg-white/80 p-3 text-xs text-blue-900">{selectedConnection.baseUrl} · {selectedConnection.enabled?'Enabled':'Disabled'}</div>:null}
          </section>

          <AdvancedSection title="Advanced · Redmine service account identity" description="This is the technical Redmine account identity, not the API Key. OpenDispatch creates it automatically; only change it when your Redmine administrator requires a specific service account.">
            <div className="space-y-3">
              <EntityPicker id="issue-service-account" disabled={!connectionId} value={principalId} onChange={setPrincipalId} placeholder="Connection identity" options={principals.map((value)=>({value:value.principalId,label:value.principalName,description:value.status || 'DRAFT'}))} />
              <InlineCreateButton disabled={!connectionId} onClick={()=>setShowNewPrincipal((value)=>!value)} label={showNewPrincipal?'Cancel':'Create another identity'} />
              {showNewPrincipal ? <div className="space-y-3"><FormField id="issue-new-principal-name" label="Display name" required><TextField id="issue-new-principal-name" value={newPrincipalName} onChange={setNewPrincipalName} /></FormField><FormField id="issue-external-principal" label="Redmine user identifier"><TextField id="issue-external-principal" value={externalPrincipalId} onChange={setExternalPrincipalId} /></FormField><button type="button" disabled={busy||!newPrincipalName.trim()} onClick={()=>void createPrincipal()} className="w-full rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white disabled:opacity-50">Create identity</button></div>:null}
            </div>
          </AdvancedSection>

          <section className={`rounded-2xl border p-4 ${credentialReady(credentials)?'border-emerald-200 bg-emerald-50':'border-amber-200 bg-amber-50'}`}>
            <h3 className="font-black text-slate-950">2. Redmine API Key & connection test</h3>
            <p className="mt-1 text-sm leading-6 text-slate-700">Paste the API Key from the Redmine account that should access these projects. OpenDispatch saves it to managed secret storage and never returns the key to the browser.</p>
            {!principalId && connectionId ? <button type="button" disabled={busy} onClick={()=>void createPrincipal()} className="mt-3 rounded-xl bg-indigo-700 px-4 py-2 text-sm font-black text-white disabled:opacity-50">Prepare secure connection</button> : null}
            {principalId ? <div className="mt-3 space-y-3"><FormField id="issue-redmine-api-key" label="Redmine API Key" help="Redmine → My account → API access key. Use the key for the account that can see the target projects." required><TextField id="issue-redmine-api-key" type="password" autoComplete="off" value={redmineApiKey} onChange={setRedmineApiKey} placeholder="Paste Redmine API Key" /></FormField><button type="button" disabled={busy||!redmineApiKey.trim()} onClick={()=>void saveRedmineApiKey()} className="w-full rounded-xl bg-amber-800 px-4 py-2 text-sm font-black text-white disabled:opacity-50">{credentialReady(credentials)?'Replace API Key & test connection':'Save API Key & test connection'}</button>{credentialReady(credentials)?<div className="rounded-xl border border-emerald-200 bg-white p-3 text-sm font-bold text-emerald-900">Authentication ready · API Key ••••{activeCredential(credentials)?.secretLast4 || 'saved'}</div>:null}</div> : null}
            <AdvancedSection title="Advanced · external secret reference" description="Only use this when the API Key is already managed outside OpenDispatch by Vault, an environment variable, or a mounted secret file."><div className="mt-3 space-y-3"><div className="grid gap-3 md:grid-cols-2"><FormField id="issue-secret-mode" label="Existing secret location"><SelectField id="issue-secret-mode" value={secretMode} onChange={(value)=>setSecretMode(value as SecretMode)} options={[{value:'VAULT',label:'HashiCorp Vault reference'},{value:'ENV',label:'Environment variable reference'},{value:'FILE',label:'Mounted secret file reference'}]} /></FormField><FormField id="issue-secret-location" label={secretMode==='VAULT'?'Vault mount/path':secretMode==='ENV'?'Environment variable':'Secret file path'} required><TextField id="issue-secret-location" value={secretLocation} onChange={setSecretLocation} placeholder={secretMode==='VAULT'?'secret/redmine/erp':secretMode==='ENV'?'REDMINE_ERP_API_KEY':'/run/secrets/redmine-api-key'} /></FormField></div>{secretMode==='VAULT'?<FormField id="issue-secret-field" label="Vault field"><TextField id="issue-secret-field" value={secretField} onChange={setSecretField} /></FormField>:null}<FormField id="issue-credential-id" label="Credential name" help="Optional technical identifier. Leave blank for the generated default."><TextField id="issue-credential-id" value={credentialId} onChange={setCredentialId} placeholder={principalId?`${safeId(principalId)}-api-token`:'credential-id'} /></FormField><button type="button" disabled={busy||!principalId||!secretLocation.trim()} onClick={()=>void saveCredentialAndTest()} className="w-full rounded-xl border border-amber-700 bg-white px-4 py-2 text-sm font-black text-amber-900 disabled:opacity-50">Use existing secret reference & test</button></div></AdvancedSection>
            {probe?<div className="mt-3 rounded-xl bg-white/80 p-3 text-xs leading-5 text-slate-700"><b>Redmine connection test:</b> {probe.providerResponseSummary}<div className="mt-1">{Object.entries(probe.capabilityResults??{}).map(([key,value])=><span key={key} className="mr-2 inline-block rounded-full bg-slate-100 px-2 py-1">{key}: {value}</span>)}</div></div>:null}
          </section>

          <section className="rounded-2xl border border-violet-200 bg-violet-50 p-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><h3 className="font-black text-violet-950">3. Choose Redmine project</h3><p className="mt-1 text-sm text-violet-900">Load the projects this Redmine credential can access, then choose where this work should create or update issues.</p></div><button type="button" disabled={busy||!connectionId||!principalId} onClick={()=>void discover()} className="rounded-xl bg-violet-700 px-4 py-2 text-sm font-black text-white disabled:opacity-50">Discover projects</button></div>
            <div className="mt-3 grid gap-3 md:grid-cols-2"><FormField id="issue-project" label="Project"><EntityPicker id="issue-project" value={projectId} onChange={setProjectId} disabled={!projectOptions.length} placeholder={projectOptions.length?'Select Redmine project':'Discover projects first'} options={projectOptions.map((value)=>({value:value.projectId,label:value.displayName || value.projectKey || value.projectId,description:value.projectKey ?? undefined}))} /></FormField><FormField id="issue-tracker" label="Tracker"><EntityPicker id="issue-tracker" value={trackerId} onChange={setTrackerId} disabled={!trackerOptions.length} placeholder={trackerOptions.length?'Select tracker':'Discover trackers first'} options={trackerOptions.map((value)=>({value:value.issueTypeId,label:value.displayName || value.issueTypeKey || value.issueTypeId,description:value.issueTypeKey ?? undefined}))} /></FormField></div>
          </section>

          {currentActive ? <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950"><div className="font-black">Issue Tracking runtime is ready</div><p className="mt-1 leading-6">{currentActive.externalProjectKey || currentActive.externalProjectId} · tracker {currentActive.externalIssueType || currentActive.externalTrackerId || 'default'} · Core confirms executable connector context and Redmine authentication. Live CREATE certification: {serverReadiness?.liveCreateCertificationStatus ?? 'NOT_CERTIFIED'}.</p></section>:null}
          {staleActive ? <section className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950"><div className="font-black">Issue Tracking needs repair</div><p className="mt-1 leading-6">A mapping is marked ACTIVE, but it is not runtime-ready. Status: {upper(staleActive.mappingStatus) || 'UNKNOWN'}; metadata snapshot/schema: {clean(staleActive.metadataSnapshotId) && clean(staleActive.metadataSchemaHash) ? 'present' : 'incomplete'}. Save & activate will validate a replacement mapping and retire the stale ACTIVE mapping.</p></section>:null}
          {currentMappingReady && !currentActive ? <section className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950"><div className="font-black">Connector is not runtime-ready</div><p className="mt-1 leading-6">The Project Mapping is ACTIVE + VALID, but the Redmine connection or credential is not currently ready. Restore an ACTIVE connection and a valid Service Account credential before Task Issue automation can run.</p></section>:null}

          <div className="border-t border-slate-200 pt-5">{activationBlocker && !busy ? <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-900"><b>Before activation:</b> {activationBlocker}</div> : null}<div className="flex flex-wrap items-center justify-between gap-3"><Link href="/settings/integrations" className="text-sm font-black text-slate-600 underline decoration-dotted">Advanced settings →</Link><div className="flex gap-2"><button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-black text-slate-700">Close</button><button type="button" disabled={busy||Boolean(currentActive)||Boolean(activationBlocker)} title={currentActive?'Issue Tracking is already runtime-ready for this work context.':activationBlocker || (staleActive ? 'Validate a replacement mapping and retire the stale ACTIVE mapping.' : 'Validate the selected Redmine project and activate Issue Tracking.')} onClick={()=>void saveAndActivate()} className="rounded-xl bg-emerald-700 px-5 py-2 text-sm font-black text-white disabled:opacity-50">{busy?'Working…':currentActive?'Runtime ready':staleActive?'Repair & activate':'Save & activate'}</button></div></div></div>
          <p className="text-xs leading-5 text-slate-500">READ / CREATE / COMMENT / UPDATE are not separately granted here. The same technical Service Account is used by the canonical connector, and Redmine decides which operations are actually permitted. Provider 403 is shown as a business permission denial, not a broken OpenDispatch connection.</p>
        </div>
      </div>
    </div>
  );
}
