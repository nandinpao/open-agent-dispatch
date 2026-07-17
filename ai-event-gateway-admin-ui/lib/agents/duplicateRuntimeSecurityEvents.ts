import type { AgentSecurityEvent } from '@/lib/types/core';

export const duplicateRuntimeAutoEventTypes = new Set([
  'DUPLICATE_RUNTIME_DETECTED',
  'DUPLICATE_RUNTIME_AUTO_ENFORCED',
  'DUPLICATE_RUNTIME_AUTO_ENFORCEMENT_FAILED',
  'DUPLICATE_RUNTIME_QUARANTINED',
  'DUPLICATE_RUNTIME_POLICY_EVALUATED',
  'SECURITY_NOTIFICATION_QUEUED',
  'CREDENTIAL_ROTATION_REQUIRED',
  'DUPLICATE_RUNTIME_REMEDIATED'
]);

export function isDuplicateRuntimeSecurityEvent(event?: AgentSecurityEvent | null): boolean {
  return Boolean(event?.eventType && duplicateRuntimeAutoEventTypes.has(event.eventType));
}

export function latestDuplicateRuntimeSecurityEvent(events?: AgentSecurityEvent[] | null): AgentSecurityEvent | undefined {
  return (events ?? [])
    .filter(isDuplicateRuntimeSecurityEvent)
    .sort((a, b) => Date.parse(b.occurredAt ?? '') - Date.parse(a.occurredAt ?? ''))[0];
}

export function duplicateRuntimeEventMode(event?: AgentSecurityEvent | null): 'AUTO_DETECTED' | 'AUTO_ENFORCED' | 'AUTO_FAILED' | 'POLICY_EVALUATED' | 'NOTIFICATION_QUEUED' | 'MANUAL' | 'NONE' {
  if (!event) return 'NONE';
  if (event.eventType === 'DUPLICATE_RUNTIME_DETECTED') return 'AUTO_DETECTED';
  if (event.eventType === 'DUPLICATE_RUNTIME_AUTO_ENFORCED') return 'AUTO_ENFORCED';
  if (event.eventType === 'DUPLICATE_RUNTIME_AUTO_ENFORCEMENT_FAILED') return 'AUTO_FAILED';
  if (event.eventType === 'DUPLICATE_RUNTIME_POLICY_EVALUATED') return 'POLICY_EVALUATED';
  if (event.eventType === 'SECURITY_NOTIFICATION_QUEUED') return 'NOTIFICATION_QUEUED';
  return 'MANUAL';
}
