'use client';

import { useMemo, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type {
  Department,
  Group,
  RbacCriticalApproval,
  RbacHardeningPreview,
  ResponsibilityTemplate,
  Role,
  RoleBinding,
  RoleBindingAssignmentPreview,
  RoleBindingDraft,
  ServiceAccount,
  User,
} from '@/lib/iam/types';
import {
  AuditReasonSelector,
  isAuditReasonValid,
  datePresetToIso,
  FieldLabel,
  HierarchySelectField,
  humanizePermission,
  SelectField,
  SearchSelectField,
  type SelectOption,
  WizardProgress,
  type WizardStepDefinition,
} from '../shared/beginnerUi';

const STEPS: WizardStepDefinition[] = [
  { key: 'who', label: 'Who', description: 'Person, Department, Group or Service Account' },
  { key: 'responsibility', label: 'Responsibility', description: 'Business Role Template' },
  { key: 'where', label: 'Where', description: 'Tenant, Department hierarchy or Group' },
  { key: 'duration', label: 'Duration', description: 'Validity and business reason' },
  { key: 'review', label: 'Review', description: 'Impact, approval and confirmation' },
];

export interface AccessWizardInitialValue {
  principalType?: 'USER' | 'DEPARTMENT' | 'GROUP' | 'SERVICE_ACCOUNT';
  principalId?: string;
  roleId?: string;
  roleCode?: string;
  scopeType?: 'TENANT' | 'DEPARTMENT' | 'DEPARTMENT_SUBTREE' | 'GROUP';
  scopeId?: string;
}

interface AssignAccessWizardProps {
  tenantId: string;
  tenantName: string;
  users: User[];
  groups: Group[];
  departments: Department[];
  roles: Role[];
  templates?: ResponsibilityTemplate[];
  serviceAccounts?: ServiceAccount[];
  approvals: RbacCriticalApproval[];
  initial?: AccessWizardInitialValue;
  onCancel: () => void;
  onComplete: (binding: RoleBinding) => Promise<void> | void;
}

export function AssignAccessWizard({
  tenantId,
  tenantName,
  users,
  groups,
  departments,
  roles,
  templates = [],
  serviceAccounts = [],
  approvals,
  initial,
  onCancel,
  onComplete,
}: Readonly<AssignAccessWizardProps>) {
  const initialRoleId = initial?.roleId
    || roles.find((role) => role.roleCode === initial?.roleCode)?.roleId
    || '';
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<RoleBindingDraft>({
    principalType: initial?.principalType ?? 'USER',
    principalId: initial?.principalId ?? '',
    roleId: initialRoleId,
    scopeType: initial?.scopeType ?? 'TENANT',
    scopeId: initial?.scopeId ?? tenantId,
    effectiveAt: null,
    expiresAt: null,
    approvalId: null,
  });
  const [duration, setDuration] = useState('PERMANENT');
  const [customDate, setCustomDate] = useState('');
  const [reason, setReason] = useState('Job responsibility changed');
  const [preview, setPreview] = useState<RoleBindingAssignmentPreview | null>(null);
  const [hardening, setHardening] = useState<RbacHardeningPreview | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [completed, setCompleted] = useState(false);

  const departmentHierarchyOptions = useMemo(() => buildDepartmentHierarchyOptions(departments), [departments]);
  const groupHierarchyOptions = useMemo(() => buildGroupHierarchyOptions(groups), [groups]);

  const principalOptions = useMemo<SelectOption[]>(() => {
    if (draft.principalType === 'GROUP') return groupHierarchyOptions;
    if (draft.principalType === 'SERVICE_ACCOUNT') return serviceAccounts.filter((account) => account.status === 'ACTIVE').map((account) => ({ value: account.serviceAccountId, label: account.name, description: account.description || `Owned by ${account.ownerUserId}` }));
    return users.filter((user) => user.status === 'ACTIVE' || user.status === 'INVITED').map((user) => ({ value: user.userId, label: `${user.displayName} (${user.username})`, description: user.email }));
  }, [draft.principalType, groupHierarchyOptions, serviceAccounts, users]);

  const roleOptions = useMemo<SelectOption[]>(() => (templates.length ? templates.filter((role) => role.permissionCount > 0 && role.allowedScopeTypes.length > 0) : roles)
    .filter((role) => role.status === 'ACTIVE')
    .map((role) => ({ value: role.roleId, label: role.roleName, description: role.description || role.roleCode })), [roles, templates]);

  const scopeOptions = useMemo<SelectOption[]>(() => {
    if (draft.scopeType === 'DEPARTMENT' || draft.scopeType === 'DEPARTMENT_SUBTREE') {
      return departmentHierarchyOptions;
    }
    if (draft.scopeType === 'GROUP') {
      return groupHierarchyOptions;
    }
    return [{ value: tenantId, label: tenantName }];
  }, [departmentHierarchyOptions, draft.scopeType, groupHierarchyOptions, tenantId, tenantName]);

  const selectedRole = roles.find((role) => role.roleId === draft.roleId);
  const selectedTemplate = templates.find((template) => template.roleId === draft.roleId);
  const selectedPrincipal = draft.principalType === 'DEPARTMENT'
    ? departments.find((department) => department.departmentId === draft.principalId)?.name
    : draft.principalType === 'GROUP'
    ? groups.find((group) => group.groupId === draft.principalId)?.name
    : draft.principalType === 'SERVICE_ACCOUNT'
      ? serviceAccounts.find((account) => account.serviceAccountId === draft.principalId)?.name
      : users.find((user) => user.userId === draft.principalId)?.displayName;
  const supportedScopes = selectedTemplate?.allowedScopeTypes.length ? selectedTemplate.allowedScopeTypes : ['TENANT', 'DEPARTMENT', 'DEPARTMENT_SUBTREE', 'GROUP'];
  const selectedScope = scopeOptions.find((scope) => scope.value === draft.scopeId)?.label ?? draft.scopeId;
  const matchingApprovals = approvals.filter((approval) => approval.status === 'APPROVED' && approval.requestHash === hardening?.requestHash);
  const blocked = Boolean(hardening?.conflicts.length);
  const needsApproval = Boolean(hardening?.approvalRequired && !draft.approvalId);

  function updateDraft(value: Partial<RoleBindingDraft>) {
    setDraft((current) => ({ ...current, ...value, approvalId: value.approvalId ?? null }));
    setPreview(null);
    setHardening(null);
    setError('');
    setNotice('');
  }

  function validationForStep(): string {
    if (step === 0 && !draft.principalId) return 'Choose a person, Department, Group or Service Account.';
    if (step === 1 && !draft.roleId) return 'Choose a responsibility.';
    if (step === 2 && !draft.scopeId) return 'Choose where the responsibility applies.';
    if (step === 3) {
      if (duration === 'CUSTOM' && !customDate) return 'Choose the access expiry date.';
      if (!isAuditReasonValid(reason, 'ELEVATED')) return 'Choose a governance reason or enter a specific business reason.';
    }
    return '';
  }

  async function next() {
    const validation = validationForStep();
    if (validation) {
      setError(validation);
      return;
    }
    if (step < 3) {
      setStep((current) => current + 1);
      return;
    }
    setBusy(true);
    setError('');
    try {
      const nextDraft: RoleBindingDraft = {
        ...draft,
        effectiveAt: null,
        expiresAt: datePresetToIso(duration, customDate),
      };
      const [assignmentPreview, riskPreview] = await Promise.all([
        accessManagementApi.previewRoleBinding(tenantId, nextDraft),
        accessManagementApi.previewRoleBindingHardening(tenantId, nextDraft),
      ]);
      setDraft(nextDraft);
      setPreview(assignmentPreview);
      setHardening(riskPreview);
      setStep(4);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to review the access assignment.'));
    } finally {
      setBusy(false);
    }
  }

  async function requestApproval() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const approval = await accessManagementApi.requestRoleBindingApproval(tenantId, draft, reason);
      setNotice(`Approval request ${approval.approvalId} was created. A different authorized person must approve it.`);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to request approval.'));
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!preview || !hardening) return;
    if (blocked) {
      setError('This assignment is blocked by an access conflict. Resolve the conflict before continuing.');
      return;
    }
    if (needsApproval) {
      setError('Attach an approved two-person request before continuing.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const binding = await accessManagementApi.bindRole(tenantId, draft, reason);
      setCompleted(true);
      await onComplete(binding);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to assign the responsibility.'));
    } finally {
      setBusy(false);
    }
  }

  if (completed) {
    return (
      <section className="rounded-3xl border border-emerald-200 bg-white p-6 text-center shadow-xl">
        <div className="mx-auto flex size-14 items-center justify-center rounded-full bg-emerald-100 text-2xl font-black text-emerald-800">✓</div>
        <h2 className="mt-4 text-xl font-black text-slate-950">Access assigned</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">
          {selectedPrincipal} now has the {selectedRole?.roleName ?? 'selected'} responsibility for {selectedScope}.
        </p>
        <button type="button" onClick={onCancel} className="mt-5 rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-black text-white">Return to Access</button>
      </section>
    );
  }

  return (
    <section className="rounded-3xl border border-blue-200 bg-white p-5 shadow-xl" aria-labelledby="assign-access-title">
      <div className="flex flex-col gap-3 border-b border-slate-200 pb-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Guided access assignment</p>
          <h2 id="assign-access-title" className="mt-1 text-xl font-black text-slate-950">Who needs to do what, where, and for how long?</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">The UI uses business responsibilities. Principal IDs, Scope IDs, Binding IDs and permission codes remain technical evidence.</p>
        </div>
        <button type="button" onClick={onCancel} className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-black text-slate-700">Close</button>
      </div>

      <div className="mt-5"><WizardProgress steps={STEPS} currentIndex={step} /></div>
      {error ? <div role="alert" className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-800">{error}</div> : null}
      {notice ? <div role="status" className="mt-5 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm font-semibold text-blue-900">{notice}</div> : null}

      <div className="mt-5 rounded-3xl border border-slate-200 bg-slate-50 p-5">
        {step === 0 ? (
          <div className="grid gap-4 lg:grid-cols-2">
            <FieldLabel htmlFor="access-principal-type" label="Assign to" required>
              <SelectField
                id="access-principal-type"
                value={draft.principalType}
                onChange={(value) => updateDraft({ principalType: value as RoleBindingDraft['principalType'], principalId: '' })}
                options={[{ value: 'USER', label: 'A person' }, { value: 'DEPARTMENT', label: 'A Department' }, { value: 'GROUP', label: 'A Group' }, { value: 'SERVICE_ACCOUNT', label: 'A Service Account' }]}
                required
              />
            </FieldLabel>
            <FieldLabel htmlFor="access-principal" label={draft.principalType === 'DEPARTMENT' ? 'Department' : draft.principalType === 'GROUP' ? 'Group' : draft.principalType === 'SERVICE_ACCOUNT' ? 'Service Account' : 'Person'} help={draft.principalType === 'DEPARTMENT' ? 'Members of this Department and its child Departments inherit the responsibility through the organization hierarchy.' : draft.principalType === 'GROUP' ? 'Members of this Group and child Groups inherit the responsibility.' : undefined} required>
              {draft.principalType === 'DEPARTMENT' ? <HierarchySelectField id="access-principal" value={draft.principalId} onChange={(value) => updateDraft({ principalId: value })} options={departmentHierarchyOptions} placeholder="Search Department hierarchy" required /> : draft.principalType === 'GROUP' ? <HierarchySelectField id="access-principal" value={draft.principalId} onChange={(value) => updateDraft({ principalId: value })} options={groupHierarchyOptions} placeholder="Search Group hierarchy" required /> : <SearchSelectField id="access-principal" value={draft.principalId} onChange={(value) => updateDraft({ principalId: value })} options={principalOptions} placeholder={draft.principalType === 'SERVICE_ACCOUNT' ? 'Search Service Accounts' : 'Search people'} required />}
            </FieldLabel>
          </div>
        ) : null}

        {step === 1 ? (
          <div className="space-y-4">
            <FieldLabel htmlFor="access-responsibility" label="Business responsibility" help="Choose a governed Role Template. Atomic permissions are hidden from normal assignment." required>
              <SearchSelectField id="access-responsibility" value={draft.roleId} onChange={(value) => {
                const template = templates.find((item) => item.roleId === value);
                const allowed = template?.allowedScopeTypes.length ? template.allowedScopeTypes : ['TENANT', 'DEPARTMENT', 'DEPARTMENT_SUBTREE', 'GROUP'];
                const nextScope = allowed.includes(draft.scopeType) ? draft.scopeType : (allowed[0] as RoleBindingDraft['scopeType'] | undefined) ?? 'TENANT';
                updateDraft({ roleId: value, scopeType: nextScope, scopeId: nextScope === 'TENANT' ? tenantId : '' });
              }} options={roleOptions} placeholder="Search responsibilities" required />
            </FieldLabel>
            {selectedRole || selectedTemplate ? (
              <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-black text-blue-950">{selectedTemplate?.roleName ?? selectedRole?.roleName}</p><p className="mt-2 text-sm leading-6 text-blue-900">{selectedTemplate?.description || selectedRole?.description || 'This responsibility uses the approved Role Template.'}</p></div>{selectedTemplate ? <span className="rounded-full border border-blue-300 bg-white px-2.5 py-1 text-xs font-black text-blue-900">{selectedTemplate.riskLevel} risk</span> : null}</div>
                {selectedTemplate ? <div className="mt-3 grid gap-2 sm:grid-cols-3"><div className="rounded-xl bg-white p-3 text-xs"><b className="block text-blue-950">Current use</b>{selectedTemplate.activeAssignmentCount} active assignments</div><div className="rounded-xl bg-white p-3 text-xs"><b className="block text-blue-950">Capabilities</b>{selectedTemplate.permissionCount} approved capabilities</div><div className="rounded-xl bg-white p-3 text-xs"><b className="block text-blue-950">Review</b>{selectedTemplate.reviewRequired ? 'Periodic review required' : 'Standard lifecycle'}</div></div> : null}
                <details className="mt-3"><summary className="cursor-pointer text-xs font-black text-blue-800">Technical Role information</summary><p className="mt-2 text-xs text-blue-800">Role code: {selectedTemplate?.roleCode ?? selectedRole?.roleCode}</p></details>
              </div>
            ) : null}
          </div>
        ) : null}

        {step === 2 ? (
          <div className="grid gap-4 lg:grid-cols-2">
            <FieldLabel htmlFor="access-scope-type" label="Where it applies" required>
              <SelectField
                id="access-scope-type"
                value={draft.scopeType}
                onChange={(value) => {
                  const scopeType = value as RoleBindingDraft['scopeType'];
                  updateDraft({ scopeType, scopeId: scopeType === 'TENANT' ? tenantId : '' });
                }}
                options={[
                  { value: 'TENANT', label: `Entire Tenant — ${tenantName}`, disabled: !supportedScopes.includes('TENANT') },
                  { value: 'DEPARTMENT', label: 'One Department only', disabled: !supportedScopes.includes('DEPARTMENT') },
                  { value: 'DEPARTMENT_SUBTREE', label: 'Department and child Departments', disabled: !supportedScopes.includes('DEPARTMENT_SUBTREE') },
                  { value: 'GROUP', label: 'A Group', disabled: !supportedScopes.includes('GROUP') },
                ]}
                required
              />
            </FieldLabel>
            <FieldLabel htmlFor="access-scope" label={draft.scopeType === 'TENANT' ? 'Tenant' : draft.scopeType === 'GROUP' ? 'Group' : 'Department'} help={draft.scopeType === 'DEPARTMENT_SUBTREE' ? 'The responsibility applies to the selected Department and all child Departments.' : undefined} required>
              {draft.scopeType === 'DEPARTMENT' || draft.scopeType === 'DEPARTMENT_SUBTREE' ? <HierarchySelectField id="access-scope" value={draft.scopeId} onChange={(value) => updateDraft({ scopeId: value })} options={departmentHierarchyOptions} placeholder="Search Department hierarchy" required /> : draft.scopeType === 'GROUP' ? <HierarchySelectField id="access-scope" value={draft.scopeId} onChange={(value) => updateDraft({ scopeId: value })} options={groupHierarchyOptions} placeholder="Search Group hierarchy" required /> : <SearchSelectField id="access-scope" value={draft.scopeId} onChange={(value) => updateDraft({ scopeId: value })} options={scopeOptions} placeholder="Current Tenant" required />}
            </FieldLabel>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="grid gap-4 lg:grid-cols-2">
            <FieldLabel htmlFor="access-duration" label="Access duration" required>
              <SelectField
                id="access-duration"
                value={duration}
                onChange={setDuration}
                options={[
                  { value: 'PERMANENT', label: 'No planned expiry' },
                  { value: '30', label: '30 days' },
                  { value: '90', label: '90 days' },
                  { value: 'CUSTOM', label: 'Choose a date' },
                ]}
                required
              />
            </FieldLabel>
            {duration === 'CUSTOM' ? (
              <FieldLabel htmlFor="access-expiry" label="Access expiry date" required>
                <input id="access-expiry" type="date" value={customDate} onChange={(event) => setCustomDate(event.target.value)} className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm" />
              </FieldLabel>
            ) : <div />}
            <div className="lg:col-span-2">
              <AuditReasonSelector idPrefix="access-assignment" value={reason} onChange={setReason} tier="ELEVATED" />
            </div>
          </div>
        ) : null}

        {step === 4 && preview && hardening ? (
          <div className="space-y-5">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <ReviewCard label="Who" value={selectedPrincipal ?? draft.principalId} />
              <ReviewCard label="Responsibility" value={preview.roleName || selectedRole?.roleName || draft.roleId} />
              <ReviewCard label="Where" value={selectedScope} />
              <ReviewCard label="Duration" value={draft.expiresAt ? `Until ${new Date(draft.expiresAt).toLocaleDateString()}` : 'No planned expiry'} />
            </div>

            <section className="rounded-2xl border border-slate-200 bg-white p-4">
              <h3 className="font-black text-slate-950">What will change</h3>
              <div className="mt-3 grid gap-3 lg:grid-cols-2">
                <div className="rounded-xl bg-emerald-50 p-3">
                  <p className="text-xs font-black uppercase tracking-wide text-emerald-800">New capabilities</p>
                  <ul className="mt-2 space-y-1 text-sm text-emerald-950">
                    {preview.newlyEffectivePermissions.map((permission) => <li key={permission}>+ {humanizePermission(permission)}</li>)}
                    {!preview.newlyEffectivePermissions.length ? <li>No new capability; access may already be effective.</li> : null}
                  </ul>
                </div>
                <div className="rounded-xl bg-slate-50 p-3">
                  <p className="text-xs font-black uppercase tracking-wide text-slate-600">Already available</p>
                  <ul className="mt-2 space-y-1 text-sm text-slate-700">
                    {preview.alreadyEffectivePermissions.map((permission) => <li key={permission}>{humanizePermission(permission)}</li>)}
                    {!preview.alreadyEffectivePermissions.length ? <li>None</li> : null}
                  </ul>
                </div>
              </div>
            </section>

            {hardening.conflicts.length ? <div className="rounded-2xl border border-rose-300 bg-rose-50 p-4 text-sm font-black text-rose-900">Blocked: {hardening.conflicts.join(' · ')}</div> : null}
            {[...preview.warnings, ...hardening.warnings].length ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{[...preview.warnings, ...hardening.warnings].join(' · ')}</div> : null}

            {hardening.approvalRequired ? (
              <section className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
                <h3 className="font-black text-amber-950">Two-person approval required</h3>
                <p className="mt-1 text-sm leading-6 text-amber-900">This is a high-risk assignment. The requester cannot approve their own change.</p>
                {matchingApprovals.length ? (
                  <div className="mt-3">
                    <FieldLabel htmlFor="access-approved-request" label="Approved request">
                      <SelectField id="access-approved-request" value={draft.approvalId ?? ''} onChange={(value) => setDraft((current) => ({ ...current, approvalId: value || null }))} options={matchingApprovals.map((approval) => ({ value: approval.approvalId, label: `Approved by ${approval.approverId}` }))} placeholder="Select an approved request" />
                    </FieldLabel>
                  </div>
                ) : (
                  <button type="button" disabled={busy || blocked} onClick={() => { void requestApproval(); }} className="mt-3 rounded-xl bg-amber-700 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Request approval</button>
                )}
              </section>
            ) : (
              <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-900">No additional approval is required.</div>
            )}

            <details className="rounded-2xl border border-slate-200 bg-white p-4">
              <summary className="cursor-pointer text-sm font-black text-slate-600">Technical evidence</summary>
              <p className="mt-3 break-all text-xs text-slate-500">Request hash: {hardening.requestHash}</p>
              <p className="mt-1 text-xs text-slate-500">Principal: {draft.principalType}:{draft.principalId}</p>
              <p className="mt-1 text-xs text-slate-500">Scope: {draft.scopeType}:{draft.scopeId}</p>
            </details>
          </div>
        ) : null}
      </div>

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
        <button type="button" onClick={step === 0 ? onCancel : () => { setStep((current) => Math.max(0, current - 1)); setError(''); }} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black text-slate-700">
          {step === 0 ? 'Cancel' : 'Back'}
        </button>
        {step < 4 ? (
          <button type="button" disabled={busy} onClick={() => { void next(); }} className="rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300">
            {step === 3 ? busy ? 'Reviewing…' : 'Review access' : 'Continue'}
          </button>
        ) : (
          <button type="button" disabled={busy || blocked || needsApproval} onClick={() => { void confirm(); }} className="rounded-xl bg-blue-700 px-5 py-2.5 text-sm font-black text-white disabled:bg-slate-300">
            {busy ? 'Assigning…' : 'Assign responsibility'}
          </button>
        )}
      </div>
    </section>
  );
}


function buildDepartmentHierarchyOptions(departments: Department[]) {
  const active = departments.filter((department) => department.status === 'ACTIVE');
  const byParent = new Map<string, Department[]>();
  for (const department of active) {
    const parent = department.parentDepartmentId || '';
    byParent.set(parent, [...(byParent.get(parent) ?? []), department]);
  }
  for (const children of byParent.values()) children.sort((left, right) => left.name.localeCompare(right.name));
  const output: Array<SelectOption & { depth?: number }> = [];
  const visited = new Set<string>();
  const walk = (parent: string, depth: number) => {
    for (const department of byParent.get(parent) ?? []) {
      if (visited.has(department.departmentId)) continue;
      visited.add(department.departmentId);
      output.push({ value: department.departmentId, label: department.name, description: department.code, depth });
      walk(department.departmentId, depth + 1);
    }
  };
  walk('', 0);
  for (const department of active) {
    if (!visited.has(department.departmentId)) output.push({ value: department.departmentId, label: department.name, description: department.code, depth: 0 });
  }
  return output;
}

function buildGroupHierarchyOptions(groups: Group[]) {
  const active = groups.filter((group) => group.status === 'ACTIVE');
  const byParent = new Map<string, Group[]>();
  for (const group of active) {
    const parent = group.parentGroupId || '';
    byParent.set(parent, [...(byParent.get(parent) ?? []), group]);
  }
  for (const children of byParent.values()) children.sort((left, right) => left.name.localeCompare(right.name));
  const output: Array<SelectOption & { depth?: number }> = [];
  const visited = new Set<string>();
  const walk = (parent: string, depth: number) => {
    for (const group of byParent.get(parent) ?? []) {
      if (visited.has(group.groupId)) continue;
      visited.add(group.groupId);
      output.push({ value: group.groupId, label: group.name, description: group.description || group.code, depth });
      walk(group.groupId, depth + 1);
    }
  };
  walk('', 0);
  for (const group of active) {
    if (!visited.has(group.groupId)) output.push({ value: group.groupId, label: group.name, description: group.description || group.code, depth: 0 });
  }
  return output;
}

function ReviewCard({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-2 text-sm font-black text-slate-950">{value}</p>
    </div>
  );
}
