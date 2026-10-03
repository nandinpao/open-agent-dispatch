'use client';

import { Button } from '@/components/ui/Button';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import {
  ConfigurationImpactPreview,
  ConfigurationLifecycleBoundaryNotice,
  ConfigurationOperationOutcome,
  ConfigurationPurposePanel,
  ConfigurationValidationPanel,
} from '@/components/configuration/ConfigurationConfidence';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import type {
  CoreCanonicalCapabilityDefinition,
  CoreCapabilityBinding,
  CoreProviderEligibilityObservation,
  CoreProviderRoutingDecision,
  CoreRoutingProfile,
  CoreRoutingProfileVersion,
} from '@/lib/types/core';

const PAGE_SIZE = 50;
const PROFILE_TYPES = ['FAST', 'LOW_COST', 'BALANCED', 'HIGH_ACCURACY', 'CRITICAL', 'CUSTOM'];
const WEIGHT_KEYS = ['QUALITY', 'RELIABILITY', 'LATENCY', 'COST', 'LOAD', 'LOCALITY'];

function message(cause: unknown, fallback: string) { return cause instanceof Error && cause.message ? cause.message : fallback; }
function csv(value: string): string[] { return [...new Set(value.split(',').map((item) => item.trim()).filter(Boolean))]; }
function n(value: string): number | undefined { if (!value.trim()) return undefined; const parsed = Number(value); return Number.isFinite(parsed) ? parsed : undefined; }
function emptyProfile(): CoreRoutingProfile {
  return {
    profileId: '', displayName: '', description: '', profileType: 'BALANCED', status: 'DRAFT',
    weights: { QUALITY: 35, RELIABILITY: 25, LATENCY: 20, COST: 10, LOAD: 10, LOCALITY: 0 },
    maxObservationAgeSeconds: 300,
  };
}

export function ProviderRoutingConsole() {
  const { activeTenantId: tenantId } = useAuth();
  const entitlements = useUiEntitlements();
  const canManage = actionAllowed(entitlements.value, 'provider-routing.manage');
  const [profiles, setProfiles] = useState<CoreRoutingProfile[]>([]);
  const [profileSearch, setProfileSearch] = useState('');
  const [profileDraft, setProfileDraft] = useState<CoreRoutingProfile>(emptyProfile());
  const [profileReason, setProfileReason] = useState('');
  const [profileVersions, setProfileVersions] = useState<CoreRoutingProfileVersion[]>([]);
  const [capabilities, setCapabilities] = useState<CoreCanonicalCapabilityDefinition[]>([]);
  const [capabilitySearch, setCapabilitySearch] = useState('');
  const [capabilityCode, setCapabilityCode] = useState('');
  const [operation, setOperation] = useState('');
  const [bindings, setBindings] = useState<CoreCapabilityBinding[]>([]);
  const [bindingId, setBindingId] = useState('');
  const selectedBinding = bindings.find((item) => item.bindingId === bindingId);
  const [observations, setObservations] = useState<CoreProviderEligibilityObservation[]>([]);
  const [observation, setObservation] = useState<CoreProviderEligibilityObservation>({
    bindingId: '', providerId: '', observationSource: 'SYNTHETIC_PREVIEW', available: true, healthy: true, capacityAvailable: true,
    qualityScore: 90, reliabilityScore: 95, p95LatencyMs: 800, estimatedCost: 0.01, loadPercent: 20, localityScore: 80,
  });
  const [authorizationIds, setAuthorizationIds] = useState('');
  const [routingDecision, setRoutingDecision] = useState<CoreProviderRoutingDecision>();
  const [recentDecisions, setRecentDecisions] = useState<CoreProviderRoutingDecision[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [profileActivationAcknowledged, setProfileActivationAcknowledged] = useState(false);
  const [profileOutcome, setProfileOutcome] = useState<{ outcome: 'INFO' | 'APPLY_FAILED'; title: string; message: string; safetyNote: string }>();

  const selectedCapability = useMemo(() => capabilities.find((item) => item.capabilityCode === capabilityCode), [capabilities, capabilityCode]);
  const persistedProfile = useMemo(() => profiles.find((item) => item.profileId === profileDraft.profileId), [profiles, profileDraft.profileId]);
  const activeProfileCount = useMemo(() => profiles.filter((item) => item.status === 'ACTIVE').length, [profiles]);
  const activeSave = profileDraft.status === 'ACTIVE';
  const routingRisk = activeSave ? 'HIGH' as const : profileDraft.status === 'SUSPENDED' || profileDraft.status === 'RETIRED' ? 'MEDIUM' as const : 'LOW' as const;

  const loadProfiles = useCallback(async () => {
    if (!tenantId) return;
    try { setProfiles(await coreAdminApi.getProviderRoutingProfiles(undefined, profileSearch || undefined, tenantId, undefined, PAGE_SIZE)); }
    catch (cause) { setError(message(cause, 'Unable to load Routing Profiles.')); }
  }, [tenantId, profileSearch]);

  const loadCapabilities = useCallback(async () => {
    if (!tenantId) return;
    try { setCapabilities(await coreAdminApi.getCanonicalCapabilities('ACTIVE', capabilitySearch || undefined, undefined, tenantId, PAGE_SIZE)); }
    catch (cause) { setError(message(cause, 'Unable to load Canonical Capabilities.')); }
  }, [tenantId, capabilitySearch]);

  const loadBindings = useCallback(async () => {
    if (!tenantId || !capabilityCode) { setBindings([]); return; }
    try {
      const rows = await coreAdminApi.getCapabilityBindings(capabilityCode, undefined, 'APPROVED', tenantId, undefined, 100);
      setBindings(rows);
      if (!rows.some((item) => item.bindingId === bindingId)) setBindingId(rows[0]?.bindingId ?? '');
    } catch (cause) { setError(message(cause, 'Unable to load approved capability providers.')); }
  }, [tenantId, capabilityCode, bindingId]);

  const loadObservations = useCallback(async () => {
    if (!tenantId || !bindingId) { setObservations([]); return; }
    try { setObservations(await coreAdminApi.getProviderEligibilityObservations(bindingId, tenantId, 20)); }
    catch (cause) { setError(message(cause, 'Unable to load eligibility observations.')); }
  }, [tenantId, bindingId]);

  const loadRecentDecisions = useCallback(async () => {
    if (!tenantId) return;
    try { setRecentDecisions(await coreAdminApi.getProviderRoutingDecisions(capabilityCode || undefined, tenantId, 20)); }
    catch (cause) { setError(message(cause, 'Unable to load WHO SHOULD decision evidence.')); }
  }, [tenantId, capabilityCode]);

  useEffect(() => { const timer = window.setTimeout(() => void loadProfiles(), 200); return () => window.clearTimeout(timer); }, [loadProfiles]);
  useEffect(() => { const timer = window.setTimeout(() => void loadCapabilities(), 200); return () => window.clearTimeout(timer); }, [loadCapabilities]);
  useEffect(() => { void loadBindings(); }, [tenantId, capabilityCode]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void loadObservations(); }, [tenantId, bindingId]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void loadRecentDecisions(); }, [tenantId, capabilityCode]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!selectedBinding) return;
    setObservation((current) => ({ ...current, bindingId: selectedBinding.bindingId, providerId: selectedBinding.providerId }));
  }, [selectedBinding]);
  useEffect(() => { setProfileActivationAcknowledged(false); }, [profileDraft]);
  useEffect(() => {
    const ops = selectedCapability?.operations ?? [];
    if (!ops.includes(operation)) setOperation(ops[0] ?? '');
  }, [selectedCapability, operation]);

  async function saveProfile() {
    if (!tenantId || !canManage || !profileDraft.profileId.trim() || !profileDraft.displayName.trim()) return;
    if (persistedProfile && !profileReason.trim()) { setError('Enter a change reason before modifying an existing Routing Profile.'); return; }
    if (activeSave && !profileActivationAcknowledged) { setError('Review the ACTIVE impact and acknowledge that this direct save can change WHO SHOULD ranking authority immediately.'); return; }
    setBusy(true); setError(''); setNotice(''); setProfileOutcome(undefined);
    try {
      const saved = await coreAdminApi.upsertProviderRoutingProfile(profileDraft.profileId.trim(), profileDraft, tenantId, profileReason.trim() || undefined);
      setProfileDraft(saved); setProfileReason('');
      setProfileVersions(await coreAdminApi.getProviderRoutingProfileVersions(saved.profileId, tenantId));
      setNotice(`Routing Profile ${saved.profileId} v${saved.version ?? 1} saved. It can rank only Phase 3 PASS candidates.`);
      setProfileOutcome({
        outcome: 'INFO',
        title: saved.status === 'ACTIVE' ? 'ACTIVE Routing Profile saved' : `${saved.status ?? 'DRAFT'} Routing Profile saved`,
        message: saved.status === 'ACTIVE'
          ? 'Core accepted a new ACTIVE profile version. Provider ranking can use it immediately wherever this profile is referenced.'
          : 'Core stored a new immutable profile version. This status does not activate new ranking authority.',
        safetyNote: saved.status === 'ACTIVE'
          ? 'This API has no separate Draft → Validate → Publish boundary. Use WHO SHOULD preview as a post-save behavior check, and reload before retrying if a future save has an ambiguous network result.'
          : 'Keep profiles in DRAFT while authoring. Immutable versions are audit evidence; there is currently no one-click restore API for an older profile version.',
      });
      await loadProfiles();
    } catch (cause) {
      const text = message(cause, 'Routing Profile could not be saved.');
      setError(text);
      setProfileOutcome({ outcome: 'APPLY_FAILED', title: 'Routing Profile save was not confirmed', message: text, safetyNote: 'If this was an ACTIVE save, reload the profile before retrying. A transport failure does not prove whether Core committed the request.' });
    }
    finally { setBusy(false); }
  }

  async function saveObservation() {
    if (!tenantId || !canManage || !observation.bindingId || !observation.providerId) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const saved = await coreAdminApi.recordProviderEligibilityObservation(observation, tenantId);
      setNotice(`Eligibility observation ${saved.observationId ?? ''} recorded. Observations are immutable evidence.`);
      await loadObservations();
    } catch (cause) { setError(message(cause, 'Eligibility observation could not be recorded.')); }
    finally { setBusy(false); }
  }

  async function evaluate() {
    if (!tenantId || !capabilityCode || !operation || !profileDraft.profileId || csv(authorizationIds).length === 0) {
      setError('Choose a Capability, operation, ACTIVE Routing Profile and provide persisted Phase 3 PASS decision IDs.'); return;
    }
    setBusy(true); setError(''); setNotice(''); setRoutingDecision(undefined);
    try {
      const decision = await coreAdminApi.evaluateProviderRoutingPreview({
        capabilityCode, operation, routingProfileId: profileDraft.profileId, authorizationDecisionIds: csv(authorizationIds),
      }, tenantId);
      setRoutingDecision(decision);
      setNotice('WHO SHOULD preview completed. The selected Binding is evidence only; no Netty, A2A, MCP or service execution occurred.');
      await loadRecentDecisions();
    } catch (cause) { setError(message(cause, 'WHO SHOULD preview failed.')); }
    finally { setBusy(false); }
  }

  function editProfile(profile: CoreRoutingProfile) {
    setProfileDraft(profile); setProfileReason(''); setRoutingDecision(undefined); setError(''); setNotice(''); setProfileOutcome(undefined);
    if (tenantId) void coreAdminApi.getProviderRoutingProfileVersions(profile.profileId, tenantId).then(setProfileVersions).catch(() => setProfileVersions([]));
  }

  function setWeight(key: string, value: string) {
    const parsed = n(value) ?? 0;
    setProfileDraft((current) => ({ ...current, weights: { ...(current.weights ?? {}), [key]: parsed } }));
  }

  return <div className="space-y-5">
    <ConfigurationPurposePanel
      title="Control how already-authorized providers are ranked"
      purpose="Use Routing Profiles to decide which eligible provider should be preferred after capability and authorization gates have already passed. This page never grants permission and never chooses transport or credentials."
      currentState={<>{activeProfileCount} ACTIVE profile{activeProfileCount === 1 ? '' : 's'} are currently available for WHO SHOULD ranking.</>}
      impact={<>Saving a profile as <b>ACTIVE</b> can immediately change ranking priority or eligibility thresholds wherever that profile is referenced.</>}
      validation={<>Keep a profile in DRAFT while authoring. After an ACTIVE save, run WHO SHOULD preview with persisted Phase 3 PASS evidence to observe ranking behavior without dispatching work.</>}
      recovery={<>Suspend the profile or save a corrective version. Immutable versions remain available for comparison, but Core does not currently expose a one-click profile rollback API.</>}
    >
      <ConfigurationLifecycleBoundaryNotice
        title="Provider Routing activation model"
        governed={<>Profile versions and change reasons are immutable audit evidence, but there is no approval/publish workflow for Routing Profiles today.</>}
        directSave={<>Saving with status <b>ACTIVE</b> is the activation boundary. DRAFT is the safe authoring state; ACTIVE changes may affect new WHO SHOULD decisions immediately.</>}
      />
    </ConfigurationPurposePanel>

    <section className="rounded-3xl border border-blue-200 bg-blue-50 p-5">
      <div className="text-xs font-black uppercase tracking-wide text-blue-700">Phase 4 · WHO SHOULD</div>
      <h2 className="mt-1 text-xl font-black text-slate-950">Rank only candidates that already passed WHO MAY</h2>
      <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">Authorization is never a score. Phase 3 PASS is a hard prerequisite; runtime eligibility is another hard gate. Only then are quality, reliability, latency, cost, load and locality ranked. This page does not choose a Target Domain, Agent Pool, endpoint, credential or execution protocol.</p>
      <div className="mt-3 rounded-2xl border border-blue-200 bg-white p-3 text-xs font-bold text-slate-700">WHAT → WHO CAN → WHO MAY → <span className="text-blue-700">WHO SHOULD</span> → HOW (Phase 5)</div>
    </section>

    {(error || notice) && <div className={`rounded-2xl border p-4 text-sm font-bold ${error ? 'border-rose-200 bg-rose-50 text-rose-800' : 'border-emerald-200 bg-emerald-50 text-emerald-800'}`}>{error || notice}</div>}

    <div className="grid gap-5 xl:grid-cols-[0.9fr_1.4fr]">
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="font-black text-slate-950">Routing Profiles</h3>
        <p className="mt-1 text-xs leading-5 text-slate-500">Profiles express ranking priorities and eligibility thresholds. They cannot authorize a caller.</p>
        <input className="mt-3 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" placeholder="Search profiles" value={profileSearch} onChange={(e) => setProfileSearch(e.target.value)} />
        <div className="mt-3 max-h-72 space-y-2 overflow-auto">
          {profiles.map((profile) => <button key={profile.profileId} onClick={() => editProfile(profile)} className="w-full rounded-xl border border-slate-200 p-3 text-left hover:bg-slate-50">
            <div className="flex items-center justify-between"><span className="font-black text-slate-900">{profile.displayName}</span><span className="text-xs font-black text-slate-500">{profile.status}</span></div>
            <div className="mt-1 text-xs text-slate-500">{profile.profileId} · {profile.profileType} · v{profile.version ?? 1}</div>
          </button>)}
        </div>
        <Button onClick={() => { setProfileDraft(emptyProfile()); setProfileVersions([]); setProfileOutcome(undefined); setProfileReason(''); }} className="mt-3" tone="secondary" size="xs">New profile</Button>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="font-black text-slate-950">Profile definition</h3>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <label className="text-xs font-bold text-slate-600">Profile ID<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.profileId} onChange={(e) => setProfileDraft({ ...profileDraft, profileId: e.target.value })} /></label>
          <label className="text-xs font-bold text-slate-600">Display name<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.displayName} onChange={(e) => setProfileDraft({ ...profileDraft, displayName: e.target.value })} /></label>
          <label className="text-xs font-bold text-slate-600">Profile type<select className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.profileType} onChange={(e) => setProfileDraft({ ...profileDraft, profileType: e.target.value })}>{PROFILE_TYPES.map((v) => <option key={v}>{v}</option>)}</select></label>
          <label className="text-xs font-bold text-slate-600">Status<select className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.status} onChange={(e) => setProfileDraft({ ...profileDraft, status: e.target.value })}><option>DRAFT</option><option>ACTIVE</option><option>SUSPENDED</option><option>RETIRED</option></select></label>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-3 lg:grid-cols-6">{WEIGHT_KEYS.map((key) => <label key={key} className="text-xs font-bold text-slate-600">{key}<input type="number" className="mt-1 w-full rounded-xl border px-2 py-2 text-sm" value={profileDraft.weights?.[key] ?? 0} onChange={(e) => setWeight(key, e.target.value)} /></label>)}</div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="text-xs font-bold text-slate-600">Min quality<input type="number" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.minQualityScore ?? ''} onChange={(e) => setProfileDraft({ ...profileDraft, minQualityScore: n(e.target.value) })} /></label>
          <label className="text-xs font-bold text-slate-600">Min reliability<input type="number" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.minReliabilityScore ?? ''} onChange={(e) => setProfileDraft({ ...profileDraft, minReliabilityScore: n(e.target.value) })} /></label>
          <label className="text-xs font-bold text-slate-600">Max P95 latency ms<input type="number" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.maxP95LatencyMs ?? ''} onChange={(e) => setProfileDraft({ ...profileDraft, maxP95LatencyMs: n(e.target.value) })} /></label>
          <label className="text-xs font-bold text-slate-600">Max estimated cost<input type="number" step="0.001" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.maxEstimatedCost ?? ''} onChange={(e) => setProfileDraft({ ...profileDraft, maxEstimatedCost: n(e.target.value) })} /></label>
          <label className="text-xs font-bold text-slate-600">Max load %<input type="number" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.maxLoadPercent ?? ''} onChange={(e) => setProfileDraft({ ...profileDraft, maxLoadPercent: n(e.target.value) })} /></label>
          <label className="text-xs font-bold text-slate-600">Observation max age (sec)<input type="number" className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.maxObservationAgeSeconds ?? 300} onChange={(e) => setProfileDraft({ ...profileDraft, maxObservationAgeSeconds: n(e.target.value) })} /></label>
        </div>
        <ConfigurationImpactPreview
          title="Routing Profile save impact"
          risk={routingRisk}
          summary={activeSave ? 'This save is an activation boundary, not a draft-only save.' : 'This save records a version without creating new ACTIVE ranking authority.'}
          items={[
            { label: 'Current status', value: persistedProfile?.status ?? 'New profile', detail: persistedProfile ? `v${persistedProfile.version ?? 1}` : 'Not persisted yet' },
            { label: 'Proposed status', value: profileDraft.status ?? 'DRAFT', tone: activeSave ? 'risk' : 'info' },
            { label: 'Ranking scope', value: profileDraft.profileType ?? 'BALANCED', detail: 'Applies only after WHO MAY PASS and runtime eligibility gates.' },
            { label: 'Recovery', value: activeSave ? 'Suspend or save corrective version' : 'Edit before activation', detail: 'No one-click version restore API.' },
          ]}
          safetyNote={activeSave ? 'Saving ACTIVE may change new provider-ranking decisions immediately. It does not dispatch work by itself.' : 'DRAFT is the safest state for editing and peer review because it does not become active ranking authority.'}
        >
          {activeSave ? <label className="flex items-start gap-2 rounded-lg border border-orange-200 bg-white p-3 text-xs font-semibold text-orange-950"><input type="checkbox" className="mt-0.5" checked={profileActivationAcknowledged} onChange={(e) => setProfileActivationAcknowledged(e.target.checked)} /><span>I reviewed the ranking weights, eligibility thresholds and recovery path. I understand that saving this profile as ACTIVE can affect new WHO SHOULD decisions immediately.</span></label> : null}
        </ConfigurationImpactPreview>
        <label className="mt-4 block text-xs font-bold text-slate-600">Change reason<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" placeholder="Required when modifying an existing profile" value={profileReason} onChange={(e) => setProfileReason(e.target.value)} /></label>
        <Button disabled={busy || !canManage || (activeSave && !profileActivationAcknowledged)} onClick={() => void saveProfile()} className="mt-4" tone={activeSave ? 'warning' : 'primary'} size="md">{activeSave ? 'Save ACTIVE profile · applies immediately' : `Save ${profileDraft.status ?? 'DRAFT'} profile`}</Button>
        {profileOutcome ? <div className="mt-4"><ConfigurationOperationOutcome {...profileOutcome} /></div> : null}
        {profileVersions.length > 0 && <div className="mt-4 rounded-2xl border bg-slate-50 p-3"><div className="text-xs font-black text-slate-700">Immutable profile versions</div><div className="mt-2 space-y-1 text-xs text-slate-600">{profileVersions.map((v) => <div key={v.version}>v{v.version} · {v.changeReason} · {v.actorRef}</div>)}</div></div>}
      </section>
    </div>

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Provider eligibility evidence</h3>
      <p className="mt-1 text-xs leading-5 text-slate-500">Observations are protocol-neutral and immutable. Agent Card/MCP self-advertisement is not accepted as runtime eligibility evidence.</p>
      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <label className="text-xs font-bold text-slate-600">Capability search<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={capabilitySearch} onChange={(e) => setCapabilitySearch(e.target.value)} /></label>
        <label className="text-xs font-bold text-slate-600">Capability<select className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={capabilityCode} onChange={(e) => setCapabilityCode(e.target.value)}><option value="">Choose…</option>{capabilities.map((item) => <option key={item.capabilityCode} value={item.capabilityCode}>{item.displayName} · {item.capabilityCode}</option>)}</select></label>
        <label className="text-xs font-bold text-slate-600">Operation<select className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={operation} onChange={(e) => setOperation(e.target.value)}><option value="">Choose…</option>{(selectedCapability?.operations ?? []).map((item) => <option key={item}>{item}</option>)}</select></label>
        <label className="text-xs font-bold text-slate-600 md:col-span-3">APPROVED WHO CAN Binding<select className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={bindingId} onChange={(e) => setBindingId(e.target.value)}><option value="">Choose…</option>{bindings.map((item) => <option key={item.bindingId} value={item.bindingId}>{item.providerDisplayName ?? item.providerId} · {item.bindingId}</option>)}</select></label>
      </div>
      {selectedBinding && <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <label className="text-xs font-bold">Quality<input type="number" className="mt-1 w-full rounded-xl border px-2 py-2" value={observation.qualityScore ?? ''} onChange={(e) => setObservation({ ...observation, qualityScore: n(e.target.value) })} /></label>
        <label className="text-xs font-bold">Reliability<input type="number" className="mt-1 w-full rounded-xl border px-2 py-2" value={observation.reliabilityScore ?? ''} onChange={(e) => setObservation({ ...observation, reliabilityScore: n(e.target.value) })} /></label>
        <label className="text-xs font-bold">P95 ms<input type="number" className="mt-1 w-full rounded-xl border px-2 py-2" value={observation.p95LatencyMs ?? ''} onChange={(e) => setObservation({ ...observation, p95LatencyMs: n(e.target.value) })} /></label>
        <label className="text-xs font-bold">Cost<input type="number" step="0.001" className="mt-1 w-full rounded-xl border px-2 py-2" value={observation.estimatedCost ?? ''} onChange={(e) => setObservation({ ...observation, estimatedCost: n(e.target.value) })} /></label>
        <label className="text-xs font-bold">Load %<input type="number" className="mt-1 w-full rounded-xl border px-2 py-2" value={observation.loadPercent ?? ''} onChange={(e) => setObservation({ ...observation, loadPercent: n(e.target.value) })} /></label>
        <label className="text-xs font-bold">Locality<input type="number" className="mt-1 w-full rounded-xl border px-2 py-2" value={observation.localityScore ?? ''} onChange={(e) => setObservation({ ...observation, localityScore: n(e.target.value) })} /></label>
        <label className="flex items-center gap-2 text-xs font-bold"><input type="checkbox" checked={observation.available} onChange={(e) => setObservation({ ...observation, available: e.target.checked })} /> Available</label>
        <label className="flex items-center gap-2 text-xs font-bold"><input type="checkbox" checked={observation.healthy} onChange={(e) => setObservation({ ...observation, healthy: e.target.checked })} /> Healthy</label>
        <label className="flex items-center gap-2 text-xs font-bold"><input type="checkbox" checked={observation.capacityAvailable} onChange={(e) => setObservation({ ...observation, capacityAvailable: e.target.checked })} /> Capacity available</label>
      </div>}
      <Button disabled={busy || !canManage || !selectedBinding} onClick={() => void saveObservation()} className="mt-4" tone="secondary" size="md">Record preview observation</Button>
      {observations.length > 0 && <div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-xs"><thead><tr className="border-b"><th className="p-2">Observed</th><th className="p-2">Source</th><th className="p-2">Available</th><th className="p-2">Quality</th><th className="p-2">Reliability</th><th className="p-2">P95</th><th className="p-2">Cost</th><th className="p-2">Load</th></tr></thead><tbody>{observations.map((item) => <tr key={item.observationId} className="border-b"><td className="p-2">{item.observedAt}</td><td className="p-2">{item.observationSource}</td><td className="p-2">{item.available && item.healthy && item.capacityAvailable ? 'YES' : 'NO'}</td><td className="p-2">{item.qualityScore ?? '—'}</td><td className="p-2">{item.reliabilityScore ?? '—'}</td><td className="p-2">{item.p95LatencyMs ?? '—'}</td><td className="p-2">{item.estimatedCost ?? '—'}</td><td className="p-2">{item.loadPercent ?? '—'}</td></tr>)}</tbody></table></div>}
    </section>

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="font-black text-slate-950">Test WHO SHOULD without executing anything</h3>
      <p className="mt-1 text-xs leading-5 text-slate-500">Paste persisted Phase 3 PASS decision IDs for candidates. Phase 4 reloads and validates those decisions; a stale or non-PASS decision is excluded rather than converted into a lower score.</p>
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <label className="text-xs font-bold text-slate-600">ACTIVE Routing Profile<select className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" value={profileDraft.profileId} onChange={(e) => { const found = profiles.find((p) => p.profileId === e.target.value); if (found) editProfile(found); }}><option value="">Choose…</option>{profiles.filter((p) => p.status === 'ACTIVE').map((p) => <option key={p.profileId} value={p.profileId}>{p.displayName} · {p.profileId}</option>)}</select></label>
        <label className="text-xs font-bold text-slate-600">Phase 3 PASS decision IDs<input className="mt-1 w-full rounded-xl border px-3 py-2 text-sm" placeholder="delegation-decision-..., delegation-decision-..." value={authorizationIds} onChange={(e) => setAuthorizationIds(e.target.value)} /></label>
      </div>
      <Button disabled={busy} onClick={() => void evaluate()} className="mt-4" tone="primary" size="md">Evaluate WHO SHOULD</Button>
      <div className="mt-4"><ConfigurationValidationPanel
        title="WHO SHOULD post-save behavior check"
        status={!routingDecision ? 'NOT_RUN' : routingDecision.result === 'SELECTED' ? 'PASSED' : 'FAILED'}
        summary={!routingDecision ? 'Run a preview after saving an ACTIVE profile to observe how persisted authorization evidence and runtime eligibility are ranked.' : routingDecision.result === 'SELECTED' ? `Preview selected ${routingDecision.selectedProviderId ?? 'a provider'} using the persisted profile.` : `Preview returned ${routingDecision.result}; review the reason codes before relying on this profile.`}
        checks={routingDecision ? [
          { label: 'Result', value: routingDecision.result, passed: routingDecision.result === 'SELECTED' },
          { label: 'Profile', value: `${routingDecision.routingProfileId} v${routingDecision.routingProfileVersion}`, passed: true },
          { label: 'Execution', value: 'No dispatch / transport execution', passed: true },
        ] : undefined}
        safetyNote="WHO SHOULD preview is post-save evidence against a persisted ACTIVE profile. It does not validate unsaved edits and it never authorizes or dispatches work."
      /></div>
      {routingDecision && <div className="mt-5 rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2"><div className="font-black text-slate-950">{routingDecision.result}</div><div className="text-xs text-slate-500">{routingDecision.decisionId}</div></div>
        <div className="mt-2 text-sm text-slate-700">Selected Binding: <b>{routingDecision.selectedBindingId ?? 'None'}</b> · Provider: <b>{routingDecision.selectedProviderId ?? 'None'}</b></div>
        <div className="mt-1 text-xs text-slate-500">Reasons: {(routingDecision.reasonCodes ?? []).join(', ') || '—'}</div>
        <div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-xs"><thead><tr className="border-b"><th className="p-2">Provider</th><th className="p-2">Eligibility</th><th className="p-2">Total</th><th className="p-2">Components</th><th className="p-2">Excluded because</th></tr></thead><tbody>{(routingDecision.candidates ?? []).map((candidate) => <tr key={candidate.authorizationDecisionId} className="border-b"><td className="p-2 font-bold">{candidate.providerId}</td><td className="p-2">{candidate.eligibilityResult}</td><td className="p-2">{candidate.totalScore ?? '—'}</td><td className="p-2">{Object.entries(candidate.scoreComponents ?? {}).map(([k,v]) => `${k}:${v}`).join(' · ') || '—'}</td><td className="p-2">{(candidate.exclusionReasons ?? []).join(', ') || '—'}</td></tr>)}</tbody></table></div>
      </div>}
      <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-3 text-xs font-bold text-amber-900">A score tie fails closed as ROUTING_AMBIGUOUS. Provider IDs are never used as a lexical tie-breaker.</div>
    </section>

    {recentDecisions.length > 0 && <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><h3 className="font-black text-slate-950">Recent WHO SHOULD evidence</h3><div className="mt-3 space-y-2">{recentDecisions.map((item) => <div key={item.decisionId} className="rounded-xl border p-3 text-xs"><b>{item.result}</b> · {item.capabilityCode} / {item.operation} · selected {item.selectedProviderId ?? 'none'} · profile {item.routingProfileId} v{item.routingProfileVersion}</div>)}</div></section>}
  </div>;
}
