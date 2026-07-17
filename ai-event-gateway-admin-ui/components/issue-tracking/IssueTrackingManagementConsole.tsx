'use client';

import { useEffect, useMemo, useState } from 'react';
import { ErrorBox } from '@/components/common/ErrorBox';
import { JsonViewer } from '@/components/common/JsonViewer';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RawDiagnosticsPanel } from '@/components/common/RawDiagnosticsPanel';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import {
  adapterActionQueueSummary,
  redmineConnectionLabel,
  redmineCoreEnvTemplate,
  redmineManagementDecision,
  redmineTestIssueLabel
} from '@/lib/issue-tracking/issueTrackingManagement';
import type {
  CoreAdapterAction,
  CoreAdapterActionMetadata,
  CoreAdapterExecutorAuditRecord,
  CoreIssueTrackingRedmineCollectionItem,
  CoreIssueTrackingRedmineConnectionResult,
  CoreIssueTrackingRedmineDiagnostics,
  CoreIssueTrackingRedmineTestIssueResult
} from '@/lib/types/core';

type BusyAction = 'load' | 'diagnostics' | 'connection' | 'test-issue' | 'queue' | 'execute-pending' | null;

function toneClass(tone: 'success' | 'warning' | 'danger' | 'info'): string {
  if (tone === 'success') return 'border-emerald-200 bg-emerald-50 text-emerald-950';
  if (tone === 'warning') return 'border-amber-200 bg-amber-50 text-amber-950';
  if (tone === 'danger') return 'border-rose-200 bg-rose-50 text-rose-950';
  return 'border-blue-200 bg-blue-50 text-blue-950';
}

function itemLabel(item: CoreIssueTrackingRedmineCollectionItem): string {
  const id = item.identifier || item.id || '-';
  return item.name ? `${item.name} (${id})` : id;
}

function itemValue(item: CoreIssueTrackingRedmineCollectionItem): string {
  return item.identifier || item.id || '';
}


function settingStatus(ok?: boolean): string {
  return ok === true ? '已設定' : ok === false ? '未設定' : '未知';
}

function maskedConfigValue(value?: string | null): string {
  if (typeof value === 'string' && value.trim()) return value.trim();
  return '未設定';
}

function statusClass(status?: string): string {
  const normalized = String(status ?? 'UNKNOWN').toUpperCase();
  if (normalized === 'COMPLETED') return 'bg-emerald-100 text-emerald-800';
  if (['FAILED', 'CANCELLED'].includes(normalized)) return 'bg-rose-100 text-rose-800';
  if (['PENDING', 'RETRY_WAITING', 'EXECUTOR_UNAVAILABLE'].includes(normalized)) return 'bg-amber-100 text-amber-800';
  if (['CLAIMED', 'EXECUTING'].includes(normalized)) return 'bg-blue-100 text-blue-800';
  return 'bg-slate-100 text-slate-700';
}

function ActionQueueTable({ actions }: Readonly<{ actions: CoreAdapterAction[] }>) {
  const issueActions = actions.filter((action) => String(action.adapterType ?? '').toUpperCase() === 'ISSUE_TRACKING' || String(action.actionType ?? '').toUpperCase().startsWith('ISSUE_')).slice(0, 20);
  if (!issueActions.length) {
    return <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5 text-sm leading-6 text-slate-500">目前沒有 Issue Tracking action。請先完成任務或建立 Redmine 測試 issue，再回來查看 queue/result。</div>;
  }
  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-xs font-black uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 text-left">Action</th>
            <th className="px-4 py-3 text-left">Type</th>
            <th className="px-4 py-3 text-left">Status</th>
            <th className="px-4 py-3 text-left">Task / Agent</th>
            <th className="px-4 py-3 text-left">Result</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 bg-white">
          {issueActions.map((action) => (
            <tr key={action.actionId}>
              <td className="px-4 py-3 font-mono text-xs text-slate-600">{action.actionId}</td>
              <td className="px-4 py-3 text-slate-700">{action.actionType ?? action.adapterType ?? '-'}</td>
              <td className="px-4 py-3"><span className={`rounded-full px-2.5 py-1 text-xs font-black ${statusClass(action.status)}`}>{action.status ?? 'UNKNOWN'}</span></td>
              <td className="px-4 py-3 text-xs leading-5 text-slate-600"><div>{action.taskId ?? '-'}</div><div>{action.agentId ?? '-'}</div></td>
              <td className="px-4 py-3 text-xs leading-5 text-slate-600"><div>{action.responseRef ?? '-'}</div><div className="text-rose-700">{action.lastError ?? ''}</div></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AuditTable({ audit }: Readonly<{ audit: CoreAdapterExecutorAuditRecord[] }>) {
  const rows = audit.filter((record) => String(record.adapterType ?? '').toUpperCase() === 'ISSUE_TRACKING' || String(record.actionId ?? '').trim()).slice(0, 10);
  if (!rows.length) return null;
  return (
    <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <summary className="cursor-pointer text-sm font-black text-slate-800">最近 adapter executor audit</summary>
      <div className="mt-3 grid gap-2">
        {rows.map((row, index) => (
          <div key={`${row.auditId ?? row.actionId ?? index}`} className="rounded-xl bg-white p-3 text-xs leading-5 text-slate-600">
            <div className="font-black text-slate-800">{row.outcome ?? row.status ?? 'audit'} · {row.executorName ?? '-'}</div>
            <div>Action: {row.actionId ?? '-'}</div>
            <div>{row.message ?? row.error ?? '-'}</div>
          </div>
        ))}
      </div>
    </details>
  );
}

export function IssueTrackingManagementConsole() {
  const [metadata, setMetadata] = useState<CoreAdapterActionMetadata | null>(null);
  const [diagnostics, setDiagnostics] = useState<CoreIssueTrackingRedmineDiagnostics | null>(null);
  const [connectionResult, setConnectionResult] = useState<CoreIssueTrackingRedmineConnectionResult | null>(null);
  const [testIssueResult, setTestIssueResult] = useState<CoreIssueTrackingRedmineTestIssueResult | null>(null);
  const [actions, setActions] = useState<CoreAdapterAction[]>([]);
  const [audit, setAudit] = useState<CoreAdapterExecutorAuditRecord[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [selectedTrackerId, setSelectedTrackerId] = useState('');
  const [severity, setSeverity] = useState('CRITICAL');
  const [eventMessage, setEventMessage] = useState('Postman 手動整合測試：設備溫度超標');
  const [objectId, setObjectId] = useState('EQP-POSTMAN-001');
  const [eventType, setEventType] = useState('EQUIPMENT_ALARM');
  const [errorCode, setErrorCode] = useState('TEMP_HIGH');
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState<BusyAction>('load');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const decision = useMemo(() => redmineManagementDecision(metadata, diagnostics), [metadata, diagnostics]);
  const queueSummary = useMemo(() => adapterActionQueueSummary(actions), [actions]);
  const projects = connectionResult?.projects ?? [];
  const trackers = connectionResult?.trackers ?? [];

  async function loadAll() {
    setBusy('load');
    setError(null);
    try {
      const [nextMetadata, nextDiagnostics, nextActions, nextAudit] = await Promise.all([
        coreAdminApi.getAdapterActionMetadata(),
        coreAdminApi.getRedmineIssueTrackingDiagnostics().catch(() => null),
        coreAdminApi.getAdapterActions(100).catch(() => []),
        coreAdminApi.getAdapterExecutorAudit(50).catch(() => [])
      ]);
      setMetadata(nextMetadata);
      setDiagnostics(nextDiagnostics);
      setActions(nextActions);
      setAudit(nextAudit);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Issue Tracking 管理資料載入失敗。');
    } finally {
      setBusy(null);
    }
  }

  useEffect(() => {
    loadAll();
  }, []);

  async function refreshDiagnostics() {
    setBusy('diagnostics');
    setError(null);
    try {
      const [nextMetadata, nextDiagnostics] = await Promise.all([
        coreAdminApi.getAdapterActionMetadata(),
        coreAdminApi.getRedmineIssueTrackingDiagnostics()
      ]);
      setMetadata(nextMetadata);
      setDiagnostics(nextDiagnostics);
      setMessage('已重新讀取 Core Redmine / adapter 設定。');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Redmine diagnostics 載入失敗。');
    } finally {
      setBusy(null);
    }
  }

  async function testConnection() {
    setBusy('connection');
    setError(null);
    try {
      const result = await coreAdminApi.testRedmineIssueTrackingConnection();
      setConnectionResult(result);
      if (!selectedProjectId && result.projects?.[0]) setSelectedProjectId(itemValue(result.projects[0]));
      if (!selectedTrackerId && result.trackers?.[0]) setSelectedTrackerId(itemValue(result.trackers[0]));
      setMessage(redmineConnectionLabel(result));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Redmine 連線測試失敗。');
    } finally {
      setBusy(null);
    }
  }

  async function createTestIssue() {
    setBusy('test-issue');
    setError(null);
    try {
      const result = await coreAdminApi.createRedmineTestIssue({
        projectId: selectedProjectId || undefined,
        trackerId: selectedTrackerId || undefined,
        severity,
        message: eventMessage,
        objectId,
        eventType,
        errorCode,
        subject: subject || undefined,
        description: description || undefined
      });
      setTestIssueResult(result);
      setMessage(redmineTestIssueLabel(result));
      const nextActions = await coreAdminApi.getAdapterActions(100).catch(() => actions);
      setActions(nextActions);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '建立 Redmine 測試 issue 失敗。');
    } finally {
      setBusy(null);
    }
  }

  async function refreshQueue() {
    setBusy('queue');
    setError(null);
    try {
      const [nextActions, nextAudit] = await Promise.all([
        coreAdminApi.getAdapterActions(100),
        coreAdminApi.getAdapterExecutorAudit(50).catch(() => [])
      ]);
      setActions(nextActions);
      setAudit(nextAudit);
      setMessage('已重新整理 Issue Tracking action queue / executor audit。');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '重新整理 action queue 失敗。');
    } finally {
      setBusy(null);
    }
  }

  async function executePending() {
    setBusy('execute-pending');
    setError(null);
    try {
      await coreAdminApi.executePendingAdapterActions(50);
      const [nextActions, nextAudit] = await Promise.all([
        coreAdminApi.getAdapterActions(100).catch(() => actions),
        coreAdminApi.getAdapterExecutorAudit(50).catch(() => audit)
      ]);
      setActions(nextActions);
      setAudit(nextAudit);
      setMessage('已要求 Core 執行 pending adapter actions。');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Execute pending adapter actions 失敗。');
    } finally {
      setBusy(null);
    }
  }

  if (busy === 'load') return <LoadingBox label="Loading Issue Tracking management console..." />;

  return (
    <div className="space-y-6">
      {error ? <ErrorBox message={error} /> : null}
      {message ? <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm font-semibold text-blue-950">{message}</div> : null}

      <section className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5 text-indigo-950 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-sm font-black opacity-70">P2 hotfix · Redmine 設定來源</p>
            <h2 className="mt-1 text-lg font-black">Redmine Host / API Key 要設定在 Core 環境變數</h2>
            <p className="mt-2 text-sm leading-6 opacity-90">Admin UI 不保存 API Key；這裡只顯示 Core 是否已讀到設定與遮蔽後的 host / project / tracker。請修改 <code className="rounded bg-white/70 px-1 py-0.5">deploy/env/.env.local</code> 或 <code className="rounded bg-white/70 px-1 py-0.5">deploy/env/.env.release</code> 後重啟 Core。</p>
          </div>
          <button type="button" onClick={refreshDiagnostics} disabled={busy !== null} className="rounded-xl bg-white/80 px-4 py-2 text-sm font-black text-indigo-950 hover:bg-white disabled:opacity-60">
            {busy === 'diagnostics' ? '讀取中...' : '重新讀取 Core 設定'}
          </button>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-xl bg-white/70 p-3 text-xs leading-5">
            <div className="font-black">REDMINE_EXECUTOR_BASE_URL</div>
            <div className="mt-1 font-mono text-[11px]">{maskedConfigValue(diagnostics?.configuredBaseUrl)}</div>
            <div className="mt-1 opacity-80">{settingStatus(diagnostics?.baseUrlConfigured)}</div>
          </div>
          <div className="rounded-xl bg-white/70 p-3 text-xs leading-5">
            <div className="font-black">REDMINE_EXECUTOR_API_KEY</div>
            <div className="mt-1 font-mono text-[11px]">{diagnostics?.apiKeyConfigured ? 'configured · value hidden' : '未設定'}</div>
            <div className="mt-1 opacity-80">API Key 僅允許存在 Core env，不顯示明文。</div>
          </div>
          <div className="rounded-xl bg-white/70 p-3 text-xs leading-5">
            <div className="font-black">REDMINE_EXECUTOR_PROJECT_ID</div>
            <div className="mt-1 font-mono text-[11px]">{maskedConfigValue(diagnostics?.configuredProjectId)}</div>
            <div className="mt-1 opacity-80">{settingStatus(diagnostics?.projectIdConfigured)}</div>
          </div>
          <div className="rounded-xl bg-white/70 p-3 text-xs leading-5">
            <div className="font-black">REDMINE_EXECUTOR_TRACKER_ID</div>
            <div className="mt-1 font-mono text-[11px]">{maskedConfigValue(diagnostics?.configuredTrackerId)}</div>
            <div className="mt-1 opacity-80">{settingStatus(diagnostics?.trackerIdConfigured)}</div>
          </div>
        </div>
        <div className="mt-4 rounded-xl border border-indigo-200 bg-white/70 p-3 text-xs leading-5">
          <div className="font-black">本機 baofire.com 範例</div>
          <pre className="mt-2 overflow-auto whitespace-pre-wrap font-mono text-[11px]">{`REDMINE_EXECUTOR_BASE_URL=http://baofire.com:8700\nREDMINE_EXECUTOR_API_KEY=<redmine-api-key>\nREDMINE_EXECUTOR_PROJECT_ID=redmine\nREDMINE_EXECUTOR_TRACKER_ID=3`}</pre>
        </div>
      </section>

      <section className={`rounded-2xl border p-5 shadow-sm ${toneClass(decision.tone)}`}>
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-sm font-black opacity-70">P2 · Issue Tracking 管理化</p>
            <h2 className="mt-1 text-lg font-black">{decision.title}</h2>
            <p className="mt-2 text-sm leading-6 opacity-90">{decision.description}</p>
            <p className="mt-2 text-sm font-bold">下一步：{decision.nextAction}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={refreshDiagnostics} disabled={busy !== null} className="rounded-xl bg-white/70 px-4 py-2 text-sm font-black text-slate-900 hover:bg-white disabled:opacity-60">
              {busy === 'diagnostics' ? '讀取中...' : '重新檢查設定'}
            </button>
            <button type="button" onClick={testConnection} disabled={busy !== null} className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-black text-white hover:bg-slate-800 disabled:bg-slate-300">
              {busy === 'connection' ? '測試中...' : '測試 Redmine 連線'}
            </button>
          </div>
        </div>
        <div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {decision.rows.map((row) => (
            <div key={row.label} className="rounded-xl bg-white/60 p-3 text-xs leading-5">
              <div className="font-black">{row.ok ? '✅' : row.required ? '❌' : '⚠️'} {row.label}</div>
              <div className="mt-1 opacity-80">{row.value}</div>
              {!row.ok ? <div className="mt-1 font-semibold opacity-90">{row.nextAction}</div> : null}
            </div>
          ))}
        </div>
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <h2 className="text-base font-black text-slate-900">Step 1 · 選 Redmine Project / Tracker</h2>
              <p className="mt-1 text-sm leading-6 text-slate-500">按「測試 Redmine 連線」後，系統會由 Core 代理讀取 Redmine projects / trackers。API Key 只留在 Core 環境變數，不進瀏覽器。</p>
            </div>
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">{redmineConnectionLabel(connectionResult)}</span>
          </div>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <label className="block text-sm font-bold text-slate-700">
              Redmine Project
              <select value={selectedProjectId} onChange={(event) => setSelectedProjectId(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100">
                <option value="">使用 Core 預設 project</option>
                {projects.map((project) => <option key={itemValue(project)} value={itemValue(project)}>{itemLabel(project)}</option>)}
              </select>
            </label>
            <label className="block text-sm font-bold text-slate-700">
              Redmine Tracker
              <select value={selectedTrackerId} onChange={(event) => setSelectedTrackerId(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100">
                <option value="">使用 Core 預設 tracker / 不指定</option>
                {trackers.map((tracker) => <option key={itemValue(tracker)} value={itemValue(tracker)}>{itemLabel(tracker)}</option>)}
              </select>
            </label>
          </div>
          {connectionResult ? <div className="mt-4"><JsonViewer value={connectionResult} /></div> : null}
        </div>

        <details className="rounded-2xl border border-slate-200 bg-slate-50 p-5 shadow-sm" open>
          <summary className="cursor-pointer text-sm font-black text-slate-900">本機 Redmine / Core env 範本</summary>
          <p className="mt-2 text-sm leading-6 text-slate-500">請把 API Key 設在 Core / adapter-worker 環境，不要貼到 Admin UI。測完後可在 Redmine 重新產生 key。</p>
          <pre className="mt-4 max-h-96 overflow-auto rounded-xl bg-slate-950 p-4 text-xs leading-relaxed text-slate-100">{redmineCoreEnvTemplate()}</pre>
        </details>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-base font-black text-slate-900">Step 2 · 建立 Redmine 測試 Issue</h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">這會真的呼叫 Redmine `/issues.json`。用途是驗證 project、tracker、API key、severity priority mapping 與建立 issue 權限。P2 hotfix 已支援 issue 標題 / 內容 / 優先權：message 會放入標題，severity 會對應 Redmine priority。</p>
          </div>
          <button type="button" onClick={createTestIssue} disabled={busy !== null || decision.tone === 'danger'} className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-black text-white hover:bg-emerald-700 disabled:bg-slate-300">
            {busy === 'test-issue' ? '建立中...' : '送出測試 Issue'}
          </button>
        </div>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <label className="block text-sm font-bold text-slate-700">
            Severity / Redmine Priority
            <select value={severity} onChange={(event) => setSeverity(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100">
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MIDDLE / MEDIUM</option>
              <option value="LOW">LOW</option>
            </select>
          </label>
          <label className="block text-sm font-bold text-slate-700">
            Message
            <input value={eventMessage} onChange={(event) => setEventMessage(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
          </label>
          <label className="block text-sm font-bold text-slate-700">
            Object ID
            <input value={objectId} onChange={(event) => setObjectId(event.target.value)} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
          </label>
          <label className="block text-sm font-bold text-slate-700">
            Event / Error
            <div className="mt-1 grid gap-2 md:grid-cols-2">
              <input value={eventType} onChange={(event) => setEventType(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
              <input value={errorCode} onChange={(event) => setErrorCode(event.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
            </div>
          </label>
          <label className="block text-sm font-bold text-slate-700">
            Subject Override（可空白，空白時自動把 message 放入標題）
            <input value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="自動產生：[CRITICAL][Redmine] message - EQP / EVENT / ERROR" className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
          </label>
          <label className="block text-sm font-bold text-slate-700 md:col-span-2">
            Description Override（可空白，空白時自動帶入 severity/message/object/event/error）
            <textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={4} className="mt-1 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100" />
          </label>
        </div>
        {testIssueResult ? (
          <div className={`mt-4 rounded-2xl border p-4 ${testIssueResult.ok ? 'border-emerald-200 bg-emerald-50 text-emerald-950' : 'border-rose-200 bg-rose-50 text-rose-950'}`}>
            <div className="text-sm font-black">{redmineTestIssueLabel(testIssueResult)}</div>
            <p className="mt-1 text-sm leading-6 opacity-90">{testIssueResult.message}</p>
            {testIssueResult.issueUrl ? <a href={testIssueResult.issueUrl} target="_blank" rel="noreferrer" className="mt-3 inline-flex rounded-xl bg-white px-3 py-2 text-sm font-black text-slate-900 hover:bg-slate-50">開啟 Redmine Issue</a> : null}
            <div className="mt-3"><JsonViewer value={testIssueResult} /></div>
          </div>
        ) : null}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-base font-black text-slate-900">Step 3 · Action Queue / Result</h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">Task Result 寫入 Redmine 時會產生 ISSUE_TRACKING adapter action。這裡顯示 pending、retry、failed、completed 與最近 executor audit。</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={refreshQueue} disabled={busy !== null} className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50 disabled:opacity-60">{busy === 'queue' ? '重新整理中...' : '重新整理 queue'}</button>
            <button type="button" onClick={executePending} disabled={busy !== null} className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-black text-white hover:bg-indigo-700 disabled:bg-slate-300">{busy === 'execute-pending' ? '執行中...' : 'Execute Pending'}</button>
          </div>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-5">
          {[
            ['Issue actions', queueSummary.issueTracking],
            ['Pending', queueSummary.pending],
            ['Retry wait', queueSummary.retryWaiting],
            ['Failed', queueSummary.failed],
            ['Completed', queueSummary.completed]
          ].map(([label, value]) => (
            <div key={String(label)} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div>
              <div className="mt-2 text-2xl font-black text-slate-900">{value}</div>
            </div>
          ))}
        </div>
        <div className={`mt-4 rounded-2xl border p-4 ${queueSummary.needsOperator ? 'border-amber-200 bg-amber-50 text-amber-950' : 'border-emerald-200 bg-emerald-50 text-emerald-950'}`}>
          <div className="text-sm font-black">下一步：{queueSummary.nextAction}</div>
        </div>
        <div className="mt-4"><ActionQueueTable actions={actions} /></div>
        <div className="mt-4"><AuditTable audit={audit} /></div>
      </section>

      <RawDiagnosticsPanel
        title="進階診斷 · Issue Tracking raw state"
        description="工程師可展開檢查 metadata、diagnostics、Redmine connection result、test issue result、actions 與 executor audit。"
        value={{ metadata, diagnostics, connectionResult, testIssueResult, actions, audit }}
      />
    </div>
  );
}
