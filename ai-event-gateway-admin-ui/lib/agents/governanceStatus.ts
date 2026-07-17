import type { CoreAgentCredentialSummary } from '@/lib/types/core';
import type { AgentDashboardRow } from '@/lib/types/dashboard';

export type AgentGovernanceSourceStatus = 'CORE_NETTY' | 'CORE_ONLY' | 'RUNTIME_ONLY_UNGOVERNED';

export type AgentProfileGovernanceStatus =
  | 'NO_CORE_PROFILE'
  | 'PROFILE_REGISTERED'
  | 'PROFILE_PENDING_REVIEW'
  | 'PROFILE_APPROVED'
  | 'PROFILE_REJECTED'
  | 'PROFILE_SUSPENDED'
  | 'PROFILE_REVOKED'
  | 'PROFILE_UNKNOWN';

export type AgentEnrollmentGovernanceStatus =
  | 'NO_ENROLLMENT'
  | 'ENROLLMENT_SUBMITTED'
  | 'ENROLLMENT_REGISTERED'
  | 'ENROLLMENT_PENDING_REVIEW'
  | 'ENROLLMENT_APPROVED'
  | 'ENROLLMENT_REJECTED'
  | 'ENROLLMENT_CANCELLED'
  | 'RUNTIME_OBSERVED'
  | 'ENROLLMENT_UNKNOWN';

export type AgentCredentialGovernanceStatus =
  | 'NO_PROFILE'
  | 'CREDENTIAL_MISSING'
  | 'CREDENTIAL_ACTIVE'
  | 'CREDENTIAL_EXPIRED'
  | 'CREDENTIAL_REVOKED'
  | 'CREDENTIAL_UNKNOWN';

export type AgentRuntimeAuthorizationStatus =
  | 'OFFLINE'
  | 'AUTH_UNKNOWN'
  | 'AUTHORIZED'
  | 'AUTH_DENIED'
  | 'AUTH_REVOKED'
  | 'AUTH_DISCONNECTED_BY_POLICY'
  | 'AUTH_UNVERIFIED'
  | 'AUTH_OTHER';

export type AgentReadinessStatus =
  | 'READY'
  | 'NO_CORE_PROFILE'
  | 'PROFILE_NOT_APPROVED'
  | 'PROFILE_DISABLED'
  | 'RISK_BLOCKED'
  | 'CREDENTIAL_MISSING'
  | 'CREDENTIAL_NOT_ACTIVE'
  | 'RUNTIME_OFFLINE'
  | 'AUTH_UNKNOWN'
  | 'AUTH_DENIED';

export interface AgentGovernanceState {
  sourceStatus: AgentGovernanceSourceStatus;
  profileStatus: AgentProfileGovernanceStatus;
  enrollmentStatus: AgentEnrollmentGovernanceStatus;
  enabledStatus: 'ENABLED' | 'DISABLED' | 'NO_PROFILE';
  riskStatus: string;
  credentialStatus: AgentCredentialGovernanceStatus;
  runtimeAuthorizationStatus: AgentRuntimeAuthorizationStatus;
  readinessStatus: AgentReadinessStatus;
  readinessLabel: string;
  readinessReason: string;
  isReady: boolean;
  requiresReview: boolean;
  isRuntimeOnlyUngoverned: boolean;
  isCoreGoverned: boolean;
  isCoreAssignableCandidate: boolean;
}

export function normalizeGovernanceStatusValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim().toUpperCase() : undefined;
}

function upper(value: unknown): string | undefined {
  return normalizeGovernanceStatusValue(value);
}

export function normalizeEnrollmentStatus(status: unknown): string | undefined {
  const normalized = upper(status);
  if (!normalized) return undefined;
  return normalized.startsWith('ENROLLMENT_') ? normalized.slice('ENROLLMENT_'.length) : normalized;
}

export function isOpenEnrollmentStatus(status: unknown): boolean {
  const normalized = normalizeEnrollmentStatus(status);
  return normalized === 'SUBMITTED' || normalized === 'REGISTERED' || normalized === 'PENDING_REVIEW' || normalized === 'RUNTIME_OBSERVED';
}

export function isCorrectableEnrollmentStatus(status: unknown): boolean {
  const normalized = normalizeEnrollmentStatus(status);
  return isOpenEnrollmentStatus(normalized) || normalized === 'REJECTED';
}

export function isRejectedEnrollmentStatus(status: unknown): boolean {
  return normalizeEnrollmentStatus(status) === 'REJECTED';
}


export function isBlockedAgentProfile(profile: AgentDashboardRow['profile']): boolean {
  if (!profile) return false;
  const approvalStatus = upper(profile.approvalStatus);
  if (approvalStatus === 'REVOKED' || approvalStatus === 'SUSPENDED') return true;
  const riskStatus = upper(profile.riskStatus);
  return riskStatus === 'REVOKED' || riskStatus === 'SUSPENDED' || riskStatus === 'QUARANTINED' || riskStatus === 'COMPROMISED';
}

export function canApproveEnrollmentForRow(row: AgentDashboardRow): boolean {
  return !isBlockedAgentProfile(row.profile);
}

export function canIssueCredentialForRow(row: AgentDashboardRow): boolean {
  const profile = row.profile;
  if (!profile) return false;
  return upper(profile.approvalStatus) === 'APPROVED' && !isBlockedAgentProfile(profile);
}

export function deriveSourceStatus(row: AgentDashboardRow): AgentGovernanceSourceStatus {
  if (row.source.profile === 'CORE' && row.source.runtime === 'NETTY') return 'CORE_NETTY';
  if (row.source.profile === 'CORE') return 'CORE_ONLY';
  return 'RUNTIME_ONLY_UNGOVERNED';
}

export function deriveProfileGovernanceStatus(row: AgentDashboardRow): AgentProfileGovernanceStatus {
  const status = upper(row.profile?.approvalStatus);
  if (!row.profile) return 'NO_CORE_PROFILE';
  if (status === 'REGISTERED') return 'PROFILE_REGISTERED';
  if (status === 'PENDING_REVIEW') return 'PROFILE_PENDING_REVIEW';
  if (status === 'APPROVED') return 'PROFILE_APPROVED';
  if (status === 'REJECTED') return 'PROFILE_REJECTED';
  if (status === 'SUSPENDED') return 'PROFILE_SUSPENDED';
  if (status === 'REVOKED') return 'PROFILE_REVOKED';
  return 'PROFILE_UNKNOWN';
}

export function deriveEnrollmentGovernanceStatus(row: AgentDashboardRow): AgentEnrollmentGovernanceStatus {
  const status = normalizeEnrollmentStatus(row.enrollment?.status);
  if (!row.enrollment) return 'NO_ENROLLMENT';
  if (status === 'SUBMITTED') return 'ENROLLMENT_SUBMITTED';
  if (status === 'REGISTERED') return 'ENROLLMENT_REGISTERED';
  if (status === 'PENDING_REVIEW') return 'ENROLLMENT_PENDING_REVIEW';
  if (status === 'APPROVED') return 'ENROLLMENT_APPROVED';
  if (status === 'REJECTED') return 'ENROLLMENT_REJECTED';
  if (status === 'CANCELLED') return 'ENROLLMENT_CANCELLED';
  if (status === 'RUNTIME_OBSERVED') return 'RUNTIME_OBSERVED';
  return 'ENROLLMENT_UNKNOWN';
}

function hasCredentialMaterial(credential?: CoreAgentCredentialSummary): boolean {
  return Boolean(
    credential?.credentialId ||
      credential?.credentialVersion ||
      normalizeGovernanceStatusValue(credential?.credentialStatus) === 'ACTIVE'
  );
}

export function deriveCredentialGovernanceStatus(row: AgentDashboardRow): AgentCredentialGovernanceStatus {
  if (!row.profile) return 'NO_PROFILE';
  const credential = row.profile.credential;
  if (!credential || !hasCredentialMaterial(credential)) return 'CREDENTIAL_MISSING';
  const status = upper(credential.credentialStatus);
  if (status === 'ACTIVE') return 'CREDENTIAL_ACTIVE';
  if (status === 'EXPIRED') return 'CREDENTIAL_EXPIRED';
  if (status === 'REVOKED') return 'CREDENTIAL_REVOKED';
  if (status === 'MISSING') return 'CREDENTIAL_MISSING';
  return 'CREDENTIAL_UNKNOWN';
}

export function deriveRuntimeAuthorizationStatus(row: AgentDashboardRow): AgentRuntimeAuthorizationStatus {
  if (row.runtime?.connected !== true) return 'OFFLINE';
  const state = upper(row.runtime?.authorizationState);
  if (!state) return 'AUTH_UNKNOWN';
  if (state === 'AUTHORIZED') return 'AUTHORIZED';
  if (state === 'DENIED') return 'AUTH_DENIED';
  if (state === 'REVOKED') return 'AUTH_REVOKED';
  if (state === 'UNVERIFIED') return 'AUTH_UNVERIFIED';
  if (state === 'DISCONNECTED_BY_POLICY') return 'AUTH_DISCONNECTED_BY_POLICY';
  return 'AUTH_OTHER';
}

export function deriveRiskGovernanceStatus(row: AgentDashboardRow): string {
  if (!row.profile) return 'NO_PROFILE';
  return upper(row.profile.riskStatus) ?? 'UNKNOWN';
}

export function deriveEnabledGovernanceStatus(row: AgentDashboardRow): 'ENABLED' | 'DISABLED' | 'NO_PROFILE' {
  if (!row.profile) {
    return isRejectedEnrollmentStatus(row.enrollment?.status) ? 'DISABLED' : 'NO_PROFILE';
  }
  if (upper(row.profile.approvalStatus) !== 'APPROVED') return 'DISABLED';
  return row.profile.enabled === true ? 'ENABLED' : 'DISABLED';
}

export function deriveReadinessStatus(row: AgentDashboardRow): AgentReadinessStatus {
  if (!row.profile) return 'NO_CORE_PROFILE';
  if (upper(row.profile.approvalStatus) !== 'APPROVED') return 'PROFILE_NOT_APPROVED';
  if (row.profile.enabled !== true) return 'PROFILE_DISABLED';
  if (deriveRiskGovernanceStatus(row) !== 'NORMAL') return 'RISK_BLOCKED';

  const credentialStatus = deriveCredentialGovernanceStatus(row);
  if (credentialStatus === 'CREDENTIAL_MISSING') return 'CREDENTIAL_MISSING';
  if (credentialStatus !== 'CREDENTIAL_ACTIVE') return 'CREDENTIAL_NOT_ACTIVE';

  const runtimeAuthorizationStatus = deriveRuntimeAuthorizationStatus(row);
  if (runtimeAuthorizationStatus === 'OFFLINE') return 'RUNTIME_OFFLINE';
  if (runtimeAuthorizationStatus === 'AUTH_UNKNOWN') return 'AUTH_UNKNOWN';
  if (runtimeAuthorizationStatus !== 'AUTHORIZED') return 'AUTH_DENIED';

  return 'READY';
}

export function readinessReason(status: AgentReadinessStatus): string {
  const reasons: Record<AgentReadinessStatus, string> = {
    READY: 'Agent is approved, enabled, normal-risk, has an active credential, is connected, and Netty reports AUTHORIZED.',
    NO_CORE_PROFILE: 'Core has no Agent profile. Runtime-only observations are ungoverned and must not be assigned work.',
    PROFILE_NOT_APPROVED: 'Core Agent profile is not APPROVED. Enrollment review and approval are required first.',
    PROFILE_DISABLED: 'Core Agent profile is disabled or the approval status is not connectable. Disabled / rejected / revoked Agents must not be treated as assignable and should be disconnected if a runtime session is still online.',
    RISK_BLOCKED: 'Core risk status is not NORMAL. Quarantine, suspension, revocation, or compromise blocks readiness.',
    CREDENTIAL_MISSING: 'Core Agent profile has no active credential material. Approval without credential is not connectable.',
    CREDENTIAL_NOT_ACTIVE: 'Core Agent credential exists but is not ACTIVE. Rotate or re-issue credentials before assignment.',
    RUNTIME_OFFLINE: 'No connected Netty runtime session is available for this Agent.',
    AUTH_UNKNOWN: 'Netty runtime is connected, but the Core authorization state is missing. Missing auth must not be treated as authorized.',
    AUTH_DENIED: 'Netty runtime authorization state is not AUTHORIZED. The session is denied, revoked, unverified, or disconnected by policy.'
  };
  return reasons[status];
}

export function readinessLabel(status: AgentReadinessStatus): string {
  const labels: Record<AgentReadinessStatus, string> = {
    READY: 'Ready',
    NO_CORE_PROFILE: 'No Core profile',
    PROFILE_NOT_APPROVED: 'Profile not approved',
    PROFILE_DISABLED: 'Profile disabled',
    RISK_BLOCKED: 'Risk blocked',
    CREDENTIAL_MISSING: 'Credential missing',
    CREDENTIAL_NOT_ACTIVE: 'Credential not active',
    RUNTIME_OFFLINE: 'Runtime offline',
    AUTH_UNKNOWN: 'Auth unknown',
    AUTH_DENIED: 'Auth denied'
  };
  return labels[status];
}

export function profileStatusLabel(status: AgentProfileGovernanceStatus): string {
  const labels: Record<AgentProfileGovernanceStatus, string> = {
    NO_CORE_PROFILE: 'No Core profile',
    PROFILE_REGISTERED: 'Profile registered',
    PROFILE_PENDING_REVIEW: 'Profile pending review',
    PROFILE_APPROVED: 'Profile approved',
    PROFILE_REJECTED: 'Profile rejected',
    PROFILE_SUSPENDED: 'Profile suspended',
    PROFILE_REVOKED: 'Profile revoked',
    PROFILE_UNKNOWN: 'Profile unknown'
  };
  return labels[status];
}

export function enrollmentStatusLabel(status: AgentEnrollmentGovernanceStatus): string {
  const labels: Record<AgentEnrollmentGovernanceStatus, string> = {
    NO_ENROLLMENT: 'No enrollment',
    ENROLLMENT_SUBMITTED: 'Enrollment submitted',
    ENROLLMENT_REGISTERED: 'Enrollment registered',
    ENROLLMENT_PENDING_REVIEW: 'Enrollment pending review',
    ENROLLMENT_APPROVED: 'Enrollment approved',
    ENROLLMENT_REJECTED: 'Enrollment rejected',
    ENROLLMENT_CANCELLED: 'Enrollment cancelled',
    RUNTIME_OBSERVED: 'Runtime observed',
    ENROLLMENT_UNKNOWN: 'Enrollment unknown'
  };
  return labels[status];
}

export function credentialStatusLabel(status: AgentCredentialGovernanceStatus): string {
  const labels: Record<AgentCredentialGovernanceStatus, string> = {
    NO_PROFILE: 'No profile',
    CREDENTIAL_MISSING: 'Credential missing',
    CREDENTIAL_ACTIVE: 'Credential active',
    CREDENTIAL_EXPIRED: 'Credential expired',
    CREDENTIAL_REVOKED: 'Credential revoked',
    CREDENTIAL_UNKNOWN: 'Credential unknown'
  };
  return labels[status];
}

export function runtimeAuthorizationLabel(status: AgentRuntimeAuthorizationStatus): string {
  const labels: Record<AgentRuntimeAuthorizationStatus, string> = {
    OFFLINE: 'Offline',
    AUTH_UNKNOWN: 'Auth unknown',
    AUTHORIZED: 'Authorized',
    AUTH_DENIED: 'Auth denied',
    AUTH_REVOKED: 'Auth revoked',
    AUTH_DISCONNECTED_BY_POLICY: 'Disconnected by policy',
    AUTH_UNVERIFIED: 'Auth unverified',
    AUTH_OTHER: 'Auth other'
  };
  return labels[status];
}

export function deriveAgentGovernanceState(row: AgentDashboardRow): AgentGovernanceState {
  const sourceStatus = deriveSourceStatus(row);
  const profileStatus = deriveProfileGovernanceStatus(row);
  const enrollmentStatus = deriveEnrollmentGovernanceStatus(row);
  const credentialStatus = deriveCredentialGovernanceStatus(row);
  const runtimeAuthorizationStatus = deriveRuntimeAuthorizationStatus(row);
  const readinessStatus = deriveReadinessStatus(row);
  const riskStatus = deriveRiskGovernanceStatus(row);
  const enabledStatus = deriveEnabledGovernanceStatus(row);
  const isReady = readinessStatus === 'READY';

  return {
    sourceStatus,
    profileStatus,
    enrollmentStatus,
    enabledStatus,
    riskStatus,
    credentialStatus,
    runtimeAuthorizationStatus,
    readinessStatus,
    readinessLabel: readinessLabel(readinessStatus),
    readinessReason: readinessReason(readinessStatus),
    isReady,
    requiresReview: enrollmentStatus === 'ENROLLMENT_SUBMITTED' || enrollmentStatus === 'ENROLLMENT_REGISTERED' || enrollmentStatus === 'ENROLLMENT_PENDING_REVIEW' || enrollmentStatus === 'RUNTIME_OBSERVED' || enrollmentStatus === 'ENROLLMENT_REJECTED',
    isRuntimeOnlyUngoverned: sourceStatus === 'RUNTIME_ONLY_UNGOVERNED',
    isCoreGoverned: sourceStatus === 'CORE_NETTY' || sourceStatus === 'CORE_ONLY',
    isCoreAssignableCandidate:
      profileStatus === 'PROFILE_APPROVED' &&
      enabledStatus === 'ENABLED' &&
      riskStatus === 'NORMAL' &&
      credentialStatus === 'CREDENTIAL_ACTIVE'
  };
}

export function canApproveCoreAgentProfile(row: AgentDashboardRow): boolean {
  const profile = row.profile;
  if (!profile) return false;
  const approval = upper(profile.approvalStatus);
  const risk = upper(profile.riskStatus) ?? 'NORMAL';
  return approval !== 'APPROVED' || risk !== 'NORMAL';
}

export function canRevokeCoreAgentProfile(row: AgentDashboardRow): boolean {
  const profile = row.profile;
  if (!profile) return false;
  return upper(profile.approvalStatus) !== 'REVOKED';
}
