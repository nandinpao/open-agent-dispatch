import { expect, test } from '@playwright/test';

test('Issue Tracking workspace separates connection, principal, credentials, mapping and probe', async ({ page }) => {
  await page.goto('/settings/integrations');
  await expect(page.getByText('Issue Tracking connection workspace')).toBeVisible();
  await expect(page.getByText('Credential metadata and rotation impact')).toBeVisible();
  await expect(page.getByText('Project mapping simulator')).toBeVisible();
  await expect(page.getByText('Project Permission Probe')).toBeVisible();
  await expect(page.locator('body')).not.toContainText(/private key|copy secret/i);
});

test('Sync Operations exposes queue, Dead Letter and conflict authority guidance', async ({ page }) => {
  await page.goto('/operations/integration-sync');
  await page.getByRole('button', { name: /Projection Queue/i }).click();
  await expect(page.getByText('Sync queue operational states')).toBeVisible();
  await expect(page.getByText('WAITING_FOR_INDEX')).toBeVisible();
  await page.getByRole('button', { name: /External Conflicts/i }).click();
  await expect(page.getByText('OpenDispatch / external issue conflict resolution')).toBeVisible();
  await expect(page.getByText(/external provider never becomes Task authority/i)).toBeVisible();
});

test('Agent list supports blocking filters and actionable blocking cards', async ({ page }) => {
  await page.goto('/agents');
  await expect(page.getByLabel('Blocking')).toBeVisible();
  await expect(page.getByLabel('Agent blocking status').first()).toBeVisible();
  await expect(page.getByText(/Safest next action/i).first()).toBeVisible();
});

test('Agent detail separates Dispatch Access, runtime binding, capacity and credential metadata', async ({ page }) => {
  await page.goto('/agents/agent-no-service-scope');
  await expect(page.getByText('Dispatch Access', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: /Configure access|Edit access/i })).toBeVisible();
  await expect(page.getByText('Agent runtime diagnostics')).toBeVisible();
  await expect(page.getByText(/Tokens and raw runtime payloads are never shown/i)).toBeVisible();
});
