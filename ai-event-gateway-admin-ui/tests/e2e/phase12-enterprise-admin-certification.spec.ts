import { expect, test } from '@playwright/test';
import { expectDirectPageDenied, loginPersona, persona, requiredEnv } from './support/r6PersonaCertification';

function requireQualifiedRuntime() {
  if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') {
    throw new Error('Phase 12 certification refuses mocked APIs.');
  }
  if (process.env.PHASE12_CERTIFICATION_REQUIRED !== 'true') {
    throw new Error('Set PHASE12_CERTIFICATION_REQUIRED=true. Phase 12 runtime certification must never pass by skipping live evidence.');
  }
}

const tenantA = () => requiredEnv('R6_TENANT_A_ID');

test.describe.configure({ mode: 'serial' });

test.describe('Phase 12 beginner Enterprise Admin certification', () => {
  test.beforeAll(() => requireQualifiedRuntime());

  test('Add Person stays a five-step guided workflow with selector-first organization choices', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/people?action=ADD_PERSON`);
    await expect(page.getByRole('heading', { name: /Add a person to/i })).toBeVisible();
    for (const step of ['Person', 'Organization', 'Responsibility', 'Sign-in', 'Review']) {
      await expect(page.getByText(step, { exact: true }).first()).toBeVisible();
    }
    await expect(page.getByLabel('Person source')).toBeVisible();
    await expect(page.getByText('Guided onboarding', { exact: true })).toBeVisible();
    await expect(page.getByText(/Tenant ID/i)).toHaveCount(0);
  });

  test('Machine Access keeps configuration on one page and uses governed selectors in the create popup', async ({ page }) => {
    await loginPersona(page, persona('SECURITY_ADMIN'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/security?view=MACHINE`);
    await expect(page.getByRole('heading', { name: 'Service Accounts and client credentials' })).toBeVisible();
    await expect(page.getByText('New to Machine Access?')).toBeVisible();
    await page.getByRole('button', { name: 'Create Service Account' }).first().click();
    await expect(page.getByLabel('Business owner')).toBeVisible();
    await expect(page.getByLabel('Owning Department')).toBeVisible();
    await expect(page.getByLabel('Responsibility')).toBeVisible();
    await expect(page.getByLabel('API products')).toBeVisible();
    await expect(page.getByLabel('Allowed operations')).toBeVisible();
    await expect(page.getByLabel('Source Systems')).toBeVisible();
    await expect(page.getByLabel('Network profile')).toBeVisible();
    await expect(page.getByText('Permission Codes', { exact: false })).toBeVisible();
    await expect(page.getByPlaceholder(/role id|department id|tenant id|source system id/i)).toHaveCount(0);
  });

  test('Security is consolidated into four primary workspaces instead of technical sub-pages', async ({ page }) => {
    await loginPersona(page, persona('SECURITY_ADMIN'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/security`);
    for (const view of ['Overview', 'Sign-in & Protection', 'Machine Access', 'Audit']) {
      await expect(page.getByRole('button', { name: new RegExp(view) })).toBeVisible();
    }
    await expect(page.getByText(/secondary changes open in a popup/i)).toBeVisible();
  });

  test('Developer Mode never grants direct engineering-page authority', async ({ page }) => {
    await loginPersona(page, persona('VIEWER'), tenantA());
    await expectDirectPageDenied(page, '/cluster');
    await expectDirectPageDenied(page, '/runtime/rejected-connections');
    await expectDirectPageDenied(page, '/testing/ui-fixtures');
  });
});
