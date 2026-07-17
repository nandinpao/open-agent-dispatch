'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { EmptyState } from '@/components/common/EmptyState';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type { CoreDispatchFlowView, CoreSourceSystem, CoreSourceSystemCommand } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

interface SourceSystemRow extends CoreSourceSystem {
  flowCount: number;
  activeFlowCount: number;
  eventTypes: string[];
  lastFlowUpdatedAt?: string;
}

type SourceEditorState = CoreSourceSystemCommand;

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_.-]/g, '_').replace(/^_+|_+$/g, '');
}

function isActiveFlow(status?: string | null): boolean {
  const normalized = String(status ?? '').trim().toUpperCase();
  return normalized === 'ACTIVE' || normalized === 'ENABLED';
}

function unique(values: Array<string | undefined | null>): string[] {
  return Array.from(new Set(values.map((value) => String(value ?? '').trim()).filter(Boolean))).sort();
}

function sourceLabel(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

function mergeSourceRows(sources: CoreSourceSystem[], flows: CoreDispatchFlowView[]): SourceSystemRow[] {
  const byCode = new Map<string, SourceSystemRow>();
  for (const source of sources) {
    byCode.set(source.sourceSystemId, {
      ...source,
      flowCount: 0,
      activeFlowCount: 0,
      eventTypes: [],
    });
  }
  for (const flow of flows) {
    const code = String(flow.sourceSystem ?? '').trim();
    const current = code ? byCode.get(code) : undefined;
    if (!current) continue;
    byCode.set(code, {
      ...current,
      flowCount: current.flowCount + 1,
      activeFlowCount: current.activeFlowCount + (isActiveFlow(flow.status) ? 1 : 0),
      eventTypes: unique([...(current.eventTypes ?? []), ...(flow.rules ?? []).map((rule) => rule.eventType)]),
      lastFlowUpdatedAt: flow.updatedAt ?? current.lastFlowUpdatedAt,
    });
  }
  return Array.from(byCode.values()).sort((left, right) => left.sourceSystemId.localeCompare(right.sourceSystemId));
}

function createFlowHref(sourceSystemId?: string): string {
  const query = new URLSearchParams({ create: '1' });
  if (sourceSystemId) query.set('sourceSystem', sourceSystemId);
  return `/dispatch-flows?${query.toString()}`;
}

function SourceSystemEditor({
  open,
  editor,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  editor: SourceEditorState;
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<SourceEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[80] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 sm:p-8" role="dialog" aria-modal="true" aria-label="來源系統表單">
      <div className="w-full max-w-2xl rounded-3xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 rounded-t-3xl border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-blue-700">Source System Master</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editor.sourceSystemId ? '維護來源系統' : '新增來源系統'}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">來源系統只是事件來源主檔，不包含派工角色、特殊能力、Profile、Scope 或任何預設 Agent 覆蓋。</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="關閉">×</button>
        </div>
        <div className="space-y-4 p-6">
          {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          <label className={labelClass}>來源識別碼<span className="text-rose-600"> *</span>
            <input className={inputClass} value={editor.sourceSystemId} onChange={(event) => onChange({ sourceSystemId: normalizeCode(event.target.value) })} placeholder="例如：SRC_E2E_7F28、FACTORY_IOT_01" />
          </label>
          <label className={labelClass}>顯示名稱<span className="text-rose-600"> *</span>
            <input className={inputClass} value={editor.displayName} onChange={(event) => onChange({ displayName: event.target.value })} placeholder="例如：工廠 IoT 事件來源" />
          </label>
          <label className={labelClass}>狀態
            <select className={inputClass} value={editor.status ?? 'ACTIVE'} onChange={(event) => onChange({ status: event.target.value })}>
              <option value="ACTIVE">啟用</option>
              <option value="DISABLED">停用</option>
            </select>
          </label>
          <label className={labelClass}>說明
            <textarea className={`${inputClass} min-h-28`} value={editor.description ?? ''} onChange={(event) => onChange({ description: event.target.value })} placeholder="說明此來源代表哪個事件入口。不要填派工角色、能力或 Agent 覆蓋規則。" />
          </label>
          <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
            建立來源後，請到「派工流程」選擇此來源並設定事件條件與處理 Agent。建立來源本身不會自動派工。
          </div>
        </div>
        <div className="flex justify-end gap-2 rounded-b-3xl border-t border-slate-200 bg-slate-50 px-6 py-4">
          <Button tone="secondary" onClick={onClose} disabled={busy}>取消</Button>
          <Button onClick={onSave} disabled={busy}>{busy ? '儲存中' : '儲存來源'}</Button>
        </div>
      </div>
    </div>
  );
}

export function SourceSystemConsole() {
  const { selectedTenantId } = useAuth();
  const [sources, setSources] = useState<CoreSourceSystem[]>([]);
  const [flows, setFlows] = useState<CoreDispatchFlowView[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editorError, setEditorError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editor, setEditor] = useState<SourceEditorState>({ sourceSystemId: '', displayName: '', description: '', status: 'ACTIVE' });

  const rows = useMemo(() => mergeSourceRows(sources, flows), [sources, flows]);

  async function reload() {
    const tenantId = selectedTenantId.trim();
    if (!tenantId) {
      setSources([]);
      setFlows([]);
      setError('請先選擇 Workspace。');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [sourceRows, flowRows] = await Promise.all([
        coreAdminApi.getSourceSystems(tenantId),
        coreAdminApi.getDispatchFlows(tenantId),
      ]);
      setSources(sourceRows);
      setFlows(flowRows);
      setLastUpdatedAt(new Date().toISOString());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '無法載入來源系統。');
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    setEditor({ sourceSystemId: '', displayName: '', description: '', status: 'ACTIVE' });
    setEditorError(null);
    setEditorOpen(true);
  }

  function openEdit(source: CoreSourceSystem) {
    setEditor({
      sourceSystemId: source.sourceSystemId,
      displayName: source.displayName,
      description: source.description ?? '',
      status: source.status ?? 'ACTIVE',
    });
    setEditorError(null);
    setEditorOpen(true);
  }

  async function saveEditor() {
    const tenantId = selectedTenantId.trim();
    if (!tenantId) {
      setEditorError('請先選擇 Workspace。');
      return;
    }
    if (!editor.sourceSystemId.trim() || !editor.displayName.trim()) {
      setEditorError('來源識別碼與顯示名稱皆為必填。');
      return;
    }
    setSaving(true);
    setEditorError(null);
    try {
      const exists = sources.some((source) => source.sourceSystemId === editor.sourceSystemId);
      if (exists) {
        await coreAdminApi.updateSourceSystem(tenantId, editor.sourceSystemId, editor);
      } else {
        await coreAdminApi.createSourceSystem(tenantId, editor);
      }
      setEditorOpen(false);
      await reload();
    } catch (caught) {
      setEditorError(caught instanceof Error ? caught.message : '來源系統儲存失敗。');
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => { void reload(); }, [selectedTenantId]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <main className="space-y-5">
      <SourceSystemEditor
        open={editorOpen}
        editor={editor}
        busy={saving}
        error={editorError}
        onChange={(patch) => setEditor((current) => ({ ...current, ...patch }))}
        onClose={() => setEditorOpen(false)}
        onSave={() => void saveEditor()}
      />

      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-blue-700">Source Systems</div>
            <h1 className="mt-1 text-2xl font-black text-slate-950">來源系統</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              Source System 是企業自行建立的事件來源主檔，只包含識別碼、顯示名稱、說明與狀態。它不是固定產品類型，也不承載派工角色、特殊能力、Scope、Profile 或 Agent 覆蓋規則。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <RefreshButton refreshing={loading} lastUpdatedAt={lastUpdatedAt} onRefresh={() => void reload()} />
            <Button onClick={openCreate}>新增來源系統</Button>
          </div>
        </div>
        <div className="mt-5 grid gap-3 sm:grid-cols-3">
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">來源主檔</div><div className="mt-1 text-2xl font-black">{rows.length}</div></div>
          <div className="rounded-2xl bg-emerald-50 p-4"><div className="text-xs font-black text-emerald-700">啟用來源</div><div className="mt-1 text-2xl font-black text-emerald-950">{rows.filter((row) => String(row.status).toUpperCase() === 'ACTIVE').length}</div></div>
          <div className="rounded-2xl bg-blue-50 p-4"><div className="text-xs font-black text-blue-700">被 Flow 使用</div><div className="mt-1 text-2xl font-black text-blue-950">{rows.filter((row) => row.flowCount > 0).length}</div></div>
        </div>
      </section>

      {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
      {!selectedTenantId.trim() ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">請先從上方選擇 Workspace，系統才會載入該企業的來源系統。</div> : null}

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-lg font-black text-slate-950">企業來源主檔</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">這裡只維護來源主檔。派工條件、處理 Agent 與特殊能力請到「派工流程」設定。</p>
          </div>
          <Button size="sm" onClick={openCreate}>新增來源</Button>
        </div>

        {loading ? <div className="mt-5"><LoadingBox label="載入來源系統..." /></div> : null}
        {!loading && !rows.length ? (
          <div className="mt-5">
            <EmptyState title="尚無來源系統" description="請先建立第一個企業來源，再到派工流程選擇它並設定事件條件與 Agent。" />
          </div>
        ) : null}

        <div className="mt-5 grid gap-3">
          {rows.map((source) => (
            <article key={source.sourceSystemId} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="break-all text-base font-black text-slate-950">{sourceLabel(source)}</h3>
                    <StatusBadge status={source.status ?? 'ACTIVE'} />
                  </div>
                  <p className="mt-2 text-sm leading-6 text-slate-600">{source.description || '來源只代表事件從哪個系統進來，不代表派工角色、特殊能力或底層授權資料。'}</p>
                  <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                    <span className="rounded-full bg-white px-3 py-1">Flow：{source.flowCount}</span>
                    <span className="rounded-full bg-white px-3 py-1">啟用 Flow：{source.activeFlowCount}</span>
                    {source.eventTypes.slice(0, 4).map((eventType) => <span key={eventType} className="rounded-full bg-blue-50 px-3 py-1 text-blue-800">{eventType}</span>)}
                    {source.eventTypes.length > 4 ? <span className="rounded-full bg-white px-3 py-1">+{source.eventTypes.length - 4}</span> : null}
                    {source.updatedAt ? <span className="rounded-full bg-white px-3 py-1">主檔更新：{formatDateTime(source.updatedAt)}</span> : null}
                    {source.lastFlowUpdatedAt ? <span className="rounded-full bg-white px-3 py-1">Flow 更新：{formatDateTime(source.lastFlowUpdatedAt)}</span> : null}
                  </div>
                </div>
                <div className="flex shrink-0 flex-wrap gap-2">
                  <button type="button" onClick={() => openEdit(source)} className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-100">編輯來源</button>
                  <Link href={`/dispatch-flows?sourceSystem=${encodeURIComponent(source.sourceSystemId)}`} className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-100">查看 Flow</Link>
                  <Link href={createFlowHref(source.sourceSystemId)} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800">用此來源建立 Flow</Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
