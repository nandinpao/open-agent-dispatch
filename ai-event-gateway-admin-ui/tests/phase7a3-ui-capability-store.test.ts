import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { UiCapability, UiCapabilityEnvelope, UiPageBootstrap } from '@/lib/ui-capability/contracts';
import { UiCapabilityStore } from '@/lib/ui-capability/store';

const view: UiCapability = {
  uiActionId: 'task.detail.view',
  displayMode: 'ENABLED',
  stepUpRequired: false,
  approvalRequired: false,
  visibilityCeiling: 'STANDARD',
  requestAccessAllowed: false,
};
const update: UiCapability = {
  uiActionId: 'task.detail.update',
  displayMode: 'ENABLED',
  stepUpRequired: false,
  approvalRequired: false,
  visibilityCeiling: 'STANDARD',
  requestAccessAllowed: false,
};

function bootstrap(overrides: Partial<UiPageBootstrap> = {}): UiPageBootstrap {
  return {
    contractVersion: '1.0',
    routeContext: 'task.detail',
    canonicalPath: '/tasks/task-1',
    tenantId: 'tenant-a',
    principalEpoch: 10,
    catalogRevision: 20,
    policyVersion: 30,
    outcome: 'PAGE',
    layoutCapabilities: [],
    pageCapabilities: [view],
    resourceSummaryRef: 'opaque-task',
    resourceVersion: 7,
    expiresAt: '2099-08-01T00:00:00Z',
    hydrationNonce: 'nonce',
    ...overrides,
  };
}

function envelope(overrides: Partial<UiCapabilityEnvelope> = {}): UiCapabilityEnvelope {
  return {
    contractVersion: '1.0',
    contextId: 'task.detail',
    tenantId: 'tenant-a',
    principalEpoch: 10,
    catalogRevision: 20,
    policyVersion: 30,
    resourceRefHash: 'opaque-task',
    resourceVersion: 7,
    enforcementMode: 'ENFORCE',
    capabilities: [update],
    expiresAt: '2099-08-01T00:00:00Z',
    refreshAfter: '2099-07-31T23:59:00Z',
    ...overrides,
  };
}

describe('Phase 7A-3 memory capability store', () => {
  it('seeds the authorized first paint without browser storage', () => {
    const store = new UiCapabilityStore(bootstrap(), 'task-1');
    assert.equal(store.getDecision('task.detail', 'task.detail.view').capability?.displayMode, 'ENABLED');
    assert.equal(store.getContext('task.detail')?.identity.resourceId, 'task-1');
  });

  it('merges accepted dynamic capabilities into the exact context', () => {
    const store = new UiCapabilityStore(bootstrap(), 'task-1');
    assert.ok(store.beginHydration('task.detail', 'request-1'));
    assert.equal(store.acceptEnvelope(envelope(), 'request-1'), true);
    assert.equal(store.getDecision('task.detail', 'task.detail.update').capability?.displayMode, 'ENABLED');
    assert.equal(store.getContext('task.detail')?.sourceByAction.get('task.detail.update'), 'HYDRATION');
  });

  it('drops a response whose authority namespace is stale', () => {
    const store = new UiCapabilityStore(bootstrap(), 'task-1');
    assert.ok(store.beginHydration('task.detail', 'request-1'));
    assert.equal(store.acceptEnvelope(envelope({ principalEpoch: 9 }), 'request-1'), false);
    assert.equal(store.getContext('task.detail')?.status, 'STALE');
    assert.equal(store.getDecision('task.detail', 'task.detail.view').contextStatus, 'STALE');
  });

  it('drops a response whose resource version changed', () => {
    const store = new UiCapabilityStore(bootstrap(), 'task-1');
    assert.ok(store.beginHydration('task.detail', 'request-1'));
    assert.equal(store.acceptEnvelope(envelope({ resourceVersion: 8 }), 'request-1'), false);
    assert.equal(store.getContext('task.detail')?.staleReason, 'RESOURCE_CHANGED');
  });

  it('invalidates only the requested resource context', () => {
    const store = new UiCapabilityStore(bootstrap(), 'task-1');
    store.seedBootstrap(bootstrap({ routeContext: 'task.related', canonicalPath: '/tasks/task-2', resourceVersion: 4 }), 'task-2');
    store.invalidateContext('task.detail', 'NOT_ALLOWED');
    assert.equal(store.getContext('task.detail')?.status, 'STALE');
    assert.equal(store.getContext('task.related')?.status, 'READY');
  });

  it('does not let a stale context reuse a previously enabled action', () => {
    const store = new UiCapabilityStore(bootstrap({ pageCapabilities: [view, update] }), 'task-1');
    store.invalidateContext('task.detail', 'RESOURCE_CHANGED');
    const decision = store.getDecision('task.detail', 'task.detail.update');
    assert.equal(decision.capability?.displayMode, 'ENABLED');
    assert.equal(decision.contextStatus, 'STALE');
  });
});
