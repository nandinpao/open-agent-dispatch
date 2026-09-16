export const UI_CAPABILITY_CONTRACT_VERSION = '1.0' as const;
export const UI_CAPABILITY_MEDIA_TYPE = 'application/vnd.opendispatch.ui-capability.v1+json' as const;

export const UI_DISPLAY_MODES = [
  'HIDE',
  'DISABLE_WITH_REASON',
  'READ_ONLY',
  'ENABLED',
  'STEP_UP_REQUIRED',
  'APPROVAL_REQUIRED',
  'REQUEST_ACCESS',
  'LOCKED_SECURITY',
  'STALE_RELOAD',
] as const;
export type UiDisplayMode = (typeof UI_DISPLAY_MODES)[number];

export const UI_REASON_CATEGORIES = [
  'NOT_ALLOWED',
  'OUTSIDE_SCOPE',
  'READ_ONLY_ACCESS',
  'APPROVAL_REQUIRED',
  'STEP_UP_REQUIRED',
  'SEPARATION_OF_DUTIES',
  'RESOURCE_LOCKED',
  'RESOURCE_QUARANTINED',
  'RESOURCE_CHANGED',
  'TENANT_CONTEXT_CHANGED',
  'ACTION_NOT_AVAILABLE_IN_CURRENT_STATE',
  'BACKGROUND_OPERATION_IN_PROGRESS',
  'EXTERNAL_PROVIDER_UNAVAILABLE',
  'TEMPORARILY_UNAVAILABLE',
] as const;
export type UiReasonCategory = (typeof UI_REASON_CATEGORIES)[number];

export const UI_VISIBILITY_LEVELS = [
  'NONE',
  'METADATA',
  'SUMMARY',
  'STANDARD',
  'SENSITIVE',
  'FULL',
  'SECRET_METADATA',
] as const;
export type UiVisibilityLevel = (typeof UI_VISIBILITY_LEVELS)[number];

export interface UiCapability {
  uiActionId: string;
  displayMode: UiDisplayMode;
  reasonCategory?: UiReasonCategory;
  stepUpRequired: boolean;
  approvalRequired: boolean;
  visibilityCeiling: UiVisibilityLevel;
  requestAccessAllowed: boolean;
  relatedHelpId?: string;
}

export interface UiCapabilityEnvelope {
  contractVersion: typeof UI_CAPABILITY_CONTRACT_VERSION;
  contextId: string;
  tenantId: string;
  principalEpoch: number;
  catalogRevision: number;
  policyVersion: number;
  resourceRefHash?: string;
  resourceVersion?: number;
  enforcementMode: string;
  capabilities: readonly UiCapability[];
  expiresAt: string;
  refreshAfter: string;
  viewStateToken?: string;
}

export interface UiCapabilityContextRequest {
  contextId: string;
  resourceId: string;
  resourceVersion?: number;
  presentedPrincipalEpoch?: number;
  uiActionIds: readonly string[];
}

export interface UiCapabilityBatchRequest {
  contractVersion: typeof UI_CAPABILITY_CONTRACT_VERSION;
  contexts: readonly UiCapabilityContextRequest[];
}

export interface UiCapabilityBatchResponse {
  contractVersion: typeof UI_CAPABILITY_CONTRACT_VERSION;
  contexts: readonly UiCapabilityEnvelope[];
}

export interface UiListCapabilitySummary {
  contextId: string;
  resourceRefHash?: string;
  resourceVersion?: number;
  principalEpoch: number;
  catalogRevision: number;
  policyVersion: number;
  actions: Readonly<Record<string, UiCapability>>;
  expiresAt: string;
}

export const UI_PAGE_BOOTSTRAP_OUTCOMES = [
  'PAGE',
  'STEP_UP_SHELL',
  'REQUEST_ACCESS_SHELL',
  'SAFE_RESOURCE_CHANGED_SHELL',
  'ANTI_ENUMERATION_NOT_FOUND_SHELL',
] as const;
export type UiPageBootstrapOutcome = (typeof UI_PAGE_BOOTSTRAP_OUTCOMES)[number];

export interface UiPageBootstrap {
  contractVersion: typeof UI_CAPABILITY_CONTRACT_VERSION;
  routeContext: string;
  canonicalPath: string;
  tenantId: string;
  principalEpoch: number;
  catalogRevision: number;
  policyVersion: number;
  outcome: UiPageBootstrapOutcome;
  layoutCapabilities: readonly UiCapability[];
  pageCapabilities: readonly UiCapability[];
  resourceSummaryRef?: string;
  resourceVersion?: number;
  expiresAt: string;
  hydrationNonce: string;
}

export function validateUiCapability(capability: UiCapability): readonly string[] {
  const issues: string[] = [];
  if (!/^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*){2,}$/.test(capability.uiActionId)) {
    issues.push('uiActionId must be a canonical dotted identifier');
  }
  if (capability.displayMode === 'ENABLED' && capability.reasonCategory) {
    issues.push('ENABLED capability must not expose a denial reason');
  }
  if (capability.displayMode === 'HIDE' && capability.reasonCategory) {
    issues.push('HIDE capability must not expose a reason');
  }
  if (!['ENABLED', 'HIDE'].includes(capability.displayMode) && !capability.reasonCategory) {
    issues.push('non-enabled visible capability requires a safe reason');
  }
  if (capability.stepUpRequired !== (capability.displayMode === 'STEP_UP_REQUIRED')) {
    issues.push('stepUpRequired must match STEP_UP_REQUIRED display mode');
  }
  if (capability.approvalRequired !== (capability.displayMode === 'APPROVAL_REQUIRED')) {
    issues.push('approvalRequired must match APPROVAL_REQUIRED display mode');
  }
  if (capability.requestAccessAllowed !== (capability.displayMode === 'REQUEST_ACCESS')) {
    issues.push('requestAccessAllowed must match REQUEST_ACCESS display mode');
  }
  return issues;
}

export function validateUiCapabilityEnvelope(envelope: UiCapabilityEnvelope): readonly string[] {
  const issues: string[] = [];
  if (envelope.contractVersion !== UI_CAPABILITY_CONTRACT_VERSION) {
    issues.push('unsupported UI capability contract version');
  }
  for (const [field, value] of [
    ['principalEpoch', envelope.principalEpoch],
    ['catalogRevision', envelope.catalogRevision],
    ['policyVersion', envelope.policyVersion],
  ] as const) {
    if (!Number.isSafeInteger(value) || value < 0) issues.push(`${field} must be a non-negative safe integer`);
  }
  if (envelope.resourceVersion !== undefined
      && (!Number.isSafeInteger(envelope.resourceVersion) || envelope.resourceVersion < 0)) {
    issues.push('resourceVersion must be a non-negative safe integer');
  }
  const actionIds = new Set<string>();
  for (const capability of envelope.capabilities) {
    issues.push(...validateUiCapability(capability));
    if (actionIds.has(capability.uiActionId)) issues.push(`duplicate uiActionId: ${capability.uiActionId}`);
    actionIds.add(capability.uiActionId);
  }
  const expiresAt = Date.parse(envelope.expiresAt);
  const refreshAfter = Date.parse(envelope.refreshAfter);
  if (!Number.isFinite(expiresAt) || !Number.isFinite(refreshAfter)) {
    issues.push('expiresAt and refreshAfter must be valid date-time values');
  } else if (refreshAfter > expiresAt) {
    issues.push('refreshAfter must not be after expiresAt');
  }
  return issues;
}
