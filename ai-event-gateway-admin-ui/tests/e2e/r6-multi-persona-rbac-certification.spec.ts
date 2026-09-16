import { expect, test } from '@playwright/test';
import {
  entitlements,
  expectActionModes,
  expectDirectPageDenied,
  expectFeatureMatrix,
  expectLoginDenied,
  getCoreJson,
  loginPersona,
  persona,
  requiredEnv,
  session,
  expectNoInteractiveTenantSwitch,
} from './support/r6PersonaCertification';

type PageResult<T> = { items: T[] };
type Department = { departmentId: string; name: string };
type Group = { groupId: string; name: string };
type User = { userId: string; displayName: string };
type EffectiveAccess = { permissions: Array<{ permissionCode: string; sources: Array<{ principalType: string; principalId: string; scopeType: string; scopeId: string }> }> };

const tenantA = () => requiredEnv('R6_TENANT_A_ID');
const tenantB = () => requiredEnv('R6_TENANT_B_ID');

function requireLiveRuntime() {
  if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') {
    throw new Error('R6 persona certification refuses mocked APIs. Run against the qualified live stack.');
  }
  if (process.env.R6_CERTIFICATION_REQUIRED !== 'true') {
    throw new Error('Set R6_CERTIFICATION_REQUIRED=true. The R6 suite must never pass through skipped persona tests.');
  }
}

async function expectRole(page: Parameters<typeof session>[0], roleCode: string) {
  expect((await session(page)).roles, `Session must contain ${roleCode}`).toContain(roleCode);
}

test.describe.configure({ mode: 'serial' });

test.describe('Phase 6 multi-account / multi-Tenant RBAC runtime certification', () => {
  test.beforeAll(() => requireLiveRuntime());

  test('Instance Root sees Platform Administration, while Internal Engineering stays out of normal navigation', async ({ page }) => {
    await loginPersona(page, persona('ROOT'));
    const value = await entitlements(page);
    expect(value.workspaceKind).toBe('INSTANCE_ROOT');
    expectFeatureMatrix(value, ['instance-administration'], []);
    const navigation = new Set(value.navigation.map((item) => item.featureId));
    expect(navigation.has('permission-catalog')).toBe(false);
    expect(navigation.has('enforcement-activation')).toBe(false);
    expect(navigation.has('permission-readiness')).toBe(false);
    expect(value.pages['permission-catalog']?.allowed).toBe(true);
  });

  test('Platform Administrator can administer the platform without Root-only engineering authority', async ({ page }) => {
    await loginPersona(page, persona('PLATFORM_ADMIN'));
    await expectRole(page, 'PLATFORM_ADMIN');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['instance-administration'], ['permission-catalog', 'enforcement-activation', 'permission-readiness']);
  });

  test('Tenant A Administrator is Tenant-bound and cannot cross into Tenant B data', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    await expectRole(page, 'TENANT_ADMIN');
    expect((await session(page)).selectedTenantId).toBe(tenantA());
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['access-management', 'access-people', 'access-organization', 'access-governance', 'access-security'], ['instance-administration']);
    const cross = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantB())}/departments?size=10`);
    expect(cross.status).toBe(403);
  });

  test('Tenant B Administrator is isolated from Tenant A', async ({ page }) => {
    await loginPersona(page, persona('TENANT_B_ADMIN'), tenantB());
    await expectRole(page, 'TENANT_ADMIN');
    expect((await session(page)).selectedTenantId).toBe(tenantB());
    const cross = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/departments?size=10`);
    expect(cross.status).toBe(403);
  });

  test('User Administrator can manage People but only read Organization', async ({ page }) => {
    await loginPersona(page, persona('USER_ADMIN'), tenantA());
    await expectRole(page, 'USER_ADMIN');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['access-management', 'access-people', 'access-organization'], ['access-governance', 'access-security', 'instance-administration']);
    expect(value.pages['access-people']?.displayMode).toBe('ENABLED');
    expect(value.pages['access-organization']?.displayMode).toBe('READ_ONLY');
    expectActionModes(value,
      ['access.people.create', 'access.people.update', 'access.tenant-membership.manage'],
      ['access.department.manage', 'access.group.manage', 'access.assignment.manage', 'access.security-policy.manage']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/roles?page=0&size=5`);
    expect(forbidden.status).toBe(403);
  });

  test('Organization Administrator manages Departments and Groups but not People accounts or Access assignments', async ({ page }) => {
    await loginPersona(page, persona('ORGANIZATION_ADMIN'), tenantA());
    await expectRole(page, 'ORGANIZATION_ADMIN');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['access-management', 'access-people', 'access-organization'], ['access-governance', 'access-security']);
    expect(value.pages['access-people']?.displayMode).toBe('READ_ONLY');
    expect(value.pages['access-organization']?.displayMode).toBe('ENABLED');
    expectActionModes(value,
      ['access.department.manage', 'access.group.manage', 'access.membership.manage'],
      ['access.people.create', 'access.people.update', 'access.assignment.manage']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/role-bindings?limit=5`);
    expect(forbidden.status).toBe(403);
  });

  test('Access Administrator manages Responsibilities and assignments without organization or security mutation', async ({ page }) => {
    await loginPersona(page, persona('ACCESS_ADMIN'), tenantA());
    await expectRole(page, 'ACCESS_ADMIN');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['access-management', 'access-people', 'access-organization', 'access-governance'], ['access-security']);
    expect(value.pages['access-people']?.displayMode).toBe('READ_ONLY');
    expect(value.pages['access-organization']?.displayMode).toBe('READ_ONLY');
    expect(value.pages['access-governance']?.displayMode).toBe('ENABLED');
    expectActionModes(value,
      ['access.role.manage', 'access.role-permissions.manage', 'access.assignment.manage'],
      ['access.people.create', 'access.department.manage', 'access.group.manage', 'access.security-policy.manage']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/security-policy-revisions?limit=5`);
    expect(forbidden.status).toBe(403);
  });

  test('Security Administrator manages sign-in security without People, organization or Role mutation', async ({ page }) => {
    await loginPersona(page, persona('SECURITY_ADMIN'), tenantA());
    await expectRole(page, 'SECURITY_ADMIN');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['administration', 'access-management', 'access-people', 'access-security'], ['access-organization', 'access-governance']);
    expect(value.pages['access-people']?.displayMode).toBe('READ_ONLY');
    expect(value.pages['access-security']?.displayMode).toBe('ENABLED');
    expectActionModes(value,
      ['access.security-session.revoke', 'access.security-policy.manage'],
      ['access.people.create', 'access.department.manage', 'access.assignment.manage']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/roles?page=0&size=5`);
    expect(forbidden.status).toBe(403);
  });

  test('Dispatch Administrator owns Dispatch configuration without IAM administration', async ({ page }) => {
    await loginPersona(page, persona('DISPATCH_ADMIN'), tenantA());
    await expectRole(page, 'DISPATCH_ADMIN');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['dashboard', 'source-systems', 'dispatch', 'agents', 'tasks'], ['access-management', 'instance-administration']);
    expect(value.pages['a2a-governance']?.displayMode).toBe('READ_ONLY');
    expect(value.navigation.some((item) => item.featureId === 'a2a-governance')).toBe(false);
    expectActionModes(value,
      ['source-systems.create', 'dispatch.update', 'agents.update'],
      ['a2a-policy.manage', 'access.people.create', 'access.department.manage', 'access.assignment.manage']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=5`);
    expect(forbidden.status).toBe(403);
  });

  test('Operator can execute day-to-day Tasks without IAM or Dispatch configuration authority', async ({ page }) => {
    await loginPersona(page, persona('OPERATOR'), tenantA());
    await expectRole(page, 'OPERATOR');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['dashboard', 'agents', 'tasks', 'issues-events'], ['access-management', 'a2a-governance', 'instance-administration']);
    expectActionModes(value, ['tasks.operate', 'issues-events.operate'], ['dispatch.update', 'source-systems.create', 'access.people.create']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=5`);
    expect(forbidden.status).toBe(403);
  });

  test('Viewer is operational read-only and has no IAM administration', async ({ page }) => {
    await loginPersona(page, persona('VIEWER'), tenantA());
    await expectRole(page, 'VIEWER');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['dashboard', 'agents', 'tasks'], ['access-management', 'instance-administration']);
    expect(value.pages['tasks']?.displayMode).toBe('READ_ONLY');
    expectActionModes(value, [], ['tasks.operate', 'dispatch.update', 'source-systems.create', 'access.people.create']);
    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=5`);
    expect(forbidden.status).toBe(403);
  });

  test('IT Department Administrator sees the IT subtree, not HR, and receives scope-aware actions', async ({ page }) => {
    const it = requiredEnv('R6_IT_DEPARTMENT_ID');
    const itChild = requiredEnv('R6_IT_CHILD_DEPARTMENT_ID');
    const hr = requiredEnv('R6_HR_DEPARTMENT_ID');
    const itUser = requiredEnv('R6_IT_USER_ID');
    const hrUser = requiredEnv('R6_HR_USER_ID');
    await loginPersona(page, persona('IT_DEPARTMENT_ADMIN'), tenantA());
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['access-management', 'access-people', 'access-organization'], ['instance-administration', 'dispatch']);
    expect(value.actionScopes?.['access.department.manage'] ?? []).toContain(`DEPARTMENT_SUBTREE:${it}`);
    expect(value.actionScopes?.['access.department.manage'] ?? []).not.toContain(`TENANT:${tenantA()}`);

    const departments = await getCoreJson<PageResult<Department>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/departments?size=100`);
    expect(departments.status).toBe(200);
    const departmentIds = new Set(departments.body?.items.map((item) => item.departmentId) ?? []);
    expect(departmentIds.has(it)).toBe(true);
    expect(departmentIds.has(itChild)).toBe(true);
    expect(departmentIds.has(hr)).toBe(false);

    const users = await getCoreJson<PageResult<User>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=100`);
    expect(users.status).toBe(200);
    const userIds = new Set(users.body?.items.map((item) => item.userId) ?? []);
    expect(userIds.has(itUser)).toBe(true);
    expect(userIds.has(hrUser)).toBe(false);

    const forbidden = await getCoreJson(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/departments/${encodeURIComponent(hr)}`);
    expect(forbidden.status).toBe(403);
    await expectDirectPageDenied(page, '/dispatch-flows');
  });

  test('HR Department Administrator is the mirror image of IT scope', async ({ page }) => {
    const it = requiredEnv('R6_IT_DEPARTMENT_ID');
    const hr = requiredEnv('R6_HR_DEPARTMENT_ID');
    const hrUser = requiredEnv('R6_HR_USER_ID');
    const itUser = requiredEnv('R6_IT_USER_ID');
    await loginPersona(page, persona('HR_DEPARTMENT_ADMIN'), tenantA());
    const departments = await getCoreJson<PageResult<Department>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/departments?size=100`);
    expect(departments.status).toBe(200);
    const departmentIds = new Set(departments.body?.items.map((item) => item.departmentId) ?? []);
    expect(departmentIds.has(hr)).toBe(true);
    expect(departmentIds.has(it)).toBe(false);
    const users = await getCoreJson<PageResult<User>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=100`);
    const userIds = new Set(users.body?.items.map((item) => item.userId) ?? []);
    expect(userIds.has(hrUser)).toBe(true);
    expect(userIds.has(itUser)).toBe(false);
  });

  test('Group Operator sees only the governed Group scope', async ({ page }) => {
    const groupId = requiredEnv('R6_OPERATOR_GROUP_ID');
    const otherGroupId = requiredEnv('R6_OTHER_GROUP_ID');
    const groupUserId = requiredEnv('R6_GROUP_USER_ID');
    const hrUserId = requiredEnv('R6_HR_USER_ID');
    await loginPersona(page, persona('GROUP_OPERATOR'), tenantA());
    const value = await entitlements(page);
    expect(value.actionScopes?.['access.membership.manage'] ?? []).toContain(`GROUP:${groupId}`);
    const groups = await getCoreJson<PageResult<Group>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/groups?size=100`);
    expect(groups.status).toBe(200);
    const groupIds = new Set(groups.body?.items.map((item) => item.groupId) ?? []);
    expect(groupIds.has(groupId)).toBe(true);
    expect(groupIds.has(otherGroupId)).toBe(false);
    const users = await getCoreJson<PageResult<User>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=100`);
    const userIds = new Set(users.body?.items.map((item) => item.userId) ?? []);
    expect(userIds.has(groupUserId)).toBe(true);
    expect(userIds.has(hrUserId)).toBe(false);
  });

  test('Auditor can inspect Access and Audit but cannot mutate organization or assignments', async ({ page }) => {
    await loginPersona(page, persona('AUDITOR'), tenantA());
    await expectRole(page, 'AUDITOR');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['access-management', 'access-people', 'access-organization', 'access-governance', 'access-security'], ['instance-administration']);
    for (const action of ['access.people.create', 'access.department.manage', 'access.group.manage', 'access.membership.manage', 'access.assignment.manage', 'access.security-policy.manage']) {
      expect(value.actions).not.toContain(action);
    }
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/organization`);
    await expect(page.getByRole('button', { name: '+ Department' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '+ Group' })).toHaveCount(0);
  });

  test('Normal User receives My Account only instead of an empty Navigator', async ({ page }) => {
    await loginPersona(page, persona('NORMAL_USER'), tenantA());
    const currentSession = await session(page);
    expect(currentSession.roles).toHaveLength(0);
    expect(currentSession.permissions).toHaveLength(0);
    const value = await entitlements(page);
    expect(value.pages['access-management']?.allowed).toBe(false);
    expect(value.pages['instance-administration']?.allowed).toBe(false);
    expect(value.pages['my-account']?.allowed).toBe(true);
    expect(value.navigation.map((item) => item.featureId)).toEqual(['my-account']);
    await expectDirectPageDenied(page, `/admin/tenants/${encodeURIComponent(tenantA())}/organization`);
  });

  test('Dual-Tenant User enters the administrator-configured home Tenant without selection or switching', async ({ page }) => {
    await loginPersona(page, persona('DUAL_TENANT_USER'), tenantA());
    const current = await entitlements(page);
    expect(current.tenantId).toBe(tenantA());
    expect((await session(page)).selectedTenantId).toBe(tenantA());
    await expectNoInteractiveTenantSwitch(page);
    expect(current.pages['access-management']?.allowed).toBe(true);
  });

  test('Suspended User cannot obtain an authenticated browser session', async ({ page }) => {
    await expectLoginDenied(page, persona('SUSPENDED_USER'));
    const response = await page.request.get('/api/session');
    expect(response.status()).toBe(401);
  });

  test('Parent Group Role is inherited by a user who belongs only to the child Group', async ({ page, browser }) => {
    const inheritedUserId = requiredEnv('R6_GROUP_INHERITED_USER_ID');
    const parentGroupId = requiredEnv('R6_PARENT_GROUP_ID');
    await loginPersona(page, persona('GROUP_INHERITED_USER'), tenantA());
    const current = await session(page);
    expect(current.permissions).toContain('api.task.search');
    const value = await entitlements(page);
    expectFeatureMatrix(value, ['dashboard', 'tasks'], ['access-management', 'instance-administration']);

    const rootContext = await browser.newContext();
    try {
      const rootPage = await rootContext.newPage();
      await loginPersona(rootPage, persona('ROOT'));
      const effective = await getCoreJson<EffectiveAccess>(rootPage, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users/${encodeURIComponent(inheritedUserId)}/effective-access`);
      expect(effective.status).toBe(200);
      const task = effective.body?.permissions.find((item) => item.permissionCode === 'api.task.search');
      expect(task).toBeTruthy();
      expect(task?.sources.some((source) => source.principalType === 'GROUP' && source.principalId === parentGroupId)).toBe(true);
    } finally {
      await rootContext.close();
    }
  });
});
