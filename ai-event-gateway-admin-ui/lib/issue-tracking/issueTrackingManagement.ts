import type {
  CoreAdapterAction,
  CoreAdapterActionMetadata,
  CoreIssueTrackingRedmineConnectionResult,
  CoreIssueTrackingRedmineDiagnostics,
  CoreIssueTrackingRedmineTestIssueResult
} from '@/lib/types/core';

export type IssueTrackingManagementTone = 'success' | 'warning' | 'danger' | 'info';

export interface IssueTrackingManagementRow {
  label: string;
  ok: boolean;
  required: boolean;
  value: string;
  nextAction?: string;
}

export interface IssueTrackingManagementDecision {
  statusCode: string;
  statusLabel: string;
  title: string;
  description: string;
  nextAction: string;
  tone: IssueTrackingManagementTone;
  rows: IssueTrackingManagementRow[];
}

function boolLabel(value: unknown): string {
  return value === true ? 'enabled' : value === false ? 'disabled' : 'unknown';
}

function text(value: unknown, fallback = '-'): string {
  if (typeof value === 'string' && value.trim()) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  if (typeof value === 'boolean') return boolLabel(value);
  return fallback;
}

function isRedmineVendor(value: unknown): boolean {
  return typeof value === 'string' && value.trim().toUpperCase() === 'REDMINE';
}

export function redmineManagementDecision(metadata?: CoreAdapterActionMetadata | null, diagnostics?: CoreIssueTrackingRedmineDiagnostics | null): IssueTrackingManagementDecision {
  const rows: IssueTrackingManagementRow[] = [
    {
      label: 'Issue action enabled',
      ok: metadata?.issueEnabled === true || diagnostics?.issueActionEnabled === true,
      required: true,
      value: boolLabel(metadata?.issueEnabled ?? diagnostics?.issueActionEnabled),
      nextAction: 'Set ISSUE_ACTION_ENABLED=true.'
    },
    {
      label: 'Adapter executor enabled',
      ok: metadata?.executorEnabled === true || diagnostics?.executorEnabled === true,
      required: true,
      value: `${boolLabel(metadata?.executorEnabled ?? diagnostics?.executorEnabled)} / mode=${text(metadata?.executorMode ?? diagnostics?.executorMode)}`,
      nextAction: 'Set ADAPTER_EXECUTOR_ENABLED=true and ADAPTER_EXECUTOR_MODE=embedded or external.'
    },
    {
      label: 'Default issue vendor',
      ok: isRedmineVendor(metadata?.issueDefaultVendor ?? diagnostics?.defaultVendor),
      required: true,
      value: text(metadata?.issueDefaultVendor ?? diagnostics?.defaultVendor),
      nextAction: 'Set ISSUE_EXECUTOR_DEFAULT_VENDOR=REDMINE.'
    },
    {
      label: 'Redmine executor enabled',
      ok: metadata?.redmineExecutorEnabled === true || diagnostics?.redmineExecutorEnabled === true,
      required: true,
      value: boolLabel(metadata?.redmineExecutorEnabled ?? diagnostics?.redmineExecutorEnabled),
      nextAction: 'Set REDMINE_EXECUTOR_ENABLED=true.'
    },
    {
      label: 'Redmine base URL',
      ok: metadata?.redmineEndpointConfigured === true || diagnostics?.baseUrlConfigured === true,
      required: true,
      value: diagnostics?.configuredBaseUrl ? `configured (${diagnostics.configuredBaseUrl})` : boolLabel(metadata?.redmineEndpointConfigured ?? diagnostics?.baseUrlConfigured),
      nextAction: 'Set REDMINE_EXECUTOR_BASE_URL, for example http://baofire.com:8700.'
    },
    {
      label: 'Redmine API key',
      ok: diagnostics?.apiKeyConfigured === true,
      required: true,
      value: boolLabel(diagnostics?.apiKeyConfigured),
      nextAction: 'Set REDMINE_EXECUTOR_API_KEY on the Core service; do not paste the key into Admin UI.'
    },
    {
      label: 'Redmine project',
      ok: diagnostics?.projectIdConfigured === true,
      required: true,
      value: diagnostics?.configuredProjectId ? `configured (${diagnostics.configuredProjectId})` : boolLabel(diagnostics?.projectIdConfigured),
      nextAction: 'Set REDMINE_EXECUTOR_PROJECT_ID or choose a project before creating a test issue.'
    },
    {
      label: 'Redmine tracker',
      ok: diagnostics?.trackerIdConfigured === true,
      required: false,
      value: diagnostics?.configuredTrackerId ? `configured (${diagnostics.configuredTrackerId})` : boolLabel(diagnostics?.trackerIdConfigured),
      nextAction: 'Set REDMINE_EXECUTOR_TRACKER_ID when the Redmine project requires a tracker.'
    },
    {
      label: 'Auto execute pending',
      ok: metadata?.executorAutoExecutePending === true || diagnostics?.executorAutoExecutePending === true,
      required: false,
      value: boolLabel(metadata?.executorAutoExecutePending ?? diagnostics?.executorAutoExecutePending),
      nextAction: 'Set ADAPTER_EXECUTOR_AUTO_EXECUTE_PENDING=true or use Execute Pending from Admin UI.'
    }
  ];

  const requiredMissing = rows.filter((row) => row.required && !row.ok);
  if (requiredMissing.length > 0) {
    return {
      statusCode: 'ISSUE_TRACKING_BLOCKED',
      statusLabel: `${requiredMissing.length} required setting(s) missing`,
      title: 'Redmine 尚未可用',
      description: 'Issue Tracking 需要 Core issue action、adapter executor、Redmine vendor、base URL、API key 與 project 同時就緒。',
      nextAction: requiredMissing[0]?.nextAction ?? 'Complete required Redmine settings.',
      tone: 'danger',
      rows
    };
  }

  const optionalMissing = rows.filter((row) => !row.required && !row.ok);
  return {
    statusCode: optionalMissing.length ? 'ISSUE_TRACKING_READY_WITH_WARNINGS' : 'ISSUE_TRACKING_READY',
    statusLabel: optionalMissing.length ? 'ready, review warnings' : 'ready',
    title: optionalMissing.length ? 'Redmine 可用，但仍有建議設定' : 'Redmine Issue Tracking 已可用',
    description: optionalMissing.length
      ? '必要條件已通過；若你希望 Task 完成後自動同步 issue，請檢查 auto execute 或 tracker 設定。'
      : '設定已就緒，可讀取 Redmine project/tracker、建立測試 issue，並檢查 adapter action queue。',
    nextAction: optionalMissing[0]?.nextAction ?? 'Run connection test, then create a test issue.',
    tone: optionalMissing.length ? 'warning' : 'success',
    rows
  };
}

export interface AdapterActionQueueSummary {
  total: number;
  issueTracking: number;
  pending: number;
  retryWaiting: number;
  failed: number;
  completed: number;
  executing: number;
  needsOperator: boolean;
  nextAction: string;
}

export function adapterActionQueueSummary(actions: CoreAdapterAction[]): AdapterActionQueueSummary {
  const issueActions = actions.filter((action) => String(action.adapterType ?? '').toUpperCase() === 'ISSUE_TRACKING' || String(action.actionType ?? '').toUpperCase().startsWith('ISSUE_'));
  const statusCounts = issueActions.reduce<Record<string, number>>((counts, action) => {
    const status = String(action.status ?? 'UNKNOWN').toUpperCase();
    counts[status] = (counts[status] ?? 0) + 1;
    return counts;
  }, {});
  const pending = (statusCounts.PENDING ?? 0) + (statusCounts.EXECUTOR_UNAVAILABLE ?? 0);
  const retryWaiting = statusCounts.RETRY_WAITING ?? 0;
  const failed = statusCounts.FAILED ?? 0;
  const completed = statusCounts.COMPLETED ?? 0;
  const executing = (statusCounts.CLAIMED ?? 0) + (statusCounts.EXECUTING ?? 0);
  const needsOperator = pending + retryWaiting + failed > 0;
  return {
    total: actions.length,
    issueTracking: issueActions.length,
    pending,
    retryWaiting,
    failed,
    completed,
    executing,
    needsOperator,
    nextAction: failed > 0
      ? 'Review failed actions, fix Redmine settings, then retry failed sync.'
      : pending > 0 || retryWaiting > 0
        ? 'Run Execute Pending or wait for the adapter worker.'
        : issueActions.length > 0
          ? 'Issue Tracking queue is clear; inspect completed results when needed.'
          : 'No issue tracking actions yet. Create a test task or test issue first.'
  };
}

export function redmineConnectionLabel(result?: CoreIssueTrackingRedmineConnectionResult | null): string {
  if (!result) return 'not tested';
  if (result.ok) return `OK · ${result.projects?.length ?? 0} project(s), ${result.trackers?.length ?? 0} tracker(s)`;
  return result.message ?? 'connection failed';
}

export function redmineTestIssueLabel(result?: CoreIssueTrackingRedmineTestIssueResult | null): string {
  if (!result) return 'not created';
  if (result.ok) return `created issue ${result.issueId ?? '-'}`;
  return result.message ?? 'test issue failed';
}

export function redmineCoreEnvTemplate(): string {
  return `# Core / adapter-worker Redmine settings\nISSUE_ACTION_ENABLED=true\nISSUE_ACTION_CREATE_ON_COMPLETED_TASK=true\nISSUE_ACTION_CREATE_ON_FAILED_TASK=true\nISSUE_ACTION_UPDATE_EXISTING_ISSUE_COMMENT=true\n\nADAPTER_EXECUTOR_MODE=embedded\nADAPTER_EXECUTOR_ENABLED=true\nADAPTER_EXECUTOR_AUTO_EXECUTE_PENDING=true\nADAPTER_EXECUTOR_AUDIT_STORE=MYBATIS\n\nISSUE_EXECUTOR_DEFAULT_VENDOR=REDMINE\nREDMINE_EXECUTOR_ENABLED=true\nREDMINE_EXECUTOR_BASE_URL=http://baofire.com:8700\nREDMINE_EXECUTOR_API_KEY=<redmine-api-key>\nREDMINE_EXECUTOR_PROJECT_ID=redmine\nREDMINE_EXECUTOR_TRACKER_ID=3\nREDMINE_EXECUTOR_NAME=redmine-issue-executor\nREDMINE_EXECUTOR_ISSUE_URL_TEMPLATE=http://baofire.com:8700/issues/{issueId}\nREDMINE_EXECUTOR_PRIORITY_CRITICAL=CRITICAL\nREDMINE_EXECUTOR_PRIORITY_HIGH=HIGH\nREDMINE_EXECUTOR_PRIORITY_MEDIUM=MIDDLE\nREDMINE_EXECUTOR_PRIORITY_LOW=LOW\n\n# Sanity check from shell; do not store the API key in the browser.\ncurl -s -H "X-Redmine-API-Key: <redmine-api-key>" http://baofire.com:8700/projects/redmine.json`;
}
