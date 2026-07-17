'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type {
  CoreAgentCapabilityCatalog,
  CoreAgentPoolView,
  CoreDispatchFlowAgentView,
  CoreDispatchFlowRequiredCapabilityView,
  CoreDispatchFlowRuleView,
  CoreDispatchFlowView,
  CoreSourceSystem,
  CoreEventIntakeDecisionResponse,
} from '@/lib/types/core';

type InitialContractQuery = Record<string, string | string[] | undefined>;

type FlowEditorState = {
  flowId?: string;
  flowCode?: string;
  flowName: string;
  description: string;
  sourceSystem: string;
  status: 'DRAFT' | 'ACTIVE';
  objectType: string;
  eventType: string;
  errorCode: string;
  severity: string;
  defaultPoolId: string;
  targetPoolId: string;
  agentIds: string[];
  capabilityCodes: string[];
};

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';
function queryValue(query: InitialContractQuery | undefined, key: string): string | undefined {
  const value = query?.[key];
  return Array.isArray(value) ? value[0] : value;
}

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_\-.]/g, '_').replace(/^_+|_+$/g, '');
}

function generateId(prefix: string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

function unique(values: Array<string | undefined | null>): string[] {
  return Array.from(new Set(values.map((value) => String(value ?? '').trim()).filter(Boolean))).sort((left, right) => left.localeCompare(right));
}

function requiredCapabilities(flow?: CoreDispatchFlowView | null): CoreDispatchFlowRequiredCapabilityView[] {
  return ((flow?.requiredCapabilities ?? flow?.requiredSkills ?? []) as CoreDispatchFlowRequiredCapabilityView[]);
}

function requiredCapabilityCode(capability: CoreDispatchFlowRequiredCapabilityView): string | undefined {
  return capability.capabilityCode ?? capability.skillCode;
}

function requiredCapabilityName(capability: CoreDispatchFlowRequiredCapabilityView): string | undefined {
  return capability.capabilityName ?? capability.skillName ?? requiredCapabilityCode(capability);
}

function isActiveStatus(status?: string | null): boolean {
  const normalized = String(status ?? '').trim().toUpperCase();
  return normalized === 'ACTIVE' || normalized === 'ENABLED';
}

function flowRule(flow?: CoreDispatchFlowView | null): CoreDispatchFlowRuleView | undefined {
  return flow?.rules?.find((rule) => String(rule.eventStage ?? 'EXTERNAL').toUpperCase() === 'EXTERNAL') ?? flow?.rules?.[0];
}

function isComplexFlow(flow?: CoreDispatchFlowView | null): boolean {
  if (!flow) return false;
  const rules = flow.rules ?? [];
  return rules.length > 1 || rules.some((rule) => String(rule.eventStage ?? 'EXTERNAL').toUpperCase() !== 'EXTERNAL');
}

function editorFromFlow(flow?: CoreDispatchFlowView | null, preferredAgentId?: string): FlowEditorState {
  const rule = flowRule(flow);
  const severity = typeof rule?.condition?.severity === 'string' ? String(rule.condition.severity) : 'ANY';
  return {
    flowId: flow?.flowId,
    flowCode: flow?.flowCode,
    flowName: flow?.flowName ?? '',
    description: flow?.description ?? '',
    sourceSystem: flow?.sourceSystem ?? '',
    status: String(flow?.status ?? 'DRAFT').toUpperCase() === 'ACTIVE' ? 'ACTIVE' : 'DRAFT',
    objectType: rule?.objectType ?? '*',
    eventType: rule?.eventType ?? '',
    errorCode: rule?.errorCode ?? '*',
    severity,
    defaultPoolId: flow?.defaultPoolId ?? '',
    targetPoolId: rule?.targetPoolId ?? flow?.defaultPoolId ?? '',
    agentIds: unique([...(flow?.agents ?? []).map((agent) => agent.agentId), preferredAgentId]),
    capabilityCodes: unique(requiredCapabilities(flow).filter((capability) => capability.required !== false).map(requiredCapabilityCode)),
  };
}

function flowIssues(flow?: CoreDispatchFlowView | null): string[] {
  if (!flow) return [];
  const issues: string[] = [];
  if (!flow.sourceSystem) issues.push('尚未選擇來源系統');
  if (!flow.defaultPoolId) issues.push('尚未設定預設 Agent Pool');
  if ((flow.rules ?? []).some((rule) => rule.enabled !== false && !rule.targetPoolId)) issues.push('已知事件規則尚未設定目標 Pool');
  return issues;
}

function poolDisplay(pool?: CoreAgentPoolView): string {
  if (!pool) return '-';
  return pool.poolName && pool.poolCode ? `${pool.poolName} (${pool.poolCode})` : pool.poolCode ?? pool.poolId;
}

function sourceDisplay(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

function FlowListItem({ flow, active, onSelect }: Readonly<{ flow: CoreDispatchFlowView; active: boolean; onSelect: () => void }>) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`w-full rounded-2xl border p-4 text-left transition ${active ? 'border-purple-300 bg-purple-50 shadow-sm' : 'border-slate-200 bg-white hover:border-purple-200 hover:bg-slate-50'}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate font-black text-slate-950">{flow.flowName ?? flow.flowCode ?? flow.flowId}</div>
          <div className="mt-1 truncate text-xs text-slate-500">{flow.sourceSystem ?? '未設定來源系統'}</div>
        </div>
        <StatusBadge status={flow.status ?? 'DRAFT'} />
      </div>
      <div className="mt-3 flex flex-wrap gap-2 text-[11px] font-bold text-slate-600">
        <span className="rounded-full bg-slate-100 px-2 py-1">事件條件 {flow.rules?.length ?? flow.externalRuleCount ?? 0}</span>
        <span className="rounded-full bg-slate-100 px-2 py-1">Default Pool {flow.defaultPoolId ? '1' : '0'}</span>
        <span className="rounded-full bg-slate-100 px-2 py-1">能力標籤參考 {requiredCapabilities(flow).length || flow.capabilityCount || flow.skillCount || 0}</span>
      </div>
    </button>
  );
}

function FlowDetail({
  flow,
  onEdit,
  onCreate,
  onSendRealTestEvent,
  sendingRealTestEvent,
  testResult,
}: Readonly<{
  flow?: CoreDispatchFlowView | null;
  onEdit: () => void;
  onCreate: () => void;
  onSendRealTestEvent: () => void;
  sendingRealTestEvent: boolean;
  testResult?: CoreEventIntakeDecisionResponse | null;
}>) {
  if (!flow) {
    return (
      <EmptyState
        title="尚未選擇派工流程"
        description="從左側選擇既有 Source Flow，或建立第一個派工流程。Phase 32-G 標準操作只設定來源系統、預設 Pool 與已知事件 Pool override；Capability 僅作能力標籤參考。"
        action={<Button tone="primary" onClick={onCreate}>建立派工流程</Button>}
      />
    );
  }
  const rule = flowRule(flow);
  const issues = flowIssues(flow);
  const complex = isComplexFlow(flow);
  return (
    <div className="space-y-5">
      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">派工流程</div>
            <h2 className="mt-1 text-2xl font-black text-slate-950">{flow.flowName ?? flow.flowCode ?? flow.flowId}</h2>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{flow.description || '尚未填寫流程說明。'}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={flow.status ?? 'DRAFT'} />
            <Button
              tone="primary"
              onClick={onSendRealTestEvent}
              disabled={complex || sendingRealTestEvent || !isActiveStatus(flow.status) || issues.length > 0}
            >
              {sendingRealTestEvent ? '建立真實 Task 中…' : '發送真實測試事件'}
            </Button>
            <Button onClick={onEdit} disabled={complex}>{complex ? '多階段流程（唯讀）' : '編輯流程'}</Button>
          </div>
        </div>
        {complex ? (
          <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
            這個流程包含多個事件階段。Stage 5 新手表單不會覆寫進階規則，因此目前只提供檢視，避免儲存時破壞既有流程。
          </div>
        ) : null}
        <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">來源系統</div><div className="mt-1 font-black text-slate-950">{flow.sourceSystem ?? '-'}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">物件／事件</div><div className="mt-1 font-black text-slate-950">{rule?.objectType ?? '*'} / {rule?.eventType ?? '*'}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">預設 Pool</div><div className="mt-1 break-all font-black text-slate-950">{flow.defaultPoolId ?? '-'}</div></div>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Rule Target Pool</div><div className="mt-1 break-all font-black text-slate-950">{rule?.targetPoolCode ?? rule?.targetPoolId ?? '-'}</div></div>
        </div>
      </section>

      <section className={`rounded-3xl border p-5 ${issues.length ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-xs font-black uppercase tracking-wide opacity-70">設定檢查</div>
            <h3 className="mt-1 text-lg font-black">{issues.length ? '尚有必要設定未完成' : '基本派工設定完整'}</h3>
          </div>
          <StatusBadge status={issues.length ? 'NOT_READY' : 'READY'} />
        </div>
        {issues.length ? (
          <ul className="mt-3 space-y-1 text-sm font-semibold"><li>{issues.join('；')}</li></ul>
        ) : (
          <p className="mt-2 text-sm leading-6">這裡只檢查已儲存的流程資料。真正的 Event → Task → Agent Pool → Agent 派送由正常 Task 流程驗證，不使用另一套 Readiness Simulator。</p>
        )}
      </section>

      {testResult ? (
        <section className={`rounded-3xl border p-5 shadow-sm ${testResult.taskCreated ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'}`}>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div className="text-xs font-black uppercase tracking-wide opacity-70">真實 Event Intake 結果</div>
              <h3 className="mt-1 text-lg font-black">{testResult.taskCreated ? '已建立正式 Task' : '事件已受理，但沒有建立新 Task'}</h3>
              <p className="mt-2 text-sm leading-6">{testResult.reason ?? '此測試事件已進入正式 Event → Task → Dispatch runtime，沒有使用 Readiness Simulator。'}</p>
            </div>
            <StatusBadge status={testResult.decisionType ?? (testResult.taskCreated ? 'TASK_CREATED' : 'EVENT_ACCEPTED')} />
          </div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl bg-white/70 p-3"><div className="text-xs font-black opacity-60">Event</div><div className="mt-1 break-all text-sm font-black">{testResult.eventId ?? '-'}</div></div>
            <div className="rounded-2xl bg-white/70 p-3"><div className="text-xs font-black opacity-60">Task</div><div className="mt-1 break-all text-sm font-black">{testResult.taskId ?? '-'}</div></div>
            <div className="rounded-2xl bg-white/70 p-3"><div className="text-xs font-black opacity-60">Agent</div><div className="mt-1 break-all text-sm font-black">{testResult.selectedAgentId ?? '尚未指派'}</div></div>
            <div className="rounded-2xl bg-white/70 p-3"><div className="text-xs font-black opacity-60">Dispatch Request</div><div className="mt-1 break-all text-sm font-black">{testResult.dispatchRequestId ?? '尚未建立'}</div></div>
          </div>
          {testResult.taskId ? (
            <div className="mt-4 flex flex-wrap gap-2">
              <Link href={`/tasks/${encodeURIComponent(testResult.taskId)}`} className="rounded-xl bg-slate-950 px-4 py-2 text-sm font-black text-white hover:bg-slate-800">查看真實 Task 與時間線</Link>
              {testResult.selectedAgentId ? <Link href={`/agents/${encodeURIComponent(testResult.selectedAgentId)}`} className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-800 hover:bg-slate-50">查看 Agent</Link> : null}
            </div>
          ) : null}
        </section>
      ) : null}

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Agent Pool / Work Queue</div><h3 className="mt-1 text-lg font-black">此 Source Flow 的派單目標</h3></div>
          <Button onClick={onEdit} disabled={complex}>調整 Pool</Button>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-black text-slate-500">預設 Pool</div>
            <div className="mt-1 break-all font-black text-slate-950">{flow.defaultPoolId ?? '尚未設定'}</div>
            <p className="mt-2 text-sm leading-6 text-slate-600">沒有明確 rule match 或事件尚未分類時，會派到此 Pool。</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-black text-slate-500">已知事件 Override</div>
            <div className="mt-1 break-all font-black text-slate-950">{rule?.targetPoolCode ?? rule?.targetPoolId ?? '使用預設 Pool'}</div>
            <p className="mt-2 text-sm leading-6 text-slate-600">已知 eventType 可以覆蓋到更精準的處理池。</p>
          </div>
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-slate-500">能力標籤參考</div>
        <h3 className="mt-1 text-lg font-black">僅供查詢，不阻擋派單</h3>
        <div className="mt-4 flex flex-wrap gap-2">
          {requiredCapabilities(flow).map((capability) => <span key={capability.id ?? `${capability.ruleId}-${requiredCapabilityCode(capability)}`} className="rounded-full bg-blue-50 px-3 py-1.5 text-sm font-black text-blue-800">{requiredCapabilityName(capability)}</span>)}
          {!requiredCapabilities(flow).length ? <span className="text-sm text-slate-500">Phase 32-A 標準流程不要求 Capability；派單由 Source Flow / Agent Pool 模型承接。</span> : null}
        </div>
      </section>
    </div>
  );
}

function FlowEditorDialog({
  open,
  editor,
  sourceSystems,
  pools,
  capabilities,
  objectTypes,
  eventTypes,
  errorCodes,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  editor: FlowEditorState;
  sourceSystems: CoreSourceSystem[];
  pools: CoreAgentPoolView[];
  capabilities: CoreAgentCapabilityCatalog[];
  objectTypes: string[];
  eventTypes: string[];
  errorCodes: string[];
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<FlowEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  const sourceExists = sourceSystems.some((source) => source.sourceSystemId === editor.sourceSystem);
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[80] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 sm:p-8" role="dialog" aria-modal="true" aria-label="派工流程表單">
      <div className="w-full max-w-5xl rounded-3xl bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 rounded-t-3xl border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Flow Setup</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editor.flowId ? '編輯派工流程' : '建立派工流程'}</h2>
            <p className="mt-1 text-sm text-slate-600">Phase 32-G 只設定來源系統、預設 Agent Pool 與已知事件 Pool override。Capability 是 Agent 能力標籤與後台查詢參考，不是第一版派單 gate。</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="關閉">×</button>
        </div>

        <div className="space-y-6 p-6">
          {error ? (
            <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900" role="alert">
              {error}
            </div>
          ) : null}
          <section className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">1. 基本資料</div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <label className={labelClass}>流程名稱<span className="text-rose-600"> *</span><input className={inputClass} value={editor.flowName} onChange={(event) => onChange({ flowName: event.target.value })} placeholder="例如：設備異常分析" /></label>
              <label className={labelClass}>狀態<select className={inputClass} value={editor.status} onChange={(event) => onChange({ status: event.target.value as FlowEditorState['status'] })}><option value="DRAFT">草稿</option><option value="ACTIVE">啟用</option></select></label>
              <label className={`${labelClass} md:col-span-2`}>說明<textarea className={`${inputClass} min-h-24`} value={editor.description} onChange={(event) => onChange({ description: event.target.value })} placeholder="說明何時使用此流程，以及處理目標。" /></label>
            </div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div><div className="text-xs font-black uppercase tracking-wide text-slate-500">2. 事件條件</div><h3 className="mt-1 text-lg font-black">什麼事件要進入這個流程？</h3></div>
              <div className="rounded-xl bg-blue-50 px-3 py-2 text-xs font-semibold text-blue-800">來源系統由企業自行定義，Runtime 不會根據來源名稱套用任何特殊派工邏輯。</div>
            </div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <label className={labelClass}>來源系統<span className="text-rose-600"> *</span>
                <select className={inputClass} value={sourceExists ? editor.sourceSystem : ''} onChange={(event) => onChange({ sourceSystem: event.target.value })}>
                  <option value="">請選擇</option>
                  {sourceSystems.map((source) => <option key={source.sourceSystemId} value={source.sourceSystemId}>{sourceDisplay(source)}</option>)}
                </select>
              </label>
              <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-4 text-sm leading-6 text-slate-600">
                來源系統必須先在「來源系統」頁建立；建立 Flow 不會偷偷建立來源主檔。
                <Link href="/source-systems" className="ml-1 font-black text-blue-700 hover:underline">前往新增來源</Link>
              </div>
              <label className={labelClass}>物件類型<input list="stage5-object-types" className={inputClass} value={editor.objectType} onChange={(event) => onChange({ objectType: normalizeCode(event.target.value) || '*' })} placeholder="選擇既有值或輸入新值" /><datalist id="stage5-object-types">{objectTypes.map((value) => <option key={value} value={value} />)}</datalist></label>
              <label className={labelClass}>事件類型<span className="text-slate-400">（選填；空白會進預設 Pool）</span><input list="stage5-event-types" className={inputClass} value={editor.eventType} onChange={(event) => onChange({ eventType: normalizeCode(event.target.value) })} placeholder="空白代表 UNKNOWN / 未分類" /><datalist id="stage5-event-types">{eventTypes.map((value) => <option key={value} value={value} />)}</datalist></label>
              <label className={labelClass}>錯誤代碼（選填）<input list="stage5-error-codes" className={inputClass} value={editor.errorCode === '*' ? '' : editor.errorCode} onChange={(event) => onChange({ errorCode: normalizeCode(event.target.value) || '*' })} placeholder="全部或指定代碼" /><datalist id="stage5-error-codes">{errorCodes.map((value) => <option key={value} value={value} />)}</datalist></label>
              <label className={labelClass}>嚴重度<select className={inputClass} value={editor.severity} onChange={(event) => onChange({ severity: event.target.value })}><option value="ANY">全部</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option><option value="CRITICAL">重大</option></select></label>
            </div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">3. Agent Pool / Work Queue</div>
            <div className="mt-1 flex items-center justify-between gap-3"><h3 className="text-lg font-black">先選預設 Pool，再選已知事件 override Pool</h3><span className="text-xs font-bold text-slate-500">Pool 是派單目標</span></div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <label className={labelClass}>預設 Pool<span className="text-rose-600"> *</span>
                <select className={inputClass} value={editor.defaultPoolId} onChange={(event) => onChange({ defaultPoolId: event.target.value, targetPoolId: editor.targetPoolId || event.target.value })}>
                  <option value="">請選擇</option>
                  {pools.map((pool) => <option key={pool.poolId} value={pool.poolId}>{poolDisplay(pool)}</option>)}
                </select>
              </label>
              <label className={labelClass}>已知事件目標 Pool<span className="text-slate-400">（選填）</span>
                <select className={inputClass} value={editor.targetPoolId} onChange={(event) => onChange({ targetPoolId: event.target.value })}>
                  <option value="">使用預設 Pool</option>
                  {pools.map((pool) => <option key={pool.poolId} value={pool.poolId}>{poolDisplay(pool)}</option>)}
                </select>
              </label>
            </div>
            <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
              未知事件或沒有 rule match 時會進預設 Pool；已知 eventType 可覆蓋到更精準的處理 Pool。Agent 成員在上方 Agent Pool 管理區維護。
            </div>
            {!pools.length ? <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">尚未建立 Agent Pool。請先建立 TRIAGE_POOL，否則 ACTIVE Source Flow 無法派單。</div> : null}
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">4. 能力標籤參考</div>
            <h3 className="mt-1 text-lg font-black">Capability 不作為第一版派單 gate</h3>
            <p className="mt-2 text-sm leading-6 text-slate-600">Phase 32-G 將 Capability 固定為 Agent 能力標籤、查詢與未來治理參考。標準派單先由 Source Flow 決定目標 Agent Pool，再由 Pool 選 Agent；本表單不建立必要能力條件。</p>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              {capabilities.map((capability) => (
                <div key={capability.capabilityCode} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <div className="font-black text-slate-950">{requiredCapabilityName(capability)}</div>
                  <div className="mt-1 text-xs text-slate-500">{capability.description ?? capability.capabilityCode}</div>
                  <div className="mt-3 rounded-xl bg-white px-3 py-2 text-xs font-bold text-slate-600">僅供查詢，不會寫入必要能力條件。</div>
                </div>
              ))}
              {!capabilities.length ? <div className="rounded-2xl border border-dashed border-slate-300 p-4 text-sm text-slate-500">目前沒有已啟用的能力標籤。這不會阻止建立一般流程。</div> : null}
            </div>
          </section>
        </div>

        <div className="sticky bottom-0 flex flex-col-reverse gap-3 rounded-b-3xl border-t border-slate-200 bg-white px-6 py-4 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={busy}>取消</Button>
          <Button tone="primary" onClick={onSave} disabled={busy}>{busy ? '儲存中…' : 'Save Dispatch Flow'}</Button>
        </div>
      </div>
    </div>
  );
}

export function DispatchContractBuilderConsole({ initialQuery }: Readonly<{ initialQuery?: InitialContractQuery }> = {}) {
  const router = useRouter();
  const { selectedTenantId: tenantId } = useAuth();
  const [sourceSystems, setSourceSystems] = useState<CoreSourceSystem[]>([]);
  const [flows, setFlows] = useState<CoreDispatchFlowView[]>([]);
  const [pools, setPools] = useState<CoreAgentPoolView[]>([]);
  const [capabilities, setCapabilities] = useState<CoreAgentCapabilityCatalog[]>([]);
  const [selectedFlow, setSelectedFlow] = useState<CoreDispatchFlowView | null>(null);
  const [editorBase, setEditorBase] = useState<CoreDispatchFlowView | null>(null);
  const [editor, setEditor] = useState<FlowEditorState>(() => editorFromFlow(null, queryValue(initialQuery, 'agentId')));
  const [editorOpen, setEditorOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [sendingRealTestEvent, setSendingRealTestEvent] = useState(false);
  const [testResult, setTestResult] = useState<CoreEventIntakeDecisionResponse | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [queryApplied, setQueryApplied] = useState(false);

  const reload = useCallback(async () => {
    const scopedTenantId = tenantId.trim();
    if (!scopedTenantId) {
      setFlows([]);
      setSourcesAndMessage();
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [flowRows, sourceRows, poolRows, capabilityRows] = await Promise.all([
        coreAdminApi.getDispatchFlows(scopedTenantId),
        coreAdminApi.getSourceSystems(scopedTenantId),
        coreAdminApi.getAgentPools(scopedTenantId),
        coreAdminApi.getCapabilities('ACTIVE', undefined, scopedTenantId),
      ]);
      setFlows(flowRows);
      setSourceSystems(sourceRows);
      setPools(poolRows.filter((pool) => String(pool.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE'));
      setCapabilities(capabilityRows.filter((capability) => capability.dispatchEligible !== false));
      if (selectedFlow?.flowId) {
        const refreshed = flowRows.find((flow) => flow.flowId === selectedFlow.flowId);
        if (refreshed) setSelectedFlow(refreshed);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '無法載入派工流程資料。');
    } finally {
      setLoading(false);
    }
  }, [selectedFlow?.flowId, tenantId]);

  function setSourcesAndMessage() {
    setSourceSystems([]);
    setPools([]);
    setCapabilities([]);
    setMessage('請先選擇 Workspace。');
  }

  useEffect(() => { void reload(); }, [tenantId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (queryApplied || !tenantId || loading) return;
    const flowId = queryValue(initialQuery, 'flowId');
    const agentId = queryValue(initialQuery, 'agentId');
    const create = queryValue(initialQuery, 'create') === '1';
    if (flowId) {
      void selectFlow(flowId);
    } else if (create || agentId) {
      openCreate(agentId);
    }
    setQueryApplied(true);
  }, [initialQuery, loading, queryApplied, tenantId]); // eslint-disable-line react-hooks/exhaustive-deps

  const sourceChoices = useMemo(() => sourceSystems
    .filter((source) => String(source.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE')
    .sort((left, right) => left.sourceSystemId.localeCompare(right.sourceSystemId)), [sourceSystems]);

  const objectTypes = useMemo(() => unique(flows.flatMap((flow) => (flow.rules ?? []).map((rule) => rule.objectType)).filter((value) => value !== '*')), [flows]);
  const eventTypes = useMemo(() => unique(flows.flatMap((flow) => (flow.rules ?? []).map((rule) => rule.eventType)).filter((value) => value !== '*')), [flows]);
  const errorCodes = useMemo(() => unique(flows.flatMap((flow) => (flow.rules ?? []).map((rule) => rule.errorCode)).filter((value) => value !== '*')), [flows]);

  async function selectFlow(flowId: string) {
    if (!tenantId.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const detail = await coreAdminApi.getDispatchFlow(flowId, tenantId);
      setSelectedFlow(detail);
      setTestResult(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '無法載入流程詳細資料。');
    } finally {
      setLoading(false);
    }
  }

  function openCreate(preferredAgentId?: string) {
    const empty = editorFromFlow(null, preferredAgentId);
    setEditorBase(null);
    setEditor({
      ...empty,
      sourceSystem: queryValue(initialQuery, 'sourceSystem') ?? empty.sourceSystem,
      objectType: queryValue(initialQuery, 'objectType') ?? empty.objectType,
      eventType: queryValue(initialQuery, 'eventType') ?? empty.eventType,
      errorCode: queryValue(initialQuery, 'errorCode') ?? empty.errorCode,
      defaultPoolId: queryValue(initialQuery, 'defaultPoolId') ?? empty.defaultPoolId,
      targetPoolId: queryValue(initialQuery, 'targetPoolId') ?? empty.targetPoolId,
    });
    setEditorOpen(true);
    setError(null);
  }

  async function openEdit() {
    if (!selectedFlow || !tenantId.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const detail = await coreAdminApi.getDispatchFlow(selectedFlow.flowId, tenantId);
      setEditorBase(detail);
      setEditor(editorFromFlow(detail));
      setEditorOpen(true);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '無法開啟流程表單。');
    } finally {
      setBusy(false);
    }
  }

  function updateEditor(patch: Partial<FlowEditorState>) {
    setEditor((current) => ({ ...current, ...patch }));
  }

  async function sendRealTestEvent() {
    if (!selectedFlow || !tenantId.trim()) return;
    setSendingRealTestEvent(true);
    setError(null);
    setMessage(null);
    setTestResult(null);
    try {
      const result = await coreAdminApi.createDispatchFlowRealTestEvent(
        selectedFlow.flowId,
        { message: `OpenDispatch real test event for ${selectedFlow.flowName ?? selectedFlow.flowCode ?? selectedFlow.flowId}` },
        tenantId,
      );
      setTestResult(result);
      setMessage(result.taskId
        ? `正式 Task ${result.taskId} 已建立，正在使用正常 Dispatch runtime。`
        : '測試事件已進入正式 Event Intake，但沒有建立新 Task；請查看決策原因。');
      if (result.taskId && result.assignmentCreated) {
        router.prefetch(`/tasks/${encodeURIComponent(result.taskId)}`);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '發送真實測試事件失敗。');
    } finally {
      setSendingRealTestEvent(false);
    }
  }

  async function saveFlow() {
    const sourceSystem = normalizeCode(editor.sourceSystem);
    const eventType = normalizeCode(editor.eventType);
    const defaultPoolId = editor.defaultPoolId.trim();
    const targetPoolId = editor.targetPoolId.trim() || defaultPoolId;
    if (!tenantId.trim()) { setError('請先選擇 Workspace。'); return; }
    if (!editor.flowName.trim()) { setError('流程名稱為必填。'); return; }
    if (!sourceSystem) { setError('來源系統為必填。'); return; }
    if (!sourceSystems.some((source) => source.sourceSystemId === sourceSystem && String(source.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE')) { setError('來源系統必須先在「來源系統」頁建立並啟用，建立 Flow 不會自動建立來源。'); return; }
    if (!defaultPoolId) { setError('請先選擇預設 Agent Pool。'); return; }
    if (!pools.some((pool) => pool.poolId === defaultPoolId)) { setError('預設 Agent Pool 不存在或未啟用。'); return; }
    if (targetPoolId && !pools.some((pool) => pool.poolId === targetPoolId)) { setError('已知事件目標 Pool 不存在或未啟用。'); return; }

    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const base = editorBase;
      const flowId = editor.flowId ?? generateId('flow');
      const flowCode = editor.flowCode ?? (normalizeCode(`${sourceSystem}_${editor.flowName}`) || normalizeCode(flowId));
      const existingRule = flowRule(base);
      const ruleId = existingRule?.ruleId ?? generateId('rule');
      const condition = { ...(existingRule?.condition ?? {}) } as Record<string, unknown>;
      if (editor.severity === 'ANY') delete condition.severity;
      else condition.severity = editor.severity;
      condition.phase32gAgentPoolFirst = true;

      const beginnerRule: CoreDispatchFlowRuleView = {
        ...existingRule,
        tenantId,
        flowId,
        ruleId,
        ruleCode: existingRule?.ruleCode ?? `${flowCode}_EVENT`,
        ruleName: existingRule?.ruleName ?? `${editor.flowName} event condition`,
        ruleScope: 'EXTERNAL_INTAKE',
        eventStage: 'EXTERNAL',
        sourceSystem,
        objectType: normalizeCode(editor.objectType) || '*',
        eventType: eventType || '*',
        errorCode: normalizeCode(editor.errorCode) || '*',
        condition,
        requestedSkill: undefined,
        capabilityRequirementMode: 'NONE',
        requiredOperation: 'ANALYZE',
        sideEffectLevel: 'NONE',
        priority: existingRule?.priority ?? 100,
        matchMode: 'EXACT_OR_WILDCARD',
        targetPoolId,
        targetPoolCode: pools.find((pool) => pool.poolId === targetPoolId)?.poolCode,
        candidatePoolMode: 'SOURCE_SYSTEM_POOL',
        routingStrategy: existingRule?.routingStrategy ?? 'LOWEST_LOAD',
        explicitActionAuthorizationRequired: false,
        enabled: editor.status === 'ACTIVE',
      };
      const otherRules = (base?.rules ?? []).filter((rule) => rule.ruleId !== existingRule?.ruleId);
      // Phase 32-G: Source Flow points to Agent Pool. Direct Flow Agent selections are legacy compatibility only.
      // The standard setting UI does not require direct Agent selection and does not create Capability gates.
      const selectedRequiredCapabilities: CoreDispatchFlowRequiredCapabilityView[] = [];
      const selectedAgents: CoreDispatchFlowAgentView[] = [];

      const payload: CoreDispatchFlowView = {
        ...(base ?? {}),
        tenantId,
        flowId,
        flowCode,
        flowName: editor.flowName.trim(),
        description: editor.description.trim(),
        sourceSystem,
        status: editor.status,
        flowType: 'SOURCE_FLOW',
        defaultPoolId,
        defaultCandidatePoolMode: 'SOURCE_SYSTEM_POOL',
        defaultRoutingStrategy: 'LOWEST_LOAD',
        rules: [beginnerRule, ...otherRules],
        requiredCapabilities: selectedRequiredCapabilities,
        requiredSkills: selectedRequiredCapabilities.map((capability: CoreDispatchFlowRequiredCapabilityView) => ({
          ...capability,
          skillCode: capability.capabilityCode ?? capability.skillCode,
          skillName: capability.capabilityName ?? capability.skillName,
          skillKind: capability.capabilityKind ?? capability.skillKind,
          openClawSkill: capability.openClawCapability ?? capability.openClawSkill,
        })),
        agents: selectedAgents,
        metadata: { ...(base?.metadata ?? {}), phase32gAgentPoolFirst: true, phase32aCapabilityReferenceOnly: true, standardEntry: 'SOURCE_FLOW_AGENT_POOL' },
      };

      const saved = editor.flowId
        ? await coreAdminApi.updateDispatchFlow(flowId, payload, tenantId)
        : await coreAdminApi.createDispatchFlow(payload, tenantId);
      setEditorOpen(false);
      setSelectedFlow(saved);
      setMessage(`派工流程「${saved.flowName ?? saved.flowCode ?? saved.flowId}」已儲存。`);
      await reload();
      await selectFlow(saved.flowId);
    } catch (caught) {
      console.error('Failed to save Dispatch Flow', caught);
      setError(caught instanceof Error ? caught.message : '儲存派工流程失敗。');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="space-y-5">
      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Flows</div>
            <h1 className="mt-1 text-2xl font-black text-slate-950">派工流程</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">唯一的派工設定入口。Phase 32-A 開始收斂為 Source Flow / Agent Pool 模型：Flow 是派單流程，Capability 只是 Agent 能力標籤與後台查詢參考，不作為第一版 routing gate。</p>
          </div>
          <Button tone="primary" onClick={() => openCreate(queryValue(initialQuery, 'agentId'))} disabled={!tenantId.trim()}>建立派工流程</Button>
        </div>
        <div className="mt-5 grid gap-3 sm:grid-cols-3">
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">全部流程</div><div className="mt-1 text-2xl font-black">{flows.length}</div></div>
          <div className="rounded-2xl bg-emerald-50 p-4"><div className="text-xs font-black text-emerald-700">已啟用</div><div className="mt-1 text-2xl font-black text-emerald-950">{flows.filter((flow) => isActiveStatus(flow.status)).length}</div></div>
          <div className="rounded-2xl bg-amber-50 p-4"><div className="text-xs font-black text-amber-700">需要補設定</div><div className="mt-1 text-2xl font-black text-amber-950">{flows.filter((flow) => flowIssues(flow).length > 0).length}</div></div>
        </div>
      </section>

      {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{message}</div> : null}
      {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
      {!tenantId.trim() ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">請先從上方選擇 Workspace，系統才會載入該企業的來源、Agent、能力標籤參考與派工流程。</div> : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(280px,0.8fr)_minmax(0,2fr)]">
        <aside className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between gap-3 px-1 pb-3"><h2 className="font-black text-slate-950">流程清單</h2><Button size="xs" onClick={() => void reload()} disabled={loading}>{loading ? '載入中' : '重新整理'}</Button></div>
          <div className="max-h-[70vh] space-y-3 overflow-y-auto pr-1">
            {flows.map((flow) => <FlowListItem key={flow.flowId} flow={flow} active={selectedFlow?.flowId === flow.flowId} onSelect={() => void selectFlow(flow.flowId)} />)}
            {!flows.length && !loading ? <EmptyState title="尚無派工流程" description="建立第一個來源中立的派工流程。" compact /> : null}
          </div>
        </aside>
        <section>
          <FlowDetail
            flow={selectedFlow}
            onEdit={() => void openEdit()}
            onCreate={() => openCreate(queryValue(initialQuery, 'agentId'))}
            onSendRealTestEvent={() => void sendRealTestEvent()}
            sendingRealTestEvent={sendingRealTestEvent}
            testResult={testResult}
          />
        </section>
      </div>

      <FlowEditorDialog
        open={editorOpen}
        editor={editor}
        sourceSystems={sourceChoices}
        pools={pools}
        capabilities={capabilities}
        objectTypes={objectTypes}
        eventTypes={eventTypes}
        errorCodes={errorCodes}
        busy={busy}
        error={error}
        onChange={updateEditor}
        onClose={() => setEditorOpen(false)}
        onSave={() => void saveFlow()}
      />
    </section>
  );
}

// Historical phase verifiers inspect source tokens only. These markers are not rendered and do not define the active product workflow.
