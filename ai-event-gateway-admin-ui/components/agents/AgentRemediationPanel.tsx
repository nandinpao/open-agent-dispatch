'use client';

import { useEffect, useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { JsonViewer } from '@/components/common/JsonViewer';
import { StatusBadge } from '@/components/common/StatusBadge';
import type {
  CoreAgentRemediationAction,
  CoreAgentRemediationProposal,
  CoreAgentRemediationProposalRequest,
  CoreAgentRemediationWorkflow,
  CoreAgentRemediationWorkflowCreateRequest,
  CoreAgentRemediationWorkflowDecisionRequest,
  CoreAgentRemediationStaleLeaseQueue,
  CoreAgentRemediationRecoveredLeaseQueue
} from '@/lib/types/core';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { formatDateTime } from '@/lib/utils/format';

interface AgentRemediationPanelProps {
  proposal?: CoreAgentRemediationProposal;
  workflows?: CoreAgentRemediationWorkflow[];
  commandRunning?: string | null;
  onCreateProposal: (request?: CoreAgentRemediationProposalRequest) => void;
  onCreateWorkflow: (request?: CoreAgentRemediationWorkflowCreateRequest) => void;
  onDecideWorkflow: (command: 'APPROVE_REMEDIATION_WORKFLOW' | 'REJECT_REMEDIATION_WORKFLOW' | 'CANCEL_REMEDIATION_WORKFLOW' | 'EXECUTE_REMEDIATION_WORKFLOW', workflowId: string, request?: CoreAgentRemediationWorkflowDecisionRequest) => void;
  onClearRuntimeBackoff: () => void;
  onSuspend: () => void;
  onSyncApprovedSkills: (skillCodes: string[]) => void;
}

const ACTION_LABELS: Record<string, string> = {
  CLEAR_RUNTIME_BACKOFF: 'Clear Runtime Backoff',
  DISCONNECT_ALL_RUNTIME_SESSIONS: 'Disconnect All Sessions',
  SUSPEND_AGENT: 'Suspend Agent',
  SYNC_APPROVED_SKILLS_AND_CAPABILITIES: 'Sync Legacy Policy Matches',
  REVIEW_OR_PUBLISH_SKILL_VERSION: 'Review / Publish Skill Version',
  REQUEST_AGENT_RECONNECT: 'Request Agent Reconnect',
  REVIEW_AGENT_ENROLLMENT: 'Review Agent Enrollment'
};

function actionLabel(action?: CoreAgentRemediationAction): string {
  const key = action?.actionType ?? 'UNKNOWN';
  return ACTION_LABELS[key] ?? key;
}

function readStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0);
}

function skillCodesFromProposal(proposal?: CoreAgentRemediationProposal): string[] {
  const values = new Set<string>();
  for (const action of proposal?.actions ?? []) {
    for (const item of readStringArray(action.commandHint?.skillCodes)) values.add(item);
  }
  for (const action of proposal?.skillProposal?.actions ?? []) {
    if (action.skillCode) values.add(action.skillCode);
    if (action.targetSkillCode) values.add(action.targetSkillCode);
    for (const item of readStringArray(action.commandHint?.skillCodes)) values.add(item);
  }
  return Array.from(values);
}

function promptText(title: string, fallback: string): string | undefined {
  const reason = window.prompt(title, fallback)?.trim();
  if (!reason) return undefined;
  if (reason.length < 12) {
    window.alert('原因太短；請至少輸入 12 個字元，以利後續審計。');
    return undefined;
  }
  return reason;
}

function promptProposalRequest(): CoreAgentRemediationProposalRequest | undefined {
  const reason = promptText('請輸入建立 Agent remediation proposal 的原因，至少 12 個字元。', 'Operator reviewed remediation signals.');
  if (!reason) return undefined;
  return { operatorId: 'admin-ui', reason, persistEvent: true };
}

function promptWorkflowRequest(proposal?: CoreAgentRemediationProposal): CoreAgentRemediationWorkflowCreateRequest | undefined {
  const reason = promptText('請輸入建立 remediation workflow 的原因，至少 12 個字元。', 'Create guarded remediation workflow for reviewed proposal.');
  if (!reason) return undefined;
  const riskAcknowledged = window.confirm('是否已確認業務風險與 rollback suggestion？按 OK 會標記 riskAcknowledged=true；高風險 action 仍需 approval。');
  const actionIds = (proposal?.actions ?? []).filter((action) => action.executable !== false).map((action) => action.actionId ?? action.actionType ?? '').filter(Boolean);
  return { proposalId: proposal?.proposalId, actionIds, operatorId: 'admin-ui', reason, riskAcknowledged };
}

function promptDecisionRequest(label: string): CoreAgentRemediationWorkflowDecisionRequest | undefined {
  const reason = promptText(`請輸入 ${label} 的原因，至少 12 個字元。`, `${label} remediation workflow after review.`);
  if (!reason) return undefined;
  return { operatorId: 'admin-ui', reason };
}

function promptExecutionRequest(): CoreAgentRemediationWorkflowDecisionRequest | undefined {
  const reason = promptText('請輸入 execute 的原因，至少 12 個字元。', 'Execute approved remediation workflow through guarded Core governance handlers.');
  if (!reason) return undefined;
  const dryRun = !window.confirm('按 OK 會真正執行 guarded actions；按 Cancel 只做 dry-run 並記錄結果。');
  return { operatorId: 'admin-ui', reason, dryRun };
}

function commandHintText(action: CoreAgentRemediationAction): string {
  const api = action.commandHint?.api;
  const ui = action.commandHint?.ui;
  if (typeof api === 'string') return api;
  if (typeof ui === 'string') return ui;
  return '-';
}

function RemediationActionCard({ action }: { action: CoreAgentRemediationAction }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-900">{actionLabel(action)}</div>
          <div className="mt-1 text-xs text-slate-400">{action.actionId ?? '-'} · {commandHintText(action)}</div>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={action.severity ?? 'LOW'} />
          <StatusBadge status={action.executable ? 'EXECUTABLE' : 'REVIEW_ONLY'} />
        </div>
      </div>
      {action.reason ? <p className="mt-3 text-sm text-slate-600">{action.reason}</p> : null}
      {action.prerequisites?.length ? (
        <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-slate-500">
          {action.prerequisites.map((item) => <li key={item}>{item}</li>)}
        </ul>
      ) : null}
      {action.metadata && Object.keys(action.metadata).length > 0 ? <div className="mt-3"><JsonViewer value={action.metadata} /></div> : null}
    </div>
  );
}

function WorkflowCard({ workflow, disabled, onDecideWorkflow }: { workflow: CoreAgentRemediationWorkflow; disabled: boolean; onDecideWorkflow: AgentRemediationPanelProps['onDecideWorkflow'] }) {
  const actionExecutions = workflow.actionExecutions ?? [];
  const workflowId = workflow.workflowId ?? '';
  const canApprove = workflow.status === 'PENDING_APPROVAL';
  const canReject = workflow.status === 'PENDING_APPROVAL';
  const canCancel = workflow.status !== 'EXECUTED' && workflow.status !== 'REJECTED' && workflow.status !== 'CANCELLED';
  const canExecute = workflow.status === 'APPROVED';
  const run = (command: 'APPROVE_REMEDIATION_WORKFLOW' | 'REJECT_REMEDIATION_WORKFLOW' | 'CANCEL_REMEDIATION_WORKFLOW' | 'EXECUTE_REMEDIATION_WORKFLOW', label: string) => {
    const request = command === 'EXECUTE_REMEDIATION_WORKFLOW' ? promptExecutionRequest() : promptDecisionRequest(label);
    if (request && workflowId) onDecideWorkflow(command, workflowId, request);
  };
  return (
    <div className="rounded-2xl border border-violet-200 bg-white p-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-900">{workflow.workflowId ?? '-'}</div>
          <div className="mt-1 text-xs text-slate-400">Updated {workflow.updatedAt ? formatDateTime(workflow.updatedAt) : '-'}</div>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={workflow.status ?? 'UNKNOWN'} />
          <StatusBadge status={workflow.severity ?? 'LOW'} />
          {workflow.approvalRequired ? <StatusBadge status="APPROVAL_REQUIRED" /> : <StatusBadge status="AUTO_APPROVED" />}
        </div>
      </div>
      <div className="mt-3 text-xs text-slate-500">Actions: {(workflow.actions ?? []).map((action) => action.actionType).join(', ') || '-'}</div>
      {(workflow.executionLeaseOwner || workflow.executionLeaseExpiresAt) ? (
        <div className="mt-3 rounded-xl border border-blue-100 bg-blue-50 p-3 text-xs text-blue-900">
          <div className="font-bold uppercase tracking-wide text-blue-500">Workflow execution lease</div>
          <div className="mt-1 break-all">owner: {workflow.executionLeaseOwner ?? '-'}</div>
          <div className="mt-1">active: {workflow.executionLeaseActive ? 'yes' : 'no'} · remaining: {workflow.executionLeaseRemainingSeconds ?? 0}s · expires: {workflow.executionLeaseExpiresAt ? formatDateTime(workflow.executionLeaseExpiresAt) : '-'}</div>
        </div>
      ) : null}
      {actionExecutions.length ? (
        <div className="mt-3 rounded-xl border border-slate-100 bg-slate-50 p-3">
          <div className="text-xs font-bold uppercase tracking-wide text-slate-400">Workflow Lease + Action-level Execution State</div>
          <div className="mt-2 grid gap-2 md:grid-cols-2">
            {actionExecutions.map((execution) => (
              <div key={execution.actionExecutionId ?? execution.actionId ?? execution.actionType} className="rounded-lg border border-slate-200 bg-white p-3 text-xs">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-slate-700">{execution.actionType ?? execution.actionId ?? '-'}</span>
                  <StatusBadge status={execution.status ?? 'UNKNOWN'} />
                </div>
                <div className="mt-1 text-slate-500">attempts: {execution.attemptCount ?? 0} · completed: {execution.completedAt ? formatDateTime(execution.completedAt) : '-'}</div>
                {execution.lastError ? <div className="mt-1 text-rose-600">{execution.lastError}</div> : null}
                {execution.idempotencyKey ? <div className="mt-1 break-all text-slate-400">{execution.idempotencyKey}</div> : null}
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {workflow.rollbackSuggestions?.length ? (
        <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-slate-500">
          {workflow.rollbackSuggestions.map((item) => <li key={item}>{item}</li>)}
        </ul>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        <button type="button" disabled={disabled || !canApprove} onClick={() => run('APPROVE_REMEDIATION_WORKFLOW', 'approve')} className="rounded-lg border border-emerald-300 px-3 py-1 text-xs font-bold text-emerald-700 disabled:border-slate-200 disabled:text-slate-400">Approve</button>
        <button type="button" disabled={disabled || !canReject} onClick={() => run('REJECT_REMEDIATION_WORKFLOW', 'reject')} className="rounded-lg border border-rose-300 px-3 py-1 text-xs font-bold text-rose-700 disabled:border-slate-200 disabled:text-slate-400">Reject</button>
        <button type="button" disabled={disabled || !canCancel} onClick={() => run('CANCEL_REMEDIATION_WORKFLOW', 'cancel')} className="rounded-lg border border-slate-300 px-3 py-1 text-xs font-bold text-slate-700 disabled:border-slate-200 disabled:text-slate-400">Cancel</button>
        <button type="button" disabled={disabled || !canExecute} onClick={() => run('EXECUTE_REMEDIATION_WORKFLOW', 'execute')} className="rounded-lg border border-blue-300 px-3 py-1 text-xs font-bold text-blue-700 disabled:border-slate-200 disabled:text-slate-400">Execute with Lease</button>
      </div>
      {workflow.history?.length ? <div className="mt-3"><JsonViewer value={workflow.history} /></div> : null}
    </div>
  );
}


function StaleLeaseQueueCard({ disabled }: { disabled: boolean }) {
  const [staleQueue, setStaleQueue] = useState<CoreAgentRemediationStaleLeaseQueue | null>(null);
  const [recoveredQueue, setRecoveredQueue] = useState<CoreAgentRemediationRecoveredLeaseQueue | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const [stale, recovered] = await Promise.all([
        coreAdminApi.listStaleAgentRemediationWorkflowLeases(50),
        coreAdminApi.listRecoveredAgentRemediationWorkflowLeases(20)
      ]);
      setStaleQueue(stale);
      setRecoveredQueue(recovered);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void reload();
  }, []);

  async function recoverStale() {
    const reason = promptText('請輸入 manual stale lease recovery 的原因，至少 12 個字元。', 'Manual stale workflow execution lease recovery from Admin UI.');
    if (!reason) return;
    setLoading(true);
    setError(null);
    try {
      await coreAdminApi.recoverStaleAgentRemediationWorkflowLeases(100, 'admin-ui', reason);
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  const staleLeases = staleQueue?.staleLeases ?? [];
  const recoveredLeases = recoveredQueue?.recoveredLeases ?? [];
  return (
    <div className="rounded-2xl border border-blue-200 bg-blue-50/50 p-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div>
          <div className="text-sm font-bold text-slate-900">Stale Workflow Lease Queue</div>
          <p className="mt-1 text-xs text-slate-500">顯示已過期但仍掛在 workflow 上的 execution lease；排程 reaper 會主動清理並寫入 EXECUTION_LEASE_STALE_RECOVERED history。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={disabled || loading} onClick={() => void reload()} className="rounded-lg border border-blue-300 bg-white px-3 py-1 text-xs font-bold text-blue-700 disabled:border-slate-200 disabled:text-slate-400">
            {loading ? 'Loading...' : 'Refresh Queue'}
          </button>
          <button type="button" disabled={disabled || loading || staleLeases.length === 0} onClick={() => void recoverStale()} className="rounded-lg border border-violet-300 bg-white px-3 py-1 text-xs font-bold text-violet-700 disabled:border-slate-200 disabled:text-slate-400">
            Recover Stale Leases
          </button>
        </div>
      </div>
      {error ? <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 p-2 text-xs text-rose-700">{error}</div> : null}
      <div className="mt-3 grid gap-3 lg:grid-cols-2">
        <div className="rounded-xl border border-blue-100 bg-white p-3">
          <div className="text-xs font-bold uppercase tracking-wide text-blue-500">Current stale leases ({staleLeases.length})</div>
          {staleLeases.length ? (
            <div className="mt-2 space-y-2">
              {staleLeases.map((lease) => (
                <div key={`${lease.workflowId}-${lease.leaseOwner}`} className="rounded-lg border border-slate-200 p-2 text-xs">
                  <div className="font-bold text-slate-700">{lease.workflowId ?? '-'}</div>
                  <div className="mt-1 text-slate-500">agent: {lease.agentId ?? '-'} · status: {lease.status ?? '-'} · expired: {lease.leaseExpiredSeconds ?? 0}s</div>
                  <div className="mt-1 break-all text-slate-400">owner: {lease.leaseOwner ?? '-'} · expires: {lease.leaseExpiresAt ? formatDateTime(lease.leaseExpiresAt) : '-'}</div>
                </div>
              ))}
            </div>
          ) : <EmptyState title="目前沒有 stale lease" description="沒有需要主動復原的 workflow execution lease。" />}
        </div>
        <div className="rounded-xl border border-blue-100 bg-white p-3">
          <div className="text-xs font-bold uppercase tracking-wide text-blue-500">Recent recovered leases ({recoveredLeases.length})</div>
          {recoveredLeases.length ? (
            <div className="mt-2 space-y-2">
              {recoveredLeases.slice(0, 6).map((item) => (
                <div key={`${item.workflowId}-${item.occurredAt}`} className="rounded-lg border border-slate-200 p-2 text-xs">
                  <div className="font-bold text-slate-700">{item.workflowId ?? '-'}</div>
                  <div className="mt-1 text-slate-500">agent: {item.agentId ?? '-'} · at: {item.occurredAt ? formatDateTime(item.occurredAt) : '-'}</div>
                  <div className="mt-1 text-slate-400">operator: {item.operatorId ?? '-'}</div>
                </div>
              ))}
            </div>
          ) : <EmptyState title="尚無 recovery history" description="排程或手動 recovery 成功後會顯示在這裡。" />}
        </div>
      </div>
    </div>
  );
}

export function AgentRemediationPanel({
  proposal,
  workflows = [],
  commandRunning,
  onCreateProposal,
  onCreateWorkflow,
  onDecideWorkflow,
  onClearRuntimeBackoff,
  onSuspend,
  onSyncApprovedSkills
}: AgentRemediationPanelProps) {
  const actions = proposal?.actions ?? [];
  const skillCodes = skillCodesFromProposal(proposal);
  const canClearBackoff = actions.some((action) => action.actionType === 'CLEAR_RUNTIME_BACKOFF' && action.executable !== false);
  const canSuspend = actions.some((action) => action.actionType === 'SUSPEND_AGENT' && action.executable !== false);
  const canSyncSkills = actions.some((action) => action.actionType === 'SYNC_APPROVED_SKILLS_AND_CAPABILITIES' && action.executable !== false) && skillCodes.length > 0;
  const canCreateWorkflow = actions.some((action) => action.executable !== false);
  const disabled = Boolean(commandRunning);

  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50/40 p-5 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-base font-bold text-slate-900">Agent Remediation Workflow</h2>
          <p className="mt-1 text-sm text-slate-600">把 poison-agent、runtime backoff、skill drift 與 skill version mismatch 轉成 proposal；approved workflow 會先取得 workflow-level execution lease，再透過 action-level idempotent execution 串接 Core governance handlers；stale lease reaper 會主動釋放過期 lease，避免 workflow 卡在執行中。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={disabled}
            onClick={() => {
              const request = promptProposalRequest();
              if (request) onCreateProposal(request);
            }}
            className="rounded-xl border border-amber-300 bg-white px-4 py-2 text-sm font-bold text-amber-800 hover:bg-amber-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
          >
            {commandRunning === 'CREATE_REMEDIATION_PROPOSAL' ? 'Creating...' : 'Create Audited Proposal'}
          </button>
          <button
            type="button"
            disabled={disabled || !canCreateWorkflow}
            onClick={() => {
              const request = promptWorkflowRequest(proposal);
              if (request) onCreateWorkflow(request);
            }}
            className="rounded-xl border border-violet-300 bg-white px-4 py-2 text-sm font-bold text-violet-700 hover:bg-violet-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
          >
            {commandRunning === 'CREATE_REMEDIATION_WORKFLOW' ? 'Creating...' : 'Create Approval Workflow'}
          </button>
          <button type="button" disabled={disabled || !canClearBackoff} onClick={onClearRuntimeBackoff} className="rounded-xl border border-blue-300 bg-white px-4 py-2 text-sm font-bold text-blue-700 hover:bg-blue-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400">
            Clear Backoff
          </button>
          <button type="button" disabled={disabled || !canSyncSkills} onClick={() => onSyncApprovedSkills(skillCodes)} className="rounded-xl border border-emerald-300 bg-white px-4 py-2 text-sm font-bold text-emerald-700 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400">
            {commandRunning === 'SYNC_APPROVED_SKILLS' ? 'Syncing...' : `Sync Skills${skillCodes.length ? ` (${skillCodes.length})` : ''}`}
          </button>
          <button type="button" disabled={disabled || !canSuspend} onClick={onSuspend} className="rounded-xl border border-rose-300 bg-white px-4 py-2 text-sm font-bold text-rose-700 hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400">
            Suspend Agent
          </button>
        </div>
      </div>

      {!proposal ? (
        <div className="mt-4">
          <EmptyState title="尚無 remediation proposal" description="Core 尚未回傳 proposal；可重新整理或建立 audited proposal。" />
        </div>
      ) : (
        <div className="mt-5 space-y-4">
          <div className="grid gap-3 md:grid-cols-4">
            <div className="rounded-xl border border-amber-100 bg-white px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Severity</div>
              <div className="mt-1"><StatusBadge status={proposal.severity ?? 'LOW'} /></div>
            </div>
            <div className="rounded-xl border border-amber-100 bg-white px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Executable</div>
              <div className="mt-1 text-sm font-bold text-slate-800">{proposal.executableActionCount ?? 0}</div>
            </div>
            <div className="rounded-xl border border-amber-100 bg-white px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Workflows</div>
              <div className="mt-1 text-sm font-bold text-slate-800">{workflows.length}</div>
            </div>
            <div className="rounded-xl border border-amber-100 bg-white px-4 py-3">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-400">Generated</div>
              <div className="mt-1 text-xs font-semibold text-slate-700">{proposal.generatedAt ? formatDateTime(proposal.generatedAt) : '-'}</div>
            </div>
          </div>

          {proposal.summary?.length ? (
            <ul className="rounded-2xl border border-amber-100 bg-white p-4 text-sm text-slate-600">
              {proposal.summary.map((item) => <li key={item}>• {item}</li>)}
            </ul>
          ) : null}

          {actions.length === 0 ? (
            <EmptyState title="沒有建議操作" description="目前 proposal 沒有可執行或需審查的 remediation action。" />
          ) : (
            <div className="grid gap-3 xl:grid-cols-2">
              {actions.map((action) => <RemediationActionCard key={action.actionId ?? action.actionType} action={action} />)}
            </div>
          )}

          <StaleLeaseQueueCard disabled={disabled} />

          <div className="rounded-2xl border border-violet-200 bg-violet-50/50 p-4">
            <div className="text-sm font-bold text-slate-900">Approval / Action Execution History</div>
            <p className="mt-1 text-xs text-slate-500">高風險 suspend、disconnect-all、skill publish/enrollment 類 action 會進入 approval-gated workflow；execute 會使用 workflow lease 與 per-action idempotency key。排程會主動清理過期 workflow lease，並產生 stale lease recovery history。</p>
            <div className="mt-3 grid gap-3">
              {workflows.length ? workflows.map((workflow) => (
                <WorkflowCard key={workflow.workflowId ?? `${workflow.status}-${workflow.updatedAt}`} workflow={workflow} disabled={disabled} onDecideWorkflow={onDecideWorkflow} />
              )) : <EmptyState title="尚無 approval workflow" description="按 Create Approval Workflow 建立可審批、可執行、可 rollback review 的 remediation workflow。" />}
            </div>
          </div>

          {proposal.context ? <JsonViewer value={proposal.context} /> : null}
        </div>
      )}
    </section>
  );
}
