import { expect, test } from '@playwright/test';
import { authenticateAdmin, selectTenantIfAvailable } from './support/stage9WorkflowHelpers';
const tenantId=process.env.PHASE7_TENANT_ID??'';
test.describe('Phase 7 Security, Credentials and Audit live workflow',()=>{
 test.skip(!tenantId,'PHASE7_TENANT_ID is required.');
 test('updates policy, governs a Service Account, issues and revokes a token, and reads immutable audit evidence',async({page})=>{
  if(process.env.NEXT_PUBLIC_USE_MOCK==='true'||process.env.PLAYWRIGHT_USE_MOCK==='true')throw new Error('Phase 7 live acceptance cannot run with mocked APIs.');
  await authenticateAdmin(page);await selectTenantIfAvailable(page,tenantId);
  await page.goto(`/admin/tenants/${encodeURIComponent(tenantId)}/security?view=OVERVIEW`);
  await expect(page.getByRole('heading',{name:/Protect /})).toBeVisible();
  await page.getByRole('button',{name:'Policies'}).click();
  await expect(page.getByText('Governed security settings')).toBeVisible();
  await expect(page.getByText('Change preview')).toBeVisible();
  await page.getByRole('button',{name:'Credentials'}).click();
  await expect(page.getByText('Service Accounts and tokens')).toBeVisible();
  await expect(page.getByText(/Raw Permission Codes.*derived from governed catalogs/i)).toBeVisible();
  await page.getByRole('button',{name:'Audit'}).click();
  await expect(page.getByText(/Immutable technical evidence/i).first()).toBeVisible();
  await page.getByRole('button',{name:'Sessions'}).click();
  await expect(page.getByText(/active sessions|No active sessions/i).first()).toBeVisible();
 });
});
