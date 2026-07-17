'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { DataSourceBadge, type DataSourceKind } from '@/components/common/DataSourceBadge';
import { LiveDataUnavailable } from '@/components/common/LiveDataUnavailable';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { getPublicEnv } from '@/lib/constants/env';
import { p3pProductionEnforceObservabilityFixture } from '@/lib/fixtures/p3pProductionEnforceObservabilityFixture';
import { buildPostCutoverObservabilityExport, evaluateEnforceAlertRules, type EnforceObservabilitySnapshot } from '@/lib/eligibility/enforceObservability';
import type { CoreEnforceArtifactRetentionRecord, CoreEnforceLegacyFinalReportItem } from '@/lib/types/core';

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function metricClass(value: number, dangerAt: number) {
  if (value >= dangerAt) return 'border-red-200 bg-red-50 text-red-800';
  if (value > 0) return 'border-amber-200 bg-amber-50 text-amber-800';
  return 'border-emerald-200 bg-emerald-50 text-emerald-800';
}

export function ProductionEnforceObservabilityPanel() {
  const env = getPublicEnv();
  const [snapshot, setSnapshot] = useState<EnforceObservabilitySnapshot | null>(env.allowFixtureData ? p3pProductionEnforceObservabilityFixture.sampleSnapshot : null);
  const [source, setSource] = useState<DataSourceKind>(env.allowFixtureData ? 'fixture' : 'unavailable');
  const [loadError, setLoadError] = useState<string | undefined>();
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const [legacyFinalReport, setLegacyFinalReport] = useState<CoreEnforceLegacyFinalReportItem[]>([]);
  const [retainedArtifacts, setRetainedArtifacts] = useState<CoreEnforceArtifactRetentionRecord[]>([]);

  useEffect(() => {
    let active = true;
    coreAdminApi.getEnforceObservabilitySnapshot?.()
      .then((result) => {
        if (!active || !result) return;
        setSnapshot({ ...p3pProductionEnforceObservabilityFixture.sampleSnapshot, ...result, source: result.source ?? 'live' });
        setSource('live');
      })
      .catch((error: unknown) => {
        if (!active) return;
        const message = error instanceof Error ? error.message : 'Live dispatch monitoring API is unavailable.';
        setLoadError(message);
        if (env.allowFixtureData) {
          setSnapshot(p3pProductionEnforceObservabilityFixture.sampleSnapshot);
          setSource('fixture');
        } else {
          setSnapshot(null);
          setSource('unavailable');
        }
      });
    coreAdminApi.getEnforceLegacyFinalReport()
      .then((result) => { if (active) setLegacyFinalReport(result); })
      .catch(() => { if (active) setLegacyFinalReport([]); });
    coreAdminApi.getEnforceArtifactRetention()
      .then((result) => { if (active) setRetainedArtifacts(result); })
      .catch(() => { if (active) setRetainedArtifacts([]); });
    return () => { active = false; };
  }, [env.allowFixtureData]);

  const alerts = useMemo(() => snapshot ? evaluateEnforceAlertRules(snapshot) : [], [snapshot]);
  const exportPayload = useMemo(() => snapshot ? buildPostCutoverObservabilityExport(snapshot) : null, [snapshot]);
  const triggeredAlerts = alerts.filter((alert) => alert.triggered);

  async function copyExport() {
    if (!exportPayload) return;
    try {
      await navigator.clipboard.writeText(JSON.stringify(exportPayload, null, 2));
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }


  if (!snapshot) {
    return (
      <LiveDataUnavailable
        title="Dispatch monitoring live API is unavailable"
        description="Production dispatch monitoring cannot be verified. Fixture data is disabled, so the page is intentionally fail-closed instead of showing sample ENFORCE metrics."
        details={loadError ?? 'Core /admin/enforce/observability did not return a usable response.'}
        action={<Link href="/cluster/diagnostics" className="rounded-xl border border-rose-200 bg-white px-3 py-2 text-xs font-black text-rose-700 hover:bg-rose-100">Open Diagnostics →</Link>}
      />
    );
  }

  return (
    <div className="space-y-6">
      <section className="rounded-3xl border border-indigo-200 bg-indigo-50 p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-[11px] font-black uppercase tracking-wide text-indigo-700">Dispatch Monitoring</div>
            <h2 className="mt-2 text-xl font-black text-slate-950">Production dispatch monitoring and post-cutover operations</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">
              Monitor long-running production dispatch operations after cutover. This dashboard tracks allowed, blocked, no-candidate, fallback-denied, quality unavailable, and score-breakdown metrics from the live Core observability API.
            </p>
            {loadError ? <p className="mt-2 text-xs font-bold text-amber-700">{loadError}</p> : null}
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={copyExport} className="rounded-xl border border-indigo-200 bg-white px-3 py-2 text-xs font-black text-indigo-700 hover:bg-indigo-100">
              Copy observability export
            </button>
            <Link href="/settings/release-cutover" className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">
              Release cutover →
            </Link>
          </div>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <MetricCard label="Mode" value={snapshot.mode} tone="neutral" />
          <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="text-[11px] font-black uppercase tracking-wide text-slate-500">Source</div>
            <div className="mt-3"><DataSourceBadge source={source} detail={source === 'live' ? 'Core API' : 'Sample data allowed only outside production'} /></div>
          </div>
          <MetricCard label="Window" value={snapshot.window} tone="neutral" />
          <MetricCard label="Readiness blocking" value={String(snapshot.readinessBlockingCount ?? 0)} tone={(snapshot.readinessBlockingCount ?? 0) > 0 ? 'danger' : 'good'} />
        </div>
        {copyState === 'copied' ? <p className="mt-3 text-xs font-bold text-emerald-700">Post-cutover observability export copied.</p> : null}
        {copyState === 'failed' ? <p className="mt-3 text-xs font-bold text-red-700">Clipboard unavailable. Use the export script in CI.</p> : null}
      </section>

      <section className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
        <MetricCard label="V2 allowed" value={String(snapshot.v2Allowed)} tone="good" />
        <MetricCard label="V2 blocked" value={String(snapshot.v2Blocked)} tone={snapshot.v2Blocked > 0 ? 'warn' : 'good'} />
        <MetricCard label="No candidate" value={String(snapshot.noCandidate)} tone={snapshot.noCandidate > 0 ? 'danger' : 'good'} />
        <MetricCard label="Fallback denied" value={String(snapshot.fallbackDenied)} tone={snapshot.fallbackDenied > 0 ? 'warn' : 'good'} />
        <MetricCard label="Quality unavailable" value={String(snapshot.qualityUnavailable)} tone={snapshot.qualityUnavailable > 0 ? 'warn' : 'good'} />
        <MetricCard label="Score missing" value={String(snapshot.scoreBreakdownMissing)} tone={snapshot.scoreBreakdownMissing > 0 ? 'danger' : 'good'} />
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        <RateCard title="Blocked rate" value={snapshot.blockedRate} dangerAt={0.35} />
        <RateCard title="No candidate rate" value={snapshot.noCandidateRate} dangerAt={0.1} />
        <RateCard title="Quality unavailable rate" value={snapshot.qualityUnavailableRate} dangerAt={0.05} />
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h3 className="text-sm font-black uppercase tracking-wide text-slate-600">Alert rules</h3>
            <p className="mt-1 text-sm leading-6 text-slate-600">Triggered alerts should create operator incidents and may recommend rollback to WARN.</p>
          </div>
          <span className={`rounded-full px-3 py-1 text-[11px] font-black uppercase tracking-wide ${triggeredAlerts.length ? 'bg-red-100 text-red-700' : 'bg-emerald-100 text-emerald-700'}`}>
            {triggeredAlerts.length} triggered
          </span>
        </div>
        <div className="mt-4 grid gap-3 lg:grid-cols-2">
          {alerts.map((alert) => (
            <div key={alert.code} className={`rounded-2xl border p-4 ${alert.triggered ? 'border-red-200 bg-red-50' : 'border-slate-200 bg-slate-50'}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="text-xs font-black text-slate-950">{alert.code}</div>
                <div className={`rounded-full px-2 py-1 text-[10px] font-black uppercase ${alert.triggered ? 'bg-red-100 text-red-700' : 'bg-slate-200 text-slate-600'}`}>{alert.severity}</div>
              </div>
              <div className="mt-2 text-xs font-bold text-slate-600">{alert.metric}: {alert.observed} / threshold {alert.threshold}</div>
              <p className="mt-2 text-xs leading-5 text-slate-600">{alert.action}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <InfoCard title="Dispatch assignment evidence" badge="ASSIGNMENT_EVIDENCE" items={[
          'Task Detail shows raw requirements, effective capabilities, selected Agent, service scope, runtime reported capabilities, dispatch request status, and issue link.',
          'Agent Detail recent tasks show compact assignment evidence for each task assigned to that Agent.',
          'Use this panel to trace source event → resolved requirements → eligible candidates → selected Agent without opening raw diagnostics.',
        ]} href="/tasks" hrefLabel="Open Tasks / assignment evidence →" />
        <InfoCard title="Routing decision audit search" badge="AUDIT_SEARCH" items={[
          `Endpoint: ${p3pProductionEnforceObservabilityFixture.routingAuditSearch.endpoint}`,
          `Filters: ${p3pProductionEnforceObservabilityFixture.routingAuditSearch.requiredFilters.join(', ')}`,
          `Required score keys: ${p3pProductionEnforceObservabilityFixture.routingAuditSearch.requiredScoreKeys.join(', ')}`,
        ]} href="/tasks" hrefLabel="Open Tasks / routing audit →" />
        <InfoCard title="Operator incident workflow" badge="INCIDENT_WORKFLOW" items={[
          `Endpoint: ${p3pProductionEnforceObservabilityFixture.operatorIncidentWorkflow.endpoint}`,
          `Template: ${p3pProductionEnforceObservabilityFixture.operatorIncidentWorkflow.incidentTemplate}`,
          `Issue tracking: ${p3pProductionEnforceObservabilityFixture.operatorIncidentWorkflow.issueTrackingLink}`,
        ]} href="/settings/issue-tracking" hrefLabel="Open issue tracking →" />
        <InfoCard title="Rollback criteria" badge="ROLLBACK_CRITERIA" items={p3pProductionEnforceObservabilityFixture.rollbackCriteria} href="/settings/release-cutover" hrefLabel="Open cutover runbook →" />
        <InfoCard title="Legacy cleanup final report" badge="LEGACY_FINAL_REPORT" items={legacyFinalReport.length
          ? legacyFinalReport.map((item) => `${item.category}: ${item.count} · ${item.severity ?? 'INFO'}`)
          : ['No archived legacy comparison rows or non-authoritative active policies were reported by Core.']}
          href="/settings/migration-readiness" hrefLabel="Open migration readiness →" />
      </section>

      <section className="rounded-3xl border border-blue-200 bg-blue-50 p-5 shadow-sm">
        <h3 className="text-sm font-black uppercase tracking-wide text-blue-800">Release artifact retention</h3>
        <p className="mt-2 text-sm leading-6 text-blue-950">Post-cutover ENFORCE operations retain acceptance, readiness, archive manifest, and observability export artifacts for release audit.</p>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {(retainedArtifacts.length ? retainedArtifacts.map((artifact) => artifact.artifactName) : ['No retained release artifacts are currently registered.']).map((artifact) => (
            <div key={artifact} className="rounded-2xl border border-blue-200 bg-white p-3 text-xs font-bold text-blue-900">{artifact}</div>
          ))}
        </div>
        <p className="mt-3 text-xs font-bold text-blue-900">Live source: Core /admin/enforce/artifact-retention · records: {retainedArtifacts.length}</p>
      </section>
    </div>
  );
}

function MetricCard({ label, value, tone }: { label: string; value: string; tone: 'good' | 'warn' | 'danger' | 'neutral' }) {
  const styles = {
    good: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    warn: 'border-amber-200 bg-amber-50 text-amber-800',
    danger: 'border-red-200 bg-red-50 text-red-800',
    neutral: 'border-slate-200 bg-white text-slate-800',
  }[tone];
  return (
    <div className={`rounded-3xl border p-4 shadow-sm ${styles}`}>
      <div className="text-[11px] font-black uppercase tracking-wide opacity-80">{label}</div>
      <div className="mt-2 text-2xl font-black">{value}</div>
    </div>
  );
}

function RateCard({ title, value, dangerAt }: { title: string; value: number; dangerAt: number }) {
  return (
    <div className={`rounded-3xl border p-5 shadow-sm ${metricClass(value, dangerAt)}`}>
      <div className="text-[11px] font-black uppercase tracking-wide opacity-80">{title}</div>
      <div className="mt-2 text-3xl font-black">{formatPercent(value)}</div>
      <div className="mt-2 text-xs font-bold opacity-80">Danger threshold: {formatPercent(dangerAt)}</div>
    </div>
  );
}

function InfoCard({ title, badge, items, href, hrefLabel }: { title: string; badge: string; items: readonly string[]; href: string; hrefLabel: string }) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="text-[11px] font-black uppercase tracking-wide text-slate-500">{badge}</div>
      <h3 className="mt-2 text-base font-black text-slate-950">{title}</h3>
      <ul className="mt-3 space-y-2 text-xs leading-5 text-slate-600">
        {items.map((item) => <li key={item} className="rounded-2xl bg-slate-50 px-3 py-2 font-bold">{item}</li>)}
      </ul>
      <Link href={href} className="mt-4 inline-flex rounded-xl border border-slate-300 bg-slate-50 px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-100">{hrefLabel}</Link>
    </div>
  );
}
