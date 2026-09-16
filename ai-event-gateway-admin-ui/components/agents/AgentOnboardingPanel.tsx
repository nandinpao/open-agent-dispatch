'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { useI18n } from '@/hooks/useI18n';
import { useAuth } from '@/components/auth/AuthProvider';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowedForScope } from '@/lib/navigation/uiEntitlements';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { generateAgentCredentialToken } from '@/lib/agents/credentialToken';
import type { CoreAgentSetupRequest, CoreAgentSetupResponse } from '@/lib/types/core';
import { BeginnerGuideButton, FieldAssist } from '@/components/resource-scope/EnterpriseAccessUi';
import { AdvancedSection, AsyncSearchSelectField, BooleanToggle, FormField, SelectField, TextAreaField, TextField, type SelectOption } from '@/components/forms';

type AgentPurpose = 'ISSUE_TRACKING' | 'CALLBACK_HANDLER' | 'DATA_SYNC' | 'CUSTOM_TASK';
type RuntimeType = 'Docker' | 'Local Process' | 'Remote Host';

interface AgentSetupDraft {
  tenantId: string;
  agentId: string;
  agentName: string;
  ownerTeam: string;
  ownerDepartmentId: string;
  ownerGroupId: string;
  businessOwnerUserId: string;
  technicalStewardUserId: string;
  responsibilityRoleId: string;
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

function isLoopbackGatewayUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return ['localhost', '127.0.0.1', '::1'].includes(url.hostname);
  } catch {
    return false;
  }
}

function insecureRemoteGatewayUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' && !isLoopbackGatewayUrl(value);
  } catch {
    return false;
  }
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
    { label: 'Basic information', done: Boolean(draft.agentId && draft.agentName), description: 'Enter a display name. The technical Agent ID is generated automatically and can be adjusted under Advanced.' },
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
    ownerDepartmentId: draft.ownerDepartmentId || undefined,
    ownerGroupId: draft.ownerGroupId || undefined,
    businessOwnerUserId: draft.businessOwnerUserId || undefined,
    technicalStewardUserId: draft.technicalStewardUserId || undefined,
    responsibilityRoleId: draft.responsibilityRoleId || undefined,
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
  const { activeTenantId: selectedTenantId } = useAuth();
  const entitlements = useUiEntitlements();
  const [draft, setDraft] = useState<AgentSetupDraft>({
    tenantId: selectedTenantId,
    agentId: '',
    agentName: '',
    ownerTeam: '',
    ownerDepartmentId: '',
    ownerGroupId: '',
    businessOwnerUserId: '',
    technicalStewardUserId: '',
    responsibilityRoleId: '',
    description: '',
    purpose: 'CUSTOM_TASK',
    runtimeType: 'Docker',
    gatewayUrl: 'http://localhost:18081',
    credentialToken: generateAgentCredentialToken(),
    autoApprove: false,
  });
  const [submitting, setSubmitting] = useState(false);
  const [agentIdCustomized, setAgentIdCustomized] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createdAgentId, setCreatedAgentId] = useState<string | null>(null);
  const [setupResult, setSetupResult] = useState<CoreAgentSetupResponse | null>(null);
  const [selectedDepartmentOption,setSelectedDepartmentOption]=useState<SelectOption|null>(null);
  const [selectedGroupOption,setSelectedGroupOption]=useState<SelectOption|null>(null);
  const [selectedBusinessOwnerOption,setSelectedBusinessOwnerOption]=useState<SelectOption|null>(null);
  const [selectedTechnicalStewardOption,setSelectedTechnicalStewardOption]=useState<SelectOption|null>(null);
  const [selectedResponsibilityOption,setSelectedResponsibilityOption]=useState<SelectOption|null>(null);

  // Tenant context is authoritative. Never carry ownership selections from a previous workspace
  // into a newly selected Tenant, even when entity identifiers happen to collide.
  useEffect(() => {
    setDraft((current) => ({
      ...current,
      tenantId: selectedTenantId,
      ownerDepartmentId: '',
      ownerGroupId: '',
      businessOwnerUserId: '',
      technicalStewardUserId: '',
      responsibilityRoleId: '',
    }));
    setSelectedDepartmentOption(null);
    setSelectedGroupOption(null);
    setSelectedBusinessOwnerOption(null);
    setSelectedTechnicalStewardOption(null);
    setSelectedResponsibilityOption(null);
  }, [selectedTenantId]);

  const loadDepartments=useCallback(async(query:string,cursor?:string)=>{
    if(!selectedTenantId)return {options:[]};
    const page=Number(cursor||'0');
    const response=await accessManagementApi.departments(selectedTenantId,page,25,query,'ACTIVE');
    const options=response.items.filter(item=>actionAllowedForScope(entitlements.value,'agents.setup','DEPARTMENT',item.departmentId)).map(item=>({value:item.departmentId,label:item.name,description:item.code}));
    return {options,nextCursor:response.hasMore?String(page+1):undefined};
  },[selectedTenantId,entitlements.value]);
  const loadGroups=useCallback(async(query:string,cursor?:string)=>{
    if(!selectedTenantId)return {options:[]};
    const page=Number(cursor||'0');
    const response=await accessManagementApi.groups(selectedTenantId,page,25,query,'','ACTIVE');
    const options=response.items.filter(item=>actionAllowedForScope(entitlements.value,'agents.setup','GROUP',item.groupId)).map(item=>({value:item.groupId,label:item.name,description:item.code}));
    return {options,nextCursor:response.hasMore?String(page+1):undefined};
  },[selectedTenantId,entitlements.value]);
  const loadBusinessOwners=useCallback(async(query:string,cursor?:string)=>{
    if(!selectedTenantId||!draft.ownerDepartmentId)return {options:[]};
    const response=await accessManagementApi.tenantUsers(selectedTenantId,25,cursor||'',query,'ACTIVE','ACTIVE','',draft.ownerDepartmentId);
    return {options:response.items.map(item=>({value:item.userId,label:item.displayName,description:item.email||item.username})),nextCursor:response.nextCursor||undefined};
  },[selectedTenantId,draft.ownerDepartmentId]);
  const loadTechnicalStewards=useCallback(async(query:string,cursor?:string)=>{
    if(!selectedTenantId)return {options:[]};
    const response=await accessManagementApi.tenantUsers(selectedTenantId,25,cursor||'',query,'ACTIVE','ACTIVE');
    return {options:response.items.map(item=>({value:item.userId,label:item.displayName,description:item.email||item.username})),nextCursor:response.nextCursor||undefined};
  },[selectedTenantId]);
  const loadResponsibilities=useCallback(async(query:string,cursor?:string)=>{
    if(!selectedTenantId)return {options:[]};
    const page=Number(cursor||'0');
    const response=await accessManagementApi.responsibilityTemplates(selectedTenantId,page,25,query,'ACTIVE');
    const options=response.items.filter(item=>item.allowedPrincipalTypes?.includes('AGENT')).map(item=>({value:item.roleId,label:item.roleName,description:item.description||item.riskLevel}));
    return {options,nextCursor:response.hasMore?String(page+1):undefined};
  },[selectedTenantId]);
  const businessOwnerEligible=!draft.businessOwnerUserId||selectedBusinessOwnerOption?.value===draft.businessOwnerUserId;

  const purpose = useMemo(() => purposeOptions.find((option) => option.value === draft.purpose) ?? purposeOptions[0], [draft.purpose]);
  const allowTenantWideOwnership = actionAllowedForScope(entitlements.value, 'agents.setup', 'TENANT', selectedTenantId);
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
    setDraft((current) => {
      if(key==='ownerDepartmentId'&&current.ownerDepartmentId!==value){
        setSelectedBusinessOwnerOption(null);
        return {...current,[key]:value,businessOwnerUserId:''};
      }
      return { ...current, [key]: value };
    });
  }

  async function submit() {
    if (!draft.agentId.trim() || !draft.agentName.trim()) {
      setError(t('agent.setup.validation.requiredName'));
      return;
    }
    if (!draft.ownerDepartmentId || !draft.businessOwnerUserId || !draft.responsibilityRoleId) {
      setError('Owner Department, Business Owner and Agent Responsibility are required.');
      return;
    }
    if (!businessOwnerEligible) {
      setError('The selected Business Owner is not an active Tenant member and active member of the selected Owner Department.');
      return;
    }
    if (draft.autoApprove && !draft.credentialToken.trim()) {
      setError(t('agent.setup.validation.tokenRequired'));
      return;
    }
    if (insecureRemoteGatewayUrl(draft.gatewayUrl)) {
      setError('Remote Agent Gateway connections must use HTTPS. Plain HTTP is allowed only for loopback development endpoints.');
      return;
    }
    setSubmitting(true);
    setMessage(null);
    setError(null);
    try {
      const result = await coreAdminApi.setupAgent(buildSetupRequest({ ...draft, tenantId: selectedTenantId || draft.tenantId }, purpose));
      setSetupResult(result);
      setMessage(result.setupStatus === 'READY' ? t('agent.setup.success.approved') : t('agent.setup.success.draft'));
      setCreatedAgentId(result.agentId || draft.agentId);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('agent.setup.error.failed'));
    } finally {
      setSubmitting(false);
    }
  }


  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_24rem]">
      <section className="space-y-5 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-xs font-black uppercase tracking-wide text-blue-600">{t('agent.setup.badge')}</p>
            <h2 className="mt-1 text-xl font-black text-slate-950">{t('agent.setup.title')}</h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">{t('agent.setup.description')}</p>
          </div>
          <BeginnerGuideButton title="Create an Agent without learning the internals" description="The setup keeps business ownership, runtime connection and dispatch usage together. Advanced values stay optional." steps={[
            { title: 'Name and own the Agent', description: 'Choose the Department or Group that owns the Agent. Only scopes allowed by your current Responsibility are offered.' },
            { title: 'Choose what it does', description: 'Pick a purpose and runtime type from the provided options. Capabilities remain optional and do not grant access by themselves.' },
            { title: 'Connect and test', description: 'Create the Agent, start its runtime with the generated command, then use the nearby Dispatch link to add it to a Flow and run a real test.' },
          ]} />
        </div>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Current workspace</div>
          <div className="mt-1 text-sm font-black text-slate-900">{selectedTenantId || draft.tenantId || 'No active workspace'}</div>
          <p className="mt-1 text-xs leading-5 text-slate-500">The Tenant comes from your authenticated session. There is no Tenant selector during Agent creation.</p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="md:col-span-2"><FormField id="agent-setup-name" label={t('agent.setup.agentName')} help="Use a business-friendly name. OpenDispatch generates the technical Agent ID for you." required><TextField id="agent-setup-name" value={draft.agentName} onChange={(value) => { setField('agentName', value); if (!agentIdCustomized) setField('agentId', normalizeAgentId(value)); }} placeholder="Finance Payment Agent" /></FormField></div>
          <div><div className="mb-1 flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">Owner Department</span><FieldAssist help="Choose the business Department responsible for this Agent. This determines the Agent's default data scope; it does not grant permission by itself." href="/access-management?workspace=organization" linkLabel="Manage organization" /></div><AsyncSearchSelectField id="agent-owner-department" value={draft.ownerDepartmentId} onChange={(value,option) => {setSelectedDepartmentOption(option??null);setField('ownerDepartmentId', value);}} loadOptions={loadDepartments} selectedOption={selectedDepartmentOption} placeholder={allowTenantWideOwnership ? 'Search authorized Departments' : 'Search an authorized Department'} /></div>
          <div><div className="mb-1 flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">Owner Group</span><FieldAssist help="Choose a Group when a cross-functional team governs this Agent. Group membership alone does not grant Agent access." href="/access-management?workspace=organization" linkLabel="Manage groups" /></div><AsyncSearchSelectField id="agent-owner-group" value={draft.ownerGroupId} onChange={(value,option) => {setSelectedGroupOption(option??null);setField('ownerGroupId', value);}} loadOptions={loadGroups} selectedOption={selectedGroupOption} placeholder="Search an authorized Group (optional)" /></div>
          <div><div className="mb-1 flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">Business Owner</span><FieldAssist help="The accountable Human owner for purpose, risk and periodic review. Ownership does not grant the Person Agent administration permissions." href="/access-management?workspace=people" linkLabel="Manage people" /></div><AsyncSearchSelectField id="agent-business-owner" value={draft.businessOwnerUserId} onChange={(value,option) => {setSelectedBusinessOwnerOption(option??null);setField('businessOwnerUserId', value);}} loadOptions={loadBusinessOwners} selectedOption={selectedBusinessOwnerOption} disabled={!draft.ownerDepartmentId} placeholder={draft.ownerDepartmentId ? "Search active Department members" : "Select Owner Department first"} required /><p className={`mt-1 text-xs ${businessOwnerEligible ? 'text-slate-500' : 'text-rose-600'}`}>Only active Tenant members with an active membership in the selected Department can be accountable Business Owners.</p></div>
          <FormField id="agent-technical-steward" label="Technical Steward"><AsyncSearchSelectField id="agent-technical-steward" value={draft.technicalStewardUserId} onChange={(value,option) => {setSelectedTechnicalStewardOption(option??null);setField('technicalStewardUserId', value);}} loadOptions={loadTechnicalStewards} selectedOption={selectedTechnicalStewardOption} placeholder="Search optional technical steward" /></FormField>
          <div className="md:col-span-2"><div className="mb-1 flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-black text-slate-800">Agent Responsibility</span><FieldAssist help="This is the canonical RBAC Responsibility held by the Agent principal. Only Responsibilities explicitly eligible for AGENT are shown." href="/access-management?workspace=roles" linkLabel="Manage responsibilities" /></div><AsyncSearchSelectField id="agent-responsibility" value={draft.responsibilityRoleId} onChange={(value,option) => {setSelectedResponsibilityOption(option??null);setField('responsibilityRoleId', value);}} loadOptions={loadResponsibilities} selectedOption={selectedResponsibilityOption} placeholder="Search Agent Responsibilities" required /></div>
        </div>
        <p className="text-xs leading-5 text-slate-500">Agent ownership combines accountable Human ownership with an Organization scope. Department or Group membership alone does not grant Agent access; your Responsibility must allow the selected scope.</p>
        <AdvancedSection title="Advanced identity and legacy metadata" description="generated Agent ID and compatibility label"><div className="grid gap-4 md:grid-cols-2"><FormField id="agent-setup-id" label={t('agent.setup.agentId')} help="Usually leave the generated value unchanged. It is shown mainly for runtime/API integration."><TextField id="agent-setup-id" value={draft.agentId} onChange={(value) => { setAgentIdCustomized(true); setField('agentId', normalizeAgentId(value)); }} placeholder="finance-payment-agent" /></FormField><FormField id="agent-owner-team" label={t('agent.setup.ownerTeam')} help="Legacy compatibility label only."><TextField id="agent-owner-team" value={draft.ownerTeam} onChange={(value)=>setField('ownerTeam',value)} placeholder="Legacy team label (optional)" /></FormField></div></AdvancedSection>

        <FormField id="agent-description" label={t('agent.setup.descriptionLabel')}><TextAreaField id="agent-description" value={draft.description} onChange={(value)=>setField('description',value)} rows={4} /></FormField>

        <div>
          <div className="text-sm font-black text-slate-800">{t('agent.setup.purposeQuestion')}</div>
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

        <div className="grid gap-4 md:grid-cols-2"><FormField id="agent-runtime-type" label={t('agent.setup.runtimeType')} help="Choose the runtime location. Connection defaults are generated automatically."><SelectField id="agent-runtime-type" value={draft.runtimeType} onChange={(value)=>setField('runtimeType',value as RuntimeType)} options={runtimeTypes.map((value)=>({value,label:value}))} /></FormField></div>

        <AdvancedSection title="Connection & approval settings" description="normally generated automatically"><p className="mb-3 text-xs leading-5 text-slate-500">Open this only when the Gateway endpoint, token, or approval workflow needs to be changed.</p><div className="grid gap-4 md:grid-cols-2"><FormField id="agent-gateway-url" label={t('agent.setup.gatewayUrl')}><TextField id="agent-gateway-url" type="url" value={draft.gatewayUrl} onChange={(value)=>setField('gatewayUrl',value)} /></FormField><div><FormField id="agent-connection-token" label={t('agent.setup.connectionToken')}><TextField id="agent-connection-token" type="password" autoComplete="new-password" value={draft.credentialToken} onChange={(value)=>setField('credentialToken',value)} /></FormField><button type="button" onClick={() => setField('credentialToken', generateAgentCredentialToken())} className="mt-2 rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-bold text-slate-700 hover:bg-slate-100">{t('agent.setup.generateToken')}</button><p className="mt-2 text-xs leading-5 text-amber-700">Treat this connection token as a secret. Copying startup commands can place it in clipboard history, terminal scrollback, or shell history.</p></div></div>{insecureRemoteGatewayUrl(draft.gatewayUrl) ? <p className="mt-3 text-xs font-semibold text-rose-700">Remote Agent Gateway connections must use HTTPS. Plain HTTP is allowed only for loopback development endpoints.</p> : null}<div className="mt-4"><BooleanToggle id="agent-auto-approve" checked={draft.autoApprove} onChange={(checked)=>setField('autoApprove',checked)} label={t('agent.setup.autoApprove')} description="Use only when your governance policy permits immediate approval during setup." /></div></AdvancedSection>

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
                  <span className={`rounded-full px-2 py-1 text-xs font-black ${check.ready ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
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
                  <span className={`rounded-full px-2 py-1 text-xs font-black ${item.done ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600'}`}>{item.done ? t('agent.setup.ready') : t('agent.setup.pending')}</span>
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
                  <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-xl bg-black/30 p-3 text-xs leading-5 text-slate-100">{entry.value}</pre>
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
                    {step.command ? <pre className="mt-2 overflow-auto rounded-lg bg-black/30 p-2 text-xs text-slate-100">{step.command}</pre> : null}
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
