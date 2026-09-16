import { expect, test, type Page } from '@playwright/test';
const session={authenticationType:'IAM',userId:'release-admin',username:'release-admin',displayName:'Release Administrator',roles:['TENANT_ADMIN'],permissions:['resource.governance.read','resource.review.manage','resource.decision.explain','resource.decision.simulate','resource.release.read'],selectedTenantId:'tenant-a',tenantChoices:[{tenantId:'tenant-a',tenantCode:'TENANT-A',tenantName:'Tenant A',membershipStatus:'ACTIVE',roleSummary:['TENANT_ADMIN']}],requiredActions:[],authenticatedAt:'2026-07-29T00:00:00Z',expiresAt:'2026-07-29T08:00:00Z'};
async function mock(page:Page){
 await page.route('**/api/session',r=>r.fulfill({json:session}));
 await page.route('**/core-api/api/resource-access/governance/summary',r=>r.fulfill({json:{activeGrants:4,grantsExpiringSoon:1,activeDenies:0,openOrphans:0,overdueReviewItems:0,activeReviewCampaigns:0,shadowMismatches24h:0,staleDescriptors:0,measuredAt:'2026-07-29T00:00:00Z'}}));
 await page.route('**/core-api/api/resource-access/decisions/simulate',r=>r.fulfill({json:{decisionId:'simulation-1',effect:'DENY',mode:'SIMULATION',shadowOnly:true,grantedVisibility:'NONE',reasonCodes:['RESOURCE_SCOPE_NOT_MATCHED'],safeMessage:'No Resource Scope evidence matched.',matchedRoleBindings:[],matchedScopeGrants:[],matchedParticipants:[],matchedOwnershipEvidence:[],matchedDenies:[],matchedScopeSources:[],evidenceRefs:[]}}));
}
test.describe('P4RA-I Resource Access release browser gate',()=>{
 test('governance route is permission gated and exposes shadow evidence',async({page})=>{await mock(page);await page.goto('/identity-access/resource-access');await expect(page.getByRole('heading',{name:'Resource Access Governance'})).toBeVisible();await expect(page.getByText('Shadow Mismatches')).toBeVisible();await expect(page.getByText('Deny always wins')).toBeVisible();});
 test('decision simulation remains explicitly non-executable',async({page})=>{await mock(page);await page.goto('/identity-access/resource-access/decision-simulator');await expect(page.getByText(/Simulation only/i)).toBeVisible();});
});
