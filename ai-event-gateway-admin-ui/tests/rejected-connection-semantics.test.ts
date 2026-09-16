import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  appendManualDisconnectNotice,
  latestRejectedConnectionsEmptyText,
  manualDisconnectResultNotice,
  rejectedConnectionMetricSubtitle,
  rejectedConnectionSemantics,
  rejectedConnectionsEmptyDescription,
  runtimeDisconnectSemantics
} from '../lib/runtime/rejectedConnectionSemantics';

describe('Phase 3G-P0 rejected connection and disconnect semantics', () => {
  it('explains that rejected connections are denied handshakes, not manual disconnects', () => {
    const copy = rejectedConnectionSemantics();

    assert.match(copy.description, /嘗試連線|拒絕|手動 Disconnect/i);
    assert.equal(copy.examples.some((item) => /credential|token|revoked|disabled|duplicate/i.test(item)), true);
    assert.equal(copy.nonExamples.some((item) => /Disconnect|正常|restart/i.test(item)), true);
  });

  it('keeps dashboard metric wording scoped to handshake denial', () => {
    assert.match(rejectedConnectionMetricSubtitle(), /Handshake denied/i);
    assert.match(latestRejectedConnectionsEmptyText(), /手動 Disconnect 不會出現在這裡|Runtime Events/i);
  });

  it('directs manual disconnect results to runtime and security event views', () => {
    const notice = manualDisconnectResultNotice('agent-1');
    const allNotice = manualDisconnectResultNotice('agent-1', true);
    const runtimeCopy = runtimeDisconnectSemantics();

    assert.match(notice, /manual disconnect|不會列入 Rejected Connections|Runtime Events/i);
    assert.match(allNotice, /所有 observed Gateway sessions|Disconnect/i);
    assert.match(runtimeCopy.destination, /Runtime Events|Core security events/);
  });

  it('appends manual disconnect semantics without duplicating existing notices', () => {
    const appended = appendManualDisconnectNotice('Disconnect command accepted.', 'agent-1');
    const alreadyExplained = appendManualDisconnectNotice(appended, 'agent-1');

    assert.match(appended, /Disconnect command accepted/);
    assert.match(appended, /不會列入 Rejected Connections/);
    assert.equal(alreadyExplained, appended);
    assert.match(rejectedConnectionsEmptyDescription(), /Disconnect|Runtime Events|Core security events/);
  });
});
