import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  UI_CAPABILITY_CONTRACT_VERSION,
  UI_DISPLAY_MODES,
  UI_REASON_CATEGORIES,
  validateUiCapability,
  validateUiCapabilityEnvelope,
  type UiCapability,
} from '@/lib/ui-capability/contracts';
import { UI_CAPABILITY_API_PATHS, UI_CAPABILITY_BATCH_LIMITS } from '@/lib/ui-capability/apiPaths';

describe('Phase 7A-0 UI capability contract', () => {
  it('uses one canonical display mode vocabulary', () => {
    assert.equal(UI_CAPABILITY_CONTRACT_VERSION, '1.0');
    assert.deepEqual(UI_DISPLAY_MODES, [
      'HIDE', 'DISABLE_WITH_REASON', 'READ_ONLY', 'ENABLED', 'STEP_UP_REQUIRED',
      'APPROVAL_REQUIRED', 'REQUEST_ACCESS', 'LOCKED_SECURITY', 'STALE_RELOAD',
    ]);
    assert.equal(UI_REASON_CATEGORIES.includes('NOT_ALLOWED'), true);
  });

  it('separates generic and list-row batch profiles', () => {
    assert.equal(UI_CAPABILITY_BATCH_LIMITS.genericContexts, 20);
    assert.equal(UI_CAPABILITY_BATCH_LIMITS.listRowContexts, 50);
    assert.equal(UI_CAPABILITY_BATCH_LIMITS.genericMaxPayloadBytes, 131072);
    assert.equal(UI_CAPABILITY_BATCH_LIMITS.listRowMaxPayloadBytes, 262144);
    assert.notEqual(UI_CAPABILITY_API_PATHS.capabilityBatch, UI_CAPABILITY_API_PATHS.listCapabilityBatch);
  });

  it('rejects presentation state drift', () => {
    const invalid: UiCapability = {
      uiActionId: 'agent.credential.rotate',
      displayMode: 'STEP_UP_REQUIRED',
      reasonCategory: 'STEP_UP_REQUIRED',
      stepUpRequired: false,
      approvalRequired: false,
      visibilityCeiling: 'SECRET_METADATA',
      requestAccessAllowed: false,
    };
    assert.equal(validateUiCapability(invalid).length > 0, true);
  });

  it('rejects epochs outside the JavaScript safe-integer range', () => {
    assert.equal(validateUiCapabilityEnvelope({
      contractVersion: '1.0',
      contextId: 'task.detail',
      tenantId: 'tenant-a',
      principalEpoch: Number.MAX_SAFE_INTEGER + 1,
      catalogRevision: 1,
      policyVersion: 1,
      enforcementMode: 'FULL_ENFORCE',
      capabilities: [],
      expiresAt: '2026-08-01T00:01:00Z',
      refreshAfter: '2026-08-01T00:00:30Z',
    }).length > 0, true);
  });

});
