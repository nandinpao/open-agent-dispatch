import { expect, test } from '@playwright/test';

test('configuration and sync operations are separate workspaces', async ({ page }) => {
  await page.goto('/settings/integrations');
  await expect(page.getByRole('heading', { name: 'Integration Configuration' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Open Sync Operations' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Projection Queue' })).toHaveCount(0);

  await page.goto('/operations/integration-sync');
  await expect(page.getByRole('heading', { name: 'Integration Sync Operations' }).first()).toBeVisible();
  for (const name of ['Projection Queue','Webhook Inbox','External Conflicts','Human Action Candidates','Relay Topologies','Reconciliation','Dead Letter']) {
    await expect(page.getByRole('button', { name: new RegExp(name) })).toBeVisible();
  }
});
