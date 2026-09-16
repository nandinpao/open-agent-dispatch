import assert from 'node:assert/strict';
import test from 'node:test';
import { generateAgentCredentialToken, isGeneratedAgentCredentialToken } from '../lib/agents/credentialToken';
import { dateTimeLocalValueToIso, isoToDateTimeLocalValue } from '../lib/utils/isoDateTime';

test('generateAgentCredentialToken creates a prefixed high-entropy token', () => {
  const token = generateAgentCredentialToken();
  assert.match(token, /^agt_[0-9a-f]{64}$/);
  assert.equal(isGeneratedAgentCredentialToken(token), true);
});

test('generateAgentCredentialToken creates unique values', () => {
  const first = generateAgentCredentialToken();
  const second = generateAgentCredentialToken();
  assert.notEqual(first, second);
});

test('dateTimeLocalValueToIso converts datetime-local values to ISO UTC strings', () => {
  const iso = dateTimeLocalValueToIso('2026-12-31T23:59:59');
  assert.equal(typeof iso, 'string');
  assert.ok(iso.endsWith('Z'));
  assert.ok(iso.includes('T'));
});

test('isoToDateTimeLocalValue returns empty string for empty or invalid values', () => {
  assert.equal(isoToDateTimeLocalValue(''), '');
  assert.equal(isoToDateTimeLocalValue('not-a-date'), '');
});
