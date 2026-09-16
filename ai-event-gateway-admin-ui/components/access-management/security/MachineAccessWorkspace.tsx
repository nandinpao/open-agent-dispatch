'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import { PRODUCT_ROUTES } from '@/lib/navigation/productRoutes';
import type {
  CredentialApiProduct,
  CredentialGovernanceCatalog,
  Department,
  IssuedServiceAccountCredential,
  ResponsibilityTemplate,
  ServiceAccount,
  ServiceAccountCredential,
  TokenSummary,
  User,
} from '@/lib/iam/types';
import {
  AuditReasonSelector,
  FieldLabel,
  HumanStatus,
  PopupMultiSelectField,
  AsyncSearchSelectField,
  SelectField,
  humanizePermission,
  isAuditReasonValid,
  type SelectOption,
} from '../shared/beginnerUi';
import { WorkspaceEmpty, WorkspaceError, WorkspaceLoading, WorkspaceModal } from '../shared/workspaceUi';

type NetworkProfile = 'PRIVATE' | 'VPN' | 'CUSTOM';
type CreateState = {
  name: string;
  description: string;
  ownerUserId: string;
  ownerDepartmentId: string;
  roleId: string;
  products: string[];
  machineScopes: string[];
  sourceSystems: string[];
  networkProfile: NetworkProfile;
  customCidr: string;
  credentialMaxTtlSeconds: string;
  maxActiveCredentials: string;
  rateLimitPerMinute: string;
  reviewDays: string;
  auditReason: string;
};
type EditState = {
  roleId: string;
  products: string[];
  machineScopes: string[];
  sourceSystems: string[];
  networkProfile: NetworkProfile;
  customCidr: string;
  credentialMaxTtlSeconds: string;
  maxActiveCredentials: string;
  auditReason: string;
};

type CredentialAction = { kind: 'ISSUE'; account: ServiceAccount } | { kind: 'ROTATE'; account: ServiceAccount; credential: ServiceAccountCredential } | null;

const CREATE_INITIAL: CreateState = {
  name: '',
  description: '',
  ownerUserId: '',
  ownerDepartmentId: '',
  roleId: '',
  products: [],
  machineScopes: [],
  sourceSystems: [],
  networkProfile: 'PRIVATE',
  customCidr: '',
  credentialMaxTtlSeconds: String(180 * 86400),
  maxActiveCredentials: '2',
  rateLimitPerMinute: '300',
  reviewDays: '90',
  auditReason: 'Service integration setup',
};

export function MachineAccessWorkspace({ tenantId, canManage, onChanged }: Readonly<{ tenantId: string; canManage: boolean; onChanged: () => void }>) {
  const [accounts, setAccounts] = useState<ServiceAccount[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [templates, setTemplates] = useState<ResponsibilityTemplate[]>([]);
  const [catalog, setCatalog] = useState<CredentialGovernanceCatalog | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [credentials, setCredentials] = useState<ServiceAccountCredential[]>([]);
  const [legacyTokens, setLegacyTokens] = useState<TokenSummary[]>([]);
  const [credentialsLoading, setCredentialsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [createState, setCreateState] = useState<CreateState>(CREATE_INITIAL);
  const [editState, setEditState] = useState<EditState | null>(null);
  const [credentialAction, setCredentialAction] = useState<CredentialAction>(null);
  const [credentialName, setCredentialName] = useState('Primary integration credential');
  const [credentialTtl, setCredentialTtl] = useState(String(90 * 86400));
  const [rotationOverlap, setRotationOverlap] = useState('300');
  const [credentialReason, setCredentialReason] = useState('Service integration setup');
  const [revokeTarget, setRevokeTarget] = useState<ServiceAccountCredential | null>(null);
  const [legacyRevokeTarget, setLegacyRevokeTarget] = useState<TokenSummary | null>(null);
  const [revokeReason, setRevokeReason] = useState('Security remediation');
  const [issued, setIssued] = useState<IssuedServiceAccountCredential | null>(null);
  const [secretAcknowledged, setSecretAcknowledged] = useState(false);

  // Entity search results are Tenant-scoped. A workspace switch must invalidate cached choices
  // before another create/edit request can reuse an identifier from the previous Tenant.
  useEffect(() => {
    setUsers([]);
    setDepartments([]);
    setTemplates([]);
    setSelectedId('');
    setCreateOpen(false);
    setCreateState(CREATE_INITIAL);
    setEditState(null);
    setCredentialAction(null);
  }, [tenantId]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [accountPage, templatePage, governance] = await Promise.all([
        accessManagementApi.serviceAccounts(100),
        accessManagementApi.responsibilityTemplates(tenantId, 0, 25, '', 'ACTIVE'),
        accessManagementApi.credentialGovernanceCatalog(tenantId),
      ]);
      const nextAccounts = accountPage.items.filter((item) => item.tenantId === tenantId);
      setAccounts(nextAccounts);
      setTemplates(templatePage.items.filter((item) => item.status === 'ACTIVE'));
      setCatalog(governance);
      setSelectedId((current) => nextAccounts.some((item) => item.serviceAccountId === current) ? current : nextAccounts[0]?.serviceAccountId ?? '');
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load Machine Access.'));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void load(); }, [load]);

  const selected = accounts.find((account) => account.serviceAccountId === selectedId) ?? null;

  useEffect(() => {
    if (!selected) return;
    let cancelled=false;
    void Promise.allSettled([
      accessManagementApi.tenantUser(tenantId, selected.ownerUserId),
      accessManagementApi.department(tenantId, selected.ownerDepartmentId),
    ]).then(([userResult,departmentResult])=>{
      if(cancelled)return;
      if(userResult.status==='fulfilled')setUsers(current=>mergeBy(current,userResult.value,'userId'));
      if(departmentResult.status==='fulfilled')setDepartments(current=>mergeBy(current,departmentResult.value,'departmentId'));
    });
    return()=>{cancelled=true;};
  },[selected,tenantId]);

  const loadCredentials = useCallback(async (accountId: string) => {
    if (!accountId) { setCredentials([]); setLegacyTokens([]); return; }
    setCredentialsLoading(true);
    try {
      const [page, legacyPage] = await Promise.all([
        accessManagementApi.serviceAccountCredentials(accountId, 100),
        accessManagementApi.tokens(100, '', accountId),
      ]);
      setCredentials(page.items);
      setLegacyTokens(legacyPage.items);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load client credentials for this Service Account.'));
      setCredentials([]);
      setLegacyTokens([]);
    } finally {
      setCredentialsLoading(false);
    }
  }, []);

  useEffect(() => { void loadCredentials(selectedId); }, [loadCredentials, selectedId]);

  const loadUsers=useCallback(async(query:string,cursor?:string)=>{
    const page=await accessManagementApi.tenantUsers(tenantId,25,cursor||'',query,'ACTIVE','ACTIVE');
    setUsers(current=>mergeManyBy(current,page.items,'userId'));
    return {options:page.items.map(user=>({value:user.userId,label:user.displayName,description:user.email||user.username})),nextCursor:page.nextCursor||undefined};
  },[tenantId]);
  const loadDepartments=useCallback(async(query:string,cursor?:string)=>{
    const pageNo=Number(cursor||'0');
    const page=await accessManagementApi.departments(tenantId,pageNo,25,query,'ACTIVE');
    setDepartments(current=>mergeManyBy(current,page.items,'departmentId'));
    return {options:page.items.map(department=>({value:department.departmentId,label:department.name,description:department.code})),nextCursor:page.hasMore?String(pageNo+1):undefined};
  },[tenantId]);
  const loadResponsibilities=useCallback(async(query:string,cursor?:string)=>{
    const pageNo=Number(cursor||'0');
    const page=await accessManagementApi.responsibilityTemplates(tenantId,pageNo,25,query,'ACTIVE');
    const eligible=page.items.filter(template=>template.allowedPrincipalTypes?.includes('SERVICE_ACCOUNT'));
    setTemplates(current=>mergeManyBy(current,eligible,'roleId'));
    return {options:eligible.map(template=>({value:template.roleId,label:template.roleName,description:template.description||`${template.riskLevel} risk`})),nextCursor:page.hasMore?String(pageNo+1):undefined};
  },[tenantId]);

  const userOptions = useMemo<SelectOption[]>(() => users.map((user) => ({ value: user.userId, label: user.displayName, description: user.email || user.username })), [users]);
  const departmentOptions = useMemo<SelectOption[]>(() => departments.map((department) => ({ value: department.departmentId, label: department.name, description: department.code })), [departments]);
  const machineTemplates = useMemo(() => templates.filter((template) => template.allowedPrincipalTypes?.includes('SERVICE_ACCOUNT')), [templates]);
  const responsibilityOptions = useMemo<SelectOption[]>(() => machineTemplates.map((template) => ({ value: template.roleId, label: template.roleName, description: template.description || `${template.riskLevel} risk` })), [machineTemplates]);
  const productOptions = useMemo<SelectOption[]>(() => (catalog?.apiProducts ?? []).map((product) => ({ value: product.productCode, label: product.displayName, description: product.description })), [catalog]);
  const scopeOptions = useMemo<SelectOption[]>(() => (catalog?.machineScopes ?? []).map((scope) => ({ value: scope.scopeCode, label: scope.displayName, description: scope.description })), [catalog]);
  const sourceSystemOptions = useMemo<SelectOption[]>(() => (catalog?.sourceSystems ?? []).map((source) => ({ value: source.sourceSystemId, label: source.displayName, description: source.description || source.sourceSystemId })), [catalog]);

  function setCreateProducts(products: string[]) {
    const recommended = recommendedScopes(products, catalog);
    setCreateState((current) => ({ ...current, products, machineScopes: Array.from(new Set([...current.machineScopes, ...recommended])) }));
  }

  async function createAccount() {
    const template = machineTemplates.find((item) => item.roleId === createState.roleId);
    const products = selectedProducts(createState.products, catalog);
    const cidrs = networkCidrs(createState.networkProfile, createState.customCidr);
    if (!template || !createState.name.trim() || !createState.ownerUserId || !createState.ownerDepartmentId || !products.length || !createState.machineScopes.length || (createState.machineScopes.includes('events.intake') && !createState.sourceSystems.length) || !cidrs.length || !isAuditReasonValid(createState.auditReason, 'ELEVATED')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const created = await accessManagementApi.createServiceAccount({
        name: createState.name.trim(),
        description: createState.description.trim(),
        ownerUserId: createState.ownerUserId,
        ownerDepartmentId: createState.ownerDepartmentId,
        responsibilityRoleId: template.roleId,
        responsibilityScopeType: 'DEPARTMENT',
        responsibilityScopeId: createState.ownerDepartmentId,
        restrictions: {
          permissions: template.capabilityCodes,
          audiences: unique(products.flatMap((item) => item.audiences)),
          apiPrefixes: unique(products.flatMap((item) => item.apiPrefixes)),
          allowedCidrs: cidrs,
        },
        machineScopes: createState.machineScopes,
        allowedSourceSystems: createState.sourceSystems,
        tokenMaxTtlSeconds: 30 * 86400,
        maxActiveTokens: 1,
        credentialMaxTtlSeconds: Number(createState.credentialMaxTtlSeconds),
        maxActiveCredentials: Number(createState.maxActiveCredentials),
        rateLimitPerMinute: Number(createState.rateLimitPerMinute),
        nextReviewAt: dateAfter(Number(createState.reviewDays)),
      }, createState.auditReason);
      setCreateOpen(false);
      setCreateState(CREATE_INITIAL);
      setNotice('Service Account created. Issue a client credential when the integration is ready to connect.');
      await load();
      setSelectedId(created.serviceAccountId);
      onChanged();
    } catch (cause) {
      setError(formatIamError(cause, 'The Service Account could not be created.'));
    } finally { setBusy(false); }
  }

  function openEdit(account: ServiceAccount) {
    setEditState({
      roleId: account.responsibilityRoleId || '',
      products: matchingProducts(account, catalog).map((item) => item.productCode),
      machineScopes: account.machineScopes,
      sourceSystems: account.allowedSourceSystems,
      networkProfile: inferNetworkProfile(account.cidrs),
      customCidr: inferNetworkProfile(account.cidrs) === 'CUSTOM' ? account.cidrs[0] ?? '' : '',
      credentialMaxTtlSeconds: String(account.credentialMaxTtlSeconds),
      maxActiveCredentials: String(account.maxActiveCredentials),
      auditReason: 'Approved lifecycle change',
    });
  }

  async function saveBoundary() {
    if (!selected || !editState) return;
    const template = machineTemplates.find((item) => item.roleId === editState.roleId);
    const products = selectedProducts(editState.products, catalog);
    const cidrs = networkCidrs(editState.networkProfile, editState.customCidr);
    if (!template || !products.length || !editState.machineScopes.length || (editState.machineScopes.includes('events.intake') && !editState.sourceSystems.length) || !cidrs.length || !isAuditReasonValid(editState.auditReason, 'ELEVATED')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.updateServiceAccountMachineBoundary(selected.serviceAccountId, {
        responsibilityRoleId: template.roleId,
        responsibilityScopeType: 'DEPARTMENT',
        responsibilityScopeId: selected.ownerDepartmentId,
        restrictions: {
          permissions: template.capabilityCodes,
          audiences: unique(products.flatMap((item) => item.audiences)),
          apiPrefixes: unique(products.flatMap((item) => item.apiPrefixes)),
          allowedCidrs: cidrs,
        },
        machineScopes: editState.machineScopes,
        allowedSourceSystems: editState.sourceSystems,
        credentialMaxTtlSeconds: Number(editState.credentialMaxTtlSeconds),
        maxActiveCredentials: Number(editState.maxActiveCredentials),
        expectedVersion: selected.version,
      }, editState.auditReason);
      setEditState(null);
      setNotice('Machine access boundary updated. Current resource authorization immediately uses the reduced boundary.');
      await load();
      onChanged();
    } catch (cause) { setError(formatIamError(cause, 'The Machine Access boundary could not be updated.')); }
    finally { setBusy(false); }
  }

  function openIssue(account: ServiceAccount) {
    setCredentialAction({ kind: 'ISSUE', account });
    setCredentialName('Primary integration credential');
    setCredentialTtl(String(Math.min(account.credentialMaxTtlSeconds, 90 * 86400)));
    setCredentialReason('Service integration setup');
  }

  function openRotate(account: ServiceAccount, credential: ServiceAccountCredential) {
    setCredentialAction({ kind: 'ROTATE', account, credential });
    setCredentialName(credential.name);
    setCredentialTtl(String(Math.min(account.credentialMaxTtlSeconds, 90 * 86400)));
    setRotationOverlap('300');
    setCredentialReason('Approved lifecycle change');
  }

  async function submitCredentialAction() {
    if (!credentialAction || !isAuditReasonValid(credentialReason, 'HIGH_RISK')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const next = credentialAction.kind === 'ISSUE'
        ? await accessManagementApi.issueServiceAccountCredential(credentialAction.account.serviceAccountId, { name: credentialName.trim(), ttlSeconds: Number(credentialTtl) }, credentialReason)
        : await accessManagementApi.rotateServiceAccountCredential(credentialAction.account.serviceAccountId, credentialAction.credential.credentialId, Number(rotationOverlap), credentialReason);
      setIssued(next);
      setSecretAcknowledged(false);
      setCredentialAction(null);
      await loadCredentials(next.serviceAccountId);
      onChanged();
    } catch (cause) {
      setError(formatIamError(cause, credentialAction.kind === 'ISSUE' ? 'The client credential could not be issued.' : 'The client credential could not be rotated.'));
    } finally { setBusy(false); }
  }

  async function revokeCredential() {
    if (!selected || !revokeTarget || !isAuditReasonValid(revokeReason, 'HIGH_RISK')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.revokeServiceAccountCredential(selected.serviceAccountId, revokeTarget.credentialId, revokeReason);
      setRevokeTarget(null);
      setRevokeReason('Security remediation');
      setNotice('Client credential revoked. Existing short-lived access tokens are invalidated by the Service Account security epoch.');
      await loadCredentials(selected.serviceAccountId);
      onChanged();
    } catch (cause) { setError(formatIamError(cause, 'The client credential could not be revoked.')); }
    finally { setBusy(false); }
  }


  async function revokeLegacyToken() {
    if (!selected || !legacyRevokeTarget || !isAuditReasonValid(revokeReason, 'HIGH_RISK')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.revokeToken(legacyRevokeTarget.tokenId, revokeReason);
      setLegacyRevokeTarget(null);
      setRevokeReason('Security remediation');
      setNotice('Legacy opaque token revoked. New integrations should use client credentials and short-lived Machine JWTs.');
      await loadCredentials(selected.serviceAccountId);
      onChanged();
    } catch (cause) { setError(formatIamError(cause, 'The legacy token could not be revoked.')); }
    finally { setBusy(false); }
  }

  if (loading && !catalog) return <WorkspaceLoading title="Loading Machine Access" description="Loading Service Accounts, owners and governed access choices." />;

  return <div className="space-y-5">
    {error ? <WorkspaceError message={error} onRetry={() => { void load(); }} /> : null}
    {notice ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{notice}</div> : null}

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Machine Access</p>
          <h2 className="mt-1 text-xl font-black text-slate-950">Service Accounts and client credentials</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Give ERP, MES and other systems only the business operations and Source Systems they need. OpenDispatch derives technical audiences and API prefixes from the selections below.</p>
        </div>
        {canManage ? <button type="button" onClick={() => setCreateOpen(true)} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Create Service Account</button> : null}
      </div>
      <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950"><b className="block">New to Machine Access?</b>Start with one Service Account per external system or integration purpose. Use the nearby selectors; technical values remain under Advanced details only.</div>
      <div className="mt-3 flex flex-col gap-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 sm:flex-row sm:items-center sm:justify-between"><div><b className="text-slate-900">Managing an Agent identity?</b><p className="mt-1 text-xs leading-5 text-slate-500">Agent credentials, capabilities and runtime readiness stay with the Agent record so the same identity is not configured twice.</p></div><Link href={PRODUCT_ROUTES.agents} className="shrink-0 rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-black text-blue-700">Open Agents →</Link></div>
    </section>

    {!accounts.length ? <WorkspaceEmpty title="No Service Accounts yet" description="Create a Service Account for a non-human integration such as ERP, MES, BPM or an external automation." nextAction="Choose an owner, responsibility, API product and Source System. OpenDispatch derives the technical boundary for you." action={canManage ? <button type="button" onClick={() => setCreateOpen(true)} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Create Service Account</button> : null} /> : (
      <div className="grid gap-5 xl:grid-cols-[minmax(18rem,0.75fr)_minmax(0,1.6fr)]">
        <section className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="px-1 text-base font-black text-slate-950">Integrations</h3>
          <p className="mt-1 px-1 text-xs leading-5 text-slate-500">Select one to manage its access and credentials without leaving this page.</p>
          <div className="mt-3 space-y-2">{accounts.map((account) => <button type="button" key={account.serviceAccountId} onClick={() => setSelectedId(account.serviceAccountId)} className={`block w-full rounded-2xl border p-4 text-left ${selectedId === account.serviceAccountId ? 'border-blue-400 bg-blue-50' : 'border-slate-200 hover:bg-slate-50'}`}><div className="flex items-start justify-between gap-3"><div className="min-w-0"><span className="block truncate text-sm font-black text-slate-950">{account.name}</span><span className="mt-1 block truncate text-xs text-slate-500">{account.description || 'Integration identity'}</span></div><HumanStatus value={account.status}/></div><div className="mt-3 flex flex-wrap gap-1.5">{account.allowedSourceSystems.slice(0, 3).map((id) => <span key={id} className="rounded-full bg-white px-2 py-1 text-xs font-bold text-slate-600">{sourceName(id, catalog)}</span>)}{account.allowedSourceSystems.length > 3 ? <span className="rounded-full bg-white px-2 py-1 text-xs font-bold text-slate-500">+{account.allowedSourceSystems.length - 3}</span> : null}</div></button>)}</div>
        </section>

        {selected ? <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"><div><div className="flex flex-wrap items-center gap-2"><h3 className="text-xl font-black text-slate-950">{selected.name}</h3><HumanStatus value={selected.status}/></div><p className="mt-2 text-sm leading-6 text-slate-600">{selected.description || 'No business purpose was recorded.'}</p><p className="mt-2 text-xs text-slate-500">Owned by <b>{userName(selected.ownerUserId, users)}</b> · <b>{departmentName(selected.ownerDepartmentId, departments)}</b></p></div>{canManage ? <div className="flex flex-wrap gap-2"><button type="button" onClick={() => openEdit(selected)} className="rounded-xl border border-blue-300 px-3 py-2 text-sm font-black text-blue-700">Edit access</button><button type="button" onClick={() => openIssue(selected)} className="rounded-xl bg-blue-700 px-3 py-2 text-sm font-black text-white">Issue client credential</button></div> : null}</div>

          <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <SummaryCard label="Can do" value={selected.machineScopes.map((scope) => scopeName(scope, catalog)).join(', ') || 'No machine operation'} />
            <SummaryCard label="Sends as" value={selected.allowedSourceSystems.map((id) => sourceName(id, catalog)).join(', ') || 'No Source System selected'} />
            <SummaryCard label="Uses" value={matchingProducts(selected, catalog).map((product) => product.displayName).join(', ') || 'Custom API boundary'} />
            <SummaryCard label="Review" value={formatDate(selected.nextReviewAt)} />
          </div>

          <div className="mt-5 grid gap-5 lg:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 p-4"><h4 className="text-sm font-black text-slate-950">Access boundary</h4><dl className="mt-3 space-y-3 text-sm"><InfoRow label="Responsibility" value={templates.find((item) => item.roleId === selected.responsibilityRoleId)?.roleName ?? (selected.responsibilityRoleId ? selected.responsibilityRoleId : 'Migration required')} /><InfoRow label="Source Systems" value={selected.allowedSourceSystems.map((id) => sourceName(id, catalog)).join(', ') || 'None'} /><InfoRow label="Network" value={humanNetwork(selected.cidrs)} /><InfoRow label="Rate limit" value={`${selected.rateLimitPerMinute.toLocaleString()} requests / minute`} /></dl><details className="mt-4 rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-600"><summary className="cursor-pointer font-black text-slate-700">Advanced technical details</summary><p className="mt-2"><b>Audiences:</b> {selected.audiences.join(', ') || 'None'}</p><p><b>API prefixes:</b> {selected.apiPrefixes.join(', ') || 'None'}</p><p><b>CIDRs:</b> {selected.cidrs.join(', ') || 'None'}</p><p><b>Permission ceiling:</b> {selected.permissions.map(humanizePermission).join(', ') || 'None'}</p></details></div>
            <div className="rounded-2xl border border-slate-200 p-4"><div className="flex items-start justify-between gap-3"><div><h4 className="text-sm font-black text-slate-950">Client credentials</h4><p className="mt-1 text-xs leading-5 text-slate-500">Use client ID + client secret only to obtain short-lived access tokens. Secrets are shown once.</p></div>{canManage ? <button type="button" onClick={() => openIssue(selected)} className="rounded-lg bg-slate-950 px-3 py-2 text-xs font-black text-white">Issue</button> : null}</div>{credentialsLoading ? <p className="mt-4 text-sm text-slate-500">Loading credentials…</p> : !credentials.length ? <p className="mt-4 rounded-xl bg-slate-50 p-3 text-sm text-slate-600">No client credential has been issued yet.</p> : <div className="mt-3 space-y-2">{credentials.map((credential) => <article key={credential.credentialId} className="rounded-xl border border-slate-200 p-3"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-sm font-black text-slate-900">{credential.name}</p><p className="mt-1 text-xs text-slate-500">Client ID {credential.clientId} · ends {credential.last4}</p></div><HumanStatus value={credential.status}/></div><div className="mt-2 grid gap-1 text-xs text-slate-500 sm:grid-cols-2"><p>Expires {formatDateTime(credential.expiresAt)}</p><p>Last used {credential.lastUsedAt ? formatDateTime(credential.lastUsedAt) : 'Never'}</p></div>{canManage && credential.status !== 'REVOKED' ? <div className="mt-3 flex gap-2"><button type="button" onClick={() => openRotate(selected, credential)} className="rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs font-black text-slate-700">Rotate</button><button type="button" onClick={() => { setRevokeTarget(credential); setRevokeReason('Security remediation'); }} className="rounded-lg border border-rose-300 px-2.5 py-1.5 text-xs font-black text-rose-700">Revoke</button></div> : null}</article>)}</div>}{legacyTokens.length ? <details className="mt-4 rounded-xl bg-slate-50 p-3"><summary className="cursor-pointer text-xs font-black text-slate-700">Legacy opaque tokens · {legacyTokens.length}</summary><p className="mt-2 text-xs leading-5 text-slate-500">Compatibility only. Do not issue new opaque tokens for Machine JWT integrations.</p><div className="mt-2 space-y-2">{legacyTokens.map((token) => <div key={token.tokenId} className="rounded-lg bg-white p-2 text-xs"><div className="flex items-center justify-between gap-2"><span><b>{token.name}</b> · ends {token.last4}</span><HumanStatus value={token.status}/></div>{canManage && token.status === 'ACTIVE' ? <button type="button" onClick={() => { setLegacyRevokeTarget(token); setRevokeReason('Security remediation'); }} className="mt-2 rounded-lg border border-rose-300 px-2 py-1 font-black text-rose-700">Revoke legacy token</button> : null}</div>)}</div></details> : null}</div>
          </div>
        </section> : null}
      </div>
    )}

    <WorkspaceModal open={createOpen} title="Create Service Account" description="Use business choices. Technical audiences and route prefixes are derived automatically." onClose={() => setCreateOpen(false)} width="max-w-4xl"><ServiceAccountForm mode="create" state={createState} onChange={(patch) => setCreateState((current) => ({ ...current, ...patch }))} catalog={catalog} templates={templates} productOptions={productOptions} scopeOptions={scopeOptions} sourceSystemOptions={sourceSystemOptions} userOptions={userOptions} departmentOptions={departmentOptions} responsibilityOptions={responsibilityOptions} loadUsers={loadUsers} loadDepartments={loadDepartments} loadResponsibilities={loadResponsibilities} onProductsChange={setCreateProducts} busy={busy} onCancel={() => setCreateOpen(false)} onSubmit={() => { void createAccount(); }}/></WorkspaceModal>

    <WorkspaceModal open={Boolean(editState && selected)} title={`Edit access · ${selected?.name ?? ''}`} description="Changing the selections reduces or expands the current Machine Access boundary. Reduced access takes effect immediately." onClose={() => setEditState(null)} width="max-w-4xl">{editState && selected ? <ServiceAccountForm mode="edit" state={editState} onChange={(patch) => setEditState((current) => current ? ({ ...current, ...patch }) : current)} catalog={catalog} templates={templates} productOptions={productOptions} scopeOptions={scopeOptions} sourceSystemOptions={sourceSystemOptions} userOptions={userOptions} departmentOptions={departmentOptions} responsibilityOptions={responsibilityOptions} loadUsers={loadUsers} loadDepartments={loadDepartments} loadResponsibilities={loadResponsibilities} onProductsChange={(products) => setEditState((current) => current ? ({ ...current, products, machineScopes: unique([...current.machineScopes, ...recommendedScopes(products, catalog)]) }) : current)} busy={busy} onCancel={() => setEditState(null)} onSubmit={() => { void saveBoundary(); }}/>:null}</WorkspaceModal>

    <WorkspaceModal open={Boolean(credentialAction)} title={credentialAction?.kind === 'ROTATE' ? 'Rotate client credential' : 'Issue client credential'} description="The client secret is shown once. Store it in an approved secret manager before closing." onClose={() => setCredentialAction(null)}><div className="space-y-4"><FieldLabel htmlFor="machine-credential-name" label="Credential name" required><input id="machine-credential-name" value={credentialName} onChange={(event) => setCredentialName(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" /></FieldLabel><FieldLabel htmlFor="machine-credential-ttl" label="Credential lifetime" required><SelectField id="machine-credential-ttl" value={credentialTtl} onChange={setCredentialTtl} required options={credentialTtlOptions(credentialAction?.account.credentialMaxTtlSeconds ?? 90 * 86400)} /></FieldLabel>{credentialAction?.kind === 'ROTATE' ? <FieldLabel htmlFor="machine-credential-overlap" label="Rotation overlap"><SelectField id="machine-credential-overlap" value={rotationOverlap} onChange={setRotationOverlap} options={[{value:'0',label:'Immediate cutover'},{value:'60',label:'1 minute overlap'},{value:'300',label:'5 minutes overlap (recommended)'},{value:'900',label:'15 minutes overlap'}]} /></FieldLabel> : null}<AuditReasonSelector idPrefix="machine-credential-change" value={credentialReason} onChange={setCredentialReason} tier="HIGH_RISK"/><div className="flex justify-end gap-2"><button type="button" onClick={() => setCredentialAction(null)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black">Cancel</button><button type="button" disabled={busy || !credentialName.trim() || !isAuditReasonValid(credentialReason, 'HIGH_RISK')} onClick={() => { void submitCredentialAction(); }} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">{credentialAction?.kind === 'ROTATE' ? 'Rotate credential' : 'Issue credential'}</button></div></div></WorkspaceModal>

    <WorkspaceModal open={Boolean(revokeTarget)} title={`Revoke ${revokeTarget?.name ?? 'client credential'}`} description="Revocation is immediate and invalidates current short-lived Machine Access tokens through the Service Account security epoch." onClose={() => setRevokeTarget(null)}><div className="space-y-4"><div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm leading-6 text-rose-950"><b className="block">This integration may stop immediately.</b>Confirm the replacement credential has been deployed before revoking an in-use credential.</div><AuditReasonSelector idPrefix="machine-credential-revoke" value={revokeReason} onChange={setRevokeReason} tier="HIGH_RISK"/><div className="flex justify-end gap-2"><button type="button" onClick={() => setRevokeTarget(null)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black">Cancel</button><button type="button" disabled={busy || !isAuditReasonValid(revokeReason, 'HIGH_RISK')} onClick={() => { void revokeCredential(); }} className="rounded-xl bg-rose-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Revoke credential</button></div></div></WorkspaceModal>

    <WorkspaceModal open={Boolean(legacyRevokeTarget)} title={`Revoke legacy token · ${legacyRevokeTarget?.name ?? ''}`} description="Legacy opaque-token revocation is immediate." onClose={() => setLegacyRevokeTarget(null)}><div className="space-y-4"><div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b className="block">Compatibility credential</b>New machine integrations should use client credentials and short-lived JWTs. Revoke this token only after its caller has migrated or been retired.</div><AuditReasonSelector idPrefix="legacy-token-revoke" value={revokeReason} onChange={setRevokeReason} tier="HIGH_RISK"/><div className="flex justify-end gap-2"><button type="button" onClick={() => setLegacyRevokeTarget(null)} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black">Cancel</button><button type="button" disabled={busy || !isAuditReasonValid(revokeReason, 'HIGH_RISK')} onClick={() => { void revokeLegacyToken(); }} className="rounded-xl bg-rose-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Revoke legacy token</button></div></div></WorkspaceModal>

    <WorkspaceModal open={Boolean(issued)} title="Save this client secret now" description="OpenDispatch does not provide the client secret again after this dialog closes." onClose={() => { if (secretAcknowledged) setIssued(null); }}><div className="space-y-4"><div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">Client ID</p><div className="mt-2 flex gap-2"><code className="min-w-0 flex-1 break-all rounded-xl bg-white p-3 text-xs text-slate-800">{issued?.clientId}</code><CopyButton value={issued?.clientId ?? ''}/></div></div><div className="rounded-2xl border border-amber-300 bg-amber-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-amber-800">Client secret · shown once</p><div className="mt-2 flex gap-2"><code className="min-w-0 flex-1 break-all rounded-xl bg-white p-3 text-xs text-amber-950">{issued?.clientSecret}</code><CopyButton value={issued?.clientSecret ?? ''}/></div></div><label className="flex gap-2 rounded-2xl border border-slate-200 p-4 text-sm font-bold text-slate-800"><input type="checkbox" checked={secretAcknowledged} onChange={(event) => setSecretAcknowledged(event.target.checked)} className="mt-1 size-4"/>I saved the client ID and secret in an approved vault or secret manager.</label><button type="button" disabled={!secretAcknowledged} onClick={() => setIssued(null)} className="w-full rounded-xl bg-slate-950 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">Close one-time display</button></div></WorkspaceModal>
  </div>;
}

function ServiceAccountForm({ mode, state, onChange, catalog, templates, productOptions, scopeOptions, sourceSystemOptions, userOptions, departmentOptions, responsibilityOptions, loadUsers, loadDepartments, loadResponsibilities, onProductsChange, busy, onCancel, onSubmit }: Readonly<{
  mode: 'create' | 'edit';
  state: CreateState | EditState;
  onChange: (patch: Partial<CreateState & EditState>) => void;
  catalog: CredentialGovernanceCatalog | null;
  templates: ResponsibilityTemplate[];
  productOptions: SelectOption[];
  scopeOptions: SelectOption[];
  sourceSystemOptions: SelectOption[];
  userOptions: SelectOption[];
  departmentOptions: SelectOption[];
  responsibilityOptions: SelectOption[];
  loadUsers: (query:string,cursor?:string)=>Promise<{options:SelectOption[];nextCursor?:string}>;
  loadDepartments: (query:string,cursor?:string)=>Promise<{options:SelectOption[];nextCursor?:string}>;
  loadResponsibilities: (query:string,cursor?:string)=>Promise<{options:SelectOption[];nextCursor?:string}>;
  onProductsChange: (products: string[]) => void;
  busy: boolean;
  onCancel: () => void;
  onSubmit: () => void;
}>) {
  const create = mode === 'create' ? state as CreateState : null;
  const selectedTemplate = templates.find((item) => item.roleId === state.roleId);
  const products = selectedProducts(state.products, catalog);
  const cidrs = networkCidrs(state.networkProfile, state.customCidr);
  const valid = Boolean(selectedTemplate && products.length && state.machineScopes.length && (!state.machineScopes.includes('events.intake') || state.sourceSystems.length) && cidrs.length && isAuditReasonValid(state.auditReason, 'ELEVATED') && (mode === 'edit' || (create?.name.trim() && create.ownerUserId && create.ownerDepartmentId)));
  return <div className="space-y-5">
    {mode === 'create' ? <div className="grid gap-4 md:grid-cols-2"><FieldLabel htmlFor="machine-account-name" label="Integration name" required><input id="machine-account-name" value={create?.name ?? ''} onChange={(event) => onChange({ name: event.target.value })} placeholder="ERP Production" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" /></FieldLabel><FieldLabel htmlFor="machine-account-description" label="Purpose"><input id="machine-account-description" value={create?.description ?? ''} onChange={(event) => onChange({ description: event.target.value })} placeholder="Sends approved ERP events into OpenDispatch" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" /></FieldLabel></div> : null}
    {mode === 'create' ? <div className="grid gap-4 md:grid-cols-2"><FieldLabel htmlFor="machine-owner" label="Business owner" required help="Search the Tenant directory. The browser does not preload the People catalog."><AsyncSearchSelectField id="machine-owner" value={create?.ownerUserId ?? ''} onChange={(ownerUserId) => onChange({ ownerUserId })} loadOptions={loadUsers} selectedOption={userOptions.find(option=>option.value===(create?.ownerUserId??''))??null} placeholder="Search people" required /></FieldLabel><FieldLabel htmlFor="machine-owner-department" label="Owning Department" required><AsyncSearchSelectField id="machine-owner-department" value={create?.ownerDepartmentId ?? ''} onChange={(ownerDepartmentId) => onChange({ ownerDepartmentId })} loadOptions={loadDepartments} selectedOption={departmentOptions.find(option=>option.value===(create?.ownerDepartmentId??''))??null} placeholder="Search Departments" required /></FieldLabel></div> : null}
    <div className="grid gap-4 md:grid-cols-2"><FieldLabel htmlFor="machine-responsibility" label="Responsibility" required help="Search governed Responsibilities; only SERVICE_ACCOUNT-eligible choices are returned."><AsyncSearchSelectField id="machine-responsibility" value={state.roleId} onChange={(roleId) => onChange({ roleId })} loadOptions={loadResponsibilities} selectedOption={responsibilityOptions.find(option=>option.value===state.roleId)??(state.roleId?{value:state.roleId,label:state.roleId}:null)} placeholder="Search responsibilities" required /></FieldLabel><FieldLabel htmlFor="machine-api-products" label="API products" required help="API products derive the audience and route boundary."><PopupMultiSelectField id="machine-api-products" values={state.products} onChange={onProductsChange} options={productOptions} placeholder="Choose API products" /></FieldLabel></div>
    <div className="grid gap-4 md:grid-cols-2"><FieldLabel htmlFor="machine-operations" label="Allowed operations" required help="Choose what the integration is allowed to do. OpenDispatch stores the canonical Machine Scope behind this label."><PopupMultiSelectField id="machine-operations" values={state.machineScopes} onChange={(machineScopes) => onChange({ machineScopes })} options={scopeOptions} placeholder="Choose allowed operations" /></FieldLabel><FieldLabel htmlFor="machine-source-systems" label="Source Systems" help="Limit which configured Source Systems this integration may represent."><PopupMultiSelectField id="machine-source-systems" values={state.sourceSystems} onChange={(sourceSystems) => onChange({ sourceSystems })} options={sourceSystemOptions} placeholder="Choose Source Systems" emptyMessage="No active Source System is configured."/>{state.machineScopes.includes('events.intake') && !state.sourceSystems.length ? <p className="mt-2 text-xs font-bold text-amber-800">Business Event Intake requires at least one Source System.</p> : null}<p className="mt-2 text-xs leading-5 text-slate-500">Need another Source System? <Link href={PRODUCT_ROUTES.sourceSystems} className="font-black text-blue-700 hover:underline">Open Source Systems</Link>. Return here after creating it.</p></FieldLabel></div>
    <div className="grid gap-4 md:grid-cols-2"><FieldLabel htmlFor="machine-network-profile" label="Network profile" required><SelectField id="machine-network-profile" value={state.networkProfile} onChange={(networkProfile) => onChange({ networkProfile: networkProfile as NetworkProfile, customCidr: networkProfile === 'CUSTOM' ? state.customCidr : '' })} required options={[{value:'PRIVATE',label:'Private company networks',description:'RFC1918 private networks. Recommended for internal ERP/MES.'},{value:'VPN',label:'Company VPN / 10.x network',description:'Restrict to 10.0.0.0/8.'},{value:'CUSTOM',label:'Custom approved network',description:'Use only when the integration has a known static CIDR.'}]} /></FieldLabel>{state.networkProfile === 'CUSTOM' ? <FieldLabel htmlFor="machine-custom-cidr" label="Approved CIDR" required help="Free text is used only because a CIDR is an external network value, not an OpenDispatch identifier."><input id="machine-custom-cidr" value={state.customCidr} onChange={(event) => onChange({ customCidr: event.target.value })} placeholder="203.0.113.0/24" className="w-full rounded-xl border border-slate-300 px-3 py-2.5 font-mono text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-100" /></FieldLabel> : <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600"><b className="block text-slate-800">Network boundary</b><p className="mt-1">{humanNetwork(cidrs)}</p></div>}</div>
    <div className="grid gap-4 md:grid-cols-3"><FieldLabel htmlFor="machine-credential-maximum" label="Maximum credential lifetime" required><SelectField id="machine-credential-maximum" value={state.credentialMaxTtlSeconds} onChange={(credentialMaxTtlSeconds) => onChange({ credentialMaxTtlSeconds })} options={credentialMaxTtlOptions()} required /></FieldLabel><FieldLabel htmlFor="machine-active-credentials" label="Maximum active credentials" required><SelectField id="machine-active-credentials" value={state.maxActiveCredentials} onChange={(maxActiveCredentials) => onChange({ maxActiveCredentials })} options={[1,2,3,5].map((value) => ({value:String(value),label:String(value),description:value === 2 ? 'Recommended for safe rotation' : undefined}))} required /></FieldLabel>{mode === 'create' ? <FieldLabel htmlFor="machine-rate-limit" label="Request rate" required><SelectField id="machine-rate-limit" value={create?.rateLimitPerMinute ?? '300'} onChange={(rateLimitPerMinute) => onChange({ rateLimitPerMinute })} options={[30,60,120,300,1000].map((value) => ({value:String(value),label:`${value} / minute`}))} required /></FieldLabel> : <div/>}</div>
    {mode === 'create' ? <FieldLabel htmlFor="machine-review-cycle" label="Access review cycle" required><SelectField id="machine-review-cycle" value={create?.reviewDays ?? '90'} onChange={(reviewDays) => onChange({ reviewDays })} options={[30,90,180,365].map((days) => ({value:String(days),label:days === 365 ? 'Every year' : `Every ${days} days`,description:days === 90 ? 'Recommended' : undefined}))} required /></FieldLabel> : null}
    <BoundaryPreview template={selectedTemplate} products={products} operations={state.machineScopes.map((scope) => scopeName(scope, catalog))} sources={state.sourceSystems.map((id) => sourceName(id, catalog))} cidrs={cidrs}/>
    <AuditReasonSelector idPrefix={mode === 'create' ? 'machine-access-create' : 'machine-access-edit'} value={state.auditReason} onChange={(auditReason) => onChange({ auditReason })} tier="ELEVATED" />
    <div className="flex justify-end gap-2"><button type="button" onClick={onCancel} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-black">Cancel</button><button type="button" disabled={busy || !valid} onClick={onSubmit} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">{mode === 'create' ? 'Create Service Account' : 'Save access boundary'}</button></div>
    {mode === 'create' && !sourceSystemOptions.length ? <p className="text-xs text-amber-800">No active Source System is available. You can still review this form, but event-producing integrations should first be configured from <Link href={PRODUCT_ROUTES.sourceSystems} className="font-black underline">Source Systems</Link>.</p> : null}
  </div>;
}
function BoundaryPreview({ template, products, operations, sources, cidrs }: Readonly<{ template?: ResponsibilityTemplate; products: CredentialApiProduct[]; operations: string[]; sources: string[]; cidrs: string[] }>) {
  return <section className="rounded-2xl border border-blue-200 bg-blue-50/60 p-4"><p className="text-xs font-black uppercase tracking-wide text-blue-800">What this Service Account will be able to do</p><div className="mt-3 grid gap-3 md:grid-cols-2"><PreviewItem label="Responsibility" value={template?.roleName ?? 'Choose a responsibility'} /><PreviewItem label="API products" value={products.map((item) => item.displayName).join(', ') || 'Choose API products'} /><PreviewItem label="Allowed operations" value={operations.join(', ') || 'Choose operations'} /><PreviewItem label="Source Systems" value={sources.join(', ') || 'No Source System selected'} /><PreviewItem label="Network" value={humanNetwork(cidrs)} /><PreviewItem label="Permission ceiling" value={template?.capabilityCodes.map(humanizePermission).join(', ') || 'Derived from responsibility'} /></div></section>;
}

function PreviewItem({ label, value }: Readonly<{ label: string; value: string }>) { return <div className="rounded-xl bg-white p-3"><p className="text-xs font-black text-slate-500">{label}</p><p className="mt-1 text-sm font-bold text-slate-800">{value}</p></div>; }
function SummaryCard({ label, value }: Readonly<{ label: string; value: string }>) { return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p><p className="mt-2 text-sm font-black leading-6 text-slate-900">{value}</p></div>; }
function InfoRow({ label, value }: Readonly<{ label: string; value: string }>) { return <div><dt className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-1 font-bold text-slate-800">{value}</dd></div>; }
function CopyButton({ value }: Readonly<{ value: string }>) { return <button type="button" onClick={() => { void navigator.clipboard.writeText(value); }} className="shrink-0 rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700">Copy</button>; }
function mergeBy<T,K extends keyof T>(items:T[],value:T,key:K):T[]{return mergeManyBy(items,[value],key);}
function mergeManyBy<T,K extends keyof T>(items:T[],values:T[],key:K):T[]{const map=new Map(items.map(item=>[String(item[key]),item]));for(const value of values)map.set(String(value[key]),value);return Array.from(map.values());}
function unique(values: string[]) { return Array.from(new Set(values.filter(Boolean))); }
function selectedProducts(codes: string[], catalog: CredentialGovernanceCatalog | null) { return (catalog?.apiProducts ?? []).filter((product) => codes.includes(product.productCode)); }
function matchingProducts(account: ServiceAccount, catalog: CredentialGovernanceCatalog | null) { return (catalog?.apiProducts ?? []).filter((product) => product.audiences.every((value) => account.audiences.includes(value)) && product.apiPrefixes.every((value) => account.apiPrefixes.includes(value))); }
function recommendedScopes(productCodes: string[], catalog: CredentialGovernanceCatalog | null) { const products = selectedProducts(productCodes, catalog); return products.some((product) => product.apiPrefixes.some((prefix) => prefix.startsWith('/api/events'))) && (catalog?.machineScopes ?? []).some((scope) => scope.scopeCode === 'events.intake') ? ['events.intake'] : []; }
function sourceName(id: string, catalog: CredentialGovernanceCatalog | null) { return catalog?.sourceSystems.find((source) => source.sourceSystemId === id)?.displayName ?? id; }
function scopeName(code: string, catalog: CredentialGovernanceCatalog | null) { return catalog?.machineScopes.find((scope) => scope.scopeCode === code)?.displayName ?? humanizePermission(code); }
function userName(id: string, users: User[]) { return users.find((user) => user.userId === id)?.displayName ?? id; }
function departmentName(id: string, departments: Department[]) { return departments.find((department) => department.departmentId === id)?.name ?? id; }
function networkCidrs(profile: NetworkProfile, custom: string) { if (profile === 'PRIVATE') return ['10.0.0.0/8','172.16.0.0/12','192.168.0.0/16']; if (profile === 'VPN') return ['10.0.0.0/8']; return isCidr(custom) ? [custom.trim()] : []; }
function inferNetworkProfile(cidrs: string[]): NetworkProfile { const sorted = [...cidrs].sort().join('|'); if (sorted === ['10.0.0.0/8','172.16.0.0/12','192.168.0.0/16'].sort().join('|')) return 'PRIVATE'; if (cidrs.length === 1 && cidrs[0] === '10.0.0.0/8') return 'VPN'; return 'CUSTOM'; }
function humanNetwork(cidrs: string[]) { const profile = inferNetworkProfile(cidrs); if (!cidrs.length) return 'No approved network'; if (profile === 'PRIVATE') return 'Private company networks'; if (profile === 'VPN') return 'Company VPN / 10.x network'; return cidrs.join(', '); }
function isCidr(value: string) { const [address, prefix] = value.trim().split('/'); if (!address || prefix === undefined || Number(prefix) < 0 || Number(prefix) > 32) return false; const parts = address.split('.'); return parts.length === 4 && parts.every((part) => /^\d{1,3}$/.test(part) && Number(part) >= 0 && Number(part) <= 255); }
function dateAfter(days: number) { const value = new Date(); value.setDate(value.getDate() + days); return value.toISOString(); }
function formatDate(value: string) { const date = new Date(value); return Number.isNaN(date.valueOf()) ? value : date.toLocaleDateString(); }
function formatDateTime(value: string) { const date = new Date(value); return Number.isNaN(date.valueOf()) ? value : date.toLocaleString(); }
function credentialMaxTtlOptions(): SelectOption[] { return [{value:String(30*86400),label:'30 days'},{value:String(90*86400),label:'90 days (recommended)'},{value:String(180*86400),label:'180 days'},{value:String(365*86400),label:'1 year'}]; }
function credentialTtlOptions(max: number): SelectOption[] { return [{value:String(86400),label:'1 day'},{value:String(7*86400),label:'7 days'},{value:String(30*86400),label:'30 days'},{value:String(90*86400),label:'90 days'},{value:String(180*86400),label:'180 days'},{value:String(365*86400),label:'1 year'}].filter((option) => Number(option.value) <= max); }
