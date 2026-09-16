'use client';

import { useEffect, useMemo, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { ApiError } from '@/lib/api/errors';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { Department, Group, Role, User, UserOnboardingRequest, UserOnboardingResult, ResponsibilityUiAccessPreview } from '@/lib/iam/types';
import { createUuid } from '@/lib/utils/uuid';
import { isAuditReasonValid, datePresetToIso, type SelectOption, type WizardStepDefinition } from '../shared/beginnerUi';

export const STEPS: WizardStepDefinition[] = [
  { key: 'person', label: 'Person', description: 'Name and sign-in identity' },
  { key: 'organization', label: 'Organization', description: 'Workspace, Department and Groups' },
  { key: 'responsibility', label: 'Responsibility', description: 'Business access and scope' },
  { key: 'sign-in', label: 'Sign-in', description: 'Invitation or administrator-assisted setup' },
  { key: 'review', label: 'Review', description: 'Confirm and add the person' },
];

export interface AddPersonWizardProps {
  tenantId: string;
  tenantName: string;
  departments: Department[];
  groups: Group[];
  roles: Role[];
  onCancel: () => void;
  onComplete: (result: UserOnboardingResult) => Promise<void> | void;
}

export interface WizardState {
  displayName: string;
  username: string;
  email: string;
  creationMode: 'INVITATION' | 'ADMIN_CREATED';
  authenticationMethod: 'LOCAL';
  activationDeliveryMethod: 'EMAIL' | 'MANUAL';
  initialPassword: string;
  confirmInitialPassword: string;
  employeeId: string;
  membershipDuration: string;
  membershipCustomDate: string;
  primaryDepartmentId: string;
  additionalDepartmentIds: string[];
  groupIds: string[];
  applicationAccessMode: 'ASSIGN' | 'DEFER';
  roleId: string;
  scopeType: 'TENANT' | 'DEPARTMENT' | 'GROUP';
  scopeId: string;
  accessDuration: string;
  accessCustomDate: string;
  auditReason: string;
}

const INITIAL_STATE: WizardState = {
  displayName: '',
  username: '',
  email: '',
  creationMode: 'INVITATION',
  authenticationMethod: 'LOCAL',
  activationDeliveryMethod: 'EMAIL',
  initialPassword: '',
  confirmInitialPassword: '',
  employeeId: '',
  membershipDuration: 'PERMANENT',
  membershipCustomDate: '',
  primaryDepartmentId: '',
  additionalDepartmentIds: [],
  groupIds: [],
  applicationAccessMode: 'ASSIGN',
  roleId: '',
  scopeType: 'TENANT',
  scopeId: '',
  accessDuration: 'PERMANENT',
  accessCustomDate: '',
  auditReason: 'New employee onboarding',
};

export function useAddPersonWizardController({
  tenantId,
  tenantName,
  departments,
  groups,
  roles,
  onComplete,
}: Readonly<AddPersonWizardProps>) {
  const [step, setStep] = useState(0);
  const [state, setState] = useState<WizardState>({ ...INITIAL_STATE, scopeId: tenantId });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<UserOnboardingResult | null>(null);
  const [accountSource, setAccountSource] = useState<'NEW' | 'EXISTING'>('NEW');
  const [existingSearch, setExistingSearch] = useState('');
  const [existingCandidates, setExistingCandidates] = useState<User[]>([]);
  const [existingUserId, setExistingUserId] = useState('');
  const [searchingExisting, setSearchingExisting] = useState(false);
  const [accessPreview, setAccessPreview] = useState<ResponsibilityUiAccessPreview | null>(null);
  const [accessPreviewLoading, setAccessPreviewLoading] = useState(false);
  const [accessPreviewError, setAccessPreviewError] = useState('');

  const selectedExistingPerson = existingCandidates.find((person) => person.userId === existingUserId);
  const existingNeedsSetupDelivery = Boolean(selectedExistingPerson && ['PENDING_ACTIVATION', 'PASSWORD_RESET_REQUIRED'].includes(selectedExistingPerson.status));
  const existingNeedsMfaOnly = selectedExistingPerson?.status === 'MFA_ENROLLMENT_REQUIRED';

  const departmentOptions = useMemo<SelectOption[]>(
    () => departments
      .filter((department) => department.status === 'ACTIVE')
      .map((department) => ({
        value: department.departmentId,
        label: department.name,
        description: department.code,
      })),
    [departments],
  );
  const groupOptions = useMemo<SelectOption[]>(
    () => groups
      .filter((group) => group.status === 'ACTIVE')
      .map((group) => ({
        value: group.groupId,
        label: group.name,
        description: group.description || group.type,
      })),
    [groups],
  );
  const roleOptions = useMemo<SelectOption[]>(
    () => roles
      .filter((role) => role.status === 'ACTIVE')
      .map((role) => ({
        value: role.roleId,
        label: role.roleName,
        description: role.description || role.roleCode,
      })),
    [roles],
  );
  const additionalDepartmentOptions = departmentOptions.filter((option) => option.value !== state.primaryDepartmentId);
  const scopeOptions = state.scopeType === 'DEPARTMENT'
    ? departmentOptions
    : state.scopeType === 'GROUP'
      ? groupOptions
      : [{ value: tenantId, label: tenantName }];

  function update<K extends keyof WizardState>(key: K, value: WizardState[K]) {
    setState((current) => ({ ...current, [key]: value }));
    setError('');
  }

  function validateCurrentStep(): string {
    if (step === 0) {
      if (accountSource === 'EXISTING') {
        if (!existingUserId) return 'Search for and select an existing person.';
      } else {
        if (!state.displayName.trim()) return 'Enter the person’s display name.';
        if (!state.username.trim()) return 'Enter a sign-in name.';
        if (state.email && !/^\S+@\S+\.\S+$/.test(state.email)) return 'Enter a valid email address.';
      }
    }
    if (step === 1 && state.membershipDuration === 'CUSTOM' && !state.membershipCustomDate) {
      return 'Choose the workspace membership expiry date.';
    }
    if (step === 2) {
      if (state.applicationAccessMode === 'ASSIGN' && !state.roleId) return 'Choose an initial responsibility, or explicitly choose No application access yet.';
      if (state.roleId && !state.scopeId) return 'Choose where the responsibility applies.';
      if (state.accessDuration === 'CUSTOM' && !state.accessCustomDate) return 'Choose the access expiry date.';
    }
    if (step === 3 && accountSource === 'NEW' && state.creationMode === 'ADMIN_CREATED') {
      if (state.initialPassword.length < 14) return 'Set an initial password with at least 14 characters.';
      if (state.initialPassword !== state.confirmInitialPassword) return 'Initial password confirmation does not match.';
    }
    if (step === 3 && (state.creationMode === 'INVITATION' || existingNeedsSetupDelivery) && state.activationDeliveryMethod === 'EMAIL' && !state.email.trim()) {
      return 'Enter an email address or choose manual secure handoff.';
    }
    if (step === 4 && !isAuditReasonValid(state.auditReason, 'ROUTINE')) {
      return 'Choose a business reason or enter a specific reason.';
    }
    return '';
  }

  async function goNext() {
    const validation = validateCurrentStep();
    if (validation) {
      setError(validation);
      return;
    }
    if (step === 0 && accountSource === 'NEW' && await redirectToExistingIdentityIfPresent()) return;
    setStep((current) => Math.min(current + 1, STEPS.length - 1));
  }

  function goBack() {
    setError('');
    setStep((current) => Math.max(current - 1, 0));
  }

  function applyExistingPerson(person: User) {
    setExistingUserId(person.userId);
    setState((current) => ({
      ...current,
      displayName: person.displayName,
      username: person.username,
      email: person.email ?? '',
      creationMode: 'ADMIN_CREATED',
    }));
  }

  async function findExactAvailableIdentity(): Promise<User | null> {
    const username = state.username.trim().toLowerCase();
    const email = state.email.trim().toLowerCase();
    const terms = [state.username.trim(), state.email.trim()]
      .filter((term, index, values) => term.length >= 2 && values.indexOf(term) === index);
    const candidates = new Map<string, User>();
    for (const term of terms) {
      const page = await accessManagementApi.availableTenantUsers(tenantId, term, 50);
      for (const person of page.items) {
        candidates.set(person.userId, person);
      }
    }
    return Array.from(candidates.values()).find((person) =>
      person.username.trim().toLowerCase() === username
      || Boolean(email && (person.email ?? '').trim().toLowerCase() === email)) ?? null;
  }

  async function redirectToExistingIdentityIfPresent(): Promise<boolean> {
    if (!state.username.trim() && !state.email.trim()) return false;
    setSearchingExisting(true);
    try {
      const match = await findExactAvailableIdentity();
      if (!match) return false;
      setAccountSource('EXISTING');
      setExistingSearch(match.username);
      setExistingCandidates([match]);
      applyExistingPerson(match);
      setError('This sign-in identity already exists in OpenDispatch and is available to be re-added to this workspace. Review the existing person below, then continue. Existing password and MFA settings will be preserved.');
      return true;
    } catch {
      // Directory preflight is advisory. The backend remains the authoritative uniqueness boundary.
      return false;
    } finally {
      setSearchingExisting(false);
    }
  }

  function buildRequest(): UserOnboardingRequest {
    const membershipExpiresAt = datePresetToIso(state.membershipDuration, state.membershipCustomDate);
    const accessExpiresAt = datePresetToIso(state.accessDuration, state.accessCustomDate);
    const departmentIds = [state.primaryDepartmentId, ...state.additionalDepartmentIds]
      .filter((value, index, values) => value && values.indexOf(value) === index);
    return {
      userId: accountSource === 'EXISTING' ? existingUserId : null,
      username: state.username.trim(),
      email: state.email.trim() || null,
      displayName: state.displayName.trim(),
      creationMode: state.creationMode,
      authenticationMethod: state.authenticationMethod,
      activationDeliveryMethod: state.activationDeliveryMethod,
      initialPassword: accountSource === 'NEW' && state.creationMode === 'ADMIN_CREATED' ? state.initialPassword : null,
      membershipId: createUuid(),
      membershipStatus: accountSource === 'EXISTING' ? 'ACTIVE' : state.creationMode === 'INVITATION' ? 'INVITED' : 'ACTIVE',
      employeeId: state.employeeId.trim() || null,
      membershipExpiresAt,
      defaultTenant: accountSource === 'NEW' && state.creationMode !== 'INVITATION',
      membershipSource: accountSource === 'EXISTING' ? 'PLATFORM_PROVISIONING' : state.creationMode === 'INVITATION' ? 'INVITATION' : 'ADMIN_CREATED',
      departments: departmentIds.map((departmentId) => ({
        membershipId: createUuid(),
        departmentId,
        membershipType: 'MEMBER',
        primary: departmentId === state.primaryDepartmentId,
        expiresAt: membershipExpiresAt,
      })),
      groups: state.groupIds.map((groupId) => ({
        membershipId: createUuid(),
        groupId,
        membershipRole: 'MEMBER',
        expiresAt: membershipExpiresAt,
      })),
      roles: state.applicationAccessMode === 'ASSIGN' && state.roleId ? [{
        bindingId: createUuid(),
        roleId: state.roleId,
        scopeType: state.scopeType,
        scopeId: state.scopeId,
        effectiveAt: null,
        expiresAt: accessExpiresAt,
      }] : [],
      applicationAccessDeferred: state.applicationAccessMode === 'DEFER',
      reason: state.auditReason.trim(),
    };
  }

  async function searchExistingPeople() {
    const term = existingSearch.trim();
    if (term.length < 2) {
      setError('Enter at least two characters to search existing people.');
      return;
    }
    setSearchingExisting(true);
    setError('');
    try {
      const page = await accessManagementApi.availableTenantUsers(tenantId, term, 50);
      const candidates = page.items;
      setExistingCandidates(candidates);
      if (candidates.length === 0) setError('No re-admittable existing identity matches this search. Create a new person instead.');
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to search existing people.'));
    } finally {
      setSearchingExisting(false);
    }
  }

  function selectExistingPerson(userId: string) {
    const selected = existingCandidates.find((person) => person.userId === userId);
    if (!selected) return;
    applyExistingPerson(selected);
    setError('');
  }

  async function submit() {
    const validation = validateCurrentStep();
    if (validation) {
      setError(validation);
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const created = await accessManagementApi.onboardUser(tenantId, buildRequest(), state.auditReason.trim());
      setState((current) => ({ ...current, initialPassword: '', confirmInitialPassword: '' }));
      setResult(created);
      setStep(STEPS.length - 1);
      await onComplete(created);
    } catch (cause) {
      if (accountSource === 'NEW' && cause instanceof ApiError && (cause.code === 'IDENTITY_USERNAME_CONFLICT' || cause.code === 'IDENTITY_EMAIL_CONFLICT')) {
        setStep(0);
        if (await redirectToExistingIdentityIfPresent()) return;
      }
      setError(formatIamError(cause, accountSource === 'EXISTING' ? 'The existing person could not be added to this workspace. No partial membership changes should be retained.' : 'The person could not be created. No partial onboarding changes should be retained.'));
    } finally {
      setSubmitting(false);
    }
  }

  const selectedRole = roles.find((role) => role.roleId === state.roleId);
  const selectedScope = scopeOptions.find((option) => option.value === state.scopeId)?.label ?? tenantName;

  useEffect(() => {
    let cancelled = false;
    if (state.applicationAccessMode !== 'ASSIGN' || !state.roleId) {
      setAccessPreview(null);
      setAccessPreviewError('');
      setAccessPreviewLoading(false);
      return () => { cancelled = true; };
    }
    setAccessPreviewLoading(true);
    setAccessPreviewError('');
    void accessManagementApi.responsibilityUiAccessPreview(tenantId, state.roleId)
      .then((preview) => { if (!cancelled) setAccessPreview(preview); })
      .catch((cause) => { if (!cancelled) { setAccessPreview(null); setAccessPreviewError(formatIamError(cause, 'Unable to preview this responsibility.')); } })
      .finally(() => { if (!cancelled) setAccessPreviewLoading(false); });
    return () => { cancelled = true; };
  }, [state.applicationAccessMode, state.roleId, tenantId]);
  const selectedPrimaryDepartment = departments.find((department) => department.departmentId === state.primaryDepartmentId);
  const selectedGroups = groups.filter((group) => state.groupIds.includes(group.groupId));


  return {
    step, state, error, submitting, result, accountSource, existingSearch, existingCandidates, existingUserId, searchingExisting,
    accessPreview, accessPreviewLoading, accessPreviewError, selectedExistingPerson, existingNeedsSetupDelivery, existingNeedsMfaOnly,
    departmentOptions, groupOptions, roleOptions, additionalDepartmentOptions, scopeOptions, selectedRole, selectedScope, selectedPrimaryDepartment, selectedGroups,
    setAccountSource, setExistingUserId, setExistingCandidates, setExistingSearch, setError, setTemporaryPassword: (value: string) => update('initialPassword', value),
    update, goNext, goBack, searchExistingPeople, selectExistingPerson, submit,
  };
}
