import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { authApi, csrfHeader } from '../lib/api/authApi';
import { clearCsrfState } from '../lib/auth/session';

const originalFetch = globalThis.fetch;
const originalWindow = (globalThis as { window?: unknown }).window;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const canonicalSession = {
  authenticationType: 'CANONICAL_SESSION',
  userId: 'user-admin',
  username: 'admin',
  displayName: 'Admin',
  roles: ['ADMIN'],
  permissions: ['identity.user.read'],
  selectedTenantId: 'tenant-a',
  tenantChoices: [{
    tenantId: 'tenant-a', tenantCode: 'TENANT_A', tenantName: 'Tenant A',
    membershipStatus: 'ACTIVE', roleSummary: ['ADMIN']
  }],
  requiredActions: [],
  credentialVersion: 1,
  authenticatedAt: '2026-08-03T00:00:00Z',
  expiresAt: '2026-08-03T01:00:00Z',
  authenticationMethods: ['IAM_PASSWORD']
};

describe('R2 canonical cookie session authentication', () => {
  beforeEach(() => {
    clearCsrfState();
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { dispatchEvent: () => true, addEventListener: () => {}, removeEventListener: () => {} }
    });
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    clearCsrfState();
    if (originalWindow === undefined) delete (globalThis as { window?: unknown }).window;
    else Object.defineProperty(globalThis, 'window', { configurable: true, value: originalWindow });
  });

  it('uses only /api/session for CSRF, login and session projection', async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    globalThis.fetch = async (input, init) => {
      const url = String(input);
      calls.push({ url, init });
      if (url === '/api/session/csrf') {
        return json({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-1' });
      }
      if (url === '/api/session/login') {
        return json({ state: 'AUTHENTICATED', tenantChoices: canonicalSession.tenantChoices, requiredActions: [], credentialVersion: 1 });
      }
      if (url === '/api/session') return json(canonicalSession);
      return json({ error: 'unexpected route' }, 404);
    };

    const user = await authApi.login({ username: 'admin', password: 'secret' });

    assert.equal(user.userId, 'user-admin');
    assert.deepEqual(calls.map(call => call.url), [
      '/api/session/csrf',
      '/api/session/login',
      '/api/session'
    ]);
    assert.equal(calls.every(call => call.init?.credentials === 'include'), true);
    const headers = new Headers(calls[1].init?.headers);
    assert.equal(headers.get('X-XSRF-TOKEN'), 'csrf-1');
    assert.equal(headers.has('Authorization'), false);
  });

  it('shares canonical CSRF with ordinary Admin API mutations', async () => {
    const calls: string[] = [];
    globalThis.fetch = async input => {
      calls.push(String(input));
      return json({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-shared' });
    };

    const first = await csrfHeader();
    const second = await csrfHeader();

    assert.deepEqual(first, { 'X-XSRF-TOKEN': 'csrf-shared' });
    assert.deepEqual(second, first);
    assert.deepEqual(calls, ['/api/session/csrf']);
  });

  it('does not expose interactive Tenant switching in the browser authentication facade', async () => {
    assert.equal('selectTenant' in authApi, false);
  });
});
