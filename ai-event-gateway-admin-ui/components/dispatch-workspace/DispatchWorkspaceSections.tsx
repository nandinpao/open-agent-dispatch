'use client';

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { ApiError } from '@/lib/api/client';
import type {
  CoreAgentCapabilityAssignment,
  CoreAgentAdvisoryRecommendation,
  CoreAdvancedSelectionStrategyContract,
  CoreAgentCapabilityCatalog,
  CoreAgentPoolCapabilityPolicy,
  CoreAgentPoolMemberView,
  CoreAgentPoolView,
  CoreAgentQualityMetricsWindow,
  CoreDispatchFlowAgentOptionView,
  CoreDispatchFlowRuleView,
  CoreDispatchFlowView,
  CoreDispatchSimulationCandidateView,
  CoreDispatchSimulationResponse,
  CoreEventIntakeDecisionResponse,
  CoreSourceSystem,
} from '@/lib/types/core';
import {
  activeRules,
  defaultRule,
  flowActivationIssues,
  flowDisplay,
  flowHealthIssues,
  flowRuntimeReadinessIssues,
  poolDisplay,
  ruleConditionSummary,
  sectionState,
  statusTone,
} from './dispatchWorkspaceModel';
import { WorkspaceStateBlock } from './WorkspaceStateBlock';

type EditablePoolMember = CoreAgentPoolMemberView;

type PoolEditorIntent = 'defaultPool' | 'ruleTargetPool' | 'memberDrawer';

type PoolEditorState = {
  poolId?: string;
  poolCode: string;
  poolName: string;
  sourceSystem: string;
  poolType: string;
  selectionStrategy: string;
  status: string;
  description: string;
  members: EditablePoolMember[];
  metadata?: Record<string, unknown>;
  version?: number;
  updatedAt?: string;
  updatedBy?: string;
};

type SimulationFormState = {
  eventType: string;
  objectType: string;
  errorCode: string;
  severity: string;
  attributesJson: string;
};

type RuleEditorState = {
  ruleId?: string;
  ruleCode: string;
  ruleName: string;
  priority: number;
  eventType: string;
  objectType: string;
  errorCode: string;
  severity: string;
  targetPoolId: string;
  enabled: boolean;
  version?: number;
};

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';
const supportedStrategies = ['LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY'] as const;

function defaultSimulationForm(flow?: CoreDispatchFlowView | null): SimulationFormState {
  const firstRule = activeRules(flow).find((rule) => rule.enabled !== false);
  const condition = firstRule?.condition ?? {};
  return {
    eventType: wildcardToBlank(firstRule?.eventType),
    objectType: wildcardToBlank(firstRule?.objectType),
    errorCode: wildcardToBlank(firstRule?.errorCode),
    severity: typeof condition.severity === 'string' ? condition.severity : 'CRITICAL',
    attributesJson: '{\n  "simulation": true\n}',
  };
}

function wildcardToBlank(value?: string | null): string {
  const text = String(value ?? '').trim();
  return !text || text === '*' ? '' : text;
}

function parseAttributesJson(value: string): Record<string, unknown> {
  const text = value.trim();
  if (!text) return {};
  const parsed = JSON.parse(text) as unknown;
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('attributes 必須是 JSON object。');
  }
  return parsed as Record<string, unknown>;
}

function simulationStatusTone(result?: CoreDispatchSimulationResponse | null): string {
  if (!result) return 'UNKNOWN';
  if (result.status === 'READY') return 'READY';
  if (result.status === 'MANUAL_ASSIGNMENT_REQUIRED') return 'MANUAL';
  return result.status ?? 'BLOCKED';
}

function candidateSummary(candidate: CoreDispatchSimulationCandidateView): string {
  const reasons = candidate.blockingReasons?.filter(Boolean) ?? [];
  if (candidate.selected) return '預計選擇';
  if (candidate.eligible) return '可接單候選';
  return reasons.length ? reasons.join(', ') : candidate.reason ?? '不可接單';
}

const memberStatusOptions = ['ACTIVE', 'INACTIVE', 'DISABLED'] as const;

function SectionShell({
  title,
  eyebrow,
  description,
  children,
  actions,
  state = 'ready',
  error,
}: Readonly<{
  title: string;
  eyebrow: string;
  description?: string;
  children: ReactNode;
  actions?: ReactNode;
  state?: 'loading' | 'ready' | 'empty' | 'error';
  error?: string | null;
}>) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-purple-700">{eyebrow}</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">{title}</h3>
          {description ? <p className="mt-1 text-sm leading-6 text-slate-600">{description}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
      </div>
      <div className="mt-4">
        <WorkspaceStateBlock state={state} title="沒有資料" description="此區段尚無可顯示資料。" error={error} />
        {state === 'ready' ? children : null}
      </div>
    </section>
  );
}

function MetricCard({ label, value, detail }: Readonly<{ label: string; value: string | number; detail?: string }>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <div className="text-xs font-black text-slate-500">{label}</div>
      <div className="mt-1 break-all text-base font-black text-slate-950">{value}</div>
      {detail ? <div className="mt-1 text-xs font-bold leading-5 text-slate-500">{detail}</div> : null}
    </div>
  );
}


function metadataString(metadata: Record<string, unknown> | undefined, key: string): string | undefined {
  const value = metadata?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}

function capabilityCode(value?: string | null): string {
  return normalizeCode(value ?? '');
}

function qualityNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

function qualityPercent(value: unknown): string {
  const numeric = qualityNumber(value);
  if (numeric === undefined) return '-';
  const percent = numeric <= 1 ? numeric * 100 : numeric;
  return `${percent.toFixed(percent >= 10 ? 1 : 2)}%`;
}

function latestQualityObservation(windows: CoreAgentQualityMetricsWindow[] | undefined): CoreAgentQualityMetricsWindow | undefined {
  return [...(windows ?? [])].sort((left, right) => {
    const leftTime = Date.parse(left.calculatedAt ?? left.windowEnd ?? left.updatedAt ?? left.createdAt ?? '') || 0;
    const rightTime = Date.parse(right.calculatedAt ?? right.windowEnd ?? right.updatedAt ?? right.createdAt ?? '') || 0;
    return rightTime - leftTime;
  })[0];
}

function qualityObservationSummary(window: CoreAgentQualityMetricsWindow | undefined): string {
  if (!window) return 'No quality observation yet · observation-only';
  const health = window.recentHealthScore ?? window.metadata?.recentHealthScore ?? window.score;
  const p95 = window.p95CompletionLatencyMs ?? window.metadata?.p95CompletionLatencyMs;
  const retry = window.retryRate ?? window.metadata?.retryRate;
  const responsibility = String(window.responsibilityScope ?? window.metadata?.responsibilityScope ?? 'UNKNOWN').toUpperCase();
  const p95Text = qualityNumber(p95) === undefined ? '-' : `${Math.round(qualityNumber(p95) ?? 0)}ms p95`;
  return `health=${health ?? '-'} · success=${qualityPercent(window.successRate)} · retry=${qualityPercent(retry)} · ${p95Text} · responsibility=${responsibility} · selectionImpact=NONE`;
}

function assignmentReferenceDetail(assignment: CoreAgentCapabilityAssignment, catalog?: CoreAgentCapabilityCatalog): string {
  const source = assignment.source || metadataString(assignment.metadata, 'source') || 'Agent declaration';
  const version = metadataString(assignment.metadata, 'capabilityVersion') || (catalog?.version ? `v${catalog.version}` : undefined);
  const lastReported = assignment.updatedAt || assignment.approvedAt || assignment.requestedAt || catalog?.updatedAt;
  const certification = assignment.evidenceRef || metadataString(assignment.metadata, 'certificationRef') || metadataString(catalog?.metadata, 'certificationRef');
  return [source, version, lastReported ? `last=${lastReported}` : undefined, certification ? `cert=${certification}` : undefined].filter(Boolean).join(' · ');
}

function CapabilityCandidateLookupPanel({
  agents,
  selectedPool,
  capabilityCatalog,
  assignmentsByAgent,
  qualityByAgent,
  loading,
  error,
}: Readonly<{
  agents: CoreDispatchFlowAgentOptionView[];
  selectedPool?: CoreAgentPoolView | null;
  capabilityCatalog: CoreAgentCapabilityCatalog[];
  assignmentsByAgent: Record<string, CoreAgentCapabilityAssignment[]>;
  qualityByAgent: Record<string, CoreAgentQualityMetricsWindow[]>;
  loading: boolean;
  error?: string | null;
}>) {
  const [query, setQuery] = useState('');
  const catalogByCode = useMemo(() => new Map(capabilityCatalog.map((capability) => [capabilityCode(capability.capabilityCode), capability])), [capabilityCatalog]);
  const selectedCode = capabilityCode(query || capabilityCatalog[0]?.capabilityCode || '');
  const poolMemberIds = new Set((selectedPool?.members ?? []).map((member) => member.agentId));
  const suggestions = agents
    .map((agent) => {
      const assignments = assignmentsByAgent[agent.agentId] ?? [];
      const assignment = assignments.find((item) => capabilityCode(item.capabilityCode) === selectedCode && String(item.status ?? '').toUpperCase() === 'APPROVED');
      const runtimeStatus = agent.runtimeConnected ? 'Runtime connected' : agent.disabledReason || agent.runtimeStatus || 'Runtime unknown';
      const quality = latestQualityObservation(qualityByAgent[agent.agentId]);
      return { agent, assignment, inPool: poolMemberIds.has(agent.agentId), runtimeStatus, quality };
    })
    .filter((row) => row.assignment)
    .sort((left, right) => Number(right.inPool) - Number(left.inPool) || left.agent.agentId.localeCompare(right.agent.agentId));

  return (
    <div className="mt-5 rounded-3xl border border-indigo-200 bg-indigo-50 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Capability Registry 2.0</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">Pool 候選能力查詢（Reference-only）</h3>
          <p className="mt-2 text-sm leading-6 text-indigo-900">此查詢只協助管理員搜尋與建議 Pool 候選 Agent；Capability 與 Agent Quality Observation 都是 reference-only。Current Routing Eligibility 仍只由 Source Flow、Agent Pool、Pool Member 與 Runtime Evidence 決定。</p>
        </div>
        <StatusBadge status="REFERENCE_ONLY" label="REFERENCE ONLY" />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <label className={`${labelClass} md:col-span-2`}>Capability
          <input className={inputClass} list="dispatch-capability-registry-codes" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="輸入 Capability Code 搜尋候選 Agent" />
          <datalist id="dispatch-capability-registry-codes">
            {capabilityCatalog.map((capability) => <option key={capability.capabilityCode} value={capability.capabilityCode}>{capability.capabilityName ?? capability.capabilityCode}</option>)}
          </datalist>
        </label>
        <MetricCard label="Registry Entries" value={capabilityCatalog.length} detail="Capability catalog entries" />
      </div>
      {loading ? <div className="mt-4 rounded-2xl border border-indigo-200 bg-white p-4 text-sm font-bold text-indigo-900">Capability registry loading…</div> : null}
      {error ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
      {selectedCode ? (
        <div className="mt-4 overflow-hidden rounded-2xl border border-indigo-200 bg-white">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Agent</th><th className="px-4 py-3">Pool</th><th className="px-4 py-3">Runtime</th><th className="px-4 py-3">Quality Observation</th><th className="px-4 py-3">Reference Evidence</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {suggestions.map(({ agent, assignment, inPool, runtimeStatus, quality }) => assignment ? (
                <tr key={`${selectedCode}-${agent.agentId}`}>
                  <td className="px-4 py-3"><div className="font-black text-slate-900">{agent.agentName ?? agent.agentId}</div><div className="text-xs text-slate-500">{agent.agentId}</div></td>
                  <td className="px-4 py-3"><StatusBadge status={inPool ? 'POOL_MEMBER' : 'CANDIDATE_SUGGESTION'} label={inPool ? 'Pool member' : 'Candidate suggestion'} /></td>
                  <td className="px-4 py-3 text-sm font-bold text-slate-700">{runtimeStatus}</td>
                  <td className="px-4 py-3 text-xs leading-5 text-slate-600">{qualityObservationSummary(quality)}<br /><b>Observation-only</b>; does not change Selection Strategy.</td>
                  <td className="px-4 py-3 text-xs leading-5 text-slate-600">{assignmentReferenceDetail(assignment, catalogByCode.get(selectedCode))}</td>
                </tr>
              ) : null)}
              {!suggestions.length ? <tr><td colSpan={5} className="px-4 py-4 text-sm font-bold text-slate-500">沒有找到已核准此 Capability 的 Agent。這不會阻擋正式派工，只表示沒有可用的 reference 建議。</td></tr> : null}
            </tbody>
          </table>
        </div>
      ) : <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 text-sm font-bold text-slate-500">請先輸入 Capability Code。</div>}
    </div>
  );
}


function recommendationLabel(type: string | undefined): string {
  switch (String(type ?? '').toUpperCase()) {
    case 'ADD_AGENT_TO_POOL': return '建議加入 Pool';
    case 'ADJUST_AGENT_WEIGHT': return '建議調整 Weight';
    case 'INCREASE_POOL_CAPACITY': return '建議增加容量';
    case 'POOL_CAPACITY_RISK': return '容量不足風險';
    default: return type || 'Advisory Recommendation';
  }
}

function confidenceLabel(value: unknown): string {
  const numeric = qualityNumber(value);
  if (numeric === undefined) return '-';
  return `${Math.round((numeric <= 1 ? numeric * 100 : numeric))}%`;
}

function suggestedChangeText(recommendation: CoreAgentAdvisoryRecommendation): string {
  const change = recommendation.suggestedChange ?? {};
  const action = typeof change.action === 'string' ? change.action : 'REVIEW_CONFIGURATION_CHANGE';
  const targetAgent = typeof change.agentId === 'string' ? change.agentId : recommendation.targetAgentId;
  const targetPool = typeof change.targetPoolId === 'string' ? change.targetPoolId : recommendation.targetPoolId;
  return [action, targetAgent ? `agent=${targetAgent}` : undefined, targetPool ? `pool=${targetPool}` : undefined, recommendation.capabilityCode ? `capability=${recommendation.capabilityCode}` : undefined]
    .filter(Boolean)
    .join(' · ');
}

function AdvisoryRecommendationPanel({
  selectedPool,
  scopedTenantId,
}: Readonly<{
  selectedPool?: CoreAgentPoolView | null;
  scopedTenantId?: string;
}>) {
  const [recommendations, setRecommendations] = useState<CoreAgentAdvisoryRecommendation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const targetPoolId = selectedPool?.poolId ?? '';

  const loadRecommendations = useCallback(async (generate = false) => {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const result = generate
        ? await coreAdminApi.generateAdvisoryRecommendations(targetPoolId, '', '24h', scopedTenantId ?? '', 100)
        : await coreAdminApi.getAdvisoryRecommendations(targetPoolId, '', '', '24h', scopedTenantId ?? '', 100);
      setRecommendations(result);
      setMessage(generate ? '已重新產生 advisory recommendations。這些建議不會自動修改設定。' : null);
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Advisory recommendations 載入失敗。'));
    } finally {
      setLoading(false);
    }
  }, [scopedTenantId, targetPoolId]);

  useEffect(() => {
    void loadRecommendations(false);
  }, [loadRecommendations]);

  async function decide(recommendation: CoreAgentAdvisoryRecommendation, decision: 'accept' | 'reject') {
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const body = {
        operatorId: 'admin-ui',
        reason: decision === 'accept'
          ? 'Accepted for explicit configuration review; do not auto-apply.'
          : 'Rejected advisory suggestion from Admin UI.',
        metadata: {
          advisoryOnly: true,
          autoApply: false,
          routingImpact: 'NONE',
        },
      };
      const updated = decision === 'accept'
        ? await coreAdminApi.acceptAdvisoryRecommendation(recommendation.recommendationId, body, scopedTenantId ?? '')
        : await coreAdminApi.rejectAdvisoryRecommendation(recommendation.recommendationId, body, scopedTenantId ?? '');
      setRecommendations((current) => current.map((item) => item.recommendationId === updated.recommendationId ? updated : item));
      setMessage(decision === 'accept'
        ? '已接受建議並留下審核意圖；系統沒有自動修改 Pool、Weight 或 capacity。'
        : '已拒絕建議並留下 audit。');
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Recommendation decision 失敗。'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mt-5 rounded-3xl border border-amber-200 bg-amber-50 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-amber-700">Advisory Recommendation</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">建議中心（只建議，不自動修改）</h3>
          <p className="mt-2 text-sm leading-6 text-amber-900">建議基於 Capability Registry 與 Agent Quality Observation 產生。Accept 只留下 configuration review intent，不會直接改 Agent Pool、Pool Member Weight、capacity、Runtime Eligibility 或 Selection Strategy。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status="ADVISORY_ONLY" label="ADVISORY ONLY" />
          <Button size="xs" onClick={() => { void loadRecommendations(true); }} disabled={loading}>重新產生建議</Button>
        </div>
      </div>
      {message ? <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-bold text-emerald-900">{message}</div> : null}
      {error ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-900">{error}</div> : null}
      <div className="mt-4 overflow-hidden rounded-2xl border border-amber-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">建議</th><th className="px-4 py-3">Evidence</th><th className="px-4 py-3">Suggested Change</th><th className="px-4 py-3">狀態</th><th className="px-4 py-3">Audit</th><th className="px-4 py-3">操作</th></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {recommendations.map((recommendation) => (
              <tr key={recommendation.recommendationId}>
                <td className="px-4 py-3"><div className="font-black text-slate-900">{recommendationLabel(recommendation.recommendationType)}</div><div className="text-xs leading-5 text-slate-500">{recommendation.reason ?? recommendation.recommendationId}</div></td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">window={recommendation.evidenceWindow ?? '24h'}<br />confidence={confidenceLabel(recommendation.confidence)}<br />agent={recommendation.targetAgentId ?? '-'}<br />pool={recommendation.targetPoolId ?? '-'}</td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">{suggestedChangeText(recommendation)}<br /><b>autoApply=false</b><br />routingImpact={recommendation.routingImpact ?? 'NONE'}</td>
                <td className="px-4 py-3"><StatusBadge status={recommendation.status ?? 'OPEN'} /></td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">accepted={recommendation.acceptedBy ?? '-'}<br />rejected={recommendation.rejectedBy ?? '-'}<br />reason={recommendation.decisionReason ?? '-'}</td>
                <td className="px-4 py-3">
                  {String(recommendation.status ?? 'OPEN').toUpperCase() === 'OPEN' ? <div className="flex flex-wrap gap-2"><Button size="xs" onClick={() => { void decide(recommendation, 'accept'); }} disabled={loading}>Accept</Button><Button size="xs" onClick={() => { void decide(recommendation, 'reject'); }} disabled={loading}>Reject</Button></div> : <span className="text-xs font-bold text-slate-500">已決策</span>}
                </td>
              </tr>
            ))}
            {!recommendations.length ? <tr><td colSpan={6} className="px-4 py-4 text-sm font-bold text-slate-500">目前沒有建議。可按「重新產生建議」依現有 Capability 與 Quality Observation 產生 advisory-only 建議。</td></tr> : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}


function splitCapabilityCodes(value: string): string[] {
  return value
    .split(/[\n,]+/g)
    .map((item) => normalizeCode(item))
    .filter(Boolean)
    .filter((item, index, array) => array.indexOf(item) === index);
}

function capabilityPolicyImpactText(policy?: CoreAgentPoolCapabilityPolicy | null): string {
  if (!policy) return '尚未設定 Pool Capability Policy。預設 ADVISORY，不影響 Current Routing。';
  const impact = policy.simulationImpact ?? {};
  const count = String(impact.requiredCapabilityCount ?? policy.requiredCapabilities?.length ?? 0);
  const selectionImpact = String(impact.selectionImpact ?? 'NONE_UNTIL_EXPLICIT_ENFORCEMENT_INTEGRATION');
  return `requiredCapabilities=${count} · matchMode=${policy.matchMode ?? 'ANY'} · enforcementMode=${policy.enforcementMode ?? 'ADVISORY'} · selectionImpact=${selectionImpact}`;
}

function ExplicitCapabilityPolicyPanel({
  selectedPool,
  scopedTenantId,
}: Readonly<{
  selectedPool?: CoreAgentPoolView | null;
  scopedTenantId?: string;
}>) {
  const [policy, setPolicy] = useState<CoreAgentPoolCapabilityPolicy | null>(null);
  const [capabilitiesText, setCapabilitiesText] = useState('');
  const [matchMode, setMatchMode] = useState<'ANY' | 'ALL'>('ANY');
  const [enforcementMode, setEnforcementMode] = useState<'ADVISORY' | 'REQUIRED'>('ADVISORY');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const targetPoolId = selectedPool?.poolId ?? '';

  const loadPolicy = useCallback(async () => {
    if (!targetPoolId) {
      setPolicy(null);
      setCapabilitiesText('');
      setMatchMode('ANY');
      setEnforcementMode('ADVISORY');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const list = await coreAdminApi.getAgentPoolCapabilityPolicies(targetPoolId, scopedTenantId ?? '', 20);
      const current = list.find((item) => item.targetPoolId === targetPoolId) ?? list[0] ?? null;
      setPolicy(current);
      setCapabilitiesText((current?.requiredCapabilities ?? []).join('\n'));
      setMatchMode(String(current?.matchMode ?? 'ANY').toUpperCase() === 'ALL' ? 'ALL' : 'ANY');
      setEnforcementMode(String(current?.enforcementMode ?? 'ADVISORY').toUpperCase() === 'REQUIRED' ? 'REQUIRED' : 'ADVISORY');
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Pool Capability Policy 載入失敗。'));
    } finally {
      setLoading(false);
    }
  }, [scopedTenantId, targetPoolId]);

  useEffect(() => {
    void loadPolicy();
  }, [loadPolicy]);

  async function savePolicy() {
    if (!targetPoolId) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const body: CoreAgentPoolCapabilityPolicy = {
        tenantId: scopedTenantId,
        policyId: policy?.policyId,
        targetPoolId,
        targetPoolName: selectedPool?.poolName ?? selectedPool?.poolCode,
        requiredCapabilities: splitCapabilityCodes(capabilitiesText),
        matchMode,
        enforcementMode,
        advisoryOnly: enforcementMode !== 'REQUIRED',
        explicitPoolPolicy: true,
        routingGate: false,
        metadata: {
          phase9dExplicitCapabilityPolicy: true,
          policyScope: 'AGENT_POOL_ONLY',
          notFlowProfileScopeRuntimeBindingOrTaskRequirement: true,
        },
      };
      const updated = await coreAdminApi.upsertAgentPoolCapabilityPolicy(targetPoolId, body, scopedTenantId ?? '');
      setPolicy(updated);
      setCapabilitiesText((updated.requiredCapabilities ?? []).join('\n'));
      setMatchMode(String(updated.matchMode ?? 'ANY').toUpperCase() === 'ALL' ? 'ALL' : 'ANY');
      setEnforcementMode(String(updated.enforcementMode ?? 'ADVISORY').toUpperCase() === 'REQUIRED' ? 'REQUIRED' : 'ADVISORY');
      setMessage(updated.enforcementMode === 'REQUIRED'
        ? '已儲存 REQUIRED Pool Capability Policy。請先查看 Simulation impact，再設計任何正式 enforcement integration。'
        : '已儲存 ADVISORY Pool Capability Policy。此設定只作建議與模擬影響，不限制派工。');
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Pool Capability Policy 儲存失敗。'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mt-5 rounded-3xl border border-indigo-200 bg-indigo-50 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Explicit Capability Policy</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">Pool Capability Policy（明確政策，不是隱性 Gate）</h3>
          <p className="mt-2 text-sm leading-6 text-indigo-950">Capability 條件只能綁在 Agent Pool Policy。預設 ADVISORY；REQUIRED 必須明確選擇，並先查看 Simulation 影響。不得散落到 Flow、Profile、Scope、Runtime Binding 或 Task Requirement。</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status={enforcementMode} label={enforcementMode} />
          <StatusBadge status="POOL_POLICY_ONLY" label="POOL POLICY ONLY" />
        </div>
      </div>
      {message ? <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-bold text-emerald-900">{message}</div> : null}
      {error ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-900">{error}</div> : null}
      {!selectedPool ? <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 text-sm font-bold text-slate-500">請先指定 Default Pool，才能設定 Pool Capability Policy。</div> : (
        <div className="mt-4 grid gap-4 lg:grid-cols-3">
          <label className={`${labelClass} lg:col-span-1`}>Required Capabilities
            <textarea className={`${inputClass} font-mono`} rows={6} placeholder="每行或逗號分隔，例如：\nERP_INVOICE_ANALYSIS" value={capabilitiesText} onChange={(event) => setCapabilitiesText(event.target.value)} />
          </label>
          <div className="space-y-4 lg:col-span-2">
            <div className="grid gap-4 md:grid-cols-2">
              <label className={labelClass}>Match Mode
                <select className={inputClass} value={matchMode} onChange={(event) => setMatchMode(event.target.value === 'ALL' ? 'ALL' : 'ANY')}>
                  <option value="ANY">ANY：符合任一 Capability</option>
                  <option value="ALL">ALL：必須符合全部 Capability</option>
                </select>
              </label>
              <label className={labelClass}>Enforcement Mode
                <select className={inputClass} value={enforcementMode} onChange={(event) => setEnforcementMode(event.target.value === 'REQUIRED' ? 'REQUIRED' : 'ADVISORY')}>
                  <option value="ADVISORY">ADVISORY：只提示與模擬影響</option>
                  <option value="REQUIRED">REQUIRED：明確限制，需審查風險</option>
                </select>
              </label>
            </div>
            {enforcementMode === 'REQUIRED' ? <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b>風險提示：</b>REQUIRED 可能縮小可派單 Agent 範圍，造成 Task 等待或阻擋。上線前必須先查看 Simulation impact，並確認 Pool 仍有足夠可接單 Agent。</div> : <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950">ADVISORY 是預設模式，只作治理提示、候選建議與 Simulation impact，不改變 Runtime Eligibility 或 Selection Strategy。</div>}
            <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm leading-6 text-slate-700"><b>Simulation impact：</b> {loading ? '載入中...' : capabilityPolicyImpactText(policy)}</div>
            <div className="flex flex-wrap gap-2"><Button size="xs" onClick={() => { void savePolicy(); }} disabled={saving || loading}>{saving ? '儲存中...' : '儲存 Pool Policy'}</Button><Button size="xs" onClick={() => { void loadPolicy(); }} disabled={loading || saving}>重新載入</Button></div>
          </div>
        </div>
      )}
    </div>
  );
}


function AdvancedSelectionStrategyContractPanel() {
  const [contracts, setContracts] = useState<CoreAdvancedSelectionStrategyContract[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadContracts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setContracts(await coreAdminApi.getAdvancedSelectionStrategyContracts());
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Advanced selection strategy contracts 載入失敗。'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadContracts();
  }, [loadContracts]);

  return (
    <div className="mt-5 rounded-3xl border border-slate-200 bg-slate-50 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-600">Advanced Selection Strategy Contract</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">進階選人策略契約（尚未啟用）</h3>
          <p className="mt-2 text-sm leading-6 text-slate-700">ROUND_ROBIN、LOCAL_FIRST、QUALITY_SCORE、COST_AWARE 與 SLA_AWARE 目前只建立契約。Current 可選策略仍只有 LOWEST_LOAD、WEIGHTED_SCORE 與 MANUAL_ONLY；進階策略在具備狀態儲存、併發定義、fallback、Assignment Evidence、Simulation 與測試矩陣前不得啟用。</p>
        </div>
        <StatusBadge status="CONTRACT_ONLY" label="CONTRACT ONLY" />
      </div>
      {loading ? <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 text-sm font-bold text-slate-600">載入進階策略契約…</div> : null}
      {error ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
      <div className="mt-4 overflow-hidden rounded-2xl border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Strategy</th><th className="px-4 py-3">State / Concurrency</th><th className="px-4 py-3">Fallback / Evidence</th><th className="px-4 py-3">Readiness</th></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {contracts.map((contract) => (
              <tr key={contract.strategyCode}>
                <td className="px-4 py-3"><div className="font-black text-slate-900">{contract.displayName ?? contract.strategyCode}</div><div className="text-xs font-bold text-slate-500">{contract.strategyCode} · productionEnabled={String(contract.productionEnabled === true)}</div><StatusBadge status={contract.status ?? 'CONTRACT_ONLY'} label={contract.status ?? 'CONTRACT_ONLY'} /></td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600"><b>State:</b> {contract.stateStorage ?? '-'}<br /><b>Concurrency:</b> {contract.concurrencyDefinition ?? '-'}</td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600"><b>Fallback:</b> {contract.fallbackStrategy ?? 'LOWEST_LOAD'}<br /><b>Evidence:</b> {contract.assignmentEvidenceContract ?? '-'}</td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">{(contract.requiredReadinessChecks ?? []).join(', ') || '-'}{contract.localityDimensions?.length ? <><br /><b>Locality:</b> {contract.localityDimensions.join(' > ')}</> : null}<br /><b>Simulation:</b> {contract.simulationSupportStatus ?? 'CONTRACT_REQUIRED_NOT_ENABLED'}</td>
              </tr>
            ))}
            {!contracts.length && !loading ? <tr><td colSpan={4} className="px-4 py-4 text-sm font-bold text-slate-500">尚無進階策略契約。Current routing 仍使用已支援策略。</td></tr> : null}
          </tbody>
        </table>
      </div>
      <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b>品質分數保護：</b>Phase 9B Agent Quality Observation 不會直接塞入現有 Selection Strategy。QUALITY_SCORE 必須先完成明確公式、樣本門檻、責任歸屬、Simulation breakdown 與 Assignment Evidence 後，才能另行啟用。</div>
    </div>
  );
}

function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback;
  if (error instanceof Error) return error.message;
  return fallback;
}

function optimisticConflictMessage(error: unknown, resourceName: string): string | null {
  if (error instanceof ApiError && (error.code === 'RESOURCE_VERSION_CONFLICT' || error.status === 409)) {
    return `${resourceName} 已被其他管理員更新。請重新載入後比較差異，確認後再儲存。`;
  }
  return null;
}

function normalizeCode(value: string | undefined | null): string {
  return String(value ?? '').trim().toUpperCase().replace(/[^A-Z0-9_\-.]/g, '_').replace(/^_+|_+$/g, '');
}

function normalizePriority(value: number | string | undefined | null, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(0, Math.round(parsed));
}

function normalizePositiveInteger(value: number | string | undefined | null, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(1, Math.round(parsed));
}

function generateId(prefix: string): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${suffix}`;
}

function sourceDisplay(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

function ruleTargetPool(rule: CoreDispatchFlowRuleView, pools: CoreAgentPoolView[]): CoreAgentPoolView | undefined {
  return pools.find((pool) => pool.poolId === rule.targetPoolId);
}

function memberStatus(value?: string | null): string {
  const normalized = normalizeCode(value ?? 'ACTIVE');
  return memberStatusOptions.some((option) => option === normalized) ? normalized : 'ACTIVE';
}

function poolEditorFromPool(pool: CoreAgentPoolView): PoolEditorState {
  return {
    poolId: pool.poolId,
    poolCode: pool.poolCode ?? '',
    poolName: pool.poolName ?? '',
    sourceSystem: pool.sourceSystem ?? '',
    poolType: pool.poolType ?? 'RESOLUTION',
    selectionStrategy: pool.selectionStrategy ?? 'LOWEST_LOAD',
    status: String(pool.status ?? 'ACTIVE').toUpperCase(),
    description: pool.description ?? '',
    metadata: pool.metadata ?? {},
    version: pool.version,
    updatedAt: pool.updatedAt,
    updatedBy: pool.updatedBy,
    members: (pool.members ?? [])
      .filter((member) => String(member.memberStatus ?? 'ACTIVE').toUpperCase() !== 'RETIRED')
      .map((member) => ({
        ...member,
        poolId: pool.poolId,
        poolCode: pool.poolCode,
        memberStatus: memberStatus(member.memberStatus),
        priority: normalizePriority(member.priority, 100),
        weight: normalizePositiveInteger(member.weight, 1),
        metadata: member.metadata ?? {},
      })),
  };
}

function defaultPoolEditor(sourceSystem?: string | null): PoolEditorState {
  const normalizedSource = normalizeCode(sourceSystem);
  return {
    poolCode: normalizedSource ? `${normalizedSource}_DEFAULT_PROCESSING_POOL` : 'DEFAULT_PROCESSING_POOL',
    poolName: normalizedSource ? `${normalizedSource} 預設處理池` : '預設處理池',
    sourceSystem: normalizedSource,
    poolType: 'RESOLUTION',
    selectionStrategy: 'LOWEST_LOAD',
    status: 'ACTIVE',
    description: '未符合特殊分類規則的事件會進入此工作池。',
    members: [],
    metadata: {
      memberSource: 'ADMIN_CONFIGURATION',
      routingModel: 'AGENT_POOL_FIRST',
    },
  };
}

function newMemberForAgent(agent: CoreDispatchFlowAgentOptionView, editor: PoolEditorState): EditablePoolMember {
  return {
    poolId: editor.poolId ?? '',
    poolCode: normalizeCode(editor.poolCode),
    agentId: agent.agentId,
    agentName: agent.agentName ?? agent.agentId,
    memberStatus: 'ACTIVE',
    priority: 100,
    weight: 1,
    approvalStatus: agent.approvalStatus,
    runtimeStatus: agent.runtimeStatus,
    metadata: {
      memberSource: 'ADMIN_CONFIGURATION',
      routingModel: 'AGENT_POOL_FIRST',
    },
  };
}

function ruleEditorFromRule(rule: CoreDispatchFlowRuleView): RuleEditorState {
  const condition = rule.condition ?? {};
  return {
    ruleId: rule.ruleId,
    ruleCode: rule.ruleCode ?? '',
    ruleName: rule.ruleName ?? '',
    priority: normalizePriority(rule.priority, 100),
    eventType: rule.eventType && rule.eventType !== '*' ? rule.eventType : '',
    objectType: rule.objectType && rule.objectType !== '*' ? rule.objectType : '',
    errorCode: rule.errorCode && rule.errorCode !== '*' ? rule.errorCode : '',
    severity: typeof condition.severity === 'string' ? condition.severity : '',
    targetPoolId: rule.targetPoolId ?? '',
    enabled: rule.enabled !== false,
  };
}

function emptyRuleEditor(flow: CoreDispatchFlowView, pools: CoreAgentPoolView[]): RuleEditorState {
  const nextPriority = Math.max(0, ...activeRules(flow).map((rule) => normalizePriority(rule.priority, 0))) + 10;
  return {
    ruleCode: `${normalizeCode(flow.sourceSystem || flow.flowCode || 'FLOW')}_RULE_${nextPriority}`,
    ruleName: `特殊分類規則 ${nextPriority}`,
    priority: nextPriority,
    eventType: '',
    objectType: '',
    errorCode: '',
    severity: '',
    targetPoolId: pools[0]?.poolId ?? flow.defaultPoolId ?? '',
    enabled: true,
  };
}

function ruleViewFromEditor(editor: RuleEditorState, flow: CoreDispatchFlowView): CoreDispatchFlowRuleView {
  const normalizedSeverity = editor.severity.trim();
  const condition: Record<string, unknown> = {};
  if (normalizedSeverity) condition.severity = normalizedSeverity;
  return {
    tenantId: flow.tenantId,
    flowId: flow.flowId,
    ruleId: editor.ruleId ?? generateId('rule'),
    ruleCode: normalizeCode(editor.ruleCode) || generateId('RULE'),
    ruleName: editor.ruleName.trim() || normalizeCode(editor.ruleCode) || '特殊分類規則',
    ruleScope: 'SOURCE_FLOW',
    eventStage: 'EXTERNAL',
    sourceSystem: flow.sourceSystem,
    eventType: editor.eventType.trim() || '*',
    objectType: editor.objectType.trim() || '*',
    errorCode: editor.errorCode.trim() || '*',
    condition,
    matchMode: 'ALL',
    targetPoolId: editor.targetPoolId,
    candidatePoolMode: 'AGENT_POOL',
    routingStrategy: flow.defaultRoutingStrategy ?? 'LOWEST_LOAD',
    priority: normalizePriority(editor.priority, 100),
    enabled: editor.enabled,
    legacyStatus: 'CURRENT',
  };
}

function poolPayload(editor: PoolEditorState, tenantId: string, agents: CoreDispatchFlowAgentOptionView[]): CoreAgentPoolView {
  const poolId = editor.poolId ?? generateId('pool');
  const poolCode = normalizeCode(editor.poolCode);
  return {
    tenantId,
    poolId,
    poolCode,
    poolName: editor.poolName.trim(),
    sourceSystem: normalizeCode(editor.sourceSystem) || undefined,
    poolType: editor.poolType,
    selectionStrategy: editor.selectionStrategy,
    status: editor.status,
    description: editor.description.trim(),
    version: editor.version,
    updatedAt: editor.updatedAt,
    updatedBy: editor.updatedBy,
    metadata: {
      ...(editor.metadata ?? {}),
      routingModel: 'AGENT_POOL_FIRST',
      adminUiEditor: 'DISPATCH_WORKSPACE_POOL_MEMBER_PRESERVING',
    },
    members: editor.members.map((member) => {
      const agent = agents.find((candidate) => candidate.agentId === member.agentId);
      return {
        ...member,
        poolId,
        poolCode,
        agentId: member.agentId,
        agentName: member.agentName ?? agent?.agentName ?? member.agentId,
        memberStatus: memberStatus(member.memberStatus),
        priority: normalizePriority(member.priority, 100),
        weight: normalizePositiveInteger(member.weight, 1),
        approvalStatus: member.approvalStatus ?? agent?.approvalStatus,
        runtimeStatus: member.runtimeStatus ?? agent?.runtimeStatus,
        metadata: member.metadata ?? {},
      };
    }),
  };
}

function PoolEditorDrawer({
  open,
  intent,
  editor,
  sourceSystems,
  agents,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  intent: PoolEditorIntent;
  editor: PoolEditorState;
  sourceSystems: CoreSourceSystem[];
  agents: CoreDispatchFlowAgentOptionView[];
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<PoolEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  const selectedAgentIds = useMemo(() => new Set(editor.members.map((member) => member.agentId)), [editor.members]);

  if (!open) return null;

  function updateMember(agentId: string, patch: Partial<EditablePoolMember>) {
    onChange({ members: editor.members.map((member) => member.agentId === agentId ? { ...member, ...patch } : member) });
  }

  function toggleAgent(agent: CoreDispatchFlowAgentOptionView) {
    if (selectedAgentIds.has(agent.agentId)) {
      onChange({ members: editor.members.filter((member) => member.agentId !== agent.agentId) });
      return;
    }
    onChange({ members: [...editor.members, newMemberForAgent(agent, editor)] });
  }

  return (
    <div className="fixed inset-0 z-[90] flex justify-end bg-slate-950/60" role="dialog" aria-modal="true" aria-label="Agent Pool drawer">
      <div className="flex h-full w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Agent Pool / Contextual resource</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editor.poolId ? '編輯工作池' : '建立工作池'}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              {intent === 'defaultPool' ? '儲存後可立即指定為此 Source Flow 的 Default Pool。' : 'Pool 成員、Weight、Priority、Member Status 與 Metadata 會完整保留。'}
            </p>
            {editor.version ? <div className="mt-1 text-xs font-bold text-slate-500">version={editor.version} · updatedAt={editor.updatedAt ?? '-'}</div> : null}
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="關閉">×</button>
        </div>
        <div className="flex-1 space-y-6 overflow-y-auto p-6">
          {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          <section className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">基本資料</div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <label className={labelClass}>來源系統
                <select className={inputClass} value={editor.sourceSystem} onChange={(event) => onChange({ sourceSystem: event.target.value })}>
                  <option value="">共用 Pool</option>
                  {sourceSystems.map((source) => <option key={source.sourceSystemId} value={source.sourceSystemId}>{sourceDisplay(source)}</option>)}
                </select>
              </label>
              <label className={labelClass}>Pool 類型
                <select className={inputClass} value={editor.poolType} onChange={(event) => onChange({ poolType: event.target.value })}>
                  <option value="RESOLUTION">處理池</option>
                  <option value="TRIAGE">分類池</option>
                  <option value="ESCALATION">升級池</option>
                  <option value="MANUAL_REVIEW">人工檢查池</option>
                </select>
              </label>
              <label className={labelClass}>Pool Code
                <input className={inputClass} value={editor.poolCode} onChange={(event) => onChange({ poolCode: normalizeCode(event.target.value) })} />
              </label>
              <label className={labelClass}>Pool 名稱
                <input className={inputClass} value={editor.poolName} onChange={(event) => onChange({ poolName: event.target.value })} />
              </label>
              <label className={labelClass}>選人方式
                <select className={inputClass} value={editor.selectionStrategy} onChange={(event) => onChange({ selectionStrategy: event.target.value })}>
                  <option value="LOWEST_LOAD">低負載優先（LOWEST_LOAD）</option>
                  <option value="WEIGHTED_SCORE">權重分數（WEIGHTED_SCORE）</option>
                  <option value="MANUAL_ONLY">等待人工指定（MANUAL_ONLY）</option>
                </select>
              </label>
              <label className={labelClass}>狀態
                <select className={inputClass} value={editor.status} onChange={(event) => onChange({ status: event.target.value })}>
                  <option value="ACTIVE">啟用</option>
                  <option value="INACTIVE">停用</option>
                  <option value="DRAFT">草稿</option>
                </select>
              </label>
              <label className={`${labelClass} md:col-span-2`}>用途說明
                <textarea className={inputClass} rows={3} value={editor.description} onChange={(event) => onChange({ description: event.target.value })} />
              </label>
            </div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="text-xs font-black uppercase tracking-wide text-slate-500">Pool Members</div>
                <h3 className="mt-1 text-base font-black text-slate-950">成員管理</h3>
                <p className="mt-1 text-sm leading-6 text-slate-600">取消勾選只會移除本次表單中的成員；既有成員的 Weight、Priority、Status 與 Metadata 不會被重設。</p>
              </div>
              <div className="rounded-full bg-white px-3 py-1.5 text-xs font-black text-slate-600">{editor.members.length} members</div>
            </div>
            <div className="mt-4 space-y-3">
              {agents.map((agent) => {
                const selected = selectedAgentIds.has(agent.agentId);
                const member = editor.members.find((row) => row.agentId === agent.agentId);
                return (
                  <div key={agent.agentId} className={`rounded-2xl border p-4 ${selected ? 'border-purple-200 bg-white' : 'border-slate-200 bg-white/70'}`}>
                    <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                      <label className="flex min-w-0 items-start gap-3 text-sm font-black text-slate-900">
                        <input type="checkbox" className="mt-1 h-4 w-4 rounded border-slate-300 text-purple-600" checked={selected} onChange={() => toggleAgent(agent)} />
                        <span className="min-w-0">
                          <span className="block truncate">{agent.agentName ?? agent.agentId}</span>
                          <span className="mt-1 block truncate text-xs font-bold text-slate-500">{agent.agentId}</span>
                        </span>
                      </label>
                      <div className="flex flex-wrap gap-2"><StatusBadge status={agent.approvalStatus ?? 'UNKNOWN'} /><StatusBadge status={agent.runtimeStatus ?? 'UNKNOWN'} /></div>
                    </div>
                    {selected && member ? (
                      <div className="mt-4 grid gap-3 sm:grid-cols-4">
                        <label className="text-xs font-black text-slate-700">Member Status
                          <select className={inputClass} value={memberStatus(member.memberStatus)} onChange={(event) => updateMember(agent.agentId, { memberStatus: event.target.value })}>
                            <option value="ACTIVE">ACTIVE</option>
                            <option value="INACTIVE">INACTIVE</option>
                            <option value="DISABLED">DISABLED</option>
                          </select>
                        </label>
                        <label className="text-xs font-black text-slate-700">Weight
                          <input type="number" min={1} className={inputClass} value={member.weight ?? 1} onChange={(event) => updateMember(agent.agentId, { weight: normalizePositiveInteger(event.target.value, 1) })} />
                        </label>
                        <label className="text-xs font-black text-slate-700">Priority
                          <input type="number" min={0} className={inputClass} value={member.priority ?? 100} onChange={(event) => updateMember(agent.agentId, { priority: normalizePriority(event.target.value, 100) })} />
                        </label>
                        <div className="rounded-xl bg-slate-50 px-3 py-2 text-xs font-semibold leading-5 text-slate-600">
                          Metadata 保留 {Object.keys(member.metadata ?? {}).length} 個欄位
                        </div>
                      </div>
                    ) : null}
                  </div>
                );
              })}
              {!agents.length ? <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-4 text-sm font-bold text-slate-600">尚無可加入的 Agent。請先建立並核准 Agent。</div> : null}
            </div>
          </section>
        </div>
        <div className="flex flex-col-reverse gap-3 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={busy}>取消</Button>
          <Button tone="primary" onClick={onSave} disabled={busy}>{busy ? '儲存中…' : '儲存工作池'}</Button>
        </div>
      </div>
    </div>
  );
}

function RuleEditorDrawer({
  open,
  editor,
  pools,
  busy,
  error,
  onChange,
  onClose,
  onSave,
}: Readonly<{
  open: boolean;
  editor: RuleEditorState;
  pools: CoreAgentPoolView[];
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<RuleEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
}>) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[90] flex justify-end bg-slate-950/60" role="dialog" aria-modal="true" aria-label="特殊分類規則表單">
      <div className="flex h-full w-full max-w-3xl flex-col overflow-hidden bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-purple-700">Classification Rule</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editor.ruleId ? '編輯特殊分類規則' : '新增特殊分類規則'}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">只替少數穩定且高價值的例外建立規則；未命中的事件會回到 Default Pool。</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="關閉">×</button>
        </div>
        <div className="flex-1 overflow-y-auto p-6">
          {error ? <div className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          <div className="grid gap-4 md:grid-cols-2">
            <label className={labelClass}>Rule Code
              <input className={inputClass} value={editor.ruleCode} onChange={(event) => onChange({ ruleCode: normalizeCode(event.target.value) })} />
            </label>
            <label className={labelClass}>Rule 名稱
              <input className={inputClass} value={editor.ruleName} onChange={(event) => onChange({ ruleName: event.target.value })} />
            </label>
            <label className={labelClass}>優先序
              <input type="number" min={0} className={inputClass} value={editor.priority} onChange={(event) => onChange({ priority: normalizePriority(event.target.value, 100) })} />
            </label>
            <label className={labelClass}>目標工作池
              <select className={inputClass} value={editor.targetPoolId} onChange={(event) => onChange({ targetPoolId: event.target.value })}>
                <option value="">請選擇 Rule target Pool</option>
                {pools.map((pool) => <option key={pool.poolId} value={pool.poolId}>{poolDisplay(pool)}</option>)}
              </select>
            </label>
            <label className={labelClass}>Event Type
              <input className={inputClass} placeholder="例如 EQUIPMENT_ALARM；空白代表不限" value={editor.eventType} onChange={(event) => onChange({ eventType: event.target.value })} />
            </label>
            <label className={labelClass}>Object Type
              <input className={inputClass} placeholder="例如 INVOICE；空白代表不限" value={editor.objectType} onChange={(event) => onChange({ objectType: event.target.value })} />
            </label>
            <label className={labelClass}>Error Code
              <input className={inputClass} placeholder="例如 TEMP_HIGH；空白代表不限" value={editor.errorCode} onChange={(event) => onChange({ errorCode: event.target.value })} />
            </label>
            <label className={labelClass}>Severity
              <input className={inputClass} placeholder="例如 CRITICAL；空白代表不限" value={editor.severity} onChange={(event) => onChange({ severity: event.target.value })} />
            </label>
            <label className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm font-black text-slate-800 md:col-span-2">
              <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-purple-600" checked={editor.enabled} onChange={(event) => onChange({ enabled: event.target.checked })} />
              啟用此規則
            </label>
          </div>
        </div>
        <div className="flex flex-col-reverse gap-3 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={busy}>取消</Button>
          <Button tone="primary" onClick={onSave} disabled={busy}>{busy ? '儲存中…' : '儲存規則'}</Button>
        </div>
      </div>
    </div>
  );
}

export function DispatchWorkspaceSections({
  tenantId,
  flow,
  sourceSystems,
  pools,
  sourceLoading,
  sourceError,
  poolLoading,
  poolError,
  onReload,
  onSimulationResultChange,
  onRealTestResultChange,
}: Readonly<{
  tenantId: string;
  flow: CoreDispatchFlowView | null;
  sourceSystems: CoreSourceSystem[];
  pools: CoreAgentPoolView[];
  sourceLoading: boolean;
  sourceError?: string | null;
  poolLoading: boolean;
  poolError?: string | null;
  onReload: () => void;
  onSimulationResultChange?: (result: CoreDispatchSimulationResponse | null) => void;
  onRealTestResultChange?: (result: CoreEventIntakeDecisionResponse | null) => void;
}>) {
  const selectedSource = sourceSystems.find((source) => source.sourceSystemId === flow?.sourceSystem);
  const defaultPool = pools.find((pool) => pool.poolId === flow?.defaultPoolId);
  const rules = activeRules(flow);
  const primaryRule = defaultRule(flow);
  const configurationIssues = flowHealthIssues(flow, pools);
  const activationIssues = flowActivationIssues(flow);
  const runtimeIssues = flowRuntimeReadinessIssues(flow, pools);
  const issues = runtimeIssues;
  const sourceState = sectionState(sourceLoading, sourceError ?? null, !flow?.sourceSystem);
  const poolState = sectionState(poolLoading, poolError ?? null, !flow?.defaultPoolId && rules.every((rule) => !rule.targetPoolId));

  const [agents, setAgents] = useState<CoreDispatchFlowAgentOptionView[]>([]);
  const [agentsLoaded, setAgentsLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [poolIntent, setPoolIntent] = useState<PoolEditorIntent>('defaultPool');
  const [poolEditorOpen, setPoolEditorOpen] = useState(false);
  const [poolEditor, setPoolEditor] = useState<PoolEditorState>(() => defaultPoolEditor());
  const [ruleEditorOpen, setRuleEditorOpen] = useState(false);
  const [ruleEditor, setRuleEditor] = useState<RuleEditorState>(() => ({ ruleCode: '', ruleName: '', priority: 100, eventType: '', objectType: '', errorCode: '', severity: '', targetPoolId: '', enabled: true }));
  const [simulationForm, setSimulationForm] = useState<SimulationFormState>(() => defaultSimulationForm(flow));
  const [simulationResult, setSimulationResult] = useState<CoreDispatchSimulationResponse | null>(null);
  const [simulationBusy, setSimulationBusy] = useState(false);
  const [simulationError, setSimulationError] = useState<string | null>(null);
  const [realTestResult, setRealTestResult] = useState<CoreEventIntakeDecisionResponse | null>(null);
  const [realTestBusy, setRealTestBusy] = useState(false);
  const [realTestError, setRealTestError] = useState<string | null>(null);
  const [capabilityCatalog, setCapabilityCatalog] = useState<CoreAgentCapabilityCatalog[]>([]);
  const [capabilityAssignmentsByAgent, setCapabilityAssignmentsByAgent] = useState<Record<string, CoreAgentCapabilityAssignment[]>>({});
  const [qualityByAgent, setQualityByAgent] = useState<Record<string, CoreAgentQualityMetricsWindow[]>>({});
  const [capabilityLookupLoading, setCapabilityLookupLoading] = useState(false);
  const [capabilityLookupError, setCapabilityLookupError] = useState<string | null>(null);

  const scopedTenantId = tenantId.trim();

  const loadAgents = useCallback(async () => {
    if (!scopedTenantId || agentsLoaded) return;
    try {
      setAgents(await coreAdminApi.getDispatchFlowAgentOptions(scopedTenantId));
      setAgentsLoaded(true);
    } catch (caught) {
      setActionError(apiErrorMessage(caught, 'Agent 清單載入失敗。'));
    }
  }, [agentsLoaded, scopedTenantId]);


  const loadCapabilityRegistry = useCallback(async () => {
    if (!scopedTenantId) {
      setCapabilityCatalog([]);
      setCapabilityAssignmentsByAgent({});
      setQualityByAgent({});
      return;
    }
    setCapabilityLookupLoading(true);
    setCapabilityLookupError(null);
    try {
      const [catalog, currentAgents] = await Promise.all([
        coreAdminApi.getCapabilities('ACTIVE', undefined, scopedTenantId),
        agentsLoaded ? Promise.resolve(agents) : coreAdminApi.getDispatchFlowAgentOptions(scopedTenantId),
      ]);
      if (!agentsLoaded) {
        setAgents(currentAgents);
        setAgentsLoaded(true);
      }
      const [assignmentEntries, qualityEntries] = await Promise.all([
        Promise.allSettled(currentAgents.slice(0, 100).map(async (agent) => [agent.agentId, await coreAdminApi.getAgentCapabilities(agent.agentId)] as const)),
        Promise.allSettled(currentAgents.slice(0, 100).map(async (agent) => [agent.agentId, await coreAdminApi.getAgentQualityWindows(agent.agentId, '24h', scopedTenantId, 4)] as const)),
      ]);
      const nextAssignments: Record<string, CoreAgentCapabilityAssignment[]> = {};
      for (const entry of assignmentEntries) {
        if (entry.status === 'fulfilled') nextAssignments[entry.value[0]] = entry.value[1];
      }
      const nextQuality: Record<string, CoreAgentQualityMetricsWindow[]> = {};
      for (const entry of qualityEntries) {
        if (entry.status === 'fulfilled') nextQuality[entry.value[0]] = entry.value[1];
      }
      setCapabilityCatalog(catalog);
      setCapabilityAssignmentsByAgent(nextAssignments);
      setQualityByAgent(nextQuality);
    } catch (caught) {
      setCapabilityLookupError(apiErrorMessage(caught, 'Capability Registry 載入失敗。'));
      setCapabilityCatalog([]);
      setCapabilityAssignmentsByAgent({});
      setQualityByAgent({});
    } finally {
      setCapabilityLookupLoading(false);
    }
  }, [agents, agentsLoaded, scopedTenantId]);

  useEffect(() => {
    if (flow?.flowId) void loadCapabilityRegistry();
  }, [flow?.flowId, loadCapabilityRegistry]);

  useEffect(() => {
    if (poolEditorOpen) void loadAgents();
  }, [loadAgents, poolEditorOpen]);

  useEffect(() => {
    setSimulationForm(defaultSimulationForm(flow));
    setSimulationResult(null);
    setSimulationError(null);
    setRealTestResult(null);
    setRealTestError(null);
    onSimulationResultChange?.(null);
    onRealTestResultChange?.(null);
  }, [flow, onRealTestResultChange, onSimulationResultChange]);

  if (!flow) {
    return (
      <section className="rounded-3xl border border-dashed border-slate-300 bg-white p-8 text-center shadow-sm">
        <div className="text-xs font-black uppercase tracking-wide text-purple-700">Dispatch Workspace</div>
        <h2 className="mt-2 text-2xl font-black text-slate-950">請先選擇 Source Flow</h2>
        <p className="mx-auto mt-2 max-w-2xl text-sm leading-6 text-slate-600">
          Flow／Rule／Pool 整合編輯會在選定 Source Flow 後顯示。Agent Pool 是目前 Flow 的 contextual resource，不是頁面第一操作物件。
        </p>
      </section>
    );
  }

  const currentFlow = flow;

  async function saveFlowPatch(patch: Partial<CoreDispatchFlowView>, successText: string) {
    if (!scopedTenantId) { setActionError('請先選擇 Workspace。'); return; }
    setBusy(true); setActionError(null); setMessage(null);
    try {
      await coreAdminApi.updateDispatchFlow(currentFlow.flowId, { ...currentFlow, ...patch, tenantId: scopedTenantId }, scopedTenantId);
      setMessage(successText);
      onReload();
    } catch (caught) {
      setActionError(optimisticConflictMessage(caught, 'Source Flow') ?? apiErrorMessage(caught, 'Source Flow 儲存失敗。'));
    } finally {
      setBusy(false);
    }
  }

  async function handleDefaultPoolChange(poolId: string) {
    await saveFlowPatch({ defaultPoolId: poolId || undefined }, poolId ? 'Default Pool 已更新。' : 'Default Pool 已清除。');
  }

  function openCreatePool(intent: PoolEditorIntent) {
    setPoolIntent(intent);
    setPoolEditor(defaultPoolEditor(currentFlow.sourceSystem));
    setPoolEditorOpen(true);
    setActionError(null);
  }

  function openEditPool(pool: CoreAgentPoolView, intent: PoolEditorIntent) {
    setPoolIntent(intent);
    setPoolEditor(poolEditorFromPool(pool));
    setPoolEditorOpen(true);
    setActionError(null);
  }

  async function savePool() {
    if (!scopedTenantId) { setActionError('請先選擇 Workspace。'); return; }
    if (!normalizeCode(poolEditor.poolCode)) { setActionError('Pool Code 為必填。'); return; }
    if (!poolEditor.poolName.trim()) { setActionError('Pool 名稱為必填。'); return; }
    if (!supportedStrategies.some((strategy) => strategy === poolEditor.selectionStrategy)) { setActionError('請選擇支援的選人方式。'); return; }
    setBusy(true); setActionError(null); setMessage(null);
    try {
      const body = poolPayload(poolEditor, scopedTenantId, agents);
      const saved = poolEditor.poolId
        ? await coreAdminApi.updateAgentPool(poolEditor.poolId, body, scopedTenantId)
        : await coreAdminApi.createAgentPool(body, scopedTenantId);
      if (poolIntent === 'defaultPool' && currentFlow.defaultPoolId !== saved.poolId) {
        await coreAdminApi.updateDispatchFlow(currentFlow.flowId, { ...currentFlow, tenantId: scopedTenantId, defaultPoolId: saved.poolId }, scopedTenantId);
      }
      setPoolEditorOpen(false);
      setMessage(poolIntent === 'defaultPool'
        ? '工作池已儲存並指定為 Default Pool。下一步請按「儲存並啟用」，再執行 Simulation／送出正式事件。'
        : '工作池已儲存。');
      onReload();
    } catch (caught) {
      setActionError(optimisticConflictMessage(caught, 'Agent Pool') ?? apiErrorMessage(caught, 'Agent Pool 儲存失敗。'));
    } finally {
      setBusy(false);
    }
  }

  function openCreateRule() {
    setRuleEditor(emptyRuleEditor(currentFlow, pools));
    setRuleEditorOpen(true);
    setActionError(null);
  }

  function openEditRule(rule: CoreDispatchFlowRuleView) {
    setRuleEditor(ruleEditorFromRule(rule));
    setRuleEditorOpen(true);
    setActionError(null);
  }

  async function saveRule() {
    if (!ruleEditor.targetPoolId) { setActionError('請選擇 Rule target Pool。'); return; }
    const nextRule = ruleViewFromEditor(ruleEditor, currentFlow);
    const currentRules = currentFlow.rules ?? [];
    const nextRules = currentRules.some((rule) => (rule.ruleId && rule.ruleId === nextRule.ruleId) || (rule.ruleCode && rule.ruleCode === nextRule.ruleCode))
      ? currentRules.map((rule) => ((rule.ruleId && rule.ruleId === nextRule.ruleId) || (rule.ruleCode && rule.ruleCode === nextRule.ruleCode)) ? { ...rule, ...nextRule } : rule)
      : [...currentRules, nextRule];
    await saveFlowPatch({ rules: nextRules }, '特殊分類規則已儲存。');
    setRuleEditorOpen(false);
  }

  async function runDispatchSimulation() {
    if (!scopedTenantId) { setSimulationError('請先選擇 Workspace。'); return; }
    if (!currentFlow.sourceSystem) { setSimulationError('Source Flow 尚未指定來源系統。'); return; }
    if (runtimeIssues.length > 0) { setSimulationError(`請先完成正式派工前置條件：${runtimeIssues.join('；')}`); return; }
    setSimulationBusy(true);
    setSimulationError(null);
    setSimulationResult(null);
    try {
      const attributes = parseAttributesJson(simulationForm.attributesJson);
      const result = await coreAdminApi.simulateDispatch({
        tenantId: scopedTenantId,
        flowId: currentFlow.flowId,
        sourceSystem: currentFlow.sourceSystem,
        eventStage: 'EXTERNAL',
        eventType: simulationForm.eventType.trim() || undefined,
        objectType: simulationForm.objectType.trim() || undefined,
        errorCode: simulationForm.errorCode.trim() || undefined,
        severity: simulationForm.severity.trim() || undefined,
        message: `OpenDispatch no-side-effect simulation for ${flowDisplay(currentFlow)}`,
        attributes,
        includeRuntimeSnapshot: true,
      }, scopedTenantId);
      setSimulationResult(result);
      onSimulationResultChange?.(result);
    } catch (caught) {
      setSimulationError(apiErrorMessage(caught, '派工模擬失敗。'));
    } finally {
      setSimulationBusy(false);
    }
  }

  async function runRealTestEvent() {
    if (!scopedTenantId) { setRealTestError('請先選擇 Workspace。'); return; }
    if (!currentFlow.sourceSystem) { setRealTestError('Source Flow 尚未指定來源系統。'); return; }
    if (runtimeIssues.length > 0) { setRealTestError(`請先完成正式派工前置條件：${runtimeIssues.join('；')}`); return; }
    if (simulationResult?.blockerCode) { setRealTestError(`Simulation 仍有阻擋：${simulationResult.blockerCode} ${simulationResult.blockerReason ?? ''}`.trim()); return; }
    setRealTestBusy(true);
    setRealTestError(null);
    setRealTestResult(null);
    onRealTestResultChange?.(null);
    try {
      const attributes = parseAttributesJson(simulationForm.attributesJson);
      const result = await coreAdminApi.createDispatchFlowRealTestEvent(currentFlow.flowId, {
        message: `OpenDispatch real test event for ${flowDisplay(currentFlow)}`,
        severity: simulationForm.severity.trim() || 'INFO',
        eventType: simulationForm.eventType.trim() || undefined,
        objectType: simulationForm.objectType.trim() || undefined,
        errorCode: simulationForm.errorCode.trim() || undefined,
        objectId: `REAL-TEST-${Date.now()}`,
        correlationId: generateId('corr'),
        attributes: {
          ...attributes,
          adminUiTest: true,
          setupJourney: 'BEGINNER_JOURNEY_REAL_TEST',
        },
      }, scopedTenantId);
      setRealTestResult(result);
      onRealTestResultChange?.(result);
    } catch (caught) {
      setRealTestError(apiErrorMessage(caught, '真實測試事件送出失敗。'));
    } finally {
      setRealTestBusy(false);
    }
  }

  async function setFlowStatus(status: string) {
    await saveFlowPatch({ status }, status === 'ACTIVE' ? 'Source Flow 已啟用。' : 'Source Flow 已儲存為草稿。');
  }

  return (
    <div className="space-y-5">
      {message ? <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-bold text-emerald-900">{message}</div> : null}
      {actionError ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{actionError}</div> : null}

      <section className={`rounded-3xl border p-5 shadow-sm ${issues.length ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide opacity-70">Flow Health</div>
            <h2 className="mt-1 text-2xl font-black">{issues.length ? '此 Source Flow 尚需補設定' : '此 Source Flow 可作為 Current 派工入口'}</h2>
            <p className="mt-2 text-sm leading-6">{issues.length ? issues.join('；') : '基本資料、預設工作池與規則目標均可解析。正式派工仍需 Runtime Eligibility 在 Task 階段決定。'}</p>
          </div>
          <StatusBadge status={issues.length ? 'NOT_READY' : 'READY'} />
        </div>
      </section>

      <SectionShell
        title="Flow 基本資料"
        eyebrow="1 / 5"
        description="Source Flow 是本頁第一操作物件。來源系統與 Flow 狀態先確定，後續區段才會顯示派工目標。"
        state={sourceState}
        error={sourceError}
        actions={<><Button size="xs" onClick={() => setFlowStatus('DRAFT')} disabled={busy}>儲存草稿</Button><Button size="xs" tone="primary" onClick={() => setFlowStatus('ACTIVE')} disabled={busy || configurationIssues.length > 0}>儲存並啟用</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="Flow" value={flowDisplay(flow)} detail={flow.flowCode ?? flow.flowId} />
          <MetricCard label="來源系統" value={selectedSource?.displayName ?? flow.sourceSystem ?? '未指定'} detail={flow.sourceSystem} />
          <MetricCard label="狀態" value={flow.status ?? 'DRAFT'} detail={statusTone(flow.status) === 'READY' ? '已啟用' : '尚未啟用或需檢查'} />
          <MetricCard label="版本" value={`v${flow.version ?? '-'}`} detail={flow.updatedAt ? `更新於 ${flow.updatedAt}` : '尚無更新時間'} />
        </div>
        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-600">{flow.description || '尚未填寫 Flow 說明。'}</div>
      </SectionShell>

      <SectionShell
        title="預設派工"
        eyebrow="2 / 5"
        description="未符合特殊分類規則的事件，會進入預設工作池。這是 Current 派工的主路徑。"
        state={poolState}
        error={poolError}
        actions={<><Button size="xs" onClick={() => openCreatePool('defaultPool')}>建立新工作池</Button>{defaultPool ? <Button size="xs" onClick={() => openEditPool(defaultPool, 'memberDrawer')}>查看工作池 Drawer</Button> : null}</>}
      >
        <div className="grid gap-3 md:grid-cols-3">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 md:col-span-1">
            <label className={labelClass}>Default Agent Pool
              <select className={inputClass} value={flow.defaultPoolId ?? ''} onChange={(event) => { void handleDefaultPoolChange(event.target.value); }} disabled={busy}>
                <option value="">尚未設定工作池</option>
                {pools.map((pool) => <option key={pool.poolId} value={pool.poolId}>{poolDisplay(pool)}</option>)}
              </select>
            </label>
          </div>
          <MetricCard label="Selection Strategy" value={flow.defaultRoutingStrategy ?? defaultPool?.selectionStrategy ?? primaryRule?.routingStrategy ?? 'LOWEST_LOAD'} detail="策略由 Flow 或目標 Pool 決定；完整公式由 Routing Core 負責。" />
          <MetricCard label="Candidate Mode" value={flow.defaultCandidatePoolMode ?? primaryRule?.candidatePoolMode ?? 'AGENT_POOL'} detail="Pool-first Current 模型" />
        </div>
      </SectionShell>

      <SectionShell
        title="特殊分類規則"
        eyebrow="3 / 5"
        description="只替少數穩定且高價值的例外建立規則；未命中的事件會回到 Default Pool。"
        actions={<Button size="xs" tone="primary" onClick={openCreateRule} disabled={!pools.length}>新增規則</Button>}
      >
        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
              <tr><th className="px-4 py-3">優先序</th><th className="px-4 py-3">條件</th><th className="px-4 py-3">目標工作池</th><th className="px-4 py-3">狀態</th><th className="px-4 py-3">操作</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {rules.map((rule) => (
                <tr key={rule.ruleId ?? rule.ruleCode ?? `${rule.priority}-${rule.eventType}`}>
                  <td className="px-4 py-3 font-black text-slate-900">{rule.priority ?? 100}</td>
                  <td className="px-4 py-3 font-bold text-slate-700">{ruleConditionSummary(rule)}</td>
                  <td className="px-4 py-3 font-bold text-slate-700">{poolDisplay(ruleTargetPool(rule, pools), rule.targetPoolCode ?? rule.targetPoolId ?? flow.defaultPoolId)}</td>
                  <td className="px-4 py-3"><StatusBadge status={rule.enabled === false ? 'DISABLED' : 'ACTIVE'} /></td>
                  <td className="px-4 py-3"><Button size="xs" onClick={() => openEditRule(rule)}>編輯</Button></td>
                </tr>
              ))}
              <tr className="bg-slate-50">
                <td className="px-4 py-3 font-black text-slate-900">Default</td>
                <td className="px-4 py-3 font-bold text-slate-700">其他事件</td>
                <td className="px-4 py-3 font-bold text-slate-700">{poolDisplay(defaultPool, flow.defaultPoolId)}</td>
                <td className="px-4 py-3"><StatusBadge status="ACTIVE" /></td>
                <td className="px-4 py-3 text-xs font-bold text-slate-500">不可刪除</td>
              </tr>
            </tbody>
          </table>
        </div>
      </SectionShell>

      <SectionShell title="工作池與 Agent" eyebrow="4 / 5" description="Agent Pool 是目前 Flow 的派工資源，不再是派工設定頁第一操作物件。" state={poolState} error={poolError} actions={defaultPool ? <Button size="xs" onClick={() => openEditPool(defaultPool, 'memberDrawer')}>管理 Pool 成員</Button> : null}>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="工作池" value={poolDisplay(defaultPool, flow.defaultPoolId)} detail={defaultPool?.poolType ?? 'Default Pool'} />
          <MetricCard label="成員數" value={defaultPool?.memberCount ?? defaultPool?.members?.length ?? 0} detail="Pool Member Agent" />
          <MetricCard label="目前可接單" value={defaultPool?.availableAgentCount ?? 0} detail="以 Core / Runtime Evidence 為準" />
          <MetricCard label="Pool 狀態" value={defaultPool?.status ?? (flow.defaultPoolId ? 'UNKNOWN' : 'NOT_CONFIGURED')} detail={defaultPool?.selectionStrategy ?? '尚無策略'} />
        </div>
        <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
          Capability 可在 Agent 詳細頁作為搜尋與治理參考；本區只呈現 Pool、Pool Member 與 Runtime Eligibility 對派工的影響。
        </div>
        <CapabilityCandidateLookupPanel
          agents={agents}
          selectedPool={defaultPool}
          capabilityCatalog={capabilityCatalog}
          assignmentsByAgent={capabilityAssignmentsByAgent}
          qualityByAgent={qualityByAgent}
          loading={capabilityLookupLoading}
          error={capabilityLookupError}
        />
        <ExplicitCapabilityPolicyPanel
          selectedPool={defaultPool}
          scopedTenantId={scopedTenantId}
        />
        <AdvisoryRecommendationPanel
          selectedPool={defaultPool}
          scopedTenantId={scopedTenantId}
        />
        <AdvancedSelectionStrategyContractPanel />
      </SectionShell>

      <div id="dispatch-workspace-simulation"><SectionShell title="測試與啟用" eyebrow="5 / 5" description="no-side-effect Dispatch Simulation 會共用正式 Flow／Rule／Pool／Runtime Eligibility／Selection 流程，但不建立 Task、Assignment、Delivery、ACK 或 Result。">
        <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
          Runtime 狀態會持續變動，模擬結果不保證正式事件送入時仍選擇相同 Agent。Simulation 只產生 Evidence，不會污染正式資料。
        </div>
        {activationIssues.length ? <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-950">{activationIssues.join('；')}。請先按「儲存並啟用」，否則直接送 /api/events/intake 會建立 NO_CANDIDATE / NO_ACTIVE_FLOW_RULE 的 Task。</div> : null}
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <label className={labelClass}>Event Type
            <input className={inputClass} placeholder="空白代表不限，走 Default Pool" value={simulationForm.eventType} onChange={(event) => setSimulationForm((current) => ({ ...current, eventType: event.target.value }))} />
          </label>
          <label className={labelClass}>Object Type
            <input className={inputClass} placeholder="空白代表不限" value={simulationForm.objectType} onChange={(event) => setSimulationForm((current) => ({ ...current, objectType: event.target.value }))} />
          </label>
          <label className={labelClass}>Error Code
            <input className={inputClass} placeholder="空白代表不限" value={simulationForm.errorCode} onChange={(event) => setSimulationForm((current) => ({ ...current, errorCode: event.target.value }))} />
          </label>
          <label className={labelClass}>Severity
            <input className={inputClass} placeholder="例如 CRITICAL" value={simulationForm.severity} onChange={(event) => setSimulationForm((current) => ({ ...current, severity: event.target.value }))} />
          </label>
          <label className={`${labelClass} md:col-span-2`}>Attributes JSON
            <textarea className={`${inputClass} font-mono`} rows={4} value={simulationForm.attributesJson} onChange={(event) => setSimulationForm((current) => ({ ...current, attributesJson: event.target.value }))} />
          </label>
        </div>
        {simulationError ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{simulationError}</div> : null}
        {simulationResult ? (
          <div className="mt-5 space-y-4 rounded-3xl border border-slate-200 bg-white p-5">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="text-xs font-black uppercase tracking-wide text-purple-700">Simulation Result</div>
                <h3 className="mt-1 text-xl font-black text-slate-950">{simulationResult.summary ?? '派工模擬完成'}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">sideEffectFree={String(simulationResult.sideEffectFree)} · createdArtifacts={(simulationResult.createdArtifacts ?? []).length}</p>
              </div>
              <StatusBadge status={simulationStatusTone(simulationResult)} />
            </div>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <MetricCard label="命中 Flow" value={simulationResult.matchedFlowId ?? '未命中'} detail={simulationResult.resolutionType ?? '-'} />
              <MetricCard label="命中 Rule" value={simulationResult.matchedRuleId ?? 'Default'} detail={simulationResult.matchedRuleId === 'SOURCE_DEFAULT' ? '使用預設工作池' : '特殊分類規則'} />
              <MetricCard label="目標 Pool" value={simulationResult.targetPoolCode ?? simulationResult.targetPoolId ?? '未解析'} detail={simulationResult.selectionStrategy ?? '-'} />
              <MetricCard label="候選 / 可接單" value={`${simulationResult.poolMemberCount ?? 0} / ${simulationResult.eligibleAgentCount ?? 0}`} detail={`預計 Agent：${simulationResult.selectedAgentId ?? '未選擇'}`} />
            </div>
            {simulationResult.blockerCode ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900"><b>{simulationResult.blockerCode}</b>：{simulationResult.blockerReason}</div> : null}
            <div className="overflow-hidden rounded-2xl border border-slate-200">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500">
                  <tr><th className="px-4 py-3">Agent</th><th className="px-4 py-3">狀態</th><th className="px-4 py-3">Score</th><th className="px-4 py-3">結果</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {(simulationResult.candidateEvidence ?? []).map((candidate) => (
                    <tr key={`candidate-${candidate.agentId}`}>
                      <td className="px-4 py-3 font-black text-slate-900">{candidate.agentId}</td>
                      <td className="px-4 py-3 font-bold text-slate-700">{candidate.status ?? '-'}</td>
                      <td className="px-4 py-3 font-bold text-slate-700">{candidate.score ?? '-'}</td>
                      <td className="px-4 py-3 font-bold text-slate-700">{candidateSummary(candidate)}</td>
                    </tr>
                  ))}
                  {(simulationResult.blockedCandidates ?? []).map((candidate) => (
                    <tr key={`blocked-${candidate.agentId}-${candidateSummary(candidate)}`} className="bg-rose-50">
                      <td className="px-4 py-3 font-black text-rose-900">{candidate.agentId}</td>
                      <td className="px-4 py-3 font-bold text-rose-800">Blocked</td>
                      <td className="px-4 py-3 font-bold text-rose-800">-</td>
                      <td className="px-4 py-3 font-bold text-rose-800">{candidateSummary(candidate)}</td>
                    </tr>
                  ))}
                  {!(simulationResult.candidateEvidence?.length || simulationResult.blockedCandidates?.length) ? (
                    <tr><td className="px-4 py-4 text-sm font-bold text-slate-500" colSpan={4}>沒有候選 Agent Evidence。</td></tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
        <div id="dispatch-workspace-real-test" className="mt-5 rounded-3xl border border-amber-200 bg-amber-50 p-5">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <div className="text-xs font-black uppercase tracking-wide text-amber-700">Real Test Event</div>
              <h3 className="mt-1 text-lg font-black text-slate-950">送出真實測試事件</h3>
              <p className="mt-2 text-sm leading-6 text-amber-900">此動作會建立正式事件與後續 Task／Assignment／Delivery 資料，請在 Simulation 通過後執行。</p>
            </div>
            {realTestResult ? <StatusBadge status={realTestResult.taskCreated ? 'TASK_CREATED' : realTestResult.decisionType ?? 'SENT'} /> : null}
          </div>
          {realTestError ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{realTestError}</div> : null}
          {realTestResult ? (
            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <MetricCard label="Event" value={realTestResult.eventId ?? '已送出'} detail={realTestResult.decisionType ?? realTestResult.reason ?? '-'} />
              <MetricCard label="Task" value={realTestResult.taskId ?? '未建立'} detail={realTestResult.taskCreated ? '正式 Task 已建立' : '請查看決策原因'} />
              <MetricCard label="Assignment" value={realTestResult.assignmentId ?? '未建立'} detail={realTestResult.selectedAgentId ? `Agent：${realTestResult.selectedAgentId}` : realTestResult.primaryReasonCode ?? '-'} />
              <MetricCard label="Delivery" value={realTestResult.dispatchRequestId ?? '未建立'} detail={realTestResult.dispatchRequestCreated ? 'Delivery request created' : realTestResult.nextAction ?? '-'} />
            </div>
          ) : null}
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button tone="primary" onClick={() => void runDispatchSimulation()} disabled={simulationBusy || busy || runtimeIssues.length > 0}>{simulationBusy ? '模擬中…' : '測試這個派工設定'}</Button>
          <Button tone="warning" onClick={() => void runRealTestEvent()} disabled={realTestBusy || busy || runtimeIssues.length > 0 || !simulationResult || Boolean(simulationResult.blockerCode)}>{realTestBusy ? '送出中…' : '送出真實測試事件'}</Button>
          <Button tone="primary" onClick={() => setFlowStatus('ACTIVE')} disabled={busy || configurationIssues.length > 0}>儲存並啟用</Button>
          <Link href={`/tasks${flow.flowId ? `?flowId=${encodeURIComponent(flow.flowId)}` : ''}`} className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 shadow-sm hover:bg-slate-50">查看相關 Task</Link>
        </div>
      </SectionShell></div>

      <PoolEditorDrawer
        open={poolEditorOpen}
        intent={poolIntent}
        editor={poolEditor}
        sourceSystems={sourceSystems}
        agents={agents}
        busy={busy}
        error={actionError}
        onChange={(patch) => setPoolEditor((current) => ({ ...current, ...patch }))}
        onClose={() => setPoolEditorOpen(false)}
        onSave={() => void savePool()}
      />
      <RuleEditorDrawer
        open={ruleEditorOpen}
        editor={ruleEditor}
        pools={pools}
        busy={busy}
        error={actionError}
        onChange={(patch) => setRuleEditor((current) => ({ ...current, ...patch }))}
        onClose={() => setRuleEditorOpen(false)}
        onSave={() => void saveRule()}
      />
    </div>
  );
}
