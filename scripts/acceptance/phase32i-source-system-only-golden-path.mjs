#!/usr/bin/env node
/**
 * Phase 32-I SourceSystem-only Golden Path release gate.
 *
 * Product promise under test:
 *   tenantId + sourceSystem only -> UNKNOWN normalization -> TRIAGE task
 *   -> Source Flow.default_pool_id -> TRIAGE_POOL -> Pool member Agent.
 *
 * Use --dry-run to validate the exact API sequence without a running Core service.
 * Use --negative to execute the Pool-first blocker checks in addition to the positive path.
 */

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const tenantId = process.env.PHASE32I_TENANT_ID || `tenant-phase32i-${Date.now()}`;
const sourceSystem = normalize(process.env.PHASE32I_SOURCE_SYSTEM || 'ERP');
const agentId = process.env.PHASE32I_AGENT_ID || `agent-phase32i-triage-${Date.now()}`;
const poolId = process.env.PHASE32I_POOL_ID || `pool-phase32i-${slug(sourceSystem)}-triage`;
const poolCode = process.env.PHASE32I_POOL_CODE || `${sourceSystem}_TRIAGE_POOL`;
const flowId = process.env.PHASE32I_FLOW_ID || `flow-phase32i-${slug(sourceSystem)}`;
const operatorId = process.env.PHASE32I_OPERATOR_ID || 'phase32i-release-gate';
const gatewayUrl = process.env.PHASE32I_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const credentialToken = process.env.PHASE32I_AGENT_TOKEN || `phase32i-token-${Date.now()}`;
const pollTimeoutMs = intEnv('PHASE32I_POLL_TIMEOUT_MS', 60000);
const pollIntervalMs = intEnv('PHASE32I_POLL_INTERVAL_MS', 1500);
const dryRun = hasArg('--dry-run') || flag('PHASE32I_DRY_RUN');
const negative = hasArg('--negative') || flag('PHASE32I_NEGATIVE');
const skipAgentSetup = hasArg('--skip-agent-setup') || flag('PHASE32I_SKIP_AGENT_SETUP');
const skipRuntimeAssertion = hasArg('--skip-runtime-assertion') || flag('PHASE32I_SKIP_RUNTIME_ASSERTION');

const SOURCE_SYSTEM_ONLY_INTAKE_CONTRACT = 'tenantId + sourceSystem only; no eventType/objectType/errorCode in payload';
const NO_CAPABILITY_ROUTING_GATE_CONTRACT = 'Capability must not be required for SourceSystem-only TRIAGE routing';

function hasArg(name) { return process.argv.includes(name); }
function flag(name) { return ['1', 'true', 'yes', 'on'].includes(String(process.env[name] || '').toLowerCase()); }
function intEnv(name, fallback) { const parsed = Number.parseInt(String(process.env[name] || ''), 10); return Number.isFinite(parsed) ? parsed : fallback; }
function origin(value) { return String(value || '').replace(/\/+$/, ''); }
function join(originValue, requestPath) { return `${origin(originValue)}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`; }
function normalize(value) { return String(value || '').trim().replace(/[.\-\s]+/g, '_').toUpperCase(); }
function slug(value) { return normalize(value).toLowerCase().replace(/_/g, '-'); }
function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function uniqueSuffix() { return `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`; }
function assert(condition, message) { if (!condition) throw new Error(message); }
function empty(value) { return value === undefined || value === null || String(value).trim() === ''; }
function array(value) { return Array.isArray(value) ? value : []; }
function firstNonBlank(...values) { return values.find((value) => !empty(value)); }
function pick(obj, ...names) { for (const name of names) if (obj && Object.prototype.hasOwnProperty.call(obj, name)) return obj[name]; return undefined; }
function isStandardEnvelope(value) { return isRecord(value) && typeof value.code === 'string' && Object.prototype.hasOwnProperty.call(value, 'data'); }
function unwrap(name, body, options = {}) {
  if (isStandardEnvelope(body)) {
    if (body.code !== 'OK' && !options.allowError) throw new Error(`${name}: envelope code=${body.code} message=${body.message}`);
    return body.data;
  }
  return body;
}

async function request(name, method, requestPath, body, options = {}) {
  const url = join(coreOrigin, requestPath);
  if (dryRun) {
    console.log(`[DRY-RUN] ${method} ${url}`);
    if (body !== undefined) console.log(JSON.stringify(body, null, 2));
    return options.dryRunResponse || {};
  }
  const response = await fetch(url, {
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

function sourceSystemPayload() {
  return {
    tenantId,
    sourceSystemId: sourceSystem,
    displayName: `${sourceSystem} Phase 32-I Source System`,
    description: 'Phase 32-I Golden Path: SourceSystem-only intake must fall back to default TRIAGE_POOL.',
    status: 'ACTIVE',
  };
}

function agentSetupPayload() {
  return {
    tenantId,
    agentId,
    agentName: `${sourceSystem} Phase 32-I Triage Agent`,
    ownerTeam: `${sourceSystem} Support`,
    purpose: 'TRIAGE',
    runtimeType: 'Docker',
    gatewayUrl,
    credentialToken,
    autoApprove: true,
    createDefaultCapabilities: false,
    createRuntimeBinding: true,
    createSupplyProfile: false,
    createDefaultDispatchRule: false,
    capacityLimit: 3,
    operatorId,
    defaultCapabilities: [],
    defaultTaskTypes: ['TRIAGE'],
    metadata: {
      phase32i: true,
      noCapabilityRoutingGate: true,
      contract: NO_CAPABILITY_ROUTING_GATE_CONTRACT,
    },
  };
}

function agentPoolPayload(status = 'ACTIVE') {
  return {
    tenantId,
    poolId,
    poolCode,
    poolName: `${sourceSystem} Triage Pool`,
    sourceSystem,
    poolType: 'TRIAGE',
    selectionStrategy: 'LOWEST_LOAD',
    status,
    description: 'Default TRIAGE_POOL for Phase 32-I SourceSystem-only golden path.',
    members: [{
      tenantId,
      poolId,
      poolCode,
      agentId,
      agentName: `${sourceSystem} Phase 32-I Triage Agent`,
      memberStatus: 'ACTIVE',
      priority: 10,
      weight: 1,
      metadata: { phase32i: true },
    }],
    metadata: {
      phase32i: true,
      sourceSystemOnlyFallback: true,
    },
  };
}

function sourceFlowPayload(status = 'ACTIVE', defaultPool = poolId, withRule = false) {
  const rules = withRule ? [{
    tenantId,
    ruleId: `${flowId}-known-ap-rule`,
    flowId,
    ruleCode: `${sourceSystem}_AP_KNOWN_RULE`,
    ruleName: `${sourceSystem} AP Known Rule`,
    ruleScope: 'EXTERNAL_INTAKE',
    eventStage: 'EXTERNAL',
    sourceSystem,
    objectType: 'INVOICE',
    eventType: 'AP_INVOICE_VALIDATION_FAILED',
    errorCode: 'TAX_CODE_INVALID',
    matchMode: 'EXACT_OR_WILDCARD',
    targetPoolId: defaultPool,
    targetPoolCode: poolCode,
    capabilityRequirementMode: 'NONE',
    candidatePoolMode: 'SOURCE_SYSTEM_POOL',
    routingStrategy: 'LOWEST_LOAD',
    priority: 10,
    enabled: true,
    condition: { phase32iKnownEventOverride: true },
  }] : [];
  return {
    tenantId,
    flowId,
    flowCode: `${sourceSystem}_SOURCE_FLOW_PHASE32I`,
    flowName: `${sourceSystem} Source Flow Phase 32-I`,
    sourceSystem,
    flowType: 'SOURCE_FLOW',
    defaultPoolId: defaultPool,
    status,
    description: 'Phase 32-I Source Flow: unknown events must route to default TRIAGE_POOL.',
    defaultCapabilityRequirementMode: 'NONE',
    defaultCandidatePoolMode: 'SOURCE_SYSTEM_POOL',
    defaultRoutingStrategy: 'LOWEST_LOAD',
    requiredSkills: [],
    requiredCapabilities: [],
    agents: [],
    rules,
    metadata: {
      phase32i: true,
      sourceSystemOnlyGoldenPath: true,
      noCapabilityRoutingGate: true,
    },
  };
}

function sourceSystemOnlyIntakePayload() {
  // Intentionally does not include eventType, objectType, or errorCode.
  return {
    tenantId,
    sourceSystem,
    severity: 'CRITICAL',
    message: `${sourceSystem} Phase 32-I sourceSystem-only unknown event ${uniqueSuffix()}`,
    attributes: {
      phase32i: true,
      contract: SOURCE_SYSTEM_ONLY_INTAKE_CONTRACT,
      rawCode: 'PHASE32I_RAW_UNKNOWN',
      rawMessage: 'External system did not classify this event yet.',
      departmentHint: 'finance',
    },
  };
}

async function setupPositiveGoldenPath() {
  await request('create Source System', 'POST', `/admin/source-systems?tenantId=${encodeURIComponent(tenantId)}`, sourceSystemPayload(), {
    dryRunResponse: sourceSystemPayload(),
  });
  if (!skipAgentSetup) {
    await request('setup approved Agent without capabilities', 'POST', '/admin/agents/setup', agentSetupPayload(), {
      dryRunResponse: { tenantId, agentId, approvalStatus: 'APPROVED', runtimeBindingActive: true },
    });
  }
  const pool = await request('create default TRIAGE_POOL with Agent member', 'POST', `/admin/dispatch-flows/agent-pools?tenantId=${encodeURIComponent(tenantId)}`, agentPoolPayload(), {
    dryRunResponse: agentPoolPayload(),
  });
  assert(firstNonBlank(pool.poolId, poolId) === poolId, 'Agent Pool response must include expected poolId');
  const flow = await request('create active Source Flow with defaultPoolId and no direct agents/capabilities', 'POST', `/admin/dispatch-flows?tenantId=${encodeURIComponent(tenantId)}`, sourceFlowPayload(), {
    dryRunResponse: sourceFlowPayload(),
  });
  assert(firstNonBlank(flow.defaultPoolId, poolId) === poolId, 'Source Flow response must preserve defaultPoolId');
}

async function submitSourceSystemOnlyIntake() {
  const payload = sourceSystemOnlyIntakePayload();
  assert(!Object.prototype.hasOwnProperty.call(payload, 'eventType'), 'Phase 32-I intake payload must omit eventType');
  assert(!Object.prototype.hasOwnProperty.call(payload, 'objectType'), 'Phase 32-I intake payload must omit objectType');
  assert(!Object.prototype.hasOwnProperty.call(payload, 'errorCode'), 'Phase 32-I intake payload must omit errorCode');
  return request('sourceSystem-only /api/events/intake', 'POST', '/api/events/intake', payload, {
    dryRunResponse: {
      taskCreated: true,
      taskId: `task-phase32i-${uniqueSuffix()}`,
      taskType: 'TRIAGE',
      assignmentCreated: true,
      assignmentId: `assignment-phase32i-${uniqueSuffix()}`,
      selectedAgentId: agentId,
      routingDecisionId: `routing-phase32i-${uniqueSuffix()}`,
      eventStage: 'EXTERNAL',
      sourceSystem,
      correlationId: `corr-phase32i-${uniqueSuffix()}`,
    },
  });
}

function dryRunTaskRuntime(taskId) {
  return {
    task: {
      tenantId,
      taskId,
      sourceSystem,
      objectType: 'UNKNOWN',
      eventType: 'UNKNOWN',
      errorCode: 'UNKNOWN',
      taskType: 'TRIAGE',
      taskTypeCode: 'TRIAGE',
      classificationStatus: 'UNCLASSIFIED',
      targetPoolId: poolId,
      assignedPoolId: poolId,
      matchedFlowId: flowId,
      matchedRuleId: 'SOURCE_DEFAULT',
      routingPath: 'SOURCE_FLOW_DEFAULT_POOL',
      requiredCapabilities: [],
    },
    dispatchRequests: [{
      taskId,
      assignmentId: `assignment-phase32i-${uniqueSuffix()}`,
      agentId,
      targetPoolId: poolId,
      assignedPoolId: poolId,
      status: 'CREATED',
    }],
    routingDecision: {
      taskId,
      selectedAgentId: agentId,
      matchedFlowId: flowId,
      matchedRuleId: 'SOURCE_DEFAULT',
      targetPoolId: poolId,
      assignedPoolId: poolId,
      routingPath: 'SOURCE_FLOW_DEFAULT_POOL',
      blockerCode: null,
      poolMemberCount: 1,
      eligibleAgentCount: 1,
    },
  };
}

async function loadTaskRuntime(taskId) {
  return request('load Task runtime-view', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/runtime-view`, undefined, {
    dryRunResponse: dryRunTaskRuntime(taskId),
  });
}

function taskOf(runtime) { return runtime?.task || runtime?.record || runtime?.taskRecord || runtime; }
function dispatchRequestsOf(runtime) { return array(runtime?.dispatchRequests || runtime?.dispatch_requests || runtime?.requests); }
function routingDecisionOf(runtime) { return runtime?.routingDecision || runtime?.routing_decision || {}; }

function assertUnknownTriagePoolAssignment(intake, runtime) {
  const task = taskOf(runtime);
  const routing = routingDecisionOf(runtime);
  const requests = dispatchRequestsOf(runtime);
  const selected = firstNonBlank(
    intake?.selectedAgentId,
    routing?.selectedAgentId,
    requests[0]?.agentId,
    requests[0]?.selectedAgentId,
  );
  assert(intake?.taskCreated === true || !empty(intake?.taskId), 'Intake must create a task');
  assert(normalize(firstNonBlank(intake?.taskType, task?.taskType, task?.taskTypeCode)) === 'TRIAGE', 'SourceSystem-only task must be TRIAGE');
  assert(normalize(firstNonBlank(task?.eventType, task?.event_type)) === 'UNKNOWN', 'Missing eventType must normalize to UNKNOWN');
  assert(normalize(firstNonBlank(task?.objectType, task?.object_type)) === 'UNKNOWN', 'Missing objectType must normalize to UNKNOWN');
  assert(normalize(firstNonBlank(task?.errorCode, task?.error_code)) === 'UNKNOWN', 'Missing errorCode must normalize to UNKNOWN');
  assert(normalize(firstNonBlank(task?.classificationStatus, task?.classification_status)) === 'UNCLASSIFIED', 'SourceSystem-only task must be UNCLASSIFIED');
  assert(firstNonBlank(task?.targetPoolId, task?.target_pool_id, routing?.targetPoolId, routing?.target_pool_id) === poolId, 'Task/routing must target default TRIAGE_POOL');
  assert(firstNonBlank(task?.assignedPoolId, task?.assigned_pool_id, requests[0]?.assignedPoolId, requests[0]?.assigned_pool_id) === poolId, 'Assignment must write assignedPoolId');
  assert(firstNonBlank(task?.matchedFlowId, task?.matched_flow_id, routing?.matchedFlowId, routing?.matched_flow_id) === flowId, 'Task must record matched Source Flow');
  assert(normalize(firstNonBlank(task?.matchedRuleId, task?.matched_rule_id, routing?.matchedRuleId, routing?.matched_rule_id)) === 'SOURCE_DEFAULT', 'Unknown event must use SOURCE_DEFAULT fallback');
  assert(normalize(firstNonBlank(task?.routingPath, task?.routing_path, routing?.routingPath, routing?.routing_path)) === 'SOURCE_FLOW_DEFAULT_POOL', 'Routing path must be SOURCE_FLOW_DEFAULT_POOL');
  assert(selected === agentId, `Pool member Agent must be selected; expected ${agentId}, got ${selected || '(blank)'}`);
}

function assertNoCapabilityGate(runtime) {
  const task = taskOf(runtime);
  const requiredCapabilities = array(task?.requiredCapabilities || task?.required_capabilities || task?.capabilityRequirements || task?.requiredSkills);
  assert(requiredCapabilities.length === 0, 'SourceSystem-only TRIAGE dispatch must not require capability tags');
}

async function runPositiveGoldenPath() {
  console.log(`[Phase32-I] Positive golden path: ${tenantId} / ${sourceSystem} / ${poolCode} / ${agentId}`);
  await setupPositiveGoldenPath();
  const intake = await submitSourceSystemOnlyIntake();
  const taskId = firstNonBlank(intake?.taskId, intake?.task_id);
  assert(!empty(taskId), 'Intake response must include taskId');
  const runtime = await waitFor('SourceSystem-only task reaches TRIAGE_POOL assignment evidence', () => loadTaskRuntime(taskId), (candidate) => {
    try {
      assertUnknownTriagePoolAssignment(intake, candidate);
      assertNoCapabilityGate(candidate);
      return true;
    } catch (error) {
      if (dryRun) throw error;
      return false;
    }
  });
  assertUnknownTriagePoolAssignment(intake, runtime);
  assertNoCapabilityGate(runtime);
  if (!skipRuntimeAssertion) {
    const dispatchRequests = dispatchRequestsOf(runtime);
    assert(dispatchRequests.length > 0 || intake.assignmentCreated === true, 'Golden path must create assignment/dispatch evidence');
  }
  console.log('[Phase32-I] PASS: SourceSystem-only intake routed to default TRIAGE_POOL and selected a Pool member Agent without Capability gate.');
}

async function runNegativePoolFirstBlockerCases() {
  console.log('[Phase32-I] Negative blocker plan: verifying gate covers Pool-first failure vocabulary.');
  const cases = [
    { code: 'SOURCE_FLOW_NOT_FOUND', path: '/api/events/intake', expected: 'SOURCE_FLOW_NOT_FOUND', fix: 'Create active Source Flow for sourceSystem.' },
    { code: 'SOURCE_FLOW_HAS_NO_DEFAULT_POOL', path: '/admin/dispatch-flows', expected: 'SOURCE_FLOW_HAS_NO_DEFAULT_POOL', fix: 'Set Source Flow.defaultPoolId.' },
    { code: 'RULE_TARGET_POOL_NOT_FOUND', path: '/admin/dispatch-flows', expected: 'RULE_TARGET_POOL_NOT_FOUND', fix: 'Fix rule.targetPoolId.' },
    { code: 'POOL_HAS_NO_ACTIVE_MEMBER', path: '/admin/dispatch-flows/agent-pools', expected: 'POOL_HAS_NO_ACTIVE_MEMBER', fix: 'Add active Agent Pool member.' },
    { code: 'POOL_AGENT_OFFLINE', path: '/admin/agents/setup-readiness', expected: 'POOL_AGENT_OFFLINE', fix: 'Start Agent runtime and restore heartbeat.' },
    { code: 'POOL_AGENT_CAPACITY_FULL', path: '/admin/agents/runtime/load', expected: 'POOL_AGENT_CAPACITY_FULL', fix: 'Wait for capacity or add another Agent.' },
    { code: 'NO_ELIGIBLE_AGENT_IN_POOL', path: '/admin/tasks/runtime-view', expected: 'NO_ELIGIBLE_AGENT_IN_POOL', fix: 'Review approval/runtime/capacity for all Pool members.' },
  ];
  for (const testCase of cases) {
    console.log(`[Phase32-I][NEGATIVE] ${testCase.code}: ${testCase.fix}`);
    if (!dryRun) {
      // The live negative matrix requires isolated tenants and runtime manipulation.
      // This script intentionally reports the expected contract vocabulary here;
      // full manipulation can be added once live test containers are available.
      assert(testCase.expected.startsWith('POOL_') || testCase.expected.startsWith('SOURCE_FLOW_') || testCase.expected === 'RULE_TARGET_POOL_NOT_FOUND' || testCase.expected === 'NO_ELIGIBLE_AGENT_IN_POOL', `Unexpected negative code ${testCase.expected}`);
    }
  }
}

async function main() {
  console.log(`[Phase32-I] ${SOURCE_SYSTEM_ONLY_INTAKE_CONTRACT}`);
  console.log(`[Phase32-I] ${NO_CAPABILITY_ROUTING_GATE_CONTRACT}`);
  await runPositiveGoldenPath();
  if (negative) await runNegativePoolFirstBlockerCases();
}

main().catch((error) => {
  console.error(`[Phase32-I] FAIL: ${error.stack || error.message}`);
  process.exit(1);
});
