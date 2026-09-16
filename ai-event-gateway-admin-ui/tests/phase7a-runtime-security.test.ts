import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { clearCsrfState } from '../lib/auth/session';
import { hydrateUiCapabilities } from '../lib/ui-capability/api';

const ORIGINAL_FETCH = globalThis.fetch;
const ORIGINAL_ENV = { ...process.env };

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const contexts = [{
  contextId: 'task.detail',
  resourceId: 'task-001',
  resourceVersion: 3,
  presentedPrincipalEpoch: 7,
  uiActionIds: ['task.retry.execute'],
}];

describe('Phase 7A runtime security integration', () => {
  beforeEach(() => {
    clearCsrfState();
    process.env.NEXT_PUBLIC_AUTH_ENABLED = 'true';
    process.env.NEXT_PUBLIC_CORE_API_BASE_URL = '/core-api';
    process.env.NEXT_PUBLIC_REQUEST_TIMEOUT_MS = '10000';
  });

  afterEach(() => {
    globalThis.fetch = ORIGINAL_FETCH;
    clearCsrfState();
    process.env = { ...ORIGINAL_ENV };
  });

  it('does not retry a verified authorization 403 as though it were a CSRF failure', async () => {
    const calls: string[] = [];
    globalThis.fetch = async (input) => {
      const url = String(input);
      calls.push(url);
      if (url.endsWith('/api/session/csrf')) {
        return json({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token-1' });
      }
      return json({ code: 'UI_ACTION_NOT_ALLOWED', message: 'Not allowed.' }, 403);
    };

    await assert.rejects(() => hydrateUiCapabilities(contexts), (error: unknown) => {
      return error instanceof Error && error.message === 'Not allowed.';
    });
    assert.equal(calls.filter((url) => url.endsWith('/api/session/csrf')).length, 1);
    assert.equal(calls.filter((url) => url.includes('/api/ui/capabilities:batch')).length, 1);
  });

  it('refreshes CSRF exactly once when the backend returns an explicit CSRF code', async () => {
    const calls: string[] = [];
    let csrfCount = 0;
    let capabilityCount = 0;
    globalThis.fetch = async (input) => {
      const url = String(input);
      calls.push(url);
      if (url.endsWith('/api/session/csrf')) {
        csrfCount += 1;
        return json({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: `token-${csrfCount}` });
      }
      capabilityCount += 1;
      if (capabilityCount === 1) return json({ code: 'CSRF_TOKEN_INVALID', message: 'Refresh CSRF.' }, 403);
      return json({ contractVersion: '1.0', contexts: [] });
    };

    const response = await hydrateUiCapabilities(contexts);
    assert.deepEqual(response, { contractVersion: '1.0', contexts: [] });
    assert.equal(csrfCount, 2);
    assert.equal(capabilityCount, 2);
    assert.equal(calls.length, 4);
  });
});
