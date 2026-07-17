import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const mode = (process.env.P3N_RUNTIME_ACCEPTANCE_MODE || process.env.P3M_RUNTIME_ACCEPTANCE_MODE || 'fixture').toLowerCase();
const outputDir = path.resolve(process.env.P3N_ACCEPTANCE_OUTPUT_DIR || '../.ci-output/reports/p3n-full-enforce');
const cleanup = process.env.P3N_CLEANUP !== 'false';
const checkRouting = process.env.P3N_CHECK_ROUTING_DECISION || process.env.P3M_CHECK_ROUTING_DECISION || 'false';
const coreBaseUrl = process.env.P3N_CORE_BASE_URL || process.env.P3M_CORE_BASE_URL || 'http://127.0.0.1:18080';
const adminBaseUrl = process.env.P3N_ADMIN_BASE_URL || process.env.P3M_ADMIN_BASE_URL || 'http://127.0.0.1:3000';

function runNode(script, env = {}) {
  const result = spawnSync(process.execPath, [script], {
    cwd: process.cwd(),
    stdio: 'inherit',
    env: { ...process.env, ...env },
  });
  return { script, status: result.status ?? 1, signal: result.signal ?? null };
}

function readJson(file, fallback = null) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; }
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function ensureAcceptanceReportShape(report) {
  const requiredCodes = [
    'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
    'P3H_REQUIRED_CAPABILITY_MISSING',
    'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
    'P3H_QUALITY_RULE_FAILED',
  ];
  const blockedCodes = new Set(report?.summary?.blockedCodes ?? []);
  const missing = requiredCodes.filter((code) => !blockedCodes.has(code));
  return { requiredCodes, missing, passed: missing.length === 0 };
}

function buildReadinessReport(acceptanceReport, seedReport) {
  const acceptanceShape = ensureAcceptanceReportShape(acceptanceReport);
  const blocking = [];
  const warnings = [];
  if (!acceptanceShape.passed) blocking.push(`Missing blocked codes: ${acceptanceShape.missing.join(', ')}`);
  if (seedReport?.summary?.warn) warnings.push(`Fixture bootstrap produced ${seedReport.summary.warn} warnings. Review p3n-seed-report.json before cutover.`);
  return {
    generatedAt: new Date().toISOString(),
    source: 'P3-N full ENFORCE acceptance automation',
    enforceReady: blocking.length === 0,
    routingEligibilityEngineModeTarget: 'ENFORCE',
    summary: {
      blocking: blocking.length,
      warnings: warnings.length,
      fixtureMode: mode,
      cleanupRequested: cleanup,
    },
    checks: [
      { id: 'seed-contract', status: seedReport ? 'PASS' : 'FAIL', message: seedReport ? 'Seed report exists.' : 'Seed report missing.' },
      { id: 'acceptance-contract', status: acceptanceShape.passed ? 'PASS' : 'FAIL', message: acceptanceShape.passed ? 'P3-M acceptance contains required blocked codes.' : acceptanceShape.missing.join(', ') },
      { id: 'routing-scorebreakdown', status: checkRouting === 'true' ? 'REQUIRED' : 'OPTIONAL', message: 'Set P3N_CHECK_ROUTING_DECISION=true for live routing scoreBreakdown assertion.' },
      { id: 'rollback-rehearsal', status: 'PASS', message: 'Rollback rehearsal artifact generated.' },
    ],
    blocking,
    warnings,
    repairChecklist: blocking.map((message) => ({ message, link: '/settings/migration-readiness' })),
  };
}

function buildRollbackReport() {
  return {
    generatedAt: new Date().toISOString(),
    previousMode: 'ENFORCE',
    targetMode: 'WARN',
    rollbackOwner: process.env.P3N_ROLLBACK_OWNER || 'release-owner-required',
    rollbackWindow: process.env.P3N_ROLLBACK_WINDOW || 'define-before-cutover',
    operatorChecklist: [
      'Set ROUTING_ELIGIBILITY_ENGINE_MODE=WARN.',
      'Restart Core / routing workers.',
      'Re-run P3-N full ENFORCE acceptance in fixture mode and live mode when stack is available.',
      'Export Migration Readiness report after rollback.',
      'Repair missing runtime binding / capability / trust / quality data before returning to ENFORCE.',
    ],
  };
}

fs.mkdirSync(outputDir, { recursive: true });

const seedResult = runNode('scripts/p3n-enforce-fixture-bootstrap.mjs', {
  P3N_FIXTURE_ACTION: 'seed',
  P3N_FIXTURE_MODE: mode,
  P3N_CORE_BASE_URL: coreBaseUrl,
  P3N_ACCEPTANCE_OUTPUT_DIR: outputDir,
});
if (seedResult.status !== 0) process.exit(seedResult.status);

const p3mOutput = path.join(outputDir, 'p3n-enforce-runtime-acceptance.json');
const acceptanceResult = runNode('scripts/p3m-enforce-runtime-acceptance.mjs', {
  P3M_RUNTIME_ACCEPTANCE_MODE: mode,
  P3M_CORE_BASE_URL: coreBaseUrl,
  P3M_ADMIN_BASE_URL: adminBaseUrl,
  P3M_ACCEPTANCE_OUTPUT: p3mOutput,
  P3M_CHECK_ROUTING_DECISION: checkRouting,
});
if (acceptanceResult.status !== 0) process.exit(acceptanceResult.status);

const seedReport = readJson(path.join(outputDir, 'p3n-seed-report.json'));
const acceptanceReport = readJson(p3mOutput);
const readinessReport = buildReadinessReport(acceptanceReport, seedReport);
writeJson(path.join(outputDir, 'p3n-readiness-report.json'), readinessReport);
writeJson(path.join(outputDir, 'p3n-rollback-rehearsal.json'), buildRollbackReport());

let teardownReport = null;
let teardownResult = { status: 0, script: 'scripts/p3n-enforce-fixture-bootstrap.mjs' };
if (cleanup) {
  teardownResult = runNode('scripts/p3n-enforce-fixture-bootstrap.mjs', {
    P3N_FIXTURE_ACTION: 'teardown',
    P3N_FIXTURE_MODE: mode,
    P3N_CORE_BASE_URL: coreBaseUrl,
    P3N_ACCEPTANCE_OUTPUT_DIR: outputDir,
  });
  teardownReport = readJson(path.join(outputDir, 'p3n-teardown-report.json'));
  if (teardownResult.status !== 0) process.exit(teardownResult.status);
}

const summary = {
  generatedAt: new Date().toISOString(),
  mode,
  outputDir,
  enforceReady: readinessReport.enforceReady,
  acceptance: {
    report: p3mOutput,
    summary: acceptanceReport?.summary ?? null,
  },
  artifacts: {
    seedReport: path.join(outputDir, 'p3n-seed-report.json'),
    acceptanceReport: p3mOutput,
    readinessReport: path.join(outputDir, 'p3n-readiness-report.json'),
    rollbackReport: path.join(outputDir, 'p3n-rollback-rehearsal.json'),
    teardownReport: cleanup ? path.join(outputDir, 'p3n-teardown-report.json') : null,
  },
  steps: [
    { name: 'seed', status: seedResult.status === 0 ? 'PASS' : 'FAIL' },
    { name: 'p3m-runtime-acceptance', status: acceptanceResult.status === 0 ? 'PASS' : 'FAIL' },
    { name: 'readiness-export', status: readinessReport.enforceReady ? 'PASS' : 'FAIL', blocking: readinessReport.blocking },
    { name: 'rollback-rehearsal', status: 'PASS' },
    { name: 'teardown', status: cleanup ? (teardownResult.status === 0 ? 'PASS' : 'FAIL') : 'SKIPPED', summary: teardownReport?.summary ?? null },
  ],
};
writeJson(path.join(outputDir, 'p3n-full-enforce-summary.json'), summary);

if (!readinessReport.enforceReady) {
  console.error(`[P3-N] ENFORCE readiness failed. See ${path.join(outputDir, 'p3n-readiness-report.json')}`);
  process.exit(1);
}

console.log(`OK P3-N full ENFORCE acceptance automation wrote ${path.join(outputDir, 'p3n-full-enforce-summary.json')}`);
