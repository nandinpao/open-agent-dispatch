'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed, actionAllowedForScope } from '@/lib/navigation/uiEntitlements';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { AuthenticationProvider, Department, ExternalIdentityLink, Group, Membership, MachineOwnershipTransferResponse, PeopleBulkActionResult, PeopleBulkOperation, Role, User, UserAccessOverview, UserInvitationStatus, UserOnboardingResult } from '@/lib/iam/types';
import { useAccessManagement } from '../AccessManagementProvider';
import { isAuditReasonValid, type AuditReasonTier } from '../shared/beginnerUi';
import { departmentAncestorIds, departmentDepth, departmentPath } from '../organization/organizationWorkspaceModel';

export type DetailTab = 'OVERVIEW' | 'MEMBERSHIP' | 'ORGANIZATION' | 'ACCESS' | 'SECURITY';
export type BulkAction = 'MOVE_DEPARTMENT' | 'ADD_GROUPS' | 'REMOVE_GROUPS' | 'SUSPEND' | 'REACTIVATE' | 'REVOKE_SESSIONS' | 'RESEND_INVITATION';
type PersonSecurityAction = 'SUSPEND' | 'REACTIVATE' | 'RESET_PASSWORD' | 'RESET_MFA' | 'REVOKE_SESSIONS' | 'RESEND_INVITATION' | 'REVOKE_INVITATION';
interface Filters { text:string; accountStatus:string; membershipStatus:string; departmentId:string; groupId:string; roleId:string; signInState:string; }
const EMPTY_FILTERS: Filters = { text:'', accountStatus:'', membershipStatus:'', departmentId:'', groupId:'', roleId:'', signInState:'' };

export function usePeopleWorkspaceController() {
  const entitlements = useUiEntitlements();
  const { scopeTenantId, tenants } = useAccessManagement();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [people, setPeople] = useState<User[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [tenantMemberships, setTenantMemberships] = useState<Membership[]>([]);
  const [filters, setFilters] = useState<Filters>(() => ({
    text: searchParams.get('text') ?? '',
    accountStatus: searchParams.get('status') ?? '',
    membershipStatus: searchParams.get('membershipStatus') ?? '',
    departmentId: searchParams.get('departmentId') ?? '',
    groupId: searchParams.get('groupId') ?? '',
    roleId: searchParams.get('roleId') ?? '',
    signInState: searchParams.get('signInState') ?? '',
  }));
  const [appliedFilters, setAppliedFilters] = useState(filters);
  const [nextCursor, setNextCursor] = useState('');
  const [hasMore, setHasMore] = useState(false);
  const [selectedId, setSelectedId] = useState(searchParams.get('userId') ?? '');
  const [overview, setOverview] = useState<UserAccessOverview | null>(null);
  const [invitation, setInvitation] = useState<UserInvitationStatus | null>(null);
  const [federationLinks, setFederationLinks] = useState<ExternalIdentityLink[]>([]);
  const [federationProviders, setFederationProviders] = useState<AuthenticationProvider[]>([]);
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkProviderId, setLinkProviderId] = useState('');
  const [linkSubject, setLinkSubject] = useState('');
  const [linkUpstreamEmail, setLinkUpstreamEmail] = useState('');
  const [linkReason, setLinkReason] = useState('');
  const [unlinkTarget, setUnlinkTarget] = useState<ExternalIdentityLink | null>(null);
  const [tab, setTab] = useState<DetailTab>('OVERVIEW');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [showAddPerson, setShowAddPerson] = useState(searchParams.get('action') === 'ADD_PERSON');
  const [bulkAction, setBulkAction] = useState<BulkAction>('SUSPEND');
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkDepartmentId, setBulkDepartmentId] = useState('');
  const [bulkGroupIds, setBulkGroupIds] = useState<string[]>([]);
  const [bulkResult, setBulkResult] = useState<PeopleBulkActionResult | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editDisplayName, setEditDisplayName] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [organizationOpen, setOrganizationOpen] = useState(false);
  const [removeOpen, setRemoveOpen] = useState(false);
  const [ownershipTransferOpen, setOwnershipTransferOpen] = useState(false);
  const [ownershipSearch, setOwnershipSearch] = useState('');
  const [ownershipCandidates, setOwnershipCandidates] = useState<User[]>([]);
  const [ownershipTargetId, setOwnershipTargetId] = useState('');
  const [ownershipReason, setOwnershipReason] = useState('Accountable ownership transfer before Person lifecycle change');
  const [ownershipTransferResult, setOwnershipTransferResult] = useState<MachineOwnershipTransferResponse | null>(null);
  const [pendingSecurityAction, setPendingSecurityAction] = useState<PersonSecurityAction | null>(null);
  const [credentialDeliveryMethod, setCredentialDeliveryMethod] = useState<'EMAIL'|'MANUAL'>('MANUAL');
  const [passwordResetMode, setPasswordResetMode] = useState<'TEMPORARY_PASSWORD'|'SETUP_LINK'>('TEMPORARY_PASSWORD');
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const [confirmTemporaryPassword, setConfirmTemporaryPassword] = useState('');
  const [oneTimeSetupUrl, setOneTimeSetupUrl] = useState('');
  const [primaryDepartmentDraft, setPrimaryDepartmentDraft] = useState('');
  const [groupDraft, setGroupDraft] = useState<string[]>([]);
  const [groupSearch, setGroupSearch] = useState('');
  const [actionReason, setActionReason] = useState('Access review correction');
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const tenantName = tenants.find((tenant) => tenant.tenantId === scopeTenantId)?.tenantName ?? scopeTenantId;
  const membershipByUser = useMemo(() => new Map(tenantMemberships.map((item) => [item.userId, item])), [tenantMemberships]);
  const selected = overview?.user ?? people.find((person) => person.userId === selectedId) ?? null;
  const tenantMembership = overview?.memberships.find((item) => item.membershipType === 'TENANT') ?? (selected ? membershipByUser.get(selected.userId) : undefined);
  const departmentMemberships = overview?.memberships.filter((item) => item.membershipType === 'DEPARTMENT' && item.status !== 'REMOVED') ?? [];
  const groupMemberships = overview?.memberships.filter((item) => item.membershipType === 'GROUP' && item.status !== 'REMOVED') ?? [];
  const readiness = overview?.authenticationReadiness ?? null;
  const primaryDepartmentMembership = departmentMemberships.find((item) => item.primary) ?? null;
  const primaryDepartmentName = primaryDepartmentMembership
    ? departments.find((item) => item.departmentId === primaryDepartmentMembership.resourceId)?.name ?? 'Assigned Department'
    : 'Unassigned';
  const groupNames = groupMemberships.map((membership) => groups.find((item) => item.groupId === membership.resourceId)?.name).filter((name): name is string => Boolean(name));
  const responsibilityNames = Array.from(new Set([...(overview?.effectiveAccess.permissions ?? []).flatMap((permission) => permission.sources.map((source) => source.roleName)).filter(Boolean), ...((selected?.responsibilitySummary ?? '').split(',').map((value) => value.trim()).filter(Boolean))]));
  const canAssignAccess = actionAllowed(entitlements.value, 'access.assignment.manage');
  const canCreatePeople = actionAllowed(entitlements.value, 'access.people.create');
  const canUpdatePeople = actionAllowed(entitlements.value, 'access.people.update');
  const canTransferMachineOwnership = canUpdatePeople && actionAllowed(entitlements.value, 'access.security-token.manage');
  const canManageMembership = actionAllowed(entitlements.value, 'access.membership.manage');
  const canManageTenantMembership = actionAllowed(entitlements.value, 'access.tenant-membership.manage');
  const canViewFederation = actionAllowed(entitlements.value, 'access.federation.view');
  const canManageFederation = actionAllowed(entitlements.value, 'access.federation.manage');
  const primaryDepartment = departmentMemberships.find((item) => item.primary && item.status === 'ACTIVE') ?? null;
  const manageableDepartmentOptions = useMemo(() => departments
    .filter((item) => item.status === 'ACTIVE' && actionAllowedForScope(entitlements.value, 'access.membership.manage', 'DEPARTMENT', item.departmentId, departmentAncestorIds(item.departmentId, departments)))
    .map((item) => ({ value: item.departmentId, label: item.name, description: departmentPath(item, departments), depth: departmentDepth(item, departments) })), [departments, entitlements.value]);
  const manageableGroupOptions = useMemo(() => groups
    .filter((item) => item.status === 'ACTIVE' && actionAllowedForScope(entitlements.value, 'access.membership.manage', 'GROUP', item.groupId))
    .map((item) => ({ value: item.groupId, label: item.name, description: item.description || item.type })), [entitlements.value, groups]);
  const hasAppliedFilters = useMemo(() => Object.values(appliedFilters).some((value) => String(value ?? '').trim().length > 0), [appliedFilters]);
  const filteredGroupOptions = useMemo(() => {
    const term = groupSearch.trim().toLowerCase();
    if (!term) return manageableGroupOptions;
    return manageableGroupOptions.filter((option) => `${option.label} ${option.description ?? ''}`.toLowerCase().includes(term));
  }, [groupSearch, manageableGroupOptions]);

  const loadReferenceData = useCallback(async () => {
    if (!scopeTenantId) return;
    const [departmentPage, groupPage, rolePage, membershipPage] = await Promise.all([
      accessManagementApi.departments(scopeTenantId, 0, 250),
      accessManagementApi.groups(scopeTenantId, 0, 250),
      accessManagementApi.roles(scopeTenantId, 0, 250),
      accessManagementApi.tenantMembers(scopeTenantId, 500),
    ]);
    setDepartments(departmentPage.items);
    setGroups(groupPage.items);
    setRoles(rolePage.items);
    setTenantMemberships(membershipPage.items);
  }, [scopeTenantId]);

  const loadPeople = useCallback(async (cursor = '', append = false) => {
    if (!scopeTenantId) return;
    setLoading(true);
    setError('');
    try {
      const page = await accessManagementApi.tenantUsers(
        scopeTenantId,
        30,
        cursor,
        appliedFilters.text,
        appliedFilters.accountStatus,
        appliedFilters.membershipStatus,
        appliedFilters.roleId,
        appliedFilters.departmentId,
        appliedFilters.groupId,
        appliedFilters.signInState,
      );
      setPeople((current) => append ? [...current, ...page.items.filter((item) => !current.some((existing) => existing.userId === item.userId))] : page.items);
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
      if (!append) setSelectedId((current) => current && page.items.some((item) => item.userId === current) ? current : page.items[0]?.userId ?? '');
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load the People directory.'));
    } finally {
      setLoading(false);
    }
  }, [appliedFilters, scopeTenantId]);

  const loadPerson = useCallback(async () => {
    if (!scopeTenantId || !selectedId) {
      setOverview(null);
      setInvitation(null);
      setFederationLinks([]);
      return;
    }
    setDetailLoading(true);
    setError('');
    try {
      const [nextOverview, nextInvitation, nextLinks] = await Promise.all([
        accessManagementApi.userOverview(scopeTenantId, selectedId),
        accessManagementApi.invitationStatus(scopeTenantId, selectedId).catch(() => null),
        canViewFederation ? accessManagementApi.externalIdentityLinks(scopeTenantId, selectedId).catch(() => []) : Promise.resolve([]),
      ]);
      setOverview(nextOverview);
      setInvitation(nextInvitation);
      setFederationLinks(nextLinks);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load the selected person.'));
    } finally {
      setDetailLoading(false);
    }
  }, [canViewFederation, scopeTenantId, selectedId]);

  useEffect(() => { void loadReferenceData().catch((cause) => setError(formatIamError(cause, 'Unable to load People filters.'))); }, [loadReferenceData]);
  useEffect(() => { if (!scopeTenantId || !canViewFederation) { setFederationProviders([]); return; } void accessManagementApi.federationProviders(scopeTenantId).then(setFederationProviders).catch(() => setFederationProviders([])); }, [canViewFederation, scopeTenantId]);
  useEffect(() => { void loadPeople(); }, [loadPeople]);
  useEffect(() => { void loadPerson(); }, [loadPerson]);

  async function searchOwnershipReceivers() {
    if (!scopeTenantId) return;
    setBusy(true);
    setError('');
    try {
      const page = await accessManagementApi.tenantUsers(scopeTenantId, 30, '', ownershipSearch, 'ACTIVE');
      setOwnershipCandidates(page.items.filter((item) => item.userId !== selectedId));
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to search active ownership recipients.'));
    } finally {
      setBusy(false);
    }
  }

  async function transferMachineOwnership() {
    if (!scopeTenantId || !selectedId || !ownershipTargetId || !isAuditReasonValid(ownershipReason, 'ELEVATED')) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await accessManagementApi.transferMachineOwnership(
        scopeTenantId, selectedId, ownershipTargetId, ownershipReason,
      );
      setOwnershipTransferResult(result);
      setNotice(`Ownership transfer ${result.transferId} completed.`);
      await loadPerson();
    } catch (cause) {
      setError(formatIamError(cause, 'Machine and Agent ownership could not be transferred.'));
    } finally {
      setBusy(false);
    }
  }

  function applyFilters() {
    setAppliedFilters(filters);
    setSelectedIds([]);
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key === 'accountStatus' ? 'status' : key, value); });
    router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/people${params.size ? `?${params}` : ''}`);
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);
    setSelectedIds([]);
    router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/people`);
  }

  function selectPerson(userId: string) {
    setSelectedId(userId);
    setTab('OVERVIEW');
    const params = new URLSearchParams(searchParams.toString());
    params.set('userId', userId);
    router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/people?${params}`);
  }

  function openExternalLink() {
    setLinkProviderId(federationProviders.find((item) => item.status === 'ACTIVE')?.providerId ?? '');
    setLinkSubject(''); setLinkUpstreamEmail(selected?.email ?? ''); setLinkReason(''); setLinkOpen(true);
  }

  async function createExternalLink() {
    if (!selected || !scopeTenantId || !linkProviderId || !linkSubject.trim() || !isAuditReasonValid(linkReason, 'HIGH_RISK')) return;
    setBusy(true); setError('');
    try {
      await accessManagementApi.linkExternalIdentity(scopeTenantId, selected.userId, {providerId:linkProviderId,externalSubject:linkSubject.trim(),upstreamUsername:'',upstreamEmail:linkUpstreamEmail.trim(),upstreamEmailVerified:false}, linkReason);
      setLinkOpen(false); setNotice('External identity linked to this canonical Person. Roles and permissions were not changed.'); await loadPerson();
    } catch (cause) { setError(formatIamError(cause, 'The external identity could not be linked.')); } finally { setBusy(false); }
  }

  async function confirmUnlink() {
    if (!selected || !scopeTenantId || !unlinkTarget || !isAuditReasonValid(linkReason, 'HIGH_RISK')) return;
    setBusy(true); setError('');
    try {
      await accessManagementApi.unlinkExternalIdentity(scopeTenantId, selected.userId, unlinkTarget.credentialLinkId, unlinkTarget.version, linkReason);
      setUnlinkTarget(null); setLinkReason(''); setNotice('External identity link disabled. Existing OpenDispatch audit evidence remains available.'); await loadPerson();
    } catch (cause) { setError(formatIamError(cause, 'The external identity could not be unlinked.')); } finally { setBusy(false); }
  }

  async function handleOnboardingComplete(result: UserOnboardingResult) {
    setShowAddPerson(false);
    setNotice(`${result.user.displayName} was added to ${tenantName}.`);
    await Promise.all([loadReferenceData(), loadPeople()]);
    selectPerson(result.user.userId);
  }

  function openEditPerson() {
    if (!selected) return;
    setEditDisplayName(selected.displayName);
    setEditEmail(selected.email ?? '');
    setActionReason('Person profile updated after administration review');
    setEditOpen(true);
  }

  function openOrganizationEditor() {
    if (!selected) return;
    setPrimaryDepartmentDraft(primaryDepartment?.resourceId ?? '');
    setGroupDraft(groupMemberships.filter((item) => item.status === 'ACTIVE').map((item) => item.resourceId));
    setGroupSearch('');
    setActionReason('Department transfer');
    setOrganizationOpen(true);
  }

  async function saveOrganizationRelationships() {
    if (!selected || !scopeTenantId || !isAuditReasonValid(actionReason, 'ROUTINE')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      let targetDepartmentMembership = primaryDepartmentDraft ? departmentMemberships.find((item) => item.resourceId === primaryDepartmentDraft) ?? null : null;
      if (primaryDepartment?.resourceId !== primaryDepartmentDraft) {
        if (!primaryDepartmentDraft) {
          if (primaryDepartment) await accessManagementApi.removeDepartmentMembership(scopeTenantId, primaryDepartment.membershipId, primaryDepartment.version, actionReason);
        } else if (primaryDepartment) {
          if (targetDepartmentMembership && targetDepartmentMembership.status !== 'ACTIVE') {
            targetDepartmentMembership = await accessManagementApi.updateDepartmentMembership(scopeTenantId, targetDepartmentMembership.membershipId, {
              membershipType: targetDepartmentMembership.role || 'MEMBER',
              primary: false,
              expiresAt: null,
              reason: actionReason,
              replacementMembershipId: null,
              replacementExpectedVersion: null,
            }, targetDepartmentMembership.version, actionReason);
          }
          const replacement = targetDepartmentMembership ?? await accessManagementApi.addDepartmentMembership(scopeTenantId, selected.userId, {
            membershipId: null,
            departmentId: primaryDepartmentDraft,
            membershipType: 'MEMBER',
            primary: false,
            expiresAt: null,
          }, actionReason);
          await accessManagementApi.removeDepartmentMembership(scopeTenantId, primaryDepartment.membershipId, primaryDepartment.version, actionReason, { id: replacement.membershipId, version: replacement.version });
        } else if (targetDepartmentMembership) {
          await accessManagementApi.updateDepartmentMembership(scopeTenantId, targetDepartmentMembership.membershipId, {
            membershipType: targetDepartmentMembership.role || 'MEMBER',
            primary: true,
            expiresAt: null,
            reason: actionReason,
            replacementMembershipId: null,
            replacementExpectedVersion: null,
          }, targetDepartmentMembership.version, actionReason);
        } else {
          await accessManagementApi.addDepartmentMembership(scopeTenantId, selected.userId, {
            membershipId: null,
            departmentId: primaryDepartmentDraft,
            membershipType: 'MEMBER',
            primary: true,
            expiresAt: null,
          }, actionReason);
        }
      }

      const currentGroups = new Map(groupMemberships.map((item) => [item.resourceId, item]));
      const desiredGroups = new Set(groupDraft);
      for (const groupId of desiredGroups) {
        const current = currentGroups.get(groupId);
        if (!current) {
          await accessManagementApi.addGroupMembership(scopeTenantId, selected.userId, {
            membershipId: null,
            groupId,
            membershipRole: 'MEMBER',
            expiresAt: null,
          }, actionReason);
        } else if (current.status !== 'ACTIVE') {
          await accessManagementApi.updateGroupMembership(scopeTenantId, current.membershipId, {
            membershipRole: current.role || 'MEMBER',
            expiresAt: null,
            reason: actionReason,
          }, current.version, actionReason);
        }
      }
      for (const membership of groupMemberships) {
        if (membership.status === 'ACTIVE' && !desiredGroups.has(membership.resourceId)) {
          await accessManagementApi.removeGroupMembership(scopeTenantId, membership.membershipId, membership.version, actionReason);
        }
      }
      setOrganizationOpen(false);
      setNotice('Department and Group membership updated. Access will be recalculated from the new organization relationships.');
      await Promise.all([loadReferenceData(), loadPeople(), loadPerson()]);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to change Department or Group membership.'));
    } finally { setBusy(false); }
  }

  async function removePersonFromTenant() {
    if (!tenantMembership || !scopeTenantId || !isAuditReasonValid(actionReason, 'HIGH_RISK')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.changeTenantMembershipStatus(scopeTenantId, tenantMembership.membershipId, 'REMOVED', tenantMembership.version, actionReason);
      setRemoveOpen(false);
      setOverview(null);
      setSelectedId('');
      router.replace(`/admin/tenants/${encodeURIComponent(scopeTenantId)}/people`);
      setNotice(`${selected?.displayName ?? 'The person'} was removed from ${tenantName}. Historical organization and access evidence is retained for audit.`);
      await Promise.all([loadReferenceData(), loadPeople()]);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to remove this person from the Tenant.'));
    } finally { setBusy(false); }
  }

  async function savePersonProfile() {
    if (!selected || !scopeTenantId || !editDisplayName.trim() || !isAuditReasonValid(actionReason, 'ROUTINE')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const updated = await accessManagementApi.updateTenantUser(scopeTenantId, selected.userId, {
        displayName: editDisplayName.trim(),
        email: editEmail.trim() || null,
      }, selected.version, actionReason);
      setNotice(`${updated.displayName} profile updated.`);
      setEditOpen(false);
      await Promise.all([loadPeople(), loadPerson()]);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to update this person.'));
    } finally { setBusy(false); }
  }

  function securityActionTier(action: PersonSecurityAction): AuditReasonTier {
    if (action === 'RESEND_INVITATION') return 'ROUTINE';
    if (action === 'SUSPEND' || action === 'REACTIVATE') return 'ELEVATED';
    return 'HIGH_RISK';
  }

  function securityActionLabel(action: PersonSecurityAction): string {
    return ({
      SUSPEND: 'Suspend account',
      REACTIVATE: 'Reactivate account',
      RESET_PASSWORD: readiness?.passwordState === 'NOT_CONFIGURED' ? 'Set up password access' : 'Reset password',
      RESET_MFA: 'Reset MFA and require re-enrollment',
      REVOKE_SESSIONS: 'Revoke all active sessions',
      RESEND_INVITATION: 'Resend invitation',
      REVOKE_INVITATION: 'Revoke invitation',
    } as Record<PersonSecurityAction, string>)[action];
  }

  function openSecurityAction(action: PersonSecurityAction) {
    const tier = securityActionTier(action);
    if (action === 'RESET_PASSWORD') {
      setPasswordResetMode('TEMPORARY_PASSWORD');
      setTemporaryPassword('');
      setConfirmTemporaryPassword('');
      setCredentialDeliveryMethod(selected?.email ? 'EMAIL' : 'MANUAL');
      setOneTimeSetupUrl('');
    } else if (action === 'RESEND_INVITATION') {
      setCredentialDeliveryMethod(selected?.email ? 'EMAIL' : 'MANUAL');
      setOneTimeSetupUrl('');
    }
    setActionReason(tier === 'HIGH_RISK' ? '' : tier === 'ELEVATED' ? 'Security remediation' : 'New employee onboarding');
    setPendingSecurityAction(action);
  }

  async function runPersonAction(action: PersonSecurityAction) {
    if (!selected || !scopeTenantId || !isAuditReasonValid(actionReason, securityActionTier(action))) return;
    setBusy(true); setError(''); setNotice('');
    try {
      if (action === 'SUSPEND' || action === 'REACTIVATE') {
        await accessManagementApi.changeUserStatus(selected.userId, action === 'SUSPEND' ? 'SUSPENDED' : 'ACTIVE', selected.version, actionReason);
      } else if (action === 'RESET_PASSWORD') {
        if (passwordResetMode === 'TEMPORARY_PASSWORD') {
          if (temporaryPassword.length < 14 || temporaryPassword !== confirmTemporaryPassword) return;
          await accessManagementApi.setTemporaryPassword(scopeTenantId, selected.userId, temporaryPassword, actionReason);
          setTemporaryPassword('');
          setConfirmTemporaryPassword('');
          setNotice('Temporary password set. Existing sessions are invalidated; the person must change this password at next sign-in and then complete MFA enrollment before normal access.');
        } else {
          const setup = await accessManagementApi.setupCredential(scopeTenantId, selected.userId, credentialDeliveryMethod, actionReason);
          setOneTimeSetupUrl(setup.setupActionUrl || '');
          if (setup.deliveryStatus === 'QUEUED') {
            setNotice('Credential setup was issued and the email is queued for delivery. Refresh Security to confirm delivery.');
          } else if (setup.deliveryStatus !== 'DELIVERED') {
            setNotice(`Credential setup was issued, but ${setup.deliveryMethod.toLowerCase()} delivery is ${setup.deliveryStatus.toLowerCase()}. ${setup.failureCode || 'Review delivery configuration.'}`);
          } else {
            setNotice(setup.deliveryMethod === 'MANUAL' ? 'One-time setup link generated. Copy it now; it will not be shown again.' : 'Credential setup link delivered by email.');
          }
        }
      }
      else if (action === 'RESET_MFA') await accessManagementApi.resetMfa(scopeTenantId, selected.userId, actionReason);
      else if (action === 'REVOKE_SESSIONS') await accessManagementApi.revokeUserSessions(scopeTenantId, selected.userId, actionReason);
      else if (action === 'RESEND_INVITATION') {
        const resent = await accessManagementApi.resendInvitation(scopeTenantId, selected.userId, credentialDeliveryMethod, actionReason);
        setInvitation(resent);
        setOneTimeSetupUrl(resent.setupActionUrl || '');
        setNotice(resent.deliveryStatus === 'DELIVERED'
          ? (resent.deliveryMethod === 'MANUAL' ? 'One-time invitation link generated. Copy it now.' : 'Invitation delivered by email.')
          : resent.deliveryStatus === 'QUEUED'
            ? 'Invitation issued and queued for email delivery. Refresh Security to confirm delivery.'
            : `Invitation issued, but delivery is ${resent.deliveryStatus.toLowerCase()}. ${resent.deliveryFailureCode || 'Review delivery configuration.'}`);
      }
      else setInvitation(await accessManagementApi.revokeInvitation(scopeTenantId, selected.userId, actionReason));
      if (action !== 'RESET_PASSWORD' && action !== 'RESEND_INVITATION') setNotice('The requested person action completed.');
      await Promise.all([loadPeople(), loadPerson(), loadReferenceData()]);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to complete the person action.'));
    } finally { setBusy(false); }
  }

  async function changeMembershipStatus(status: string) {
    if (!tenantMembership || !scopeTenantId || !isAuditReasonValid(actionReason, 'ELEVATED')) return;
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.changeTenantMembershipStatus(scopeTenantId, tenantMembership.membershipId, status, tenantMembership.version, actionReason);
      setNotice(`Workspace membership changed to ${status.toLowerCase()}.`);
      await Promise.all([loadReferenceData(), loadPeople(), loadPerson()]);
    } catch (cause) { setError(formatIamError(cause, 'Unable to change workspace membership.')); }
    finally { setBusy(false); }
  }

  const bulkAuditTier: AuditReasonTier = bulkAction === 'REVOKE_SESSIONS' ? 'HIGH_RISK' : bulkAction === 'RESEND_INVITATION' ? 'ROUTINE' : 'ELEVATED';
  const organizationBulkAction = bulkAction === 'MOVE_DEPARTMENT' || bulkAction === 'ADD_GROUPS' || bulkAction === 'REMOVE_GROUPS';
  const bulkTargetValid = bulkAction === 'MOVE_DEPARTMENT' || !organizationBulkAction || bulkGroupIds.length > 0;

  async function executeBulkAction() {
    if (!scopeTenantId || selectedIds.length === 0 || !bulkTargetValid || !isAuditReasonValid(actionReason, bulkAuditTier)) return;
    setBusy(true); setError(''); setNotice(''); setBulkResult(null);
    const operation: PeopleBulkOperation = bulkAction === 'MOVE_DEPARTMENT' ? 'MOVE_PRIMARY_DEPARTMENT' : bulkAction;
    try {
      const result = await accessManagementApi.peopleBulkAction(scopeTenantId, {
        operation,
        userIds: selectedIds,
        targetDepartmentId: bulkAction === 'MOVE_DEPARTMENT' ? bulkDepartmentId : undefined,
        groupIds: bulkAction === 'ADD_GROUPS' || bulkAction === 'REMOVE_GROUPS' ? bulkGroupIds : undefined,
        deliveryMethod: bulkAction === 'RESEND_INVITATION' ? 'EMAIL' : undefined,
      }, actionReason);
      setBulkResult(result);
      setNotice(`Bulk operation ${result.bulkOperationId} reconciled ${result.selectedCount} people: ${result.succeededCount} succeeded, ${result.skippedCount} skipped, ${result.failedCount} failed.`);
      setSelectedIds([]);
      setBulkGroupIds([]);
      setBulkDepartmentId('');
      await Promise.all([loadPeople(), loadReferenceData()]);
      if (selectedId) await loadPerson();
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to submit the server-side bulk command.'));
    } finally { setBusy(false); }
  }


  if (!scopeTenantId) return { mode: 'tenant-required' as const };
  if (showAddPerson) return { mode: 'add-person' as const, scopeTenantId, tenantName, departments, groups, roles, setShowAddPerson, handleOnboardingComplete };


  return {
    mode: 'workspace' as const, scopeTenantId, tenantName, people, departments, groups, roles, tenantMemberships, filters, appliedFilters, nextCursor, hasMore, selectedId, overview, invitation, federationLinks, federationProviders,
    linkOpen, linkProviderId, linkSubject, linkUpstreamEmail, linkReason, unlinkTarget, tab, selectedIds, bulkAction, bulkOpen, bulkDepartmentId, bulkGroupIds, bulkResult, editOpen, editDisplayName, editEmail, organizationOpen, removeOpen,
    ownershipTransferOpen, ownershipSearch, ownershipCandidates, ownershipTargetId, ownershipReason, ownershipTransferResult, pendingSecurityAction, credentialDeliveryMethod, passwordResetMode, temporaryPassword, confirmTemporaryPassword, oneTimeSetupUrl,
    primaryDepartmentDraft, groupDraft, groupSearch, actionReason, loading, detailLoading, busy, error, notice, membershipByUser, selected, tenantMembership, departmentMemberships, groupMemberships, readiness, primaryDepartmentName, groupNames, responsibilityNames,
    canAssignAccess, canCreatePeople, canUpdatePeople, canTransferMachineOwnership, canManageMembership, canManageTenantMembership, canViewFederation, canManageFederation, primaryDepartment, manageableDepartmentOptions, manageableGroupOptions, hasAppliedFilters, filteredGroupOptions,
    setFilters, setSelectedIds, setShowAddPerson, setBulkAction, setBulkOpen, setBulkDepartmentId, setBulkGroupIds, setBulkResult, setEditOpen, setEditDisplayName, setEditEmail, setOrganizationOpen, setRemoveOpen, setOwnershipTransferOpen, setOwnershipSearch, setOwnershipCandidates, setOwnershipTargetId, setOwnershipReason, setOwnershipTransferResult,
    setPendingSecurityAction, setCredentialDeliveryMethod, setPasswordResetMode, setTemporaryPassword, setConfirmTemporaryPassword, setOneTimeSetupUrl, setPrimaryDepartmentDraft, setGroupDraft, setGroupSearch, setActionReason, setTab, setLinkOpen, setLinkProviderId, setLinkSubject, setLinkUpstreamEmail, setLinkReason, setUnlinkTarget,
    loadPeople, loadReferenceData, loadPerson, searchOwnershipReceivers, transferMachineOwnership, applyFilters, clearFilters, selectPerson, openExternalLink, createExternalLink, confirmUnlink, openEditPerson, openOrganizationEditor, saveOrganizationRelationships, removePersonFromTenant, savePersonProfile,
    securityActionTier, securityActionLabel, openSecurityAction, runPersonAction, changeMembershipStatus, bulkAuditTier, organizationBulkAction, bulkTargetValid, executeBulkAction,
  };
}
