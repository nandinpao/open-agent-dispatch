import assert from 'node:assert/strict';
import test from 'node:test';
import { canonicalizeLegacyTenantAccessTarget } from '../lib/server/accessRouteCanonicalization';

test('rewrites stale query-based onboarding to the canonical Tenant path', () => {
  const target = canonicalizeLegacyTenantAccessTarget(
    ['api', 'admin', 'access', 'user-onboarding'],
    new URLSearchParams('tenantId=tenant-a&view=people'),
    'tenant-a',
  );
  assert.deepEqual(target.path, ['api', 'admin', 'access', 'tenants', 'tenant-a', 'user-onboarding']);
  assert.equal(target.query.get('tenantId'), null);
  assert.equal(target.query.get('view'), 'people');
  assert.equal(target.rewritten, true);
});

test('rewrites stale invitation routes using the selected Tenant header', () => {
  const target = canonicalizeLegacyTenantAccessTarget(
    ['api', 'admin', 'access', 'users', 'user-a', 'invitation', 'resend'],
    new URLSearchParams(),
    'tenant-a',
  );
  assert.deepEqual(target.path, [
    'api', 'admin', 'access', 'tenants', 'tenant-a', 'users', 'user-a', 'invitation', 'resend',
  ]);
  assert.equal(target.rewritten, true);
});

test('does not rewrite conflicting Tenant evidence', () => {
  const target = canonicalizeLegacyTenantAccessTarget(
    ['api', 'admin', 'access', 'user-onboarding'],
    new URLSearchParams('tenantId=tenant-a'),
    'tenant-b',
  );
  assert.equal(target.rewritten, false);
  assert.deepEqual(target.path, ['api', 'admin', 'access', 'user-onboarding']);
});


test('rewrites stale tenant dashboard routes to explicit Tenant paths', () => {
  const target = canonicalizeLegacyTenantAccessTarget(
    ['admin', 'dashboard', 'snapshot'],
    new URLSearchParams('tenantId=tenant-a'),
    'tenant-a',
  );
  assert.deepEqual(target.path, ['admin', 'tenants', 'tenant-a', 'dashboard', 'snapshot']);
  assert.equal(target.query.has('tenantId'), false);
  assert.equal(target.rewritten, true);
});

test('does not rewrite dashboard requests with conflicting Tenant evidence', () => {
  const target = canonicalizeLegacyTenantAccessTarget(
    ['admin', 'tasks', 'runtime-view'],
    new URLSearchParams('tenantId=tenant-a'),
    'tenant-b',
  );
  assert.deepEqual(target.path, ['admin', 'tasks', 'runtime-view']);
  assert.equal(target.rewritten, false);
});
