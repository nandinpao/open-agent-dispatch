import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { duplicateRuntimeEventMode, isDuplicateRuntimeSecurityEvent, latestDuplicateRuntimeSecurityEvent } from '@/lib/agents/duplicateRuntimeSecurityEvents';
import type { AgentSecurityEvent } from '@/lib/types/core';

const events: AgentSecurityEvent[] = [
  { eventId: '1', eventType: 'CONNECTION_DENIED', occurredAt: '2026-01-01T00:00:00Z' },
  { eventId: '2', eventType: 'DUPLICATE_RUNTIME_DETECTED', occurredAt: '2026-01-01T00:01:00Z' },
  { eventId: '3', eventType: 'DUPLICATE_RUNTIME_AUTO_ENFORCED', occurredAt: '2026-01-01T00:02:00Z' }
];

describe('duplicate runtime security events', () => {
  it('detects duplicate runtime event lifecycle records', () => {
    assert.equal(isDuplicateRuntimeSecurityEvent(events[0]), false);
    assert.equal(isDuplicateRuntimeSecurityEvent(events[1]), true);
    assert.equal(duplicateRuntimeEventMode(events[2]), 'AUTO_ENFORCED');
    assert.equal(latestDuplicateRuntimeSecurityEvent(events)?.eventId, '3');
  });
});
