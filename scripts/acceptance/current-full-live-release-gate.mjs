#!/usr/bin/env node
/**
 * Phase 7B Current Full Live Release Gate acceptance scenario.
 *
 * Product promise under test:
 *   clean stack + fresh/upgrade migration + SourceSystem-only intake
 *   -> Source Flow default Pool -> LOWEST_LOAD Pool member Agent
 *   -> Assignment -> Dispatch Request -> Netty/Agent Delivery
 *   -> ACK -> Result -> Task COMPLETED.
 *
 * Use --dry-run for offline contract validation. Live mode requires a running
 * local/CI stack with a connected mock/runtime Agent whose id matches AGENT_ID.
 */

import fs from 'node:fs';
import path from 'node:path';

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const dryRun = hasArg('--dry-run') || flag('CURRENT_FULL_LIVE_DRY_RUN');
const tenantId = process.env.CURRENT_FULL_LIVE_TENANT_ID || 'tenant-phase7b-current-live';
const sourceSystem = normalize(process.env.CURRENT_FULL_LIVE_SOURCE_SYSTEM || 'SOURCE_X');
const agentId = process.env.CURRENT_FULL_LIVE_AGENT_ID || process.env.SOURCE_SYSTEM_ONLY_AGENT_ID || 'agent-local-001';
const poolId = process.env.CURRENT_FULL_LIVE_POOL_ID || `pool-phase7b-${slug(sourceSystem)}-default`;
const poolCode = process.env.CURRENT_FULL_LIVE_POOL_CODE || 'DEFAULT_PROCESSING_POOL';
const flowId = process.env.CURRENT_FULL_LIVE_FLOW_ID || `flow-phase7b-${slug(sourceSystem)}-default`;
const operatorId = process.env.CURRENT_FULL_LIVE_OPERATOR_ID || 'phase7b-current-live-release-gate';
const gatewayUrl = process.env.CURRENT_FULL_LIVE_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const credentialToken = process.env.CURRENT_FULL_LIVE_AGENT_TOKEN || process.env.AGENT_ONBOARDING_TOKEN || `phase7b-token-${Date.now()}`;
const evidenceFile = process.env.CURRENT_FULL_LIVE_EVIDENCE_FILE || '';
const transcriptFile = process.env.CURRENT_FULL_LIVE_HTTP_TRANSCRIPT_FILE || '';
const pollTimeoutMs = intEnv('CURRENT_FULL_LIVE_POLL_TIMEOUT_MS', 120000);
const pollIntervalMs = intEnv('CURRENT_FULL_LIVE_POLL_INTERVAL_MS', 1500);
const skipDispatchExecute = flag('CURRENT_FULL_LIVE_SKIP_DISPATCH_EXECUTE');
const skipAgentSetup = flag('CURRENT_FULL_LIVE_SKIP_AGENT_SETUP');

const transcript = [];

function hasArg(name) { return process.argv.includes(name); }
function flag(name) { return ['1', 'true', 'yes', 'on'].includes(String(process.env[name] || '').toLowerCase()); }
function intEnv(name, fallback) { const parsed = Number.parseInt(String(process.env[name] || ''), 10); return Number.isFinite(parsed) ? parsed : fallback; }
function origin(value) { return String(value || '').replace(/\/+$/, ''); }
function join(originValue, requestPath) { return `${origin(originValue)}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`; }
function normalize(value) { return String(value || '').trim().replace(/[.\-\s]+/g, '_').toUpperCase(); }
function slug(value) { return normalize(value).toLowerCase().replace(/_/g, '-'); }
function nowIso() { return new Date().toISOString(); }
function uniqueSuffix() { return `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`; }
function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function array(value) { return Array.isArray(value) ? value : []; }
function empty(value) { return value === undefined || value === null || String(value).trim() === ''; }
function firstNonBlank(...values) { return values.find((value) => !empty(value)); }
function status(value) { return normalize(value); }
function assert(condition, message) { if (!condition) throw new Error(message); }
function isEnvelope(body) { return isRecord(body) && typeof body.code === 'string' && Object.prototype.hasOwnProperty.call(body, 'data'); }
function unwrap(name, body, options = {}) {
  if (isEnvelope(body)) {
    if (body.code !== 'OK' && !options.allowError) throw new Error(`${name}: envelope code=${body.code} message=${body.message || ''}`);
    return body.data;
  }
  return body;
}
function callbackType(entry) { return status(entry?.callbackType || entry?.eventType || entry?.payload?.callbackType || entry?.payload?.eventType); }
function hasAck(entries) { return entries.some((entry) => ['TASK_ACK', 'ACK', 'AI_TASK_ACK'].includes(callbackType(entry)) && entry.accepted !== false); }
function hasTerminal(entries, summary) {
  if (summary?.terminalCallbackReceived) return true;
  return entries.some((entry) => ['TASK_RESULT', 'RESULT', 'TASK_ERROR', 'ERROR', 'AI_TASK_RESULT', 'AI_TASK_ERROR'].includes(callbackType(entry)) && entry.accepted !== false);
}
function taskOf(runtime) { return runtime?.task || runtime?.record || runtime?.taskRecord || runtime || {}; }
function routingOf(runtime) { return runtime?.routingDecision || runtime?.routing_decision || runtime?.routing || {}; }
function requestsOf(runtime) { return array(runtime?.dispatchRequests || runtime?.dispatch_requests || runtime?.requests); }
function pickDispatchRequestId(intake, runtime) {
  const requests = requestsOf(runtime);
  const first = requests[0] || {};
  return firstNonBlank(
    intake?.dispatchRequestId,
    intake?.dispatch_request_id,
    intake?.deliveryId,
    intake?.delivery_id,
    first.dispatchRequestId,
    first.dispatch_request_id,
    first.requestId,
    first.request_id,
    first.deliveryId,
    first.delivery_id,
    first.id,
  );
}
function pickAssignmentId(intake, runtime) {
  const first = requestsOf(runtime)[0] || {};
  return firstNonBlank(intake?.assignmentId, intake?.assignment_id, first.assignmentId, first.assignment_id);
}

async function request(name, method, requestPath, body, options = {}) {
  const url = join(coreOrigin, requestPath);
  const startedAt = nowIso();
  if (dryRun) {
    const response = options.dryRunResponse || {};
    transcript.push({ name, method, url, dryRun: true, request: body, response });
    console.log(`[DRY-RUN] ${method} ${url}`);
    if (body !== undefined) console.log(JSON.stringify(body, null, 2));
    return response;
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
  transcript.push({
    name,
    method,
    url,
    startedAt,
    finishedAt: nowIso(),
    status: response.status,
    request: body,
    response: parsed,
  });
  if (!options.allowHttpError && (response.status < 200 || response.status >= 300)) {
    throw new Error(`${name}: HTTP ${response.status}; body=${text.slice(0, 1600)}`);
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
  throw new Error(`${name}: timed out after ${timeoutMs}ms; last=${JSON.stringify(last).slice(0, 1800)}`);
}

function sourceSystemPayload() {
  return {
    tenantId,
    sourceSystemId: sourceSystem,
    displayName: `${sourceSystem} Current Full Live Source`,
    description: 'Phase 7B Current full live release gate source system.',
    status: 'ACTIVE',
  };
}
function agentSetupPayload() {
  return {
    tenantId,
    agentId,
    agentName: `${sourceSystem} Current Full Live Agent`,
    ownerTeam: 'release-gate',
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
      phase7b: true,
      currentFullLiveReleaseGate: true,
      capabilityReferenceOnly: true,
    },
  };
}
function agentPoolPayload() {
  return {
    tenantId,
    poolId,
    poolCode,
    poolName: `${sourceSystem} Default Processing Pool`,
    sourceSystem,
    poolType: 'TRIAGE',
    selectionStrategy: 'LOWEST_LOAD',
    status: 'ACTIVE',
    description: 'Phase 7B default Agent Pool for Current full live release gate.',
    members: [{
      tenantId,
      poolId,
      poolCode,
      agentId,
      agentName: `${sourceSystem} Current Full Live Agent`,
      memberStatus: 'ACTIVE',
      priority: 10,
      weight: 1,
      metadata: { phase7b: true, currentFullLiveReleaseGate: true },
    }],
    metadata: { phase7b: true, currentFullLiveReleaseGate: true },
  };
}
function sourceFlowPayload() {
  return {
    tenantId,
    flowId,
    flowCode: `${sourceSystem}_CURRENT_FULL_LIVE_FLOW`,
    flowName: `${sourceSystem} Current Full Live Flow`,
    sourceSystem,
    flowType: 'SOURCE_FLOW',
    defaultPoolId: poolId,
    status: 'ACTIVE',
    description: 'SourceSystem-only Current full live release gate Flow.',
    defaultCapabilityRequirementMode: 'NONE',
    defaultCandidatePoolMode: 'SOURCE_SYSTEM_POOL',
    defaultRoutingStrategy: 'LOWEST_LOAD',
    requiredSkills: [],
    requiredCapabilities: [],
    agents: [],
    rules: [],
    metadata: {
      phase7b: true,
      currentFullLiveReleaseGate: true,
      noCapabilityRoutingGate: true,
    },
  };
}
function intakePayload() {
  return {
    tenantId,
    sourceSystem,
    severity: 'CRITICAL',
    message: `${sourceSystem} Phase 7B SourceSystem-only live event ${uniqueSuffix()}`,
    attributes: {
      phase7b: true,
      currentFullLiveReleaseGate: true,
      contract: 'sourceSystem-only intake must route through Source Flow default Pool and complete via ACK/Result',
    },
  };
}

async function setupConfiguration() {
  await request('create Source System', 'POST', `/admin/source-systems?tenantId=${encodeURIComponent(tenantId)}`, sourceSystemPayload(), { dryRunResponse: sourceSystemPayload() });
  if (!skipAgentSetup) {
    await request('setup approved Agent', 'POST', '/admin/agents/setup', agentSetupPayload(), { dryRunResponse: { tenantId, agentId, approvalStatus: 'APPROVED' } });
  }
  const pool = await request('create Default Agent Pool', 'POST', `/admin/dispatch-flows/agent-pools?tenantId=${encodeURIComponent(tenantId)}`, agentPoolPayload(), { dryRunResponse: agentPoolPayload() });
  assert(firstNonBlank(pool?.poolId, poolId) === poolId, 'Default Agent Pool response must preserve poolId');
  const flow = await request('create active Source Flow', 'POST', `/admin/dispatch-flows?tenantId=${encodeURIComponent(tenantId)}`, sourceFlowPayload(), { dryRunResponse: sourceFlowPayload() });
  assert(firstNonBlank(flow?.flowId, flowId) === flowId, 'Source Flow response must preserve flowId');
  assert(firstNonBlank(flow?.defaultPoolId, poolId) === poolId, 'Source Flow response must preserve defaultPoolId');
}

function dryRuntime(taskId, dispatchRequestId) {
  return {
    task: {
      tenantId,
      taskId,
      sourceSystem,
      objectType: 'UNKNOWN',
      eventType: 'UNKNOWN',
      errorCode: 'UNKNOWN',
      taskType: 'TRIAGE',
      status: 'COMPLETED',
      callbackStatus: 'COMPLETED',
      targetPoolId: poolId,
      assignedPoolId: poolId,
      matchedFlowId: flowId,
      matchedRuleId: 'SOURCE_DEFAULT',
      routingPath: 'SOURCE_FLOW_DEFAULT_POOL',
      requiredCapabilities: [],
    },
    routingDecision: {
      selectedAgentId: agentId,
      matchedFlowId: flowId,
      matchedRuleId: 'SOURCE_DEFAULT',
      targetPoolId: poolId,
      assignedPoolId: poolId,
      routingPath: 'SOURCE_FLOW_DEFAULT_POOL',
      poolMemberCount: 1,
      eligibleAgentCount: 1,
    },
    dispatchRequests: [{
      dispatchRequestId,
      taskId,
      assignmentId: `assignment-phase7b-${uniqueSuffix()}`,
      agentId,
      targetPoolId: poolId,
      assignedPoolId: poolId,
      status: 'COMPLETED',
      dispatchedAt: nowIso(),
    }],
  };
}

async function submitIntake() {
  return request('SourceSystem-only intake', 'POST', '/api/events/intake', intakePayload(), {
    dryRunResponse: {
      taskCreated: true,
      taskId: `task-phase7b-${uniqueSuffix()}`,
      taskType: 'TRIAGE',
      assignmentCreated: true,
      assignmentId: `assignment-phase7b-${uniqueSuffix()}`,
      dispatchRequestId: `dispatch-phase7b-${uniqueSuffix()}`,
      selectedAgentId: agentId,
      sourceSystem,
      eventStage: 'EXTERNAL',
    },
  });
}
async function loadRuntime(taskId, dispatchRequestId) {
  return request('load Task runtime-view', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/runtime-view`, undefined, { dryRunResponse: dryRuntime(taskId, dispatchRequestId) });
}
function assertCurrentRouting(intake, runtime) {
  const task = taskOf(runtime);
  const routing = routingOf(runtime);
  const requests = requestsOf(runtime);
  const selected = firstNonBlank(intake?.selectedAgentId, routing?.selectedAgentId, requests[0]?.agentId, requests[0]?.selectedAgentId);
  assert(firstNonBlank(task?.targetPoolId, task?.target_pool_id, routing?.targetPoolId, routing?.target_pool_id) === poolId, 'Task must target default Agent Pool');
  assert(firstNonBlank(task?.assignedPoolId, task?.assigned_pool_id, requests[0]?.assignedPoolId, requests[0]?.assigned_pool_id) === poolId, 'Assignment must record assignedPoolId');
  assert(firstNonBlank(task?.matchedFlowId, task?.matched_flow_id, routing?.matchedFlowId, routing?.matched_flow_id) === flowId, 'Task must record matched Source Flow');
  assert(status(firstNonBlank(task?.matchedRuleId, task?.matched_rule_id, routing?.matchedRuleId, routing?.matched_rule_id)) === 'SOURCE_DEFAULT', 'SourceSystem-only event must use SOURCE_DEFAULT fallback');
  assert(status(firstNonBlank(task?.routingPath, task?.routing_path, routing?.routingPath, routing?.routing_path)) === 'SOURCE_FLOW_DEFAULT_POOL', 'Routing path must be SOURCE_FLOW_DEFAULT_POOL');
  assert(selected === agentId, `LOWEST_LOAD must select the connected Pool member Agent; expected ${agentId}, got ${selected || '(blank)'}`);
  assert(array(task?.requiredCapabilities || task?.required_capabilities || task?.requiredSkills).length === 0, 'Current Source Flow default Pool path must not require Capability gates');
}

async function waitForAssignment(intake) {
  const taskId = firstNonBlank(intake?.taskId, intake?.task_id);
  const dispatchRequestId = firstNonBlank(intake?.dispatchRequestId, intake?.dispatch_request_id, `dispatch-phase7b-${uniqueSuffix()}`);
  assert(!empty(taskId), 'Intake response must include taskId');
  return waitFor('Task assignment/routing evidence', () => loadRuntime(taskId, dispatchRequestId), (candidate) => {
    try { assertCurrentRouting(intake, candidate); return true; }
    catch (error) { if (dryRun) throw error; return false; }
  });
}

async function ensureDispatchDelivery(dispatchRequestId) {
  if (empty(dispatchRequestId)) throw new Error('Dispatch request id is required to verify Delivery/ACK/Result path');
  let dispatch = await request('read Dispatch Request', 'GET', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}`, undefined, {
    allowHttpError: false,
    dryRunResponse: { dispatchRequestId, status: 'CREATED' },
  });
  if (!skipDispatchExecute && !['DISPATCHED', 'COMPLETED'].includes(status(dispatch?.status))) {
    await request('execute Dispatch Request', 'POST', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}/execute`, undefined, {
      dryRunResponse: { dispatchRequestId, status: 'DISPATCHED', dispatchedAt: nowIso() },
    });
  }
  dispatch = await waitFor('Dispatch delivered to Netty/Agent', () => request('poll Dispatch Request', 'GET', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}`, undefined, {
    dryRunResponse: { dispatchRequestId, status: 'COMPLETED', dispatchedAt: nowIso() },
  }), (value) => ['DISPATCHED', 'COMPLETED'].includes(status(value?.status)) || Boolean(value?.dispatchedAt));
  return dispatch;
}
async function waitForCallbacks(taskId) {
  const ackEntries = await waitFor('Agent ACK callback', () => request('poll callback inbox for ACK', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox?limit=100`, undefined, {
    dryRunResponse: [{ callbackType: 'TASK_ACK', accepted: true, receivedAt: nowIso() }],
  }), (value) => Array.isArray(value) && hasAck(value));
  const terminalEntries = await waitFor('Agent RESULT callback', () => request('poll callback inbox for RESULT', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox?limit=100`, undefined, {
    dryRunResponse: [{ callbackType: 'TASK_ACK', accepted: true }, { callbackType: 'TASK_RESULT', accepted: true, receivedAt: nowIso() }],
  }), async (value) => {
    if (!Array.isArray(value)) return false;
    const summary = await request('callback inbox summary', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox/summary?limit=100`, undefined, {
      dryRunResponse: { terminalCallbackReceived: true, ackCallbackReceived: true },
    });
    return hasTerminal(value, summary);
  });
  const summary = await request('callback inbox summary final', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox/summary?limit=100`, undefined, {
    dryRunResponse: { terminalCallbackReceived: true, ackCallbackReceived: true },
  });
  return { ackCount: ackEntries.length, terminalCount: terminalEntries.length, summary };
}
async function waitForTaskCompleted(taskId, dispatchRequestId) {
  return waitFor('Task COMPLETED', () => loadRuntime(taskId, dispatchRequestId), (runtime) => {
    const task = taskOf(runtime);
    return ['COMPLETED', 'SUCCEEDED', 'SUCCESS'].includes(status(task?.status)) || ['COMPLETED', 'SUCCEEDED', 'SUCCESS'].includes(status(task?.callbackStatus));
  });
}

function writeJson(outputPath, value) {
  if (!outputPath) return;
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(value, null, 2)}\n`);
}
async function writeOutputs(evidence) {
  if (transcriptFile) writeJson(transcriptFile, transcript);
  if (evidenceFile) writeJson(evidenceFile, evidence);
}

async function main() {
  console.log(`[phase7b-current-live] Core=${coreOrigin}`);
  console.log(`[phase7b-current-live] tenant=${tenantId} source=${sourceSystem} flow=${flowId} pool=${poolId} agent=${agentId}`);
  if (dryRun) console.log('[phase7b-current-live] Dry-run contract mode: no HTTP side effects.');
  const startedAt = nowIso();
  await setupConfiguration();
  const intake = await submitIntake();
  const taskId = firstNonBlank(intake?.taskId, intake?.task_id);
  const assignmentRuntime = await waitForAssignment(intake);
  const assignmentId = pickAssignmentId(intake, assignmentRuntime);
  const dispatchRequestId = pickDispatchRequestId(intake, assignmentRuntime);
  assert(!empty(dispatchRequestId), 'Current full live gate must create a Dispatch Request for Delivery verification');
  const dispatch = await ensureDispatchDelivery(dispatchRequestId);
  const callbacks = await waitForCallbacks(taskId);
  const finalRuntime = await waitForTaskCompleted(taskId, dispatchRequestId);
  const task = taskOf(finalRuntime);
  const evidence = {
    phase: '7B',
    gate: 'current-full-live-release-gate',
    mode: dryRun ? 'dry-run' : 'live',
    startedAt,
    completedAt: nowIso(),
    tenantId,
    sourceSystem,
    flowId,
    resolutionType: 'SOURCE_DEFAULT',
    targetPoolId: poolId,
    selectionStrategy: 'LOWEST_LOAD',
    selectedAgentId: agentId,
    taskId,
    assignmentId,
    dispatchRequestId,
    dispatchStatus: dispatch?.status || null,
    ackReceived: callbacks.ackCount > 0 || callbacks.summary?.ackCallbackReceived === true,
    resultReceived: callbacks.terminalCount > 0 || callbacks.summary?.terminalCallbackReceived === true,
    taskStatus: task?.status || null,
    callbackStatus: task?.callbackStatus || null,
    requiredPath: [
      'Clean Stack',
      'Fresh DB Migration',
      'Upgrade DB Migration',
      'Source System',
      'Source Flow',
      'Default Pool',
      'Pool Member Agent',
      'Runtime Agent',
      'SourceSystem-only Event',
      'Task',
      'Assignment',
      'Delivery',
      'ACK',
      'Result',
      'COMPLETED',
    ],
  };
  await writeOutputs(evidence);
  console.log(`[phase7b-current-live] PASS task=${taskId} assignment=${assignmentId || '-'} dispatch=${dispatchRequestId} status=${evidence.taskStatus || '-'} callback=${evidence.callbackStatus || '-'}`);
}

main().catch(async (error) => {
  const failed = {
    phase: '7B',
    gate: 'current-full-live-release-gate',
    mode: dryRun ? 'dry-run' : 'live',
    failedAt: nowIso(),
    tenantId,
    sourceSystem,
    flowId,
    targetPoolId: poolId,
    selectedAgentId: agentId,
    error: error instanceof Error ? error.stack || error.message : String(error),
  };
  await writeOutputs(failed);
  console.error(`[phase7b-current-live] FAILED: ${failed.error}`);
  process.exit(1);
});
