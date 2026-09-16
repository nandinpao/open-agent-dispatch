import { expect, test, type Page, type Route } from '@playwright/test';

const now='2026-08-04T00:00:00Z'; const tenantId='tenant-a'; const requestHash='hash-critical-binding';
const cursor=<T>(items:T[])=>({items,nextCursor:'',hasMore:false}); const offset=<T>(items:T[])=>({items,page:0,size:250,totalElements:items.length,totalPages:1});

async function session(page:Page){
 await page.route('**/api/ui/runtime-capabilities',r=>r.fulfill({json:{contractVersion:'4.0',generatedAt:now,authentication:{sessionPath:'/api/session',legacyPasswordAdapterEnabled:false},surfaces:{iamAdministration:'ENABLED',uiCapabilityProjection:'ENABLED'}}}));
 await page.route('**/api/session/csrf',r=>r.fulfill({json:{headerName:'X-XSRF-TOKEN',parameterName:'_csrf',token:'csrf'}}));
 await page.route('**/api/session/preflight',r=>r.fulfill({json:{authenticated:true,principalType:'INSTANCE_ROOT',sessionValid:true,csrfReady:true,tenantContext:'',authorizationService:'AUTHENTICATED_SESSION_READY',permissionCatalog:'AVAILABLE',correlationId:'r7-test'}}));
 await page.route('**/api/session',r=>r.fulfill({json:{authenticationType:'CANONICAL_SESSION',userId:'root',username:'root',displayName:'Instance Root',roles:['INSTANCE_ROOT'],permissions:['*'],selectedTenantId:'',tenantChoices:[{tenantId,tenantCode:'TENANT-A',tenantName:'Tenant A',membershipStatus:'ACTIVE',roleSummary:['PLATFORM_ADMIN']}],requiredActions:[],credentialVersion:2,authenticatedAt:now,expiresAt:'2026-08-04T00:30:00Z',authenticationMethods:['PASSWORD','TOTP']}}));
}
async function backend(page:Page){
 await page.route('**/core-api/api/admin/access/**',async(route:Route)=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace(/^\/core-api/,'');
  if(path.endsWith('/role-bindings/hardening-preview'))return route.fulfill({json:{operation:'ROLE_BINDING_CREATE',requestHash,critical:true,approvalRequired:true,selfEscalation:false,beforePermissions:[],afterPermissions:['identity.user.read'],addedPermissions:['identity.user.read'],removedPermissions:[],conflicts:[],warnings:[]}});
  if(path.endsWith('/role-bindings/preview'))return route.fulfill({json:{tenantId,principalType:'USER',principalId:'user-1',roleId:'role-1',roleName:'User Reader',scopeType:'TENANT',scopeId:tenantId,effectiveAt:now,expiresAt:null,newlyEffectivePermissions:['identity.user.read'],alreadyEffectivePermissions:[],warnings:[]}});
  if(path.endsWith('/rbac-approvals'))return route.fulfill({json:[{approvalId:'approval-1',tenantId,operation:'ROLE_BINDING_CREATE',requestHash,requesterId:'requester',targetType:'ROLE_BINDING',targetId:'user-1:role-1',status:'APPROVED',approverId:'approver',decisionReason:'Independent review completed',requestedAt:now,expiresAt:'2026-08-04T00:30:00Z',decidedAt:now,consumedAt:null,version:2}]});
  if(req.method()==='POST')return route.fulfill({status:201,json:{bindingId:'binding-1',tenantId,principalType:'USER',principalId:'user-1',roleId:'role-1',scopeType:'TENANT',scopeId:tenantId,status:'ACTIVE',version:1}});
  if(path==='/api/admin/access/tenants')return route.fulfill({json:offset([{tenantId,tenantCode:'TENANT-A',tenantName:'Tenant A',status:'ACTIVE',createdAt:now,updatedAt:now,version:1}])});
  if(path.endsWith('/users'))return route.fulfill({json:cursor([{userId:'user-1',username:'user@example.com',email:'user@example.com',displayName:'User One',status:'ACTIVE',creationMode:'INVITATION',createdAt:now,updatedAt:now,version:1}])});
  if(path.endsWith('/groups'))return route.fulfill({json:offset([])});
  if(path.endsWith('/role-bindings'))return route.fulfill({json:cursor([])});
  if(path.endsWith('/responsibility-templates'))return route.fulfill({json:offset([])});
  if(path.endsWith('/departments'))return route.fulfill({json:offset([])});
  if(path.endsWith('/roles'))return route.fulfill({json:offset([{roleId:'role-1',tenantId,roleCode:'USER_READER',roleName:'User Reader',description:'Read users',roleType:'CUSTOM_TENANT_ROLE',systemManaged:false,status:'ACTIVE',createdAt:now,updatedAt:now,version:1}])});
  return route.fulfill({json:cursor([])});
 });
}

test('R7 attaches an independently approved payload before a critical Role Binding',async({page})=>{
 await session(page);await backend(page);
 await page.goto(`/access-management/assignments?tenantId=${tenantId}&assign=1&principalType=USER&principalId=user-1&roleId=role-1&scopeType=TENANT&scopeId=${tenantId}`);
 await expect(page).toHaveURL(new RegExp(`/admin/tenants/${tenantId}/access`));
 await expect(page.getByRole('heading',{name:'Who needs to do what, where, and for how long?'})).toBeVisible();
 await page.getByRole('button',{name:'Continue'}).click();
 await page.getByRole('button',{name:'Continue'}).click();
 await page.getByRole('button',{name:'Continue'}).click();
 await page.getByRole('button',{name:'Review access'}).click();
 await expect(page.getByText('Two-person approval required')).toBeVisible();
 await page.getByLabel('Approved request').selectOption('approval-1');
 await expect(page.getByRole('button',{name:'Assign responsibility'})).toBeEnabled();
});
