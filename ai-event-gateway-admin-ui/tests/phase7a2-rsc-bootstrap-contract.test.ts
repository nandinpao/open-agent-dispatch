import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { UI_PAGE_BOOTSTRAP_OUTCOMES, type UiPageBootstrap, type UiPageBootstrapOutcome } from '@/lib/ui-capability/contracts';
import type { ServerBootstrapResult } from '@/lib/server/uiPageBootstrap';

describe('Phase 7A-2 RSC bootstrap contract', () => {
  it('has one explicit safe-shell vocabulary', () => {
    assert.deepEqual(UI_PAGE_BOOTSTRAP_OUTCOMES, ['PAGE','STEP_UP_SHELL','REQUEST_ACCESS_SHELL','SAFE_RESOURCE_CHANGED_SHELL','ANTI_ENUMERATION_NOT_FOUND_SHELL']);
  });

  it('narrows SAFE_SHELL bootstrap outcome so PAGE cannot reach SafePageShell', () => {
    const result: Extract<ServerBootstrapResult, { kind: 'SAFE_SHELL' }> = {
      kind: 'SAFE_SHELL',
      bootstrap: {
        contractVersion: '1.0', routeContext: 'task.detail', canonicalPath: '/tasks/task-1',
        tenantId: 'tenant-a', principalEpoch: 1, catalogRevision: 1, policyVersion: 1,
        outcome: 'STEP_UP_SHELL', layoutCapabilities: [], pageCapabilities: [],
        expiresAt: '2026-08-01T00:00:00Z', hydrationNonce: 'nonce',
      },
    };
    const outcome: Exclude<UiPageBootstrapOutcome, 'PAGE'> = result.bootstrap.outcome;
    assert.equal(outcome, 'STEP_UP_SHELL');
  });
  it('does not require a resource title in the bootstrap wire model', () => {
    const bootstrap: UiPageBootstrap = { contractVersion:'1.0', routeContext:'task.detail', canonicalPath:'/tasks/task-1', tenantId:'tenant-a', principalEpoch:1, catalogRevision:1, policyVersion:1, outcome:'PAGE', layoutCapabilities:[], pageCapabilities:[], resourceSummaryRef:'opaque', resourceVersion:1, expiresAt:'2026-08-01T00:00:00Z', hydrationNonce:'nonce' };
    assert.equal('resourceTitle' in bootstrap, false);
  });
});
