import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { parseDispatchUserFacingError } from '../lib/dispatch-readiness/dispatchUserFacingError';
import { buildDispatchOperatorActions, dispatchRunbookRefForCode } from '../lib/dispatch-readiness/dispatchOperatorActions';

describe('dispatch user-facing error parser', () => {
  it('splits code, message, next action, and technical details without dropping nested markers', () => {
    const parsed = parseDispatchUserFacingError(
      'DISPATCH_AGENT_NO_CAPACITY: 候選 Agent 目前沒有足夠容量接新任務。 下一步：請等待目前任務完成。 Technical details: selectedAgent=agent-1; routingReason=DISPATCH_NO_AGENT_ONLINE: x Technical details: nested=true'
    );

    assert.equal(parsed.code, 'DISPATCH_AGENT_NO_CAPACITY');
    assert.equal(parsed.message, '候選 Agent 目前沒有足夠容量接新任務。');
    assert.equal(parsed.nextAction, '請等待目前任務完成。');
    assert.match(parsed.technicalDetails ?? '', /nested=true/);
  });

  it('uses structured error before legacy text', () => {
    const parsed = parseDispatchUserFacingError('legacy fallback', {
      code: 'DISPATCH_PROFILE_NOT_CONFIGURED',
      severity: 'HIGH',
      message: '此任務找不到對應的後台派工 Profile。',
      nextAction: '建立或啟用 Profile。',
      technicalDetails: { sourceSystem: 'ERP', taskType: 'INCIDENT_REMEDIATION' }
    });

    assert.equal(parsed.code, 'DISPATCH_PROFILE_NOT_CONFIGURED');
    assert.equal(parsed.nextAction, '建立或啟用 Profile。');
    assert.match(parsed.technicalDetails ?? '', /sourceSystem/);
  });

  it('parses a lone DISPATCH code as a code instead of a raw message', () => {
    const parsed = parseDispatchUserFacingError('DISPATCH_AGENT_PROFILE_MISSING');

    assert.equal(parsed.code, 'DISPATCH_AGENT_PROFILE_MISSING');
    assert.equal(parsed.message, '目前沒有明確阻擋原因。');
  });

  it('keeps delayed recovery reason outside of the next-action field', () => {
    const parsed = parseDispatchUserFacingError(
      'DISPATCH_DELAYED_NO_ELIGIBLE_AGENT: 目前沒有符合條件的 Agent 可派工，系統已安排稍後自動重試。 下一步：請先依原因修正 Agent。 原因：DISPATCH_AGENT_PROFILE_MISSING 下次重試：2026-07-05T00:00:00Z Technical details: nextDispatchAttemptAt=2026-07-05T00:00:00Z'
    );

    assert.equal(parsed.nextAction, '請先依原因修正 Agent。');
    assert.match(parsed.message, /原因：DISPATCH_AGENT_PROFILE_MISSING/);
  });

  it('maps dispatch codes to operator action links', () => {
    const profileActions = buildDispatchOperatorActions({ code: 'DISPATCH_PROFILE_NOT_CONFIGURED' });
    assert.equal(profileActions[0]?.href, '/dispatch-flows');
    assert.equal(profileActions[0]?.label, '開啟派工設定');

    const capacityActions = buildDispatchOperatorActions({ code: 'DISPATCH_AGENT_NO_CAPACITY' }, { agentId: 'agent-1' });
    assert.ok(capacityActions.some((action) => action.href === '/agents/runtime'));
    assert.ok(capacityActions.some((action) => action.href === '/agents/agent-1'));
  });

  it('adds task commands only when action context asks for commands', () => {
    const linkOnly = buildDispatchOperatorActions({ code: 'DISPATCH_DELAYED_NO_ELIGIBLE_AGENT' });
    assert.equal(linkOnly.some((action) => action.command === 'triggerRecoveryNow'), false);

    const commandActions = buildDispatchOperatorActions(
      { code: 'DISPATCH_DELAYED_NO_ELIGIBLE_AGENT' },
      { taskId: 'task-1', includeTaskCommands: true }
    );
    assert.ok(commandActions.some((action) => action.command === 'triggerRecoveryNow'));

    const exhaustedActions = buildDispatchOperatorActions(
      { code: 'DISPATCH_RECOVERY_EXHAUSTED' },
      { taskId: 'task-1', includeTaskCommands: true }
    );
    assert.ok(exhaustedActions.some((action) => action.command === 'escalate'));
    assert.ok(exhaustedActions.some((action) => action.command === 'deadLetter'));
  });

  it('returns default runbook references for dispatch codes', () => {
    assert.equal(
      dispatchRunbookRefForCode('DISPATCH_AGENT_PROFILE_MISSING'),
      'runbooks/dispatch/agent-profile-missing'
    );
    assert.equal(
      dispatchRunbookRefForCode('DISPATCH_AGENT_PROFILE_MISSING', 'custom/runbook'),
      'custom/runbook'
    );
  });
});
