export type IssueSyncQueueStatus =
  | 'PENDING'
  | 'RETRYING'
  | 'WAITING_FOR_INDEX'
  | 'FAILED'
  | 'DEAD_LETTER'
  | 'CONFLICT';

export type IssueConflictResolution =
  | 'OPENDISPATCH_WINS'
  | 'EXTERNAL_DISPLAY_ONLY'
  | 'MERGE_COMMENTS'
  | 'CREATE_REPLACEMENT'
  | 'MANUAL_REVIEW'
  | 'IGNORE';

export type AgentBlockingReason =
  | 'MISSING_PROFILE'
  | 'NOT_APPROVED'
  | 'DISABLED'
  | 'QUARANTINED'
  | 'CREDENTIAL_MISSING'
  | 'CREDENTIAL_EXPIRED'
  | 'RUNTIME_OFFLINE'
  | 'NO_RUNTIME_BINDING'
  | 'NO_SERVICE_SCOPE'
  | 'NO_DISPATCH_FLOW'
  | 'NO_AVAILABLE_SLOTS'
  | 'RUNTIME_DRAINING'
  | 'READY';

export interface AgentBlockingInput {
  hasProfile: boolean;
  approvalStatus?: string | null;
  enabled?: boolean;
  riskStatus?: string | null;
  credentialStatus?: string | null;
  credentialExpiresAt?: string | null;
  runtimeConnected?: boolean;
  runtimeBindingActive?: boolean;
  dispatchAccessRuleCount?: number;
  /** Historical compatibility input. Standard UI uses dispatchAccessRuleCount. */
  serviceScopeCount?: number;
  activeDispatchFlowCount?: number;
  availableSlots?: number | null;
  draining?: boolean;
  now?: Date;
}

export interface AgentBlockingExperience {
  reason: AgentBlockingReason;
  title: string;
  explanation: string;
  safestNextAction: string;
  canSelfRepair: boolean;
  severity: 'READY' | 'WARNING' | 'BLOCKED';
  repairTarget?: 'PROFILE' | 'CREDENTIAL' | 'RUNTIME' | 'BINDING' | 'DISPATCH_ACCESS' | 'SERVICE_SCOPE' | 'DISPATCH_FLOW' | 'CAPACITY';
}

const BLOCKERS: Record<Exclude<AgentBlockingReason, 'READY'>, Omit<AgentBlockingExperience, 'reason'>> = {
  MISSING_PROFILE: { title: 'Agent profile is missing', explanation: 'The runtime identity is not backed by an approved Core Agent profile.', safestNextAction: 'Create and approve the Agent profile before allowing runtime use.', canSelfRepair: true, severity: 'BLOCKED', repairTarget: 'PROFILE' },
  NOT_APPROVED: { title: 'Agent approval is incomplete', explanation: 'The Agent profile has not reached the approved lifecycle state.', safestNextAction: 'Complete the governed Agent approval flow.', canSelfRepair: false, severity: 'BLOCKED', repairTarget: 'PROFILE' },
  DISABLED: { title: 'Agent is disabled', explanation: 'Core has disabled the Agent profile, so new dispatch assignments are blocked.', safestNextAction: 'Review the disable reason and use the governed enable action when permitted.', canSelfRepair: false, severity: 'BLOCKED', repairTarget: 'PROFILE' },
  QUARANTINED: { title: 'Agent is quarantined', explanation: 'A security state prevents the Agent from receiving work.', safestNextAction: 'Review the security evidence and complete the approved remediation workflow.', canSelfRepair: false, severity: 'BLOCKED', repairTarget: 'PROFILE' },
  CREDENTIAL_MISSING: { title: 'Agent credential is missing', explanation: 'The runtime cannot authenticate without an active credential reference.', safestNextAction: 'Issue or rotate an approved credential; secret material must remain outside the UI.', canSelfRepair: true, severity: 'BLOCKED', repairTarget: 'CREDENTIAL' },
  CREDENTIAL_EXPIRED: { title: 'Agent credential is expired or inactive', explanation: 'The credential metadata indicates that the runtime credential is no longer usable.', safestNextAction: 'Preview the rotation impact, rotate the credential, and reconnect the runtime.', canSelfRepair: true, severity: 'BLOCKED', repairTarget: 'CREDENTIAL' },
  RUNTIME_OFFLINE: { title: 'Agent runtime is offline', explanation: 'No current Gateway runtime session is available for dispatch.', safestNextAction: 'Start or reconnect the runtime, then refresh readiness.', canSelfRepair: true, severity: 'BLOCKED', repairTarget: 'RUNTIME' },
  NO_RUNTIME_BINDING: { title: 'Runtime binding is not active', explanation: 'An online session is telemetry only; Core requires an active Agent-to-runtime binding for dispatch authority.', safestNextAction: 'Create or activate the runtime binding and re-evaluate readiness.', canSelfRepair: true, severity: 'BLOCKED', repairTarget: 'BINDING' },
  NO_SERVICE_SCOPE: { title: 'Dispatch access is not configured', explanation: 'The Agent can be connected and authenticated while assigned Task execution is still denied. No approved Source System + Task Type access rule is available.', safestNextAction: 'Configure the narrowest required Dispatch Access, then refresh Agent readiness. Routing and Capability selection remain separate.', canSelfRepair: true, severity: 'BLOCKED', repairTarget: 'DISPATCH_ACCESS' },
  NO_DISPATCH_FLOW: { title: 'Agent is not used by an active Dispatch Flow', explanation: 'The Agent may be healthy, but no active Flow currently selects it as a candidate.', safestNextAction: 'Add the Agent to an approved Agent Pool used by an active Dispatch Flow.', canSelfRepair: true, severity: 'WARNING', repairTarget: 'DISPATCH_FLOW' },
  NO_AVAILABLE_SLOTS: { title: 'Agent has no available capacity', explanation: 'The runtime reports no remaining execution slots.', safestNextAction: 'Wait for work to finish, increase approved capacity, or move work to another eligible Agent.', canSelfRepair: false, severity: 'WARNING', repairTarget: 'CAPACITY' },
  RUNTIME_DRAINING: { title: 'Agent runtime is draining', explanation: 'The runtime is intentionally refusing new assignments while current work completes.', safestNextAction: 'Wait for drain completion or cancel the drain through the governed runtime workflow.', canSelfRepair: false, severity: 'WARNING', repairTarget: 'RUNTIME' },
};

function upper(value?: string | null): string { return String(value ?? '').trim().toUpperCase(); }

export function deriveAgentBlockingExperience(input: AgentBlockingInput): AgentBlockingExperience {
  const now = input.now ?? new Date();
  let reason: AgentBlockingReason = 'READY';
  if (!input.hasProfile) reason = 'MISSING_PROFILE';
  else if (upper(input.approvalStatus) !== 'APPROVED') reason = 'NOT_APPROVED';
  else if (input.enabled === false) reason = 'DISABLED';
  else if (upper(input.riskStatus) && upper(input.riskStatus) !== 'NORMAL') reason = 'QUARANTINED';
  else if (!upper(input.credentialStatus) || ['MISSING', 'NONE', 'NOT_CONFIGURED'].includes(upper(input.credentialStatus))) reason = 'CREDENTIAL_MISSING';
  else if (upper(input.credentialStatus) !== 'ACTIVE' || (input.credentialExpiresAt && Date.parse(input.credentialExpiresAt) <= now.getTime())) reason = 'CREDENTIAL_EXPIRED';
  else if (!input.runtimeConnected) reason = 'RUNTIME_OFFLINE';
  else if (input.runtimeBindingActive === false) reason = 'NO_RUNTIME_BINDING';
  else if ((input.dispatchAccessRuleCount ?? input.serviceScopeCount ?? 0) <= 0) reason = 'NO_SERVICE_SCOPE';
  else if (input.draining) reason = 'RUNTIME_DRAINING';
  else if (input.availableSlots != null && input.availableSlots <= 0) reason = 'NO_AVAILABLE_SLOTS';
  else if ((input.activeDispatchFlowCount ?? 0) <= 0) reason = 'NO_DISPATCH_FLOW';

  if (reason === 'READY') return {
    reason,
    title: 'Agent is dispatch ready',
    explanation: 'Profile, credential, runtime, binding, dispatch access, capacity, and Dispatch Flow evidence are currently usable.',
    safestNextAction: 'Monitor runtime health and Task delivery evidence.',
    canSelfRepair: false,
    severity: 'READY',
  };
  return { reason, ...BLOCKERS[reason] };
}

export interface CredentialMetadataInput {
  status?: string | null;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  rotatedAt?: string | null;
  mappingCount?: number;
  principalCount?: number;
  now?: Date;
}

export function credentialRotationImpact(input: CredentialMetadataInput) {
  const now = input.now ?? new Date();
  const expires = input.expiresAt ? Date.parse(input.expiresAt) : Number.NaN;
  const daysRemaining = Number.isFinite(expires) ? Math.ceil((expires - now.getTime()) / 86_400_000) : null;
  const status = upper(input.status) || 'UNKNOWN';
  const urgency = status !== 'ACTIVE' || (daysRemaining != null && daysRemaining <= 0) ? 'CRITICAL'
    : daysRemaining != null && daysRemaining <= 14 ? 'HIGH'
    : daysRemaining != null && daysRemaining <= 30 ? 'MODERATE' : 'LOW';
  return {
    urgency,
    daysRemaining,
    blastRadius: Math.max(0, input.mappingCount ?? 0) + Math.max(0, input.principalCount ?? 0),
    reconnectRequired: urgency === 'CRITICAL' || urgency === 'HIGH',
    safestNextAction: urgency === 'LOW'
      ? 'Keep the credential under scheduled rotation and verify the latest permission probe.'
      : 'Preview affected mappings, rotate the secret reference through the approved vault workflow, then re-run the permission probe.',
  } as const;
}

export function syncQueueAction(status: IssueSyncQueueStatus): { action: string; safeReason: string } {
  const map: Record<IssueSyncQueueStatus, { action: string; safeReason: string }> = {
    PENDING: { action: 'Wait or inspect lane ordering', safeReason: 'The projection is queued and has not exceeded its retry budget.' },
    RETRYING: { action: 'Review retry evidence', safeReason: 'An idempotent retry is already in progress.' },
    WAITING_FOR_INDEX: { action: 'Wait for provider visibility', safeReason: 'The provider accepted the write but its read index has not caught up.' },
    FAILED: { action: 'Review provider error', safeReason: 'The current attempt failed but may still be recoverable.' },
    DEAD_LETTER: { action: 'Open manual recovery', safeReason: 'Automatic retry stopped after the governed retry budget was exhausted.' },
    CONFLICT: { action: 'Open conflict review', safeReason: 'OpenDispatch desired state differs from the external observed state.' },
  };
  return map[status];
}

export function allowedConflictResolutions(): ReadonlyArray<{ code: IssueConflictResolution; label: string; authorityEffect: string }> {
  return [
    { code: 'OPENDISPATCH_WINS', label: 'OpenDispatch wins', authorityEffect: 'Re-project canonical OpenDispatch state without transferring Task authority.' },
    { code: 'EXTERNAL_DISPLAY_ONLY', label: 'External display only', authorityEffect: 'Keep external state as display evidence only.' },
    { code: 'MERGE_COMMENTS', label: 'Merge comments', authorityEffect: 'Import approved comments without accepting external lifecycle authority.' },
    { code: 'CREATE_REPLACEMENT', label: 'Create replacement', authorityEffect: 'Create a governed replacement projection and preserve the original audit chain.' },
    { code: 'MANUAL_REVIEW', label: 'Manual review', authorityEffect: 'Pause automation and require an authorized human decision.' },
    { code: 'IGNORE', label: 'Ignore observation', authorityEffect: 'Record the external observation without changing OpenDispatch state.' },
  ];
}
