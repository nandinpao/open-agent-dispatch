import fs from 'node:fs';

const requiredFiles = [
  'lib/fixtures/p3pProductionEnforceObservabilityFixture.ts',
  'lib/eligibility/enforceObservability.ts',
  'components/enforce-observability/ProductionEnforceObservabilityPanel.tsx',
  'app/settings/enforce-observability/page.tsx',
  'scripts/p3p-post-cutover-observability-export.mjs',
  'docs/P3_P_PRODUCTION_ENFORCE_OBSERVABILITY.md',
  '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V75__p3p_enforce_observability_post_cutover.sql',
  '../scripts/verify/verify-p3p-production-observability.py',
  '../docs/CURRENT_DISPATCH_DOMAIN_MODEL.md',
];
for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const fixture = fs.readFileSync('lib/fixtures/p3pProductionEnforceObservabilityFixture.ts', 'utf8');
for (const token of [
  'P3P_PRODUCTION_ENFORCE_OBSERVABILITY',
  'opendispatch_enforce_v2_allowed_total',
  'opendispatch_enforce_v2_blocked_total',
  'opendispatch_enforce_no_candidate_total',
  'opendispatch_enforce_legacy_fallback_denied_total',
  'P3P_BLOCKED_RATE_HIGH',
  'P3P_NO_CANDIDATE_RATE_HIGH',
  'P3P_QUALITY_UNAVAILABLE_RATE_HIGH',
  'P3P_SCORE_BREAKDOWN_MISSING_NONZERO',
  'routingAuditSearch',
  'operatorIncidentWorkflow',
  'legacyFinalReport',
  'artifactRetention',
]) {
  if (!fixture.includes(token)) throw new Error(`P3-P fixture missing token: ${token}`);
}

const panel = fs.readFileSync('components/enforce-observability/ProductionEnforceObservabilityPanel.tsx', 'utf8');
for (const token of ['Production ENFORCE observability', 'Alert rules', 'Routing decision audit search', 'Operator incident workflow', 'Rollback criteria', 'Legacy cleanup final report', 'Release artifact retention']) {
  if (!panel.includes(token)) throw new Error(`P3-P panel missing token: ${token}`);
}

const api = fs.readFileSync('lib/api/coreAdminApi.ts', 'utf8');
for (const token of ['getEnforceObservabilitySnapshot', 'searchEnforceRoutingAudit', 'createEnforceOperatorIncident', 'getEnforceLegacyFinalReport', 'getEnforceArtifactRetention']) {
  if (!api.includes(token)) throw new Error(`coreAdminApi missing P3-P method: ${token}`);
}

const endpoints = fs.readFileSync('lib/api/endpoints.ts', 'utf8');
for (const token of ['/admin/enforce/observability', '/admin/enforce/routing-audit', '/admin/enforce/incidents', '/admin/enforce/legacy-final-report', '/admin/enforce/artifact-retention']) {
  if (!endpoints.includes(token)) throw new Error(`endpoints missing P3-P endpoint: ${token}`);
}

const migration = fs.readFileSync('../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V75__p3p_enforce_observability_post_cutover.sql', 'utf8');
for (const token of ['p3p_enforce_observability_snapshot', 'p3p_routing_decision_audit_search', 'p3p_legacy_final_report', 'p3p_release_artifact_retention_policy']) {
  if (!migration.includes(token)) throw new Error(`P3-P migration missing token: ${token}`);
}

const nav = fs.readFileSync('lib/navigation/adminInformationArchitecture.ts', 'utf8');
if (!nav.includes('/settings/enforce-observability')) throw new Error('Navigation missing ENFORCE Observability link.');

const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
for (const script of ['verify:p3p-observability', 'export:p3p-observability']) {
  if (!pkg.scripts?.[script]) throw new Error(`package.json missing script: ${script}`);
}
if (!pkg.scripts?.ci?.includes('verify:p3p-observability')) throw new Error('ci script must include verify:p3p-observability after P3-P.');

console.log('OK P3-P production ENFORCE observability, alert rules, audit search, incident workflow, rollback criteria, legacy final report, and artifact retention wiring are present.');
