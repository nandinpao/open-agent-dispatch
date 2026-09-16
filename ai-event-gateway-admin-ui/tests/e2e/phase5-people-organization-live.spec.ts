import { expect, test } from '@playwright/test';
import { authenticateAdmin, selectTenantIfAvailable } from './support/stage9WorkflowHelpers';

const tenantId = process.env.PHASE5_TENANT_ID ?? '';
const departmentName = process.env.PHASE5_DEPARTMENT_NAME ?? 'Information Technology';
const groupName = process.env.PHASE5_GROUP_NAME ?? 'ERP Implementation Team';
const roleName = process.env.PHASE5_ROLE_NAME ?? 'ERP Operator';

test.describe('Phase 5 People and Organization live production workflow', () => {
  test.skip(!tenantId, 'PHASE5_TENANT_ID is required for the live Phase 5 gate.');

  test('creates a person, places the person in organization, assigns access and verifies activation on a live stack', async ({ page }, testInfo) => {
    if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') {
      throw new Error('Phase 5 live acceptance cannot run with mocked APIs.');
    }

    const suffix = `${Date.now()}-${testInfo.workerIndex}`;
    const displayName = `Phase5 User ${suffix}`;
    const username = `phase5-${suffix}`.toLowerCase();
    const email = `${username}@example.invalid`;

    await authenticateAdmin(page);
    await selectTenantIfAvailable(page, tenantId);

    await test.step('open the canonical URL-backed People workspace and create the person', async () => {
      await page.goto(`/admin/tenants/${encodeURIComponent(tenantId)}/people`);
      await expect(page.getByRole('heading', { name: /People in/i })).toBeVisible();
      await page.getByRole('button', { name: 'Add person' }).click();
      await page.getByLabel('Display name').fill(displayName);
      await page.getByLabel('Sign-in name').fill(username);
      await page.getByLabel('Email address').fill(email);
      await page.getByLabel('Account activation').selectOption('INVITATION');
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByLabel('Primary Department').selectOption({ label: departmentName });
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByText(groupName, { exact: true }).click();
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByLabel('Initial responsibility').selectOption({ label: roleName });
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByRole('button', { name: /Add person and continue|Create person|Complete onboarding/i }).click();
      await expect(page.getByText(displayName)).toBeVisible({ timeout: 30_000 });
    });

    await test.step('verify Tenant membership, Department, Group, invitation and Effective Access', async () => {
      await page.getByRole('tab', { name: 'Membership' }).click();
      await expect(page.getByText('Tenant membership')).toBeVisible();
      await page.getByRole('tab', { name: 'Organization' }).click();
      await expect(page.getByText(departmentName)).toBeVisible();
      await expect(page.getByText(groupName)).toBeVisible();
      await page.getByRole('tab', { name: 'Access' }).click();
      await expect(page.getByText(new RegExp(roleName, 'i'))).toBeVisible();
      await page.getByRole('tab', { name: 'Security' }).click();
      await expect(page.getByText(/Invitation/i)).toBeVisible();
    });

    await test.step('verify object-centric membership from the Organization workspace', async () => {
      await page.goto(`/admin/tenants/${encodeURIComponent(tenantId)}/organization`);
      await expect(page.getByRole('heading', { name: /Manage structure and membership/i })).toBeVisible();
      await page.getByPlaceholder('Search Departments and Groups').fill(departmentName);
      await page.getByRole('button', { name: departmentName }).click();
      await page.getByRole('tab', { name: 'Members' }).click();
      await expect(page.getByText(displayName)).toBeVisible();
    });
  });
});
