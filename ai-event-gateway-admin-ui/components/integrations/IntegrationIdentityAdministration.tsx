'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { ProjectMappingGovernancePanel } from '@/components/integrations/ProjectMappingGovernancePanel';
import { CredentialMetadataImpactPanel } from '@/components/phase7d/CredentialMetadataImpactPanel';
import { IssueConnectionOperationalSummary } from '@/components/phase7d/IssueConnectionOperationalSummary';
import { ResourceGovernancePanel } from '@/components/resource-access/ResourceGovernancePanel';
import {
  addCredential,
  listConnections,
  listCredentials,
  listPrincipals,
  saveConnection,
  savePrincipal,
  type IntegrationConnection,
  type IntegrationCredentialMetadata,
  type IntegrationPrincipal,
} from '@/lib/api/domains/integrationIdentityApi';

const blankConnection: IntegrationConnection = {
  connectionId: '',
  providerType: 'REDMINE',
  connectionName: '',
  baseUrl: '',
  deploymentType: 'SELF_HOSTED',
  status: 'DRAFT',
  timeoutMs: 10000,
  enabled: false,
};
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

function maskReference(reference?: string | null) {
  if (!reference) return '—';
  const index = reference.lastIndexOf('/');
  return index < 0 ? '••••' : `${reference.slice(0, index + 1)}••••${reference.includes('#') ? `#${reference.split('#').at(-1)}` : ''}`;
}
function errorText(error: unknown) { return error instanceof Error ? error.message : 'The operation failed.'; }
function generatedId(prefix: string, value: string) { const code=value.trim().toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'').slice(0,64); return `${prefix}-${code || 'default'}`; }

export function IntegrationIdentityAdministration() {
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [selectedConnectionId, setSelectedConnectionId] = useState('');
  const [connectionDraft, setConnectionDraft] = useState<IntegrationConnection>(blankConnection);
  const [serviceAccounts, setServiceAccounts] = useState<IntegrationPrincipal[]>([]);
  const [selectedServiceAccountId, setSelectedServiceAccountId] = useState('');
  const [serviceAccountDraft, setServiceAccountDraft] = useState<IntegrationPrincipal>(blankServiceAccount);
  const [credentials, setCredentials] = useState<IntegrationCredentialMetadata[]>([]);
  const [credentialDraft, setCredentialDraft] = useState<IntegrationCredentialMetadata>(blankCredential());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const selectedConnection = connections.find((value) => value.connectionId === selectedConnectionId);
  const selectedServiceAccount = serviceAccounts.find((value) => value.principalId === selectedServiceAccountId);

  const refreshConnections = useCallback(async (preferred?: string) => {
    const values = (await listConnections()).filter((value) => value.providerType === 'REDMINE');
    setConnections(values);
    setSelectedConnectionId(preferred ?? values[0]?.connectionId ?? '');
  }, []);

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
    refreshConnections().catch((error) => setMessage(errorText(error))).finally(() => setBusy(false));
  }, [refreshConnections]);

  useEffect(() => {
    if (!selectedConnectionId) { setConnectionDraft(blankConnection); setServiceAccounts([]); return; }
    setConnectionDraft(selectedConnection ?? { ...blankConnection, connectionId: selectedConnectionId });
    setBusy(true);
    refreshServiceAccounts(selectedConnectionId).catch((error) => setMessage(errorText(error))).finally(() => setBusy(false));
  }, [selectedConnectionId, selectedConnection, refreshServiceAccounts]);

  useEffect(() => {
    if (!selectedServiceAccountId) { setServiceAccountDraft({ ...blankServiceAccount, connectionId: selectedConnectionId }); setCredentials([]); return; }
    setServiceAccountDraft(selectedServiceAccount ?? { ...blankServiceAccount, connectionId: selectedConnectionId, principalId: selectedServiceAccountId });
    setBusy(true);
    refreshCredentials(selectedServiceAccountId).catch((error) => setMessage(errorText(error))).finally(() => setBusy(false));
  }, [selectedServiceAccountId, selectedServiceAccount, selectedConnectionId, refreshCredentials]);

  async function perform(action: () => Promise<void>, success: string) {
    setBusy(true); setMessage('');
    try { await action(); setMessage(success); } catch (error) { setMessage(errorText(error)); } finally { setBusy(false); }
  }

  return <div className="space-y-5">
    <section className="rounded-2xl border border-blue-200 bg-blue-50 p-5">
      <h2 className="text-lg font-black text-blue-950">Redmine Connector Configuration</h2>
      <p className="mt-2 text-sm leading-6 text-blue-900">OpenDispatch manages connection routing, one technical Service Account per canonical connector context, credential references and operational health. Redmine manages project membership, roles, workflow and issue permissions.</p>
      <div className="mt-3 rounded-xl border border-blue-200 bg-white/80 p-3 text-sm text-blue-900"><b>Recommended setup:</b> configure Issue Tracking from the Source System, Dispatch Flow, or Agent Overview using the nearby popup. This page remains the advanced administration surface for lifecycle, versions and diagnostics.</div>
    </section>

    {message ? <div role="status" className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700">{message}</div> : null}

    <IssueConnectionOperationalSummary connection={selectedConnection} principal={selectedServiceAccount} credentials={credentials} probe={null}/>
    {selectedConnectionId ? <ResourceGovernancePanel resourceType="ISSUE_CONNECTION" resourceId={selectedConnectionId} permissionCode="integration.issue.connection.read" requestedVisibility="STANDARD" compact/> : null}

    <div className="grid gap-5 xl:grid-cols-2">
      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <div className="flex items-center justify-between gap-3"><h3 className="font-black text-slate-950">Redmine Connection</h3><button type="button" onClick={() => { setSelectedConnectionId(''); setConnectionDraft(blankConnection); }} className="rounded-lg border px-3 py-2 text-sm font-bold">New</button></div>
        <label className="mt-4 block text-sm font-bold text-slate-700">Connection<select value={selectedConnectionId} onChange={(event) => setSelectedConnectionId(event.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2"><option value="">New Redmine connection</option>{connections.map((value) => <option key={value.connectionId} value={value.connectionId}>{value.connectionName}</option>)}</select></label>
        <div className="mt-4 grid gap-3">
          <Field label="Display name" value={connectionDraft.connectionName} onChange={(value) => setConnectionDraft({ ...connectionDraft, connectionName: value })}/>
          <Field label="Redmine base URL" value={connectionDraft.baseUrl} onChange={(value) => setConnectionDraft({ ...connectionDraft, baseUrl: value })}/>
          <div className="rounded-xl bg-slate-50 px-3 py-2 text-sm"><b>Provider:</b> REDMINE <span className="text-slate-500">(fixed for this release)</span></div>
          <label className="flex items-center gap-2 text-sm font-bold"><input type="checkbox" checked={Boolean(connectionDraft.enabled)} onChange={(event) => setConnectionDraft({ ...connectionDraft, enabled: event.target.checked })}/> Enable connection</label>
          <details className="rounded-xl border border-slate-200 bg-white p-3"><summary className="cursor-pointer text-sm font-black text-slate-700">Advanced connection identity</summary><div className="mt-3 grid gap-3"><Field label="Connection ID" value={connectionDraft.connectionId} onChange={(value) => setConnectionDraft({ ...connectionDraft, connectionId: value, providerType: 'REDMINE' })}/><Field label="Timeout (ms)" value={String(connectionDraft.timeoutMs ?? 10000)} onChange={(value) => setConnectionDraft({ ...connectionDraft, timeoutMs: Number(value) || 10000 })}/></div></details>
          <button disabled={busy || !connectionDraft.connectionName.trim() || !connectionDraft.baseUrl} type="button" onClick={() => perform(async () => { const connectionId=connectionDraft.connectionId.trim() || generatedId('redmine',connectionDraft.connectionName); const saved = await saveConnection({ ...connectionDraft, connectionId, providerType: 'REDMINE' }); await refreshConnections(saved.connectionId); }, 'Redmine connection saved.')} className="rounded-xl bg-blue-700 px-4 py-2 font-black text-white disabled:opacity-50">Save Connection</button>
        </div>
      </section>

      <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5">
        <h3 className="font-black text-indigo-950">Technical Service Account</h3>
        <p className="mt-1 text-sm leading-6 text-indigo-900">This is the Redmine identity used by the connector. Its actual issue permissions are configured in Redmine, not OpenDispatch.</p>
        <label className="mt-4 block text-sm font-bold text-slate-700">Service Account<select disabled={!selectedConnectionId} value={selectedServiceAccountId} onChange={(event) => setSelectedServiceAccountId(event.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2"><option value="">New Service Account</option>{serviceAccounts.map((value) => <option key={value.principalId} value={value.principalId}>{value.principalName}</option>)}</select></label>
        <div className="mt-4 grid gap-3">
          <Field label="Display name" value={serviceAccountDraft.principalName} onChange={(value) => setServiceAccountDraft({ ...serviceAccountDraft, principalName: value })}/>
          <Field label="External Redmine user identifier" value={serviceAccountDraft.externalPrincipalIdentifier ?? ''} onChange={(value) => setServiceAccountDraft({ ...serviceAccountDraft, externalPrincipalIdentifier: value })}/>
          <details className="rounded-xl border border-indigo-200 bg-white p-3"><summary className="cursor-pointer text-sm font-black text-indigo-800">Advanced Service Account identity</summary><div className="mt-3"><Field label="Service Account ID" value={serviceAccountDraft.principalId} onChange={(value) => setServiceAccountDraft({ ...serviceAccountDraft, principalId: value, connectionId: selectedConnectionId, principalType: 'SERVICE_ACCOUNT' })}/></div></details>
          <button disabled={busy || !selectedConnectionId || !serviceAccountDraft.principalName.trim()} type="button" onClick={() => perform(async () => { const principalId=serviceAccountDraft.principalId.trim() || generatedId(generatedId('svc',selectedConnectionId),serviceAccountDraft.principalName); const saved = await savePrincipal(selectedConnectionId, { ...serviceAccountDraft, principalId, connectionId: selectedConnectionId, principalType: 'SERVICE_ACCOUNT' }); await refreshServiceAccounts(selectedConnectionId, saved.principalId); }, 'Technical Service Account saved.')} className="rounded-xl bg-indigo-700 px-4 py-2 font-black text-white disabled:opacity-50">Save Service Account</button>
        </div>
      </section>
    </div>

    {selectedServiceAccountId ? <section className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
      <h3 className="font-black text-amber-950">Credential References · Advanced</h3>
      <p className="mt-1 text-sm text-amber-900">Only secret references and lifecycle metadata are stored here. OpenDispatch never displays the Redmine API key.</p>
      <p className="mt-2 rounded-xl bg-white/70 px-3 py-2 text-xs leading-5 text-amber-900">Rotation is fail-safe: add the next credential as PENDING_VALIDATION, validate provider authentication/connectivity, cut over to ACTIVE while the old credential enters GRACE_PERIOD, then revoke the old credential after the grace window. Issue permissions remain managed in Redmine.</p>
      <div className="mt-3 space-y-2">{credentials.length ? credentials.map((value) => <div key={value.credentialId} className="rounded-xl border border-amber-200 bg-white p-3"><div className="font-black text-slate-900">{value.credentialId}</div><div className="mt-1 text-xs text-slate-600">{maskReference(value.secretRef)} · version {value.secretVersion || 'current'} · {value.status}</div><div className="mt-1 text-xs text-slate-500">Last used: {value.lastUsedAt ? new Date(value.lastUsedAt).toLocaleString() : 'Not observed yet'}</div></div>) : <p className="text-sm text-amber-900">No credential references.</p>}</div>
      <div className="mt-4"><CredentialMetadataImpactPanel credentials={credentials} principalCount={serviceAccounts.length}/></div>
      <details className="mt-4 rounded-xl border border-amber-200 bg-white/80 p-3"><summary className="cursor-pointer text-sm font-black text-amber-900">Add raw secret reference</summary><div className="mt-3 grid gap-3 md:grid-cols-3">
        <Field label="Credential ID" value={credentialDraft.credentialId} onChange={(value) => setCredentialDraft({ ...credentialDraft, credentialId: value, principalId: selectedServiceAccountId })}/>
        <Field label="Secret reference" value={credentialDraft.secretRef ?? ''} onChange={(value) => setCredentialDraft({ ...credentialDraft, secretRef: value })} placeholder="vault://mount/path#field"/>
        <Field label="Secret version" value={credentialDraft.secretVersion ?? ''} onChange={(value) => setCredentialDraft({ ...credentialDraft, secretVersion: value })}/>
      </div>
      <button disabled={busy || !credentialDraft.credentialId || !credentialDraft.secretRef} type="button" onClick={() => perform(async () => { await addCredential(selectedServiceAccountId, { ...credentialDraft, principalId: selectedServiceAccountId }); await refreshCredentials(selectedServiceAccountId); }, 'Credential reference saved.')} className="mt-3 rounded-xl bg-amber-800 px-4 py-2 font-black text-white disabled:opacity-50">Add Credential Reference</button></details>
    </section> : null}

    <ProjectMappingGovernancePanel connectionId={selectedConnectionId}/>

    <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><h3 className="font-black text-emerald-950">Provider operations</h3><p className="mt-1 text-sm leading-6 text-emerald-900">Task execution shows Redmine success, permission denial, authentication failure, rate limiting and availability evidence. Issue permissions themselves are administered in Redmine.</p></div><Link href="/operations/integration-sync" className="shrink-0 rounded-xl bg-emerald-800 px-4 py-2 text-sm font-black text-white hover:bg-emerald-900">Open integration operations</Link></div>
    </section>
  </div>;
}

function Field({label,value,onChange,placeholder}:{label:string;value:string;onChange:(value:string)=>void;placeholder?:string}){
  return <label className="text-sm font-bold text-slate-700">{label}<input value={value} onChange={(event)=>onChange(event.target.value)} placeholder={placeholder} className="mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-normal"/></label>;
}
