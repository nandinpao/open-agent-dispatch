'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed, actionAllowedForScope, featureAllowed } from '@/lib/navigation/uiEntitlements';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { ApiError } from '@/lib/api/errors';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { Department, Group, IdentityAudit, Membership, OrganizationRetirementPreview, PeopleBulkActionResult, Role, RoleBinding, User } from '@/lib/iam/types';
import { useAccessManagement } from '../AccessManagementProvider';
import { isAuditReasonValid } from '../shared/beginnerUi';
import { departmentAncestorIds, fromLocalDateTime, generateUniqueCode, hasCollapsedDepartmentAncestor, hasCollapsedGroupAncestor, hierarchyDepartmentOptions, hierarchyGroupOptions, isDepartmentDescendant, isGroupDescendant, countDepartmentDescendants, toLocalDateTime, humanize } from './organizationWorkspaceModel';

export type OrganizationType = 'DEPARTMENT' | 'GROUP';
export type OrganizationTab = 'OVERVIEW' | 'MEMBERS' | 'ACCESS' | 'AUDIT';
export type OrganizationDialog =
  | 'CREATE_DEPARTMENT'
  | 'CREATE_GROUP'
  | 'EDIT'
  | 'MOVE'
  | 'MANAGER'
  | 'ADD_MEMBERS'
  | 'MEMBER'
  | 'LIFECYCLE'
  | 'DELETE'
  | null;

export interface Selection {
  type: OrganizationType;
  id: string;
}

export function useOrganizationWorkspaceController() {
  const entitlements = useUiEntitlements();
  const { scopeTenantId, tenants } = useAccessManagement();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [people, setPeople] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [bindings, setBindings] = useState<RoleBinding[]>([]);
  const [members, setMembers] = useState<Membership[]>([]);
  const [audit, setAudit] = useState<IdentityAudit[]>([]);
  const [eligibleManagers, setEligibleManagers] = useState<User[]>([]);
  const [selection, setSelection] = useState<Selection | null>(() => {
    const type = searchParams.get('type');
    const id = searchParams.get('id');
    return id && (type === 'DEPARTMENT' || type === 'GROUP') ? { type, id } : null;
  });
  const [tab, setTab] = useState<OrganizationTab>(() => {
    const value = searchParams.get('tab');
    return value === 'MEMBERS' || value === 'ACCESS' || value === 'AUDIT' ? value : 'OVERVIEW';
  });
  const [treeSearch, setTreeSearch] = useState('');
  const [memberSearch, setMemberSearch] = useState('');
  const [collapsedDepartments, setCollapsedDepartments] = useState<string[]>([]);
  const [collapsedGroups, setCollapsedGroups] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [recoveryErrorCode, setRecoveryErrorCode] = useState('');
  const [notice, setNotice] = useState('');
  const [dialog, setDialog] = useState<OrganizationDialog>(null);
  const [retirementPreview, setRetirementPreview] = useState<OrganizationRetirementPreview | null>(null);
  const [retirementLoading, setRetirementLoading] = useState(false);

  // Beginner-facing dialog state. Internal IDs are selected, never manually typed.
  const [draftName, setDraftName] = useState('');
  const [draftCode, setDraftCode] = useState('');
  const [draftParentId, setDraftParentId] = useState('');
  const [draftOwnerDepartmentId, setDraftOwnerDepartmentId] = useState('');
  const [draftGroupType, setDraftGroupType] = useState('GENERAL');
  const [draftDescription, setDraftDescription] = useState('');
  const [draftStatus, setDraftStatus] = useState('ACTIVE');
  const [selectedPeople, setSelectedPeople] = useState<string[]>([]);
  const [memberCandidates, setMemberCandidates] = useState<User[]>([]);
  const [memberCandidateCursor, setMemberCandidateCursor] = useState('');
  const [memberCandidateHasMore, setMemberCandidateHasMore] = useState(false);
  const [memberSearchLoading, setMemberSearchLoading] = useState(false);
  const [memberBulkResult, setMemberBulkResult] = useState<PeopleBulkActionResult | null>(null);
  const [memberRole, setMemberRole] = useState('MEMBER');
  const [memberExpiresAt, setMemberExpiresAt] = useState('');
  const [selectedMembershipId, setSelectedMembershipId] = useState('');
  const [managerUserId, setManagerUserId] = useState('');
  const [grantManagerAccess, setGrantManagerAccess] = useState(true);
  const [reason, setReason] = useState('Job responsibility changed');
  const routeActionHandled = useRef(false);
  const memberSearchSequence = useRef(0);

  const tenantName = tenants.find((tenant) => tenant.tenantId === scopeTenantId)?.tenantName ?? scopeTenantId;
  const selectedDepartment = selection?.type === 'DEPARTMENT'
    ? departments.find((department) => department.departmentId === selection.id) ?? null
    : null;
  const selectedGroup = selection?.type === 'GROUP'
    ? groups.find((group) => group.groupId === selection.id) ?? null
    : null;
  const selectedName = selectedDepartment?.name ?? selectedGroup?.name ?? '';
  const selectedStatus = selectedDepartment?.status ?? selectedGroup?.status ?? '';
  const selectedMembership = members.find((item) => item.membershipId === selectedMembershipId) ?? null;

  const departmentOptions = useMemo(() => hierarchyDepartmentOptions(departments), [departments]);
  const groupOptions = useMemo(() => hierarchyGroupOptions(groups), [groups]);
  const manageableDepartmentOptions = useMemo(() => departmentOptions.filter((option) => actionAllowedForScope(
    entitlements.value, 'access.department.manage', 'DEPARTMENT', option.value, departmentAncestorIds(option.value, departments),
  )), [departmentOptions, departments, entitlements.value]);
  const manageableGroupOptions = useMemo(() => groupOptions.filter((option) => actionAllowedForScope(
    entitlements.value, 'access.group.manage', 'GROUP', option.value,
  )), [entitlements.value, groupOptions]);

  const canManageDepartment = actionAllowed(entitlements.value, 'access.department.manage');
  const canManageGroup = actionAllowed(entitlements.value, 'access.group.manage');
  const canManageDepartmentTenant = actionAllowedForScope(entitlements.value, 'access.department.manage', 'TENANT', scopeTenantId);
  const canManageGroupTenant = actionAllowedForScope(entitlements.value, 'access.group.manage', 'TENANT', scopeTenantId);
  const canManageSelectedDepartment = selectedDepartment
    ? actionAllowedForScope(entitlements.value, 'access.department.manage', 'DEPARTMENT', selectedDepartment.departmentId, departmentAncestorIds(selectedDepartment.departmentId, departments))
    : false;
  const canManageSelectedGroup = selectedGroup
    ? actionAllowedForScope(entitlements.value, 'access.group.manage', 'GROUP', selectedGroup.groupId)
    : false;
  const canManageSelectedMembership = selection?.type === 'DEPARTMENT'
    ? selectedDepartment
      ? actionAllowedForScope(entitlements.value, 'access.membership.manage', 'DEPARTMENT', selectedDepartment.departmentId, departmentAncestorIds(selectedDepartment.departmentId, departments))
      : false
    : selection?.type === 'GROUP'
      ? selectedGroup
        ? actionAllowedForScope(entitlements.value, 'access.membership.manage', 'GROUP', selectedGroup.groupId)
        : false
      : false;
  const canManageSelected = selection?.type === 'DEPARTMENT' ? canManageSelectedDepartment : selection?.type === 'GROUP' ? canManageSelectedGroup : false;
  const canOpenSourceSystems = featureAllowed(entitlements.value, 'source-systems');
  const canOpenDispatch = featureAllowed(entitlements.value, 'dispatch');
  const canOpenA2AGovernance = featureAllowed(entitlements.value, 'a2a-governance');
  const editableGroupParentOptions = useMemo(() => {
    if (!selectedGroup) return [];
    const allowed = new Map(manageableGroupOptions.map((option) => [option.value, option]));
    const current = groupOptions.find((option) => option.value === selectedGroup.parentGroupId);
    if (current) allowed.set(current.value, current);
    return [...allowed.values()].filter((option) => option.value !== selectedGroup.groupId && !isGroupDescendant(option.value, selectedGroup.groupId, groups));
  }, [groupOptions, groups, manageableGroupOptions, selectedGroup]);
  const movableDepartmentOptions = useMemo(() => {
    if (!selectedDepartment) return [];
    const allowed = new Map(manageableDepartmentOptions.map((option) => [option.value, option]));
    const current = departmentOptions.find((option) => option.value === selectedDepartment.parentDepartmentId);
    if (current) allowed.set(current.value, current);
    return [...allowed.values()].filter((option) => option.value !== selectedDepartment.departmentId && !isDepartmentDescendant(option.value, selectedDepartment.departmentId, departments));
  }, [departmentOptions, departments, manageableDepartmentOptions, selectedDepartment]);
  const groupParentChangeAuthorized = !selectedGroup
    || draftParentId === (selectedGroup.parentGroupId || '')
    || (draftParentId ? manageableGroupOptions.some((option) => option.value === draftParentId) : canManageGroupTenant);
  const departmentMoveAuthorized = !selectedDepartment
    || draftParentId === (selectedDepartment.parentDepartmentId || '')
    || (draftParentId ? manageableDepartmentOptions.some((option) => option.value === draftParentId) : canManageDepartmentTenant);

  const loadWorkspace = useCallback(async () => {
    if (!scopeTenantId) return;
    setLoading(true);
    setError('');
    try {
      const [departmentPage, groupPage, peoplePage, rolePage, bindingPage] = await Promise.all([
        accessManagementApi.departments(scopeTenantId, 0, 500),
        accessManagementApi.groups(scopeTenantId, 0, 500),
        accessManagementApi.tenantUsers(scopeTenantId, 500),
        accessManagementApi.roles(scopeTenantId, 0, 250),
        accessManagementApi.roleBindings(scopeTenantId, 750),
      ]);
      setDepartments(departmentPage.items);
      setGroups(groupPage.items);
      setPeople(peoplePage.items);
      setRoles(rolePage.items);
      setBindings(bindingPage.items);
      const requestedType = searchParams.get('type');
      setSelection((current) => {
        if (current?.type === 'DEPARTMENT' && departmentPage.items.some((item) => item.departmentId === current.id)) return current;
        if (current?.type === 'GROUP' && groupPage.items.some((item) => item.groupId === current.id)) return current;
        if (requestedType === 'GROUP' && groupPage.items[0]) return { type: 'GROUP', id: groupPage.items[0].groupId };
        if (departmentPage.items[0]) return { type: 'DEPARTMENT', id: departmentPage.items[0].departmentId };
        if (groupPage.items[0]) return { type: 'GROUP', id: groupPage.items[0].groupId };
        return null;
      });
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load the Organization workspace.'));
    } finally {
      setLoading(false);
    }
  }, [scopeTenantId, searchParams]);

  const loadSelection = useCallback(async () => {
    if (!scopeTenantId || !selection) {
      setMembers([]);
      setAudit([]);
      setEligibleManagers([]);
      return;
    }
    setDetailLoading(true);
    setError('');
    try {
      if (selection.type === 'DEPARTMENT') {
        const [memberPage, managerPage, auditPage] = await Promise.all([
          accessManagementApi.departmentMembers(scopeTenantId, selection.id, 500),
          accessManagementApi.eligibleDepartmentManagers(scopeTenantId, selection.id, '', 500),
          accessManagementApi.audit(scopeTenantId, 100, '', '', '', selection.id),
        ]);
        setMembers(memberPage.items);
        setEligibleManagers(managerPage.items);
        setAudit(auditPage.items);
      } else {
        const [memberPage, auditPage] = await Promise.all([
          accessManagementApi.groupMembers(scopeTenantId, selection.id, 500),
          accessManagementApi.audit(scopeTenantId, 100, '', '', '', selection.id),
        ]);
        setMembers(memberPage.items);
        setEligibleManagers([]);
        setAudit(auditPage.items);
      }
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load organization details.'));
    } finally {
      setDetailLoading(false);
    }
  }, [scopeTenantId, selection]);

  useEffect(() => { void loadWorkspace(); }, [loadWorkspace]);
  useEffect(() => {
    if (routeActionHandled.current || loading) return;
    const action = searchParams.get('action');
    if (action === 'ADD_DEPARTMENT' && canManageDepartment) {
      routeActionHandled.current = true;
      setDraftName(''); setDraftCode(''); setDraftDescription('');
      setDraftParentId(canManageDepartmentTenant ? '' : manageableDepartmentOptions[0]?.value || '');
      setReason('New Tenant setup'); setDialog('CREATE_DEPARTMENT');
    } else if (action === 'ADD_GROUP' && canManageGroup) {
      routeActionHandled.current = true;
      setDraftName(''); setDraftCode(''); setDraftDescription(''); setDraftOwnerDepartmentId(''); setDraftGroupType('GENERAL');
      setDraftParentId(canManageGroupTenant ? '' : manageableGroupOptions[0]?.value || '');
      setReason('New Tenant setup'); setDialog('CREATE_GROUP');
    }
  }, [canManageDepartment, canManageDepartmentTenant, canManageGroup, canManageGroupTenant, loading, manageableDepartmentOptions, manageableGroupOptions, searchParams]);
  useEffect(() => { void loadSelection(); }, [loadSelection]);

  const filteredDepartments = useMemo(() => {
    const term = treeSearch.trim().toLowerCase();
    const matching = term ? departments.filter((department) => `${department.name} ${department.code}`.toLowerCase().includes(term)) : departments;
    return term ? matching : matching.filter((department) => !hasCollapsedDepartmentAncestor(department, departments, new Set(collapsedDepartments)));
  }, [collapsedDepartments, departments, treeSearch]);
  const filteredGroups = useMemo(() => {
    const term = treeSearch.trim().toLowerCase();
    const matching = term ? groups.filter((group) => `${group.name} ${group.code} ${group.description}`.toLowerCase().includes(term)) : groups;
    return term ? matching : matching.filter((group) => !hasCollapsedGroupAncestor(group, groups, new Set(collapsedGroups)));
  }, [collapsedGroups, groups, treeSearch]);
  const memberIds = useMemo(() => new Set(members.map((membership) => membership.userId)), [members]);
  const loadMemberCandidates = useCallback(async (cursor = '', append = false) => {
    if (!scopeTenantId) return;
    const sequence = ++memberSearchSequence.current;
    setMemberSearchLoading(true);
    try {
      const page = await accessManagementApi.tenantUsers(scopeTenantId, 50, cursor, memberSearch, 'ACTIVE');
      if (sequence !== memberSearchSequence.current) return;
      const eligible = page.items.filter((person) => !memberIds.has(person.userId));
      setMemberCandidates((current) => append
        ? [...new Map([...current, ...eligible].map((person) => [person.userId, person])).values()]
        : eligible);
      setMemberCandidateCursor(page.nextCursor);
      setMemberCandidateHasMore(page.hasMore);
    } catch (cause) {
      if (sequence === memberSearchSequence.current) setError(formatIamError(cause, 'Unable to search Tenant people.'));
    } finally {
      if (sequence === memberSearchSequence.current) setMemberSearchLoading(false);
    }
  }, [memberIds, memberSearch, scopeTenantId]);
  useEffect(() => {
    if (dialog !== 'ADD_MEMBERS' || memberBulkResult) return;
    const timer = window.setTimeout(() => { void loadMemberCandidates('', false); }, 250);
    return () => window.clearTimeout(timer);
  }, [dialog, loadMemberCandidates, memberBulkResult]);
  const scopedBindings = selection
    ? bindings.filter((binding) => binding.scopeType === selection.type && binding.scopeId === selection.id)
    : [];
  const selectedMemberPerson = selectedMembership ? people.find((item) => item.userId === selectedMembership.userId) ?? null : null;

  const departmentChildCount = selectedDepartment ? departments.filter((item) => item.parentDepartmentId === selectedDepartment.departmentId && item.status !== 'DELETED').length : 0;
  const departmentDescendantCount = selectedDepartment ? countDepartmentDescendants(selectedDepartment.departmentId, departments) : 0;
  const groupChildCount = selectedGroup ? groups.filter((item) => item.parentGroupId === selectedGroup.groupId && item.status !== 'DELETED').length : 0;
  const ownedGroupCount = selectedDepartment ? groups.filter((group) => group.ownerDepartmentId === selectedDepartment.departmentId && group.status !== 'DELETED').length : 0;
  const activeScopedAssignments = scopedBindings.filter((item) => item.status === 'ACTIVE').length;
  // RC0 Fix5: People, hierarchy and scoped Role Bindings are soft retirement dependencies.
  // Governed Source/Dispatch/A2A ownership remains a server-authoritative hard blocker.
  const deleteBlocked = retirementLoading || !retirementPreview || !retirementPreview.canDelete;
  const departmentsWithoutManager = departments.filter((item) => item.status === 'ACTIVE' && !item.managerUserId).length;

  function choose(next: Selection, nextTab: OrganizationTab = 'OVERVIEW') {
    setSelection(next);
    setTab(nextTab);
    const params = new URLSearchParams(searchParams.toString());
    params.set('type', next.type);
    params.set('id', next.id);
    params.set('tab', nextTab);
    router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/organization?${params.toString()}`);
  }

  function changeTab(nextTab: OrganizationTab) {
    setTab(nextTab);
    if (!selection) return;
    const params = new URLSearchParams(searchParams.toString());
    params.set('type', selection.type);
    params.set('id', selection.id);
    params.set('tab', nextTab);
    router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/organization?${params.toString()}`);
  }

  function clearDraft() {
    setDraftName('');
    setDraftCode('');
    setDraftParentId('');
    setDraftOwnerDepartmentId('');
    setDraftGroupType('GENERAL');
    setDraftDescription('');
    setDraftStatus('ACTIVE');
    setSelectedPeople([]);
    setMemberSearch('');
    setMemberCandidates([]);
    setMemberCandidateCursor('');
    setMemberCandidateHasMore(false);
    setMemberBulkResult(null);
    setMemberRole('MEMBER');
    setMemberExpiresAt('');
    setSelectedMembershipId('');
    setManagerUserId('');
    setGrantManagerAccess(true);
    setReason('Job responsibility changed');
  }

  function closeDialog() {
    setDialog(null);
    clearDraft();
  }

  function openCreateDepartment(parentDepartmentId = '') {
    clearDraft();
    const authorizedParent = parentDepartmentId || (canManageDepartmentTenant ? '' : manageableDepartmentOptions[0]?.value || '');
    setDraftParentId(authorizedParent);
    setReason('New Tenant setup');
    setDialog('CREATE_DEPARTMENT');
  }

  function openCreateGroup(parentGroupId = '') {
    clearDraft();
    const authorizedParent = parentGroupId || (canManageGroupTenant ? '' : manageableGroupOptions[0]?.value || '');
    setDraftParentId(authorizedParent);
    setReason('New Tenant setup');
    setDialog('CREATE_GROUP');
  }

  function openEdit() {
    if (selectedDepartment) {
      setDraftName(selectedDepartment.name);
      setDraftCode(selectedDepartment.code);
      setDraftParentId(selectedDepartment.parentDepartmentId || '');
      setDraftOwnerDepartmentId('');
      setDraftDescription('');
    } else if (selectedGroup) {
      setDraftName(selectedGroup.name);
      setDraftCode(selectedGroup.code);
      setDraftParentId(selectedGroup.parentGroupId || '');
      setDraftOwnerDepartmentId(selectedGroup.ownerDepartmentId || '');
      setDraftGroupType(selectedGroup.type || 'GENERAL');
      setDraftDescription(selectedGroup.description || '');
    } else return;
    setReason('Job responsibility changed');
    setDialog('EDIT');
  }

  function openMove() {
    if (!selectedDepartment) return;
    setDraftParentId(selectedDepartment.parentDepartmentId || '');
    setReason('Department transfer');
    setDialog('MOVE');
  }

  function openLifecycle() {
    if (!selection) return;
    setDraftStatus(selectedStatus || 'ACTIVE');
    setReason('Approved lifecycle change');
    setDialog('LIFECYCLE');
  }

  async function openDelete() {
    if (!selection || !scopeTenantId) return;
    setReason('');
    setRetirementPreview(null);
    setRetirementLoading(true);
    setDialog('DELETE');
    try {
      const preview = selection.type === 'DEPARTMENT'
        ? await accessManagementApi.departmentRetirementPreview(scopeTenantId, selection.id)
        : await accessManagementApi.groupRetirementPreview(scopeTenantId, selection.id);
      setRetirementPreview(preview);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to calculate the retirement impact.'));
    } finally {
      setRetirementLoading(false);
    }
  }

  async function refreshRetirementAfterConcurrency(cause: unknown): Promise<boolean> {
    if (!scopeTenantId || !selection || !(cause instanceof ApiError)) return false;
    const code = cause.code ?? '';
    const concurrent = [409, 412, 428].includes(cause.status ?? 0) || /VERSION|CONFLICT|PRECONDITION|STALE/.test(code);
    if (!concurrent) return false;
    setRetirementLoading(true);
    setRecoveryErrorCode(code);
    try {
      const preview = selection.type === 'DEPARTMENT'
        ? await accessManagementApi.departmentRetirementPreview(scopeTenantId, selection.id)
        : await accessManagementApi.groupRetirementPreview(scopeTenantId, selection.id);
      setRetirementPreview(preview);
      await loadWorkspace();
      setError('The organization changed after this retirement preview was calculated. OpenDispatch refreshed the authoritative impact. Review the updated counts and blockers, then retry the retirement action.');
    } catch (refreshCause) {
      setError(formatIamError(refreshCause, 'The organization changed and the retirement impact could not be refreshed. Reload the workspace before retrying.'));
    } finally {
      setRetirementLoading(false);
    }
    return true;
  }

  function openManager() {
    if (!selectedDepartment) return;
    setManagerUserId(selectedDepartment.managerUserId || '');
    setGrantManagerAccess(true);
    setReason('Job responsibility changed');
    setDialog('MANAGER');
  }

  function openAddMembers() {
    setSelectedPeople([]);
    setMemberSearch('');
    setMemberCandidates([]);
    setMemberCandidateCursor('');
    setMemberCandidateHasMore(false);
    setMemberBulkResult(null);
    setMemberRole('MEMBER');
    setReason('New employee onboarding');
    setDialog('ADD_MEMBERS');
  }

  function openMember(membership: Membership) {
    setSelectedMembershipId(membership.membershipId);
    setMemberRole(membership.role || 'MEMBER');
    setMemberExpiresAt(toLocalDateTime(membership.expiresAt));
    setReason('Job responsibility changed');
    setDialog('MEMBER');
  }

  async function refreshAfter(message: string, preferred?: Selection) {
    setNotice(message);
    setDialog(null);
    clearDraft();
    await loadWorkspace();
    if (preferred) choose(preferred, 'OVERVIEW');
    await loadSelection();
  }

  async function createDepartment() {
    if (!scopeTenantId || !draftName.trim() || !isAuditReasonValid(reason, 'ROUTINE')) return;
    const code = draftCode.trim() || generateUniqueCode(draftName, departments.map((item) => item.code), 'DEPT');
    setBusy(true); setError(''); setNotice('');
    try {
      const created = await accessManagementApi.createDepartment(scopeTenantId, {
        departmentId: null,
        code,
        name: draftName.trim(),
        parentDepartmentId: draftParentId || null,
        managerUserId: null,
        displayOrder: departments.length * 10,
        reason,
      }, reason);
      await refreshAfter(`${created.name} was created. Add people or assign its Manager from this page.`, { type: 'DEPARTMENT', id: created.departmentId });
    } catch (cause) { setError(formatIamError(cause, 'Unable to create the Department.')); }
    finally { setBusy(false); }
  }

  async function createGroup() {
    if (!scopeTenantId || !draftName.trim() || !isAuditReasonValid(reason, 'ROUTINE')) return;
    const code = draftCode.trim() || generateUniqueCode(draftName, groups.map((item) => item.code), 'GROUP');
    setBusy(true); setError(''); setNotice('');
    try {
      const created = await accessManagementApi.createGroup(scopeTenantId, {
        groupId: null,
        code,
        name: draftName.trim(),
        type: draftGroupType,
        parentGroupId: draftParentId || null,
        ownerDepartmentId: draftOwnerDepartmentId || null,
        description: draftDescription.trim(),
      }, reason);
      await refreshAfter(`${created.name} was created. Membership and access remain separate, explicit records.`, { type: 'GROUP', id: created.groupId });
    } catch (cause) { setError(formatIamError(cause, 'Unable to create the Group.')); }
    finally { setBusy(false); }
  }

  async function updateSelected() {
    if (!scopeTenantId || !selection || !draftName.trim() || !isAuditReasonValid(reason, 'ROUTINE')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (selectedDepartment) {
        await accessManagementApi.updateDepartment(scopeTenantId, selectedDepartment.departmentId, {
          code: draftCode.trim() || selectedDepartment.code,
          name: draftName.trim(),
          parentDepartmentId: selectedDepartment.parentDepartmentId || null,
          managerUserId: selectedDepartment.managerUserId || null,
          displayOrder: selectedDepartment.displayOrder,
          reason,
        }, selectedDepartment.version, reason);
      } else if (selectedGroup) {
        await accessManagementApi.updateGroup(scopeTenantId, selectedGroup.groupId, {
          code: draftCode.trim() || selectedGroup.code,
          name: draftName.trim(),
          type: draftGroupType,
          parentGroupId: draftParentId || null,
          ownerDepartmentId: draftOwnerDepartmentId || null,
          description: draftDescription.trim(),
        }, selectedGroup.version, reason);
      }
      await refreshAfter(`${selectedName} details were updated.`);
    } catch (cause) { setError(formatIamError(cause, `Unable to update ${selectedName || 'the organization item'}.`)); }
    finally { setBusy(false); }
  }

  async function moveDepartment() {
    if (!scopeTenantId || !selectedDepartment || !isAuditReasonValid(reason, 'ROUTINE')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.moveDepartment(scopeTenantId, selectedDepartment.departmentId, draftParentId, selectedDepartment.version, reason);
      await refreshAfter(`${selectedDepartment.name} moved successfully. Members, Manager and scoped access were preserved.`);
    } catch (cause) { setError(formatIamError(cause, 'Unable to move the Department.')); }
    finally { setBusy(false); }
  }

  async function saveManager() {
    if (!scopeTenantId || !selectedDepartment || !isAuditReasonValid(reason, 'ELEVATED')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.assignOfficialManager(scopeTenantId, selectedDepartment.departmentId, managerUserId, selectedDepartment.version, reason);
      if (grantManagerAccess && managerUserId) {
        const managerRole = roles.find((item) => item.roleCode === 'DEPARTMENT_MANAGER' && item.status === 'ACTIVE');
        const alreadyBound = bindings.some((item) => item.principalType === 'USER'
          && item.principalId === managerUserId
          && item.roleId === managerRole?.roleId
          && item.scopeType === 'DEPARTMENT'
          && item.scopeId === selectedDepartment.departmentId
          && item.status === 'ACTIVE');
        if (managerRole && !alreadyBound) {
          await accessManagementApi.bindRole(scopeTenantId, {
            principalType: 'USER',
            principalId: managerUserId,
            roleId: managerRole.roleId,
            scopeType: 'DEPARTMENT',
            scopeId: selectedDepartment.departmentId,
            effectiveAt: null,
            expiresAt: null,
            approvalId: null,
          }, reason);
        }
      }
      await refreshAfter(grantManagerAccess && managerUserId
        ? 'Official Manager and Department Manager responsibility were updated.'
        : 'Official Manager was updated. Access assignments were left unchanged.');
    } catch (cause) { setError(formatIamError(cause, 'Unable to update the Official Manager.')); }
    finally { setBusy(false); }
  }

  async function addMembers() {
    if (!scopeTenantId || !selection || selectedPeople.length === 0 || !isAuditReasonValid(reason, 'ROUTINE')) return;
    setBusy(true); setError(''); setNotice(''); setMemberBulkResult(null);
    try {
      const result = await accessManagementApi.peopleBulkAction(scopeTenantId, {
        operation: selection.type === 'DEPARTMENT' ? 'ADD_DEPARTMENT_MEMBERSHIP' : 'ADD_GROUPS',
        userIds: selectedPeople,
        targetDepartmentId: selection.type === 'DEPARTMENT' ? selection.id : undefined,
        groupIds: selection.type === 'GROUP' ? [selection.id] : undefined,
        membershipRole: selection.type === 'GROUP' ? memberRole as 'MEMBER'|'LEAD' : undefined,
      }, reason);
      setMemberBulkResult(result);
      if (result.failedCount === 0) {
        await refreshAfter(`${result.succeededCount} ${result.succeededCount === 1 ? 'person was' : 'people were'} added to ${selectedName}; ${result.skippedCount} already had the requested membership.`);
      } else {
        setNotice(`Bulk membership operation completed with ${result.succeededCount} succeeded, ${result.skippedCount} skipped and ${result.failedCount} failed. Review remediation below and retry only failed people.`);
        await loadSelection();
      }
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to submit the server-side membership command.'));
    } finally { setBusy(false); }
  }

  async function updateMembership() {
    if (!scopeTenantId || !selectedMembership || !isAuditReasonValid(reason, 'ELEVATED')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (selection?.type === 'DEPARTMENT') {
        await accessManagementApi.updateDepartmentMembership(scopeTenantId, selectedMembership.membershipId, {
          membershipType: memberRole,
          primary: selectedMembership.primary,
          expiresAt: fromLocalDateTime(memberExpiresAt),
          reason,
        }, selectedMembership.version, reason);
      } else {
        await accessManagementApi.updateGroupMembership(scopeTenantId, selectedMembership.membershipId, {
          membershipRole: memberRole,
          expiresAt: fromLocalDateTime(memberExpiresAt),
          reason,
        }, selectedMembership.version, reason);
      }
      await refreshAfter(`${selectedMemberPerson?.displayName ?? 'Membership'} was updated.`);
    } catch (cause) { setError(formatIamError(cause, 'Unable to update the membership.')); }
    finally { setBusy(false); }
  }

  async function removeMembership() {
    if (!scopeTenantId || !selectedMembership || !isAuditReasonValid(reason, 'ELEVATED')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (selection?.type === 'DEPARTMENT') {
        await accessManagementApi.removeDepartmentMembership(scopeTenantId, selectedMembership.membershipId, selectedMembership.version, reason);
      } else {
        await accessManagementApi.removeGroupMembership(scopeTenantId, selectedMembership.membershipId, selectedMembership.version, reason);
      }
      await refreshAfter(`${selectedMemberPerson?.displayName ?? 'The person'} was removed from ${selectedName}.`);
    } catch (cause) { setError(formatIamError(cause, 'Unable to remove the membership.')); }
    finally { setBusy(false); }
  }

  async function changeLifecycle() {
    if (!scopeTenantId || !selection || !draftStatus || !isAuditReasonValid(reason, 'ELEVATED')) return;
    setBusy(true); setError(''); setRecoveryErrorCode(''); setNotice('');
    try {
      if (selectedDepartment) {
        await accessManagementApi.changeDepartmentStatus(scopeTenantId, selectedDepartment.departmentId, draftStatus, selectedDepartment.version, reason);
      } else if (selectedGroup) {
        await accessManagementApi.changeGroupStatus(scopeTenantId, selectedGroup.groupId, draftStatus, selectedGroup.version, reason);
      }
      await refreshAfter(`${selectedName} status changed to ${humanize(draftStatus)}.`);
    } catch (cause) { setRecoveryErrorCode(cause instanceof ApiError ? cause.code ?? '' : ''); setError(formatIamError(cause, 'Unable to change lifecycle status.')); }
    finally { setBusy(false); }
  }

  async function deleteOrganizationItem() {
    if (!scopeTenantId || !selection || !isAuditReasonValid(reason, 'HIGH_RISK')) return;
    setBusy(true); setError(''); setRecoveryErrorCode(''); setNotice('');
    try {
      if (selectedDepartment) {
        await accessManagementApi.changeDepartmentStatus(scopeTenantId, selectedDepartment.departmentId, 'DELETED', selectedDepartment.version, reason);
      } else if (selectedGroup) {
        await accessManagementApi.changeGroupStatus(scopeTenantId, selectedGroup.groupId, 'DELETED', selectedGroup.version, reason);
      }
      setDialog(null);
      setSelection(null);
      setTab('OVERVIEW');
      await loadWorkspace();
      setNotice(`${selectedName} was deleted from active administration. Historical audit evidence remains retained.`);
      router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/organization`);
    } catch (cause) {
      if (!(await refreshRetirementAfterConcurrency(cause))) {
        setRecoveryErrorCode(cause instanceof ApiError ? cause.code ?? '' : '');
        setError(formatIamError(cause, `Unable to delete ${selectedName || 'this organization item'}. Resolve the active dependencies first.`));
      }
    }
    finally { setBusy(false); }
  }

  if (!scopeTenantId) return { mode: 'tenant-required' as const };


  return {
    mode: 'workspace' as const, scopeTenantId, tenantName, departments, groups, people, roles, bindings, members, audit, eligibleManagers, selection, tab, treeSearch, memberSearch, collapsedDepartments, collapsedGroups,
    loading, detailLoading, busy, error, recoveryErrorCode, notice, dialog, retirementPreview, retirementLoading, draftName, draftCode, draftParentId, draftOwnerDepartmentId, draftGroupType, draftDescription, draftStatus, selectedPeople, memberCandidates, memberCandidateCursor, memberCandidateHasMore, memberSearchLoading, memberBulkResult, memberRole, memberExpiresAt, selectedMembershipId, managerUserId, grantManagerAccess, reason,
    selectedDepartment, selectedGroup, selectedName, selectedStatus, selectedMembership, departmentOptions, groupOptions, manageableDepartmentOptions, manageableGroupOptions, canManageDepartment, canManageGroup, canManageDepartmentTenant, canManageGroupTenant, canManageSelectedDepartment, canManageSelectedGroup, canManageSelectedMembership, canManageSelected, canOpenSourceSystems, canOpenDispatch, canOpenA2AGovernance, editableGroupParentOptions, movableDepartmentOptions, groupParentChangeAuthorized, departmentMoveAuthorized, filteredDepartments, filteredGroups, memberIds, scopedBindings, selectedMemberPerson, departmentChildCount, departmentDescendantCount, groupChildCount, ownedGroupCount, activeScopedAssignments, deleteBlocked, departmentsWithoutManager,
    setSelection, setTab, setTreeSearch, setMemberSearch, setCollapsedDepartments, setCollapsedGroups, setError, setRecoveryErrorCode, setNotice, setDialog, setRetirementPreview, setDraftName, setDraftCode, setDraftParentId, setDraftOwnerDepartmentId, setDraftGroupType, setDraftDescription, setDraftStatus, setSelectedPeople, setMemberBulkResult, setMemberRole, setMemberExpiresAt, setSelectedMembershipId, setManagerUserId, setGrantManagerAccess, setReason,
    choose, changeTab, clearDraft, closeDialog, openCreateDepartment, openCreateGroup, openEdit, openMove, openLifecycle, openDelete, openManager, openAddMembers, openMember, loadMemberCandidates, createDepartment, createGroup, updateSelected, moveDepartment, saveManager, addMembers, updateMembership, removeMembership, changeLifecycle, deleteOrganizationItem,
  };
}
