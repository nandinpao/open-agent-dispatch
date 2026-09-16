import assert from 'node:assert/strict';
import http from 'node:http';
import { afterEach, test } from 'node:test';
import { backendOrigins, fetchBackend, BackendConnectionError } from '@/lib/server/backendOrigins';

const original = { ...process.env };

afterEach(() => {
  process.env = { ...original };
});

async function startServer(): Promise<{ origin: string; close: () => Promise<void> }> {
  const server = http.createServer((request, response) => {
    if (request.url === '/api/session/csrf') {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token' }));
      return;
    }
    response.writeHead(404).end();
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('test server did not bind');
  return {
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
  };
}

test('falls back when the primary Core origin is unreachable', async () => {
  const server = await startServer();
  try {
    process.env.CORE_BACKEND_ORIGIN = 'http://127.0.0.1:1';
    process.env.CORE_BACKEND_FALLBACK_ORIGINS = server.origin;
    process.env.ADMIN_UI_BACKEND_CONNECT_TIMEOUT_MS = '250';
    delete process.env.OPENDISPATCH_PUBLIC_HOST;

    const result = await fetchBackend('core', '/api/session/csrf', { method: 'GET' });
    assert.equal(result.response.status, 200);
    assert.equal(result.origin, server.origin);
    assert.equal(result.attempts.length, 1);
    assert.equal(result.attempts[0]?.origin, 'http://127.0.0.1:1');
  } finally {
    await server.close();
  }
});

test('adds the configured public host as a Core fallback', () => {
  process.env.CORE_BACKEND_ORIGIN = 'http://core:18080';
  delete process.env.CORE_BACKEND_FALLBACK_ORIGINS;
  process.env.OPENDISPATCH_PUBLIC_HOST = 'baofire.com';
  process.env.OPENDISPATCH_PUBLIC_SCHEME = 'http';
  process.env.CORE_HTTP_PORT = '18080';

  assert.deepEqual(backendOrigins('core').slice(0, 2), [
    'http://core:18080',
    'http://baofire.com:18080'
  ]);
});

test('reports each attempted Core origin when all routes are unavailable', async () => {
  process.env.CORE_BACKEND_ORIGIN = 'http://127.0.0.1:1';
  process.env.CORE_BACKEND_FALLBACK_ORIGINS = 'http://127.0.0.1:2';
  process.env.ADMIN_UI_BACKEND_CONNECT_TIMEOUT_MS = '250';
  delete process.env.OPENDISPATCH_PUBLIC_HOST;

  await assert.rejects(
    () => fetchBackend('core', '/api/session/csrf', { method: 'GET' }),
    (error: unknown) => {
      assert.ok(error instanceof BackendConnectionError);
      assert.ok(error.attempts.some((attempt) => attempt.origin === 'http://127.0.0.1:1'));
      assert.ok(error.attempts.some((attempt) => attempt.origin === 'http://127.0.0.1:2'));
      return true;
    }
  );
});
