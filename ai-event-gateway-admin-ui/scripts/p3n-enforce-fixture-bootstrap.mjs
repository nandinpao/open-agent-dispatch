import fs from 'node:fs';
import path from 'node:path';

const action = (process.env.P3N_FIXTURE_ACTION || process.argv[2] || 'seed').toLowerCase();
const mode = (process.env.P3N_FIXTURE_MODE || process.env.P3N_RUNTIME_ACCEPTANCE_MODE || 'fixture').toLowerCase();
const coreBaseUrl = trimSlash(process.env.P3N_CORE_BASE_URL || process.env.P3M_CORE_BASE_URL || 'http://127.0.0.1:18080');
const outputDir = path.resolve(process.env.P3N_ACCEPTANCE_OUTPUT_DIR || '../.ci-output/reports/p3n-full-enforce');
const strict = process.env.P3N_STRICT_BOOTSTRAP === 'true';
const seedPath = path.resolve('../ai-event-gateway-core/control-plane-app/src/test/resources/fixtures/p3n-enforce-runtime-seed.json');

function trimSlash(value) {
  return String(value).replace(/\/+$/, '');
}

function writeReport(name, report) {
  fs.mkdirSync(outputDir, { recursive: true });
  const out = path.join(outputDir, name);
  fs.writeFileSync(out, `${JSON.stringify(report, null, 2)}\n`);
  return out;
}

function envelopeData(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload && ('code' in payload || 'message' in payload)) return payload.data;
  return payload;
}

async function callApi(method, endpoint, body, description, required = true) {
  const url = `${coreBaseUrl}${endpoint}`;
  const step = { description, method, endpoint, url, required, status: 'PENDING' };
  try {
    const response = await fetch(url, {
      method,
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let json = {};
    try { json = text ? JSON.parse(text) : {}; } catch { json = { raw: text.slice(0, 500) }; }
    step.httpStatus = response.status;
    step.envelopeCode = json?.code ?? null;
    step.responsePreview = JSON.stringify(envelopeData(json) ?? json).slice(0, 500);
    if (!response.ok && required && strict) throw new Error(`${method} ${endpoint} -> HTTP ${response.status}`);
    step.status = response.ok ? 'PASS' : required ? 'WARN' : 'SKIPPED';
  } catch (error) {
    step.status = required && strict ? 'FAIL' : 'WARN';
    step.error = error instanceof Error ? error.message : String(error);
    if (required && strict) throw error;
  }
  return step;
}

function seedBodyVariants(seed) {
  const calls = [];
  calls.push(['PUT', `/admin/dispatch-task-definitions/${encodeURIComponent(seed.taskDefinition.definitionId)}`, seed.taskDefinition, 'seed task definition']);
  for (const capability of seed.capabilities ?? []) {
    calls.push(['PUT', `/admin/capabilities/${encodeURIComponent(capability.capabilityCode)}`, capability, `seed capability ${capability.capabilityCode}`]);
  }
  for (const runtime of seed.runtimeResources ?? []) {
    calls.push(['PUT', `/admin/runtime-resources/${encodeURIComponent(runtime.runtimeId)}`, runtime, `seed runtime resource ${runtime.runtimeId}`]);
  }
  for (const binding of seed.runtimeBindings ?? []) {
    calls.push(['POST', `/admin/agents/${encodeURIComponent(binding.agentId)}/runtime-bindings`, binding, `seed runtime binding ${binding.bindingId}`]);
  }
  const policy = seed.dispatchPolicy;
  if (policy) {
    calls.push(['PUT', `/admin/dispatch-policies/${encodeURIComponent(policy.policyCode)}`, policy, `seed dispatch policy ${policy.policyCode}`]);
    for (const scope of policy.scopes ?? []) calls.push(['POST', `/admin/dispatch-policies/${encodeURIComponent(policy.policyCode)}/scopes`, scope, `seed policy scope ${policy.policyCode}`]);
    for (const capabilityCode of policy.requiredCapabilities ?? []) calls.push(['POST', `/admin/dispatch-policies/${encodeURIComponent(policy.policyCode)}/required-capabilities`, { capabilityCode, blocking: true }, `seed policy required capability ${capabilityCode}`]);
    for (const featureCode of policy.requiredRuntimeFeatures ?? []) calls.push(['POST', `/admin/dispatch-policies/${encodeURIComponent(policy.policyCode)}/required-runtime-features`, { featureCode, blocking: true }, `seed policy required runtime feature ${featureCode}`]);
    for (const qualityRule of policy.qualityRules ?? []) calls.push(['POST', `/admin/dispatch-policies/${encodeURIComponent(policy.policyCode)}/quality-rules`, qualityRule, `seed policy quality rule ${qualityRule.metricName}`]);
    for (const scoringRule of policy.scoringRules ?? []) calls.push(['POST', `/admin/dispatch-policies/${encodeURIComponent(policy.policyCode)}/scoring-rules`, scoringRule, `seed policy scoring rule ${scoringRule.factorName}`]);
  }
  for (const profile of seed.supplyProfiles ?? []) {
    calls.push(['PUT', `/admin/supply-profiles/${encodeURIComponent(profile.profileCode)}`, profile, `seed supply profile ${profile.profileCode}`]);
  }
  for (const snapshot of seed.qualitySnapshots ?? []) {
    calls.push(['POST', `/admin/supply-profiles/${encodeURIComponent(snapshot.profileCode)}/quality-snapshot`, snapshot, `seed quality snapshot ${snapshot.profileCode}`]);
  }
  return calls;
}

function teardownCalls(seed) {
  // Most P3 admin resources intentionally do not expose destructive endpoints.
  // Teardown therefore prefers state demotion / retirement where APIs exist and records
  // explicit manual cleanup evidence for release review.
  const calls = [];
  for (const profile of seed.supplyProfiles ?? []) {
    calls.push(['PUT', `/admin/supply-profiles/${encodeURIComponent(profile.profileCode)}`, { ...profile, status: 'RETIRED' }, `retire supply profile ${profile.profileCode}`, false]);
  }
  if (seed.dispatchPolicy) {
    calls.push(['PUT', `/admin/dispatch-policies/${encodeURIComponent(seed.dispatchPolicy.policyCode)}`, { ...seed.dispatchPolicy, status: 'RETIRED' }, `retire dispatch policy ${seed.dispatchPolicy.policyCode}`, false]);
  }
  for (const runtime of seed.runtimeResources ?? []) {
    calls.push(['PUT', `/admin/runtime-resources/${encodeURIComponent(runtime.runtimeId)}`, { ...runtime, status: 'RETIRED' }, `retire runtime resource ${runtime.runtimeId}`, false]);
  }
  return calls;
}

async function run() {
  const seed = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
  const steps = [];
  if (mode === 'live') {
    const calls = action === 'teardown' ? teardownCalls(seed) : seedBodyVariants(seed);
    for (const [method, endpoint, body, description, required = true] of calls) {
      steps.push(await callApi(method, endpoint, body, description, required));
    }
  } else {
    const planned = action === 'teardown' ? teardownCalls(seed) : seedBodyVariants(seed);
    for (const [method, endpoint, body, description, required = true] of planned) {
      steps.push({ description, method, endpoint, required, status: 'PLANNED', payloadPreview: JSON.stringify(body).slice(0, 300) });
    }
  }

  const report = {
    generatedAt: new Date().toISOString(),
    fixtureId: seed.id,
    action,
    mode,
    strict,
    coreBaseUrl: mode === 'live' ? coreBaseUrl : null,
    summary: {
      total: steps.length,
      pass: steps.filter((step) => step.status === 'PASS').length,
      planned: steps.filter((step) => step.status === 'PLANNED').length,
      warn: steps.filter((step) => step.status === 'WARN').length,
      fail: steps.filter((step) => step.status === 'FAIL').length,
    },
    seedContract: {
      taskId: seed.taskId,
      tenantId: seed.tenantId,
      expected: seed.expected,
    },
    steps,
  };
  const fileName = action === 'teardown' ? 'p3n-teardown-report.json' : 'p3n-seed-report.json';
  const out = writeReport(fileName, report);
  console.log(`OK P3-N ${action} ${mode} report wrote ${out}`);
}

await run();
