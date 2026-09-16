import { expect, test } from '@playwright/test';
test.describe('Phase 7F Operations UX',()=>{
 test('operations workspace preserves governed export and attachment wording',async({page})=>{await page.goto('/operations');await expect(page.getByRole('heading',{name:'Operations'})).toBeVisible();await page.getByRole('button',{name:'Exports'}).click();await expect(page.getByText('Authorize first; never download directly')).toBeVisible();await page.getByRole('button',{name:'Attachments'}).click();await expect(page.getByText('Attachment security gate')).toBeVisible();});
 test('support diagnostics state that sensitive values are omitted',async({page})=>{await page.goto('/operations');await page.getByRole('button',{name:'Audit & Support'}).click();await expect(page.getByText(/Secret, token, credential, payload/i)).toBeVisible();});
});
