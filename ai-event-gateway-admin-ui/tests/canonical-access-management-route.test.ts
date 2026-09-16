import assert from 'node:assert/strict';
import test from 'node:test';
import { canonicalTenantWorkspaceRoute } from '../lib/navigation/canonicalAccessManagementRoute';

test('legacy Access Management URLs without a Tenant converge on the Tenant directory', () => {
  assert.equal(canonicalTenantWorkspaceRoute({}, 'people'), '/admin/tenants');
});

test('legacy People URL preserves business query state inside the canonical Tenant workspace', () => {
  assert.equal(
    canonicalTenantWorkspaceRoute({ tenantId: 'tenant-a', userId: 'user-1', action: 'ADD_PERSON' }, 'people'),
    '/admin/tenants/tenant-a/people?userId=user-1&action=ADD_PERSON',
  );
});

test('legacy role catalog URL forces the canonical Responsibilities view', () => {
  assert.equal(
    canonicalTenantWorkspaceRoute({ tenantId: 'tenant-a', view: 'ASSIGNMENTS' }, 'access', { view: 'TEMPLATES' }),
    '/admin/tenants/tenant-a/access?view=TEMPLATES',
  );
});
