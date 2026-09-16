import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import { ApiError, apiRequest, coreApiGet, isNotFoundOrUnsupportedApiError, nettyApiGet } from '../lib/api/client';

const ORIGINAL_FETCH = globalThis.fetch;
const ORIGINAL_ENV = { ...process.env };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' }
  });
}

function mockFetch(body: unknown, status = 200): void {
  globalThis.fetch = async () => jsonResponse(body, status);
}

describe('P19-C API client dual-format response support', () => {
  beforeEach(() => {
    process.env.NEXT_PUBLIC_AUTH_ENABLED = 'false';
    process.env.NEXT_PUBLIC_GATEWAY_API_BASE_URL = '/api';
    process.env.NEXT_PUBLIC_REQUEST_TIMEOUT_MS = '10000';
  });

  afterEach(() => {
    globalThis.fetch = ORIGINAL_FETCH;
    process.env = { ...ORIGINAL_ENV };
  });

  it('unwraps the new code/message/data/timestamp standard envelope', async () => {
    mockFetch({
      code: 'OK',
      message: 'Success',
      data: { agentId: 'agent-001' },
      timestamp: '2026-06-26T00:00:00.000Z'
    });

    const result = await apiRequest<{ agentId: string }>('/agents/agent-001', { skipAuth: true });

    assert.deepEqual(result, { agentId: 'agent-001' });
  });

  it('throws ApiError with code when the new standard envelope reports business failure over HTTP 200', async () => {
    mockFetch({
      code: 'CORE_TASK_INVALID_TRANSITION',
      message: 'Task transition is invalid.',
      data: null,
      timestamp: '2026-06-26T00:00:00.000Z'
    });

    await assert.rejects(
      () => apiRequest('/tasks/task-001', { skipAuth: true }),
      (error: unknown) => error instanceof ApiError
        && error.status === 200
        && error.code === 'CORE_TASK_INVALID_TRANSITION'
        && error.message === 'Task transition is invalid.'
    );
  });

  it('keeps legacy success/data/error envelope compatibility during the rollout window', async () => {
    mockFetch({
      success: true,
      data: { gatewayNodeId: 'gateway-node-001' },
      timestamp: '2026-06-26T00:00:00.000Z'
    });

    const result = await apiRequest<{ gatewayNodeId: string }>('/gateway/status', { skipAuth: true });

    assert.deepEqual(result, { gatewayNodeId: 'gateway-node-001' });
  });

  it('keeps plain DTO response compatibility during the rollout window', async () => {
    mockFetch({ taskId: 'task-001', status: 'COMPLETED' });

    const result = await apiRequest<{ taskId: string; status: string }>('/tasks/task-001', { skipAuth: true });

    assert.deepEqual(result, { taskId: 'task-001', status: 'COMPLETED' });
  });

  it('treats standard NOT_FOUND codes as not-found compatibility errors even when HTTP status is 200', async () => {
    mockFetch({
      code: 'NOT_FOUND',
      message: 'Resource not found.',
      data: null,
      timestamp: '2026-06-26T00:00:00.000Z'
    });

    try {
      await apiRequest('/missing', { skipAuth: true });
      assert.fail('expected ApiError');
    } catch (error) {
      assert.equal(isNotFoundOrUnsupportedApiError(error), true);
    }
  });

  it('requires the standard envelope for Core API calls so HTTP 200 plain DTO cannot be mistaken for success', async () => {
    mockFetch({ taskId: 'task-plain-dto', status: 'COMPLETED' });

    await assert.rejects(
      () => coreApiGet('/api/admin/tasks/task-plain-dto', undefined, { skipAuth: true }),
      (error: unknown) => error instanceof ApiError
        && error.status === 200
        && error.code === 'API_ENVELOPE_REQUIRED'
    );
  });

  it('requires the standard envelope for Netty API calls so runtime business errors are not hidden by plain DTOs', async () => {
    mockFetch({ gatewayNodeId: 'gateway-node-001', status: 'UP' });

    await assert.rejects(
      () => nettyApiGet('/api/admin/runtime', undefined, { skipAuth: true }),
      (error: unknown) => error instanceof ApiError
        && error.status === 200
        && error.code === 'API_ENVELOPE_REQUIRED'
    );
  });

  it('allows explicit plain DTO compatibility only when a Core/Netty caller opts out for a controlled migration path', async () => {
    mockFetch({ gatewayNodeId: 'gateway-node-001', status: 'UP' });

    const result = await nettyApiGet<{ gatewayNodeId: string; status: string }>(
      '/api/admin/runtime',
      undefined,
      { skipAuth: true, requireStandardEnvelope: false }
    );

    assert.deepEqual(result, { gatewayNodeId: 'gateway-node-001', status: 'UP' });
  });

});
