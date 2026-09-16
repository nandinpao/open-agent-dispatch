import { expect, test } from '@playwright/test';

const mockCore = 'http://127.0.0.1:18080';

async function resetHydration(request: import('@playwright/test').APIRequestContext, mode = 'normal') {
  const response = await request.post(`${mockCore}/__certification__/mode?hydration=${mode}`);
  expect(response.ok()).toBeTruthy();
}

test.beforeEach(async ({ request }) => resetHydration(request));

test('hard navigation bootstraps safely, batches dynamic actions, and never persists capability data', async ({ page, request }) => {
  await page.goto('/tasks/task-ready');
  await expect(page.locator('[data-ui-bootstrap="task.detail"]')).toHaveAttribute('data-ui-bootstrap-outcome', 'PAGE');

  const retry = page.getByRole('button', { name: /retry dispatch|checking access/i }).first();
  await expect(retry).toBeDisabled();
  await expect(retry).toHaveAttribute('data-display-mode', /HYDRATING|UNAVAILABLE/);

  await expect(page.getByRole('button', { name: 'Retry dispatch' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Cancel task' })).toBeDisabled();
  await expect(page.getByText(/current access does not allow/i).first()).toBeVisible();

  const storageDump = await page.evaluate(() => ({
    local: Object.entries(localStorage), session: Object.entries(sessionStorage),
  }));
  expect(JSON.stringify(storageDump)).not.toMatch(/task-ready|task\.detail|task\.update|capabilit/i);

  const metrics = await (await request.get(`${mockCore}/__certification__/metrics`)).json();
  expect(metrics.hydrationRequests).toBeGreaterThan(0);
  expect(metrics.maxBatchContexts).toBeLessThanOrEqual(20);
});

test('F5 and new-tab navigation use the same anti-enumeration shell without leaking resource identity', async ({ page, context }) => {
  await page.goto('/tasks/task-hidden-sensitive-id');
  await expect(page.getByRole('heading', { name: 'Resource not found or unavailable' })).toBeVisible();
  await expect(page).toHaveTitle('Resource unavailable | OpenDispatch');
  await expect(page.locator('body')).not.toContainText('task-hidden-sensitive-id');
  const metadata = await page.locator('meta').evaluateAll((items) => items.map((item) => item.getAttribute('content') ?? '').join(' '));
  expect(metadata).not.toContain('task-hidden-sensitive-id');

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Resource not found or unavailable' })).toBeVisible();

  const second = await context.newPage();
  await second.goto('/tasks/task-hidden-sensitive-id');
  await expect(second.getByRole('heading', { name: 'Resource not found or unavailable' })).toBeVisible();
  await expect(second.locator('body')).not.toContainText('task-hidden-sensitive-id');
  const secondMetadata = await second.locator('meta').evaluateAll((items) => items.map((item) => item.getAttribute('content') ?? '').join(' '));
  expect(secondMetadata).not.toContain('task-hidden-sensitive-id');
});

test('safe shell outcomes remain distinct and protected', async ({ page }) => {
  await page.goto('/tasks/task-step-up');
  await expect(page.getByRole('heading', { name: 'Reauthentication required' })).toBeVisible();
  await page.goto('/tasks/task-request-access');
  await expect(page.getByRole('heading', { name: 'Access required' })).toBeVisible();
  await page.goto('/tasks/task-resource-changed');
  await expect(page.getByRole('heading', { name: 'Resource changed' })).toBeVisible();
});

test('soft navigation opens the intercepted task modal while direct refresh remains independently guarded', async ({ page }) => {
  await page.goto('/tasks');
  const taskLink = page.locator('a[href^="/tasks/"]').filter({ hasNotText: /failure queue/i }).first();
  await expect(taskLink).toBeVisible();
  await taskLink.click();
  await expect(page.getByRole('dialog', { name: 'Task detail' })).toBeVisible();
  const currentUrl = page.url();
  await page.reload();
  await expect(page.locator('[data-ui-bootstrap="task.detail"]')).toHaveAttribute('data-ui-bootstrap-outcome', 'PAGE');
  expect(page.url()).toBe(currentUrl);
});

test('stale hydration drops the old result and forces secure refresh recovery', async ({ page, request }) => {
  await resetHydration(request, 'stale');
  await page.goto('/tasks/task-ready');
  await expect(page.getByRole('dialog', { name: /page changed/i })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Review latest' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Retry dispatch' })).toBeDisabled();
});

test('Core proxy preserves 403, 409 and 412 for UI capability endpoints', async ({ page }) => {
  await page.goto('/tasks/task-ready');
  for (const status of [403, 409, 412]) {
    const observed = await page.evaluate(async (value) => {
      const response = await fetch(`/core-api/api/ui/capabilities:batch?forceStatus=${value}`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ contractVersion: '1.0', contexts: [] }),
      });
      return response.status;
    }, status);
    expect(observed).toBe(status);
  }
});
