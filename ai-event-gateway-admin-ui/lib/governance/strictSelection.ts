export interface GovernedOption {
  value: string;
  label: string;
  description?: string;
}

export const GOVERNED_AGENT_TYPES: GovernedOption[] = [
  { value: 'ANY', label: 'Any governed agent' },
  { value: 'OPENCLAW', label: 'OpenClaw worker' },
  { value: 'LOCAL_SIMULATOR', label: 'Local simulator' },
  { value: 'ISSUE_SYNC', label: 'Issue sync worker' },
  { value: 'REMEDIATION', label: 'Remediation worker' },
  { value: 'DISPATCH_TEST', label: 'Dispatch test worker' },
];

export const GOVERNED_OWNER_TEAMS: GovernedOption[] = [
  { value: 'PLATFORM', label: 'Platform' },
  { value: 'SRE', label: 'SRE' },
  { value: 'SECOPS', label: 'SecOps' },
  { value: 'COMPLIANCE', label: 'Compliance' },
  { value: 'APPLICATION', label: 'Application' },
  { value: 'QA', label: 'QA' },
];

export const GOVERNED_TOOL_POLICIES: GovernedOption[] = [
  { value: 'READ_ONLY', label: 'Read only' },
  { value: 'PROPOSE_ONLY', label: 'Propose only' },
  { value: 'EXECUTE_APPROVED', label: 'Execute after approval' },
  { value: 'NO_TOOLS', label: 'No tools' },
];

export const GOVERNED_RISK_LEVELS: GovernedOption[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MIDDLE', label: 'Middle' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
];

export const GOVERNED_POLICY_TEMPLATES: GovernedOption[] = [
  { value: 'BASELINE_POLICY', label: 'Baseline policy' },
  { value: 'HIGH_RISK_POLICY', label: 'High-risk policy' },
  { value: 'PII_POLICY', label: 'PII / masking policy' },
  { value: 'ISSUE_SYNC_POLICY', label: 'Issue sync policy' },
];

export const GOVERNED_PROFILE_ROLES: GovernedOption[] = [
  { value: 'REVIEWER', label: 'Reviewer' },
  { value: 'APPROVER', label: 'Approver' },
  { value: 'EXECUTOR', label: 'Executor' },
  { value: 'AUDITOR', label: 'Auditor' },
  { value: 'RESOLVER', label: 'Resolver' },
];

export const GOVERNED_POLICY_OPERATIONS: GovernedOption[] = [
  { value: 'READ', label: 'Read' },
  { value: 'ANALYZE', label: 'Analyze' },
  { value: 'PROPOSE', label: 'Propose' },
  { value: 'EXECUTE', label: 'Execute' },
  { value: 'SYNC_ISSUE', label: 'Sync issue' },
];

export const GOVERNED_DATA_CLASSES: GovernedOption[] = [
  { value: 'PUBLIC', label: 'Public' },
  { value: 'INTERNAL', label: 'Internal' },
  { value: 'CONFIDENTIAL', label: 'Confidential' },
  { value: 'PII', label: 'PII' },
  { value: 'FINANCIAL', label: 'Financial' },
];

export const GOVERNED_RESOURCE_SCOPES: GovernedOption[] = [
  { value: 'TENANT', label: 'Tenant' },
  { value: 'SOURCE_SYSTEM', label: 'Source system' },
  { value: 'TASK_DEFINITION', label: 'Task definition' },
  { value: 'ISSUE_PROJECT', label: 'Issue project' },
];

export function normalizeGovernedCode(value?: string | null): string {
  return (value ?? '').trim().toUpperCase().replace(/[.-]/g, '_').replace(/[^A-Z0-9_*]/g, '_');
}

export function isActiveCatalogStatus(status?: string | null): boolean {
  return normalizeGovernedCode(status || 'ACTIVE') === 'ACTIVE';
}

export function hasOption(options: readonly GovernedOption[], value?: string | null): boolean {
  const normalized = normalizeGovernedCode(value);
  return options.some((option) => normalizeGovernedCode(option.value) === normalized);
}

export function coerceGovernedOption(options: readonly GovernedOption[], value: string | undefined, fallback: string): string {
  const normalized = normalizeGovernedCode(value);
  return hasOption(options, normalized) ? normalized : fallback;
}

export function splitGovernedCsv(value?: string | null): string[] {
  return Array.from(new Set((value ?? '').split(',').map(normalizeGovernedCode).filter(Boolean)));
}

export function joinGovernedCsv(values: readonly string[]): string {
  return Array.from(new Set(values.map(normalizeGovernedCode).filter(Boolean))).sort().join(', ');
}

export function findLegacyValues(values: readonly string[], options: readonly GovernedOption[]): string[] {
  return values.map(normalizeGovernedCode).filter((value) => value && !hasOption(options, value));
}

export function generatedProfileCode(sourceSystem?: string, taskType?: string, role = 'REVIEWER'): string {
  const source = normalizeGovernedCode(sourceSystem);
  const task = normalizeGovernedCode(taskType);
  const normalizedRole = normalizeGovernedCode(role) || 'REVIEWER';
  return source && task ? `${source}_${task}_${normalizedRole}` : '';
}

export function generatedPolicyCode(sourceSystem?: string, taskType?: string, template = 'BASELINE_POLICY'): string {
  const source = normalizeGovernedCode(sourceSystem);
  const task = normalizeGovernedCode(taskType);
  const normalizedTemplate = normalizeGovernedCode(template) || 'BASELINE_POLICY';
  return source && task ? `${source}_${task}_${normalizedTemplate}` : '';
}
