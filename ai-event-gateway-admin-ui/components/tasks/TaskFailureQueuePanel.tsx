'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { CommandMessage } from '@/components/common/CommandMessage';
import { DispatchOperatorActions } from '@/components/common/DispatchOperatorActions';
import { DispatchUserFacingReason } from '@/components/common/DispatchUserFacingReason';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorBox } from '@/components/common/ErrorBox';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { TaskActionDialog } from '@/components/tasks/TaskActionDialog';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { parseDispatchUserFacingError } from '@/lib/dispatch-readiness/dispatchUserFacingError';
import { buildDispatchOperatorActions, type DispatchOperatorCommand } from '@/lib/dispatch-readiness/dispatchOperatorActions';
import type { CoreAdminFailureQueueItem, CoreAdminFailureQueueResponse } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

interface QueueDispatchErrorGroup {
  code: string;
  count: number;
  waiting: number;
  blocked: number;
  failed: number;
  message: string;
  nextAction?: string;
}

function reasonCategoryLabel(category?: string): string {
  const normalized = String(category ?? '').toUpperCase();
  if (normalized === 'WAITING_RETRY') return '等待重試';
  if (normalized === 'DISPATCH_BLOCKED') return '派工阻擋';
  if (normalized === 'TERMINAL_FAILURE') return '終止失敗';
  if (normalized === 'DEAD_LETTER') return 'Dead Letter';
  if (normalized === 'ESCALATED') return '已升級';
  if (normalized === 'NEEDS_OPERATOR_RECONCILIATION') return '需要人工對帳';
  return normalized || '未分類';
}

function reasonCategoryTone(category?: string): string {
  const normalized = String(category ?? '').toUpperCase();
  if (normalized === 'TERMINAL_FAILURE' || normalized === 'DEAD_LETTER') return 'border-rose-200 bg-rose-50 text-rose-800';
  if (normalized === 'DISPATCH_BLOCKED' || normalized === 'ESCALATED' || normalized === 'NEEDS_OPERATOR_RECONCILIATION') return 'border-amber-200 bg-amber-50 text-amber-800';
  if (normalized === 'WAITING_RETRY') return 'border-blue-200 bg-blue-50 text-blue-800';
  return 'border-slate-200 bg-slate-50 text-slate-700';
}

function dispatchErrorForItem(item: CoreAdminFailureQueueItem) {
  const structured = item.userFacingDispatchError ?? item.latestRoutingDecision?.userFacingError;
  const value = item.dispatchWaitReason ?? item.dispatchRetryReason ?? item.blockedReason ?? item.failureReason ?? item.lifecycleReason ?? item.latestRoutingDecision?.decisionReason;
  const parsed = parseDispatchUserFacingError(value, structured);
  if (!parsed.code?.startsWith('DISPATCH_')) return undefined;
  return parsed;
}

function buildDispatchErrorGroups(items: CoreAdminFailureQueueItem[]): QueueDispatchErrorGroup[] {
  const groups = new Map<string, QueueDispatchErrorGroup>();
  items.forEach((item) => {
    const parsed = dispatchErrorForItem(item);
    if (!parsed?.code) return;
    const category = String(item.reasonCategory ?? '').toUpperCase();
    const current = groups.get(parsed.code) ?? {
      code: parsed.code,
      count: 0,
      waiting: 0,
      blocked: 0,
      failed: 0,
      message: parsed.message,
      nextAction: parsed.nextAction,
    };
    current.count += 1;
    if (category === 'WAITING_RETRY') current.waiting += 1;
    if (category === 'DISPATCH_BLOCKED' || category === 'ESCALATED' || category === 'NEEDS_OPERATOR_RECONCILIATION') current.blocked += 1;
    if (category === 'TERMINAL_FAILURE' || category === 'DEAD_LETTER') current.failed += 1;
    current.message = current.message || parsed.message;
    current.nextAction = current.nextAction || parsed.nextAction;
    groups.set(parsed.code, current);
  });
  return Array.from(groups.values()).sort((left, right) => {
    if (right.blocked !== left.blocked) return right.blocked - left.blocked;
    if (right.failed !== left.failed) return right.failed - left.failed;
    if (right.count !== left.count) return right.count - left.count;
    return left.code.localeCompare(right.code);
  });
}

export function TaskFailureQueuePanel() {
  const [data, setData] = useState<CoreAdminFailureQueueResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | undefined>();
  const [selectedCode, setSelectedCode] = useState<string>('ALL');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [pendingAction, setPendingAction] = useState<{ action: 'manualRetry' | 'escalate' | 'deadLetter' | 'triggerRecoveryNow'; taskId: string } | null>(null);

  const load = useCallback(async () => {
    setError(null);
    const response = await coreAdminApi.getTaskFailureQueue(200);
    setData(response);
    setLastUpdatedAt(new Date().toISOString());
  }, []);

  useEffect(() => {
    void load().catch((err) => setError(err instanceof Error ? err.message : 'Unknown failure queue error')).finally(() => setLoading(false));
  }, [load]);

  async function refresh() {
    setRefreshing(true);
    try {
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown failure queue error');
    } finally {
      setRefreshing(false);
    }
  }

  async function runAction(action: 'manualRetry' | 'escalate' | 'deadLetter' | 'triggerRecoveryNow', taskId: string, reason: string) {
    let result;
    if (action === 'manualRetry' || action === 'triggerRecoveryNow') result = await coreAdminApi.manualRetryTask(taskId, { reason, immediate: true });
    else if (action === 'escalate') result = await coreAdminApi.escalateTask(taskId, { reason });
    else result = await coreAdminApi.deadLetterTask(taskId, { reason });
    setMessage(result.message);
    setPendingAction(null);
    await refresh();
  }


  function runOperatorCommand(command: DispatchOperatorCommand, taskId: string) {
    if (command === 'triggerRecoveryNow') {
      setPendingAction({ action: 'triggerRecoveryNow', taskId });
      return;
    }
    if (command === 'manualRetry') {
      setPendingAction({ action: 'manualRetry', taskId });
      return;
    }
    if (command === 'escalate') {
      setPendingAction({ action: 'escalate', taskId });
      return;
    }
    if (command === 'deadLetter') {
      setPendingAction({ action: 'deadLetter', taskId });
    }
  }

  const dispatchErrorGroups = useMemo(() => buildDispatchErrorGroups(data?.items ?? []), [data?.items]);
  const filteredItems = useMemo(() => {
    return (data?.items ?? []).filter((item) => {
      const parsed = dispatchErrorForItem(item);
      const codeOk = selectedCode === 'ALL' || parsed?.code === selectedCode;
      const categoryOk = selectedCategory === 'ALL' || item.reasonCategory === selectedCategory;
      return codeOk && categoryOk;
    });
  }, [data?.items, selectedCategory, selectedCode]);

  if (loading) return <LoadingBox label="讀取 Core Admin Failure Queue..." />;
  if (error) return <ErrorBox message={error} />;
  if (!data || data.items.length === 0) return <EmptyState title="目前沒有 Failure Queue 任務" description="Core 沒有 RETRY_WAIT / FAILED / ESCALATED / DEAD_LETTER / ORPHANED / RECONCILING 任務。" />;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <CommandMessage message={message} />
        <RefreshButton refreshing={refreshing} lastUpdatedAt={lastUpdatedAt} onRefresh={() => void refresh()} />
      </div>

      <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        <div className="font-bold">Admin Failure Queue</div>
        <p className="mt-1 leading-6">集中顯示 retry wait、dispatch blocked、terminal failed、escalated、dead-letter、orphaned 與 reconciling 任務。P1-4 後不再把等待重試直接當成 failure reason。</p>
        <div className="mt-2 flex flex-wrap gap-2 text-xs">
          {Object.entries(data.counts ?? {}).map(([status, count]) => <span key={status} className="rounded-full bg-white px-2.5 py-1 font-semibold text-amber-800">{status}: {count}</span>)}
          {Object.entries(data.reasonCategoryCounts ?? {}).map(([category, count]) => (
            <button key={category} type="button" onClick={() => setSelectedCategory(category)} className="rounded-full bg-white px-2.5 py-1 font-semibold text-amber-800 hover:bg-amber-100">{reasonCategoryLabel(category)}: {count}</button>
          ))}
          {selectedCategory !== 'ALL' || selectedCode !== 'ALL' ? (
            <button type="button" onClick={() => { setSelectedCategory('ALL'); setSelectedCode('ALL'); }} className="rounded-full bg-amber-900 px-2.5 py-1 font-bold text-white">清除 queue 篩選</button>
          ) : null}
          {data.generatedAt ? <span className="rounded-full bg-white px-2.5 py-1 font-semibold text-amber-800">Generated: {formatDateTime(data.generatedAt)}</span> : null}
        </div>
      </div>

      {dispatchErrorGroups.length > 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="text-sm font-black text-slate-950">Dispatch error code groups</div>
              <p className="mt-1 text-xs leading-5 text-slate-500">依 `userFacingDispatchError.code` 分組。點選 code 後只看同類派工問題。</p>
            </div>
            <div className="text-xs font-bold text-slate-500">{dispatchErrorGroups.reduce((sum, group) => sum + group.count, 0)} 筆有 DISPATCH_* code</div>
          </div>
          <div className="mt-3 grid gap-2 lg:grid-cols-2 xl:grid-cols-3">
            {dispatchErrorGroups.map((group) => (
              <div key={group.code} className={`rounded-xl border p-3 text-left text-sm shadow-sm ${selectedCode === group.code ? 'border-blue-300 bg-blue-50' : 'border-slate-200 bg-white'}`}>
                <button type="button" onClick={() => setSelectedCode(group.code)} className="block w-full text-left hover:opacity-80">
                  <div className="flex items-center justify-between gap-3">
                    <span className="break-all font-black text-slate-950">{group.code}</span>
                    <span className="shrink-0 rounded-full bg-slate-900 px-2.5 py-1 text-xs font-black text-white">{group.count}</span>
                  </div>
                  <div className="mt-1 text-xs leading-5 text-slate-600">{group.message}</div>
                  <div className="mt-2 flex flex-wrap gap-2 text-[11px] font-bold text-slate-500">
                    <span>Blocked：{group.blocked}</span>
                    <span>Waiting：{group.waiting}</span>
                    <span>Failed：{group.failed}</span>
                    {group.nextAction ? <span className="break-words">Next：{group.nextAction}</span> : null}
                  </div>
                </button>
                <div className="mt-3 border-t border-slate-100 pt-3">
                  <DispatchOperatorActions
                    compact
                    actions={buildDispatchOperatorActions(group, { includeRunbook: false })}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {filteredItems.length === 0 ? (
        <EmptyState title="沒有符合篩選的 Failure Queue 任務" description="請清除 dispatch code 或 reason category 篩選。" />
      ) : null}

      <div className="space-y-3">
        {filteredItems.map((item) => {
          const parsed = dispatchErrorForItem(item);
          return (
            <div key={item.taskId} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="text-sm font-bold text-slate-950">{item.taskId}</div>
                    <span className={`rounded-full border px-2.5 py-1 text-[11px] font-black ${reasonCategoryTone(item.reasonCategory)}`}>{reasonCategoryLabel(item.reasonCategory)}</span>
                    {parsed?.code ? <span className="rounded-full bg-slate-900 px-2.5 py-1 text-[11px] font-black text-white">{parsed.code}</span> : null}
                  </div>
                  <div className="mt-1 text-xs text-slate-500">Incident: {item.incidentId ?? '-'} · Type: {item.taskType ?? '-'} · Priority: {item.priority ?? '-'}</div>
                  <div className="mt-1 text-xs text-slate-500">Object: {item.objectType ?? '-'} / {item.objectId ?? '-'}</div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {item.status ? <StatusBadge status={String(item.status)} /> : null}
                  <Link href={`/tasks/${encodeURIComponent(item.taskId)}`} className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50">Timeline</Link>
                  {Boolean(item.actions?.manualRetry) ? <button type="button" onClick={() => setPendingAction({ action: 'manualRetry', taskId: item.taskId })} className="rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-blue-700">Manual Retry</button> : null}
                  {Boolean(item.actions?.escalate) ? <button type="button" onClick={() => setPendingAction({ action: 'escalate', taskId: item.taskId })} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-bold text-amber-700 hover:bg-amber-50">Escalate</button> : null}
                  {Boolean(item.actions?.deadLetter) ? <button type="button" onClick={() => setPendingAction({ action: 'deadLetter', taskId: item.taskId })} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-bold text-rose-700 hover:bg-rose-50">DLQ</button> : null}
                </div>
              </div>
              <div className="mt-4 grid gap-3 md:grid-cols-4">
                <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3"><div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Updated</div><div className="mt-1 text-sm font-semibold text-slate-800">{item.updatedAt ? formatDateTime(item.updatedAt) : '-'}</div></div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3"><div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Next Retry</div><div className="mt-1 text-sm font-semibold text-slate-800">{item.nextDispatchAttemptAt ? formatDateTime(item.nextDispatchAttemptAt) : '-'}</div></div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3"><div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Attempts</div><div className="mt-1 text-sm font-semibold text-slate-800">{item.dispatchAttemptCount ?? 0}</div></div>
                <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3"><div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Latest</div><div className="mt-1 text-sm font-semibold text-slate-800">{item.latestTimelineEvent?.action ?? '-'}</div></div>
              </div>

              {item.blockedReason ? (
                <div className="mt-4 rounded-xl bg-amber-50 p-4 text-sm text-amber-800">
                  <div className="mb-1 text-xs font-black uppercase tracking-wide text-amber-700">Blocked reason</div>
                  <DispatchUserFacingReason value={item.blockedReason} error={item.userFacingDispatchError} showOperatorActions actionContext={{ taskId: item.taskId, agentId: item.latestRoutingDecision?.selectedAgentId, reasonCategory: item.reasonCategory, includeTaskCommands: true, includeRunbook: false, canTriggerRecoveryNow: Boolean(item.actions?.manualRetry), canManualRetry: Boolean(item.actions?.manualRetry), canEscalate: Boolean(item.actions?.escalate), canDeadLetter: Boolean(item.actions?.deadLetter) }} onOperatorCommand={(command) => runOperatorCommand(command, item.taskId)} codeClassName="inline-flex rounded-full bg-amber-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white" detailsClassName="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-xs font-semibold" technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-amber-900" />
                </div>
              ) : null}
              {item.dispatchWaitReason ? (
                <div className="mt-4 rounded-xl bg-blue-50 p-4 text-sm text-blue-800">
                  <div className="mb-1 text-xs font-black uppercase tracking-wide text-blue-700">Dispatch wait / retry reason</div>
                  <DispatchUserFacingReason value={item.dispatchWaitReason} error={item.userFacingDispatchError} showOperatorActions actionContext={{ taskId: item.taskId, agentId: item.latestRoutingDecision?.selectedAgentId, reasonCategory: item.reasonCategory, includeTaskCommands: true, includeRunbook: false, canTriggerRecoveryNow: Boolean(item.actions?.manualRetry), canManualRetry: Boolean(item.actions?.manualRetry), canEscalate: Boolean(item.actions?.escalate), canDeadLetter: Boolean(item.actions?.deadLetter) }} onOperatorCommand={(command) => runOperatorCommand(command, item.taskId)} codeClassName="inline-flex rounded-full bg-blue-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white" detailsClassName="rounded-xl border border-blue-100 bg-white/70 px-3 py-2 text-xs font-semibold" technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-blue-900" />
                </div>
              ) : null}
              {item.failureReason ? (
                <div className="mt-4 rounded-xl bg-rose-50 p-4 text-sm text-rose-800">
                  <div className="mb-1 text-xs font-black uppercase tracking-wide text-rose-700">Terminal failure reason</div>
                  <DispatchUserFacingReason value={item.failureReason} error={item.userFacingDispatchError} showOperatorActions actionContext={{ taskId: item.taskId, agentId: item.latestRoutingDecision?.selectedAgentId, reasonCategory: item.reasonCategory, includeTaskCommands: true, includeRunbook: false, canTriggerRecoveryNow: Boolean(item.actions?.manualRetry), canManualRetry: Boolean(item.actions?.manualRetry), canEscalate: Boolean(item.actions?.escalate), canDeadLetter: Boolean(item.actions?.deadLetter) }} onOperatorCommand={(command) => runOperatorCommand(command, item.taskId)} codeClassName="inline-flex rounded-full bg-rose-900 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white" detailsClassName="rounded-xl border border-rose-100 bg-white/70 px-3 py-2 text-xs font-semibold" technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-rose-900" />
                </div>
              ) : null}
              {!item.blockedReason && !item.dispatchWaitReason && !item.failureReason && (item.lifecycleReason || item.dispatchRetryReason) ? (
                <div className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
                  <div className="mb-1 text-xs font-black uppercase tracking-wide text-slate-500">Lifecycle note</div>
                  <DispatchUserFacingReason value={item.lifecycleReason ?? item.dispatchRetryReason} codeClassName="inline-flex rounded-full bg-slate-800 px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-white" detailsClassName="rounded-xl border border-slate-100 bg-white/70 px-3 py-2 text-xs font-semibold" technicalClassName="mt-2 break-words whitespace-pre-wrap font-mono leading-5 text-slate-700" />
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
      <TaskActionDialog
        open={pendingAction !== null}
        title={pendingAction?.action === 'deadLetter' ? '移至 Dead Letter' : pendingAction?.action === 'escalate' ? '升級人工處理' : '重新派工'}
        target={pendingAction?.taskId ?? ''}
        description="此操作會寫入 Core Task timeline，並由 Core 權威狀態機處理。"
        confirmLabel={pendingAction?.action === 'deadLetter' ? '確認移至 Dead Letter' : pendingAction?.action === 'escalate' ? '確認升級' : '確認重新派工'}
        tone={pendingAction?.action === 'deadLetter' ? 'danger' : 'warning'}
        requiredPhrase={pendingAction?.action === 'deadLetter' ? 'CONFIRM_DEAD_LETTER' : undefined}
        onCancel={() => setPendingAction(null)}
        onConfirm={async ({ reason }) => {
          if (pendingAction) await runAction(pendingAction.action, pendingAction.taskId, reason);
        }}
      />
    </div>
  );
}
