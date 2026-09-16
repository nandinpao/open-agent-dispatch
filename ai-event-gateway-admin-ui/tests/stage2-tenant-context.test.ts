import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import {
  ApiError,
  coreApiGet,
  coreApiPut,
  coreTenantApiGet,
  getCoreTenantContext,
  setCoreTenantContext,
} from '../lib/api/client';

const ORIGINAL_FETCH = globalThis.fetch;
const ORIGINAL_ENV = { ...process.env };

function okEnvelope(data: unknown = []): Response {
  return new Response(JSON.stringify({
    code: 'OK',
    message: 'Success',
    data,
    timestamp: '2026-07-13T00:00:00.000Z',
  }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

describe('Stage 2 authoritative Admin tenant context', () => {
  let requestedUrls: string[];
  let requestedHeaders: Headers[];

  beforeEach(() => {
    requestedUrls = [];
    requestedHeaders = [];
    process.env.NEXT_PUBLIC_AUTH_ENABLED = 'false';
    process.env.NEXT_PUBLIC_CORE_API_BASE_URL = '/core-api';
    process.env.NEXT_PUBLIC_REQUEST_TIMEOUT_MS = '10000';
    setCoreTenantContext('');
    globalThis.fetch = async (input, init) => {
      requestedUrls.push(String(input));
      requestedHeaders.push(new Headers(init?.headers));
      return okEnvelope();
    };
  });

  afterEach(() => {
    setCoreTenantContext('');
    globalThis.fetch = ORIGINAL_FETCH;
    process.env = { ...ORIGINAL_ENV };
  });

  it('fails before network I/O when no workspace is selected', async () => {
    await assert.rejects(
      () => coreApiGet('/admin/capabilities', undefined, { skipAuth: true }),
      (error: unknown) => error instanceof ApiError
        && error.code === 'TENANT_CONTEXT_REQUIRED'
        && error.status === 400,
    );
    assert.equal(requestedUrls.length, 0);
  });

  it('injects the selected tenant into every Core /admin request', async () => {
    setCoreTenantContext('tenant-a');
    assert.equal(getCoreTenantContext(), 'tenant-a');

    await coreApiGet('/admin/capabilities', { status: 'ACTIVE' }, { skipAuth: true });

    assert.equal(requestedUrls.length, 1);
    const url = new URL(requestedUrls[0], 'http://opendispatch.local');
    assert.equal(url.searchParams.get('tenantId'), 'tenant-a');
    assert.equal(url.searchParams.get('status'), 'ACTIVE');
  });

  it('preserves a matching explicit tenant without adding a duplicate parameter', async () => {
    setCoreTenantContext('tenant-a');

    await coreApiGet('/admin/capabilities?tenantId=tenant-a', undefined, { skipAuth: true });

    const url = new URL(requestedUrls[0], 'http://opendispatch.local');
    assert.deepEqual(url.searchParams.getAll('tenantId'), ['tenant-a']);
  });

  it('rejects query, path, or request-body tenant spoofing', async () => {
    setCoreTenantContext('tenant-a');

    for (const request of [
      () => coreApiGet('/admin/capabilities', { tenantId: 'tenant-b' }, { skipAuth: true }),
      () => coreApiGet('/admin/capabilities?tenantId=tenant-b', undefined, { skipAuth: true }),
      () => coreApiPut('/admin/capabilities/CAP_X', { tenantId: 'tenant-b' }, { skipAuth: true }),
    ]) {
      await assert.rejects(
        request,
        (error: unknown) => error instanceof ApiError
          && error.code === 'TENANT_CONTEXT_MISMATCH'
          && error.status === 409,
      );
    }
    assert.equal(requestedUrls.length, 0);
  });

  it('injects the selected Tenant into explicit tenant-scoped Core APIs', async () => {
    setCoreTenantContext('tenant-a');

    await coreTenantApiGet('/api/a2a-operations', { limit: 50 }, { skipAuth: true });

    const url = new URL(requestedUrls[0], 'http://opendispatch.local');
    assert.equal(url.searchParams.get('tenantId'), 'tenant-a');
    assert.equal(url.searchParams.get('limit'), '50');
    assert.equal(requestedHeaders[0].get('X-Tenant-Id'), 'tenant-a');
  });

  it('fails before network I/O when a tenant-scoped Core API has no selected workspace', async () => {
    await assert.rejects(
      () => coreTenantApiGet('/api/a2a-operations', undefined, { skipAuth: true }),
      (error: unknown) => error instanceof ApiError
        && error.code === 'TENANT_CONTEXT_REQUIRED'
        && error.status === 400,
    );
    assert.equal(requestedUrls.length, 0);
  });

  it('does not add a tenant to ordinary non-admin Core endpoints', async () => {
    setCoreTenantContext('tenant-a');

    await coreApiGet('/api/core/status', undefined, {
      skipAuth: true,
      requireStandardEnvelope: true,
    });

    const url = new URL(requestedUrls[0], 'http://opendispatch.local');
    assert.equal(url.searchParams.has('tenantId'), false);
    assert.equal(requestedHeaders[0].has('X-Tenant-Id'), false);
  });
});
