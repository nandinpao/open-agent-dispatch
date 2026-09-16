import assert from 'node:assert/strict';
import test from 'node:test';
import { buildAuthBackendPath } from '../lib/server/authProxyPath';

test('R2 exposes one browser-session namespace', () => {
  const canonical = buildAuthBackendPath('/api/session', ['csrf']);
  assert.equal(canonical, '/api/session/csrf');
  assert.equal(canonical.includes('/api/auth'), false);
  assert.equal(canonical.includes('/api/legacy-auth'), false);
});
