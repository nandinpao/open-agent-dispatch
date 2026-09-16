'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed, featureAllowed } from '@/lib/navigation/uiEntitlements';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { Department, Group, RbacCriticalApproval, ResponsibilityTemplate, Role, ServiceAccount, User } from '@/lib/iam/types';
import { useAccessManagement } from '../AccessManagementProvider';
import { ErrorNotice, TenantRequired } from '../ui';
import { AssignAccessWizard, type AccessWizardInitialValue } from './AssignAccessWizard';
import { AccessAssignmentDirectory } from './AccessAssignmentDirectory';
import { AccessReviewPanel } from './AccessReviewPanel';
import { ApprovalQueue } from './ApprovalQueue';
import { EffectiveAccessExplorer } from './EffectiveAccessExplorer';
import { ResponsibilityTemplateCatalog } from './ResponsibilityTemplateCatalog';

type View = 'ASSIGNMENTS' | 'TEMPLATES' | 'APPROVALS' | 'REVIEWS' | 'EFFECTIVE';
const VIEWS: { value: View; label: string; description: string }[] = [
  { value: 'ASSIGNMENTS', label: 'Assignments', description: 'Who has which responsibility and where it applies.' },
  { value: 'TEMPLATES', label: 'Responsibilities', description: 'Business-facing templates and approved capabilities.' },
  { value: 'APPROVALS', label: 'Approvals', description: 'Independent decisions for high-risk changes.' },
  { value: 'REVIEWS', label: 'Reviews', description: 'Expiring, privileged and orphan access.' },
  { value: 'EFFECTIVE', label: 'Effective Access', description: 'Explain why a person can perform an action.' },
];

export function AccessWorkspace() {
  const entitlements = useUiEntitlements();
  const { scopeTenantId, tenants } = useAccessManagement();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [users, setUsers] = useState<User[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [templates, setTemplates] = useState<ResponsibilityTemplate[]>([]);
  const [serviceAccounts, setServiceAccounts] = useState<ServiceAccount[]>([]);
  const [approvals, setApprovals] = useState<RbacCriticalApproval[]>([]);
  const [showWizard, setShowWizard] = useState(false);
  const [wizardInitial, setWizardInitial] = useState<AccessWizardInitialValue>({});
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const tenantName = tenants.find((tenant) => tenant.tenantId === scopeTenantId)?.tenantName ?? scopeTenantId;
  const selectedView = normalizeView(searchParams.get('view'));
  const highlightedBindingId = searchParams.get('bindingId') ?? undefined;
  const initialUserId = searchParams.get('userId') ?? undefined;
  const canManage = actionAllowed(entitlements.value, 'access.assignment.manage');
  const canManageRole = actionAllowed(entitlements.value, 'access.role.manage');

  const routeInitial = useMemo<AccessWizardInitialValue>(() => ({
    principalType: searchParams.get('principalType') === 'DEPARTMENT' ? 'DEPARTMENT' : searchParams.get('principalType') === 'GROUP' ? 'GROUP' : searchParams.get('principalType') === 'SERVICE_ACCOUNT' ? 'SERVICE_ACCOUNT' : searchParams.get('principalType') === 'USER' ? 'USER' : undefined,
    principalId: searchParams.get('principalId') ?? undefined,
    roleId: searchParams.get('roleId') ?? undefined,
    roleCode: searchParams.get('roleCode') ?? undefined,
    scopeType: searchParams.get('scopeType') === 'DEPARTMENT_SUBTREE' ? 'DEPARTMENT_SUBTREE' : searchParams.get('scopeType') === 'DEPARTMENT' ? 'DEPARTMENT' : searchParams.get('scopeType') === 'GROUP' ? 'GROUP' : searchParams.get('scopeType') === 'TENANT' ? 'TENANT' : undefined,
    scopeId: searchParams.get('scopeId') ?? undefined,
  }), [searchParams]);

  const loadReferences = useCallback(async () => {
    if (!scopeTenantId) return;
    setLoading(true); setError('');
    try {
      const [userPage, groupPage, departmentPage, rolePage, templatePage, approvalList] = await Promise.all([
        accessManagementApi.tenantUsers(scopeTenantId, 250),
        accessManagementApi.groups(scopeTenantId, 0, 250),
        accessManagementApi.departments(scopeTenantId, 0, 250),
        accessManagementApi.roles(scopeTenantId, 0, 250, '', '', 'ACTIVE'),
        accessManagementApi.responsibilityTemplates(scopeTenantId, 0, 250, '', 'ACTIVE'),
        accessManagementApi.rbacApprovals(scopeTenantId, '', 250),
      ]);
      setUsers(userPage.items); setGroups(groupPage.items); setDepartments(departmentPage.items); setRoles(rolePage.items); setTemplates(templatePage.items); setApprovals(approvalList);
      try { const servicePage = await accessManagementApi.serviceAccounts(250); setServiceAccounts(servicePage.items.filter((item) => item.tenantId === scopeTenantId && item.status === 'ACTIVE')); }
      catch { setServiceAccounts([]); }
    } catch (cause) { setError(formatIamError(cause, 'Unable to load Access workspace reference data.')); }
    finally { setLoading(false); }
  }, [scopeTenantId]);
  useEffect(() => { void loadReferences(); }, [loadReferences, refreshKey]);
  useEffect(() => { if (routeInitial.principalId || routeInitial.roleId || routeInitial.roleCode || routeInitial.scopeId || searchParams.get('assign') === '1') { setWizardInitial(routeInitial); setShowWizard(true); } }, [routeInitial, searchParams]);

  function changeView(view: View, extras: Record<string, string | undefined> = {}) {
    const next = new URLSearchParams(searchParams.toString()); next.set('view', view);
    for (const [key, value] of Object.entries(extras)) { if (value) next.set(key, value); else next.delete(key); }
    router.replace(`${pathname}?${next.toString()}`);
  }
  function openAssignment(bindingId: string) { changeView('ASSIGNMENTS', { bindingId, userId: undefined }); }
  function startAssignment(initial: AccessWizardInitialValue = {}) { setWizardInitial(initial); setShowWizard(true); }
  function changed() { setRefreshKey((value) => value + 1); }

  if (!scopeTenantId) return <TenantRequired />;
  if (showWizard) return <AssignAccessWizard tenantId={scopeTenantId} tenantName={tenantName} users={users} groups={groups} departments={departments} roles={roles} templates={templates} serviceAccounts={serviceAccounts} approvals={approvals} initial={wizardInitial} onCancel={() => setShowWizard(false)} onComplete={async () => { setShowWizard(false); changed(); }} />;

  return <div className="space-y-5"><ErrorNotice message={error} />
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"><div><p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Access</p><h1 className="mt-1 text-2xl font-black text-slate-950">Responsibilities in {tenantName}</h1><p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Answer five business questions: who needs access, which responsibility, where it applies, how long it lasts, and whether another approver is required. Technical Role Binding and Permission codes stay under Technical details.</p><div className="mt-3 flex flex-wrap gap-3 text-xs font-black">{featureAllowed(entitlements.value, 'resource-access') ? <a href="/resource-access" className="text-blue-700 hover:underline">Manage resource-specific access →</a> : null}<button type="button" onClick={() => changeView('EFFECTIVE')} className="text-blue-700 hover:underline">Explain a person&apos;s access →</button></div></div>{canManage ? <button type="button" disabled={loading} onClick={() => startAssignment({})} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white disabled:bg-slate-300">Assign responsibility</button> : null}</div></section>
    <nav aria-label="Access workspace views" className="grid gap-2 rounded-3xl border border-slate-200 bg-white p-2 shadow-sm md:grid-cols-5">{VIEWS.map((item) => <button key={item.value} type="button" onClick={() => changeView(item.value, { bindingId: undefined })} className={`rounded-2xl p-3 text-left ${selectedView === item.value ? 'bg-slate-950 text-white' : 'hover:bg-slate-50'}`}><span className="block text-sm font-black">{item.label}</span><span className={`mt-1 block text-xs leading-5 ${selectedView === item.value ? 'text-slate-300' : 'text-slate-500'}`}>{item.description}</span></button>)}</nav>
    {selectedView === 'ASSIGNMENTS' ? <AccessAssignmentDirectory tenantId={scopeTenantId} highlightedBindingId={highlightedBindingId} canManage={canManage} refreshKey={refreshKey} onChanged={changed} onAssign={() => startAssignment({})} /> : null}
    {selectedView === 'TEMPLATES' ? <ResponsibilityTemplateCatalog tenantId={scopeTenantId} canAssign={canManage} canManageRole={canManageRole} canManagePermissions={actionAllowed(entitlements.value, 'access.role-permissions.manage')} canRequestApproval={actionAllowed(entitlements.value, 'access.approval.request')} autoCreate={searchParams.get('action') === 'ADD_RESPONSIBILITY'} onAssign={(template) => startAssignment({ roleId: template.roleId, roleCode: template.roleCode })} /> : null}
    {selectedView === 'APPROVALS' ? <ApprovalQueue tenantId={scopeTenantId} canApprove={actionAllowed(entitlements.value, 'access.approval.approve')} refreshKey={refreshKey} onChanged={changed} /> : null}
    {selectedView === 'REVIEWS' ? <AccessReviewPanel tenantId={scopeTenantId} refreshKey={refreshKey} onOpenAssignment={openAssignment} /> : null}
    {selectedView === 'EFFECTIVE' ? <EffectiveAccessExplorer tenantId={scopeTenantId} tenantName={tenantName} users={users} groups={groups} departments={departments} templates={templates} initialUserId={initialUserId} onOpenAssignment={openAssignment} /> : null}
  </div>;
}
function normalizeView(value: string | null): View { return value === 'TEMPLATES' || value === 'APPROVALS' || value === 'REVIEWS' || value === 'EFFECTIVE' ? value : 'ASSIGNMENTS'; }
