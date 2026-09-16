import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import {
  buildStandardDispatchTimeline,
  deriveTaskDispatchDiagnosis,
} from '../lib/tasks/dispatchLifecycle';
import { coreAdminApi } from '../lib/api/coreAdminApi';
import { setCoreTenantContext } from '../lib/api/client';
import type { CoreTaskRuntimeView } from '../lib/types/core';

function task(overrides: Partial<CoreTaskRuntimeView> = {}): CoreTaskRuntimeView {
  return {
    taskId: 'task-stage5',
    status: 'OPEN',
    sourceSystem: 'SRC_E2E',
    objectType: 'ORDER',
    eventType: 'ORDER_FAILED',
    routingPath: 'FLOW_RULE',
    ...overrides,
  };
}

describe('Stage 5 standard Task diagnosis', () => {
  it('normalizes historical missing-flow variants to the Current NO_MATCHING_FLOW reason', () => {
    const diagnosis = deriveTaskDispatchDiagnosis({
      task: task({ routingPath: 'FLOW_RULE_REQUIRED_BLOCKED', blockedReason: 'No active flow rule' }),
      evidence: { taskId: 'task-stage5', firstBlockingCode: 'NO_SOURCE_SYSTEM_PROFILE_MATCH' },
    });

    assert.equal(diagnosis.code, 'NO_MATCHING_FLOW');
    assert.equal(diagnosis.actionLabel, '建立派工流程');
    assert.match(diagnosis.actionHref ?? '', /sourceSystem=SRC_E2E/);
    assert.doesNotMatch(diagnosis.reason, /Service Scope|Assignment Profile|Source Default/i);
  });

  it('keeps capability labels as reference without blocking Current Agent Pool routing', () => {
    const diagnosis = deriveTaskDispatchDiagnosis({
      task: task({
        matchedFlowId: 'flow-1',
        matchedRuleId: 'rule-1',
        requestedSkill: 'CAP_DOCUMENT_ANALYSIS',
        requiredCapabilities: ['CAP_DOCUMENT_ANALYSIS'],
      }),
      runtimeVerification: {
        taskId: 'task-stage5',
        firstBlockingCode: 'REQUIRED_CAPABILITY_MISSING',
      },
    });

    assert.equal(diagnosis.code, 'IN_PROGRESS');
    assert.deepEqual(diagnosis.missingCapabilities, ['CAP_DOCUMENT_ANALYSIS']);
    assert.equal(diagnosis.actionHref, undefined);
    assert.doesNotMatch(diagnosis.reason, /缺少必要特殊能力|Capability.*阻擋/i);
  });

  it('maps manual assignment evidence to the operator remediation stage', () => {
    const diagnosis = deriveTaskDispatchDiagnosis({
      task: task({
        matchedFlowId: 'flow-1',
        matchedRuleId: 'rule-1',
        dispatchRetryReason: 'MANUAL_ASSIGNMENT_REQUIRED: no automatic candidate selected',
      }),
    });

    assert.equal(diagnosis.code, 'MANUAL_ASSIGNMENT_REQUIRED');
    assert.equal(diagnosis.actionLabel, '執行人工處置');
    assert.equal(diagnosis.actionHref, '/tasks/task-stage5');
  });

  it('links an offline blocker directly to the selected Agent', () => {
    const diagnosis = deriveTaskDispatchDiagnosis({
      task: task({
        matchedFlowId: 'flow-1',
        matchedRuleId: 'rule-1',
        assignedAgentId: 'agent-1',
        blockedReason: 'RUNTIME_NOT_CONNECTED',
      }),
    });

    assert.equal(diagnosis.code, 'AGENT_OFFLINE');
    assert.equal(diagnosis.actionHref, '/agents/agent-1');
  });

  it('builds the same eight-step Event-to-Result timeline used by Task Detail', () => {
    const completed = task({
      status: 'COMPLETED',
      sourceEventId: 'event-1',
      matchedFlowId: 'flow-1',
      matchedRuleId: 'rule-1',
      assignedAgentId: 'agent-1',
      dispatchRequestId: 'dispatch-1',
      dispatchStatus: 'COMPLETED',
      callbackStatus: 'COMPLETED',
    });
    const diagnosis = deriveTaskDispatchDiagnosis({ task: completed });
    const timeline = buildStandardDispatchTimeline(completed, undefined, diagnosis);

    assert.deepEqual(timeline.map((step) => step.id), [
      'event', 'task', 'flow', 'agent', 'assignment', 'delivery', 'ack', 'result',
    ]);
    assert.ok(timeline.every((step) => step.state === 'done'));
  });
});


const ORIGINAL_FETCH = globalThis.fetch;
const ORIGINAL_ENV = { ...process.env };

describe('Stage 5 real Flow test-event API client', () => {
  let requestedUrl = '';
  let requestedInit: RequestInit | undefined;

  beforeEach(() => {
    process.env.NEXT_PUBLIC_AUTH_ENABLED = 'false';
    process.env.NEXT_PUBLIC_CORE_API_BASE_URL = '/core-api';
    setCoreTenantContext('tenant-a');
    globalThis.fetch = async (input, init) => {
      requestedUrl = String(input);
      requestedInit = init;
      return new Response(JSON.stringify({
        code: 'OK',
        message: 'Success',
        data: { taskCreated: true, taskId: 'task-real-1', assignmentCreated: true },
        timestamp: '2026-07-13T00:00:00Z',
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    };
  });

  afterEach(() => {
    globalThis.fetch = ORIGINAL_FETCH;
    process.env = { ...ORIGINAL_ENV };
    setCoreTenantContext('');
  });

  it('posts to the persisted Flow real-test endpoint with the selected tenant', async () => {
    const response = await coreAdminApi.createDispatchFlowRealTestEvent('flow-1', { message: 'real event' }, 'tenant-a');

    const url = new URL(requestedUrl, 'http://opendispatch.local');
    assert.equal(url.pathname, '/core-api/admin/dispatch-flows/flow-1/test-event');
    assert.equal(url.searchParams.get('tenantId'), 'tenant-a');
    assert.equal(requestedInit?.method, 'POST');
    assert.equal(response.taskId, 'task-real-1');
  });
});
