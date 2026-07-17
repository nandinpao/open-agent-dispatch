import fs from 'node:fs';
import path from 'node:path';

const output = process.env.P3P_OBSERVABILITY_OUTPUT ?? '../.ci-output/reports/p3p-post-cutover-observability.json';
const mode = process.env.P3P_OBSERVABILITY_MODE ?? 'fixture';
const generatedAt = new Date().toISOString();
const snapshot = {
  generatedAt,
  source: mode,
  mode: 'ENFORCE',
  window: process.env.P3P_OBSERVABILITY_WINDOW ?? '24h',
  v2Allowed: Number(process.env.P3P_V2_ALLOWED ?? 128),
  v2Blocked: Number(process.env.P3P_V2_BLOCKED ?? 17),
  noCandidate: Number(process.env.P3P_NO_CANDIDATE ?? 2),
  fallbackDenied: Number(process.env.P3P_FALLBACK_DENIED ?? 0),
  qualityUnavailable: Number(process.env.P3P_QUALITY_UNAVAILABLE ?? 1),
  scoreBreakdownMissing: Number(process.env.P3P_SCORE_BREAKDOWN_MISSING ?? 0),
};
const total = Math.max(1, snapshot.v2Allowed + snapshot.v2Blocked + snapshot.noCandidate);
snapshot.blockedRate = snapshot.v2Blocked / total;
snapshot.noCandidateRate = snapshot.noCandidate / total;
snapshot.qualityUnavailableRate = snapshot.qualityUnavailable / total;
snapshot.latestAcceptanceArtifact = 'p3n-enforce-runtime-acceptance.json';
snapshot.latestReadinessArtifact = 'p3n-readiness-report.json';
snapshot.latestArchiveManifest = 'p3o-release-artifact-manifest.json';
snapshot.readinessBlockingCount = Number(process.env.P3P_READINESS_BLOCKING_COUNT ?? 0);

const alertRules = [
  ['P3P_BLOCKED_RATE_HIGH', 'blockedRate', 'HIGH', 0.35],
  ['P3P_NO_CANDIDATE_RATE_HIGH', 'noCandidateRate', 'CRITICAL', 0.1],
  ['P3P_QUALITY_UNAVAILABLE_RATE_HIGH', 'qualityUnavailableRate', 'HIGH', 0.05],
  ['P3P_FALLBACK_DENIED_NONZERO', 'fallbackDenied', 'MIDDLE', 0],
  ['P3P_SCORE_BREAKDOWN_MISSING_NONZERO', 'scoreBreakdownMissing', 'CRITICAL', 0],
];
const alerts = alertRules.map(([code, metric, severity, threshold]) => {
  const observed = Number(snapshot[metric] ?? 0);
  const triggered = metric.endsWith('Rate') ? observed >= Number(threshold) : observed > Number(threshold);
  return { code, metric, severity, observed, threshold, triggered };
});

const report = {
  id: 'P3P_PRODUCTION_ENFORCE_OBSERVABILITY',
  generatedAt,
  snapshot,
  alerts,
  rollbackRecommended: alerts.some((alert) => alert.triggered && ['CRITICAL', 'HIGH'].includes(alert.severity)),
  routingAuditSearch: {
    endpoint: '/admin/enforce/routing-audit',
    requiredScoreKeys: ['eligibilityEngineMode', 'eligibilityV2Applied', 'eligibilityV2BlockingReasons', 'eligibilityV2ScoreBreakdown'],
  },
  operatorIncidentWorkflow: {
    endpoint: '/admin/enforce/incidents',
    template: 'ENFORCE_POST_CUTOVER_BLOCKING_INCIDENT',
  },
  legacyFinalReport: {
    endpoint: '/admin/enforce/legacy-final-report',
    views: ['p3o_legacy_readonly_report', 'p3p_legacy_final_report'],
  },
  artifactRetention: {
    retentionDays: 180,
    requiredArtifacts: ['p3n-full-enforce-summary.json', 'p3o-release-artifact-manifest.json', 'p3p-post-cutover-observability.json'],
  },
};

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, JSON.stringify(report, null, 2));
console.log(`OK P3-P post-cutover observability export written: ${output}`);
