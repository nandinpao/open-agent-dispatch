import { expect, test } from '@playwright/test';

const now = '2026-08-05T00:00:00Z';

const capabilitySnapshot = {
  contractVersion: '4.0',
  generatedAt: now,
  authentication: { sessionPath: '/api/session', legacyPasswordAdapterEnabled: false },
  surfaces: {
    iamAdministration: 'ENABLED',
    resourceAccessAdministration: 'DISABLED',
    uiCapabilityProjection: 'ENABLED',
    enforcementActivation: 'DISABLED',
    a2aOperations: 'ENABLED',
    issueTracking: 'DISABLED',
  },
};

const rootSession = {
  authenticationType: 'CANONICAL_SESSION',
  userId: 'root',
  username: 'root',
  displayName: 'Instance Root',
  roles: ['INSTANCE_ROOT'],
  permissions: ['*'],
  selectedTenantId: '',
  tenantChoices: [],
  requiredActions: [],
  credentialVersion: 2,
  authenticatedAt: now,
  expiresAt: '2026-08-05T00:30:00Z',
  authenticationMethods: ['PASSWORD', 'TOTP'],
};

test('repeated unauthorized revalidation does not remount runtime capability authority', async ({ page }) => {
  let runtimeCapabilityRequests = 0;
  let sessionRequests = 0;

  await page.route('**/api/ui/runtime-capabilities', async (route) => {
    runtimeCapabilityRequests += 1;
    await route.fulfill({ json: capabilitySnapshot });
  });
  await page.route('**/api/session', async (route) => {
    sessionRequests += 1;
    await route.fulfill({ json: rootSession });
  });
  await page.route('**/api/ui/entitlements', route => route.fulfill({
    json: {
      contractVersion: '2.0',
      workspaceKind: 'INSTANCE_ROOT',
      tenantId: '',
      generatedAt: now,
      navigation: [{ featureId: 'instance-administration', section: 'PLATFORM', order: 210, route: '/instance-administration', label: 'Platform Administration', purpose: 'Root administration' }],
      pages: { 'instance-administration': { featureId: 'instance-administration', route: '/instance-administration', allowed: true, denialReason: '', surface: 'PLATFORM' } },
      actions: [],
    },
  }));
  await page.route('**/api/realtime/events', route => route.fulfill({
    status: 200,
    contentType: 'text/event-stream; charset=utf-8',
    body: ': connected\n\n',
  }));

  await page.goto('/instance-administration');
  await expect(page.getByRole('heading', { name: 'Instance Administration' })).toBeVisible();
  await expect.poll(() => runtimeCapabilityRequests).toBe(1);

  await page.evaluate(() => {
    for (let index = 0; index < 8; index += 1) {
      window.dispatchEvent(new CustomEvent('ai-event-gateway-admin:unauthorized', {
        detail: { status: 401, path: `/api/test/${index}`, plane: 'core' },
      }));
    }
  });

  await expect.poll(() => sessionRequests).toBeGreaterThanOrEqual(2);
  await page.waitForTimeout(500);
  expect(runtimeCapabilityRequests).toBe(1);
  await expect(page.getByRole('heading', { name: 'Instance Administration' })).toBeVisible();
});
