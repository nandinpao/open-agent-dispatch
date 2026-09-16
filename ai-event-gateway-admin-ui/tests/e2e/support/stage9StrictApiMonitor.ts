import type { Page, Request, Response } from '@playwright/test';
import { expect } from '@playwright/test';

const STANDARD_ADMIN_PATHS = [
  '/admin/source-systems',
  '/admin/agents',
  '/admin/agent-enrollments',
  '/admin/dispatch-flows',
  '/admin/tasks',
  '/admin/dispatch-requests',
];

const LEGACY_OR_PARALLEL_PATH_TOKENS = [
  'assignment-profiles',
  'service-scope',
  'service-scopes',
  'source-coverage',
  'source-assignments',
  'operation-profiles',
  'dispatch-governance',
  'dispatch-readiness',
  'dispatch-simulator',
  'task-offer',
  'task_offers',
  'legacy pre-assignment handoff',
];

function isStandardAdminUrl(url: URL): boolean {
  const path = decodeURIComponent(url.pathname);
  return STANDARD_ADMIN_PATHS.some((candidate) => path.includes(candidate));
}

function isLegacyOrParallelUrl(url: URL): boolean {
  const normalized = decodeURIComponent(`${url.pathname}?${url.searchParams.toString()}`).toLowerCase();
  return LEGACY_OR_PARALLEL_PATH_TOKENS.some((token) => normalized.includes(token));
}

function requestHasTenant(request: Request, url: URL, expectedTenantId: string): boolean {
  if (url.searchParams.get('tenantId') === expectedTenantId) return true;
  const postData = request.postData();
  if (!postData) return request.method() === 'GET' || request.method() === 'HEAD' ? false : false;
  try {
    const parsed = JSON.parse(postData) as { tenantId?: unknown };
    return parsed.tenantId === expectedTenantId;
  } catch {
    return postData.includes(`tenantId=${encodeURIComponent(expectedTenantId)}`) || postData.includes(`tenantId=${expectedTenantId}`);
  }
}

export interface StrictApiMonitorResult {
  assertClean: () => Promise<void>;
  standardRequests: () => string[];
}

export function installStrictApiMonitor(page: Page, expectedTenantId: string): StrictApiMonitorResult {
  const failures: string[] = [];
  const standardRequests: string[] = [];

  page.on('request', (request) => {
    const url = new URL(request.url());
    if (isLegacyOrParallelUrl(url)) {
      failures.push(`Legacy or parallel dispatch API was called: ${request.method()} ${request.url()}`);
    }
    if (isStandardAdminUrl(url)) {
      standardRequests.push(`${request.method()} ${request.url()}`);
      if (!requestHasTenant(request, url, expectedTenantId)) {
        failures.push(`Tenant context missing on standard Admin request: ${request.method()} ${request.url()}`);
      }
    }
  });

  page.on('response', async (response: Response) => {
    const url = new URL(response.url());
    if (!isStandardAdminUrl(url) && !isLegacyOrParallelUrl(url)) return;
    const status = response.status();
    if ([403, 404, 500].includes(status) || status >= 501) {
      failures.push(`Unexpected HTTP ${status} from ${response.request().method()} ${response.url()}`);
    }
    if (status >= 400) {
      const body = await response.text().catch(() => '');
      if (body.includes('tenantId is required') || body.includes('BadSqlGrammarException') || body.includes('permission is not allowed')) {
        failures.push(`Forbidden runtime/API error body from ${response.url()}: ${body.slice(0, 500)}`);
      }
    }
  });

  return {
    assertClean: async () => {
      await page.waitForLoadState('networkidle').catch(() => undefined);
      expect(failures, failures.join('\n')).toEqual([]);
    },
    standardRequests: () => standardRequests.slice(),
  };
}
