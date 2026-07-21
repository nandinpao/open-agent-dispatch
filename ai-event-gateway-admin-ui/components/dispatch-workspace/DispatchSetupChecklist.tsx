'use client';

import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import type {
  CoreAgentPoolView,
  CoreDispatchFlowAgentOptionView,
  CoreDispatchFlowView,
  CoreDispatchSimulationResponse,
  CoreEventIntakeDecisionResponse,
  CoreSourceSystem,
} from '@/lib/types/core';
import { flowDisplay, flowHealthIssues, poolDisplay } from './dispatchWorkspaceModel';

type ChecklistStatus = 'DONE' | 'ACTION_REQUIRED' | 'WAITING' | 'OPTIONAL';

type ChecklistItem = {
  id: string;
  title: string;
  description: string;
  status: ChecklistStatus;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
};

function statusLabel(status: ChecklistStatus): string {
  switch (status) {
    case 'DONE': return '完成';
    case 'WAITING': return '等待';
    case 'OPTIONAL': return '可選';
    default: return '需處理';
  }
}

function itemTone(status: ChecklistStatus): string {
  switch (status) {
    case 'DONE': return 'border-emerald-200 bg-emerald-50';
    case 'WAITING': return 'border-blue-200 bg-blue-50';
    case 'OPTIONAL': return 'border-slate-200 bg-slate-50';
    default: return 'border-amber-200 bg-amber-50';
  }
}

function hasApprovedAgent(agents: CoreDispatchFlowAgentOptionView[]): boolean {
  return agents.some((agent) => {
    const approved = String(agent.approvalStatus ?? '').toUpperCase();
    return agent.enabled !== false && ['APPROVED', 'ACTIVE', 'READY'].includes(approved);
  });
}

function hasRuntimeReadyAgent(agents: CoreDispatchFlowAgentOptionView[], pool?: CoreAgentPoolView | null): boolean {
  if ((pool?.availableAgentCount ?? 0) > 0) return true;
  return agents.some((agent) => agent.selectable === true || (agent.runtimeConnected === true && agent.capacityAvailable !== false && agent.heartbeatHealthy !== false));
}

function hasPoolMembers(pool?: CoreAgentPoolView | null): boolean {
  if (!pool) return false;
  return (pool.memberCount ?? pool.members?.length ?? 0) > 0;
}

function simulationDone(result?: CoreDispatchSimulationResponse | null): boolean {
  if (!result) return false;
  return result.sideEffectFree === true && (result.dispatchable === true || result.manualOnly === true || result.status === 'READY' || result.status === 'MANUAL_ASSIGNMENT_REQUIRED');
}

function realEventDone(result?: CoreEventIntakeDecisionResponse | null): boolean {
  if (!result) return false;
  return Boolean(result.eventId || result.taskId || result.taskCreated || result.decisionType);
}

export function DispatchSetupChecklist({
  open,
  sourceSystems,
  pools,
  agents,
  selectedFlow,
  selectedPool,
  sourceError,
  flowError,
  poolError,
  agentError,
  simulationResult,
  realTestResult,
  onCreateFlow,
  onToggleOpen,
}: Readonly<{
  open: boolean;
  sourceSystems: CoreSourceSystem[];
  pools: CoreAgentPoolView[];
  agents: CoreDispatchFlowAgentOptionView[];
  selectedFlow?: CoreDispatchFlowView | null;
  selectedPool?: CoreAgentPoolView | null;
  sourceError?: string | null;
  flowError?: string | null;
  poolError?: string | null;
  agentError?: string | null;
  simulationResult?: CoreDispatchSimulationResponse | null;
  realTestResult?: CoreEventIntakeDecisionResponse | null;
  onCreateFlow: () => void;
  onToggleOpen: () => void;
}>) {
  const sourceReady = sourceSystems.length > 0 || Boolean(selectedFlow?.sourceSystem);
  const flowReady = Boolean(selectedFlow?.flowId);
  const poolReady = Boolean(selectedPool?.poolId && selectedFlow?.defaultPoolId);
  const membersReady = hasPoolMembers(selectedPool);
  const agentReady = hasApprovedAgent(agents) || membersReady;
  const runtimeReady = hasRuntimeReadyAgent(agents, selectedPool);
  const configIssues = flowHealthIssues(selectedFlow, pools);
  const simulationReady = simulationDone(simulationResult);
  const realReady = realEventDone(realTestResult);
  const sourceLoadProblem = sourceError || flowError || poolError || agentError;

  const items: ChecklistItem[] = [
    {
      id: 'source-system',
      title: '建立來源系統',
      description: sourceReady ? `目前可用來源系統：${sourceSystems.length || 1}。` : '先建立來源系統，後續 Source Flow 才有明確業務入口。',
      status: sourceReady ? 'DONE' : 'ACTION_REQUIRED',
      actionLabel: '前往來源系統',
      actionHref: '/source-systems',
    },
    {
      id: 'agent',
      title: '建立並核准 Agent',
      description: agentReady ? '已有可作為 Pool Member 的 Agent 或既有工作池成員。' : '請先建立 Agent，完成核准後再加入工作池。Capability 僅作參考，不是此流程 Gate。',
      status: agentReady ? 'DONE' : 'ACTION_REQUIRED',
      actionLabel: '前往 Agent',
      actionHref: '/agents',
    },
    {
      id: 'pool',
      title: '建立工作池並加入 Agent',
      description: poolReady ? `Default Pool：${poolDisplay(selectedPool, selectedFlow?.defaultPoolId)}。` : '在預設派工區建立或選擇工作池，並加入 Pool Member Agent。',
      status: poolReady && membersReady ? 'DONE' : poolReady ? 'WAITING' : 'ACTION_REQUIRED',
    },
    {
      id: 'flow',
      title: '建立 Source Flow 並指定 Default Pool',
      description: flowReady ? `${flowDisplay(selectedFlow)}${configIssues.length ? `：${configIssues.join('；')}` : ' 已具備基本派工設定。'}` : '建立 Source Flow 後，將未命中特殊規則的事件送入 Default Pool。',
      status: flowReady && configIssues.length === 0 ? 'DONE' : flowReady ? 'WAITING' : 'ACTION_REQUIRED',
      actionLabel: flowReady ? undefined : '建立 Source Flow',
      onAction: flowReady ? undefined : onCreateFlow,
    },
    {
      id: 'runtime',
      title: '確認 Runtime 可接單',
      description: runtimeReady ? 'Runtime evidence 顯示至少一個 Agent 可作為派工候選。' : '請確認 Agent 已連線、heartbeat 正常、容量未滿，且沒有 backoff 或停用狀態。',
      status: runtimeReady ? 'DONE' : 'WAITING',
      actionLabel: '查看 Agent Runtime',
      actionHref: '/agents',
    },
    {
      id: 'simulation',
      title: '執行派工模擬',
      description: simulationReady ? 'no-side-effect simulation 已產生可用派工 Evidence。' : '使用測試與啟用區塊執行 Simulation；它不會建立 Task、Assignment 或 Delivery。',
      status: simulationReady ? 'DONE' : poolReady ? 'WAITING' : 'OPTIONAL',
      actionLabel: '前往模擬區塊',
      actionHref: '#dispatch-workspace-simulation',
    },
    {
      id: 'real-event',
      title: '送出真實測試事件',
      description: realReady ? `真實測試事件已送出${realTestResult?.taskId ? `，Task：${realTestResult.taskId}` : ''}。` : 'Simulation 通過後，再送出真實測試事件建立正式 Task，確認 Event → Task → Assignment → Delivery 流程。',
      status: realReady ? 'DONE' : simulationReady ? 'WAITING' : 'OPTIONAL',
      actionLabel: '前往真實測試',
      actionHref: '#dispatch-workspace-real-test',
    },
  ];

  const doneCount = items.filter((item) => item.status === 'DONE').length;
  const actionRequiredCount = items.filter((item) => item.status === 'ACTION_REQUIRED').length;
  const setupReady = doneCount === items.length && !sourceLoadProblem;
  const summaryStatus = setupReady ? 'READY' : actionRequiredCount ? 'NOT_READY' : 'IN_PROGRESS';

  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm" id="dispatch-workspace-setup-check">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-purple-700">Beginner Journey / Setup Check</div>
          <h2 className="mt-1 text-xl font-black text-slate-950">設定檢查</h2>
          <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
            依序完成來源系統、Agent、工作池、Source Flow、Runtime、Simulation 與真實測試事件。此流程不要求理解 Capability、Profile 或 Scope。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={summaryStatus} />
          <Button size="xs" onClick={onToggleOpen}>{open ? '收合設定檢查' : '重新開啟設定檢查'}</Button>
        </div>
      </div>

      {sourceLoadProblem ? (
        <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold leading-6 text-rose-900">
          設定檢查目前有載入問題：{[sourceError, flowError, poolError, agentError].filter(Boolean).join('；')}
        </div>
      ) : null}

      {open ? (
        <div className="mt-5 grid gap-3 xl:grid-cols-7">
          {items.map((item, index) => (
            <div key={item.id} className={`rounded-2xl border p-4 ${itemTone(item.status)}`}>
              <div className="flex items-start justify-between gap-3">
                <div className="rounded-full bg-white px-2.5 py-1 text-xs font-black text-slate-600">{index + 1}</div>
                <StatusBadge status={statusLabel(item.status)} />
              </div>
              <h3 className="mt-3 text-sm font-black text-slate-950">{item.title}</h3>
              <p className="mt-2 text-xs font-bold leading-5 text-slate-600">{item.description}</p>
              {item.actionHref ? (
                <Link className="mt-3 inline-flex rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-black text-slate-700 hover:bg-slate-50" href={item.actionHref}>{item.actionLabel}</Link>
              ) : item.onAction ? (
                <Button size="xs" className="mt-3" onClick={item.onAction}>{item.actionLabel}</Button>
              ) : null}
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm font-bold text-slate-600">
          已收合。需要再次檢查設定時，點選「重新開啟設定檢查」。目前完成 {doneCount} / {items.length} 項。
        </div>
      )}
    </section>
  );
}
