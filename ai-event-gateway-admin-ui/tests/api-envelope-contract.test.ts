import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  STANDARD_SUCCESS_CODE,
  isNotFoundOrUnsupportedCode,
  isStandardApiEnvelope,
  isUnauthorizedApiCode,
  makeStandardApiEnvelope,
  standardEnvelopeCode
} from '../lib/api/envelope';

describe('P21 API envelope frontend contract helpers', () => {
  it('creates the standard code/message/data/timestamp envelope used by proxy runtime errors', () => {
    const envelope = makeStandardApiEnvelope('ADMIN_PROXY_CORE_UNAVAILABLE', 'Core backend unavailable.', null);

    assert.equal(envelope.code, 'ADMIN_PROXY_CORE_UNAVAILABLE');
    assert.equal(envelope.message, 'Core backend unavailable.');
    assert.equal(envelope.data, null);
    assert.equal(typeof envelope.timestamp, 'string');
    assert.equal(isStandardApiEnvelope(envelope), true);
  });

  it('recognizes success, authorization, and not-found codes without transport status branching', () => {
    const success = makeStandardApiEnvelope(STANDARD_SUCCESS_CODE, 'Success', { ok: true });

    assert.equal(standardEnvelopeCode(success), 'OK');
    assert.equal(isUnauthorizedApiCode('UNAUTHORIZED'), true);
    assert.equal(isUnauthorizedApiCode('ADMIN_TOKEN_EXPIRED'), true);
    assert.equal(isNotFoundOrUnsupportedCode('CORE_AGENT_NOT_FOUND'), true);
    assert.equal(isNotFoundOrUnsupportedCode('GATEWAY_AGENT_NOT_FOUND'), true);
  });
});
