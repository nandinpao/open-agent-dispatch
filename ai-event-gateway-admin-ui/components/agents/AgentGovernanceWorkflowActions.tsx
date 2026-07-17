'use client';

import { useState } from 'react';
import Link from 'next/link';
import { AgentCredentialIssueDialog } from '@/components/agents/AgentCredentialIssueDialog';
import { AgentEnrollmentReviewDialog } from '@/components/agents/AgentEnrollmentReviewDialog';
import { AgentProfileEditDialog } from '@/components/agents/AgentProfileEditDialog';
import { AgentProfileApprovalDialog } from '@/components/agents/AgentProfileApprovalDialog';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { manualDisconnectResultNotice } from '@/lib/runtime/rejectedConnectionSemantics';
import { canApproveCoreAgentProfile, canApproveEnrollmentForRow, canIssueCredentialForRow, canRevokeCoreAgentProfile, deriveAgentGovernanceState, isBlockedAgentProfile, isCorrectableEnrollmentStatus, isRejectedEnrollmentStatus } from '@/lib/agents/governanceStatus';
import type { AgentDashboardRow } from '@/lib/types/dashboard';
import type { CoreAgentProfile } from '@/lib/types/core';

interface AgentGovernanceWorkflowActionsProps {
  row: AgentDashboardRow;
  onChanged?: () => Promise<void> | void;
}

function actionReason(action: string, row?: AgentDashboardRow): { reason: string; gatewayNodeId?: string } {
  const gatewayNodeId = row?.runtime?.gatewayNodeId ?? row?.runtime?.nodeId;
  return gatewayNodeId
    ? { reason: `${action} from Admin UI Agent Governance actions`, gatewayNodeId }
    : { reason: `${action} from Admin UI Agent Governance actions` };
}

function actionAllRuntimeSessionsReason(action: string, row: AgentDashboardRow): { reason: string; gatewayNodeIds: string[] } {
  const ids = row.runtimeSummary?.gatewayNodeIds ?? [];
  return {
    reason: `${action} from Admin UI Agent Governance actions for all observed runtime sessions`,
    gatewayNodeIds: ids
  };
}

interface ProfileActionButtonProps {
  label: string;
  tone: 'green' | 'amber' | 'rose' | 'slate' | 'blue';
  onClick: () => Promise<CoreAgentProfile | unknown>;
  onChanged?: () => Promise<void> | void;
  successMessage?: string;
}

function ProfileActionButton({ label, tone, onClick, onChanged, successMessage }: Readonly<ProfileActionButtonProps>) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const toneClass = {
    green: 'border-emerald-200 text-emerald-700 hover:bg-emerald-50',
    amber: 'border-amber-200 text-amber-700 hover:bg-amber-50',
    rose: 'border-rose-200 text-rose-700 hover:bg-rose-50',
    slate: 'border-slate-200 text-slate-700 hover:bg-slate-50',
    blue: 'border-blue-200 text-blue-700 hover:bg-blue-50'
  }[tone];

  async function execute() {
    const confirmed = window.confirm(`Confirm ${label}? This action may change Core governance state or disconnect the current Netty runtime session.`);
    if (!confirmed) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await onClick();
      setSuccess(successMessage ?? `${label} completed.`);
      await onChanged?.();
    } catch (err) {
      try {
        await onChanged?.();
      } catch {
        // Keep the original action error visible; refresh errors are handled by the parent view.
      }
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <span className="inline-flex flex-col gap-1">
      <button
        type="button"
        disabled={saving}
        onClick={() => void execute()}
        className={`rounded-lg border px-2 py-1 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${toneClass}`}
      >
        {saving ? `${label}...` : label}
      </button>
      {error ? <span className="max-w-40 text-xs text-rose-600">{error}</span> : null}
      {success ? <span className="max-w-64 text-xs text-emerald-700">{success}</span> : null}
    </span>
  );
}

export function AgentGovernanceWorkflowActions({ row, onChanged }: Readonly<AgentGovernanceWorkflowActionsProps>) {
  const governance = deriveAgentGovernanceState(row);
  const hasProfile = Boolean(row.profile);
  const status = row.enrollment?.status;
  const reviewable = isCorrectableEnrollmentStatus(status);
  const canApproveEnrollment = reviewable && canApproveEnrollmentForRow(row);
  const canEditDraft = !hasProfile && Boolean(row.enrollment || row.runtime);
  const profile = row.profile;
  const profileRisk = profile?.riskStatus?.toUpperCase() ?? 'NORMAL';
  const profileApproval = profile?.approvalStatus?.toUpperCase();
  const runtimeConnected = row.runtime?.connected === true;
  const profileBlocked = isBlockedAgentProfile(profile);
  const canApproveProfile = canApproveCoreAgentProfile(row);
  const canEnable = Boolean(profile && !profile.enabled && profileApproval === 'APPROVED' && !profileBlocked);
  const canIssueCredential = canIssueCredentialForRow(row);
  const canDisable = Boolean(profile?.enabled);
  const canSuspend = Boolean(profile && profileApproval !== 'SUSPENDED' && profileRisk !== 'SUSPENDED' && profileRisk !== 'COMPROMISED');
  const canRevoke = canRevokeCoreAgentProfile(row);

  return (
    <div className="flex flex-wrap gap-2">
      <Link href={`/agents/${encodeURIComponent(row.agentId)}`} className="rounded-lg border border-blue-200 px-2 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50">
        Detail
      </Link>

      {profile ? <AgentProfileEditDialog profile={profile} triggerLabel="Edit" onSaved={onChanged} /> : null}

      {canEditDraft ? <AgentEnrollmentReviewDialog row={row} triggerLabel="Edit" intent="edit" onChanged={onChanged} /> : null}

      {reviewable ? (
        <>
          {canApproveEnrollment ? (
            <AgentEnrollmentReviewDialog row={row} triggerLabel={isRejectedEnrollmentStatus(status) ? "Approve Again" : "Approve"} intent="approve" onChanged={onChanged} />
          ) : (
            <span className="rounded-lg border border-amber-200 px-2 py-1 text-xs font-semibold text-amber-700" title="Enrollment approval cannot restore a blocked Core Agent profile. Use Restore Approve with new credential material.">
              Restore required
            </span>
          )}
          {!isRejectedEnrollmentStatus(status) ? (
            <AgentEnrollmentReviewDialog row={row} triggerLabel="Reject" intent="reject" onChanged={onChanged} />
          ) : null}
        </>
      ) : null}

      {canApproveProfile && profile ? (
        <AgentProfileApprovalDialog profile={profile} triggerLabel={profileApproval === 'REVOKED' ? "Restore Approve" : "Approve"} onSaved={onChanged} />
      ) : null}


      {profile && canIssueCredential && governance.credentialStatus !== 'CREDENTIAL_ACTIVE' ? (
        <AgentCredentialIssueDialog profile={profile} triggerLabel="Issue Credential" onSaved={onChanged} />
      ) : null}

      {profile && canIssueCredential && governance.credentialStatus === 'CREDENTIAL_ACTIVE' ? (
        <AgentCredentialIssueDialog profile={profile} triggerLabel="Rotate Credential" onSaved={onChanged} />
      ) : null}

      {profile && !canIssueCredential && governance.credentialStatus !== 'CREDENTIAL_ACTIVE' ? (
        <span className="rounded-lg border border-amber-200 px-2 py-1 text-xs font-semibold text-amber-700" title="Credential issuance is restricted to APPROVED Agent profiles with NORMAL risk. Use Restore Approve with new credential material for blocked or non-approved profiles.">
          Restore credential required
        </span>
      ) : null}

      {canEnable && profile ? (
        <ProfileActionButton
          label="Enable"
          tone="green"
          onChanged={onChanged}
          onClick={() => coreAdminApi.enableAgent(profile.agentId, actionReason('Enable', row))}
        />
      ) : null}

      {canDisable && profile ? (
        <ProfileActionButton
          label="Disable"
          tone="amber"
          onChanged={onChanged}
          onClick={() => coreAdminApi.disableAgent(profile.agentId, actionReason('Disable', row))}
        />
      ) : null}

      {canSuspend && profile ? (
        <ProfileActionButton
          label="Suspend"
          tone="amber"
          onChanged={onChanged}
          onClick={() => coreAdminApi.suspendAgent(profile.agentId, actionReason('Suspend', row))}
        />
      ) : null}

      {canRevoke && profile ? (
        <ProfileActionButton
          label="Revoke"
          tone="rose"
          onChanged={onChanged}
          onClick={() => coreAdminApi.revokeAgent(profile.agentId, actionReason('Revoke', row))}
        />
      ) : null}



      {runtimeConnected ? (
        <ProfileActionButton
          label="Disconnect"
          tone="blue"
          onChanged={onChanged}
          successMessage={manualDisconnectResultNotice(row.agentId, false)}
          onClick={() => coreAdminApi.disconnectAgent(row.agentId, actionReason('Disconnect', row))}
        />
      ) : null}

      {row.runtimeSummary?.duplicateRuntimeDetected ? (
        <ProfileActionButton
          label="Disconnect All"
          tone="rose"
          onChanged={onChanged}
          successMessage={manualDisconnectResultNotice(row.agentId, true)}
          onClick={() => coreAdminApi.disconnectAllAgentSessions(row.agentId, actionAllRuntimeSessionsReason('Disconnect All', row))}
        />
      ) : null}

      {governance.isRuntimeOnlyUngoverned && !reviewable ? (
        <span className="rounded-lg border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-700" title={governance.readinessReason}>
          Ungoverned
        </span>
      ) : null}
    </div>
  );
}
