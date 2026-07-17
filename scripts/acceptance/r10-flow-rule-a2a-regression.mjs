#!/usr/bin/env node
/**
 * R10 Flow-owned routing + A2A regression acceptance gate.
 *
 * Live mode requires a running Core service and pre-created R10 Flow-owned ERP/MES
 * test data. Use --dry-run to print the exact request chain without calling Core.
 */

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const tenantId = process.env.R10_TENANT_ID || 'tenant-a';
const flowId = process.env.R10_FLOW_ID || '00000000-0000-0000-0000-000000001010';
const policyCode = process.env.R10_LEGACY_POLICY_CODE || 'R10_STANDALONE_WRITE_BLOCKED_POLICY';
const correlationId = process.env.R10_CORRELATION_ID || `r10-case-${Date.now()}`;
const dryRun = hasArg('--dry-run') || flag('R10_DRY_RUN');
const skipIssue = hasArg('--skip-issue') || flag('R10_SKIP_ISSUE');
const skipStandaloneWriteCheck = hasArg('--skip-standalone-write-check') || flag('R10_SKIP_STANDALONE_WRITE_CHECK');

function hasArg(name) { return process.argv.includes(name); }
function flag(name) { return ['1', 'true', 'yes', 'on'].includes(String(process.env[name] || '').toLowerCase()); }
function origin(value) { return String(value || '').replace(/\/+$/, ''); }
function join(originValue, requestPath) { return `${originValue}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`; }
function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function normalize(value) { return String(value || '').trim().replace(/[.\-\s]+/g, '_').toUpperCase(); }
function isStandardEnvelope(value) { return isRecord(value) && typeof value.code === 'string' && Object.prototype.hasOwnProperty.call(value, 'data'); }
function unwrap(name, body, options = {}) {
  if (isStandardEnvelope(body)) {
    if (body.code !== 'OK' && !options.allowError) throw new Error(`${name}: envelope code=${body.code} message=${body.message}`);
    return body.data;
  }
  return body;
}
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
  if (!options.allowHttpError && response.status < 200 || (!options.allowHttpError && response.status >= 300)) {
    throw new Error(`${name}: HTTP ${response.status}; body=${text.slice(0, 1000)}`);
  }
  const data = unwrap(name, parsed, options);
  return { data, status: response.status, body: parsed };
}
function requireFormalEvidence(label, value) {
  const data = value?.data || value || {};
  const missing = [];
  if (!data.matchedFlowId && !data.matched_flow_id) missing.push('matchedFlowId');
  if (!data.matchedRuleId && !data.matched_rule_id) missing.push('matchedRuleId');
  if (!data.requestedSkill && !data.requested_skill) missing.push('requestedSkill');
  const routingPath = data.routingPath || data.routing_path;
  if (routingPath && normalize(routingPath) !== 'FLOW_RULE') missing.push(`routingPath=FLOW_RULE actual=${routingPath}`);
  if (missing.length > 0) throw new Error(`${label}: missing formal Flow Rule evidence: ${missing.join(', ')}; response=${JSON.stringify(data).slice(0, 1200)}`);
}
function requireNoLegacy(label, value) {
  const data = value?.data || value || {};
  const text = JSON.stringify(data);
  for (const forbidden of ['LEGACY_ROUTING', 'CAPABILITY_FIRST', 'PROFILE_FIRST']) {
    if (text.includes(forbidden)) throw new Error(`${label}: forbidden legacy routing evidence ${forbidden}; response=${text.slice(0, 1200)}`);
  }
}
function extractTaskId(value) {
  const data = value?.data || value || {};
  return data.taskId || data.task_id || data.id || null;
}
async function validateStandaloneWriteBlocked() {
  if (skipStandaloneWriteCheck) return;
  const result = await request('standalone Dispatch Rules write should be blocked', 'PUT', `/admin/dispatch-policies/${encodeURIComponent(policyCode)}`, {
    policyCode,
    description: 'R10 verifies standalone Dispatch Rules writes are blocked.',
  }, { allowHttpError: true });
  if (dryRun) return;
  const bodyText = JSON.stringify(result.body || result.data || {});
  if (result.status >= 200 && result.status < 300 && !bodyText.includes('R9_STANDALONE_DISPATCH_RULE_CRUD_DISABLED')) {
    throw new Error(`standalone Dispatch Rules write unexpectedly succeeded; status=${result.status} body=${bodyText.slice(0, 1000)}`);
  }
  if (!bodyText.includes('R9_STANDALONE_DISPATCH_RULE_CRUD_DISABLED')) {
    throw new Error(`standalone Dispatch Rules write did not return R9 block code; status=${result.status} body=${bodyText.slice(0, 1000)}`);
  }
}
async function main() {
  const externalEvent = {
    tenantId,
    eventStage: 'EXTERNAL',
    sourceSystem: 'ERP',
    originSourceSystem: 'ERP',
    eventType: 'PAYMENT_BLOCKED_BY_RISK_RULE',
    requestedSkill: 'ERP_PAYMENT_RISK_ANALYSIS',
    correlationId,
    message: 'R10 ERP payment risk regression: payment is blocked and may depend on MES work order status.',
    attributes: {
      paymentNo: `PAY-R10-${Date.now()}`,
      vendorId: 'VENDOR-R10-001',
      workOrderId: 'WO-R10-001',
      plantId: 'LOCAL-FAB-01',
      r10: true,
    },
  };
  const external = await request('ERP EXTERNAL intake', 'POST', '/api/events/intake', externalEvent, {
    dryRunResponse: { matchedFlowId: flowId, matchedRuleId: 'r10-external-rule', requestedSkill: externalEvent.requestedSkill, routingPath: 'FLOW_RULE', taskId: 'dry-run-erp-task' },
  });
  requireFormalEvidence('ERP EXTERNAL intake', external);
  requireNoLegacy('ERP EXTERNAL intake', external);
  const parentTaskId = extractTaskId(external) || process.env.R10_PARENT_TASK_ID || 'dry-run-erp-task';

  const a2aEvent = {
    tenantId,
    eventStage: 'A2A',
    sourceSystem: 'ERP_AGENT',
    originSourceSystem: 'ERP',
    targetSystem: 'MES',
    eventType: 'MES_WORK_ORDER_INVESTIGATION_REQUESTED',
    requestedSkill: 'MES_WORK_ORDER_TRACE',
    handoffMode: 'CONSULT',
    correlationId,
    parentTaskId,
    message: 'R10 A2A regression: ERP Agent asks MES Agent to verify work order state.',
    attributes: {
      workOrderId: 'WO-R10-001',
      plantId: 'LOCAL-FAB-01',
      r10: true,
    },
  };
  const a2a = await request('ERP to MES A2A intake2', 'POST', '/api/events/intake', a2aEvent, {
    dryRunResponse: { matchedFlowId: flowId, matchedRuleId: 'r10-a2a-rule', requestedSkill: a2aEvent.requestedSkill, routingPath: 'FLOW_RULE', taskId: 'dry-run-mes-task', parentTaskId },
  });
  requireFormalEvidence('ERP to MES A2A intake2', a2a);
  requireNoLegacy('ERP to MES A2A intake2', a2a);

  const resultEvent = {
    tenantId,
    eventStage: 'RESULT',
    sourceSystem: 'MES_AGENT',
    originSourceSystem: 'ERP',
    targetSystem: 'ERP_AGENT',
    eventType: 'MES_WORK_ORDER_INVESTIGATION_COMPLETED',
    requestedSkill: 'MES_WORK_ORDER_TRACE',
    correlationId,
    parentTaskId,
    message: 'R10 RESULT regression: MES confirms work order investigation is complete.',
    attributes: {
      workOrderId: 'WO-R10-001',
      result: 'ROUTING_STEP_DELAY_FOUND',
      r10: true,
    },
  };
  const result = await request('MES RESULT callback', 'POST', '/api/events/intake', resultEvent, {
    dryRunResponse: { matchedFlowId: flowId, matchedRuleId: 'r10-result-rule', requestedSkill: resultEvent.requestedSkill, routingPath: 'FLOW_RULE', parentTaskId, correlationId },
  });
  requireFormalEvidence('MES RESULT callback', result);

  await request('Dispatch Flow chain trace', 'POST', `/admin/dispatch-flows/${encodeURIComponent(flowId)}/test-chain`, {
    tenantId,
    correlationId,
    parentTaskId,
    externalEvent,
    a2aEvent,
    resultEvent,
    requireIssueEvidence: !skipIssue,
  }, {
    dryRunResponse: { flowId, correlationId, steps: [{ eventStage: 'EXTERNAL' }, { eventStage: 'A2A' }, { eventStage: 'RESULT' }], status: 'R10_DRY_RUN_CHAIN_ACCEPTED' },
  });

  await validateStandaloneWriteBlocked();

  console.log('R10 Flow-owned routing + A2A regression acceptance completed.');
}

main().catch((error) => {
  console.error(error?.stack || error?.message || error);
  process.exit(1);
});
