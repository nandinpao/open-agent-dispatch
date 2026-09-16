'use client';
import { createUuid } from '@/lib/utils/uuid';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { ApiError } from '@/lib/api/client';
import type { CoreAgentPoolView, CoreDispatchFlowAgentOptionView, CoreDispatchFlowView, CoreDispatchSimulationResponse, CoreEventIntakeDecisionResponse, CoreSourceSystem } from '@/lib/types/core';
import { DispatchWorkspaceSections } from './DispatchWorkspaceSections';
import { CreateSourceFlowDialog, type CreateSourceFlowInput } from './CreateSourceFlowDialog';
import { DispatchSetupChecklist } from './DispatchSetupChecklist';
import {
  type DispatchWorkspaceQuery,
  flowDisplay,
  queryValue,
  sectionState,
} from './dispatchWorkspaceModel';
import { SourceFlowMasterList } from './SourceFlowMasterList';
import { BeginnerGuideButton } from '@/components/resource-scope/EnterpriseAccessUi';
import { SourceSystemOnboardingDialog, type SourceSystemOnboardingResult } from '@/components/source-systems/SourceSystemOnboardingDialog';
import { IssueTrackingContextCard } from '@/components/integrations/IssueTrackingContextCard';

function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback;
  if (error instanceof Error) return error.message;
  return fallback;
}

function normalizeSource(value?: string | null): string {
  return String(value ?? '').trim();
}

function buildWorkspaceHref(pathname: string, sourceSystem?: string | null, flowId?: string | null): string {
  const query = new URLSearchParams();
  if (sourceSystem) query.set('sourceSystem', sourceSystem);
  if (flowId) query.set('flowId', flowId);
  const text = query.toString();
  return text ? `${pathname}?${text}` : pathname;
}


function generateId(prefix: string): string {
  return `${prefix}-${createUuid()}`;
}

export function DispatchWorkspace({ initialQuery }: Readonly<{ initialQuery?: DispatchWorkspaceQuery }> = {}) {
  const router = useRouter();
  const pathname = usePathname();
  const { activeTenantId: selectedTenantId } = useAuth();
  const entitlements = useUiEntitlements();
  const canCreateFlow = actionAllowed(entitlements.value, 'dispatch.create');
  const canCreateSource = actionAllowed(entitlements.value, 'source-systems.create');
  const tenantId = selectedTenantId ?? '';

  const [sourceSystems, setSourceSystems] = useState<CoreSourceSystem[]>([]);
  const [flows, setFlows] = useState<CoreDispatchFlowView[]>([]);
  const [pools, setPools] = useState<CoreAgentPoolView[]>([]);
  const [agents, setAgents] = useState<CoreDispatchFlowAgentOptionView[]>([]);

  const [sourceLoading, setSourceLoading] = useState(false);
  const [flowLoading, setFlowLoading] = useState(false);
  const [poolLoading, setPoolLoading] = useState(false);
  const [agentLoading, setAgentLoading] = useState(false);
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [flowError, setFlowError] = useState<string | null>(null);
  const [poolError, setPoolError] = useState<string | null>(null);
  const [agentError, setAgentError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [setupChecklistOpen, setSetupChecklistOpen] = useState(true);
  const [createFlowOpen, setCreateFlowOpen] = useState(() => queryValue(initialQuery, 'create') === '1');
  const [createSourceOpen, setCreateSourceOpen] = useState(false);
  const [latestSimulationResult, setLatestSimulationResult] = useState<CoreDispatchSimulationResponse | null>(null);
  const [latestRealTestResult, setLatestRealTestResult] = useState<CoreEventIntakeDecisionResponse | null>(null);

  const [selectedSourceSystem, setSelectedSourceSystem] = useState<string | null>(() => normalizeSource(queryValue(initialQuery, 'sourceSystem')) || null);
  const [selectedFlowId, setSelectedFlowId] = useState<string | null>(() => normalizeSource(queryValue(initialQuery, 'flowId')) || null);

  const selectedFlow = useMemo(() => flows.find((flow) => flow.flowId === selectedFlowId) ?? null, [flows, selectedFlowId]);
  const selectedDefaultPool = useMemo(() => pools.find((pool) => pool.poolId === selectedFlow?.defaultPoolId) ?? null, [pools, selectedFlow?.defaultPoolId]);
  const allLoading = sourceLoading || flowLoading || poolLoading || agentLoading;

  const loadSourceSystems = useCallback(async () => {
    if (!tenantId.trim()) {
      setSourceSystems([]);
      return;
    }
    setSourceLoading(true);
    setSourceError(null);
    try {
      setSourceSystems(await coreAdminApi.getSourceSystems(tenantId));
    } catch (caught) {
      setSourceError(apiErrorMessage(caught, 'Failed to load Source Systems.'));
      setSourceSystems([]);
    } finally {
      setSourceLoading(false);
    }
  }, [tenantId]);

  const loadFlows = useCallback(async () => {
    if (!tenantId.trim()) {
      setFlows([]);
      return;
    }
    setFlowLoading(true);
    setFlowError(null);
    try {
      setFlows(await coreAdminApi.getDispatchFlows(tenantId));
    } catch (caught) {
      setFlowError(apiErrorMessage(caught, 'Source Flow Loading failed.'));
      setFlows([]);
    } finally {
      setFlowLoading(false);
    }
  }, [tenantId]);

  const loadPools = useCallback(async () => {
    if (!tenantId.trim()) {
      setPools([]);
      return;
    }
    setPoolLoading(true);
    setPoolError(null);
    try {
      setPools(await coreAdminApi.getAgentPools(tenantId));
    } catch (caught) {
      setPoolError(apiErrorMessage(caught, 'Agent Pool Loading failed.'));
      setPools([]);
    } finally {
      setPoolLoading(false);
    }
  }, [tenantId]);

  const loadAgents = useCallback(async () => {
    if (!tenantId.trim()) {
      setAgents([]);
      return;
    }
    setAgentLoading(true);
    setAgentError(null);
    try {
      setAgents(await coreAdminApi.getDispatchFlowAgentOptions(tenantId));
    } catch (caught) {
      setAgentError(apiErrorMessage(caught, 'Failed to load Agents.'));
      setAgents([]);
    } finally {
      setAgentLoading(false);
    }
  }, [tenantId]);

  const reload = useCallback(() => {
    void loadSourceSystems();
    void loadFlows();
    void loadPools();
    void loadAgents();
  }, [loadAgents, loadFlows, loadPools, loadSourceSystems]);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    if (!flows.length) return;
    const querySource = normalizeSource(queryValue(initialQuery, 'sourceSystem')) || null;
    const queryFlowId = normalizeSource(queryValue(initialQuery, 'flowId')) || null;
    const flowFromQuery = queryFlowId ? flows.find((flow) => flow.flowId === queryFlowId) : undefined;
    const nextSource = selectedSourceSystem
      ?? flowFromQuery?.sourceSystem
      ?? querySource
      ?? flows[0]?.sourceSystem
      ?? null;
    const flowsForSource = flows.filter((flow) => !nextSource || flow.sourceSystem === nextSource);
    const nextFlowId = selectedFlowId && flowsForSource.some((flow) => flow.flowId === selectedFlowId)
      ? selectedFlowId
      : flowFromQuery?.flowId ?? flowsForSource[0]?.flowId ?? flows[0]?.flowId ?? null;

    if (nextSource !== selectedSourceSystem) setSelectedSourceSystem(nextSource);
    if (nextFlowId !== selectedFlowId) setSelectedFlowId(nextFlowId);
    if (nextFlowId !== selectedFlowId) {
      setLatestSimulationResult(null);
      setLatestRealTestResult(null);
    }
  }, [flows, initialQuery, selectedFlowId, selectedSourceSystem]);

  function persistSelection(sourceSystem?: string | null, flowId?: string | null) {
    router.replace(buildWorkspaceHref(pathname, sourceSystem, flowId), { scroll: false });
  }

  function handleSelectSource(sourceSystemId: string) {
    const firstFlow = flows.find((flow) => flow.sourceSystem === sourceSystemId) ?? null;
    setSelectedSourceSystem(sourceSystemId);
    setSelectedFlowId(firstFlow?.flowId ?? null);
    persistSelection(sourceSystemId, firstFlow?.flowId ?? null);
  }

  function handleSelectFlow(flowId: string) {
    const flow = flows.find((row) => row.flowId === flowId);
    const sourceSystem = flow?.sourceSystem ?? selectedSourceSystem;
    setSelectedSourceSystem(sourceSystem ?? null);
    setSelectedFlowId(flowId);
    persistSelection(sourceSystem, flowId);
  }

  function openCreateSourceDialog() {
    setActionError(null);
    setActionMessage(null);
    if (!canCreateSource) { setActionError('Your current access does not allow Source System creation.'); return; }
    setCreateSourceOpen(true);
  }

  function handleSourceCreated(result: SourceSystemOnboardingResult) {
    setSourceSystems((current) => current.some((item) => item.sourceSystemId === result.source.sourceSystemId) ? current : [...current, result.source].sort((left, right) => left.sourceSystemId.localeCompare(right.sourceSystemId)));
    setSelectedSourceSystem(result.source.sourceSystemId);
    setSelectedFlowId(null);
    persistSelection(result.source.sourceSystemId, null);
    setActionMessage(`${result.source.displayName} and its default Intake are ready. Create the Source Flow next; machine secrets remain governed by Machine Access.`);
  }

  function openCreateFlowDialog() {
    setActionError(null);
    setActionMessage(null);
    if (!canCreateFlow) { setActionError('Your current access does not allow Source Flow creation.'); return; }
    setCreateFlowOpen(true);
  }

  function closeCreateFlowDialog() {
    setCreateFlowOpen(false);
    setActionError(null);
    router.replace(buildWorkspaceHref(pathname, selectedSourceSystem, selectedFlowId), { scroll: false });
  }

  async function handleCreateFlow(input: CreateSourceFlowInput) {
    const scopedTenantId = tenantId.trim();
    if (!scopedTenantId) {
      setActionError('Select a Workspace first.');
      return;
    }
    const sourceSystem = input.sourceSystem.trim();
    const flowCode = input.flowCode.trim();
    const flowName = input.flowName.trim();
    if (!sourceSystem || !flowCode || !flowName) {
      setActionError('Source System, Flow Code, and Flow Name are required.');
      return;
    }
    if (!sourceSystems.some((source) => source.sourceSystemId === sourceSystem)) {
      setActionError('Select a valid Source System before creating the Source Flow.');
      return;
    }
    if (flows.some((flow) => String(flow.flowCode ?? '').trim().toUpperCase() === flowCode.toUpperCase())) {
      setActionError(`Flow Code ${flowCode} already exists in this Workspace.`);
      return;
    }

    const flowId = generateId('flow');
    setActionBusy(true);
    setActionError(null);
    setActionMessage(null);
    try {
      const saved = await coreAdminApi.createDispatchFlow({
        tenantId: scopedTenantId,
        flowId,
        flowCode,
        flowName,
        sourceSystem,
        flowType: 'SOURCE_FLOW',
        defaultPoolId: input.defaultPoolId || undefined,
        status: 'DRAFT',
        defaultCandidatePoolMode: 'AGENT_POOL',
        defaultRoutingStrategy: input.defaultRoutingStrategy || 'LOWEST_LOAD',
        defaultIssueSyncPolicy: input.defaultIssueSyncPolicy,
        evidenceMutationSource: 'ADMIN_UI_CREATE_SOURCE_FLOW',
        description: input.description.trim() || undefined,
        rules: [],
        metadata: {
          routingModel: 'AGENT_POOL_FIRST',
          adminUiEditor: 'DISPATCH_WORKSPACE_FLOW_EDITOR',
        },
      }, scopedTenantId);
      setCreateFlowOpen(false);
      setSelectedSourceSystem(saved.sourceSystem ?? sourceSystem);
      setSelectedFlowId(saved.flowId);
      persistSelection(saved.sourceSystem ?? sourceSystem, saved.flowId);
      setActionMessage(saved.defaultPoolId
        ? 'Source Flow Draft created. Review Pool Members and runtime eligibility before activation.'
        : 'Source Flow Draft created. Assign a Default Agent Pool before activation.');
      reload();
    } catch (caught) {
      setActionError(apiErrorMessage(caught, 'Source Flow creation failed.'));
    } finally {
      setActionBusy(false);
    }
  }

  const sourceState = sectionState(sourceLoading, sourceError, sourceSystems.length === 0 && flows.length === 0);
  const flowState = sectionState(flowLoading, flowError, selectedSourceSystem ? flows.filter((flow) => flow.sourceSystem === selectedSourceSystem).length === 0 : flows.length === 0);
  const selectedSourceFlowCount = selectedSourceSystem ? flows.filter((flow) => flow.sourceSystem === selectedSourceSystem).length : flows.length;

  return (
    <section className="space-y-6">
      <SourceSystemOnboardingDialog
        open={createSourceOpen}
        tenantId={tenantId}
        existingSources={sourceSystems}
        onClose={() => setCreateSourceOpen(false)}
        onCreated={handleSourceCreated}
      />
      <CreateSourceFlowDialog
        open={createFlowOpen}
        sourceSystems={sourceSystems}
        flows={flows}
        pools={pools}
        initialSourceSystem={selectedSourceSystem ?? queryValue(initialQuery, 'sourceSystem') ?? null}
        busy={actionBusy}
        error={createFlowOpen ? actionError : null}
        onClose={closeCreateFlowDialog}
        onCreateSource={openCreateSourceDialog}
        onSubmit={(input) => { void handleCreateFlow(input); }}
      />
      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Workspace</div>
            <h1 className="mt-1 text-2xl font-black text-slate-950">Dispatch</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              Configure the whole dispatch path in one workspace: Source System → Classification → Required Capability → Agent Pool → Preview → Activate & Live Test.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <BeginnerGuideButton title="Dispatch setup from one workspace" description="The normal path stays on this page. Use inline selectors, drawers and dialogs; engineering diagnostics stay collapsed unless you need them." steps={[{title:'1. Source and classify the work',description:'Choose the Source System, then add clear classification rules for the work this Flow should handle.'},{title:'2. Choose capability and Agent Pool',description:'Select Canonical Capabilities when needed, then choose the Pool and add approved Agents.'},{title:'3. Preview, activate and verify',description:'Run a side-effect-free preview, activate only when ready, check the live path, then send one governed test event.'}]} />
            {canCreateSource ? <Button tone="secondary" onClick={openCreateSourceDialog} disabled={actionBusy || !tenantId.trim()}>+ Source System</Button> : null}
            {canCreateFlow ? <Button tone="primary" onClick={openCreateFlowDialog} disabled={actionBusy || !tenantId.trim()}>Create a Source Flow</Button> : null}
            <Button onClick={reload} disabled={allLoading}>{allLoading ? 'Loading' : 'Refresh'}</Button>
          </div>
        </div>
        <div className="mt-5 grid gap-3 md:grid-cols-4">
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Source Systems</div><div className="mt-1 text-2xl font-black text-slate-950">{sourceSystems.length || Array.from(new Set(flows.map((flow) => flow.sourceSystem).filter(Boolean))).length}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Source Flow</div><div className="mt-1 text-2xl font-black text-slate-950">{flows.length}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Selected Source Flows</div><div className="mt-1 text-2xl font-black text-slate-950">{selectedSourceFlowCount}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Agent Pool</div><div className="mt-1 text-2xl font-black text-slate-950">{pools.length}</div></div>
        </div>
      </section>

      {!tenantId.trim() ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">No active workspace is available. Sign in again or ask an administrator to review your Tenant membership before managing Source Systems, Source Flows, and Agent Pools.</div>
      ) : null}

      {actionMessage ? (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{actionMessage}</div>
      ) : null}

      {actionError ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{actionError}</div>
      ) : null}

      {(sourceError || flowError || poolError) ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">
          {[sourceError, flowError, poolError].filter(Boolean).join(';')}
        </div>
      ) : null}

      <DispatchSetupChecklist
        open={setupChecklistOpen}
        sourceSystems={sourceSystems}
        pools={pools}
        agents={agents}
        selectedFlow={selectedFlow}
        selectedPool={selectedDefaultPool}
        sourceError={sourceError}
        flowError={flowError}
        poolError={poolError}
        agentError={agentError}
        simulationResult={latestSimulationResult}
        realTestResult={latestRealTestResult}
        onCreateSource={openCreateSourceDialog}
        onCreateFlow={openCreateFlowDialog}
        onToggleOpen={() => setSetupChecklistOpen((current) => !current)}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(280px,0.9fr)_minmax(0,2.1fr)]">
        <SourceFlowMasterList
          sourceSystems={sourceSystems}
          flows={flows}
          pools={pools}
          selectedSourceSystem={selectedSourceSystem}
          selectedFlowId={selectedFlowId}
          sourceState={sourceState}
          flowState={flowState}
          sourceError={sourceError}
          flowError={flowError}
          loading={allLoading}
          onSelectSource={handleSelectSource}
          onSelectFlow={handleSelectFlow}
          onRefresh={reload}
          onCreateSource={canCreateSource ? openCreateSourceDialog : undefined}
        />
        <section className="min-w-0 space-y-5">
          {selectedFlow ? (
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <div className="text-xs font-black uppercase tracking-wide text-purple-700">Selected Source Flow</div>
                  <h2 className="mt-1 text-2xl font-black text-slate-950">{flowDisplay(selectedFlow)}</h2>
                  <p className="mt-2 text-sm leading-6 text-slate-600">Complete the business setup here. Advanced routing internals and migration evidence stay collapsed unless an engineer needs them.</p>
                </div>
                <StatusBadge status={selectedFlow.status ?? 'DRAFT'} />
              </div>
            </div>
          ) : null}
          {selectedFlow ? (
            <IssueTrackingContextCard
              title="Issue Tracking · inherited from Source System"
              configure={false}
              contexts={[{ sourceSystemId: selectedFlow.sourceSystem ?? '', taskType: null }]}
            />
          ) : null}
          <DispatchWorkspaceSections
            flow={selectedFlow}
            sourceSystems={sourceSystems}
            pools={pools}
            sourceLoading={sourceLoading}
            sourceError={sourceError}
            poolLoading={poolLoading}
            poolError={poolError}
            tenantId={tenantId}
            onReload={reload}
            onSimulationResultChange={setLatestSimulationResult}
            onRealTestResultChange={setLatestRealTestResult}
          />
        </section>
      </div>
    </section>
  );
}
