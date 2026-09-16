import assert from 'node:assert/strict';
import test from 'node:test';
import { navigationFromEntitlements } from '../lib/navigation/permissionAwareNavigation';
import { featureAllowed, actionAllowed, type UiEntitlementResponse } from '../lib/navigation/uiEntitlements';
import { UiListCapabilityStore, listCapabilityRecordIsFresh } from '../lib/ui-capability/listStore';
import type { UiCapabilityEnvelope, UiListCapabilitySummary } from '../lib/ui-capability/contracts';

function entitlements(overrides: Partial<UiEntitlementResponse> = {}): UiEntitlementResponse {
  return {
    contractVersion: '3.0', workspaceKind: 'TENANT', tenantId: 'tenant-a',
    navigation: [{ featureId: 'tasks', parentFeatureId: null, section: 'OPERATIONS', order: 50, route: '/tasks', label: 'Tasks', purpose: 'Review work.', displayMode: 'READ_ONLY', children: [] }],
    pages: { tasks: { featureId: 'tasks', route: '/tasks', displayMode: 'READ_ONLY', allowed: true, denialReason: '', surface: 'TENANT' } },
    actions: ['tasks.retry'], actionEntitlements: { 'tasks.retry': { actionId: 'tasks.retry', displayMode: 'ENABLED', scopes: ['TENANT:tenant-a'] } }, actionScopes: { 'tasks.retry': ['TENANT:tenant-a'] }, generatedAt: '2026-08-08T00:00:00Z', ...overrides,
  };
}

test('legacy navigation compatibility is derived only from backend entitlement output', () => {
  const projected = navigationFromEntitlements(entitlements());
  assert.equal(projected.contractVersion, '3.0');
  assert.deepEqual(projected.items.map(item => item.href), ['/tasks']);
  const payload = JSON.stringify(projected);
  assert.equal(payload.includes('requiredPermission'), false);
  assert.equal(payload.includes('roles'), false);
});

test('page and action helpers consume entitlement IDs instead of Role names', () => {
  const value = entitlements();
  assert.equal(featureAllowed(value, 'tasks'), true);
  assert.equal(featureAllowed(value, 'dispatch'), false);
  assert.equal(actionAllowed(value, 'tasks.retry'), true);
  assert.equal(actionAllowed(value, 'dispatch.update'), false);
});

test('embedded list summary seeds an immediately readable short-lived row capability', () => {
  const store = new UiListCapabilityStore();
  const summary: UiListCapabilitySummary = {
    contextId: 'task.list.task-1',
    resourceVersion: 3,
    principalEpoch: 7,
    catalogRevision: 8,
    policyVersion: 9,
    actions: {
      'task.detail.view': {
        uiActionId: 'task.detail.view',
        displayMode: 'ENABLED',
        stepUpRequired: false,
        approvalRequired: false,
        visibilityCeiling: 'STANDARD',
        requestAccessAllowed: false,
      },
    },
    expiresAt: '2099-01-01T00:00:00Z',
  };
  store.seedEmbedded(summary, 'task-1');
  const record = store.get(summary.contextId);
  assert.equal(record?.status, 'READY');
  assert.equal(record?.capabilities.get('task.detail.view')?.displayMode, 'ENABLED');
  assert.equal(listCapabilityRecordIsFresh(record), true);
});

test('older overlapping response cannot overwrite the newest request sequence', () => {
  const store = new UiListCapabilityStore();
  const identity = store.register({ contextId: 'task.list.task-1', resourceId: 'task-1', resourceVersion: 3 });
  const first = store.begin(identity.contextId, identity.generation);
  const second = store.begin(identity.contextId, identity.generation);
  assert.equal(typeof first, 'number');
  assert.equal(typeof second, 'number');
  const envelope: UiCapabilityEnvelope = {
    contractVersion: '1.0',
    contextId: identity.contextId,
    tenantId: 'tenant-a',
    principalEpoch: 7,
    catalogRevision: 8,
    policyVersion: 9,
    resourceVersion: 3,
    enforcementMode: 'ENFORCE',
    capabilities: [],
    expiresAt: '2099-01-01T00:00:00Z',
    refreshAfter: '2098-12-31T23:59:00Z',
  };
  assert.equal(store.accept(envelope, identity.generation, first!), false);
  assert.equal(store.accept(envelope, identity.generation, second!), true);
});

test('authority drift marks a row stale instead of replacing the embedded summary', () => {
  const store = new UiListCapabilityStore();
  const summary: UiListCapabilitySummary = {
    contextId: 'task.list.task-2',
    resourceVersion: 1,
    principalEpoch: 3,
    catalogRevision: 4,
    policyVersion: 5,
    actions: {},
    expiresAt: '2099-01-01T00:00:00Z',
  };
  const identity = store.seedEmbedded(summary, 'task-2');
  store.queue(identity.contextId);
  const sequence = store.begin(identity.contextId, identity.generation)!;
  const changed: UiCapabilityEnvelope = {
    contractVersion: '1.0',
    contextId: identity.contextId,
    tenantId: 'tenant-a',
    principalEpoch: 4,
    catalogRevision: 4,
    policyVersion: 5,
    resourceVersion: 1,
    enforcementMode: 'ENFORCE',
    capabilities: [],
    expiresAt: '2099-01-01T00:00:00Z',
    refreshAfter: '2098-12-31T23:59:00Z',
  };
  assert.equal(store.accept(changed, identity.generation, sequence), false);
  assert.equal(store.get(identity.contextId)?.status, 'STALE');
});

test('unmounted virtual row drops late results', () => {
  const store = new UiListCapabilityStore();
  const identity = store.register({ contextId: 'task.list.task-3', resourceId: 'task-3' });
  const sequence = store.begin(identity.contextId, identity.generation)!;
  store.remove(identity.contextId, identity.generation);
  const envelope: UiCapabilityEnvelope = {
    contractVersion: '1.0',
    contextId: identity.contextId,
    tenantId: 'tenant-a',
    principalEpoch: 1,
    catalogRevision: 1,
    policyVersion: 1,
    enforcementMode: 'ENFORCE',
    capabilities: [],
    expiresAt: '2099-01-01T00:00:00Z',
    refreshAfter: '2098-12-31T23:59:00Z',
  };
  assert.equal(store.accept(envelope, identity.generation, sequence), false);
  assert.equal(store.get(identity.contextId), undefined);
});
