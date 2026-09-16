'use client';

import { useState } from 'react';
import type { CommandResult } from '@/lib/types/admin';
import type { CoreAgentCapabilityCommand } from '@/lib/types/core';
import type { AgentDetailBundle } from '@/hooks/useAgentDetail';
import { Panel, activeCapabilityAssignments } from '@/components/agents/AgentDetailUi';
import { AgentQualityObservationPanel, CapabilityRegistryPanel, CapabilityList, QuickCapabilityDialog } from '@/components/agents/AgentCapabilityPanels';

type CommandFn<TBody> = (body: TBody) => Promise<CommandResult>;

export function AgentCapabilitiesWorkspace({
  data,
  agentId,
  tenantId,
  requestAgentCapability,
  approveAgentCapability,
  suspendAgentCapability,
  resumeAgentCapability,
  revokeAgentCapability,
  removeAgentCapability,
  onRefresh,
}: Readonly<{
  data: AgentDetailBundle;
  agentId: string;
  tenantId: string;
  requestAgentCapability: CommandFn<CoreAgentCapabilityCommand>;
  approveAgentCapability: (assignmentId: string, body: CoreAgentCapabilityCommand) => Promise<CommandResult>;
  suspendAgentCapability: (assignmentId: string, body: CoreAgentCapabilityCommand) => Promise<CommandResult>;
  resumeAgentCapability: (assignmentId: string, body: CoreAgentCapabilityCommand) => Promise<CommandResult>;
  revokeAgentCapability: (assignmentId: string, body: CoreAgentCapabilityCommand) => Promise<CommandResult>;
  removeAgentCapability: (assignmentId: string, body: CoreAgentCapabilityCommand) => Promise<CommandResult>;
  onRefresh: () => Promise<void> | void;
}>) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const confirmAction = (message: string, action: () => Promise<unknown>) => {
    if (window.confirm(message)) void action().then(() => onRefresh());
  };
  return (
    <div className="space-y-5">
      <Panel
        title="Capabilities"
        description="Manage the Canonical Capabilities this Agent is approved to provide. Capability is the WHAT contract; Agent Pool membership and Dispatch Access are configured separately."
        action={<button type="button" onClick={() => setDialogOpen(true)} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800">+ Add Capability</button>}
      >
        <CapabilityList
          assignments={data.capabilityAssignments}
          profileCapabilities={data.profile?.capabilities}
          setupReadiness={data.setupReadiness}
          onApprove={(assignment) => confirmAction(`Approve capability ${assignment.capabilityCode}?`, () => approveAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Approved from Agent capability workspace.' }))}
          onSuspend={(assignment) => confirmAction(`Suspend capability ${assignment.capabilityCode}?`, () => suspendAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Suspended from Agent capability workspace.' }))}
          onResume={(assignment) => confirmAction(`Resume capability ${assignment.capabilityCode}?`, () => resumeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Resumed from Agent capability workspace.' }))}
          onRevoke={(assignment) => confirmAction(`Revoke capability ${assignment.capabilityCode}?`, () => revokeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Revoked from Agent capability workspace.' }))}
          onRemove={(assignment) => confirmAction(`Remove capability record ${assignment.capabilityCode}?`, () => removeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Removed from Agent capability workspace.' }))}
          onRevokeAndRemove={(assignment) => {
            if (!window.confirm(`Revoke and remove ${assignment.capabilityCode}?`)) return;
            void (async () => {
              await revokeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Revoked before removal from Agent capability workspace.' });
              await removeAgentCapability(assignment.assignmentId ?? '', { operatorId: 'admin-ui', reason: 'Removed after revoke from Agent capability workspace.' });
              await onRefresh();
            })();
          }}
        />
      </Panel>
      <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-sm font-black text-slate-800">Advanced capability evidence</summary>
        <div className="mt-5 space-y-5">
          <CapabilityRegistryPanel data={data} />
          <AgentQualityObservationPanel data={data} />
        </div>
      </details>
      <QuickCapabilityDialog
        agentId={agentId}
        tenantId={tenantId}
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        existingCodes={activeCapabilityAssignments(data.capabilityAssignments).map((item) => item.capabilityCode)}
        requestAgentCapability={requestAgentCapability}
        onChanged={onRefresh}
      />
    </div>
  );
}

