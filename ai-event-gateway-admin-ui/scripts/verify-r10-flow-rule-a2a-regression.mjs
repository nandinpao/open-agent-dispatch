#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = path.resolve(process.cwd(), '..');
function read(rel) {
  const p = path.join(repoRoot, rel);
  if (!fs.existsSync(p)) throw new Error(`Missing ${rel}`);
  return fs.readFileSync(p, 'utf8');
}
function requireIncludes(label, text, needles) {
  for (const needle of needles) {
    if (!text.includes(needle)) throw new Error(`${label} missing ${needle}`);
  }
}

const migration = read('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V103__r10_full_workflow_regression_acceptance.sql');
requireIncludes('R10 migration', migration, [
  'dispatch_r10_formal_success_acceptance_report',
  'R10_FORMAL_ROUTING_ACCEPTED',
  'dispatch_r10_a2a_chain_acceptance_report',
  'R10_A2A_CHAIN_ACCEPTED',
  'dispatch_r10_legacy_cleanup_blockers',
  'LEGACY_FALLBACK_NOT_FORMAL_SUCCESS',
]);

const acceptance = read('scripts/acceptance/r10-flow-rule-a2a-regression.mjs');
requireIncludes('R10 acceptance script', acceptance, [
  'ERP EXTERNAL intake',
  'ERP to MES A2A intake2',
  'MES RESULT callback',
  'R9_STANDALONE_DISPATCH_RULE_CRUD_DISABLED',
  'requireFormalEvidence',
  'requireNoLegacy',
  '/admin/dispatch-flows/',
  '/api/events/intake',
  'routingPath: \'FLOW_RULE\'',
]);

const docs = [
  'docs/R10_FULL_WORKFLOW_REGRESSION/README.md',
  'docs/R10_FULL_WORKFLOW_REGRESSION/r10_acceptance_checklist.md',
  'docs/R10_FULL_WORKFLOW_REGRESSION/r10_regression_matrix.md',
  'docs/R10_FULL_WORKFLOW_REGRESSION/r10_to_schema_cleanup_handoff.md',
  'docs/R10_FULL_WORKFLOW_REGRESSION/r10_acceptance_report_template.md',
  'ai-event-gateway-admin-ui/docs/R10_FULL_WORKFLOW_REGRESSION.md',
];
for (const rel of docs) {
  const content = read(rel);
  requireIncludes(rel, content, ['R10']);
}

const domainModel = read('docs/CURRENT_DISPATCH_DOMAIN_MODEL.md');
requireIncludes('CURRENT_DISPATCH_DOMAIN_MODEL', domainModel, [
  'R10 — Full workflow regression acceptance',
  'dispatch_r10_legacy_cleanup_blockers',
  'schema archive/drop cleanup',
]);

const pkg = read('ai-event-gateway-admin-ui/package.json');
requireIncludes('package.json', pkg, ['verify:r10-flow-rule-a2a-regression']);

const makefile = read('Makefile');
requireIncludes('Makefile', makefile, [
  'verify-r10-flow-rule-a2a-regression',
  'acceptance-r10-flow-rule-a2a-regression',
]);

console.log('OK R10 full workflow regression reports, acceptance script, docs, and verification gate are present.');
