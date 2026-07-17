'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreAgentPoolMemberView, CoreAgentPoolView, CoreDispatchFlowAgentOptionView, CoreSourceSystem } from '@/lib/types/core';

type PoolEditorState = {
  poolId?: string;
  poolCode: string;
  poolName: string;
  sourceSystem: string;
  poolType: string;
  selectionStrategy: string;
  status: string;
  description: string;
  memberIds: string[];
};

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_\-.]/g, '_').replace(/^_+|_+$/g, '');
}

function generateId(prefix: string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

function sourceDisplay(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

function emptyEditor(sourceSystem = ''): PoolEditorState {
  const normalizedSource = normalizeCode(sourceSystem);
  return {
    poolCode: normalizedSource ? `${normalizedSource}_TRIAGE_POOL` : '',
    poolName: normalizedSource ? `${normalizedSource} 一線分類池` : '',
    sourceSystem: normalizedSource,
    poolType: 'TRIAGE',
    selectionStrategy: 'LOWEST_LOAD',
    status: 'ACTIVE',
    description: '未知事件與未分類事件先進入此 Pool，再由 Triage Agent 分類。',
    memberIds: [],
  };
}

function editorFromPool(pool: CoreAgentPoolView): PoolEditorState {
  return {
    poolId: pool.poolId,
    poolCode: pool.poolCode ?? '',
    poolName: pool.poolName ?? '',
    sourceSystem: pool.sourceSystem ?? '',
    poolType: pool.poolType ?? 'RESOLUTION',
    selectionStrategy: pool.selectionStrategy ?? 'LOWEST_LOAD',
    status: String(pool.status ?? 'ACTIVE').toUpperCase(),
    description: pool.description ?? '',
    memberIds: (pool.members ?? []).filter((member) => String(member.memberStatus ?? 'ACTIVE').toUpperCase() !== 'RETIRED').map((member) => member.agentId),
  };
}

function memberViews(editor: PoolEditorState, agents: CoreDispatchFlowAgentOptionView[]): CoreAgentPoolMemberView[] {
  return editor.memberIds.map((agentId) => {
    const agent = agents.find((candidate) => candidate.agentId === agentId);
    return {
      poolId: editor.poolId ?? '',
      poolCode: normalizeCode(editor.poolCode),
      agentId,
      agentName: agent?.agentName ?? agentId,
      memberStatus: 'ACTIVE',
      priority: 100,
      weight: 1,
      approvalStatus: agent?.approvalStatus,
      runtimeStatus: agent?.runtimeStatus,
      metadata: { phase32gPoolMember: true },
    };
  });
}

function PoolEditorDialog({
  open,
  editor,
  sourceSystems,
  agents,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  editor: PoolEditorState;
  sourceSystems: CoreSourceSystem[];
  agents: CoreDispatchFlowAgentOptionView[];
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<PoolEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  if (!open) return null;

  function toggleAgent(agentId: string) {
    onChange({ memberIds: editor.memberIds.includes(agentId) ? editor.memberIds.filter((id) => id !== agentId) : [...editor.memberIds, agentId] });
  }

  return (
    <div className="fixed inset-0 z-[90] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 sm:p-8" role="dialog" aria-modal="true" aria-label="Agent Pool 表單">
      <div className="w-full max-w-5xl rounded-3xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 rounded-t-3xl border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Agent Pool / Work Queue</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editor.poolId ? '編輯 Agent Pool' : '建立 Agent Pool'}</h2>
            <p className="mt-1 text-sm text-slate-600">Pool 是 Phase 32-G 的派單目標；Capability 僅為 Agent 能力標籤，不會阻擋第一版派單。</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="關閉">×</button>
        </div>
        <div className="space-y-6 p-6">
          {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          <section className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">1. Pool 基本資料</div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <label className={labelClass}>來源系統
                <select className={inputClass} value={editor.sourceSystem} onChange={(event) => {
                  const sourceSystem = normalizeCode(event.target.value);
                  const next = emptyEditor(sourceSystem);
                  onChange({ sourceSystem, poolCode: editor.poolCode || next.poolCode, poolName: editor.poolName || next.poolName });
                }}>
                  <option value="">共用 Pool</option>
                  {sourceSystems.map((source) => <option key={source.sourceSystemId} value={source.sourceSystemId}>{sourceDisplay(source)}</option>)}
                </select>
              </label>
              <label className={labelClass}>Pool 類型
                <select className={inputClass} value={editor.poolType} onChange={(event) => onChange({ poolType: event.target.value })}>
                  <option value="TRIAGE">TRIAGE：一線分類池</option>
                  <option value="RESOLUTION">RESOLUTION：處理池</option>
                  <option value="ESCALATION">ESCALATION：升級處理池</option>
                  <option value="MANUAL_REVIEW">MANUAL_REVIEW：人工審核池</option>
                </select>
              </label>
              <label className={labelClass}>Pool Code<span className="text-rose-600"> *</span><input className={inputClass} value={editor.poolCode} onChange={(event) => onChange({ poolCode: normalizeCode(event.target.value) })} placeholder="ERP_TRIAGE_POOL" /></label>
              <label className={labelClass}>Pool 名稱<span className="text-rose-600"> *</span><input className={inputClass} value={editor.poolName} onChange={(event) => onChange({ poolName: event.target.value })} placeholder="ERP 一線分類池" /></label>
              <label className={labelClass}>選人策略
                <select className={inputClass} value={editor.selectionStrategy} onChange={(event) => onChange({ selectionStrategy: event.target.value })}>
                  <option value="LOWEST_LOAD">LOWEST_LOAD：低負載優先</option>
                  <option value="WEIGHTED_SCORE">WEIGHTED_SCORE：權重分數</option>
                  <option value="ROUND_ROBIN">ROUND_ROBIN：輪詢</option>
                  <option value="LOCAL_FIRST">LOCAL_FIRST：本地優先</option>
                  <option value="MANUAL_ONLY">MANUAL_ONLY：只進工作佇列</option>
                </select>
              </label>
              <label className={labelClass}>狀態
                <select className={inputClass} value={editor.status} onChange={(event) => onChange({ status: event.target.value })}>
                  <option value="ACTIVE">啟用</option>
                  <option value="DRAFT">草稿</option>
                  <option value="DISABLED">停用</option>
                </select>
              </label>
              <label className={`${labelClass} md:col-span-2`}>說明<textarea className={`${inputClass} min-h-24`} value={editor.description} onChange={(event) => onChange({ description: event.target.value })} /></label>
            </div>
          </section>
          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <div className="flex items-center justify-between gap-3">
              <div><div className="text-xs font-black uppercase tracking-wide text-slate-500">2. Pool Members</div><h3 className="mt-1 text-lg font-black">把 Agent 加入這個 Pool</h3></div>
              <span className="text-xs font-bold text-slate-500">已選 {editor.memberIds.length}</span>
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              {agents.map((agent) => {
                const checked = editor.memberIds.includes(agent.agentId);
                return (
                  <label key={agent.agentId} className={`flex cursor-pointer items-start gap-3 rounded-2xl border p-4 ${checked ? 'border-purple-300 bg-purple-50' : 'border-slate-200 bg-slate-50'}`}>
                    <input type="checkbox" className="mt-1 h-4 w-4" checked={checked} onChange={() => toggleAgent(agent.agentId)} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-black text-slate-950">{agent.agentName ?? agent.agentId}</span>
                      <span className="mt-1 block text-xs text-slate-500">{agent.agentId}</span>
                      <span className="mt-2 flex flex-wrap gap-2"><StatusBadge status={agent.approvalStatus ?? 'UNKNOWN'} /><StatusBadge status={agent.runtimeConnected ? 'CONNECTED' : 'NOT_CONNECTED'} /></span>
                    </span>
                  </label>
                );
              })}
              {!agents.length ? <EmptyState title="沒有 Agent" description="請先建立並核准 Agent，再回到 Pool 加入成員。" compact /> : null}
            </div>
          </section>
        </div>
        <div className="sticky bottom-0 flex flex-col-reverse gap-3 rounded-b-3xl border-t border-slate-200 bg-white px-6 py-4 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={busy}>取消</Button>
          <Button tone="primary" onClick={onSave} disabled={busy}>{busy ? '儲存中…' : '儲存 Agent Pool'}</Button>
        </div>
      </div>
    </div>
  );
}

export function AgentPoolManagementConsole() {
  const { selectedTenantId: tenantId } = useAuth();
  const [sourceSystems, setSourceSystems] = useState<CoreSourceSystem[]>([]);
  const [agents, setAgents] = useState<CoreDispatchFlowAgentOptionView[]>([]);
  const [pools, setPools] = useState<CoreAgentPoolView[]>([]);
  const [editor, setEditor] = useState<PoolEditorState>(() => emptyEditor());
  const [editorOpen, setEditorOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const activeSources = useMemo(() => sourceSystems.filter((source) => String(source.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE'), [sourceSystems]);

  const reload = useCallback(async () => {
    const scopedTenantId = tenantId.trim();
    if (!scopedTenantId) {
      setPools([]); setSourceSystems([]); setAgents([]); return;
    }
    setLoading(true); setError(null);
    try {
      const [sourceRows, agentRows, poolRows] = await Promise.all([
        coreAdminApi.getSourceSystems(scopedTenantId),
        coreAdminApi.getDispatchFlowAgentOptions(scopedTenantId),
        coreAdminApi.getAgentPools(scopedTenantId),
      ]);
      setSourceSystems(sourceRows);
      setAgents(agentRows);
      setPools(poolRows);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '無法載入 Agent Pool 資料。');
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void reload(); }, [reload]);

  function openCreate(sourceSystem?: string) {
    setEditor(emptyEditor(sourceSystem));
    setEditorOpen(true);
    setError(null);
  }

  function openEdit(pool: CoreAgentPoolView) {
    setEditor(editorFromPool(pool));
    setEditorOpen(true);
    setError(null);
  }

  async function savePool() {
    const scopedTenantId = tenantId.trim();
    if (!scopedTenantId) { setError('請先選擇 Workspace。'); return; }
    const poolCode = normalizeCode(editor.poolCode);
    if (!poolCode) { setError('Pool Code 為必填。'); return; }
    if (!editor.poolName.trim()) { setError('Pool 名稱為必填。'); return; }
    setBusy(true); setError(null); setMessage(null);
    try {
      const body: CoreAgentPoolView = {
        tenantId: scopedTenantId,
        poolId: editor.poolId ?? generateId('pool'),
        poolCode,
        poolName: editor.poolName.trim(),
        sourceSystem: normalizeCode(editor.sourceSystem) || undefined,
        poolType: editor.poolType,
        selectionStrategy: editor.selectionStrategy,
        status: editor.status,
        description: editor.description.trim(),
        members: memberViews(editor, agents),
        metadata: { phase32gAgentPoolAdminUi: true },
      };
      const saved = editor.poolId
        ? await coreAdminApi.updateAgentPool(editor.poolId, body, scopedTenantId)
        : await coreAdminApi.createAgentPool(body, scopedTenantId);
      setEditorOpen(false);
      setMessage(`Agent Pool「${saved.poolName ?? saved.poolCode ?? saved.poolId}」已儲存。`);
      await reload();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '儲存 Agent Pool 失敗。');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="space-y-5">
      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Agent Pools / Work Queues</div>
            <h1 className="mt-1 text-2xl font-black text-slate-950">Agent Pool 管理</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Phase 32-G 的設定主體是 Pool。先建立 TRIAGE_POOL 與各業務處理池，再由 Source Flow 指到 Pool；新手不需要碰 Capability 也能完成派單。</p>
          </div>
          <Button tone="primary" onClick={() => openCreate(activeSources[0]?.sourceSystemId)} disabled={!tenantId.trim()}>建立 Agent Pool</Button>
        </div>
        <div className="mt-5 grid gap-3 sm:grid-cols-4">
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">全部 Pool</div><div className="mt-1 text-2xl font-black">{pools.length}</div></div>
          <div className="rounded-2xl bg-emerald-50 p-4"><div className="text-xs font-black text-emerald-700">TRIAGE</div><div className="mt-1 text-2xl font-black text-emerald-950">{pools.filter((pool) => pool.poolType === 'TRIAGE').length}</div></div>
          <div className="rounded-2xl bg-blue-50 p-4"><div className="text-xs font-black text-blue-700">可用 Agent</div><div className="mt-1 text-2xl font-black text-blue-950">{pools.reduce((sum, pool) => sum + (pool.availableAgentCount ?? 0), 0)}</div></div>
          <div className="rounded-2xl bg-amber-50 p-4"><div className="text-xs font-black text-amber-700">空 Pool</div><div className="mt-1 text-2xl font-black text-amber-950">{pools.filter((pool) => !(pool.memberCount ?? 0)).length}</div></div>
        </div>
      </div>

      {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{message}</div> : null}
      {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}

      <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {pools.map((pool) => (
          <button key={pool.poolId} type="button" onClick={() => openEdit(pool)} className="rounded-3xl border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:border-purple-200 hover:bg-purple-50">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="truncate font-black text-slate-950">{pool.poolName ?? pool.poolCode}</div>
                <div className="mt-1 truncate text-xs text-slate-500">{pool.poolCode} · {pool.sourceSystem ?? '共用'}</div>
              </div>
              <StatusBadge status={pool.status ?? 'ACTIVE'} />
            </div>
            <div className="mt-4 flex flex-wrap gap-2 text-[11px] font-bold text-slate-600">
              <span className="rounded-full bg-slate-100 px-2 py-1">{pool.poolType ?? 'RESOLUTION'}</span>
              <span className="rounded-full bg-slate-100 px-2 py-1">{pool.selectionStrategy ?? 'LOWEST_LOAD'}</span>
              <span className="rounded-full bg-slate-100 px-2 py-1">成員 {pool.memberCount ?? pool.members?.length ?? 0}</span>
              <span className="rounded-full bg-slate-100 px-2 py-1">可用 {pool.availableAgentCount ?? 0}</span>
            </div>
            <div className="mt-4 space-y-2">
              {(pool.members ?? []).slice(0, 4).map((member) => (
                <div key={member.agentId} className="rounded-2xl bg-slate-50 p-3">
                  <div className="truncate text-sm font-black text-slate-900">{member.agentName ?? member.agentId}</div>
                  <div className="mt-1 flex flex-wrap gap-2"><StatusBadge status={member.approvalStatus ?? 'UNKNOWN'} /><StatusBadge status={member.runtimeStatus ?? 'UNKNOWN'} /></div>
                </div>
              ))}
              {!(pool.members ?? []).length ? <div className="rounded-2xl border border-dashed border-amber-200 bg-amber-50 p-3 text-sm font-semibold text-amber-900">此 Pool 尚未加入 Agent，Source Flow 指到這裡會顯示 Pool blocker。</div> : null}
            </div>
          </button>
        ))}
        {!pools.length && !loading ? <EmptyState title="尚無 Agent Pool" description="先建立每個 Source System 的 TRIAGE_POOL，再建立已知事件的處理 Pool。" /> : null}
      </div>

      <PoolEditorDialog
        open={editorOpen}
        editor={editor}
        sourceSystems={activeSources}
        agents={agents}
        busy={busy}
        error={error}
        onChange={(patch) => setEditor((current) => ({ ...current, ...patch }))}
        onClose={() => setEditorOpen(false)}
        onSave={() => void savePool()}
      />
    </section>
  );
}
