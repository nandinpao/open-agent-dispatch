'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { ApiError } from '@/lib/api/client';
import type { CoreAgentPoolView, CoreDispatchFlowAgentOptionView, CoreDispatchFlowView, CoreDispatchSimulationResponse, CoreEventIntakeDecisionResponse, CoreSourceSystem } from '@/lib/types/core';
import { DispatchWorkspaceSections } from './DispatchWorkspaceSections';
import { DispatchSetupChecklist } from './DispatchSetupChecklist';
import {
  type DispatchWorkspaceQuery,
  flowDisplay,
  queryValue,
  sectionState,
} from './dispatchWorkspaceModel';
import { SourceFlowMasterList } from './SourceFlowMasterList';

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

function normalizeCode(value: string | undefined | null): string {
  return String(value ?? '').trim().toUpperCase().replace(/[^A-Z0-9_\-.]/g, '_').replace(/^_+|_+$/g, '');
}

function generateId(prefix: string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

export function DispatchWorkspace({ initialQuery }: Readonly<{ initialQuery?: DispatchWorkspaceQuery }> = {}) {
  const router = useRouter();
  const pathname = usePathname();
  const { selectedTenantId } = useAuth();
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
      setSourceError(apiErrorMessage(caught, '來源系統載入失敗。'));
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
      setFlowError(apiErrorMessage(caught, 'Source Flow 載入失敗。'));
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
      setPoolError(apiErrorMessage(caught, 'Agent Pool 載入失敗。'));
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
      setAgentError(apiErrorMessage(caught, 'Agent 清單載入失敗。'));
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

  async function handleCreateFlow() {
    const scopedTenantId = tenantId.trim();
    if (!scopedTenantId) {
      setActionError('請先選擇 Workspace。');
      return;
    }
    const sourceSystem = selectedSourceSystem ?? sourceSystems[0]?.sourceSystemId ?? flows[0]?.sourceSystem ?? '';
    if (!sourceSystem) {
      setActionError('請先建立來源系統，再建立 Source Flow。');
      return;
    }
    const normalizedSource = normalizeCode(sourceSystem);
    const flowId = generateId('flow');
    setActionBusy(true);
    setActionError(null);
    setActionMessage(null);
    try {
      const saved = await coreAdminApi.createDispatchFlow({
        tenantId: scopedTenantId,
        flowId,
        flowCode: `${normalizedSource}_DEFAULT_FLOW`,
        flowName: `${normalizedSource} 預設派工`,
        sourceSystem,
        flowType: 'SOURCE_FLOW',
        status: 'DRAFT',
        defaultCandidatePoolMode: 'AGENT_POOL',
        defaultRoutingStrategy: 'LOWEST_LOAD',
        description: '未符合特殊分類規則的事件會進入此 Source Flow 的 Default Pool。',
        rules: [],
        metadata: {
          routingModel: 'AGENT_POOL_FIRST',
          adminUiEditor: 'DISPATCH_WORKSPACE_FLOW_EDITOR',
        },
      }, scopedTenantId);
      setSelectedSourceSystem(saved.sourceSystem ?? sourceSystem);
      setSelectedFlowId(saved.flowId);
      persistSelection(saved.sourceSystem ?? sourceSystem, saved.flowId);
      setActionMessage('Source Flow 已建立，請指定 Default Pool。');
      reload();
    } catch (caught) {
      setActionError(apiErrorMessage(caught, 'Source Flow 建立失敗。'));
    } finally {
      setActionBusy(false);
    }
  }

  const sourceState = sectionState(sourceLoading, sourceError, sourceSystems.length === 0 && flows.length === 0);
  const flowState = sectionState(flowLoading, flowError, selectedSourceSystem ? flows.filter((flow) => flow.sourceSystem === selectedSourceSystem).length === 0 : flows.length === 0);
  const selectedSourceFlowCount = selectedSourceSystem ? flows.filter((flow) => flow.sourceSystem === selectedSourceSystem).length : flows.length;

  return (
    <section className="space-y-6">
      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Workspace</div>
            <h1 className="mt-1 text-2xl font-black text-slate-950">派工設定</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              本工作區採 Source System / Source Flow 主導設計，並提供互動式設定檢查，協助管理員依序完成來源系統、Agent、工作池、Source Flow、Runtime、派工模擬與真實測試事件。Current setup path：來源系統 → Source Flow → Agent Pool → Pool Member Agent。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button tone="primary" onClick={() => { void handleCreateFlow(); }} disabled={actionBusy || !tenantId.trim()}>建立 Source Flow</Button>
            <Button onClick={reload} disabled={allLoading}>{allLoading ? '載入中' : '重新整理'}</Button>
          </div>
        </div>
        <div className="mt-5 grid gap-3 md:grid-cols-4">
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">來源系統</div><div className="mt-1 text-2xl font-black text-slate-950">{sourceSystems.length || Array.from(new Set(flows.map((flow) => flow.sourceSystem).filter(Boolean))).length}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Source Flow</div><div className="mt-1 text-2xl font-black text-slate-950">{flows.length}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">目前來源 Flow</div><div className="mt-1 text-2xl font-black text-slate-950">{selectedSourceFlowCount}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">工作池</div><div className="mt-1 text-2xl font-black text-slate-950">{pools.length}</div></div>
        </div>
      </section>

      {!tenantId.trim() ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">請先選擇 Workspace，才會載入來源系統、Source Flow 與 Agent Pool。</div>
      ) : null}

      {actionMessage ? (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{actionMessage}</div>
      ) : null}

      {actionError ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{actionError}</div>
      ) : null}

      {(sourceError || flowError || poolError) ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">
          {[sourceError, flowError, poolError].filter(Boolean).join('；')}
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
        onCreateFlow={() => { void handleCreateFlow(); }}
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
        />
        <section className="min-w-0 space-y-5">
          {selectedFlow ? (
            <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <div className="text-xs font-black uppercase tracking-wide text-purple-700">Selected Source Flow</div>
                  <h2 className="mt-1 text-2xl font-black text-slate-950">{flowDisplay(selectedFlow)}</h2>
                  <p className="mt-2 text-sm leading-6 text-slate-600">URL 會保存目前選取的 sourceSystem 與 flowId，重新整理後仍回到同一個工作區上下文。</p>
                </div>
                <StatusBadge status={selectedFlow.status ?? 'DRAFT'} />
              </div>
            </div>
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
