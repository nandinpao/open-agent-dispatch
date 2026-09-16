import assert from 'node:assert/strict';
import test from 'node:test';
import { replaceAdministrationTenantPath, tenantFromAdministrationPath } from '../components/auth/WorkspaceTenantSelector';

test('selecting an administration tenant outside People & Access does not navigate', () => {
  assert.equal(replaceAdministrationTenantPath('/dashboard', 'tenant-b'), '/dashboard');
  assert.equal(replaceAdministrationTenantPath('/tasks', 'tenant-b'), '/tasks');
  assert.equal(replaceAdministrationTenantPath('/agents/agent-1', 'tenant-b'), '/agents/agent-1');
});

test('changing tenant inside People & Access preserves the current section', () => {
  assert.equal(tenantFromAdministrationPath('/admin/tenants/tenant-a/people'), 'tenant-a');
  assert.equal(replaceAdministrationTenantPath('/admin/tenants/tenant-a/people', 'tenant-b'), '/admin/tenants/tenant-b/people');
  assert.equal(replaceAdministrationTenantPath('/admin/tenants/tenant-a/organization', 'tenant-b'), '/admin/tenants/tenant-b/organization');
  assert.equal(replaceAdministrationTenantPath('/admin/tenants/tenant-a/access', 'tenant-b'), '/admin/tenants/tenant-b/access');
});

test('clearing root tenant from a tenant resource returns only to the tenant directory', () => {
  assert.equal(replaceAdministrationTenantPath('/admin/tenants/tenant-a/security', ''), '/admin/tenants');
});
