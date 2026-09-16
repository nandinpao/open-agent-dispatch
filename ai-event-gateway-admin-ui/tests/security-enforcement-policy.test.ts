import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { CoreAgentSecurityEnforcementPolicyUpdateRequest } from '@/lib/types/core';

function enabledModes(request: CoreAgentSecurityEnforcementPolicyUpdateRequest): string[] {
  const channels: string[] = [];
  if (request.notifyEmail) channels.push('EMAIL');
  if (request.notifySlack) channels.push('SLACK');
  if (request.notifySiem) channels.push('SIEM');
  return channels;
}

describe('P8 security enforcement policy', () => {
  it('keeps notification hooks explicit', () => {
    const request: CoreAgentSecurityEnforcementPolicyUpdateRequest = {
      duplicateRuntimeMode: 'QUARANTINE_AND_DISCONNECT',
      notifyEmail: true,
      notifySlack: false,
      notifySiem: true
    };
    assert.deepEqual(enabledModes(request), ['EMAIL', 'SIEM']);
  });
});
