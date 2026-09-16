import { expect, test } from '@playwright/test';

test('Task Detail explains the active blocker and safest next action', async ({ page }) => {
  await page.goto('/tasks/task-blocked-no-service-scope');
  const blocker = page.getByRole('region', { name: 'Task blocking reason' });
  await expect(blocker).toBeVisible();
  await expect(blocker).toContainText('Dispatch access is not configured');
  await expect(blocker).toContainText('Safest next action');
  await expect(blocker).toContainText(/Operator can act|Requires another authority/);
});

test('restricted Task chain nodes preserve topology without resource enumeration', async ({ page }) => {
  await page.goto('/tasks/task-chain-restricted');
  const restricted = page.getByText('Restricted task').first();
  await expect(restricted).toBeVisible();
  await expect(page.locator('body')).not.toContainText('confidential child title');
  await expect(page.locator('a', { hasText: 'Restricted task' })).toHaveCount(0);
  await expect(page.getByText(/title, owner, domain, and identifiers are not disclosed/i)).toBeVisible();
});

test('Task delegation guidance describes capability-first Core authority without topology selectors', async ({ page }) => {
  await page.goto('/tasks/task-ready');
  await page.getByRole('button', { name: 'Delegation model' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(/capability-first/i);
  await expect(dialog).toContainText(/Core/i);
  await expect(dialog).toContainText(/A2A Operations/i);
  await expect(dialog).not.toContainText(/Target Domain/i);
  await expect(dialog).not.toContainText(/Target Agent Pool/i);
  await expect(dialog).not.toContainText(/Target Agent/i);
});

test('Legacy A2A archive is read-only', async ({ page }) => {
  await page.goto('/a2a-governance');
  await expect(page.getByText(/Legacy A2A Archive/i).first()).toBeVisible();
  await expect(page.getByText(/read-only/i).first()).toBeVisible();
  await expect(page.getByRole('button', { name: /new policy/i })).toHaveCount(0);
});

test('Agent, human accepted and external issue results remain separate for historical evidence', async ({ page }) => {
  await page.goto('/a2a-operations/request-result-lanes');
  await expect(page.getByText('Agent Result', { exact: true })).toBeVisible();
  await expect(page.getByText('Human Accepted Result', { exact: true })).toBeVisible();
  await expect(page.getByText('External Issue Projection', { exact: true })).toBeVisible();
  await expect(page.getByText(/does not, by itself, complete/i)).toBeVisible();
});
