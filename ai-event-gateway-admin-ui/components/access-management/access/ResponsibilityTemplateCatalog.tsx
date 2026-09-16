'use client';

import type { FormEvent } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { Permission, RbacCriticalApproval, RbacHardeningPreview, ResponsibilityTemplate, ResponsibilityUiAccessPreview, Role } from '@/lib/iam/types';
import type { UiEntitlementResponse } from '@/lib/navigation/uiEntitlements';
import { EmptyState, ErrorNotice, SuccessNotice } from '../ui';
import {
  AuditReasonSelector,
  FieldLabel,
  HumanStatus,
  SearchField,
  SelectField,
  humanizePermission,
  isAuditReasonValid,
} from '../shared/beginnerUi';
import { WorkspaceModal } from '../shared/workspaceUi';

interface ResponsibilityEditorState {
  mode: 'CREATE' | 'EDIT';
  roleName: string;
  description: string;
  businessCode: string;
}

const DEFAULT_REASON = 'Responsibility updated after access governance review';

export function ResponsibilityTemplateCatalog({
  tenantId,
  canAssign,
  canManageRole,
  canManagePermissions,
  canRequestApproval,
  autoCreate = false,
  onAssign,
}: Readonly<{
  tenantId: string;
  canAssign: boolean;
  canManageRole: boolean;
  canManagePermissions: boolean;
  canRequestApproval: boolean;
  autoCreate?: boolean;
  onAssign: (template: ResponsibilityTemplate) => void;
}>) {

  const [roles, setRoles] = useState<Role[]>([]);
  const [templates, setTemplates] = useState<ResponsibilityTemplate[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [approvals, setApprovals] = useState<RbacCriticalApproval[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [selectedCodes, setSelectedCodes] = useState<Set<string>>(new Set());
  const [savedCodes, setSavedCodes] = useState<Set<string>>(new Set());
  const [text, setText] = useState('');
  const [risk, setRisk] = useState('');
  const [scope, setScope] = useState('');
  const [permissionSearch, setPermissionSearch] = useState('');
  const [editor, setEditor] = useState<ResponsibilityEditorState | null>(null);
  const [lifecycleOpen, setLifecycleOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [reason, setReason] = useState(DEFAULT_REASON);
  const [hardening, setHardening] = useState<RbacHardeningPreview | null>(null);
  const [uiPreview, setUiPreview] = useState<ResponsibilityUiAccessPreview | null>(null);
  const [draftUiPreview, setDraftUiPreview] = useState<ResponsibilityUiAccessPreview | null>(null);
  const [approvalId, setApprovalId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const autoCreateHandled = useRef(false);

  const templateByRole = useMemo(() => new Map(templates.map((template) => [template.roleId, template])), [templates]);
  const selected = roles.find((role) => role.roleId === selectedId) ?? null;
  const selectedTemplate = selected ? templateByRole.get(selected.roleId) ?? null : null;
  const dirtyPermissions = !sameSet(selectedCodes, savedCodes);

  useEffect(() => {
    if (!autoCreate || !canManageRole || autoCreateHandled.current) return;
    autoCreateHandled.current = true;
    setReason('Create a governed business responsibility for this Tenant');
    setEditor({ mode: 'CREATE', roleName: '', description: '', businessCode: '' });
  }, [autoCreate, canManageRole]);

  const filteredRoles = useMemo(() => roles.filter((role) => {
    const template = templateByRole.get(role.roleId);
    const haystack = `${role.roleName} ${role.description} ${role.roleCode}`.toLowerCase();
    if (text && !haystack.includes(text.toLowerCase())) return false;
    if (risk && (template?.riskLevel ?? 'STANDARD') !== risk) return false;
    if (scope && !(template?.allowedScopeTypes ?? []).includes(scope)) return false;
    return true;
  }), [risk, roles, scope, templateByRole, text]);

  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, Permission[]>();
    permissions
      .filter((permission) => !permissionSearch || `${permission.displayName} ${permission.description} ${permission.permissionCode}`.toLowerCase().includes(permissionSearch.toLowerCase()))
      .forEach((permission) => {
        const group = permissionDomain(permission.permissionCode);
        groups.set(group, [...(groups.get(group) ?? []), permission]);
      });
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [permissionSearch, permissions]);

  const matchingApprovals = approvals.filter((approval) => approval.status === 'APPROVED' && approval.requestHash === hardening?.requestHash);

  const load = useCallback(async (preferredRoleId?: string) => {
    setLoading(true);
    setError('');
    try {
      const [rolePage, templatePage, permissionPage, approvalPage] = await Promise.all([
        accessManagementApi.roles(tenantId, 0, 250, '', '', ''),
        accessManagementApi.responsibilityTemplates(tenantId, 0, 250, '', '', '', ''),
        accessManagementApi.permissions(tenantId, 0, 1000),
        accessManagementApi.rbacApprovals(tenantId, '', 250),
      ]);
      setRoles(rolePage.items);
      setTemplates(templatePage.items);
      setPermissions(permissionPage.items);
      setApprovals(approvalPage);
      setSelectedId((current) => {
        if (preferredRoleId && rolePage.items.some((role) => role.roleId === preferredRoleId)) return preferredRoleId;
        if (current && rolePage.items.some((role) => role.roleId === current)) return current;
        return rolePage.items[0]?.roleId ?? '';
      });
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load responsibilities and permission choices.'));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!selectedId) {
      setSelectedCodes(new Set());
      setSavedCodes(new Set());
      setUiPreview(null);
      setDraftUiPreview(null);
      return;
    }
    setError('');
    void Promise.all([
      accessManagementApi.rolePermissions(tenantId, selectedId),
      accessManagementApi.responsibilityUiAccessPreview(tenantId, selectedId),
    ])
      .then(([matrix, preview]) => {
        const codes = new Set(matrix.permissions.map((permission) => permission.permissionCode));
        setSelectedCodes(codes);
        setSavedCodes(new Set(codes));
        setUiPreview(preview);
        setDraftUiPreview(null);
        setHardening(null);
        setApprovalId(null);
      })
      .catch((cause) => setError(formatIamError(cause, 'Unable to load this responsibility’s permissions and access preview.')));
  }, [selectedId, tenantId]);

  async function execute(action: () => Promise<unknown>, success: string, preferredRoleId?: string) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
      setNotice(success);
      await load(preferredRoleId);
    } catch (cause) {
      setError(formatIamError(cause, 'Responsibility action failed.'));
    } finally {
      setBusy(false);
    }
  }

  async function saveEditor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editor) return;
    const name = editor.roleName.trim();
    const description = editor.description.trim();
    if (!name) { setError('Enter a responsibility name.'); return; }
    if (!isAuditReasonValid(reason, 'ELEVATED')) { setError('Choose a governance reason or enter a specific business reason.'); return; }

    if (editor.mode === 'CREATE') {
      const roleCode = normalizeBusinessCode(editor.businessCode || name);
      setBusy(true);
      setError('');
      setNotice('');
      try {
        const created = await accessManagementApi.createRole(tenantId, {
          roleId: null,
          roleCode,
          roleName: name,
          description,
        }, reason);
        setEditor(null);
        setNotice('Responsibility created. Choose its permissions before assigning it.');
        await load(created.roleId);
      } catch (cause) {
        setError(formatIamError(cause, 'Unable to create this responsibility.'));
      } finally {
        setBusy(false);
      }
      return;
    }
    if (!selected) return;
    await execute(() => accessManagementApi.updateRole(tenantId, selected.roleId, {
      roleName: name,
      description,
    }, selected.version, reason), 'Responsibility details updated.', selected.roleId);
    setEditor(null);
  }

  async function changeLifecycle() {
    if (!selected) return;
    const next = selected.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    if (!isAuditReasonValid(reason, 'ELEVATED')) { setError('Choose an approved lifecycle reason.'); return; }
    await execute(() => accessManagementApi.changeRoleStatus(tenantId, selected.roleId, next, selected.version, reason), `Responsibility ${next === 'ACTIVE' ? 'activated' : 'disabled'}. Existing evidence remains available for audit.`, selected.roleId);
    setLifecycleOpen(false);
  }

  async function deleteResponsibility() {
    if (!selected || selected.systemManaged) return;
    if ((selectedTemplate?.activeAssignmentCount ?? 0) > 0) {
      setError('Revoke active assignments before deleting this responsibility.');
      return;
    }
    if (!isAuditReasonValid(reason, 'HIGH_RISK')) { setError('Select a destructive-action category and enter the specific delete justification.'); return; }
    await execute(() => accessManagementApi.changeRoleStatus(tenantId, selected.roleId, 'DELETED', selected.version, reason), 'Responsibility deleted from active administration. Historical permission and assignment evidence remains available for audit.');
    setDeleteOpen(false);
  }

  async function previewPermissions() {
    if (!selected) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const [preview, accessPreview] = await Promise.all([
        accessManagementApi.previewRolePermissionHardening(tenantId, selected.roleId, [...selectedCodes], approvalId),
        accessManagementApi.previewResponsibilityUiAccessDraft(tenantId, selected.roleId, [...selectedCodes]),
      ]);
      setHardening(preview);
      setDraftUiPreview(accessPreview);
      const approved = approvals.find((approval) => approval.status === 'APPROVED' && approval.requestHash === preview.requestHash);
      if (approved) setApprovalId(approved.approvalId);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to review this permission change.'));
    } finally {
      setBusy(false);
    }
  }

  async function requestApproval() {
    if (!selected) return;
    if (!isAuditReasonValid(reason, 'HIGH_RISK')) { setError('Enter the specific justification for requesting independent approval.'); return; }
    await execute(async () => {
      const approval = await accessManagementApi.requestRolePermissionApproval(tenantId, selected.roleId, [...selectedCodes], reason);
      setApprovalId(null);
      setNotice(`Independent approval requested (${approval.approvalId}). Another authorized approver must approve it before this permission change can be saved.`);
    }, 'Independent approval requested. Open Approvals in this Access workspace for the second-person decision.', selected.roleId);
    setHardening(null);
  }

  async function savePermissions() {
    if (!selected || !hardening) return;
    if (!isAuditReasonValid(reason, 'ELEVATED')) { setError('Choose a governance reason or enter a specific business reason.'); return; }
    if (hardening.conflicts.length) { setError('Resolve the blocked permission conflicts before saving.'); return; }
    if (hardening.approvalRequired && !approvalId) { setError('Attach an approved independent request before saving this critical permission change.'); return; }
    await execute(() => accessManagementApi.replaceRolePermissions(tenantId, selected.roleId, [...selectedCodes], selected.version, reason, approvalId), 'Permission matrix saved. Effective Access will use the updated responsibility immediately.', selected.roleId);
    setSavedCodes(new Set(selectedCodes));
    setUiPreview(await accessManagementApi.responsibilityUiAccessPreview(tenantId, selected.roleId));
    setHardening(null);
    setDraftUiPreview(null);
    setApprovalId(null);
  }

  function resetPermissionDraft() {
    setSelectedCodes(new Set(savedCodes));
    setHardening(null);
    setDraftUiPreview(null);
    setApprovalId(null);
    setError('');
  }

  return (
    <div className="space-y-5">
      <ErrorNotice message={error} />
      <SuccessNotice message={notice} />

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="grid flex-1 gap-3 md:grid-cols-[minmax(240px,1fr)_180px_220px]">
            <div>
              <label htmlFor="responsibility-search" className="mb-1.5 block text-sm font-black text-slate-800">Search responsibilities</label>
              <SearchField id="responsibility-search" value={text} onChange={setText} placeholder="Search responsibility name or purpose" />
            </div>
            <div>
              <label htmlFor="responsibility-risk" className="mb-1.5 block text-sm font-black text-slate-800">Risk</label>
              <SelectField id="responsibility-risk" value={risk} onChange={setRisk} options={[
                { value: 'LOW', label: 'Low' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'HIGH', label: 'High' }, { value: 'CRITICAL', label: 'Critical' },
              ]} placeholder="All risk levels" />
            </div>
            <div>
              <label htmlFor="responsibility-scope" className="mb-1.5 block text-sm font-black text-slate-800">Can apply to</label>
              <SelectField id="responsibility-scope" value={scope} onChange={setScope} options={[
                { value: 'TENANT', label: 'Entire Tenant' },
                { value: 'DEPARTMENT', label: 'One Department' },
                { value: 'DEPARTMENT_SUBTREE', label: 'Department and children' },
                { value: 'GROUP', label: 'One Group' },
              ]} placeholder="All supported scopes" />
            </div>
          </div>
          {canManageRole ? <button type="button" onClick={() => { setReason('Create a governed business responsibility for this Tenant'); setEditor({ mode: 'CREATE', roleName: '', description: '', businessCode: '' }); }} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Create responsibility</button> : null}
        </div>
      </section>

      <div className="grid gap-5 xl:grid-cols-[minmax(300px,.72fr)_minmax(0,1.28fr)]">
        <section className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between gap-3"><div><h2 className="font-black text-slate-950">Responsibilities</h2><p className="text-xs text-slate-500">Choose one to manage it here.</p></div><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-black text-slate-600">{filteredRoles.length}</span></div>
          {loading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Loading responsibilities…</div> : null}
          {!loading && !filteredRoles.length ? <EmptyState>No responsibility matches the selected filters.</EmptyState> : null}
          <div className="space-y-2">
            {filteredRoles.map((role) => {
              const template = templateByRole.get(role.roleId);
              return <button key={role.roleId} type="button" onClick={() => setSelectedId(role.roleId)} className={`w-full rounded-2xl border p-4 text-left transition ${selectedId === role.roleId ? 'border-blue-300 bg-blue-50 ring-2 ring-blue-100' : 'border-slate-200 hover:bg-slate-50'}`}>
                <div className="flex items-start justify-between gap-3"><div><p className="font-black text-slate-950">{role.roleName}</p><p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">{role.description || 'No business description has been provided.'}</p></div><HumanStatus value={role.status} /></div>
                <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                  <span>{template?.permissionCount ?? 0} capabilities</span><span>•</span><span>{template?.activeAssignmentCount ?? 0} active assignments</span>{template?.riskLevel ? <><span>•</span><span>{template.riskLevel} risk</span></> : null}
                </div>
              </button>;
            })}
          </div>
        </section>

        {!selected ? <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm"><EmptyState>Select a responsibility to view its permissions, assignments and lifecycle.</EmptyState></section> : (
          <div className="space-y-5">
            <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div><p className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Business responsibility</p><h2 className="mt-1 text-xl font-black text-slate-950">{selected.roleName}</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{selected.description || 'Describe what this responsibility is for so administrators can assign it confidently.'}</p></div>
                <div className="flex flex-wrap gap-2"><HumanStatus value={selected.status} /><RiskPill value={selectedTemplate?.riskLevel ?? 'STANDARD'} /></div>
              </div>

              <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <Metric label="Capabilities" value={selectedTemplate?.permissionCount ?? selectedCodes.size} />
                <Metric label="Active assignments" value={selectedTemplate?.activeAssignmentCount ?? 0} />
                <Metric label="Expiring in 30 days" value={selectedTemplate?.expiringAssignmentCount ?? 0} />
                <Metric label="Review" value={selectedTemplate?.reviewRequired ? 'Required' : 'Standard'} />
              </div>

              <div className="mt-5">
                <p className="text-xs font-black uppercase tracking-wide text-slate-500">Can apply to</p>
                <div className="mt-2 flex flex-wrap gap-2">{selectedTemplate?.allowedScopeTypes.map((item) => <span key={item} className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-black text-slate-700">{scopeLabel(item)}</span>)}{!selectedTemplate?.allowedScopeTypes.length ? <span className="text-xs font-bold text-amber-800">Choose at least one capability before this responsibility can be assigned.</span> : null}</div>
              </div>

              <div className="mt-5 flex flex-wrap gap-2">
                {canAssign && selected.status === 'ACTIVE' && selectedTemplate && selectedTemplate.permissionCount > 0 && selectedTemplate.allowedScopeTypes.length > 0 ? <button type="button" onClick={() => onAssign(selectedTemplate)} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Assign responsibility</button> : null}
                {canManageRole && !selected.systemManaged ? <button type="button" onClick={() => { setReason(DEFAULT_REASON); setEditor({ mode: 'EDIT', roleName: selected.roleName, description: selected.description, businessCode: selected.roleCode }); }} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black text-slate-700">Edit details</button> : null}
                {canManageRole && !selected.systemManaged ? <button type="button" onClick={() => { setReason(`Responsibility ${selected.roleName} lifecycle changed after access review`); setLifecycleOpen(true); }} className="rounded-xl border border-amber-300 px-4 py-2.5 text-sm font-black text-amber-900">{selected.status === 'ACTIVE' ? 'Disable' : 'Activate'}</button> : null}
                {canManageRole && !selected.systemManaged ? <button type="button" onClick={() => { setReason(''); setDeleteOpen(true); }} className="rounded-xl border border-rose-300 px-4 py-2.5 text-sm font-black text-rose-800">Delete…</button> : null}
              </div>

              <details className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-600"><summary className="cursor-pointer font-black text-slate-800">Technical details</summary><dl className="mt-3 grid gap-2 sm:grid-cols-2"><div><dt className="font-bold">Role code</dt><dd>{selected.roleCode}</dd></div><div><dt className="font-bold">Role ID</dt><dd className="break-all">{selected.roleId}</dd></div><div><dt className="font-bold">Type</dt><dd>{selected.roleType}</dd></div><div><dt className="font-bold">Version</dt><dd>{selected.version}</dd></div></dl></details>
            </section>

            {uiPreview ? <ResponsibilityAccessPreviewPanel preview={uiPreview} /> : null}

            <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between"><div><h3 className="text-lg font-black text-slate-950">Permission matrix</h3><p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">Select capabilities by business name. Permission codes remain available only inside Technical details for audit and support.</p></div>{dirtyPermissions ? <span className="rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-black text-amber-900">Unsaved changes</span> : null}</div>
              <div className="mt-4"><SearchField id="permission-search" value={permissionSearch} onChange={setPermissionSearch} placeholder="Search capabilities, such as Tasks, People or Dispatch" /></div>
              <div className="mt-4 space-y-4">
                {groupedPermissions.map(([group, items]) => <fieldset key={group} className="rounded-2xl border border-slate-200 p-4"><legend className="px-2 text-xs font-black uppercase tracking-[.14em] text-blue-700">{humanizeDomain(group)}</legend><div className="grid gap-2 xl:grid-cols-2">{items.map((permission) => {
                  const checked = selectedCodes.has(permission.permissionCode);
                  return <label key={permission.permissionCode} className={`flex cursor-pointer gap-3 rounded-xl border p-3 ${checked ? 'border-blue-200 bg-blue-50' : 'border-transparent hover:bg-slate-50'}`}><input type="checkbox" checked={checked} disabled={!canManagePermissions || selected.systemManaged} onChange={(event) => { setSelectedCodes((current) => { const next = new Set(current); if (event.target.checked) next.add(permission.permissionCode); else next.delete(permission.permissionCode); return next; }); setHardening(null); setDraftUiPreview(null); setApprovalId(null); }} className="mt-1 h-4 w-4"/><span className="min-w-0"><b className="block text-sm text-slate-900">{permissionBusinessName(permission)}</b>{permission.description ? <span className="mt-1 block text-xs leading-5 text-slate-600">{permission.description}</span> : null}<span className="mt-2 flex flex-wrap gap-1">{permission.allowedScopeTypes.map((allowed) => <span key={allowed} className="rounded-full bg-white px-2 py-0.5 text-xs font-bold text-slate-500 ring-1 ring-slate-200">{scopeLabel(allowed)}</span>)}</span><details className="mt-2 text-xs text-slate-500"><summary className="cursor-pointer font-bold">Technical details</summary><span className="mt-1 block break-all">{permission.permissionCode}</span></details></span></label>;
                })}</div></fieldset>)}
              </div>

              {dirtyPermissions && canManagePermissions && !selected.systemManaged ? <div className="mt-5 space-y-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <AuditReasonSelector idPrefix="responsibility-permissions" value={reason} onChange={setReason} tier="ELEVATED" />
                {!hardening ? <div className="flex flex-wrap gap-2"><button type="button" disabled={busy} onClick={() => { void previewPermissions(); }} className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Review permission changes</button><button type="button" onClick={resetPermissionDraft} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black text-slate-700">Discard changes</button></div> : null}
                {hardening ? <HardeningReview preview={hardening} /> : null}
                {hardening && uiPreview && draftUiPreview ? <UiAccessImpactReview before={uiPreview.uiAccess} after={draftUiPreview.uiAccess} /> : null}
                {hardening?.approvalRequired ? <div className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-4"><p className="text-sm font-black text-amber-950">Independent approval required</p><p className="text-xs leading-5 text-amber-900">A different authorized approver must approve this exact permission change. This prevents one person from granting a critical responsibility alone.</p>{matchingApprovals.length ? <FieldLabel htmlFor="responsibility-approved-request" label="Approved request"><SelectField id="responsibility-approved-request" value={approvalId ?? ''} onChange={(value) => setApprovalId(value || null)} options={matchingApprovals.map((approval) => ({ value: approval.approvalId, label: `Approved by ${approval.approverId}`, description: new Date(approval.decidedAt ?? approval.requestedAt).toLocaleString() }))} placeholder="Select an approved request" /></FieldLabel> : canRequestApproval ? <button type="button" disabled={busy || hardening.conflicts.length > 0} onClick={() => { void requestApproval(); }} className="rounded-xl bg-amber-700 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Request independent approval</button> : <p className="text-xs font-bold text-amber-900">Ask an authorized access owner to request approval.</p>}</div> : null}
                {hardening ? <div className="flex flex-wrap gap-2"><button type="button" disabled={busy || hardening.conflicts.length > 0 || (hardening.approvalRequired && !approvalId)} onClick={() => { void savePermissions(); }} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Save permission matrix</button><button type="button" onClick={() => { setHardening(null); setDraftUiPreview(null); }} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black text-slate-700">Back to editing</button></div> : null}
              </div> : null}
            </section>
          </div>
        )}
      </div>

      <WorkspaceModal open={Boolean(editor)} title={editor?.mode === 'CREATE' ? 'Create responsibility' : 'Edit responsibility'} description="Use a business name and purpose. OpenDispatch manages the internal identity and permission evidence." onClose={() => setEditor(null)}>
        {editor ? <form onSubmit={saveEditor} className="space-y-4">
          <FieldLabel htmlFor="responsibility-name" label="Responsibility name" help="Use the job or operational responsibility administrators recognize." required><input id="responsibility-name" value={editor.roleName} onChange={(event) => setEditor({ ...editor, roleName: event.target.value })} required placeholder="For example: Finance Reviewer" className="w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel>
          <FieldLabel htmlFor="responsibility-description" label="Purpose" help="Explain when this responsibility should be assigned."><textarea id="responsibility-description" value={editor.description} onChange={(event) => setEditor({ ...editor, description: event.target.value })} placeholder="Review finance tasks and approve routine finance work." rows={4} className="w-full rounded-xl border border-slate-300 px-3 py-2.5" /></FieldLabel>
          {editor.mode === 'CREATE' ? <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><summary className="cursor-pointer text-sm font-black text-slate-800">Advanced business code</summary><p className="mt-2 text-xs leading-5 text-slate-600">Usually leave this blank. OpenDispatch generates a stable code from the responsibility name.</p><input value={editor.businessCode} onChange={(event) => setEditor({ ...editor, businessCode: event.target.value })} placeholder={normalizeBusinessCode(editor.roleName || 'Finance Reviewer')} className="mt-3 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5" /></details> : null}
          <AuditReasonSelector idPrefix="responsibility-editor" value={reason} onChange={setReason} tier="ELEVATED" />
          <button disabled={busy} className="w-full rounded-xl bg-blue-700 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">{editor.mode === 'CREATE' ? 'Create responsibility' : 'Save changes'}</button>
        </form> : null}
      </WorkspaceModal>

      <WorkspaceModal open={lifecycleOpen} title={selected?.status === 'ACTIVE' ? 'Disable responsibility' : 'Activate responsibility'} description={selected?.status === 'ACTIVE' ? 'Disabling prevents new assignments while preserving historical evidence. Review existing assignments separately.' : 'Activating makes the responsibility available for new assignments.'} onClose={() => setLifecycleOpen(false)}>
        <div className="space-y-4"><div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b>{selected?.roleName}</b> {selected?.status === 'ACTIVE' ? 'will no longer be offered for new access assignments.' : 'will become available for access assignments.'}</div><AuditReasonSelector idPrefix="responsibility-lifecycle" value={reason} onChange={setReason} tier="ELEVATED" /><button type="button" disabled={busy} onClick={() => { void changeLifecycle(); }} className="w-full rounded-xl bg-amber-700 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">Confirm lifecycle change</button></div>
      </WorkspaceModal>

      <WorkspaceModal open={deleteOpen} title={`Delete ${selected?.roleName ?? 'responsibility'}`} description="Delete removes this responsibility from normal assignment and administration while keeping historical audit evidence." onClose={() => setDeleteOpen(false)}>
        <div className="space-y-4">
          <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm leading-6 text-rose-950"><b className="block">Before deleting</b><p className="mt-1">Revoke all active assignments first. System-managed responsibilities cannot be deleted.</p><p className="mt-2 font-bold">Active assignments: {selectedTemplate?.activeAssignmentCount ?? 0}</p></div>
          <AuditReasonSelector idPrefix="responsibility-delete" value={reason} onChange={setReason} tier="HIGH_RISK" />
          <button type="button" disabled={busy || (selectedTemplate?.activeAssignmentCount ?? 0) > 0 || !isAuditReasonValid(reason, 'HIGH_RISK')} onClick={() => { void deleteResponsibility(); }} className="w-full rounded-xl bg-rose-700 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">Delete responsibility</button>
        </div>
      </WorkspaceModal>
    </div>
  );
}

function ResponsibilityAccessPreviewPanel({ preview }: Readonly<{ preview: ResponsibilityUiAccessPreview }>) {
  const pages = Object.values(preview.uiAccess.pages).filter((page) => page.displayMode !== 'HIDDEN');
  const actions = Object.values(preview.uiAccess.actionEntitlements).filter((action) => action.displayMode === 'ENABLED');
  const labels = entitlementLabels(preview.uiAccess);
  return <section className="rounded-3xl border border-emerald-200 bg-emerald-50/40 p-5 shadow-sm"><div><p className="text-xs font-black uppercase tracking-[.16em] text-emerald-800">Access preview</p><h3 className="mt-1 text-lg font-black text-slate-950">What a person receives from this responsibility</h3><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Generated by the same backend entitlement registry used after sign-in. This preview shows this responsibility alone; a person may receive additional access from other direct, Department or Group assignments.</p></div><div className="mt-5 grid gap-4 lg:grid-cols-2"><div className="rounded-2xl border border-emerald-200 bg-white p-4"><p className="text-sm font-black text-slate-900">Navigator and pages</p><div className="mt-3 space-y-2">{pages.map((page) => <PreviewLine key={page.featureId} label={labels.get(page.featureId) ?? businessUiName(page.featureId)} mode={page.displayMode} />)}{!pages.length ? <p className="text-sm text-slate-500">This responsibility does not expose a normal Tenant page.</p> : null}</div></div><div className="rounded-2xl border border-emerald-200 bg-white p-4"><p className="text-sm font-black text-slate-900">Functions</p><div className="mt-3 space-y-2">{actions.map((action) => <PreviewLine key={action.actionId} label={businessUiName(action.actionId.replaceAll('.', '-'))} mode="ENABLED" />)}{!actions.length ? <p className="text-sm text-slate-500">No write function is enabled; visible pages are read-only.</p> : null}</div></div></div></section>;
}

function UiAccessImpactReview({ before, after }: Readonly<{ before: UiEntitlementResponse; after: UiEntitlementResponse }>) {
  const beforePages = new Map(Object.values(before.pages).map((page) => [page.featureId, page.displayMode]));
  const afterPages = new Map(Object.values(after.pages).map((page) => [page.featureId, page.displayMode]));
  const beforeActions = new Set(Object.values(before.actionEntitlements).filter((item) => item.displayMode === 'ENABLED').map((item) => item.actionId));
  const afterActions = new Set(Object.values(after.actionEntitlements).filter((item) => item.displayMode === 'ENABLED').map((item) => item.actionId));
  const addedPages = [...afterPages].filter(([id, mode]) => mode !== 'HIDDEN' && beforePages.get(id) === 'HIDDEN').map(([id]) => id);
  const removedPages = [...beforePages].filter(([id, mode]) => mode !== 'HIDDEN' && afterPages.get(id) === 'HIDDEN').map(([id]) => id);
  const changedPages = [...afterPages].filter(([id, mode]) => beforePages.has(id) && beforePages.get(id) !== mode && beforePages.get(id) !== 'HIDDEN' && mode !== 'HIDDEN').map(([id, mode]) => `${id}: ${beforePages.get(id)} → ${mode}`);
  const addedActions = [...afterActions].filter((id) => !beforeActions.has(id));
  const removedActions = [...beforeActions].filter((id) => !afterActions.has(id));
  return <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4"><p className="text-sm font-black text-emerald-950">UI access impact</p><p className="mt-1 text-xs leading-5 text-emerald-900">This is the projected Navigator/Page/Function impact before saving the permission matrix.</p><div className="mt-3 grid gap-3 sm:grid-cols-2"><ImpactList title="UI access added" values={[...addedPages, ...changedPages, ...addedActions].map(businessUiName)} empty="No UI access will be added." /><ImpactList title="UI access removed" values={[...removedPages, ...removedActions].map(businessUiName)} empty="No UI access will be removed." /></div></div>;
}

function PreviewLine({ label, mode }: Readonly<{ label: string; mode: string }>) {
  return <div className="flex items-center justify-between gap-3 rounded-xl bg-slate-50 px-3 py-2"><span className="text-sm font-bold text-slate-800">{label}</span><span className={`rounded-full px-2 py-0.5 text-xs font-black ${mode === 'ENABLED' ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'}`}>{mode === 'ENABLED' ? 'Manage' : 'Read only'}</span></div>;
}

function entitlementLabels(value: UiEntitlementResponse): Map<string, string> {
  const result = new Map<string, string>();
  const visit = (items: typeof value.navigation) => items.forEach((item) => { result.set(item.featureId, item.label); visit(item.children); });
  visit(value.navigation);
  return result;
}
function businessUiName(value: string): string { return value.split(/[-.]/).filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join(' › '); }

function HardeningReview({ preview }: Readonly<{ preview: RbacHardeningPreview }>) {
  return <div className={`rounded-xl border p-4 ${preview.conflicts.length ? 'border-rose-200 bg-rose-50' : 'border-blue-200 bg-blue-50'}`}><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-black text-slate-950">Permission impact review</p>{preview.critical ? <span className="rounded-full bg-rose-100 px-2 py-1 text-xs font-black text-rose-800">Critical change</span> : null}</div><div className="mt-3 grid gap-3 sm:grid-cols-2"><ImpactList title="Capabilities added" values={preview.addedPermissions} empty="No capability will be added." /><ImpactList title="Capabilities removed" values={preview.removedPermissions} empty="No capability will be removed." /></div>{preview.warnings.length ? <div className="mt-3 rounded-xl bg-white/70 p-3 text-xs text-slate-700"><b>Review notes</b><ul className="mt-1 space-y-1">{preview.warnings.map((warning) => <li key={warning}>• {warning}</li>)}</ul></div> : null}{preview.conflicts.length ? <div className="mt-3 rounded-xl border border-rose-200 bg-white p-3 text-xs font-bold text-rose-800"><p>Blocked conflicts</p><ul className="mt-1 space-y-1">{preview.conflicts.map((conflict) => <li key={conflict}>• {conflict}</li>)}</ul></div> : null}<details className="mt-3 text-xs text-slate-500"><summary className="cursor-pointer font-bold">Technical evidence</summary><span className="mt-1 block break-all">Request hash: {preview.requestHash}</span></details></div>;
}

function ImpactList({ title, values, empty }: Readonly<{ title: string; values: string[]; empty: string }>) {
  return <div className="rounded-xl bg-white/70 p-3"><p className="text-xs font-black text-slate-800">{title}</p><ul className="mt-2 space-y-1 text-xs text-slate-600">{values.map((value) => <li key={value}>• {humanizePermission(value)}</li>)}{!values.length ? <li>{empty}</li> : null}</ul></div>;
}

function Metric({ label, value }: Readonly<{ label: string; value: string | number }>) {
  return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3"><p className="text-xs font-bold text-slate-500">{label}</p><p className="mt-1 text-lg font-black text-slate-950">{value}</p></div>;
}

function RiskPill({ value }: Readonly<{ value: string }>) {
  const normalized = value.toUpperCase();
  const style = normalized === 'CRITICAL' ? 'border-rose-200 bg-rose-50 text-rose-800' : normalized === 'HIGH' ? 'border-amber-200 bg-amber-50 text-amber-900' : 'border-slate-200 bg-slate-100 text-slate-700';
  return <span className={`rounded-full border px-2.5 py-1 text-xs font-black ${style}`}>{normalized || 'STANDARD'} risk</span>;
}

function scopeLabel(scope: string): string {
  if (scope === 'INSTANCE') return 'Platform';
  if (scope === 'TENANT') return 'Entire Tenant';
  if (scope === 'DEPARTMENT') return 'One Department';
  if (scope === 'DEPARTMENT_SUBTREE') return 'Department and children';
  if (scope === 'GROUP') return 'One Group';
  return scope;
}

function permissionBusinessName(permission: Permission): string {
  const displayName = permission.displayName?.trim();
  return displayName && displayName !== permission.permissionCode ? displayName : humanizePermission(permission.permissionCode);
}
function permissionDomain(code: string): string { return code.split('.')[0] || 'other'; }
function humanizeDomain(value: string): string { return value.replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()); }
function sameSet(left: Set<string>, right: Set<string>): boolean { return left.size === right.size && [...left].every((value) => right.has(value)); }
function normalizeBusinessCode(value: string): string {
  const normalized = value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 80);
  return normalized || `CUSTOM_RESPONSIBILITY_${Date.now().toString(36).toUpperCase()}`;
}
