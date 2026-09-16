import type { APIRequestContext, Page } from '@playwright/test';
import { expect } from '@playwright/test';

export interface Stage9TestData {
  tenantId: string;
  sourceSystemId: string;
  sourceDisplayName: string;
  flowName: string;
  eventType: string;
  objectType: string;
}

export function createStage9TestData(workerIndex: number): Stage9TestData {
  const suffix = `${Date.now().toString(36)}_${workerIndex}_${Math.random().toString(36).slice(2, 8)}`.toUpperCase();
  return {
    tenantId: process.env.STAGE9_TENANT_ID ?? 'mock-tenant',
    sourceSystemId: `SRC_E2E_${suffix}`,
    sourceDisplayName: `Stage9 Source ${suffix}`,
    flowName: `Stage9 Flow ${suffix}`,
    eventType: `EVT_E2E_${suffix}`,
    objectType: 'E2E_OBJECT',
  };
}

export async function authenticateAdmin(page: Page): Promise<void> {
  await page.goto('/dashboard');
  if (page.url().includes('/login')) {
    const username = process.env.STAGE9_ADMIN_USERNAME ?? 'admin';
    const password = process.env.STAGE9_ADMIN_PASSWORD ?? 'admin';
    await page.getByPlaceholder('username').fill(username);
    await page.getByPlaceholder('••••••••').fill(password);
    await page.getByRole('button', { name: /sign in/i }).click();
  }
  await expect(page).not.toHaveURL(/\/login$/);
}

export async function selectTenantIfAvailable(page: Page, tenantId: string): Promise<void> {
  const selector = page.getByLabel('Workspace tenant');
  if (await selector.count()) {
    const options = await selector.locator('option').allTextContents();
    if (options.includes(tenantId)) {
      await selector.selectOption(tenantId);
    }
  }
}

export async function getJson<T>(request: APIRequestContext, path: string): Promise<T> {
  const response = await request.get(path);
  expect(response.ok(), `${response.status()} ${path}`).toBeTruthy();
  return (await response.json()) as T;
}

export async function waitForApiCondition<T>(
  request: APIRequestContext,
  path: string,
  predicate: (payload: T) => boolean,
  description: string,
  timeoutMs = 30_000,
): Promise<T> {
  const started = Date.now();
  let lastPayload: T | undefined;
  while (Date.now() - started < timeoutMs) {
    lastPayload = await getJson<T>(request, path);
    if (predicate(lastPayload)) return lastPayload;
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error(`Timed out waiting for ${description}. Last payload: ${JSON.stringify(lastPayload).slice(0, 1000)}`);
}

export interface AgentOption {
  agentId: string;
  agentName?: string;
  selectable?: boolean;
  disabledReason?: string | null;
  runtimeConnected?: boolean;
  heartbeatHealthy?: boolean;
  capacityAvailable?: boolean;
}

export async function requireSelectableAgent(request: APIRequestContext, tenantId: string): Promise<AgentOption> {
  const options = await getJson<AgentOption[]>(request, `/api/admin/dispatch-flows/agent-options?tenantId=${encodeURIComponent(tenantId)}`);
  const selectable = options.find((agent) => agent.selectable !== false);
  if (!selectable) {
    throw new Error(`Phase 9 requires at least one approved and selectable Agent connected in tenant ${tenantId}. Returned options: ${JSON.stringify(options).slice(0, 1500)}`);
  }
  return selectable;
}
