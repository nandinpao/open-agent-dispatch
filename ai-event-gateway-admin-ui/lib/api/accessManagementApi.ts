import { ApiError } from '@/lib/api/errors';
import { apiRequestFor, type HttpMethod } from '@/lib/api/client';
import type * as T from '@/lib/iam/types';
import { createIdempotencyKey } from '@/lib/utils/uuid';

const BASE = '/api/admin/access';

type WriteOptions = {
  auditReason?: string;
  expectedVersion?: number;
  idempotencyKey?: string;
  headers?: Record<string, string>;
  tenantScoped?: boolean;
};

const MAX_IAM_PAGE_SIZE = 100;

function query(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    const bounded = typeof value === 'number' && (key === 'size' || key === 'limit')
      ? Math.min(MAX_IAM_PAGE_SIZE, Math.max(1, value))
      : value;
    if (bounded !== undefined && bounded !== '') params.set(key, String(bounded));
  });
  const serialized = params.toString();
  return serialized ? `?${serialized}` : '';
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function contractError(path: string, expected: string, body: unknown): never {
  throw new ApiError(
    `Access Management response contract mismatch for ${path}: expected ${expected}.`,
    502,
    body,
    'ACCESS_MANAGEMENT_RESPONSE_CONTRACT_MISMATCH',
  );
}

function normalizeOffsetPage<R>(body: unknown, path: string): T.OffsetPage<R> {
  const value = asRecord(body);
  if (!value || !Array.isArray(value.items)) contractError(path, 'an offset page with items[]', body);
  return {
    items: value.items as R[],
    page: typeof value.page === 'number' ? value.page : 0,
    size: typeof value.size === 'number' ? value.size : value.items.length,
    hasMore: value.hasMore === true,
    totalCount: typeof value.totalCount === 'number' ? value.totalCount : null,
  };
}

function normalizeCursorPage<R>(body: unknown, path: string): T.CursorPage<R> {
  const value = asRecord(body);
  if (!value || !Array.isArray(value.items)) contractError(path, 'a cursor page with items[]', body);
  return {
    items: value.items as R[],
    nextCursor: typeof value.nextCursor === 'string' ? value.nextCursor : '',
    hasMore: value.hasMore === true,
  };
}

async function requestOffsetPage<R>(path: string): Promise<T.OffsetPage<R>> {
  return normalizeOffsetPage<R>(await request<unknown>(path), path);
}

async function requestCursorPage<R>(path: string): Promise<T.CursorPage<R>> {
  return normalizeCursorPage<R>(await request<unknown>(path), path);
}

async function requestArray<R>(path: string): Promise<R[]> {
  const body = await request<unknown>(path);
  if (!Array.isArray(body)) contractError(path, 'an array', body);
  return body as R[];
}

type AccessRequestInit = {
  method?: HttpMethod;
  body?: unknown;
  headers?: Record<string, string>;
};

async function request<R>(path: string, init: AccessRequestInit = {}, options: WriteOptions = {}): Promise<R> {
  const method = init.method ?? 'GET';
  const write = method !== 'GET';
  const headers: Record<string, string> = {
    ...(init.headers ?? {}),
    ...(options.headers ?? {}),
  };
  if (write) headers['Idempotency-Key'] = options.idempotencyKey ?? createIdempotencyKey('access');
  if (options.auditReason) headers['X-Audit-Reason'] = options.auditReason;
  if (options.expectedVersion !== undefined) headers['If-Match'] = String(options.expectedVersion);

  return apiRequestFor<R>('core', `${BASE}${path}`, {
    method,
    body: init.body,
    headers,
    tenantScoped: options.tenantScoped ?? path.startsWith('/tenants/'),
    requireStandardEnvelope: false,
  });
}

function json(method: HttpMethod, body: unknown): AccessRequestInit {
  return { method, body };
}

const tenant = (tenantId: string) => `/tenants/${encodeURIComponent(tenantId)}`;
const user = (userId: string) => `/users/${encodeURIComponent(userId)}`;

export const accessManagementApi = {
  // Instance users
  users: (limit = 25, cursor = '', text = '', status = '', tenantId = '') =>
    requestCursorPage<T.User>(`/users${query({ limit, cursor, text, status, tenantId })}`),
  user: (userId: string) => request<T.User>(user(userId)),
  createUser: (value: Record<string, unknown>, auditReason: string) =>
    request<T.User>('/users', json('POST', value), { auditReason }),
  updateUser: (userId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.User>(user(userId), json('PUT', value), { expectedVersion: version, auditReason }),
  changeUserStatus: (userId: string, status: string, version: number, auditReason: string) =>
    request<T.User>(`${user(userId)}/status`, json('POST', { status, reason: auditReason }), { expectedVersion: version, auditReason }),
  userTenantMemberships: (userId: string, limit = 100, cursor = '') =>
    requestCursorPage<T.PlatformTenantMembership>(`${user(userId)}/tenant-memberships${query({ limit, cursor })}`),
  requirePasswordChange: (userId: string, auditReason: string) =>
    request<void>(`${user(userId)}/require-password-change`, json('POST', {}), { auditReason }),
  revokeAllUserSessions: (userId: string, auditReason: string) =>
    request<void>(`${user(userId)}/revoke-all-sessions`, json('POST', {}), { auditReason }),

  // Tenants
  tenants: (page = 0, size = 50, text = '', status = '') =>
    requestOffsetPage<T.Tenant>(`/tenants${query({ page, size, text, status })}`),
  tenant: (tenantId: string) => request<T.Tenant>(tenant(tenantId)),
  tenantWorkspaceSummary: (tenantId: string) => request<T.TenantWorkspaceSummary>(`${tenant(tenantId)}/workspace-summary`),
  createTenant: (value: Record<string, unknown>, auditReason: string) =>
    request<T.Tenant>('/tenants', json('POST', value), { auditReason }),
  changeTenantStatus: (tenantId: string, status: string, version: number, auditReason: string) =>
    request<T.Tenant>(`${tenant(tenantId)}/status/${encodeURIComponent(status)}`, json('POST', {}), { expectedVersion: version, auditReason }),

  // Tenant users and membership
  tenantUsers: (tenantId: string, limit = 25, cursor = '', text = '', status = '', membershipStatus = '', roleId = '', departmentId = '', groupId = '', signInState = '') =>
    requestCursorPage<T.User>(`${tenant(tenantId)}/users${query({ limit, cursor, text, status, membershipStatus, roleId, departmentId, groupId, signInState })}`),
  availableTenantUsers: (tenantId: string, text: string, limit = 50, cursor = '') =>
    requestCursorPage<T.User>(`${tenant(tenantId)}/available-users${query({ limit, cursor, text })}`),
  peopleBulkAction: (tenantId: string, value: T.PeopleBulkActionRequest, auditReason: string) =>
    request<T.PeopleBulkActionResult>(`${tenant(tenantId)}/people/bulk-actions`, json('POST', value), { auditReason }),
  tenantUser: (tenantId: string, userId: string) =>
    request<T.User>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}`),
  createTenantUser: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.User>(`${tenant(tenantId)}/users`, json('POST', value), { auditReason }),
  onboardUser: (tenantId: string, value: T.UserOnboardingRequest, auditReason: string) =>
    request<T.UserOnboardingResult>(`${tenant(tenantId)}/user-onboarding`, json('POST', value), { auditReason }),
  invitationStatus: (tenantId: string, userId: string) =>
    request<T.UserInvitationStatus>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/invitation`),
  resendInvitation: (tenantId: string, userId: string, deliveryMethod: 'EMAIL'|'MANUAL'|'DEVELOPMENT_FILE', auditReason: string) =>
    request<T.UserInvitationStatus>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/invitation/resend`, json('POST', { deliveryMethod }), { auditReason }),
  revokeInvitation: (tenantId: string, userId: string, auditReason: string) =>
    request<T.UserInvitationStatus>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/invitation/revoke`, json('POST', {}), { auditReason }),
  updateTenantUser: (tenantId: string, userId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.User>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  userOverview: (tenantId: string, userId: string) =>
    request<T.UserAccessOverview>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/overview`),
  transferMachineOwnership: (tenantId: string, fromUserId: string, toUserId: string, reason: string) =>
    request<T.MachineOwnershipTransferResponse>(`${tenant(tenantId)}/users/${encodeURIComponent(fromUserId)}/machine-ownership/transfer`, json('POST', { toUserId, reason }), { auditReason: reason }),
  memberships: (tenantId: string, userId: string, limit = 100, cursor = '') =>
    requestCursorPage<T.Membership>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/memberships${query({ limit, cursor })}`),
  createTenantMembership: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/memberships`, json('POST', value), { auditReason }),
  updateTenantMembership: (tenantId: string, membershipId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/memberships/${encodeURIComponent(membershipId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  changeTenantMembershipStatus: (tenantId: string, membershipId: string, status: string, version: number, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/memberships/${encodeURIComponent(membershipId)}/status`, json('POST', { status, reason: auditReason }), { expectedVersion: version, auditReason }),
  tenantMembers: (tenantId: string, limit = 100, cursor = '') =>
    requestCursorPage<T.Membership>(`${tenant(tenantId)}/members${query({ limit, cursor })}`),
  addTenantMember: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/members`, json('POST', value), { auditReason }),

  // Departments and official manager
  departments: (tenantId: string, page = 0, size = 200, text = '', status = '') =>
    requestOffsetPage<T.Department>(`${tenant(tenantId)}/departments${query({ page, size, text, status })}`),
  department: (tenantId: string, departmentId: string) =>
    request<T.Department>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}`),
  departmentRetirementPreview: (tenantId: string, departmentId: string) =>
    request<T.OrganizationRetirementPreview>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/retirement-preview`),
  createDepartment: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Department>(`${tenant(tenantId)}/departments`, json('POST', value), { auditReason }),
  updateDepartment: (tenantId: string, departmentId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.Department>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  changeDepartmentStatus: (tenantId: string, departmentId: string, status: string, version: number, auditReason: string) =>
    request<T.Department>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/status`, json('POST', { status, reason: auditReason }), { expectedVersion: version, auditReason }),
  moveDepartment: (tenantId: string, departmentId: string, parentDepartmentId: string, version: number, auditReason: string) =>
    request<T.Department>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/move`, json('POST', { parentDepartmentId: parentDepartmentId || null, reason: auditReason }), { expectedVersion: version, auditReason }),
  assignOfficialManager: (tenantId: string, departmentId: string, managerUserId: string, version: number, auditReason: string) =>
    request<T.Department>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/official-manager`, json('PUT', { managerUserId: managerUserId || null }), { expectedVersion: version, auditReason }),
  departmentMembers: (tenantId: string, departmentId: string, limit = 100, cursor = '') =>
    requestCursorPage<T.Membership>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/members${query({ limit, cursor })}`),
  eligibleDepartmentManagers: (tenantId: string, departmentId: string, text = '', limit = 50, cursor = '') =>
    requestCursorPage<T.User>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/eligible-managers${query({ text, limit, cursor })}`),
  addDepartmentMember: (tenantId: string, departmentId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/departments/${encodeURIComponent(departmentId)}/members`, json('POST', value), { auditReason }),
  addDepartmentMembership: (tenantId: string, userId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/department-memberships`, json('POST', value), { auditReason }),
  updateDepartmentMembership: (tenantId: string, membershipId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/department-memberships/${encodeURIComponent(membershipId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  removeDepartmentMembership: (tenantId: string, membershipId: string, version: number, auditReason: string, replacement?: { id: string; version: number }) =>
    request<T.Membership>(`${tenant(tenantId)}/department-memberships/${encodeURIComponent(membershipId)}`, { method: 'DELETE' }, {
      expectedVersion: version,
      auditReason,
      headers: replacement ? {
        'X-Replacement-Membership-Id': replacement.id,
        'X-Replacement-Membership-Version': String(replacement.version),
      } : undefined,
    }),

  // Groups
  groups: (tenantId: string, page = 0, size = 200, text = '', type = '', status = '') =>
    requestOffsetPage<T.Group>(`${tenant(tenantId)}/groups${query({ page, size, text, type, status })}`),
  group: (tenantId: string, groupId: string) =>
    request<T.Group>(`${tenant(tenantId)}/groups/${encodeURIComponent(groupId)}`),
  groupRetirementPreview: (tenantId: string, groupId: string) =>
    request<T.OrganizationRetirementPreview>(`${tenant(tenantId)}/groups/${encodeURIComponent(groupId)}/retirement-preview`),
  createGroup: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Group>(`${tenant(tenantId)}/groups`, json('POST', value), { auditReason }),
  updateGroup: (tenantId: string, groupId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.Group>(`${tenant(tenantId)}/groups/${encodeURIComponent(groupId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  changeGroupStatus: (tenantId: string, groupId: string, status: string, version: number, auditReason: string) =>
    request<T.Group>(`${tenant(tenantId)}/groups/${encodeURIComponent(groupId)}/status`, json('POST', { status, reason: auditReason }), { expectedVersion: version, auditReason }),
  groupMembers: (tenantId: string, groupId: string, limit = 100, cursor = '') =>
    requestCursorPage<T.Membership>(`${tenant(tenantId)}/groups/${encodeURIComponent(groupId)}/members${query({ limit, cursor })}`),
  addGroupMember: (tenantId: string, groupId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/groups/${encodeURIComponent(groupId)}/members`, json('POST', value), { auditReason }),
  addGroupMembership: (tenantId: string, userId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/group-memberships`, json('POST', value), { auditReason }),
  updateGroupMembership: (tenantId: string, membershipId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/group-memberships/${encodeURIComponent(membershipId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  removeGroupMembership: (tenantId: string, membershipId: string, version: number, auditReason: string) =>
    request<T.Membership>(`${tenant(tenantId)}/group-memberships/${encodeURIComponent(membershipId)}`, { method: 'DELETE' }, { expectedVersion: version, auditReason }),

  // Roles, permissions, bindings and Effective Access
  roles: (tenantId: string, page = 0, size = 200, text = '', type = '', status = '') =>
    requestOffsetPage<T.Role>(`${tenant(tenantId)}/roles${query({ page, size, text, type, status })}`),
  responsibilityTemplates: (tenantId: string, page = 0, size = 100, text = '', status = 'ACTIVE', riskLevel = '', scopeType = '') =>
    requestOffsetPage<T.ResponsibilityTemplate>(`${tenant(tenantId)}/responsibility-templates${query({ page, size, text, status, riskLevel, scopeType })}`),
  accessAssignments: (tenantId: string, limit = 50, cursor = '', text = '', status = '', principalType = '', scopeType = '', lifecycle = '') =>
    requestCursorPage<T.AccessAssignment>(`${tenant(tenantId)}/access-assignments${query({ limit, cursor, text, status, principalType, scopeType, lifecycle })}`),
  accessLifecycleSummary: (tenantId: string) =>
    request<T.AccessLifecycleSummary>(`${tenant(tenantId)}/access-lifecycle-summary`),
  accessReviewCandidates: (tenantId: string, limit = 50, cursor = '', reason = '', riskLevel = '') =>
    requestCursorPage<T.AccessReviewCandidate>(`${tenant(tenantId)}/access-review-candidates${query({ limit, cursor, reason, riskLevel })}`),
  createRole: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.Role>(`${tenant(tenantId)}/roles`, json('POST', value), { auditReason }),
  updateRole: (tenantId: string, roleId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.Role>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  changeRoleStatus: (tenantId: string, roleId: string, status: string, version: number, auditReason: string) =>
    request<T.Role>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/status`, json('POST', { status }), { expectedVersion: version, auditReason }),
  rolePermissions: (tenantId: string, roleId: string) =>
    request<T.RolePermissionMatrix>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/permissions`),
  responsibilityUiAccessPreview: (tenantId: string, roleId: string) =>
    request<T.ResponsibilityUiAccessPreview>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/ui-access-preview`),
  previewResponsibilityUiAccessDraft: (tenantId: string, roleId: string, permissionCodes: string[]) =>
    request<T.ResponsibilityUiAccessPreview>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/ui-access-preview`, json('POST', { permissionCodes })),
  previewRolePermissionHardening: (tenantId: string, roleId: string, permissionCodes: string[], approvalId?: string | null) =>
    request<T.RbacHardeningPreview>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/permissions/hardening-preview`, json('POST', { permissionCodes, approvalId })),
  requestRolePermissionApproval: (tenantId: string, roleId: string, permissionCodes: string[], auditReason: string) =>
    request<T.RbacCriticalApproval>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/permissions/approval-requests`, json('POST', { permissionCodes }), { auditReason }),
  replaceRolePermissions: (tenantId: string, roleId: string, permissionCodes: string[], version: number, auditReason: string, approvalId?: string | null) =>
    request<void>(`${tenant(tenantId)}/roles/${encodeURIComponent(roleId)}/permissions`, json('PUT', { permissionCodes, approvalId }), { expectedVersion: version, auditReason }),
  permissions: (tenantId: string, page = 0, size = 500, text = '', scopeType = '') =>
    requestOffsetPage<T.Permission>(`${tenant(tenantId)}/permissions${query({ page, size, text, scopeType })}`),
  roleBindings: (tenantId: string, limit = 200, cursor = '', roleId = '', principalId = '') =>
    requestCursorPage<T.RoleBinding>(`${tenant(tenantId)}/role-bindings${query({ limit, cursor, roleId, principalId })}`),
  previewRoleBinding: (tenantId: string, value: T.RoleBindingDraft) =>
    request<T.RoleBindingAssignmentPreview>(`${tenant(tenantId)}/role-bindings/preview`, json('POST', value)),
  previewRoleBindingHardening: (tenantId: string, value: T.RoleBindingDraft) =>
    request<T.RbacHardeningPreview>(`${tenant(tenantId)}/role-bindings/hardening-preview`, json('POST', { bindingId: null, ...value })),
  requestRoleBindingApproval: (tenantId: string, value: T.RoleBindingDraft, auditReason: string) =>
    request<T.RbacCriticalApproval>(`${tenant(tenantId)}/role-bindings/approval-requests`, json('POST', { bindingId: null, ...value }), { auditReason }),
  bindRole: (tenantId: string, value: T.RoleBindingDraft, auditReason: string) =>
    request<T.RoleBinding>(`${tenant(tenantId)}/role-bindings`, json('POST', { bindingId: null, ...value }), { auditReason }),
  previewRoleBindingRevocation: (tenantId: string, bindingId: string, userId: string) =>
    request<T.RoleBindingRevocationPreview>(`${tenant(tenantId)}/role-bindings/${encodeURIComponent(bindingId)}/revocation-preview${query({ userId })}`),
  revokeRoleBinding: (tenantId: string, bindingId: string, version: number, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/role-bindings/${encodeURIComponent(bindingId)}/revoke`, json('POST', { reason: auditReason }), { expectedVersion: version, auditReason }),
  effectiveAccess: (tenantId: string, userId: string) =>
    request<T.EffectiveAccess>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/effective-access`),
  effectiveUiAccess: (tenantId: string, userId: string) =>
    request<T.EffectiveUiAccess>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/ui-access`),
  rbacApprovals: (tenantId: string, status = '', limit = 100) =>
    requestArray<T.RbacCriticalApproval>(`${tenant(tenantId)}/rbac-approvals${query({ status, limit })}`),
  approveRbacChange: (tenantId: string, approvalId: string, reason: string) =>
    request<T.RbacCriticalApproval>(`${tenant(tenantId)}/rbac-approvals/${encodeURIComponent(approvalId)}/approve`, json('POST', { reason }), { auditReason: reason }),
  rejectRbacChange: (tenantId: string, approvalId: string, reason: string) =>
    request<T.RbacCriticalApproval>(`${tenant(tenantId)}/rbac-approvals/${encodeURIComponent(approvalId)}/reject`, json('POST', { reason }), { auditReason: reason }),

  // Service Accounts, access tokens and Tenant security policy
  serviceAccounts: async (limit = 100, cursor = '', status = '') =>
    normalizeCursorPage<T.ServiceAccount>(await request<unknown>(`/security/service-accounts${query({ limit, cursor, status })}`, {}, { tenantScoped: true }), '/security/service-accounts'),
  createServiceAccount: (value: Record<string, unknown>, auditReason: string) =>
    request<T.ServiceAccount>('/security/service-accounts', json('POST', { serviceAccountId: null, ...value }), { auditReason, tenantScoped: true }),
  updateServiceAccountMachineBoundary: (serviceAccountId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.ServiceAccount>(`/security/service-accounts/${encodeURIComponent(serviceAccountId)}/machine-boundary`, json('POST', value), { auditReason, tenantScoped: true }),
  serviceAccountCredentials: async (serviceAccountId: string, limit = 100, cursor = '', status = '') =>
    normalizeCursorPage<T.ServiceAccountCredential>(await request<unknown>(`/security/service-accounts/${encodeURIComponent(serviceAccountId)}/credentials${query({ limit, cursor, status })}`, {}, { tenantScoped: true }), `/security/service-accounts/${encodeURIComponent(serviceAccountId)}/credentials`),
  issueServiceAccountCredential: (serviceAccountId: string, value: { name: string; ttlSeconds: number }, auditReason: string) =>
    request<T.IssuedServiceAccountCredential>(`/security/service-accounts/${encodeURIComponent(serviceAccountId)}/credentials`, json('POST', value), { auditReason, tenantScoped: true }),
  rotateServiceAccountCredential: (serviceAccountId: string, credentialId: string, overlapSeconds: number, auditReason: string) =>
    request<T.IssuedServiceAccountCredential>(`/security/service-accounts/${encodeURIComponent(serviceAccountId)}/credentials/${encodeURIComponent(credentialId)}/rotate`, json('POST', { overlapSeconds }), { auditReason, tenantScoped: true }),
  revokeServiceAccountCredential: (serviceAccountId: string, credentialId: string, auditReason: string) =>
    request<void>(`/security/service-accounts/${encodeURIComponent(serviceAccountId)}/credentials/${encodeURIComponent(credentialId)}/revoke`, json('POST', { reason: auditReason }), { auditReason, tenantScoped: true }),
  tokens: async (limit = 100, cursor = '', principalId = '', status = '') =>
    normalizeCursorPage<T.TokenSummary>(await request<unknown>(`/security/tokens${query({ limit, cursor, principalId, status })}`, {}, { tenantScoped: true }), '/security/tokens'),
  issuePersonalToken: (value: Record<string, unknown>, auditReason: string) =>
    request<T.IssuedToken>('/security/tokens/personal', json('POST', value), { auditReason, tenantScoped: true }),
  issueServiceToken: (serviceAccountId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.IssuedToken>(`/security/service-accounts/${encodeURIComponent(serviceAccountId)}/tokens`, json('POST', value), { auditReason, tenantScoped: true }),
  rotateToken: (tokenId: string, overlapSeconds: number, auditReason: string) =>
    request<T.IssuedToken>(`/security/tokens/${encodeURIComponent(tokenId)}/rotate`, json('POST', { overlapSeconds }), { auditReason, tenantScoped: true }),
  revokeToken: (tokenId: string, auditReason: string) =>
    request<void>(`/security/tokens/${encodeURIComponent(tokenId)}/revoke`, json('POST', { reason: auditReason }), { auditReason, tenantScoped: true }),
  securityWorkspaceSummary: (tenantId: string) =>
    request<T.SecurityWorkspaceSummary>(`${tenant(tenantId)}/security-summary`),
  credentialGovernanceCatalog: (tenantId: string) =>
    request<T.CredentialGovernanceCatalog>(`${tenant(tenantId)}/credential-governance-catalog`),
  humanReadableAudit: (tenantId: string, limit = 50, cursor = '', category = '', outcome = '') =>
    requestCursorPage<T.HumanReadableAudit>(`${tenant(tenantId)}/audit-feed${query({ limit, cursor, category, outcome })}`),
  securityPolicyRevisions: (tenantId: string, policyKind = '', limit = 50, cursor = '') =>
    requestCursorPage<T.SecurityPolicyRevision>(`${tenant(tenantId)}/security-policy-revisions${query({ policyKind, limit, cursor })}`),
  securityPolicies: () => request<T.SecurityPolicies>('/security/policies/', {}, { tenantScoped: true }),
  updateSecurityPolicy: (kind: 'password' | 'mfa' | 'session' | 'token', value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.SecurityPolicies>(`/security/policies/${kind}`, json('PUT', value), { expectedVersion: version, auditReason, tenantScoped: true }),
  restoreSecurityPolicy: (kind: 'password' | 'mfa' | 'session' | 'token', revisionId: string, version: number, auditReason: string) =>
    request<T.SecurityPolicies>(`/security/policies/${kind}/restore/${encodeURIComponent(revisionId)}`, json('POST', {}), { expectedVersion: version, auditReason, tenantScoped: true }),

  // Security and audit
  // Enterprise authentication federation
  federationProviders: (tenantId: string) =>
    requestArray<T.AuthenticationProvider>(`${tenant(tenantId)}/federation/providers`),
  createFederationProvider: (tenantId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.AuthenticationProvider>(`${tenant(tenantId)}/federation/providers`, json('POST', value), { auditReason }),
  updateFederationProvider: (tenantId: string, providerId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.AuthenticationProvider>(`${tenant(tenantId)}/federation/providers/${encodeURIComponent(providerId)}`, json('PUT', value), { expectedVersion: version, auditReason }),
  changeFederationProviderStatus: (tenantId: string, providerId: string, status: 'ACTIVE'|'DISABLED', version: number, auditReason: string) =>
    request<T.AuthenticationProvider>(`${tenant(tenantId)}/federation/providers/${encodeURIComponent(providerId)}/status/${status}`, json('POST', {}), { expectedVersion: version, auditReason }),
  federationPolicy: (tenantId: string) =>
    request<T.FederationPolicy>(`${tenant(tenantId)}/federation/policy`),
  updateFederationPolicy: (tenantId: string, value: Record<string, unknown>, version: number, auditReason: string) =>
    request<T.FederationPolicy>(`${tenant(tenantId)}/federation/policy`, json('PUT', value), { expectedVersion: version, auditReason }),
  externalIdentityLinks: (tenantId: string, userId: string) =>
    requestArray<T.ExternalIdentityLink>(`${tenant(tenantId)}/federation/users/${encodeURIComponent(userId)}/links`),
  linkExternalIdentity: (tenantId: string, userId: string, value: Record<string, unknown>, auditReason: string) =>
    request<T.ExternalIdentityLink>(`${tenant(tenantId)}/federation/users/${encodeURIComponent(userId)}/links`, json('POST', value), { auditReason }),
  unlinkExternalIdentity: (tenantId: string, userId: string, credentialLinkId: string, version: number, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/federation/users/${encodeURIComponent(userId)}/links/${encodeURIComponent(credentialLinkId)}`, { method: 'DELETE' }, { expectedVersion: version, auditReason }),

  sessions: (tenantId: string, limit = 100, cursor = '', subjectId = '') =>
    requestCursorPage<T.Session>(`${tenant(tenantId)}/sessions${query({ limit, cursor, subjectId })}`),
  revokeSession: (tenantId: string, sessionId: string, version: number, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/sessions/${encodeURIComponent(sessionId)}/revoke`, json('POST', { reason: auditReason }), { expectedVersion: version, auditReason }),
  revokeUserSessions: (tenantId: string, userId: string, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/revoke-sessions`, json('POST', { reason: auditReason }), { auditReason }),
  resetPassword: (tenantId: string, userId: string, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/reset-password`, json('POST', { reason: auditReason }), { auditReason }),
  setTemporaryPassword: (tenantId: string, userId: string, temporaryPassword: string, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/temporary-password`, json('POST', { temporaryPassword, reason: auditReason }), { auditReason }),
  setupCredential: (tenantId: string, userId: string, deliveryMethod: 'EMAIL'|'MANUAL'|'DEVELOPMENT_FILE', auditReason: string) =>
    request<T.CredentialSetupResponse>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/setup-credential`, json('POST', { deliveryMethod, reason: auditReason }), { auditReason }),
  resetMfa: (tenantId: string, userId: string, auditReason: string) =>
    request<void>(`${tenant(tenantId)}/users/${encodeURIComponent(userId)}/reset-mfa`, json('POST', { reason: auditReason }), { auditReason }),
  audit: (tenantId: string, limit = 100, cursor = '', eventType = '', actorId = '', targetId = '') =>
    requestCursorPage<T.IdentityAudit>(`${tenant(tenantId)}/audit${query({ limit, cursor, eventType, actorId, targetId })}`),
};
