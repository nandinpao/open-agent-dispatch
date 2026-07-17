import { p3pProductionEnforceObservabilityFixture } from '@/lib/fixtures/p3pProductionEnforceObservabilityFixture';

export type EnforceAlertSeverity = 'CRITICAL' | 'HIGH' | 'MIDDLE' | 'LOW' | string;

export interface EnforceObservabilitySnapshot {
  generatedAt: string;
  source: string;
  mode: string;
  window: string;
  v2Allowed: number;
  v2Blocked: number;
  noCandidate: number;
  fallbackDenied: number;
  qualityUnavailable: number;
  scoreBreakdownMissing: number;
  blockedRate: number;
  noCandidateRate: number;
  qualityUnavailableRate: number;
  latestAcceptanceArtifact?: string;
  latestReadinessArtifact?: string;
  latestArchiveManifest?: string;
  readinessBlockingCount?: number;
}

export interface EnforceAlertEvaluation {
  code: string;
  metric: string;
  severity: EnforceAlertSeverity;
  triggered: boolean;
  observed: number;
  threshold: number;
  action: string;
}

function metricValue(snapshot: EnforceObservabilitySnapshot, metric: string): number {
  const value = (snapshot as unknown as Record<string, unknown>)[metric];
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function compare(observed: number, operator: string, threshold: number): boolean {
  if (operator === '>=') return observed >= threshold;
  if (operator === '>') return observed > threshold;
  if (operator === '<=') return observed <= threshold;
  if (operator === '<') return observed < threshold;
  return observed === threshold;
}

export function evaluateEnforceAlertRules(snapshot: EnforceObservabilitySnapshot): EnforceAlertEvaluation[] {
  return p3pProductionEnforceObservabilityFixture.alertRules.map((rule) => {
    const observed = metricValue(snapshot, rule.metric);
    return {
      code: rule.code,
      metric: rule.metric,
      severity: rule.severity,
      observed,
      threshold: rule.threshold,
      triggered: compare(observed, rule.operator, rule.threshold),
      action: rule.action,
    };
  });
}

export function buildPostCutoverObservabilityExport(snapshot: EnforceObservabilitySnapshot) {
  const alerts = evaluateEnforceAlertRules(snapshot);
  return {
    id: p3pProductionEnforceObservabilityFixture.id,
    generatedAt: new Date().toISOString(),
    snapshot,
    alerts,
    rollbackRecommended: alerts.some((alert) => alert.triggered && ['CRITICAL', 'HIGH'].includes(String(alert.severity))),
    rollbackCriteria: p3pProductionEnforceObservabilityFixture.rollbackCriteria,
    routingAuditSearch: p3pProductionEnforceObservabilityFixture.routingAuditSearch,
    operatorIncidentWorkflow: p3pProductionEnforceObservabilityFixture.operatorIncidentWorkflow,
    legacyFinalReport: p3pProductionEnforceObservabilityFixture.legacyFinalReport,
    artifactRetention: p3pProductionEnforceObservabilityFixture.artifactRetention,
  };
}
