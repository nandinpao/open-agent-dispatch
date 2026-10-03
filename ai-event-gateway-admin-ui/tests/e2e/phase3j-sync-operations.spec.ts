import { expect, test } from '@playwright/test';

test('configuration and canonical integration recovery are separate authorities', async ({ page }) => {
  await page.goto('/settings/integrations');
  await expect(page.getByRole('heading', { name: /Issue Tracking Integration|Integration Configuration/ }).first()).toBeVisible();
  await expect(page.getByRole('link', { name: /Open integration operations|Open Sync Operations/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Projection Queue' })).toHaveCount(0);

  await page.goto('/operations/integration-sync');
  await expect(page.getByRole('heading', { name: 'Integration Sync Operations' }).first()).toBeVisible();
  for (const name of ['Overview', 'Failures', 'Recovery', 'Provider Observations']) {
    await expect(page.getByRole('button', { name: new RegExp(name, 'i') })).toBeVisible();
  }
});
