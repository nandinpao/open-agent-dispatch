'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError } from '@/lib/api/client';
import {
  runtimeConfigurationApi,
  type RuntimeConfigurationAuthorityRuntimeState,
  type RuntimeConfigurationCutoverStatus,
  type RuntimeConfigurationCutoverWaveSafetyAttestation,
  type RuntimeConfigurationCutoverWaveSafetyAttestationStatus,
  type RuntimeConfigurationCutoverWaveStatus,
} from '@/lib/api/runtimeConfigurationApi';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';

type ActionKind = 'ASSESS' | 'PREPARE' | 'FINALIZE' | 'CANCEL' | 'CERTIFY';
type EvidenceItem = { key: string; label: string; passed: boolean | null; value: string; detail?: string };
type UiFailure = { message: string; code?: string; correlationId?: string };

function Badge({ children, tone = 'slate' }: Readonly<{ children: React.ReactNode; tone?: 'slate' | 'emerald' | 'amber' | 'rose' | 'violet' | 'blue' }>) {
  const tones = {
    slate: 'border-slate-200 bg-slate-50 text-slate-700',
    emerald: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    amber: 'border-amber-200 bg-amber-50 text-amber-900',
    rose: 'border-rose-200 bg-rose-50 text-rose-800',
    violet: 'border-violet-200 bg-violet-50 text-violet-800',
    blue: 'border-blue-200 bg-blue-50 text-blue-800',
  } as const;
  return <span className={`inline-flex rounded-full border px-2 py-1 text-[11px] font-black ${tones[tone]}`}>{children}</span>;
}

function phaseTone(phase: string): 'slate' | 'emerald' | 'amber' | 'rose' | 'violet' {
  if (phase === 'FINALIZED') return 'emerald';
  if (phase === 'READY_TO_FINALIZE') return 'violet';
  if (phase === 'PREPARED_AWAITING_CONVERGENCE') return 'amber';
  if (phase === 'NOT_READY' || phase.includes('DRIFT') || phase.includes('INVALID')) return 'rose';
  return 'slate';
}

function authorityTone(state?: RuntimeConfigurationAuthorityRuntimeState | null): 'slate' | 'emerald' | 'amber' | 'rose' {
  if (state === 'ACTIVE') return 'emerald';
  if (state === 'STALE_LKG') return 'amber';
  if (state === 'EXPIRED' || state === 'INVALID' || state === 'MISSING') return 'rose';
  return 'slate';
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : undefined;
}
function numberValue(record: Record<string, unknown> | undefined, key: string): number | undefined {
  const value = record?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
function parseEvidence(attestation?: RuntimeConfigurationCutoverWaveSafetyAttestation | null): Record<string, unknown> | undefined {
  if (!attestation?.evidenceJson) return undefined;
  try { return asRecord(JSON.parse(attestation.evidenceJson)); } catch { return undefined; }
}
function platformEvidence(attestation?: RuntimeConfigurationCutoverWaveSafetyAttestation | null): EvidenceItem[] {
  const root = parseEvidence(attestation);
  const config = asRecord(root?.configurationRecovery);
  const gateway = asRecord(root?.gatewayHealth);
  const dispatch = asRecord(root?.dispatch);
  const reconciliation = asRecord(root?.reconciliation);
  const emergency = numberValue(config, 'activeEmergencyOverrides');
  const applyFailures = numberValue(config, 'recentApplyFailures');
  const drift = numberValue(config, 'platformApplyDrift');
  const distributionFailures = numberValue(config, 'activeRevisionDistributionFailures');
  const staleDistribution = numberValue(config, 'staleDistributionWork');
  const recovery = numberValue(config, 'w6CompleteRecoveryPoints');
  const expectedRecovery = numberValue(config, 'w6ExpectedRecoveryPoints');
  const missingLkg = numberValue(config, 'w6MissingLastKnownGoodNodeSnapshots');
  const onlineGateway = numberValue(gateway, 'onlineLeaseValid');
  const expiredGateway = numberValue(gateway, 'onlineLeaseExpired');
  const dispatchOldest = numberValue(dispatch, 'oldestAgeMillis');
  const dispatchSlo = numberValue(dispatch, 'sloSeconds');
  const reconciliationOldest = numberValue(reconciliation, 'oldestAgeMillis');
  const reconciliationSlo = numberValue(reconciliation, 'sloSeconds');
  const eqZero = (v?: number) => v === undefined ? null : v === 0;
  return [
    { key: 'override', label: 'Emergency override', passed: eqZero(emergency), value: emergency === undefined ? 'No evidence' : `${emergency} active` },
    { key: 'apply', label: 'Recent apply failures', passed: eqZero(applyFailures), value: applyFailures === undefined ? 'No evidence' : `${applyFailures} in 15 min` },
    { key: 'drift', label: 'Configuration drift', passed: eqZero(drift), value: drift === undefined ? 'No evidence' : `${drift} required nodes` },
    { key: 'distribution-failure', label: 'Distribution failures', passed: eqZero(distributionFailures), value: distributionFailures === undefined ? 'No evidence' : `${distributionFailures} active revisions` },
    { key: 'distribution-stale', label: 'Stale distribution work', passed: eqZero(staleDistribution), value: staleDistribution === undefined ? 'No evidence' : `${staleDistribution} stale jobs` },
    { key: 'recovery', label: 'Published recovery points', passed: recovery === undefined || expectedRecovery === undefined ? null : recovery === expectedRecovery, value: recovery === undefined ? 'No evidence' : `${recovery} / ${expectedRecovery ?? '?'}` },
    { key: 'lkg', label: 'Required-node LKG', passed: eqZero(missingLkg), value: missingLkg === undefined ? 'No evidence' : `${missingLkg} missing` },
    { key: 'gateway', label: 'Gateway leases', passed: onlineGateway === undefined || expiredGateway === undefined ? null : onlineGateway >= 1 && expiredGateway === 0, value: onlineGateway === undefined ? 'No evidence' : `${onlineGateway} healthy · ${expiredGateway ?? '?'} expired` },
    { key: 'dispatch', label: 'Dispatch backlog SLO', passed: dispatchOldest === undefined || dispatchSlo === undefined ? null : dispatchOldest <= dispatchSlo * 1000, value: dispatchOldest === undefined ? 'No evidence' : `${Math.round(dispatchOldest / 1000)}s oldest / ${dispatchSlo ?? '?'}s SLO` },
    { key: 'reconciliation', label: 'Reconciliation backlog SLO', passed: reconciliationOldest === undefined || reconciliationSlo === undefined ? null : reconciliationOldest <= reconciliationSlo * 1000, value: reconciliationOldest === undefined ? 'No evidence' : `${Math.round(reconciliationOldest / 1000)}s oldest / ${reconciliationSlo ?? '?'}s SLO` },
  ];
}

function durationLabel(ms: number): string {
  if (ms <= 0) return 'expired';
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remaining).padStart(2, '0')}`;
}
function compact(value?: string | null, size = 12): string {
  if (!value) return '—';
  return value.length <= size ? value : `${value.slice(0, size)}…`;
}
function failureFrom(error: unknown): UiFailure {
  if (error instanceof ApiError) return { message: error.message, code: error.code, correlationId: error.correlationId };
  return { message: error instanceof Error ? error.message : String(error) };
}

function ActionError({ failure }: Readonly<{ failure: UiFailure }>) {
  return <div className="rounded-xl border border-rose-300 bg-rose-50 p-3 text-sm text-rose-900">
    <div className="font-black">{failure.code ?? 'CUTOVER_ACTION_FAILED'}</div>
    <div className="mt-1">{failure.message}</div>
    {failure.correlationId && <div className="mt-2 font-mono text-[11px]">correlationId: {failure.correlationId}</div>}
  </div>;
}

export function CutoverWaveOperationsConsole({ onGovernanceReload }: Readonly<{ onGovernanceReload?: () => Promise<void> | void }>) {
  const entitlements = useUiEntitlements();
  const canView = actionAllowed(entitlements.value, 'runtime-configuration.cutover.view');
  const canAssess = actionAllowed(entitlements.value, 'runtime-configuration.cutover.assess');
  const canPrepare = actionAllowed(entitlements.value, 'runtime-configuration.cutover.prepare');
  const canFinalize = actionAllowed(entitlements.value, 'runtime-configuration.cutover.finalize');
  const canCancel = actionAllowed(entitlements.value, 'runtime-configuration.cutover.cancel');
  const canCertify = actionAllowed(entitlements.value, 'runtime-configuration.cutover.certify');
  const [waves, setWaves] = useState<RuntimeConfigurationCutoverWaveStatus[]>([]);
  const [selectedWaveId, setSelectedWaveId] = useState<string | null>(null);
  const [waveDetail, setWaveDetail] = useState<RuntimeConfigurationCutoverWaveStatus | null>(null);
  const [safety, setSafety] = useState<RuntimeConfigurationCutoverWaveSafetyAttestationStatus | null>(null);
  const [memberDetails, setMemberDetails] = useState<Record<string, RuntimeConfigurationCutoverStatus>>({});
  const [now, setNow] = useState(() => Date.now());
  const [loading, setLoading] = useState(false);
  const [pendingAction, setPendingAction] = useState<ActionKind | null>(null);
  const [actionWave, setActionWave] = useState<RuntimeConfigurationCutoverWaveStatus | null>(null);
  const [actionReason, setActionReason] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [failure, setFailure] = useState<UiFailure | null>(null);
  const [message, setMessage] = useState('');

  const loadWaves = useCallback(async () => {
    if (!canView) return;
    try { setWaves(await runtimeConfigurationApi.cutoverWaves()); setFailure(null); }
    catch (error) { setFailure(failureFrom(error)); }
  }, [canView]);

  const loadSelected = useCallback(async (waveId: string, showSpinner = false) => {
    if (!canView) return;
    if (showSpinner) setLoading(true);
    try {
      const detail = await runtimeConfigurationApi.cutoverWave(waveId);
      const safetyStatus = detail.safetyAttestationRequired
        ? await runtimeConfigurationApi.cutoverWaveSafetyAttestation(waveId)
        : null;
      const members = await Promise.all(detail.members.map(member => runtimeConfigurationApi.cutoverStatus(member.configSetId)));
      setWaveDetail(detail);
      setSafety(safetyStatus);
      setMemberDetails(Object.fromEntries(members.map(member => [member.configSetId, member])));
      setFailure(null);
    } catch (error) { setFailure(failureFrom(error)); }
    finally { if (showSpinner) setLoading(false); }
  }, [canView]);

  useEffect(() => { if (canView) void loadWaves(); }, [canView, loadWaves]);
  useEffect(() => {
    const id = globalThis.setInterval(() => setNow(Date.now()), 1000);
    return () => globalThis.clearInterval(id);
  }, []);
  useEffect(() => {
    if (!canView) return;
    const transitioning = waves.some(w => w.phase === 'PREPARED_AWAITING_CONVERGENCE' || w.phase === 'READY_TO_FINALIZE');
    if (!transitioning) return;
    const id = globalThis.setInterval(() => void loadWaves(), 5000);
    return () => globalThis.clearInterval(id);
  }, [canView, waves, loadWaves]);
  useEffect(() => {
    if (!selectedWaveId || !canView) return;
    void loadSelected(selectedWaveId, true);
    const id = globalThis.setInterval(() => void loadSelected(selectedWaveId), 3000);
    return () => globalThis.clearInterval(id);
  }, [selectedWaveId, canView, loadSelected]);

  const totalRuntimeKeys = useMemo(() => waves.reduce((sum, wave) => sum + wave.runtimeKeyCount, 0), [waves]);
  const evidence = useMemo(() => safety?.profile === 'PLATFORM_READINESS_RECOVERY' ? platformEvidence(safety.latestAttestation) : [], [safety]);
  const safetyRemainingMs = waveDetail?.safetyAttestationExpiresAt ? new Date(waveDetail.safetyAttestationExpiresAt).getTime() - now : 0;
  const safetyFresh = waveDetail?.safetyAttestationStatus === 'PASS' && safetyRemainingMs > 0;

  function openAction(kind: ActionKind, wave: RuntimeConfigurationCutoverWaveStatus) {
    setPendingAction(kind); setActionWave(wave); setActionReason(''); setConfirmation(''); setFailure(null); setMessage('');
  }
  function closeAction() { setPendingAction(null); setActionWave(null); setActionReason(''); setConfirmation(''); }

  async function executeAction() {
    if (!pendingAction || !actionWave) return;
    const reason = actionReason.trim();
    if (!reason) { setFailure({ message: 'Audit reason is required for this cutover action.', code: 'AUDIT_REASON_REQUIRED' }); return; }
    if (pendingAction === 'FINALIZE' && !safetyFresh) {
      setFailure({ message: 'Safety evidence is no longer fresh. Re-assess readiness before finalization.', code: 'CUTOVER_SAFETY_REASSESS_REQUIRED' }); return;
    }
    if (pendingAction === 'FINALIZE' && confirmation.trim() !== actionWave.waveId) {
      setFailure({ message: `Type ${actionWave.waveId} exactly before finalization.`, code: 'CUTOVER_CONFIRMATION_REQUIRED' }); return;
    }
    setLoading(true); setFailure(null); setMessage('');
    try {
      if (pendingAction === 'ASSESS') await runtimeConfigurationApi.assessCutoverWaveSafety(actionWave.waveId, reason);
      else if (pendingAction === 'PREPARE') await runtimeConfigurationApi.prepareCutoverWave(actionWave.waveId, reason);
      else if (pendingAction === 'FINALIZE') await runtimeConfigurationApi.finalizeCutoverWave(actionWave.waveId, reason);
      else if (pendingAction === 'CANCEL') await runtimeConfigurationApi.cancelCutoverWave(actionWave.waveId, reason);
      else await runtimeConfigurationApi.certifyCutoverWave(actionWave.waveId, reason);
      setMessage(`${actionWave.waveId} ${pendingAction.toLowerCase()} completed.`);
      closeAction();
      await loadWaves();
      if (selectedWaveId === actionWave.waveId) await loadSelected(actionWave.waveId);
      await onGovernanceReload?.();
    } catch (error) { setFailure(failureFrom(error)); }
    finally { setLoading(false); }
  }

  if (!canView) {
    return <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-xs font-black uppercase tracking-wide text-slate-500">Single Authority cutover waves</div>
      <div className="mt-2 text-sm text-slate-600">Cutover operations are hidden because this session does not have <code>configuration.cutover.view</code>.</div>
    </section>;
  }

  return <>
    <section className="rounded-3xl border border-violet-200 bg-violet-50 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-violet-700">Single Authority cutover waves</div>
          <h2 className="mt-1 text-xl font-black text-slate-950">Ordered cutover with live readiness and node authority evidence</h2>
          <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">Prepare and finalize are separate operations. Critical waves require fresh safety evidence, exact Authority Contract v2 convergence, and ACTIVE remote signed-snapshot authority. Open a wave to inspect evidence before acting.</p>
        </div>
        <div className="flex flex-wrap gap-2"><Badge tone="violet">{waves.length} waves</Badge><Badge>{totalRuntimeKeys} runtime keys</Badge></div>
      </div>
      <div className="mt-4 grid gap-3 xl:grid-cols-2">
        {waves.map(wave => {
          const remaining = wave.safetyAttestationExpiresAt ? new Date(wave.safetyAttestationExpiresAt).getTime() - now : 0;
          const effectiveSafety = wave.safetyAttestationStatus === 'PASS' && remaining <= 0 ? 'EXPIRED' : (wave.safetyAttestationStatus ?? 'MISSING');
          return <button key={wave.waveId} type="button" onClick={() => setSelectedWaveId(wave.waveId)} className="rounded-2xl border border-violet-200 bg-white p-4 text-left shadow-sm transition hover:border-violet-400 hover:shadow-md">
            <div className="flex items-start justify-between gap-3">
              <div><div className="text-xs font-black uppercase tracking-wide text-violet-600">{wave.waveId} · Wave {wave.sequenceNo}</div><div className="mt-1 font-black text-slate-950">{wave.displayName}</div><div className="mt-1 text-xs text-slate-500">{wave.configSetCount} Config Sets · {wave.runtimeKeyCount} keys · Authority Contract v{wave.requiredAuthorityContractVersion}</div></div>
              <div className="flex flex-col items-end gap-1"><Badge tone={wave.riskTier === 'CRITICAL' ? 'rose' : wave.riskTier === 'HIGH' ? 'amber' : 'slate'}>{wave.riskTier}</Badge><Badge tone={phaseTone(wave.phase)}>{wave.phase.replaceAll('_', ' ')}</Badge></div>
            </div>
            <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
              <div className="rounded-xl bg-slate-50 p-3"><div className="text-slate-500">Node convergence</div><div className="mt-1 text-base font-black text-slate-950">{wave.convergedNodeCount} / {wave.requiredNodeCount}</div></div>
              <div className="rounded-xl bg-slate-50 p-3"><div className="text-slate-500">Safety</div><div className="mt-1 flex items-center gap-2"><Badge tone={effectiveSafety === 'PASS' ? (remaining < 60_000 ? 'amber' : 'emerald') : 'rose'}>{effectiveSafety}</Badge>{wave.safetyAttestationStatus === 'PASS' && <span className="font-mono text-[11px]">{durationLabel(remaining)}</span>}</div></div>
            </div>
            {wave.blockers.length > 0 && <div className="mt-3 text-xs font-bold text-rose-700">{wave.blockers.length} blocker(s) · open for details</div>}
          </button>;
        })}
      </div>
    </section>

    {message && <div className="rounded-xl border border-emerald-300 bg-emerald-50 p-3 text-sm font-bold text-emerald-800">{message}</div>}
    {failure && !pendingAction && <ActionError failure={failure} />}

    {selectedWaveId && <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/40" role="dialog" aria-modal="true" aria-label="Cutover wave details">
      <button type="button" aria-label="Close cutover wave details" className="absolute inset-0 cursor-default" onClick={() => setSelectedWaveId(null)} />
      <aside className="relative z-10 h-full w-full max-w-5xl overflow-y-auto bg-slate-50 shadow-2xl">
        <div className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 px-5 py-4 backdrop-blur">
          <div className="flex items-start justify-between gap-3">
            <div><div className="text-xs font-black uppercase tracking-wide text-violet-700">Cutover Wave Detail</div><h2 className="mt-1 text-xl font-black text-slate-950">{waveDetail?.waveId ?? selectedWaveId} · {waveDetail?.displayName ?? 'Loading…'}</h2></div>
            <button type="button" onClick={() => setSelectedWaveId(null)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-black text-slate-700">Close</button>
          </div>
        </div>
        <div className="space-y-4 p-5">
          {loading && !waveDetail && <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500">Loading cutover evidence…</div>}
          {waveDetail && <>
            <div className="grid gap-3 md:grid-cols-4">
              <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs text-slate-500">Phase</div><div className="mt-2"><Badge tone={phaseTone(waveDetail.phase)}>{waveDetail.phase.replaceAll('_', ' ')}</Badge></div></div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs text-slate-500">Scope</div><div className="mt-1 text-lg font-black">{waveDetail.configSetCount} sets / {waveDetail.runtimeKeyCount} keys</div></div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs text-slate-500">Required nodes</div><div className="mt-1 text-lg font-black">{waveDetail.convergedNodeCount} / {waveDetail.requiredNodeCount}</div></div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs text-slate-500">Risk</div><div className="mt-2"><Badge tone={waveDetail.riskTier === 'CRITICAL' ? 'rose' : 'amber'}>{waveDetail.riskTier}</Badge></div></div>
            </div>

            {waveDetail.predecessorWaveId && <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm"><span className="font-black">Dependency:</span> {waveDetail.predecessorWaveId} · certification <Badge tone={waveDetail.predecessorCertificationStatus === 'PASS' ? 'emerald' : 'rose'}>{waveDetail.predecessorCertificationStatus ?? 'MISSING'}</Badge></div>}

            {waveDetail.safetyAttestationRequired && <section className={`rounded-2xl border p-4 ${safetyFresh ? 'border-emerald-200 bg-emerald-50' : 'border-amber-300 bg-amber-50'}`}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div><div className="text-xs font-black uppercase tracking-wide">Platform readiness</div><div className="mt-2 flex flex-wrap items-center gap-2"><Badge tone={safetyFresh ? 'emerald' : 'rose'}>{safetyFresh ? 'PASS' : (waveDetail.safetyAttestationStatus === 'PASS' ? 'EXPIRED' : waveDetail.safetyAttestationStatus ?? 'MISSING')}</Badge>{waveDetail.safetyAttestationExpiresAt && <span className={`font-mono text-sm font-black ${safetyRemainingMs < 60_000 ? 'text-amber-900' : ''}`}>{durationLabel(safetyRemainingMs)}</span>}</div></div>
                {canAssess && waveDetail.phase !== 'FINALIZED' && <button type="button" onClick={() => openAction('ASSESS', waveDetail)} className="rounded-lg border border-amber-400 bg-white px-3 py-2 text-xs font-black text-amber-900">Re-assess safety</button>}
              </div>
              {safety?.latestAttestation && <div className="mt-3 text-xs text-slate-700">Captured {new Date(safety.latestAttestation.capturedAt).toLocaleString()} by <b>{safety.latestAttestation.attestedBy}</b> · attestation <span className="font-mono">{safety.latestAttestation.attestationId}</span></div>}
              {evidence.length > 0 && <div className="mt-4 grid gap-2 md:grid-cols-2">{evidence.map(item => <div key={item.key} className="flex items-start justify-between gap-3 rounded-xl border border-white/70 bg-white/80 p-3"><div><div className="text-xs font-black text-slate-900">{item.label}</div><div className="mt-1 text-[11px] text-slate-600">{item.value}</div></div><Badge tone={item.passed === true ? 'emerald' : item.passed === false ? 'rose' : 'slate'}>{item.passed === true ? 'PASS' : item.passed === false ? 'FAIL' : 'UNKNOWN'}</Badge></div>)}</div>}
              {safety?.blockers?.length ? <div className="mt-3 space-y-1">{safety.blockers.map(blocker => <div key={blocker} className="rounded-lg bg-rose-100 px-2 py-1 font-mono text-[11px] text-rose-800">{blocker}</div>)}</div> : null}
            </section>}

            <section className="rounded-2xl border border-slate-200 bg-white p-4">
              <div className="flex items-center justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Required-node convergence</div><h3 className="mt-1 font-black text-slate-950">Live Runtime Authority evidence</h3></div><Badge tone={waveDetail.convergedNodeCount === waveDetail.requiredNodeCount ? 'emerald' : 'amber'}>{waveDetail.convergedNodeCount} / {waveDetail.requiredNodeCount}</Badge></div>
              <div className="mt-4 space-y-3">{waveDetail.members.map(member => {
                const detail = memberDetails[member.configSetId];
                return <div key={member.configSetId} className="overflow-hidden rounded-xl border border-slate-200">
                  <div className="flex flex-wrap items-center justify-between gap-2 bg-slate-50 px-3 py-3"><div><div className="font-mono text-xs font-black text-slate-950">{member.setKey}</div><div className="mt-1 text-[11px] text-slate-500">{member.actualRuntimeKeyCount} keys · {member.phase.replaceAll('_', ' ')}</div></div><Badge tone={member.convergedNodeCount === member.requiredNodeCount ? 'emerald' : 'amber'}>{member.convergedNodeCount} / {member.requiredNodeCount}</Badge></div>
                  <div className="overflow-x-auto"><table className="min-w-full text-left text-xs"><thead className="bg-white text-[10px] uppercase tracking-wide text-slate-500"><tr><th className="px-3 py-2">Node</th><th className="px-3 py-2">Apply</th><th className="px-3 py-2">Authority</th><th className="px-3 py-2">Revision / Fingerprint</th><th className="px-3 py-2">Observed / Expiry</th><th className="px-3 py-2">Converged</th></tr></thead><tbody>{detail?.nodes?.map(node => <tr key={`${member.configSetId}:${node.nodeId}`} className="border-t border-slate-100"><td className="px-3 py-3"><div className="font-black">{node.nodeId}</div><div className="text-[10px] text-slate-500">{node.nodeRole} · contract v{node.supportedAuthorityContractVersion}</div></td><td className="px-3 py-3"><Badge tone={node.applyState === 'APPLIED' ? 'emerald' : node.applyState === 'FAILED' || node.applyState === 'STALE' ? 'rose' : 'amber'}>{node.applyState}</Badge></td><td className="px-3 py-3"><Badge tone={authorityTone(node.authorityRuntimeState)}>{node.nodeRole === 'CORE' && !node.authorityRuntimeState ? 'CORE LOCAL' : node.authorityRuntimeState ?? 'NOT REPORTED'}</Badge></td><td className="px-3 py-3 font-mono text-[10px]"><div>{compact(node.appliedRevisionId, 16)}</div><div className="mt-1 text-slate-500">{compact(node.snapshotFingerprint, 16)}</div></td><td className="px-3 py-3 text-[10px] text-slate-600"><div>{node.authorityObservedAt ? new Date(node.authorityObservedAt).toLocaleTimeString() : '—'}</div><div className="mt-1">{node.snapshotExpiresAt ? `exp ${new Date(node.snapshotExpiresAt).toLocaleTimeString()}` : '—'}</div></td><td className="px-3 py-3"><Badge tone={node.converged ? 'emerald' : 'rose'}>{node.converged ? 'YES' : 'NO'}</Badge></td></tr>) ?? <tr><td colSpan={6} className="px-3 py-4 text-slate-500">Node evidence is loading.</td></tr>}</tbody></table></div>
                  {detail?.blockers?.length ? <details className="border-t border-slate-100 px-3 py-2"><summary className="cursor-pointer text-[11px] font-black text-rose-700">{detail.blockers.length} Config Set blocker(s)</summary><div className="mt-2 space-y-1">{detail.blockers.map(blocker => <div key={blocker} className="font-mono text-[10px] text-rose-700">{blocker}</div>)}</div></details> : null}
                </div>;
              })}</div>
            </section>

            {waveDetail.blockers.length > 0 && <section className="rounded-2xl border border-rose-200 bg-rose-50 p-4"><div className="text-xs font-black uppercase tracking-wide text-rose-700">Wave blockers</div><div className="mt-3 space-y-1">{waveDetail.blockers.map(blocker => <div key={blocker} className="rounded-lg bg-white px-2 py-1 font-mono text-[11px] text-rose-800">{blocker}</div>)}</div></section>}

            <section className="rounded-2xl border border-violet-200 bg-violet-50 p-4">
              <div className="text-xs font-black uppercase tracking-wide text-violet-700">Available actions</div>
              <div className="mt-3 flex flex-wrap gap-2">
                {canAssess && waveDetail.safetyAttestationRequired && waveDetail.phase !== 'FINALIZED' && <button type="button" onClick={() => openAction('ASSESS', waveDetail)} className="rounded-lg border border-amber-300 bg-white px-3 py-2 text-xs font-black text-amber-900">Assess safety</button>}
                {canPrepare && waveDetail.phase === 'READY_TO_PREPARE' && <button type="button" onClick={() => openAction('PREPARE', waveDetail)} className="rounded-lg bg-violet-700 px-3 py-2 text-xs font-black text-white">Prepare wave</button>}
                {canFinalize && waveDetail.phase === 'READY_TO_FINALIZE' && safetyFresh && <button type="button" onClick={() => openAction('FINALIZE', waveDetail)} className="rounded-lg bg-emerald-700 px-3 py-2 text-xs font-black text-white">Finalize wave</button>}
                {canCancel && (waveDetail.phase === 'PREPARED_AWAITING_CONVERGENCE' || waveDetail.phase === 'READY_TO_FINALIZE') && <button type="button" onClick={() => openAction('CANCEL', waveDetail)} className="rounded-lg border border-rose-300 bg-white px-3 py-2 text-xs font-black text-rose-700">Cancel prepared wave</button>}
                {canCertify && waveDetail.phase === 'FINALIZED' && <button type="button" onClick={() => openAction('CERTIFY', waveDetail)} className="rounded-lg border border-emerald-300 bg-white px-3 py-2 text-xs font-black text-emerald-700">Certify convergence</button>}
              </div>
              {!canAssess && !canPrepare && !canFinalize && !canCancel && !canCertify && <div className="mt-2 text-xs text-slate-600">Read-only. This session has cutover view permission but no cutover mutation authority.</div>}
              {canFinalize && waveDetail.phase !== 'READY_TO_FINALIZE' && <div className="mt-3 text-xs text-slate-600">Finalize is unavailable until the server reports <b>READY_TO_FINALIZE</b>; blockers above explain why.</div>}
              {canFinalize && waveDetail.phase === 'READY_TO_FINALIZE' && !safetyFresh && <div className="mt-3 rounded-lg border border-amber-300 bg-amber-100 px-3 py-2 text-xs font-bold text-amber-900">Safety evidence expired while the wave was ready. Re-assess safety before Finalize.</div>}
            </section>
          </>}
          {failure && !pendingAction && <ActionError failure={failure} />}
        </div>
      </aside>
    </div>}

    {pendingAction && actionWave && <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/50 p-4" role="dialog" aria-modal="true" aria-label={`${pendingAction} cutover wave`}>
      <div className="w-full max-w-2xl rounded-3xl border border-slate-200 bg-white p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-violet-700">{pendingAction} cutover wave</div><h3 className="mt-1 text-xl font-black text-slate-950">{actionWave.waveId} · {actionWave.displayName}</h3></div><button type="button" onClick={closeAction} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-black">Close</button></div>
        {pendingAction === 'FINALIZE' && <div className="mt-4 rounded-2xl border border-rose-300 bg-rose-50 p-4 text-sm text-rose-950"><div className="font-black">Critical authority transition</div><div className="mt-1">Finalization changes {actionWave.runtimeKeyCount} Runtime Configuration keys across {actionWave.configSetCount} Config Sets to Single Runtime Authority. YAML / ENV is no longer a valid runtime fallback for migrated keys.</div><div className="mt-3 grid gap-2 sm:grid-cols-3"><div className="rounded-lg bg-white p-2"><div className="text-[10px] text-slate-500">Nodes</div><b>{actionWave.convergedNodeCount}/{actionWave.requiredNodeCount}</b></div><div className="rounded-lg bg-white p-2"><div className="text-[10px] text-slate-500">Safety</div><b>{safetyFresh ? `PASS ${durationLabel(safetyRemainingMs)}` : 'NOT FRESH'}</b></div><div className="rounded-lg bg-white p-2"><div className="text-[10px] text-slate-500">Contract</div><b>v{actionWave.requiredAuthorityContractVersion}</b></div></div></div>}
        <label className="mt-4 block text-xs font-black uppercase tracking-wide text-slate-600">Audit reason</label>
        <textarea value={actionReason} onChange={event => setActionReason(event.target.value)} rows={3} placeholder="Describe the operational evidence and reason for this action." className="mt-2 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" />
        {pendingAction === 'FINALIZE' && <><label className="mt-4 block text-xs font-black uppercase tracking-wide text-rose-700">Type {actionWave.waveId} to confirm</label><input value={confirmation} onChange={event => setConfirmation(event.target.value)} className="mt-2 w-full rounded-xl border border-rose-300 px-3 py-2 font-mono text-sm" /></>}
        {failure && <div className="mt-4"><ActionError failure={failure} /></div>}
        <div className="mt-5 flex justify-end gap-2"><button type="button" onClick={closeAction} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-black">Cancel</button><button type="button" disabled={loading || !actionReason.trim() || (pendingAction === 'FINALIZE' && confirmation.trim() !== actionWave.waveId)} onClick={() => void executeAction()} className={`rounded-lg px-4 py-2 text-sm font-black text-white disabled:bg-slate-300 ${pendingAction === 'FINALIZE' ? 'bg-rose-700' : 'bg-violet-700'}`}>{loading ? 'Working…' : pendingAction}</button></div>
      </div>
    </div>}
  </>;
}
