#!/usr/bin/env node
/**
 * P6 Empty-DB Flow Rule E2E acceptance gate.
 *
 * This gate validates the product promise that a tenant with no pre-seeded
 * Dispatch Flow records can create a Flow-owned contract through Admin/Core APIs
 * and then route new intake tasks through formal FLOW_RULE evidence.
 *
 * Live mode expects a running Core service. Use --dry-run to print the exact
 * API sequence and validate the gate logic without calling Core.
 */

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const tenantId = process.env.P6_TENANT_ID || 'tenant-p6-empty-db';
const agentId = process.env.P6_AGENT_ID || 'agent-p6-flow-rule-001';
const gatewayUrl = process.env.P6_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const credentialToken = process.env.P6_AGENT_TOKEN || `p6-token-${Date.now()}`;
const operatorId = process.env.P6_OPERATOR_ID || 'p6-empty-db-e2e';
const dryRun = hasArg('--dry-run') || flag('P6_DRY_RUN');
const skipAgentSetup = hasArg('--skip-agent-setup') || flag('P6_SKIP_AGENT_SETUP');
const skipIntake = hasArg('--skip-intake') || flag('P6_SKIP_INTAKE');
const negativeIntake = hasArg('--negative-intake') || flag('P6_NEGATIVE_INTAKE');
const requireIssueLink = hasArg('--require-issue-link') || flag('P6_REQUIRE_ISSUE_LINK');
const pollTimeoutMs = intEnv('P6_POLL_TIMEOUT_MS', 60000);
const pollIntervalMs = intEnv('P6_POLL_INTERVAL_MS', 1500);

const positiveCases = [
  {
    code: 'ERP_VENDOR_BANK_CHANGE',
    sourceSystem: 'ERP',
    objectType: 'VENDOR',
    eventType: 'VENDOR_MASTER_BANK_ACCOUNT_CHANGED',
    errorCode: 'VENDOR_BANK_ACCOUNT_CHANGE',
    requestedSkill: 'ERP_VENDOR_MASTER_RISK_TRIAGE',
    message: 'P6 ERP vendor bank account changed: route to ERP risk triage Agent.',
  },
  {
    code: 'ERP_PAYMENT_RISK',
    sourceSystem: 'ERP',
    objectType: 'PAYMENT',
    eventType: 'PAYMENT_BLOCKED_BY_RISK_RULE',
    errorCode: 'PAYMENT_RISK_BLOCKED',
    requestedSkill: 'ERP_PAYMENT_RISK_TRIAGE',
    message: 'P6 ERP payment blocked by risk rule: route to ERP payment risk triage Agent.',
  },
  {
    code: 'MES_EQUIPMENT_ALARM',
    sourceSystem: 'MES',
    objectType: 'EQUIPMENT',
    eventType: 'EQUIPMENT_ALARM',
    errorCode: 'TEMP_HIGH',
    requestedSkill: 'MES_EQUIPMENT_ALARM_TRIAGE',
    message: 'P6 MES equipment high temperature alarm: route to MES equipment alarm triage Agent.',
  },
  {
    code: 'CMS_CONTENT_PUBLISH_FAILED',
    sourceSystem: 'CMS',
    objectType: 'CONTENT',
    eventType: 'CONTENT_PUBLISH_FAILED',
    errorCode: 'PUBLISH_VALIDATION_FAILED',
    requestedSkill: 'CMS_CONTENT_REVIEW',
    message: 'P6 CMS content publish failed: route to CMS content review Agent.',
  },
];

const negativeCases = [
  {
    code: 'NO_AGENT_ASSIGNMENT',
    sourceSystem: 'MES',
    objectType: 'LOT',
    eventType: 'LOT_HOLD_REQUIRED',
    errorCode: 'QA_HOLD',
    requestedSkill: 'MES_LOT_HOLD_TRIAGE',
    expectedBlockingCode: 'FLOW_AGENT_ASSIGNMENT',
    includeAgentAssignment: false,
    message: 'P6 negative case: Flow exists but no Agent assignment is configured.',
  },
  {
    code: 'AGENT_MISSING_REQUESTED_SKILL_GRANT',
    sourceSystem: 'ERP',
    objectType: 'GL_ENTRY',
    eventType: 'GL_POSTING_REJECTED',
    errorCode: 'GL_BALANCE_MISMATCH',
    requestedSkill: 'ERP_GL_POSTING_TRIAGE_UNGRANTED',
    expectedBlockingCode: 'AGENT_SKILL_GRANT',
    includeAgentAssignment: true,
    message: 'P6 negative case: Flow has assigned Agent but Agent lacks requestedSkill grant.',
  },
];

function hasArg(name) { return process.argv.includes(name); }
function flag(name) { return ['1', 'true', 'yes', 'on'].includes(String(process.env[name] || '').toLowerCase()); }
function intEnv(name, fallback) { const parsed = Number.parseInt(String(process.env[name] || ''), 10); return Number.isFinite(parsed) ? parsed : fallback; }
function origin(value) { return String(value || '').replace(/\/+$/, ''); }
function join(originValue, requestPath) { return `${originValue}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`; }
function normalize(value) { return String(value || '').trim().replace(/[.\-\s]+/g, '_').toUpperCase(); }
function slug(value) { return normalize(value).toLowerCase().replace(/_/g, '-'); }
function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function isStandardEnvelope(value) { return isRecord(value) && typeof value.code === 'string' && Object.prototype.hasOwnProperty.call(value, 'data'); }
function unwrap(name, body, options = {}) {
  if (isStandardEnvelope(body)) {
    if (body.code !== 'OK' && !options.allowError) throw new Error(`${name}: envelope code=${body.code} message=${body.message}`);
    return body.data;
  }
  return body;
}
function uniqueSuffix() { return `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`; }
function assert(condition, message) { if (!condition) throw new Error(message); }
function includesNormalized(text, expected) { return normalize(text).includes(normalize(expected)); }
function firstBlockingCode(response) { return response?.firstBlockingCode || response?.first_blocking_code || ''; }
function selectedAgent(response) { return response?.selectedAgentId || response?.selected_agent_id || ''; }
function taskIdOf(response) { return response?.taskId || response?.task_id || response?.id || null; }

async function request(name, method, requestPath, body, options = {}) {
  if (dryRun) {
    console.log(`[DRY-RUN] ${method} ${join(coreOrigin, requestPath)}`);
    if (body !== undefined) console.log(JSON.stringify(body, null, 2));
    return options.dryRunResponse || {};
  }
  const response = await fetch(join(coreOrigin, requestPath), {
    method,
    headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
  });
  const text = await response.text();
  let parsed = null;
  if (text) {
    try { parsed = JSON.parse(text); } catch { parsed = { raw: text }; }
  }
  if (!options.allowHttpError && (response.status < 200 || response.status >= 300)) {
    throw new Error(`${name}: HTTP ${response.status}; body=${text.slice(0, 1500)}`);
  }
  return unwrap(name, parsed, options);
}
async function sleep(ms) { await new Promise((resolve) => setTimeout(resolve, ms)); }
async function waitFor(name, loader, predicate, timeoutMs = pollTimeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() <= deadline) {
    last = await loader();
    if (await predicate(last)) return last;
    await sleep(pollIntervalMs);
  }
  throw new Error(`${name}: timed out after ${timeoutMs}ms; last=${JSON.stringify(last).slice(0, 1500)}`);
}

function flowPayload(testCase, options = {}) {
  const caseCode = normalize(testCase.code);
  const sourceSystem = normalize(testCase.sourceSystem);
  const eventStage = 'EXTERNAL';
  const flowId = `flow-p6-${slug(caseCode)}`;
  const ruleId = `rule-p6-${slug(caseCode)}`;
  const skillId = `skill-p6-${slug(caseCode)}-${slug(testCase.requestedSkill)}`;
  return {
    tenantId,
    flowId,
    flowCode: `P6_${caseCode}_FLOW`,
    flowName: `P6 ${caseCode.replace(/_/g, ' ')} Flow`,
    sourceSystem,
    status: 'ACTIVE',
    description: `P6 empty-db acceptance Flow for ${caseCode}. Created through DB-backed Dispatch Flow CRUD, not migration seed.`,
    metadata: {
      p6EmptyDbE2E: true,
      createdBy: 'scripts/acceptance/p6-empty-db-flow-rule-e2e.mjs',
      expectedRuntimePath: 'FLOW_RULE',
    },
    rules: [{
      tenantId,
      ruleId,
      flowId,
      ruleCode: `P6_${caseCode}_RULE`,
      ruleName: `P6 ${caseCode.replace(/_/g, ' ')} Rule`,
      ruleScope: 'EXTERNAL_INTAKE',
      eventStage,
      sourceSystem,
      objectType: normalize(testCase.objectType),
      eventType: normalize(testCase.eventType),
      errorCode: normalize(testCase.errorCode),
      requestedSkill: normalize(testCase.requestedSkill),
      priority: 10,
      enabled: true,
      condition: { p6: true },
    }],
    requiredSkills: [{
      tenantId,
      id: skillId,
      flowId,
      ruleId,
      eventStage,
      agentRole: 'LEAD',
      skillCode: normalize(testCase.requestedSkill),
      skillName: normalize(testCase.requestedSkill).replace(/_/g, ' '),
      skillKind: 'FLOW_SKILL',
      required: true,
      openClawSkill: true,
      description: `P6 required skill for ${caseCode}.`,
    }],
    agents: options.includeAgentAssignment === false ? [] : [{
      tenantId,
      id: `flow-agent-p6-${slug(caseCode)}-${slug(agentId)}`,
      flowId,
      agentId,
      agentName: 'P6 Flow Rule Acceptance Agent',
      eventStage,
      agentRole: 'LEAD',
      assignmentStatus: 'ACTIVE',
      approvalStatus: 'APPROVED',
      readinessStatus: 'READY',
      runtimeStatus: 'CONNECTED',
    }],
  };
}

function dryRunRequest(testCase, flowId) {
  return {
    tenantId,
    flowId,
    sourceSystem: normalize(testCase.sourceSystem),
    eventStage: 'EXTERNAL',
    objectType: normalize(testCase.objectType),
    eventType: normalize(testCase.eventType),
    errorCode: normalize(testCase.errorCode),
    severity: 'CRITICAL',
    message: testCase.message,
    attributes: { p6EmptyDbE2E: true, caseCode: normalize(testCase.code) },
  };
}

function intakeEnvelope(testCase) {
  const suffix = uniqueSuffix();
  return {
    tenantId,
    sourceSystem: normalize(testCase.sourceSystem),
    eventStage: 'EXTERNAL',
    objectType: normalize(testCase.objectType),
    objectId: `P6-${normalize(testCase.code)}-${suffix}`,
    eventType: normalize(testCase.eventType),
    errorCode: normalize(testCase.errorCode),
    severity: 'CRITICAL',
    message: `${testCase.message} (${suffix})`,
    occurredAt: new Date().toISOString(),
    attributes: {
      p6EmptyDbE2E: true,
      uniqueSuffix: suffix,
      caseCode: normalize(testCase.code),
      expectedRequestedSkill: normalize(testCase.requestedSkill),
    },
  };
}

async function setupAgent() {
  if (skipAgentSetup) return;
  const capabilityCodes = positiveCases.map((testCase) => normalize(testCase.requestedSkill));
  await request('P6 setup dispatchable Agent with approved requestedSkill grants', 'POST', '/admin/agents/setup', {
    tenantId,
    agentId,
    agentName: 'P6 Flow Rule Acceptance Agent',
    ownerTeam: 'p6-e2e',
    description: 'Created by P6 empty-db Flow Rule E2E acceptance gate.',
    purpose: 'FLOW_RULE_ACCEPTANCE',
    runtimeType: 'Docker',
    gatewayUrl,
    credentialToken,
    autoApprove: true,
    createDefaultCapabilities: true,
    createRuntimeBinding: true,
    createSupplyProfile: false,
    createDefaultDispatchRule: false,
    defaultCapabilities: capabilityCodes,
    defaultTaskTypes: capabilityCodes,
    capacityLimit: 4,
    operatorId,
    metadata: { p6EmptyDbE2E: true, note: 'No migration seed required.' },
  }, {
    dryRunResponse: { agentId, setupStatus: 'READY', capabilityAssignments: capabilityCodes.map((capabilityCode) => ({ capabilityCode, status: 'APPROVED' })) },
  });
}

async function createFlow(testCase, options = {}) {
  const payload = flowPayload(testCase, options);
  const flow = await request(`P6 create DB-backed Dispatch Flow ${testCase.code}`, 'POST', `/admin/dispatch-flows?tenantId=${encodeURIComponent(tenantId)}`, payload, {
    dryRunResponse: payload,
  });
  assert(flow?.flowId || dryRun, `${testCase.code}: createDispatchFlow did not return flowId`);
  return { payload, flow: flow || payload, flowId: payload.flowId };
}

async function assertReadyDryRun(testCase, flowId) {
  const response = await request(`P6 dry-run ready ${testCase.code}`, 'POST', `/admin/dispatch-flows/${encodeURIComponent(flowId)}/dry-run?tenantId=${encodeURIComponent(tenantId)}`, dryRunRequest(testCase, flowId), {
    dryRunResponse: {
      tenantId,
      flowId,
      ruleId: `rule-p6-${slug(testCase.code)}`,
      requestedSkill: normalize(testCase.requestedSkill),
      selectedAgentId: agentId,
      ready: true,
      dispatchable: true,
      status: 'READY',
      checks: [
        { code: 'FLOW_RULE_MATCH', status: 'PASS' },
        { code: 'REQUESTED_SKILL', status: 'PASS' },
        { code: 'FLOW_REQUIRED_SKILL', status: 'PASS' },
        { code: 'FLOW_AGENT_ASSIGNMENT', status: 'PASS' },
        { code: 'AGENT_SKILL_GRANT', status: 'PASS' },
        { code: 'DISPATCHABLE_AGENT', status: 'PASS' },
      ],
    },
  });
  assert(response.ready === true || response.dispatchable === true, `${testCase.code}: dry-run is not ready: ${JSON.stringify(response).slice(0, 1500)}`);
  assert(normalize(response.requestedSkill) === normalize(testCase.requestedSkill), `${testCase.code}: dry-run requestedSkill mismatch: ${response.requestedSkill}`);
  assert(selectedAgent(response) === agentId, `${testCase.code}: dry-run selectedAgentId mismatch: ${selectedAgent(response)}`);
  const text = JSON.stringify(response);
  for (const marker of ['FLOW_RULE_MATCH', 'REQUESTED_SKILL', 'FLOW_REQUIRED_SKILL', 'FLOW_AGENT_ASSIGNMENT', 'AGENT_SKILL_GRANT', 'DISPATCHABLE_AGENT']) {
    assert(text.includes(marker), `${testCase.code}: dry-run missing check ${marker}`);
  }
  return response;
}

async function assertBlockedDryRun(testCase, flowId) {
  const response = await request(`P6 dry-run blocked ${testCase.code}`, 'POST', `/admin/dispatch-flows/${encodeURIComponent(flowId)}/dry-run?tenantId=${encodeURIComponent(tenantId)}`, dryRunRequest(testCase, flowId), {
    dryRunResponse: {
      tenantId,
      flowId,
      ruleId: `rule-p6-${slug(testCase.code)}`,
      requestedSkill: normalize(testCase.requestedSkill),
      selectedAgentId: testCase.includeAgentAssignment === false ? undefined : agentId,
      ready: false,
      dispatchable: false,
      status: 'BLOCKED',
      firstBlockingCode: testCase.expectedBlockingCode,
      checks: [{ code: testCase.expectedBlockingCode, status: 'BLOCKED', blocking: true }],
    },
  });
  assert(response.ready !== true && response.dispatchable !== true, `${testCase.code}: dry-run unexpectedly ready: ${JSON.stringify(response).slice(0, 1500)}`);
  const code = firstBlockingCode(response);
  assert(includesNormalized(code, testCase.expectedBlockingCode) || JSON.stringify(response).includes(testCase.expectedBlockingCode), `${testCase.code}: expected blocking ${testCase.expectedBlockingCode}, got ${code}; response=${JSON.stringify(response).slice(0, 1500)}`);
  return response;
}

async function assertTaskEvidence(taskId, testCase, intakeResponse) {
  if (!taskId || dryRun) return;
  const expectedSkill = normalize(testCase.requestedSkill);
  const expectedFlow = `flow-p6-${slug(testCase.code)}`;
  await waitFor(`P6 task ${taskId} formal FLOW_RULE evidence`, async () => {
    const views = await Promise.allSettled([
      request('task runtime view', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/runtime-view`, undefined, { allowHttpError: true }),
      request('task detail', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}`, undefined, { allowHttpError: true }),
      request('task dispatch evidence', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/dispatch-evidence`, undefined, { allowHttpError: true }),
      request('task routing decisions', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/routing-decisions`, undefined, { allowHttpError: true }),
      request('task case timeline', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/case-timeline`, undefined, { allowHttpError: true }),
    ]);
    const combined = {
      intakeResponse,
      taskViews: views.map((item) => item.status === 'fulfilled' ? item.value : { error: String(item.reason) }),
    };
    return combined;
  }, (combined) => {
    const text = JSON.stringify(combined);
    return text.includes('FLOW_RULE')
      && text.includes(expectedFlow)
      && text.includes(expectedSkill)
      && text.includes(agentId);
  });
}

async function runPositiveCase(testCase) {
  const { flowId } = await createFlow(testCase, { includeAgentAssignment: true });
  await assertReadyDryRun(testCase, flowId);
  if (skipIntake) return;
  const intake = await request(`P6 intake ${testCase.code}`, 'POST', '/api/events/intake', intakeEnvelope(testCase), {
    dryRunResponse: {
      taskCreated: true,
      taskId: `task-p6-${slug(testCase.code)}`,
      assignmentCreated: true,
      selectedAgentId: agentId,
      dispatchRequestCreated: true,
      matchedFlowId: flowId,
      matchedRuleId: `rule-p6-${slug(testCase.code)}`,
      requestedSkill: normalize(testCase.requestedSkill),
      routingPath: 'FLOW_RULE',
    },
  });
  assert(intake.taskCreated !== false, `${testCase.code}: intake did not create a task: ${JSON.stringify(intake).slice(0, 1500)}`);
  assert(intake.assignmentCreated === true, `${testCase.code}: assignmentCreated must be true: ${JSON.stringify(intake).slice(0, 1500)}`);
  assert(intake.dispatchRequestCreated === true, `${testCase.code}: dispatchRequestCreated must be true: ${JSON.stringify(intake).slice(0, 1500)}`);
  assert(selectedAgent(intake) === agentId, `${testCase.code}: selectedAgentId mismatch: ${selectedAgent(intake)}`);
  if (requireIssueLink) {
    const text = JSON.stringify(intake);
    assert(text.includes('issue') || text.includes('Issue'), `${testCase.code}: requireIssueLink enabled but response has no issue evidence.`);
  }
  const taskId = taskIdOf(intake);
  await assertTaskEvidence(taskId, testCase, intake);
}

async function runNegativeCase(testCase) {
  const { flowId } = await createFlow(testCase, { includeAgentAssignment: testCase.includeAgentAssignment });
  await assertBlockedDryRun(testCase, flowId);
  if (!negativeIntake || skipIntake) return;
  const intake = await request(`P6 negative intake ${testCase.code}`, 'POST', '/api/events/intake', intakeEnvelope(testCase), {
    dryRunResponse: {
      taskCreated: true,
      assignmentCreated: false,
      dispatchRequestCreated: false,
      reason: testCase.expectedBlockingCode,
      requestedSkill: normalize(testCase.requestedSkill),
      routingPath: 'FLOW_RULE_REQUIRED_BLOCKED',
    },
  });
  assert(intake.assignmentCreated !== true, `${testCase.code}: negative intake unexpectedly created assignment: ${JSON.stringify(intake).slice(0, 1500)}`);
  assert(intake.dispatchRequestCreated !== true, `${testCase.code}: negative intake unexpectedly created dispatch request: ${JSON.stringify(intake).slice(0, 1500)}`);
}

async function main() {
  console.log(`P6 Empty-DB Flow Rule E2E acceptance starting. tenantId=${tenantId} agentId=${agentId} dryRun=${dryRun}`);
  await setupAgent();
  for (const testCase of positiveCases) {
    await runPositiveCase(testCase);
  }
  for (const testCase of negativeCases) {
    await runNegativeCase(testCase);
  }
  console.log('P6 Empty-DB Flow Rule E2E acceptance completed.');
}

main().catch((error) => {
  console.error(error?.stack || error?.message || error);
  process.exit(1);
});
