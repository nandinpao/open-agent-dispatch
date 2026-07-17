#!/usr/bin/env node
/**
 * Stage 23 real runtime dispatch delivery confirmation gate.
 *
 * This gate assumes a real Netty runtime Agent is connected (for example via
 * cluster-run-many-agents.sh with AGENT_WORKER_MODE=process-result). It verifies
 * the path after Core assignment authority:
 *
 *   dispatch request created
 *   -> Core executes Netty delivery
 *   -> Gateway delivers TASK_DISPATCH to the runtime
 *   -> Agent sends TASK_ACK
 *   -> Agent sends TASK_RESULT / TASK_ERROR
 *   -> Core persists callback inbox / ledger truth
 *   -> task reaches a terminal status
 *
 * Use --dry-run to print configuration without sending requests.
 */

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const tenantId = process.env.STAGE23_TENANT_ID || process.env.STAGE19_TENANT_ID || 'tenant-a';
const agentId = process.env.STAGE23_AGENT_ID || process.env.STAGE19_AGENT_ID || 'agent-cluster-node-001-002';
const gatewayNodeId = process.env.STAGE23_GATEWAY_NODE_ID || process.env.STAGE19_GATEWAY_NODE_ID || 'gateway-node-001';
const gatewayUrl = process.env.STAGE23_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const credentialToken = process.env.STAGE23_AGENT_TOKEN || process.env.STAGE19_AGENT_TOKEN || `stage23-token-${Date.now()}`;
const operatorId = process.env.STAGE23_OPERATOR_ID || 'stage23-e2e';
const effectiveCapabilities = normalizeList(process.env.STAGE23_EFFECTIVE_CAPABILITIES || 'MES_ALARM_TRIAGE,MES_LOT_TRACE,MES_YIELD_ANOMALY_ANALYSIS');
const defaultTaskTypes = normalizeList(process.env.STAGE23_DEFAULT_TASK_TYPES || 'INCIDENT_RESPONSE,MES_ALARM_TRIAGE,MES_LOT_TRACE,MES_YIELD_ANOMALY_ANALYSIS');
const dispatchRequiredCapabilities = normalizeList(process.env.STAGE23_EVENT_REQUIRED_CAPABILITIES || 'INCIDENT_ANALYSIS,DOMAIN:MES,PROVIDER:MES,OPERATION:ANALYZE');
const pollTimeoutMs = intEnv('STAGE23_POLL_TIMEOUT_MS', 90000);
const pollIntervalMs = intEnv('STAGE23_POLL_INTERVAL_MS', 1500);
const dryRun = hasArg('--dry-run') || flag('STAGE23_DRY_RUN');
const skipSetup = hasArg('--skip-setup') || flag('STAGE23_SKIP_SETUP');
const skipApprove = hasArg('--skip-approve') || flag('STAGE23_SKIP_APPROVE');
const skipExecute = hasArg('--skip-execute') || flag('STAGE23_SKIP_EXECUTE');
const requireIssueLink = hasArg('--require-issue-link') || flag('STAGE23_REQUIRE_ISSUE_LINK');

function hasArg(name) { return process.argv.includes(name); }
function flag(name) { return ['1', 'true', 'yes', 'on'].includes(String(process.env[name] || '').toLowerCase()); }
function intEnv(name, fallback) { const parsed = Number.parseInt(String(process.env[name] || ''), 10); return Number.isFinite(parsed) ? parsed : fallback; }
function origin(value) { return String(value || '').replace(/\/+$/, ''); }
function join(originValue, requestPath) { return `${originValue}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`; }
function normalize(value) { return String(value || '').trim().replace(/[.\-\s]+/g, '_').toUpperCase(); }
function normalizeList(value) { return [...new Set(String(value || '').split(',').map(normalize).filter(Boolean))]; }
function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function isStandardEnvelope(value) { return isRecord(value) && typeof value.code === 'string' && Object.prototype.hasOwnProperty.call(value, 'data'); }
function unwrap(name, body, options = {}) {
  if (isStandardEnvelope(body)) {
    if (body.code !== 'OK' && !options.allowError) throw new Error(`${name}: envelope code=${body.code} message=${body.message}`);
    return body.data;
  }
  return body;
}
async function request(name, method, requestPath, body, options = {}) {
  const response = await fetch(join(coreOrigin, requestPath), {
    method,
    headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
  });
  const text = await response.text();
  let parsed = null;
  if (text) { try { parsed = JSON.parse(text); } catch { parsed = { raw: text }; } }
  if (response.status !== 200 && !options.allowHttpError) throw new Error(`${name}: HTTP ${response.status}; body=${text.slice(0, 800)}`);
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
  throw new Error(`${name}: timed out after ${timeoutMs}ms; last=${JSON.stringify(last).slice(0, 1000)}`);
}
function arrayIncludes(values, expected) {
  const actual = normalizeList(Array.isArray(values) ? values.join(',') : String(values || ''));
  return expected.every((item) => actual.includes(normalize(item)));
}
function status(value) { return normalize(value); }
function callbackType(entry) { return status(entry?.callbackType || entry?.payload?.callbackType || entry?.payload?.eventType); }
function hasAck(entries) { return entries.some((entry) => ['TASK_ACK', 'ACK', 'AI_TASK_ACK'].includes(callbackType(entry)) && entry.accepted !== false); }
function hasTerminal(entries, summary) {
  if (summary?.terminalCallbackReceived) return true;
  return entries.some((entry) => ['TASK_RESULT', 'RESULT', 'TASK_ERROR', 'ERROR', 'AI_TASK_RESULT', 'AI_TASK_ERROR'].includes(callbackType(entry)) && entry.accepted !== false);
}
async function setupAgent() {
  if (skipSetup) return;
  await request('setup target Agent', 'POST', '/admin/agents/setup', {
    tenantId,
    agentId,
    agentName: 'Stage 23 Delivery Confirmation Agent',
    ownerTeam: 'stage23-e2e',
    description: 'Created or refreshed by Stage 23 real runtime delivery confirmation gate.',
    purpose: 'MES_OPERATIONS_ANALYZER',
    runtimeType: 'Docker',
    gatewayUrl,
    credentialToken,
    autoApprove: true,
    createDefaultCapabilities: true,
    createRuntimeBinding: true,
    createSupplyProfile: true,
    createDefaultDispatchRule: true,
    defaultCapabilities: effectiveCapabilities,
    defaultTaskTypes,
    capacityLimit: 2,
    operatorId,
    metadata: { stage: '23', gate: 'dispatch-delivery-confirmation' },
  });
}
async function assertReadinessReady() {
  const readiness = await request('target Agent setup-readiness', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
  if (readiness.ready !== true || readiness.status !== 'READY') {
    throw new Error(`Agent is not READY. Start the real runtime first, then retry. status=${readiness.status} blocking=${(readiness.blockingReasons || []).join(',')}`);
  }
  if (!arrayIncludes(readiness.runtimeReportedCapabilities, effectiveCapabilities)) throw new Error(`runtimeReportedCapabilities missing MES abilities: ${(readiness.runtimeReportedCapabilities || []).join(',')}`);
  return readiness;
}
async function previewReadiness() {
  const result = await request('dispatch readiness preview', 'POST', '/admin/dispatch-readiness/evaluate', {
    agentId,
    taskId: `stage23-preview-${Date.now()}`,
    taskType: 'INCIDENT_RESPONSE',
    domain: 'MES',
    provider: 'MES',
    operation: 'ANALYZE',
    requiredCapabilities: dispatchRequiredCapabilities,
    siteCode: 'MES_SITE_A',
    plantId: 'FAB_A',
    objectType: 'MES_LOT',
    eventType: 'MES_LOT_TRACE',
  });
  if (result.ready !== true) throw new Error(`dispatch-readiness preview is not ready: ${result.summary || JSON.stringify(result).slice(0, 800)}`);
  if (!arrayIncludes(result.effectiveDispatchCapabilities, effectiveCapabilities)) throw new Error(`effectiveDispatchCapabilities missing MES abilities: ${(result.effectiveDispatchCapabilities || []).join(',')}`);
  return result;
}
async function createTaskAndDispatch() {
  const response = await request('event intake creates MES dispatch task', 'POST', '/api/events/intake', {
    tenantId,
    sourceSystem: 'MES',
    siteId: 'MES_SITE_A',
    plantId: 'FAB_A',
    objectType: 'MES_LOT',
    objectId: `LOT-STAGE23-${Date.now()}`,
    eventType: 'MES_LOT_TRACE',
    errorCode: 'MES_LOT_TRACE_REQUIRED',
    severity: 'CRITICAL',
    message: `Stage 23 real runtime delivery confirmation test for ${agentId}`,
    attributes: {
      requiredCapabilities: dispatchRequiredCapabilities,
      routingPolicy: 'LOCAL_FIRST',
      domain: 'MES',
      provider: 'MES',
      operation: 'ANALYZE',
      stage: '23',
      targetAgentId: agentId,
    },
  });
  for (const key of ['taskId', 'assignmentId', 'dispatchRequestId']) if (!response[key]) throw new Error(`event intake missing ${key}: ${JSON.stringify(response).slice(0, 1000)}`);
  if (response.selectedAgentId !== agentId) throw new Error(`selectedAgentId mismatch: expected=${agentId} got=${response.selectedAgentId}`);
  if (response.dispatchEligibilityStatus !== 'ELIGIBLE') throw new Error(`dispatchEligibilityStatus=${response.dispatchEligibilityStatus}; reason=${response.dispatchReason}`);
  return response;
}
async function ensureDispatchExecuted(dispatchRequestId) {
  let dispatch = await request('read dispatch request', 'GET', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}`);
  if (!skipApprove && status(dispatch.status) === 'PENDING_REVIEW') {
    dispatch = await request('approve dispatch request', 'POST', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}/approve`, { reason: 'Stage 23 delivery confirmation approves dispatch request.' });
  }
  if (!skipExecute && !['DISPATCHED', 'COMPLETED'].includes(status(dispatch.status))) {
    await request('execute dispatch request', 'POST', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}/execute`);
  }
  return waitFor('dispatch gateway delivery', () => request('poll dispatch request', 'GET', `/api/dispatch-requests/${encodeURIComponent(dispatchRequestId)}`), (value) => ['DISPATCHED', 'COMPLETED'].includes(status(value.status)) || Boolean(value.dispatchedAt));
}
async function waitForCallbackInbox(taskId) {
  const entries = await waitFor('agent ACK callback', () => request('poll task callback inbox', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox?limit=100`), (value) => Array.isArray(value) && hasAck(value));
  const terminalEntries = await waitFor('agent RESULT callback', () => request('poll task callback inbox', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox?limit=100`), async (value) => {
    if (!Array.isArray(value)) return false;
    const summary = await request('task callback inbox summary', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox/summary?limit=100`);
    return hasTerminal(value, summary);
  });
  const summary = await request('task callback inbox summary final', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/callback-inbox/summary?limit=100`);
  return { entries: terminalEntries.length ? terminalEntries : entries, summary };
}
async function waitForTaskCompleted(taskId) {
  return waitFor('task terminal completion', () => request('poll task runtime view', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/runtime-view`), (value) => ['COMPLETED', 'SUCCEEDED', 'SUCCESS', 'FAILED'].includes(status(value.status)) || ['COMPLETED', 'FAILED'].includes(status(value.callbackStatus)));
}
async function maybeRequireIssueLink(taskId) {
  const task = await request('read final task runtime view', 'GET', `/admin/tasks/${encodeURIComponent(taskId)}/runtime-view`);
  const issue = task.issueTracking || {};
  if (requireIssueLink && !issue.issueUrl && !issue.issueId) throw new Error(`issue link required but not present for task=${taskId}`);
  return { task, issue };
}
async function main() {
  console.log(`[stage23-delivery] Core=${coreOrigin}`);
  console.log(`[stage23-delivery] Target Agent=${agentId}`);
  if (dryRun) {
    console.log('[stage23-delivery] Dry run only. Start a real runtime with AGENT_WORKER_MODE=process-result before running without --dry-run.');
    return;
  }
  await setupAgent();
  const readiness = await assertReadinessReady();
  console.log(`[stage23-delivery] readiness=${readiness.status} runtime=${(readiness.runtimeReportedCapabilities || []).join(',')}`);
  const preview = await previewReadiness();
  console.log(`[stage23-delivery] effective=${(preview.effectiveDispatchCapabilities || []).join(',')}`);
  const intake = await createTaskAndDispatch();
  console.log(`[stage23-delivery] task=${intake.taskId} assignment=${intake.assignmentId} dispatch=${intake.dispatchRequestId}`);
  const dispatch = await ensureDispatchExecuted(intake.dispatchRequestId);
  console.log(`[stage23-delivery] dispatch status=${dispatch.status} dispatchedAt=${dispatch.dispatchedAt || '-'}`);
  const callback = await waitForCallbackInbox(intake.taskId);
  console.log(`[stage23-delivery] callbacks=${callback.entries.length} terminal=${callback.summary?.terminalCallbackReceived}`);
  const finalTask = await waitForTaskCompleted(intake.taskId);
  const { issue } = await maybeRequireIssueLink(intake.taskId);
  console.log(`[stage23-delivery] task status=${finalTask.status} callbackStatus=${finalTask.callbackStatus || '-'} issue=${issue.issueUrl || issue.issueId || '-'}`);
  console.log('OK Stage 23 real runtime dispatch delivery confirmation completed.');
}

main().catch((error) => {
  console.error(`[stage23-delivery] FAILED: ${error instanceof Error ? error.stack || error.message : String(error)}`);
  process.exit(1);
});
