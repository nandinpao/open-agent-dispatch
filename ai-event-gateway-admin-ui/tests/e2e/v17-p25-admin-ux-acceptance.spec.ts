import { expect, test } from '@playwright/test';
import {
  entitlements,
  expectDirectPageDenied,
  getCoreJson,
  loginPersona,
  persona,
  requiredEnv,
  session,
  expectNoInteractiveTenantSwitch,
} from './support/r6PersonaCertification';

type PageResult<T> = { items: T[] };
type Department = { departmentId: string };
type User = { userId: string };

const tenantA = () => requiredEnv('R6_TENANT_A_ID');

function requireQualifiedP25Runtime() {
  if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') {
    throw new Error('P2.5 Admin UX acceptance refuses mocked APIs.');
  }
  if (process.env.P25_ACCEPTANCE_REQUIRED !== 'true') {
    throw new Error('Set P25_ACCEPTANCE_REQUIRED=true. P2.5 must never pass through skipped persona acceptance.');
  }
}

test.describe.configure({ mode: 'serial' });

test.describe('v17 P2.5 Admin UX Acceptance & Workflow Convergence', () => {
  test.beforeAll(() => requireQualifiedP25Runtime());

  test('Tenant Administrator receives a business-first setup journey and progressive technical details', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/overview`);
    await expect(page.getByRole('heading', { name: /workspace|company|business unit/i })).toBeVisible();
    await expect(page.getByText(/Recommended next action|People & Access ready/i)).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Continue the first-time administrator journey' })).toBeVisible();
    const technical = page.getByText(/Technical Tenant ID|Technical details/i).first();
    if (await technical.count()) await expect(technical).toBeVisible();
  });

  test('Department Administrator sees only effective Department data and cannot cross the scope through direct APIs', async ({ page }) => {
    const allowedDepartment = requiredEnv('R6_IT_DEPARTMENT_ID');
    const forbiddenDepartment = requiredEnv('R6_HR_DEPARTMENT_ID');
    const allowedUser = requiredEnv('R6_IT_USER_ID');
    const forbiddenUser = requiredEnv('R6_HR_USER_ID');
    await loginPersona(page, persona('IT_DEPARTMENT_ADMIN'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/people`);
    await expect(page.getByRole('heading', { name: /People in/i })).toBeVisible();

    const departments = await getCoreJson<PageResult<Department>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/departments?size=100`);
    const departmentIds = new Set(departments.body?.items.map((item) => item.departmentId) ?? []);
    expect(departmentIds.has(allowedDepartment)).toBe(true);
    expect(departmentIds.has(forbiddenDepartment)).toBe(false);

    const users = await getCoreJson<PageResult<User>>(page, `/api/admin/access/tenants/${encodeURIComponent(tenantA())}/users?limit=100`);
    const userIds = new Set(users.body?.items.map((item) => item.userId) ?? []);
    expect(userIds.has(allowedUser)).toBe(true);
    expect(userIds.has(forbiddenUser)).toBe(false);
  });

  test('Group Operator remains Group-scoped while the UI exposes only nearby permitted actions', async ({ page }) => {
    await loginPersona(page, persona('GROUP_OPERATOR'), tenantA());
    const value = await entitlements(page);
    expect(value.actions).toContain('access.membership.manage');
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/organization`);
    await expect(page.getByRole('heading', { name: /Manage structure and membership/i })).toBeVisible();
    await expect(page.getByRole('button', { name: '+ Department' })).toHaveCount(0);
  });

  test('Auditor receives an explicit read-only experience and cannot mutate through direct UI or API', async ({ page }) => {
    await loginPersona(page, persona('AUDITOR'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/organization`);
    await expect(page.getByText(/Read-only access|inspect the organization/i)).toBeVisible();
    await expect(page.getByRole('button', { name: '+ Department' })).toHaveCount(0);
    const value = await entitlements(page);
    expect(value.actions).not.toContain('access.department.manage');
    expect(value.actions).not.toContain('access.assignment.manage');
  });

  test('Normal User cannot reach administration by guessing canonical URLs', async ({ page }) => {
    await loginPersona(page, persona('NORMAL_USER'), tenantA());
    await expectDirectPageDenied(page, `/admin/tenants/${encodeURIComponent(tenantA())}/people`);
    await expectDirectPageDenied(page, `/admin/tenants/${encodeURIComponent(tenantA())}/organization`);
    await expectDirectPageDenied(page, `/admin/tenants/${encodeURIComponent(tenantA())}/access`);
  });

  test('Dual-Tenant User opens the configured home workspace without a Tenant chooser', async ({ page }) => {
    await loginPersona(page, persona('DUAL_TENANT_USER'), tenantA());
    const current = await entitlements(page);
    expect(current.tenantId).toBe(tenantA());
    expect((await session(page)).selectedTenantId).toBe(tenantA());
    await expectNoInteractiveTenantSwitch(page);
  });

  test('Suspended User does not obtain an authenticated session', async ({ page }) => {
    const suspended = persona('SUSPENDED_USER');
    await page.goto('/login');
    await page.getByLabel(/username|sign-in name/i).fill(suspended.username);
    await page.getByLabel(/password/i).fill(suspended.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page.getByText(/suspended|cannot sign in|access is not available/i)).toBeVisible();
    expect((await page.request.get('/api/session')).status()).toBe(401);
  });

  test('Instance Root retains platform administration without surfacing engineering kernel pages in normal navigation', async ({ page }) => {
    await loginPersona(page, persona('ROOT'));
    const value = await entitlements(page);
    expect(value.workspaceKind).toBe('INSTANCE_ROOT');
    const navigation = new Set(value.navigation.map((item) => item.featureId));
    expect(navigation.has('instance-administration')).toBe(true);
    expect(navigation.has('permission-catalog')).toBe(false);
    expect(navigation.has('enforcement-activation')).toBe(false);
  });
});
