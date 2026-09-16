import { expect, test } from '@playwright/test';

const pending = {
  requestId: 'access-request-1', uiActionId: 'task.detail.update', resourceType: 'TASK', resourceId: 'task-1',
  resourceVersionAtRequest: 7, requestedVisibility: 'STANDARD', validFrom: '2026-08-01T12:00:00Z',
  validTo: '2026-08-02T12:00:00Z', state: 'PENDING_APPROVAL', requestVersion: 2, grantVersion: 2,
  independentlyApproved: false, requesterIsCurrentUser: false, updatedAt: '2026-08-01T12:00:00Z',
};

test.describe('Phase 7E governed access-request integration', () => {
  test('submission creates pending approval and never claims active access', async ({ page }) => {
    await page.route('**/api/ui/access-requests', route => route.request().method() === 'POST'
      ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pending) })
      : route.continue());
    await page.goto('/identity-access/resource-access/access-requests');
    await page.getByLabel('Resource ID').fill('task-1');
    await page.getByLabel('Current resource version').fill('7');
    await page.getByLabel('Business purpose').fill('Temporary production investigation and repair.');
    await page.getByRole('button', { name: 'Submit for approval' }).click();
    await expect(page.getByText('Access request submitted')).toBeVisible();
    await expect(page.getByText(/is not active access/i)).toBeVisible();
  });

  test('requester cannot approve their own request', async ({ page }) => {
    await page.route('**/api/ui/access-requests/access-request-1/review', route => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify({ ...pending, requesterIsCurrentUser: true }),
    }));
    await page.goto('/identity-access/resource-access/access-requests');
    await page.getByLabel('Request ID').fill('access-request-1');
    await page.getByRole('button', { name: 'Load for review' }).click();
    await expect(page.getByText(/Separation of duties/i)).toBeVisible();
    await page.getByLabel('Review reason').fill('Independent review cannot be performed by requester.');
    await expect(page.getByRole('button', { name: 'Approve and activate' })).toBeDisabled();
  });

  for (const status of [403, 409, 412]) {
    test(`review preserves HTTP ${status} as actionable UX`, async ({ page }) => {
      await page.route('**/api/ui/access-requests/access-request-1/review', route => route.fulfill({
        status: 200, contentType: 'application/json', body: JSON.stringify(pending),
      }));
      await page.route('**/api/ui/access-requests/access-request-1/approve', route => route.fulfill({
        status, contentType: 'application/json', body: JSON.stringify({ code: `TEST_${status}`, message: 'safe error' }),
      }));
      await page.goto('/identity-access/resource-access/access-requests');
      await page.getByLabel('Request ID').fill('access-request-1');
      await page.getByRole('button', { name: 'Load for review' }).click();
      await page.getByLabel('Review reason').fill('Independent review completed with sufficient evidence.');
      await page.getByRole('button', { name: 'Approve and activate' }).click();
      await expect(page.getByText(new RegExp(`HTTP ${status}`))).toBeVisible();
    });
  }

  test('critical controls remain keyboard-labelled and browser storage stays empty', async ({ page }) => {
    await page.goto('/identity-access/resource-access/access-requests');
    await expect(page.getByRole('heading', { name: 'Temporary Access Request' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Independent Access Review' })).toBeVisible();
    await page.keyboard.press('Tab');
    const keys = await page.evaluate(() => [...Object.keys(localStorage), ...Object.keys(sessionStorage)]
      .filter(key => /capability|permission|access-request|scope-grant/i.test(key)));
    expect(keys).toEqual([]);
  });
});
