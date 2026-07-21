import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import type { CoreAgentPoolView, CoreDispatchFlowView, CoreSourceSystem } from '@/lib/types/core';
import { flowDisplay, flowHealthIssues, isActiveStatus, sourceDisplay, sourceHealthLabel } from './dispatchWorkspaceModel';
import { WorkspaceStateBlock } from './WorkspaceStateBlock';

function flowStatusLabel(flow: CoreDispatchFlowView, pools: CoreAgentPoolView[]): string {
  const issues = flowHealthIssues(flow, pools);
  if (issues.length) return issues[0] ?? '需要補設定';
  return isActiveStatus(flow.status) ? '正常' : '尚未啟用';
}

export function SourceFlowMasterList({
  sourceSystems,
  flows,
  pools,
  selectedSourceSystem,
  selectedFlowId,
  sourceState,
  flowState,
  sourceError,
  flowError,
  loading,
  onSelectSource,
  onSelectFlow,
  onRefresh,
}: Readonly<{
  sourceSystems: CoreSourceSystem[];
  flows: CoreDispatchFlowView[];
  pools: CoreAgentPoolView[];
  selectedSourceSystem?: string | null;
  selectedFlowId?: string | null;
  sourceState: 'loading' | 'ready' | 'empty' | 'error';
  flowState: 'loading' | 'ready' | 'empty' | 'error';
  sourceError?: string | null;
  flowError?: string | null;
  loading: boolean;
  onSelectSource: (sourceSystemId: string) => void;
  onSelectFlow: (flowId: string) => void;
  onRefresh: () => void;
}>) {
  const visibleSources = sourceSystems.length
    ? sourceSystems
    : Array.from(new Set(flows.map((flow) => flow.sourceSystem).filter(Boolean))).map((sourceSystemId) => ({
      sourceSystemId: String(sourceSystemId),
      displayName: String(sourceSystemId),
      status: 'ACTIVE',
    }));
  const visibleFlows = flows.filter((flow) => !selectedSourceSystem || flow.sourceSystem === selectedSourceSystem);

  return (
    <aside className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3 px-1 pb-4">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-purple-700">Source / Flow</div>
          <h2 className="mt-1 text-lg font-black text-slate-950">來源與 Source Flow</h2>
          <p className="mt-1 text-xs leading-5 text-slate-500">先選來源系統，再選要維護的 Source Flow。</p>
        </div>
        <Button size="xs" onClick={onRefresh} disabled={loading}>{loading ? '載入中' : '重新整理'}</Button>
      </div>

      {sourceState === 'empty' ? (
        <div className="rounded-2xl border border-dashed border-purple-300 bg-purple-50 p-5">
          <div className="text-xs font-black uppercase tracking-wide text-purple-700">空資料庫導引</div>
          <h3 className="mt-1 text-base font-black text-slate-950">先建立第一個來源系統</h3>
          <p className="mt-2 text-sm font-bold leading-6 text-slate-600">空資料庫的起點是 Source System。建立來源後，回到本頁建立 Source Flow、Default Pool 與 Pool Member。</p>
          <Link href="/source-systems" className="mt-4 inline-flex rounded-xl border border-purple-200 bg-white px-4 py-2 text-sm font-black text-purple-700 hover:bg-purple-50">前往來源系統</Link>
        </div>
      ) : <WorkspaceStateBlock state={sourceState} title="尚無來源系統" description="請先建立來源系統，再建立 Source Flow。" error={sourceError} />}
      {sourceState === 'ready' ? (
        <div className="space-y-3">
          {visibleSources.map((source) => {
            const sourceFlows = flows.filter((flow) => flow.sourceSystem === source.sourceSystemId);
            const active = selectedSourceSystem === source.sourceSystemId;
            return (
              <section key={source.sourceSystemId} className={`rounded-2xl border p-3 transition ${active ? 'border-purple-300 bg-purple-50' : 'border-slate-200 bg-slate-50'}`}>
                <button type="button" className="w-full text-left" onClick={() => onSelectSource(source.sourceSystemId)}>
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-black text-slate-950">{sourceDisplay(source)}</div>
                      <div className="mt-1 text-xs font-bold text-slate-500">{sourceFlows.length} 條 Flow · {sourceHealthLabel(source.sourceSystemId, flows, pools)}</div>
                    </div>
                    <StatusBadge status={isActiveStatus(source.status) ? 'ACTIVE' : source.status ?? 'UNKNOWN'} />
                  </div>
                </button>
                {active ? (
                  <div className="mt-3 space-y-2">
                    {flowState === 'empty' ? (
                      <div className="rounded-2xl border border-dashed border-amber-300 bg-amber-50 p-4 text-sm font-bold leading-6 text-amber-900">
                        此來源尚無 Source Flow。請使用頁面右上角「建立 Source Flow」，再指定 Default Pool。
                      </div>
                    ) : <WorkspaceStateBlock state={flowState} title="此來源尚無 Source Flow" description="可從右側建立 Source Flow 或補齊 Default Pool。" error={flowError} />}
                    {visibleFlows.map((flow) => {
                      const flowActive = selectedFlowId === flow.flowId;
                      return (
                        <button
                          type="button"
                          key={flow.flowId}
                          onClick={() => onSelectFlow(flow.flowId)}
                          className={`w-full rounded-xl border p-3 text-left transition ${flowActive ? 'border-purple-400 bg-white shadow-sm' : 'border-slate-200 bg-white hover:border-purple-200'}`}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="truncate text-sm font-black text-slate-900">{flowDisplay(flow)}</div>
                              <div className="mt-1 truncate text-xs font-bold text-slate-500">{flow.defaultPoolId ? `Default Pool：${flow.defaultPoolId}` : '尚未指定 Default Pool'}</div>
                            </div>
                            <StatusBadge status={flow.status ?? 'DRAFT'} />
                          </div>
                          <div className="mt-2 text-xs font-bold text-slate-500">{flowStatusLabel(flow, pools)}</div>
                        </button>
                      );
                    })}
                  </div>
                ) : null}
              </section>
            );
          })}
        </div>
      ) : null}
    </aside>
  );
}
