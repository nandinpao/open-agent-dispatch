'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import {
  runtimeConfigurationApi,
  type RuntimeConfigurationApplicationStatus,
  type RuntimeConfigurationApplyStateView,
  type RuntimeConfigurationCategory,
  type RuntimeConfigurationCutoverStatus,
  type RuntimeConfigurationDataType,
  type RuntimeConfigurationGovernanceOverview,
  type RuntimeConfigurationOverview,
  type RuntimeConfigurationRevisionView,
  type RuntimeConfigurationRollbackPreview,
  type RuntimeConfigurationSettingDetail,
} from '@/lib/api/runtimeConfigurationApi';

function valueText(value: unknown, unit?: string): string {
  if (value === null || value === undefined) return '—';
  const raw = typeof value === 'object' ? JSON.stringify(value) : String(value);
  if (unit === 'duration' && /^PT/.test(raw)) return raw.replace(/^PT/, '').toLowerCase();
  if (unit === 'percent') return `${raw}%`;
  return raw;
}

const statusCopy: Record<RuntimeConfigurationApplicationStatus, { label: string; tone: string }> = {
  STARTUP_FALLBACK: { label: 'Startup value — not yet runtime-managed', tone: 'border-slate-200 bg-slate-50 text-slate-700' },
  PUBLISHED_APPLYING: { label: 'Published — applying to runtime', tone: 'border-blue-200 bg-blue-50 text-blue-800' },
  APPLIED: { label: 'Applied', tone: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  PARTIALLY_APPLIED: { label: 'Partially applied — action required', tone: 'border-amber-300 bg-amber-50 text-amber-900' },
  CONFIGURATION_INCOMPLETE: { label: 'Configuration incomplete', tone: 'border-rose-300 bg-rose-50 text-rose-900' },
  FAILED: { label: 'Apply failed', tone: 'border-rose-300 bg-rose-50 text-rose-900' },
  NOT_MIGRATED: { label: 'Not yet migrated', tone: 'border-slate-200 bg-slate-50 text-slate-600' },
};

function StatusBadge({ status }: Readonly<{ status: RuntimeConfigurationApplicationStatus }>) {
  const view = statusCopy[status] ?? statusCopy.NOT_MIGRATED;
  return <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-black ${view.tone}`}>{view.label}</span>;
}

function HealthCard({ label, value, note }: Readonly<{ label: string; value: string; note: string }>) {
  return <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-2 text-2xl font-black text-slate-950">{value}</div><div className="mt-1 text-xs leading-5 text-slate-500">{note}</div></div>;
}

const lifecycleCopy: Record<RuntimeConfigurationCategory['lifecycle'], { label: string; tone: string }> = {
  PLANNED: { label: 'Planned', tone: 'bg-slate-100 text-slate-600' },
  PILOT: { label: 'Pilot', tone: 'bg-blue-100 text-blue-800' },
  MIGRATING: { label: 'Migrating', tone: 'bg-amber-100 text-amber-900' },
  MIGRATED: { label: 'Migrated', tone: 'bg-emerald-100 text-emerald-800' },
  MIXED: { label: 'Mixed', tone: 'bg-violet-100 text-violet-800' },
};

function CategoryCard({ category, selected, onSelect }: Readonly<{ category: RuntimeConfigurationCategory; selected: boolean; onSelect: () => void }>) {
  const hasSettings = category.settings.length > 0;
  const lifecycle = lifecycleCopy[category.lifecycle];
  return <button type="button" disabled={!hasSettings} onClick={onSelect} className={`min-h-40 rounded-2xl border p-4 text-left transition ${selected ? 'border-blue-400 bg-blue-50 shadow-sm' : 'border-slate-200 bg-white hover:border-blue-200'} ${!hasSettings ? 'cursor-not-allowed opacity-80' : ''}`}>
    <div className="flex items-start justify-between gap-3"><div className="font-black text-slate-950">{category.title}</div><span className={`rounded-full px-2 py-1 text-[10px] font-black uppercase tracking-wide ${lifecycle.tone}`}>{lifecycle.label}</span></div>
    <p className="mt-2 text-sm leading-6 text-slate-600">{category.description}</p>
    <div className="mt-3 flex flex-wrap gap-2 text-[11px] font-bold text-slate-500"><span>{category.definitionCount} runtime targets</span><span>·</span><span>{category.migrationAuthorizedCount} migration-ready</span><span>·</span><span>{category.editableCount} editable</span><span>·</span><span>{category.configSetKeys.length} config set{category.configSetKeys.length === 1 ? '' : 's'}</span>{category.readOnly && <><span>·</span><span>read-only</span></>}</div>
    <div className="mt-3"><StatusBadge status={category.applicationStatus} /></div>
  </button>;
}

function RevisionBadge({ state }: Readonly<{ state: string }>) {
  const tone = state === 'APPROVED' ? 'bg-emerald-100 text-emerald-800' : state === 'PENDING_APPROVAL' ? 'bg-amber-100 text-amber-900' : state === 'PUBLISHED' ? 'bg-blue-100 text-blue-800' : 'bg-slate-100 text-slate-700';
  return <span className={`rounded-full px-2 py-1 text-[10px] font-black ${tone}`}>{state.replaceAll('_', ' ')}</span>;
}

function NodeStateBadge({ state }: Readonly<{ state: string }>) {
  const tone = state === 'APPLIED' ? 'bg-emerald-100 text-emerald-800' : state === 'FAILED' || state === 'STALE' ? 'bg-rose-100 text-rose-800' : state === 'NOT_SEEN' ? 'bg-amber-100 text-amber-900' : 'bg-blue-100 text-blue-800';
  return <span className={`rounded-full px-2 py-1 text-[10px] font-black ${tone}`}>{state.replaceAll('_', ' ')}</span>;
}

function toEditorValue(value: unknown, dataType?: RuntimeConfigurationDataType): string {
  if (value === null || value === undefined) return '';
  if (dataType === 'JSON') return JSON.stringify(value, null, 2);
  return String(value);
}

function parseDurationEditor(value: string, unit: string): string {
  const trimmed = value.trim();
  if (/^P/i.test(trimmed)) return trimmed.toUpperCase();
  const n = Number(trimmed);
  if (!Number.isFinite(n) || n <= 0) throw new Error('Enter a positive duration.');
  if (unit === 'milliseconds') return `PT${n / 1000}S`;
  if (unit === 'minutes') return `PT${n}M`;
  if (unit === 'hours') return `PT${n}H`;
  return `PT${n}S`;
}

function SettingEditor({ detail, value, onChange }: Readonly<{ detail: RuntimeConfigurationSettingDetail; value: string; onChange: (value: string) => void }>) {
  const common = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm font-semibold';
  if (detail.dataType === 'BOOLEAN') {
    return <select value={value.toLowerCase() === 'true' ? 'true' : 'false'} onChange={e => onChange(e.target.value)} className={common}><option value="true">Enabled</option><option value="false">Disabled</option></select>;
  }
  if (detail.dataType === 'ENUM') {
    const values = Array.isArray(detail.validation?.allowedValues) ? detail.validation.allowedValues.map(String) : [];
    return <select value={value} onChange={e => onChange(e.target.value)} className={common}>{values.map(option => <option key={option} value={option}>{option}</option>)}</select>;
  }
  if (detail.dataType === 'JSON') {
    return <textarea value={value} onChange={e => onChange(e.target.value)} rows={7} spellCheck={false} className={`${common} font-mono`} />;
  }
  const numeric = detail.dataType === 'INTEGER' || detail.dataType === 'LONG' || detail.dataType === 'DECIMAL';
  const minimum = numeric && detail.validation?.minimum !== undefined ? Number(detail.validation.minimum) : undefined;
  const maximum = numeric && detail.validation?.maximum !== undefined ? Number(detail.validation.maximum) : undefined;
  return <><input type={numeric ? 'number' : detail.dataType === 'URI' ? 'url' : 'text'} min={Number.isFinite(minimum) ? minimum : undefined} max={Number.isFinite(maximum) ? maximum : undefined} step={detail.dataType === 'INTEGER' || detail.dataType === 'LONG' ? 1 : 'any'} value={value} onChange={e => onChange(e.target.value)} className={common} />{numeric && (Number.isFinite(minimum) || Number.isFinite(maximum)) && <div className="mt-1 text-[11px] font-semibold text-slate-500">Allowed: {Number.isFinite(minimum) ? minimum : 'no minimum'} – {Number.isFinite(maximum) ? maximum : 'no maximum'} {detail.unit && detail.unit !== 'none' ? detail.unit : ''}</div>}</>;
}

export function RuntimeConfigurationConsole() {
  const entitlements = useUiEntitlements();
  const [overview, setOverview] = useState<RuntimeConfigurationOverview>();
  const [governance, setGovernance] = useState<RuntimeConfigurationGovernanceOverview>();
  const [selectedCategory, setSelectedCategory] = useState('dispatch-routing');
  const [detail, setDetail] = useState<RuntimeConfigurationSettingDetail>();
  const [revisions, setRevisions] = useState<RuntimeConfigurationRevisionView[]>([]);
  const [applyState, setApplyState] = useState<RuntimeConfigurationApplyStateView>();
  const [cutover, setCutover] = useState<RuntimeConfigurationCutoverStatus>();
  const [searchQuery, setSearchQuery] = useState('');
  const [rollbackPreview, setRollbackPreview] = useState<RuntimeConfigurationRollbackPreview>();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [editValue, setEditValue] = useState('');
  const [reason, setReason] = useState('');
  const [ttlMinutes, setTtlMinutes] = useState(30);
  const [busy, setBusy] = useState(false);

  const canEdit = actionAllowed(entitlements.value, 'runtime-configuration.edit');
  const canApprove = actionAllowed(entitlements.value, 'runtime-configuration.approve');
  const canPublish = actionAllowed(entitlements.value, 'runtime-configuration.publish');
  const canRollback = actionAllowed(entitlements.value, 'runtime-configuration.rollback');
  const canBreakGlass = actionAllowed(entitlements.value, 'runtime-configuration.break-glass');
  const canGovern = canEdit || canApprove || canPublish || canRollback || canBreakGlass;
  const category = useMemo(() => overview?.categories.find(item => item.id === selectedCategory), [overview, selectedCategory]);
  const visibleSettings = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!category) return [];
    if (!q) return category.settings;
    return category.settings.filter(item => `${item.label} ${item.key} ${item.description}`.toLowerCase().includes(q));
  }, [category, searchQuery]);

  async function reloadGovernance() { setGovernance(await runtimeConfigurationApi.governance()); }
  async function loadOverview() { const value = await runtimeConfigurationApi.overview(); setOverview(value); await reloadGovernance(); }
  async function openSetting(key: string, clearFeedback = true) {
    if (clearFeedback) { setError(''); setMessage(''); }
    const [value, history, state] = await Promise.all([runtimeConfigurationApi.detail(key), runtimeConfigurationApi.revisions(key), runtimeConfigurationApi.applyState(key)]);
    let cutoverState: RuntimeConfigurationCutoverStatus | undefined;
    if (value.configSetId && value.migrationState !== 'PROPOSED') {
      try { cutoverState = await runtimeConfigurationApi.cutoverStatus(value.configSetId); } catch { cutoverState = undefined; }
    }
    setDetail(value); setRevisions(history); setApplyState(state); setCutover(cutoverState); setRollbackPreview(undefined);
    setEditValue(toEditorValue(value.desiredValue ?? value.localEffectiveValue, value.dataType));
  }
  async function refreshSelected() { await loadOverview(); if (detail) await openSetting(detail.key, false); }

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const [value, governanceValue] = await Promise.all([runtimeConfigurationApi.overview(), runtimeConfigurationApi.governance()]);
        if (active) { setOverview(value); setGovernance(governanceValue); }
      } catch (cause) { if (active) setError(cause instanceof Error ? cause.message : 'Runtime Configuration could not be loaded.'); }
      finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!overview || overview.categories.length === 0) return;
    if (!overview.categories.some(item => item.id === selectedCategory)) setSelectedCategory(overview.categories[0].id);
  }, [overview, selectedCategory]);

  function typedValue(): unknown {
    if (!detail) return null;
    if (detail.dataType === 'BOOLEAN') return editValue.toLowerCase() === 'true';
    if (detail.dataType === 'INTEGER' || detail.dataType === 'LONG') {
      const v = Number(editValue); if (!Number.isInteger(v)) throw new Error('Enter a whole number.'); return v;
    }
    if (detail.dataType === 'DECIMAL') { const v = Number(editValue); if (!Number.isFinite(v)) throw new Error('Enter a valid decimal.'); return v; }
    if (detail.dataType === 'DURATION') return parseDurationEditor(editValue, detail.unit);
    if (detail.dataType === 'JSON') { try { return JSON.parse(editValue); } catch { throw new Error('Enter valid JSON.'); } }
    return editValue.trim();
  }

  async function run(action: () => Promise<unknown>, success: string, confirmation?: string) {
    if (!reason.trim()) { setError('Enter an audit reason. Governance actions require a reason.'); return; }
    if (confirmation && !window.confirm(confirmation)) return;
    setBusy(true); setError(''); setMessage('');
    try { await action(); setReason(''); await refreshSelected(); setMessage(success); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'The governance action failed.'); }
    finally { setBusy(false); }
  }

  async function previewRollback(revision: RuntimeConfigurationRevisionView) {
    if (!detail) return;
    setBusy(true); setError('');
    try { setRollbackPreview(await runtimeConfigurationApi.rollbackPreview(detail.key, revision.revisionId)); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Rollback preview failed.'); }
    finally { setBusy(false); }
  }

  const pendingForSelected = governance?.pendingRevisions.filter(r => revisions.some(x => x.revisionId === r.revisionId)) ?? [];
  const overridesForSelected = governance?.activeEmergencyOverrides.filter(o => o.definitionKey === detail?.key) ?? [];
  const rollbackSources = revisions.filter(r => r.state === 'PUBLISHED' || r.state === 'SUPERSEDED');

  if (loading) return <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm font-semibold text-slate-600" aria-busy="true">Loading Runtime Configuration…</div>;

  return <div className="space-y-5"><div className="flex justify-end"><Link href="/settings/runtime-configuration/migration-governance" className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm font-black text-slate-700">Migration Governance</Link></div>
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-4"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Runtime Configuration</div><h1 className="mt-1 text-2xl font-black text-slate-950">Governed runtime tuning</h1><p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Create a change request, obtain approval from a different operator, publish the approved revision, then verify required-node convergence. Publication is not application.</p></div><span className="rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-black text-rose-800">{overview?.environmentLabel ?? overview?.environment}</span></div>
      {message && <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-bold text-emerald-900">{message}</div>}
      {error && <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-bold text-rose-900">{error}</div>}
    </section>

    <section className="grid gap-3 md:grid-cols-5">
      <HealthCard label="System status" value={overview?.health === 'HEALTHY' ? 'Healthy' : overview?.health === 'APPLYING' ? 'Applying' : 'Attention'} note="Derived from desired snapshots and required-node acknowledgements." />
      <HealthCard label="Runtime migration" value={`${overview?.migrationCoverage.runtimeAuthoritative ?? 0} / ${overview?.migrationCoverage.runtimeTargetDefinitions ?? 0}`} note={`${overview?.migrationCoverage.migrationAuthorized ?? 0} migration-authorized · ${overview?.migrationCoverage.pendingConsumerMigration ?? 0} still require consumer migration.`} />
      <HealthCard label="Required applications" value={`${overview?.appliedNodes ?? 0} / ${overview?.knownNodes ?? 0}`} note="Aggregated Config Set × required-node applications, not unique physical nodes." />
      <HealthCard label="Pending approval" value={String(governance?.pendingRevisions.filter(r => r.state === 'PENDING_APPROVAL').length ?? 0)} note="Creator or submitter cannot approve the same request." />
      <HealthCard label="Emergency overrides" value={String(governance?.activeEmergencyOverrides.length ?? 0)} note="Temporary key-level overlays with TTL." />
    </section>

    {(overview?.recentChanges.length ?? 0) > 0 && <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">Recent changes</div><div className="mt-3 divide-y divide-slate-100">{overview?.recentChanges.map(change => <div key={`${change.revisionId}-${change.state}`} className="flex flex-wrap items-center justify-between gap-3 py-3"><div><div className="text-sm font-black text-slate-900">{change.category} · Revision {change.sequenceNo}</div><div className="mt-1 font-mono text-[10px] text-slate-400">{change.configSetKey}</div><div className="mt-1 text-xs text-slate-500">{change.reason} · {change.actor ?? 'unknown operator'}</div></div><div className="text-right"><RevisionBadge state={change.state} /><div className="mt-1 text-[11px] text-slate-400">{change.publishedAt ? new Date(change.publishedAt).toLocaleString() : '—'}</div></div></div>)}</div></section>}

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-xs font-black uppercase tracking-wide text-slate-500">Manage configuration</div><h2 className="mt-1 text-xl font-black text-slate-950">Choose the work area you want to tune</h2>
      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">{overview?.categories.map(item => <CategoryCard key={item.id} category={item} selected={item.id === selectedCategory} onSelect={() => { setSelectedCategory(item.id); setSearchQuery(''); const first = item.settings[0]; if (first) void openSetting(first.key); }} />)}</div>
    </section>

    {category && <section className="grid gap-4 xl:grid-cols-[360px_1fr]">
      <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{category.title}</div><input value={searchQuery} onChange={e => setSearchQuery(e.target.value)} placeholder="Search setting name, key or purpose" className="mt-3 w-full rounded-xl border border-slate-300 px-3 py-2 text-sm" /><div className="mt-2 text-[11px] font-semibold text-slate-500">Showing {visibleSettings.length} / {category.settings.length} runtime targets</div><div className="mt-3 space-y-2">{visibleSettings.map(setting => <button key={setting.key} type="button" onClick={() => void openSetting(setting.key)} className={`w-full rounded-xl border p-3 text-left ${detail?.key === setting.key ? 'border-blue-400 bg-blue-50' : 'border-slate-200 hover:border-blue-200'}`}><div className="font-black text-slate-900">{setting.label}</div><div className="mt-1 font-mono text-[10px] text-slate-400">{setting.key}</div><div className="mt-1 text-xs leading-5 text-slate-500">{setting.description}</div><div className="mt-2"><StatusBadge status={setting.applicationStatus} /></div></button>)}</div></div>

      <div className="space-y-4">
        {detail && <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
          <div><div className="text-xs font-black uppercase tracking-wide text-slate-500">{category.title}</div><h2 className="mt-1 text-xl font-black text-slate-950">{detail.label}</h2><p className="mt-2 text-sm leading-6 text-slate-600">{detail.description}</p></div>
          <div className="grid gap-3 sm:grid-cols-2"><div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Local effective value</div><div className="mt-1 text-2xl font-black text-slate-950">{valueText(detail.localEffectiveValue, detail.unit)}</div><div className="mt-2 text-[11px] font-semibold text-slate-500">Source: {detail.effectiveSource.replaceAll('_', ' ')}</div></div><div className="rounded-2xl border border-blue-200 bg-blue-50 p-4"><div className="text-xs font-black text-blue-700">Published desired</div><div className="mt-1 text-2xl font-black text-blue-950">{detail.valuePresentInActiveRevision ? valueText(detail.desiredValue, detail.unit) : 'Not present in active revision'}</div><div className="mt-2 text-[11px] font-semibold text-blue-700">Desired revision: {detail.desiredRevisionId ?? 'None'}</div></div></div>
          {detail.configurationState !== 'HEALTHY' && <div className={`rounded-2xl border p-4 text-sm ${detail.configurationState === 'DRIFT' ? 'border-amber-300 bg-amber-50 text-amber-950' : 'border-rose-300 bg-rose-50 text-rose-950'}`}><div className="font-black">{detail.configurationState === 'DRIFT' ? 'Local runtime differs from the desired revision' : 'Runtime configuration is not complete for this key'}</div><div className="mt-1 text-xs leading-5">{detail.errorCode ? `${detail.errorCode}: ${detail.errorDetail ?? 'No additional detail.'}` : `Desired ${detail.desiredRevisionId ?? '—'} · local ${detail.localAppliedRevisionId ?? '—'}`}</div></div>}
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4"><div className="rounded-xl border border-slate-200 p-3"><div className="text-[10px] font-black uppercase text-slate-500">Migration</div><div className="mt-1 text-sm font-black text-slate-900">{detail.migrationState.replaceAll('_', ' ')}</div></div><div className="rounded-xl border border-slate-200 p-3"><div className="text-[10px] font-black uppercase text-slate-500">Authority</div><div className="mt-1 text-sm font-black text-slate-900">{detail.authorityMode.replaceAll('_', ' ')}</div></div><div className="rounded-xl border border-slate-200 p-3"><div className="text-[10px] font-black uppercase text-slate-500">Effective source</div><div className="mt-1 text-sm font-black text-slate-900">{detail.effectiveSource.replaceAll('_', ' ')}</div></div><div className="rounded-xl border border-slate-200 p-3"><div className="text-[10px] font-black uppercase text-slate-500">Apply state</div><div className="mt-1"><StatusBadge status={detail.applicationStatus} /></div></div></div><div className="flex flex-wrap items-center gap-2"><span className="text-xs font-semibold text-slate-500">Configuration: {detail.configurationState}</span><span className="text-xs font-semibold text-slate-500">Cluster: {detail.convergenceState.replaceAll('_', ' ')}</span><span className="text-xs font-semibold text-slate-500">Mutability: {detail.mutability}</span><span className="text-xs font-semibold text-slate-500">Risk: {detail.risk}</span></div>
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4"><div className="text-xs font-black uppercase tracking-wide text-emerald-800">Impact</div><p className="mt-2 text-sm leading-6 text-emerald-950">↑ {detail.impactPositive}</p><p className="mt-1 text-sm leading-6 text-amber-900">△ {detail.impactTradeoff}</p><p className="mt-2 text-xs font-bold text-slate-600">Recommended: {detail.recommended}</p></div>

          {!detail.editable && <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-sm font-black text-slate-900">Read-only runtime target</div><p className="mt-1 text-xs leading-5 text-slate-600">This setting is visible so migration coverage remains complete, but it cannot be changed here until its typed consumer is migration-authorized and its governance contract permits runtime editing.</p></div>}

          {canEdit && detail.editable && <div className="space-y-3 rounded-2xl border border-blue-200 bg-blue-50 p-4"><div className="text-sm font-black text-blue-950">Create change request</div><label className="block text-sm font-black text-slate-900">New value<SettingEditor detail={detail} value={editValue} onChange={setEditValue} /></label><button type="button" disabled={busy} onClick={() => void run(() => runtimeConfigurationApi.requestChange(detail.key, typedValue(), detail.activeRevisionId, reason.trim()), 'Change request submitted for approval. A different operator must approve it before publication.')} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Create change request</button></div>}

          {canBreakGlass && detail.editable && detail.activeRevisionId && <div className="space-y-3 rounded-2xl border border-rose-200 bg-rose-50 p-4"><div className="text-sm font-black text-rose-950">Emergency override</div><p className="text-xs leading-5 text-rose-900">Temporary operational mitigation. It overlays one key, expires automatically, and does not rewrite the normal revision.</p><div className="grid grid-cols-[1fr_120px] gap-2"><SettingEditor detail={detail} value={editValue} onChange={setEditValue} /><input type="number" min={5} max={240} value={ttlMinutes} onChange={e => setTtlMinutes(Number(e.target.value))} className="mt-1 rounded-xl border border-rose-200 bg-white px-3 py-2 text-sm" aria-label="Emergency TTL minutes" /></div><button type="button" disabled={busy} onClick={() => void run(() => runtimeConfigurationApi.emergencyOverride(detail.key, typedValue(), ttlMinutes, reason.trim()), 'Emergency override activated. Required nodes are reapplying the effective snapshot.', `Activate an emergency override for ${ttlMinutes} minutes?`)} className="rounded-xl bg-rose-700 px-4 py-2 text-sm font-black text-white disabled:bg-slate-300">Activate temporary override</button></div>}

          <details className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><summary className="cursor-pointer text-sm font-black text-slate-800">Advanced details</summary><dl className="mt-3 grid grid-cols-[160px_1fr] gap-2 text-xs"><dt className="font-bold text-slate-500">Technical key</dt><dd className="break-all font-mono">{detail.key}</dd><dt className="font-bold text-slate-500">Authority</dt><dd>{detail.authority}</dd><dt className="font-bold text-slate-500">Authority source</dt><dd>{detail.authoritySource.replaceAll('_', ' ')}</dd><dt className="font-bold text-slate-500">Effective source</dt><dd>{detail.effectiveSource.replaceAll('_', ' ')}</dd><dt className="font-bold text-slate-500">Configuration state</dt><dd>{detail.configurationState}</dd><dt className="font-bold text-slate-500">Active revision contains key</dt><dd>{detail.valuePresentInActiveRevision ? 'Yes' : 'No'}</dd><dt className="font-bold text-slate-500">Desired revision</dt><dd className="break-all font-mono">{detail.desiredRevisionId ?? 'No active runtime revision'}</dd><dt className="font-bold text-slate-500">Local applied revision</dt><dd className="break-all font-mono">{detail.localAppliedRevisionId ?? 'No local authenticated snapshot'}</dd><dt className="font-bold text-slate-500">Cluster convergence</dt><dd>{detail.convergenceState.replaceAll('_', ' ')}</dd><dt className="font-bold text-slate-500">Data type</dt><dd>{detail.dataType}</dd><dt className="font-bold text-slate-500">Mutability</dt><dd>{detail.mutability}</dd><dt className="font-bold text-slate-500">Required ACK</dt><dd>{detail.appliedNodes} / {detail.knownNodes}</dd></dl></details>
        </div>}

        {detail && detail.migrationState !== 'PROPOSED' && applyState && <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Required-node convergence</div><h3 className="mt-1 text-lg font-black text-slate-950">Applied {applyState.appliedNodes} / {applyState.requiredNodes}</h3><p className="mt-1 text-xs text-slate-500">NOT_SEEN {applyState.notSeenNodes} · STALE {applyState.staleNodes} · FAILED {applyState.failedNodes}</p></div><div className="max-w-xs break-all text-right font-mono text-[10px] text-slate-400">{applyState.desiredFingerprint ?? 'No desired fingerprint'}</div></div>
          {applyState.requiredNodes === 0 ? <p className="mt-4 text-sm text-amber-800">No required topology targets are registered yet. Application cannot be certified from an empty denominator.</p> : <div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-xs"><thead><tr className="border-b border-slate-200 text-slate-500"><th className="px-2 py-2">Node</th><th className="px-2 py-2">Role</th><th className="px-2 py-2">State</th><th className="px-2 py-2">Applied revision</th><th className="px-2 py-2">Last seen</th><th className="px-2 py-2">Error</th></tr></thead><tbody>{applyState.nodes.map(node => <tr key={node.nodeId} className="border-b border-slate-100"><td className="px-2 py-2 font-mono">{node.nodeId}</td><td className="px-2 py-2">{node.nodeRole}</td><td className="px-2 py-2"><NodeStateBadge state={node.state} /></td><td className="max-w-48 truncate px-2 py-2 font-mono" title={node.appliedRevisionId ?? ''}>{node.appliedRevisionId ?? '—'}</td><td className="px-2 py-2">{node.lastSeenAt ? new Date(node.lastSeenAt).toLocaleString() : 'Never'}</td><td className="max-w-64 px-2 py-2 text-rose-700">{node.errorCode ? `${node.errorCode}: ${node.errorDetail ?? ''}` : '—'}</td></tr>)}</tbody></table></div>}
        </div>}

        {detail && cutover && <div className="rounded-3xl border border-violet-200 bg-violet-50 p-5 shadow-sm"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-violet-700">Single Authority readiness</div><h3 className="mt-1 text-lg font-black text-violet-950">{cutover.phase.replaceAll('_', ' ')}</h3><p className="mt-1 text-xs text-violet-800">Authority Contract v2 convergence: {cutover.convergedNodeCount} / {cutover.requiredNodeCount} required nodes</p></div><Link href="/settings/runtime-configuration/migration-governance" className="rounded-xl border border-violet-300 bg-white px-3 py-2 text-xs font-black text-violet-800">Open migration governance</Link></div>{cutover.blockers.length > 0 ? <div className="mt-4"><div className="text-xs font-black uppercase text-rose-700">Current blockers</div><ul className="mt-2 space-y-1 text-xs text-rose-900">{cutover.blockers.map(blocker => <li key={blocker} className="rounded-lg border border-rose-200 bg-white px-3 py-2 font-mono">{blocker}</li>)}</ul></div> : <div className="mt-4 rounded-xl border border-emerald-200 bg-white p-3 text-sm font-bold text-emerald-800">No cutover blocker is currently reported for this Config Set.</div>}<div className="mt-4 overflow-x-auto"><table className="min-w-full text-left text-xs"><thead><tr className="border-b border-violet-200 text-violet-800"><th className="px-2 py-2">Node</th><th className="px-2 py-2">Role</th><th className="px-2 py-2">Authority contract</th><th className="px-2 py-2">Apply state</th><th className="px-2 py-2">Converged</th></tr></thead><tbody>{cutover.nodes.map(node => <tr key={node.nodeId} className="border-b border-violet-100"><td className="px-2 py-2 font-mono">{node.nodeId}</td><td className="px-2 py-2">{node.nodeRole}</td><td className="px-2 py-2">v{node.supportedAuthorityContractVersion}</td><td className="px-2 py-2">{node.applyState.replaceAll('_', ' ')}</td><td className="px-2 py-2 font-black">{node.converged ? 'Yes' : 'No'}</td></tr>)}</tbody></table></div></div>}

        {canGovern && detail && detail.editable && <div className="rounded-3xl border border-indigo-200 bg-indigo-50 p-5 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-indigo-700">Audit reason</div><p className="mt-1 text-xs leading-5 text-indigo-900">Required for every change, approval, rejection, publication, rollback and emergency action. This field is available independently of Edit permission.</p><textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} placeholder="Why is this action required?" className="mt-3 w-full rounded-xl border border-indigo-200 bg-white px-3 py-2 text-sm" /></div>}

        {detail && detail.migrationState !== 'PROPOSED' && <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">Governance</div><h3 className="mt-1 text-lg font-black text-slate-950">Approval, publication and rollback</h3><div className="mt-4 space-y-3">
          {pendingForSelected.length === 0 && <p className="text-sm text-slate-500">No pending or approved change request for this configuration set.</p>}
          {pendingForSelected.map(revision => <div key={revision.revisionId} className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-2"><span className="text-sm font-black">Revision {revision.sequenceNo}</span><RevisionBadge state={revision.state} /></div><p className="mt-2 text-sm text-slate-700">{revision.reason}</p><div className="mt-3 flex flex-wrap gap-2">{revision.state === 'PENDING_APPROVAL' && canApprove && <><button type="button" disabled={busy} onClick={() => void run(() => runtimeConfigurationApi.approve(revision.revisionId, reason.trim()), 'Revision approved. It is not published yet.')} className="rounded-lg bg-emerald-700 px-3 py-2 text-xs font-black text-white">Approve</button><button type="button" disabled={busy} onClick={() => void run(() => runtimeConfigurationApi.reject(revision.revisionId, reason.trim()), 'Revision rejected.')} className="rounded-lg border border-rose-300 bg-white px-3 py-2 text-xs font-black text-rose-800">Reject</button></>}{revision.state === 'APPROVED' && canPublish && <button type="button" disabled={busy} onClick={() => void run(() => runtimeConfigurationApi.publishApproved(revision.revisionId, revision.baseRevisionId, reason.trim()), 'Approved revision published. Runtime convergence remains visible separately.', 'Publish this approved revision to the current environment?')} className="rounded-lg bg-blue-700 px-3 py-2 text-xs font-black text-white">Publish approved revision</button>}</div></div>)}
          {canRollback && rollbackSources.length > 1 && <div className="border-t border-slate-200 pt-3"><div className="text-xs font-black text-slate-500">Rollback</div><p className="mt-1 text-xs leading-5 text-slate-600">Preview the semantic diff first. Rollback creates a new governed change request; history is never rewritten.</p><div className="mt-2 flex flex-wrap gap-2">{rollbackSources.filter(r => r.revisionId !== detail.activeRevisionId).slice(0, 5).map(r => <button key={r.revisionId} type="button" disabled={busy} onClick={() => void previewRollback(r)} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700">Preview revision {r.sequenceNo}</button>)}</div></div>}
          {rollbackPreview && <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4"><div className="text-sm font-black text-amber-950">Rollback preview · restore revision {rollbackPreview.restoreSourceSequence}</div>{rollbackPreview.changes.length === 0 ? <p className="mt-2 text-sm text-amber-900">No semantic value differences from the current revision.</p> : <div className="mt-3 space-y-2">{rollbackPreview.changes.map(diff => <div key={diff.key} className="grid gap-2 rounded-xl border border-amber-200 bg-white p-3 text-xs sm:grid-cols-[1fr_1fr_1fr]"><div><div className="font-black">{diff.label}</div><div className="font-mono text-[10px] text-slate-400">{diff.key}</div></div><div><span className="font-bold text-slate-500">Current</span><div className="mt-1 break-all">{valueText(diff.currentValue)}</div></div><div><span className="font-bold text-slate-500">Restore</span><div className="mt-1 break-all">{valueText(diff.restoreValue)}</div></div></div>)}</div>}<button type="button" disabled={busy || rollbackPreview.changes.length === 0} onClick={() => void run(() => runtimeConfigurationApi.requestRollback(detail.key, rollbackPreview.restoreSourceRevisionId, reason.trim()), `Rollback request created from revision ${rollbackPreview.restoreSourceSequence}.`, `Create a rollback request restoring revision ${rollbackPreview.restoreSourceSequence}?`)} className="mt-3 rounded-lg bg-amber-700 px-3 py-2 text-xs font-black text-white disabled:bg-slate-300">Create rollback request</button></div>}
        </div></div>}

        {detail && overridesForSelected.length > 0 && <div className="rounded-3xl border border-rose-200 bg-rose-50 p-5 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-rose-700">Active emergency override</div>{overridesForSelected.map(o => <div key={o.overrideId} className="mt-3 rounded-xl border border-rose-200 bg-white p-3"><div className="font-black text-rose-950">Temporary value: {valueText(o.value, detail.unit)}</div><div className="mt-1 text-xs text-rose-800">Expires {new Date(o.expiresAt).toLocaleString()}</div><p className="mt-2 text-sm text-slate-700">{o.reason}</p>{canBreakGlass && <button type="button" disabled={busy} onClick={() => void run(() => runtimeConfigurationApi.revokeEmergencyOverride(o.overrideId, reason.trim()), 'Emergency override revoked. Required nodes are reapplying the normal revision.', 'Revoke this emergency override now?')} className="mt-3 rounded-lg border border-rose-300 px-3 py-2 text-xs font-black text-rose-800">Revoke override</button>}</div>)}</div>}
      </div>
    </section>}

    <section className="grid gap-4 lg:grid-cols-2"><div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">Governance rule</div><h2 className="mt-1 text-lg font-black text-slate-950">Edit ≠ approve ≠ publish ≠ applied</h2><p className="mt-2 text-sm leading-6 text-slate-600">The operator who creates/submits a change request cannot approve the same revision. Publication requires prior approval. Application requires matching revision and snapshot fingerprint from every required node.</p></div><div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"><div className="text-xs font-black uppercase tracking-wide text-slate-500">Security & Access</div><h2 className="mt-1 text-lg font-black text-slate-950">Existing IAM/ReBAC remains authoritative</h2><p className="mt-2 text-sm leading-6 text-slate-600">Configuration permissions remain inside the existing authorization model. No Configuration-specific RBAC database is created.</p><div className="mt-4 flex gap-2"><Link href="/admin/tenants" className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-black text-slate-700">People & Access</Link><Link href="/settings/integrations" className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-black text-slate-700">Integration Identity</Link></div></div></section>

    <p className="text-xs leading-5 text-slate-500">{governance?.notice ?? overview?.governanceNotice}</p>
  </div>;
}
