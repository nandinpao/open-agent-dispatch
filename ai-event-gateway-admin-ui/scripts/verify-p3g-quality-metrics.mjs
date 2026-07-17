import { existsSync, readFileSync } from 'node:fs';

const checks = [
  ['migration', '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V72__p3g_quality_metrics_model.sql', ['agent_quality_metrics_daily', 'agent_quality_metrics_window', 'runtime_quality_metrics_daily', 'supply_profile_quality_snapshot']],
  ['domain agent daily', '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentQualityMetricsDaily.java', ['successRate', 'qualityGrade', 'score']],
  ['domain window', '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentQualityMetricsWindow.java', ['metricWindow', 'windowStart', 'windowEnd']],
  ['domain snapshot', '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/SupplyProfileQualitySnapshot.java', ['snapshotId', 'profileCode']],
  ['repository', '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentRepository.java', ['saveAgentQualityMetricsDaily', 'findAgentQualityMetricsWindow', 'saveSupplyProfileQualitySnapshot']],
  ['controller', '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java', ['/quality/agents/{agentId}/windows', '/quality/supply-profiles', '/quality-snapshot']],
  ['ui type', 'lib/types/core.ts', ['CoreAgentQualityMetricsWindow', 'CoreSupplyProfileQualitySnapshot']],
  ['api client', 'lib/api/coreAdminApi.ts', ['getAgentQualityWindows', 'getSupplyProfileQualitySnapshots', 'getSupplyProfileQualitySnapshot']],
  ['quality panel', 'components/quality/QualityMetricsSummaryPanel.tsx', ['P3-G Quality Model', 'getSupplyProfileQualitySnapshots']],
  ['quality settings page', 'app/settings/quality-metrics/page.tsx', ['Quality / Rating Metrics']],
  ['docs', 'docs/P3_G_QUALITY_METRICS_MODEL.md', ['P3-G Quality Metrics Model']],
];

const root = process.cwd();
const failures = [];
for (const [name, rel, patterns] of checks) {
  const file = new URL(rel, `file://${root}/`);
  const path = file.pathname;
  if (!existsSync(path)) {
    failures.push(`${name}: missing ${rel}`);
    continue;
  }
  const text = readFileSync(path, 'utf8');
  for (const pattern of patterns) {
    if (!text.includes(pattern)) failures.push(`${name}: missing ${pattern}`);
  }
}

if (failures.length) {
  console.error('[verify:p3g-quality-metrics] FAILED');
  for (const failure of failures) console.error(` - ${failure}`);
  process.exit(1);
}
console.log('[verify:p3g-quality-metrics] OK');
