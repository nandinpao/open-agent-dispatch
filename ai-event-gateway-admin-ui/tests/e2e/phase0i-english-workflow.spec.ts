import { expect, test } from '@playwright/test';
import { authenticateAdmin } from './support/stage9WorkflowHelpers';

const primaryNavigation = [
  'Dashboard',
  'Source Systems',
  'Dispatch',
  'Agents',
  'Tasks',
  'Incidents',
  'Integrations',
  'Administration',
] as const;

test.describe('Phase 0I English-only product workflow', () => {
  test('primary product navigation and onboarding are English and accessible', async ({ page }) => {
    await authenticateAdmin(page);
    await page.goto('/dashboard');

    await expect(page.locator('html')).toHaveAttribute('lang', 'en-US');
    const navigation = page.getByRole('navigation', { name: 'Main navigation' });
    await expect(navigation).toBeVisible();
    for (const label of primaryNavigation) {
      await expect(navigation.getByRole('link', { name: label, exact: true })).toBeVisible();
    }

    await expect(page.getByRole('heading', { name: 'Set Up Dispatch from a Source System' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Create Your First Source System' })).toBeVisible();
  });

  test('Source Systems and Integrations use the current English product model', async ({ page }) => {
    await authenticateAdmin(page);
    await page.goto('/source-systems');
    await expect(page.getByRole('heading', { name: 'Source Systems' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add Source System' })).toBeVisible();

    await page.goto('/settings/integrations');
    await expect(page.getByRole('heading', { name: 'Integration Administration' })).toBeVisible();
    await expect(page.getByText('Scoped Integration Identity')).toBeVisible();
    await expect(page.getByText('Project Permission Probe')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Open Sync Operations' })).toBeVisible();

    await page.goto('/operations/integration-sync');
    await expect(page.getByRole('heading', { name: 'Integration Sync Operations' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Sync Operations sections' })).toBeVisible();
  });
});
