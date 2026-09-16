import assert from 'node:assert/strict';
import test from 'node:test';
import { buildAuthBackendPath } from '../lib/server/authProxyPath';

test('canonical session proxy preserves nested paths and query parameters', () => {
  assert.equal(buildAuthBackendPath('/api/session', ['mfa', 'verify']), '/api/session/mfa/verify');
  assert.equal(buildAuthBackendPath('/api/session', [], 'include=roles'), '/api/session?include=roles');
});

test('canonical proxy encodes each path segment', () => {
  assert.equal(buildAuthBackendPath('/api/session', ['sessions', 'a/b']), '/api/session/sessions/a%2Fb');
});
