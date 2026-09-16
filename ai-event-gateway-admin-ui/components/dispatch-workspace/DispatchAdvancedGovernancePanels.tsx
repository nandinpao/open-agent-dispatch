'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { dispatchAdminApi } from '@/lib/api/domains/dispatchAdminApi';
import { ApiError } from '@/lib/api/client';
import type {
  CoreAgentCapabilityAssignment,
  CoreAgentAdvisoryRecommendation,
  CoreAdvancedSelectionStrategyContract,
  CoreAgentCapabilityCatalog,
  CoreCanonicalCapabilityDefinition,
  CoreAgentPoolView,
  CoreAgentQualityMetricsWindow,
  CoreDispatchFlowAgentOptionView,
} from '@/lib/types/core';

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';

function MetricCard({ label, value, detail }: Readonly<{ label: string; value: string | number; detail?: string }>) {
  return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">{label}</div><div className="mt-1 break-all text-base font-black text-slate-950">{value}</div>{detail ? <div className="mt-1 text-xs font-bold leading-5 text-slate-500">{detail}</div> : null}</div>;
}

function normalizeCode(value: string | undefined | null): string {
  return String(value ?? '').trim().toUpperCase().replace(/[^A-Z0-9_]+/g, '_').replace(/^_+|_+$/g, '');
}

function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback;
  if (error instanceof Error) return error.message;
  return fallback;
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

export function CapabilityCandidateLookupPanel({
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
          <h3 className="mt-1 text-lg font-black text-slate-950">Pool Capability Candidate Lookup</h3>
          <p className="mt-2 text-sm leading-6 text-indigo-900">Use this read-only lookup to inspect which Pool members have a Core APPROVED assignment for the selected Capability. Actual routing uses Source Flow / Agent Pool as the candidate boundary, then Required Capability as a blocking qualification gate, followed by runtime readiness/capacity and final scoring.</p>
        </div>
        <StatusBadge status="READ_ONLY" label="READ-ONLY LOOKUP" />
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <label className={`${labelClass} md:col-span-2`}>Capability
          <input className={inputClass} list="dispatch-capability-registry-codes" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search capability code" />
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
            <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Agent</th><th className="px-4 py-3">Pool</th><th className="px-4 py-3">Runtime</th><th className="px-4 py-3">Quality Observation</th><th className="px-4 py-3">Qualification Evidence</th></tr></thead>
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
              {!suggestions.length ? <tr><td colSpan={5} className="px-4 py-4 text-sm font-bold text-slate-500">No Core APPROVED Agent capability assignment matches this capability in the current candidate view.</td></tr> : null}
            </tbody>
          </table>
        </div>
      ) : <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 text-sm font-bold text-slate-500">Choose a capability code to review matching Agent declarations.</div>}
    </div>
  );
}


function recommendationLabel(type: string | undefined): string {
  switch (String(type ?? '').toUpperCase()) {
    case 'ADD_AGENT_TO_POOL': return 'Add Agent to Pool';
    case 'ADJUST_AGENT_WEIGHT': return 'Adjust Agent Weight';
    case 'INCREASE_POOL_CAPACITY': return 'Increase Pool capacity';
    case 'POOL_CAPACITY_RISK': return 'Pool capacity risk';
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

export function AdvisoryRecommendationPanel({
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
        ? await dispatchAdminApi.generateAdvisoryRecommendations(targetPoolId, '', '24h', scopedTenantId ?? '', 100)
        : await dispatchAdminApi.getAdvisoryRecommendations(targetPoolId, '', '', '24h', scopedTenantId ?? '', 100);
      setRecommendations(result);
      setMessage(generate ? 'Advisory recommendations generated.' : null);
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Failed to load advisory recommendations.'));
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
        ? await dispatchAdminApi.acceptAdvisoryRecommendation(recommendation.recommendationId, body, scopedTenantId ?? '')
        : await dispatchAdminApi.rejectAdvisoryRecommendation(recommendation.recommendationId, body, scopedTenantId ?? '');
      setRecommendations((current) => current.map((item) => item.recommendationId === updated.recommendationId ? updated : item));
      setMessage(decision === 'accept'
        ? 'Recommendation accepted for explicit configuration review. No routing change was applied automatically.'
        : 'Recommendation rejected. The decision was recorded for audit and no routing change was applied.');
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Failed to record the recommendation decision.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mt-5 rounded-3xl border border-amber-200 bg-amber-50 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-amber-700">Advisory Recommendation</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">Details</h3>
          <p className="mt-2 text-sm leading-6 text-amber-900">Recommendations are advisory only. Accept records an explicit configuration-review intent; it does not modify Agent Pool membership, weight, capacity, Runtime Eligibility, or Selection Strategy.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status="ADVISORY_ONLY" label="ADVISORY ONLY" />
          <Button size="xs" onClick={() => { void loadRecommendations(true); }} disabled={loading}>{loading ? 'Generating...' : 'Generate Recommendations'}</Button>
        </div>
      </div>
      {message ? <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-bold text-emerald-900">{message}</div> : null}
      {error ? <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-900">{error}</div> : null}
      <div className="mt-4 overflow-hidden rounded-2xl border border-amber-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-black uppercase tracking-wide text-slate-500"><tr><th className="px-4 py-3">Details</th><th className="px-4 py-3">Evidence</th><th className="px-4 py-3">Suggested Change</th><th className="px-4 py-3">Status</th><th className="px-4 py-3">Audit</th><th className="px-4 py-3">Actions</th></tr></thead>
          <tbody className="divide-y divide-slate-100">
            {recommendations.map((recommendation) => (
              <tr key={recommendation.recommendationId}>
                <td className="px-4 py-3"><div className="font-black text-slate-900">{recommendationLabel(recommendation.recommendationType)}</div><div className="text-xs leading-5 text-slate-500">{recommendation.reason ?? recommendation.recommendationId}</div></td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">window={recommendation.evidenceWindow ?? '24h'}<br />confidence={confidenceLabel(recommendation.confidence)}<br />agent={recommendation.targetAgentId ?? '-'}<br />pool={recommendation.targetPoolId ?? '-'}</td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">{suggestedChangeText(recommendation)}<br /><b>autoApply=false</b><br />routingImpact={recommendation.routingImpact ?? 'NONE'}</td>
                <td className="px-4 py-3"><StatusBadge status={recommendation.status ?? 'OPEN'} /></td>
                <td className="px-4 py-3 text-xs leading-5 text-slate-600">accepted={recommendation.acceptedBy ?? '-'}<br />rejected={recommendation.rejectedBy ?? '-'}<br />reason={recommendation.decisionReason ?? '-'}</td>
                <td className="px-4 py-3">
                  {String(recommendation.status ?? 'OPEN').toUpperCase() === 'OPEN' ? <div className="flex flex-wrap gap-2"><Button size="xs" onClick={() => { void decide(recommendation, 'accept'); }} disabled={loading}>Accept</Button><Button size="xs" onClick={() => { void decide(recommendation, 'reject'); }} disabled={loading}>Reject</Button></div> : <span className="text-xs font-bold text-slate-500">Decision recorded</span>}
                </td>
              </tr>
            ))}
            {!recommendations.length ? <tr><td colSpan={6} className="px-4 py-4 text-sm font-bold text-slate-500">No advisory recommendations are currently available. Capability and Quality Observation remain advisory-only evidence.</td></tr> : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}



export function ExplicitCapabilityPolicyPanel({
  selectedPool,
  scopedTenantId,
  capabilityCatalog,
}: Readonly<{
  selectedPool?: CoreAgentPoolView | null;
  scopedTenantId?: string;
  capabilityCatalog: CoreCanonicalCapabilityDefinition[];
}>) {
  const activeCapabilityCount = capabilityCatalog.filter((item) => String(item.status ?? '').toUpperCase() === 'ACTIVE').length;
  return (
    <div className="mt-5 rounded-3xl border border-indigo-200 bg-indigo-50 p-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-indigo-700">Canonical Capability Eligibility</div>
          <h3 className="mt-1 text-lg font-black text-slate-950">Required Capability is part of the Task contract</h3>
          <p className="mt-2 text-sm leading-6 text-indigo-950">The Dispatch Flow selects the Agent Pool. The Task&apos;s Required Capability then filters Pool members by Core-approved Agent Capability. Runtime readiness and capacity are evaluated next, and Routing Score chooses only among eligible Agents.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge status="REQUIRED" label="CAPABILITY REQUIRED" />
          <StatusBadge status="CANONICAL" label="TASK AUTHORITY" />
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <MetricCard label="1. Candidate boundary" value={selectedPool?.poolName ?? selectedPool?.poolCode ?? selectedPool?.poolId ?? 'Flow Agent Pool'} detail="Source Flow / Agent Pool selects where to look." />
        <MetricCard label="2. Qualification" value="Required Capability" detail="ALL Task-required Capabilities must be Core-approved for the Agent." />
        <MetricCard label="3. Availability" value="Runtime Eligibility" detail="Connection, profile, credential and capacity gates remain independent." />
        <MetricCard label="4. Final choice" value="Routing Score" detail="Score ranks only candidates that already passed eligibility." />
      </div>
      <div className="mt-4 rounded-2xl border border-white bg-white p-4 text-sm leading-6 text-slate-700">
        <b>Pool Capability Policy retired:</b> the old ADVISORY/REQUIRED Pool editor used an in-memory registry and could create a second Capability authority. It is no longer editable and no longer participates in routing. Configure Required Capability on the Dispatch Flow / Task Definition instead.
        <div className="mt-2 text-xs font-bold text-slate-500">Tenant {scopedTenantId || '-'} · Active canonical Capabilities {activeCapabilityCount}</div>
      </div>
    </div>
  );
}


export function AdvancedSelectionStrategyContractPanel() {
  const [contracts, setContracts] = useState<CoreAdvancedSelectionStrategyContract[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadContracts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setContracts(await dispatchAdminApi.getAdvancedSelectionStrategyContracts());
    } catch (caught) {
      setError(apiErrorMessage(caught, 'Failed to load advanced selection strategy contracts.'));
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
          <h3 className="mt-1 text-lg font-black text-slate-950">Contract-only strategies</h3>
          <p className="mt-2 text-sm leading-6 text-slate-700">ROUND_ROBIN, LOCAL_FIRST, QUALITY_SCORE, COST_AWARE, and SLA_AWARE are contract-only. Current selection remains LOWEST_LOAD, WEIGHTED_SCORE, or MANUAL_ONLY until readiness, fallback, Assignment Evidence, and Simulation requirements are satisfied.</p>
        </div>
        <StatusBadge status="CONTRACT_ONLY" label="CONTRACT ONLY" />
      </div>
      {loading ? <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 text-sm font-bold text-slate-600">Loading strategy contracts…</div> : null}
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
            {!contracts.length && !loading ? <tr><td colSpan={4} className="px-4 py-4 text-sm font-bold text-slate-500">No advanced strategy contracts are currently available.</td></tr> : null}
          </tbody>
        </table>
      </div>
      <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b>Activation guard:</b> Agent Quality Observation does not automatically become a Selection Strategy. QUALITY_SCORE requires an explicit formula, Simulation breakdown, and Assignment Evidence before enablement.</div>
    </div>
  );
}

