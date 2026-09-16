export type PermissionRiskLane = 'READ' | 'WRITE' | 'EXPORT' | 'ADMIN' | 'CRITICAL';
export type GrantDurationPreset = 'EIGHT_HOURS' | 'ONE_DAY' | 'SEVEN_DAYS' | 'THIRTY_DAYS';
export type ScopeGrantValidationStatus = 'READY' | 'WARNING' | 'BLOCKED';
export type AccessReviewDecision = 'KEEP' | 'REDUCE' | 'REVOKE' | 'REQUEST_MORE_INFORMATION';
export type OwnershipTransferRisk = 'CONTROLLED' | 'HIGH_RISK' | 'BLOCKED';

export interface PermissionBundleItem {
  permissionCode: string;
  displayName?: string;
  description?: string;
  riskLane?: PermissionRiskLane | string;
  allowedScopeTypes?: string[];
  systemManaged?: boolean;
}

export interface PermissionBundleSummary {
  total: number;
  read: number;
  write: number;
  export: number;
  admin: number;
  critical: number;
  systemManaged: number;
  requiresIndependentApproval: boolean;
}

const inferredRisk = (code: string): PermissionRiskLane => {
  const value = code.toLowerCase();
  if (/(break.glass|root|deny|revoke|transfer|rotate|publish|activate|rollback)/.test(value)) return 'CRITICAL';
  if (/(manage|approve|grant|suspend|admin)/.test(value)) return 'ADMIN';
  if (/(export|download)/.test(value)) return 'EXPORT';
  if (/(create|update|write|comment|execute|retry|cancel|assign)/.test(value)) return 'WRITE';
  return 'READ';
};

export function summarizePermissionBundle(items: readonly PermissionBundleItem[]): PermissionBundleSummary {
  const summary: PermissionBundleSummary = { total: items.length, read: 0, write: 0, export: 0, admin: 0, critical: 0, systemManaged: 0, requiresIndependentApproval: false };
  for (const item of items) {
    const lane = (String(item.riskLane ?? '').toUpperCase() as PermissionRiskLane) || inferredRisk(item.permissionCode);
    const normalized = ['READ', 'WRITE', 'EXPORT', 'ADMIN', 'CRITICAL'].includes(lane) ? lane : inferredRisk(item.permissionCode);
    summary[normalized.toLowerCase() as 'read' | 'write' | 'export' | 'admin' | 'critical'] += 1;
    if (item.systemManaged) summary.systemManaged += 1;
  }
  summary.requiresIndependentApproval = summary.admin > 0 || summary.critical > 0;
  return summary;
}

export interface ScopeGrantDraft {
  permissionCode: string;
  resourceType: string;
  scopeType: string;
  scopeRefId?: string;
  visibilityLevel: string;
  validFrom: string;
  validTo?: string | null;
  grantReason: string;
  allowedScopeTypes?: string[];
  riskLane?: PermissionRiskLane | string;
}

export interface ScopeGrantValidation {
  status: ScopeGrantValidationStatus;
  issues: string[];
  durationHours: number | null;
  temporary: boolean;
  safestNextAction: string;
}

const IMPLICIT_SCOPES = new Set(['TENANT', 'OWNER', 'PARTICIPANT', 'CREATED_BY_ME', 'ASSIGNED_TO_ME']);
const HIGH_VISIBILITY = new Set(['SENSITIVE', 'FULL', 'SECRET_METADATA']);

export function validateScopeGrantDraft(input: ScopeGrantDraft, now = new Date()): ScopeGrantValidation {
  const issues: string[] = [];
  const permission = input.permissionCode.trim();
  const reason = input.grantReason.trim();
  const scope = input.scopeType.trim().toUpperCase();
  const risk = String(input.riskLane ?? inferredRisk(permission)).toUpperCase();
  if (!permission || /\b(select|insert|update|delete|drop|alter|grant)\s+/i.test(permission)) issues.push('Permission must be a catalog action, never SQL or an arbitrary expression.');
  if (!input.resourceType.trim()) issues.push('Resource type is required.');
  if (!scope) issues.push('Scope type is required.');
  if (input.allowedScopeTypes?.length && !input.allowedScopeTypes.includes(scope)) issues.push('The selected scope is not supported by the catalog permission.');
  if (!IMPLICIT_SCOPES.has(scope) && !String(input.scopeRefId ?? '').trim()) issues.push('A scope reference is required for the selected scope.');
  if (reason.length < 12) issues.push('Business purpose must contain at least 12 characters.');
  const start = Date.parse(input.validFrom);
  const end = input.validTo ? Date.parse(input.validTo) : Number.NaN;
  if (!Number.isFinite(start)) issues.push('Valid-from timestamp is required.');
  if (input.validTo && !Number.isFinite(end)) issues.push('Valid-to timestamp is invalid.');
  if (Number.isFinite(end) && Number.isFinite(start) && end <= start) issues.push('Valid-to must be later than valid-from.');
  const durationHours = Number.isFinite(end) && Number.isFinite(start) ? Math.round((end - start) / 3_600_000) : null;
  if (durationHours != null && durationHours > 24 * 30) issues.push('Temporary access cannot exceed 30 days in the standard request lane.');
  if (['ADMIN', 'CRITICAL'].includes(risk) && durationHours == null) issues.push('Administrative or critical access requires an explicit expiration.');
  if (HIGH_VISIBILITY.has(input.visibilityLevel.toUpperCase()) && durationHours == null) issues.push('Sensitive visibility requires an explicit expiration.');
  if (start < now.getTime() - 300_000) issues.push('Valid-from cannot be materially in the past.');
  const status: ScopeGrantValidationStatus = issues.length ? 'BLOCKED' : (durationHours == null || durationHours > 24 * 7 || HIGH_VISIBILITY.has(input.visibilityLevel.toUpperCase()) ? 'WARNING' : 'READY');
  return {
    status,
    issues,
    durationHours,
    temporary: durationHours != null,
    safestNextAction: issues.length ? 'Correct the blocked fields before creating a draft.' : status === 'WARNING' ? 'Review duration, visibility, and approval impact before submission.' : 'Create a draft and submit it for independent approval.',
  };
}

export function durationPresetHours(preset: GrantDurationPreset): number {
  return { EIGHT_HOURS: 8, ONE_DAY: 24, SEVEN_DAYS: 168, THIRTY_DAYS: 720 }[preset];
}

export interface AccessRequestInput {
  requestedAction: string;
  resourceType: string;
  resourceId: string;
  businessPurpose: string;
  durationHours: number;
  requestedVisibility: string;
  urgency?: string;
}

export function validateAccessRequest(input: AccessRequestInput): ScopeGrantValidation {
  const issues: string[] = [];
  if (!input.requestedAction.trim()) issues.push('Select a server-defined action.');
  if (!input.resourceType.trim() || !input.resourceId.trim()) issues.push('Select the target resource.');
  if (input.businessPurpose.trim().length < 20) issues.push('Business purpose must contain at least 20 characters.');
  if (!Number.isFinite(input.durationHours) || input.durationHours <= 0 || input.durationHours > 720) issues.push('Request duration must be between 1 hour and 30 days.');
  if (input.requestedAction.includes('permissionCode') || /\s/.test(input.requestedAction.trim())) issues.push('The request must use a server-defined UI action, not a raw permission expression.');
  return {
    status: issues.length ? 'BLOCKED' : input.durationHours > 168 || HIGH_VISIBILITY.has(input.requestedVisibility.toUpperCase()) ? 'WARNING' : 'READY',
    issues,
    durationHours: input.durationHours,
    temporary: true,
    safestNextAction: issues.length ? 'Correct the request before submission.' : 'Submit the temporary request to the resource owner or eligible approver.',
  };
}

export interface SeparationOfDutiesInput { requesterId?: string; actorId?: string; approverId?: string; action: string; }
export function separationOfDuties(input: SeparationOfDutiesInput) {
  const requester = String(input.requesterId ?? '').trim();
  const actor = String(input.actorId ?? '').trim();
  const approver = String(input.approverId ?? actor).trim();
  const violation = Boolean(requester && approver && requester === approver);
  return {
    allowed: !violation,
    reason: violation ? 'The requester cannot approve or activate the same access change.' : 'An independent actor may continue, subject to backend authorization and recent-authentication policy.',
    stepUpStillBackendAuthoritative: true,
  } as const;
}

export interface OwnershipTransferImpactInput {
  participantCount: number;
  activeGrantCount: number;
  activeDenyCount: number;
  childResourceCount: number;
  changesDepartment: boolean;
  changesGroup: boolean;
  clearsExistingOwner?: boolean;
}

export function ownershipTransferImpact(input: OwnershipTransferImpactInput) {
  const affected = Math.max(0, input.participantCount) + Math.max(0, input.activeGrantCount) + Math.max(0, input.activeDenyCount) + Math.max(0, input.childResourceCount);
  const risk: OwnershipTransferRisk = input.clearsExistingOwner ? 'BLOCKED' : (input.activeDenyCount > 0 || input.childResourceCount > 0 || affected >= 25 || (input.changesDepartment && input.changesGroup) ? 'HIGH_RISK' : 'CONTROLLED');
  return {
    risk,
    affectedEvidenceCount: affected,
    requiresApproval: risk !== 'CONTROLLED',
    requiresStepUp: risk !== 'CONTROLLED',
    safestNextAction: risk === 'BLOCKED' ? 'Select a canonical owner before continuing.' : risk === 'HIGH_RISK' ? 'Review access changes, cache/epoch impact, child resources, and obtain approval.' : 'Run the backend impact preview and confirm the latest resource version.',
  } as const;
}

export function accessReviewDecisionExperience(decision: AccessReviewDecision) {
  return {
    KEEP: { title: 'Keep current access', effect: 'Retain the current grant until its existing expiration.', requiresReason: true },
    REDUCE: { title: 'Reduce access', effect: 'Replace broad scope or visibility with a smaller approved grant.', requiresReason: true },
    REVOKE: { title: 'Revoke access', effect: 'End the access grant and invalidate affected authorization cache namespaces.', requiresReason: true },
    REQUEST_MORE_INFORMATION: { title: 'Request more information', effect: 'Leave the review open without granting additional access.', requiresReason: true },
  }[decision];
}

export function explicitDenyExperience(severity: string, state: string) {
  const critical = severity.toUpperCase() === 'CRITICAL';
  const active = state.toUpperCase() === 'ACTIVE';
  return {
    title: critical ? 'Critical explicit deny' : 'Explicit deny',
    authorityEffect: active ? 'A matching active deny overrides role, ownership, participant, and scope-grant allow evidence.' : 'The deny is not active until independent approval and backend lifecycle checks complete.',
    requiresStepUp: critical,
    requiresIndependentApproval: true,
    colorAloneForbidden: true,
  } as const;
}
