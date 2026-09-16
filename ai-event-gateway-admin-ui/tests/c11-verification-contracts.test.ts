import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const registry = JSON.parse(readFileSync(resolve(root, 'verification/contracts/invariants.json'), 'utf8'));
const makefile = readFileSync(resolve(root, 'Makefile'), 'utf8');
const packageJson = JSON.parse(readFileSync(resolve(root, 'ai-event-gateway-admin-ui/package.json'), 'utf8'));

test('canonical verification scopes are explicit and stable', () => {
  assert.deepEqual(registry.policy.allowedPublicScopes, ['source', 'build', 'integration', 'migrations', 'live', 'release', 'status']);
  assert.deepEqual(registry.policy.aliases, { fast: 'source', full: 'release', db: 'migrations', certification: 'live' });
});

test('source suite uses current invariant IDs and excludes frozen C5 release gate', () => {
  const source = registry.suites.source;
  assert.ok(source.invariants.includes('SEC-C6-OPERATIONAL-INTEGRITY'));
  assert.ok(source.invariants.includes('VERIFY-C11-CONTRACT-ARCHITECTURE'));
  const text = JSON.stringify(source);
  assert.equal(text.includes('verify-c5-production-certification-readiness'), false);
  assert.equal(text.includes('verify-fast'), false);
});

test('make verify delegates to the canonical runner', () => {
  assert.match(makefile, /^verify:\n\tpython3 scripts\/verify\/run-verification\.py --scope "\$\(VERIFY_SCOPE\)"/m);
});

test('Admin UI canonical verification scripts are thin runner aliases', () => {
  assert.equal(packageJson.scripts.verify, 'python3 ../scripts/verify/run-verification.py --scope source');
  assert.equal(packageJson.scripts['verify:release'], 'python3 ../scripts/verify/run-verification.py --scope release');
  assert.equal(packageJson.scripts.ci, 'python3 ../scripts/verify/run-verification.py --scope build');
});

test('release suite composes source build integration migrations and live', () => {
  assert.deepEqual(registry.suites.release.includes, ['source', 'build', 'integration', 'migrations', 'live']);
});
