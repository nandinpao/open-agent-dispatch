import { expect, test, type Page, type Route } from '@playwright/test';

const now = '2026-08-03T00:00:00Z';
const tenant = {
  tenantId: 'tenant-a', tenantCode: 'TENANT-A', tenantName: 'Tenant A',
  status: 'ACTIVE', createdAt: now, updatedAt: now, version: 1,
};
const user = {
  userId: 'user-001', username: 'operator@example.com', email: 'operator@example.com',
  displayName: 'Operations User', status: 'ACTIVE', creationMode: 'INVITATION',
  createdAt: now, updatedAt: now, version: 3,
};
const department = {
  departmentId: 'department-it', tenantId: tenant.tenantId, code: 'IT', name: 'Information Technology',
  parentDepartmentId: '', managerUserId: '', displayOrder: 0, status: 'ACTIVE',
  createdAt: now, updatedAt: now, version: 1,
};
const group = {
  groupId: 'group-security', tenantId: tenant.tenantId, code: 'SECURITY', name: 'Security Team',
  type: 'SECURITY', ownerDepartmentId: department.departmentId, status: 'ACTIVE',
  createdAt: now, updatedAt: now, version: 1,
};
const role = {
  roleId: 'role-access-admin', tenantId: tenant.tenantId, roleCode: 'ACCESS_ADMIN', roleName: 'Access Administrator',
  roleType: 'TENANT_ROLE', description: 'Manages Roles and Role Bindings.', systemManaged: false,
  status: 'ACTIVE', createdAt: now, updatedAt: now, version: 1,
};
const permission = {
  permissionCode: 'identity.role_binding.manage', displayName: 'Manage Role Bindings',
  description: 'Create and revoke scoped Role Bindings.', domain: 'Access Control',
  allowedScopeTypes: ['TENANT', 'DEPARTMENT', 'GROUP'], riskLevel: 'HIGH', status: 'ACTIVE', version: 1,
};
const capability = {
  contractVersion: '4.0', generatedAt: now,
  authentication: { sessionPath: '/api/session', legacyPasswordAdapterEnabled: false },
  surfaces: {
    iamAdministration: 'ENABLED', resourceAccessAdministration: 'ENABLED', uiCapabilityProjection: 'ENABLED',
    enforcementActivation: 'ENABLED', a2aOperations: 'ENABLED', issueTracking: 'ENABLED',
  },
};

const cursorPage = <T>(items: T[]) => ({ items, nextCursor: '', hasMore: false });
const offsetPage = <T>(items: T[]) => ({ items, page: 0, size: 250, totalElements: items.length, totalPages: 1 });

async function routeCanonicalSession(page: Page) {
  await page.route('**/api/ui/runtime-capabilities', route => route.fulfill({ json: capability }));
  await page.route('**/api/session/csrf', route => route.fulfill({ json: { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-test' } }));
  await page.route('**/api/session/preflight', route => route.fulfill({ json: { authenticated: true, principalType: 'INSTANCE_ROOT', sessionValid: true, csrfReady: true, tenantContext: '', authorizationService: 'AUTHENTICATED_SESSION_READY', permissionCatalog: 'AVAILABLE', correlationId: 'test-correlation' } }));
  await page.route('**/api/session', route => route.fulfill({ json: {
    authenticationType: 'CANONICAL_SESSION', userId: 'root', username: 'root', displayName: 'Instance Root',
    roles: ['INSTANCE_ROOT'], permissions: ['*'], selectedTenantId: '',
    tenantChoices: [{ tenantId: tenant.tenantId, tenantCode: tenant.tenantCode, tenantName: tenant.tenantName, membershipStatus: 'ACTIVE', roleSummary: ['PLATFORM_ADMIN'] }],
    requiredActions: [], credentialVersion: 2, authenticatedAt: now, expiresAt: '2026-08-03T08:00:00Z', authenticationMethods: ['PASSWORD'],
  } }));
}

async function fulfillAccessRoute(route: Route) {
  const request = route.request();
  const url = new URL(request.url());
  const path = url.pathname.replace(/^\/core-api/, '');
  if (request.method() !== 'GET') {
    await route.fulfill({ status: 200, json: {} });
    return;
  }
  if (path === '/api/admin/access/tenants') return route.fulfill({ json: offsetPage([tenant]) });
  if (path === '/api/admin/access/users') return route.fulfill({ json: cursorPage([user]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/users`) return route.fulfill({ json: cursorPage([user]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/departments`) return route.fulfill({ json: offsetPage([department]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/groups`) return route.fulfill({ json: offsetPage([group]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/roles`) return route.fulfill({ json: offsetPage([role]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/permissions`) return route.fulfill({ json: offsetPage([permission]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/role-bindings`) return route.fulfill({ json: cursorPage([]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/members`) return route.fulfill({ json: cursorPage([]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/responsibility-templates`) return route.fulfill({ json: offsetPage([]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/rbac-approvals`) return route.fulfill({ json: [] });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/sessions`) return route.fulfill({ json: cursorPage([]) });
  if (path === `/api/admin/access/tenants/${tenant.tenantId}/audit`) return route.fulfill({ json: cursorPage([]) });
  if (path.includes('/memberships')) return route.fulfill({ json: cursorPage([]) });
  if (path.includes('/effective-access')) return route.fulfill({ json: { userId: user.userId, tenantId: tenant.tenantId, permissions: [], conflicts: [] } });
  return route.fulfill({ json: cursorPage([]) });
}

async function routeUnifiedAccessBackend(page: Page) {
  await page.route('**/core-api/api/admin/access/**', fulfillAccessRoute);
}

test.describe('P1 canonical Access Management convergence', () => {
  test.beforeEach(async ({ page }) => {
    await routeCanonicalSession(page);
    await routeUnifiedAccessBackend(page);
  });

  test('canonical navigation starts at the Tenant directory', async ({ page }) => {
    await page.goto('/admin/tenants');
    await expect(page).toHaveURL(/\/admin\/tenants$/);
    await expect(page.getByRole('heading', { name: 'Tenant administration' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Choose the company or business unit to manage' })).toBeVisible();
    await expect(page.getByText('Tenant A', { exact: true })).toBeVisible();
  });

  test('legacy users route is compatibility-only and redirects to canonical People', async ({ page }) => {
    await page.goto(`/access-management/users?tenantId=${tenant.tenantId}`);
    await expect(page).toHaveURL(new RegExp(`/admin/tenants/${tenant.tenantId}/people`));
    await expect(page.getByRole('heading', { name: 'People in Tenant A' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Tenant workspace sections' })).toBeVisible();
  });

  test('legacy role route redirects to the canonical Responsibilities view', async ({ page }) => {
    await page.goto(`/access-management/roles?tenantId=${tenant.tenantId}`);
    await expect(page).toHaveURL(new RegExp(`/admin/tenants/${tenant.tenantId}/access\\?view=TEMPLATES`));
    await expect(page.getByRole('heading', { name: 'Responsibilities in Tenant A' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Responsibilities' })).toBeVisible();
  });
});
