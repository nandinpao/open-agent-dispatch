import { expect, test, type Page } from '@playwright/test';
import { loginPersona, persona, requiredEnv, session } from './support/r6PersonaCertification';

type ResourceType = 'TASK'|'AGENT'|'SOURCE_SYSTEM'|'EVENT'|'ISSUE'|'INCIDENT';
type OperationalHit = { type: ResourceType; id: string; title: string; ownerDepartmentId?: string; ownerGroupId?: string };
type DashboardSnapshot = {
  counts: Array<{type:ResourceType;value:number;capped:boolean}>;
  recent: OperationalHit[];
  accessibleTypes: ResourceType[];
  tenantWideRuntimeDiagnosticsAllowed: boolean;
};

const tenantA = () => requiredEnv('R6_TENANT_A_ID');
const tenantB = () => requiredEnv('R6_TENANT_B_ID');
const resourceTypes: ResourceType[] = ['TASK','AGENT','SOURCE_SYSTEM','EVENT','ISSUE','INCIDENT'];

function requireQualifiedRuntime() {
  if (process.env.NEXT_PUBLIC_USE_MOCK === 'true' || process.env.PLAYWRIGHT_USE_MOCK === 'true') throw new Error('RS9 refuses mocked APIs.');
  if (process.env.RS9_CERTIFICATION_REQUIRED !== 'true') throw new Error('Set RS9_CERTIFICATION_REQUIRED=true. RS9 must never pass through skipped runtime evidence.');
}

function corePath(type: ResourceType): string {
  if (type === 'TASK') return '/api/tasks?limit=500';
  if (type === 'AGENT') return '/admin/agents?limit=500';
  if (type === 'SOURCE_SYSTEM') return '/admin/source-systems';
  if (type === 'EVENT') return '/admin/business-events?limit=500';
  if (type === 'ISSUE') return '/api/issues?limit=500';
  return '/api/incidents?limit=500';
}

function idFrom(type: ResourceType, value: Record<string, unknown>): string | undefined {
  const keys = type === 'TASK' ? ['taskId','id']
    : type === 'AGENT' ? ['agentId','id']
    : type === 'SOURCE_SYSTEM' ? ['sourceSystemId','sourceSystem','id']
    : type === 'EVENT' ? ['eventId','id']
    : type === 'ISSUE' ? ['linkId','externalIssueKey','id']
    : ['incidentId','id'];
  for (const key of keys) if (typeof value[key] === 'string' && String(value[key]).trim()) return String(value[key]);
  return undefined;
}

function unwrapRows(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) return value.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object');
  if (!value || typeof value !== 'object') return [];
  const root = value as Record<string, unknown>;
  for (const key of ['items','content','records','rows','results']) if (Array.isArray(root[key])) return unwrapRows(root[key]);
  if (root.data && typeof root.data === 'object') return unwrapRows(root.data);
  return [];
}

async function directIds(page: Page, type: ResourceType): Promise<{status:number; ids:string[]}> {
  const response = await page.request.get(`/core-api${corePath(type)}`);
  if (!response.ok()) return { status: response.status(), ids: [] };
  const rows = unwrapRows(await response.json());
  return { status: response.status(), ids: rows.map((row)=>idFrom(type,row)).filter((id): id is string => Boolean(id)) };
}

async function dashboard(page: Page): Promise<DashboardSnapshot> {
  const response = await page.request.get('/api/operational-scope/dashboard');
  expect(response.status()).toBe(200);
  return await response.json() as DashboardSnapshot;
}

async function searchIds(page: Page, type: ResourceType): Promise<string[]> {
  const response = await page.request.get(`/api/operational-scope/search?type=${type}&limit=200`);
  expect(response.status()).toBe(200);
  const body = await response.json() as {items:OperationalHit[]};
  return body.items.map((item)=>item.id);
}

async function exportRows(page: Page, type: ResourceType): Promise<Array<Record<string, unknown>>> {
  const response = await page.request.get(`/api/operational-scope/export?type=${type}&format=JSON&limit=100`);
  expect(response.status()).toBe(200);
  return await response.json() as Array<Record<string, unknown>>;
}

async function expectResourceDenied(page: Page, path: string) {
  const response = await page.request.get(`/core-api${path}`);
  expect([403,404], `Cross-scope resource ${path} must not be readable`).toContain(response.status());
}

async function certifyChannelParity(page: Page) {
  const snap = await dashboard(page);
  for (const type of resourceTypes) {
    const direct = await directIds(page,type);
    const count = snap.counts.find((item)=>item.type===type);
    expect(count, `Dashboard must report ${type}`).toBeTruthy();
    if (direct.status === 200) {
      expect(snap.accessibleTypes, `${type} must be marked accessible when its scoped domain endpoint is readable`).toContain(type);
      expect(count?.value, `${type} Dashboard count must equal the same scoped resource page scan`).toBe(direct.ids.length);
      const searched = await searchIds(page,type);
      for (const id of searched) expect(direct.ids, `Search ${type} result must come from currently accessible rows`).toContain(id);
      if (direct.ids.length <= 200) expect(new Set(searched)).toEqual(new Set(direct.ids));
      const exported = await exportRows(page,type);
      const exportedIds = exported.map((row)=>String(row.id ?? '')).filter(Boolean);
      for (const id of exportedIds) expect(direct.ids, `Export ${type} result must come from currently accessible rows`).toContain(id);
      for (const row of exported) {
        for (const sensitive of ['payload','credential','storageObjectRef','providerUrl','externalIssueUrl','attachmentContent']) expect(row).not.toHaveProperty(sensitive);
      }
    } else {
      expect([401,403,404]).toContain(direct.status);
      expect(snap.accessibleTypes).not.toContain(type);
      expect(count?.value).toBe(0);
    }
  }
}

test.describe.configure({ mode:'serial' });

test.describe('RS9 Resource Scope release certification', () => {
  test.beforeAll(()=>requireQualifiedRuntime());

  test('beginner sign-in and main workspace do not ask Humans for internal Tenant IDs', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByLabel('Tenant')).toHaveCount(0);
    await expect(page.getByLabel('Company workspace')).toBeVisible();
    await expect(page.getByPlaceholder('Example: kuang-ho')).toBeVisible();
    await expect(page.getByText('Workspace code or Tenant ID', {exact:true})).toHaveCount(0);
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    await page.goto('/dashboard');
    await expect(page.getByRole('heading',{name:'Start here'})).toBeVisible();
    await expect(page.getByRole('button',{name:'Search my work'})).toBeVisible();
    await expect(page.getByRole('button',{name:'Export'})).toBeVisible();
  });

  test('Search, Export and advanced diagnostics stay on the Dashboard through drawers/selectors', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    await page.goto('/dashboard');
    await page.getByRole('button',{name:'Search my work'}).click();
    await expect(page.getByRole('heading',{name:'Search my accessible work'})).toBeVisible();
    await expect(page.getByText('Your company workspace is resolved automatically.')).toBeVisible();
    await expect(page.getByLabel('Resource types')).toBeVisible();
    await page.keyboard.press('Escape');
    await page.getByRole('button',{name:'Export'}).click();
    await expect(page.getByRole('heading',{name:'Export an authorized summary'})).toBeVisible();
    await expect(page.getByLabel('Resource type')).toBeVisible();
    await expect(page.getByLabel('Format')).toBeVisible();
    await expect(page.getByLabel('Maximum rows')).toBeVisible();
  });

  test('Issues & Events remains one primary workspace instead of separate beginner landing pages', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    await page.goto('/issues-events');
    await expect(page.getByRole('button',{name:/Business Issues/i})).toBeVisible();
    await expect(page.getByRole('button',{name:/Business Events/i})).toBeVisible();
    await expect(page.getByText(/More tools/i)).toBeVisible();
  });

  test('Tenant Administrator has Dashboard/Search/Export parity with scoped resource pages', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    expect((await session(page)).selectedTenantId).toBe(tenantA());
    await certifyChannelParity(page);
  });

  test('IT Department Administrator has the same data boundary across resource page, Dashboard, Search and Export', async ({ page }) => {
    await loginPersona(page, persona('IT_DEPARTMENT_ADMIN'), tenantA());
    await certifyChannelParity(page);
    const own = requiredEnv('RS9_IT_TASK_ID');
    const other = requiredEnv('RS9_HR_TASK_ID');
    const ownResponse = await page.request.get(`/core-api/api/tasks/${encodeURIComponent(own)}`);
    expect(ownResponse.status()).toBe(200);
    await expectResourceDenied(page, `/api/tasks/${encodeURIComponent(other)}`);
    expect(await searchIds(page,'TASK')).toContain(own);
    expect(await searchIds(page,'TASK')).not.toContain(other);
    expect((await exportRows(page,'TASK')).map((row)=>row.id)).toContain(own);
    expect((await exportRows(page,'TASK')).map((row)=>row.id)).not.toContain(other);
  });

  test('HR Department Administrator is isolated from IT across the same channels', async ({ page }) => {
    await loginPersona(page, persona('HR_DEPARTMENT_ADMIN'), tenantA());
    await certifyChannelParity(page);
    const own = requiredEnv('RS9_HR_TASK_ID');
    const other = requiredEnv('RS9_IT_TASK_ID');
    const ownResponse = await page.request.get(`/core-api/api/tasks/${encodeURIComponent(own)}`);
    expect(ownResponse.status()).toBe(200);
    await expectResourceDenied(page, `/api/tasks/${encodeURIComponent(other)}`);
    expect(await searchIds(page,'TASK')).toContain(own);
    expect(await searchIds(page,'TASK')).not.toContain(other);
  });

  test('Tenant A account cannot obtain a Tenant B Task through direct URL, Search or Export', async ({ page }) => {
    await loginPersona(page, persona('TENANT_A_ADMIN'), tenantA());
    const foreignTask = requiredEnv('RS9_TENANT_B_TASK_ID');
    await expectResourceDenied(page, `/api/tasks/${encodeURIComponent(foreignTask)}`);
    expect(await searchIds(page,'TASK')).not.toContain(foreignTask);
    expect((await exportRows(page,'TASK')).map((row)=>row.id)).not.toContain(foreignTask);
    expect(tenantA()).not.toBe(tenantB());
  });

  test('Department-scoped persona is not offered Tenant-wide Runtime diagnostics', async ({ page }) => {
    await loginPersona(page, persona('IT_DEPARTMENT_ADMIN'), tenantA());
    await page.goto('/dashboard');
    const snap = await dashboard(page);
    expect(snap.tenantWideRuntimeDiagnosticsAllowed).toBe(false);
    await expect(page.getByRole('button',{name:'Runtime diagnostics'})).toHaveCount(0);
    await expect(page.getByText('Runtime diagnostics require Tenant-wide authority.')).toBeVisible();
  });
});
