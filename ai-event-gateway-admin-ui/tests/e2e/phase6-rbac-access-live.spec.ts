import { expect, test } from '@playwright/test';
import {
  entitlements,
  loginPersona,
  persona,
  requiredEnv,
  session,
} from './support/r6PersonaCertification';

const tenantA = () => requiredEnv('R6_TENANT_A_ID');
const assigneeName = () => requiredEnv('R6_REVOCATION_USER_NAME');
const responsibilityName = () => requiredEnv('R6_REVOCATION_RESPONSIBILITY_NAME');
const expectedRole = () => requiredEnv('R6_REVOCATION_EXPECTED_ROLE');

test.describe.configure({ mode: 'serial' });

test.describe('Phase 6 dynamic Role assignment and stale-privilege revocation', () => {
  test('assigns a low-risk Responsibility, proves it in a real Session, revokes it and removes stale privilege', async ({ page, browser }) => {
    if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') {
      throw new Error('Phase 6 dynamic RBAC certification cannot run with mocked APIs.');
    }
    if (process.env.R6_CERTIFICATION_REQUIRED !== 'true') {
      throw new Error('R6_CERTIFICATION_REQUIRED=true is mandatory.');
    }

    // Fixture invariant: this disposable account starts with no business Responsibility.
    const beforeContext = await browser.newContext();
    try {
      const beforePage = await beforeContext.newPage();
      await loginPersona(beforePage, persona('REVOCATION_USER'), tenantA());
      expect((await session(beforePage)).roles).not.toContain(expectedRole());
      expect((await entitlements(beforePage)).navigation.map((item) => item.featureId)).toEqual(['my-account']);
    } finally {
      await beforeContext.close();
    }

    // ACCESS_ADMIN owns Role Binding administration but not unrelated Tenant administration.
    await loginPersona(page, persona('ACCESS_ADMIN'), tenantA());
    await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/access?view=TEMPLATES`);
    await expect(page.getByRole('heading', { name: /Responsibilities in/i })).toBeVisible();

    await test.step('assign the low-risk certification Responsibility', async () => {
      const card = page.getByRole('article').filter({ hasText: responsibilityName() });
      await expect(card).toBeVisible();
      await card.getByRole('button', { name: 'Assign this responsibility' }).click();
      await page.getByLabel('Assigned to').selectOption('USER');
      await page.getByLabel('Person').fill(assigneeName());
      await page.getByText(assigneeName(), { exact: true }).click();
      await page.getByRole('button', { name: 'Continue' }).click();
      await expect(page.getByText(responsibilityName(), { exact: true })).toBeVisible();
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByLabel('Applies to').selectOption('TENANT');
      await page.getByRole('button', { name: 'Continue' }).click();
      await page.getByLabel('Duration').selectOption('90_DAYS');
      await page.getByRole('button', { name: 'Continue' }).click();
      if (await page.getByText('Two-person approval required').isVisible().catch(() => false)) {
        throw new Error('R6 revocation fixture must use a low-risk Responsibility that does not require two-person approval. Do not skip this certification.');
      }
      await page.getByRole('button', { name: /Assign access|Confirm assignment/i }).click();
    });

    const userContext = await browser.newContext();
    try {
      const userPage = await userContext.newPage();
      await loginPersona(userPage, persona('REVOCATION_USER'), tenantA());
      expect((await session(userPage)).roles).toContain(expectedRole());
      expect((await entitlements(userPage)).navigation.map((item) => item.featureId)).not.toEqual(['my-account']);

      await test.step('revoke the same canonical Role Binding', async () => {
        await page.goto(`/admin/tenants/${encodeURIComponent(tenantA())}/access?view=ASSIGNMENTS`);
        await page.getByLabel('Search assignments').fill(assigneeName());
        await page.getByRole('button', { name: 'Apply' }).click();
        const assignment = page.getByRole('article').filter({ hasText: responsibilityName() }).filter({ hasText: assigneeName() });
        await expect(assignment).toBeVisible();
        await assignment.getByRole('button', { name: 'Review and revoke' }).click();
        await expect(page.getByRole('heading', { name: 'Review access revocation' })).toBeVisible();
        await page.getByLabel('Reason').selectOption('Access review correction');
        await page.getByRole('button', { name: /Revoke access|Confirm revocation/i }).click();
        await expect(page.getByText(/was revoked/i)).toBeVisible();
      });

      // Existing user Session may be invalidated or may stay authenticated with a refreshed authority set.
      // Both are secure outcomes; retaining the revoked Role is not.
      const staleSessionResponse = await userPage.request.get('/api/session');
      if (staleSessionResponse.status() === 200) {
        const staleSession = await staleSessionResponse.json() as { roles: string[] };
        expect(staleSession.roles).not.toContain(expectedRole());
        const refreshed = await entitlements(userPage);
        expect(refreshed.navigation.map((item) => item.featureId)).toEqual(['my-account']);
      } else {
        expect([401, 403]).toContain(staleSessionResponse.status());
      }
    } finally {
      await userContext.close();
    }
  });
});
