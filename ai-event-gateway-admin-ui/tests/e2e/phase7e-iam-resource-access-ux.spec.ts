import { expect, test } from '@playwright/test';

test.describe('Phase 7E IAM and Resource Access UX',()=>{
  test('Resource Access workspace keeps access requests temporary and non-authoritative',async({page})=>{
    await page.goto('/identity-access/resource-access/access-requests');
    await expect(page.getByRole('heading',{name:'Access Requests'})).toBeVisible();
    await expect(page.getByText(/does not activate access/i)).toBeVisible();
    await expect(page.getByRole('button',{name:'Submit for approval'})).toBeDisabled();
    await expect(page.getByRole('heading',{name:'Independent Access Review'})).toBeVisible();
  });
  test('Scope Grant and Explicit Deny surfaces explain lifecycle safety',async({page})=>{
    await page.goto('/identity-access/resource-access/grants');
    await expect(page.getByText(/grant is policy data, not an executable authorization token/i)).toBeVisible();
    await page.goto('/identity-access/resource-access/denies');
    await expect(page.getByText(/deny has precedence/i)).toBeVisible();
  });
  test('Role and catalog workspaces expose impact and immutable lifecycle',async({page})=>{
    await page.route('**/api/session/preflight', route => route.fulfill({ json: { authenticated: true, principalType: 'INSTANCE_ROOT', sessionValid: true, csrfReady: true, tenantContext: '', authorizationService: 'AUTHENTICATED_SESSION_READY', permissionCatalog: 'AVAILABLE', correlationId: 'phase7e-test' } }));
    await page.goto('/identity-access/roles');
    await expect(page.getByText(/permission bundle impact/i)).toBeVisible();
    await page.goto('/platform-administration/permission-catalog');
    await expect(page.getByLabel('Permission Catalog lifecycle')).toBeVisible();
  });
});
