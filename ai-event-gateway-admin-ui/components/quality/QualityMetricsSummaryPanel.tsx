'use client';

import { useEffect, useMemo, useState } from 'react';
import { DataSourceBadge, type DataSourceKind } from '@/components/common/DataSourceBadge';
import { LiveDataUnavailable } from '@/components/common/LiveDataUnavailable';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { getPublicEnv } from '@/lib/constants/env';
import type { CoreAgentQualityMetricsWindow, CoreSupplyProfileQualitySnapshot } from '@/lib/types/core';

function formatPercent(value: unknown): string {
  const numeric = typeof value === 'number' ? value : Number(value ?? 0);
  if (!Number.isFinite(numeric)) return '0%';
  return `${(numeric * 100).toFixed(1)}%`;
}

function formatScore(value: unknown): string {
  const numeric = typeof value === 'number' ? value : Number(value ?? 0);
  return Number.isFinite(numeric) ? numeric.toFixed(1) : '0.0';
}

const fallbackSnapshots: CoreSupplyProfileQualitySnapshot[] = [
  {
    snapshotId: 'quality-snapshot-cms-reviewer-l1-24h',
    profileCode: 'CMS_REVIEWER_L1',
    metricWindow: '24h',
    successRate: 0.98,
    slaBreachRate: 0.01,
    recentFailureCount: 0,
    qualityGrade: 'A',
    score: 98,
    sampleSize: 10,
    source: 'P3G_FALLBACK_SAMPLE',
  },
  {
    snapshotId: 'quality-snapshot-issue-triage-analyst-24h',
    profileCode: 'ISSUE_TRIAGE_ANALYST',
    metricWindow: '24h',
    successRate: 0.93,
    slaBreachRate: 0.03,
    recentFailureCount: 1,
    qualityGrade: 'B',
    score: 91,
    sampleSize: 8,
    source: 'P3G_FALLBACK_SAMPLE',
  },
];

export function QualityMetricsSummaryPanel({
  title = 'Quality metrics snapshot',
  description = 'Shows live Core quality snapshots used by dispatch readiness checks and quality requirements. Production does not show sample scores when the API is unavailable.',
  agentId,
}: {
  title?: string;
  description?: string;
  agentId?: string;
}) {
  const env = getPublicEnv();
  const [snapshots, setSnapshots] = useState<CoreSupplyProfileQualitySnapshot[]>(() => env.allowFixtureData ? fallbackSnapshots : []);
  const [agentMetrics, setAgentMetrics] = useState<CoreAgentQualityMetricsWindow[]>([]);
  const [source, setSource] = useState<DataSourceKind>(() => env.allowFixtureData ? 'fixture' : 'unavailable');
  const [loadError, setLoadError] = useState<string | undefined>();

  useEffect(() => {
    let mounted = true;
    async function load() {
      try {
        const [qualitySnapshots, windows] = await Promise.all([
          coreAdminApi.getSupplyProfileQualitySnapshots('24h', agentId),
          agentId ? coreAdminApi.getAgentQualityWindows(agentId, '24h') : Promise.resolve([]),
        ]);
        if (!mounted) return;
        setSnapshots(qualitySnapshots.length ? qualitySnapshots : (env.allowFixtureData ? fallbackSnapshots : []));
        setAgentMetrics(windows);
        setSource(qualitySnapshots.length ? 'live' : (env.allowFixtureData ? 'fixture' : 'unavailable'));
        setLoadError(qualitySnapshots.length ? undefined : 'Core quality metrics API returned no records.');
      } catch (error: unknown) {
        if (!mounted) return;
        setSnapshots(env.allowFixtureData ? fallbackSnapshots : []);
        setAgentMetrics([]);
        setSource(env.allowFixtureData ? 'fixture' : 'unavailable');
        setLoadError(error instanceof Error ? error.message : 'Core quality metrics API is unavailable.');
      }
    }
    void load();
    return () => { mounted = false; };
  }, [agentId, env.allowFixtureData]);

  const summary = useMemo(() => {
    const first = snapshots[0];
    return {
      grade: first?.qualityGrade ?? 'UNKNOWN',
      score: first?.score ?? 0,
      successRate: first?.successRate ?? 0,
      slaBreachRate: first?.slaBreachRate ?? 0,
      recentFailureCount: first?.recentFailureCount ?? 0,
    };
  }, [snapshots]);

  if (source === 'unavailable' && snapshots.length === 0) {
    return (
      <LiveDataUnavailable
        title="Quality requirements live API is unavailable"
        description="Quality metrics must come from Core in production. Fixture quality samples are disabled, so the UI will not show sample grades or scores."
        details={loadError}
      />
    );
  }

  return (
    <section className="rounded-3xl border border-emerald-100 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="text-xs font-black uppercase tracking-wide text-emerald-700">Quality Requirements</p>
          <h3 className="mt-1 text-lg font-black text-slate-950">{title}</h3>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">{description}</p>
        </div>
        <DataSourceBadge source={source} detail={loadError} />
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-5">
        <MetricCard label="Grade" value={summary.grade} />
        <MetricCard label="Score" value={formatScore(summary.score)} />
        <MetricCard label="Success" value={formatPercent(summary.successRate)} />
        <MetricCard label="SLA breach" value={formatPercent(summary.slaBreachRate)} />
        <MetricCard label="Recent failures" value={String(summary.recentFailureCount ?? 0)} />
      </div>

      <div className="mt-4 overflow-hidden rounded-2xl border border-slate-200">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2 text-left">Profile</th>
              <th className="px-3 py-2 text-left">Window</th>
              <th className="px-3 py-2 text-left">Grade</th>
              <th className="px-3 py-2 text-left">Success</th>
              <th className="px-3 py-2 text-left">SLA breach</th>
              <th className="px-3 py-2 text-left">Failures</th>
              <th className="px-3 py-2 text-left">Source</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {snapshots.map((snapshot) => (
              <tr key={snapshot.snapshotId ?? `${snapshot.profileCode}-${snapshot.metricWindow}`}>
                <td className="px-3 py-2 font-bold text-slate-800">{snapshot.profileCode}</td>
                <td className="px-3 py-2 text-slate-600">{snapshot.metricWindow ?? '24h'}</td>
                <td className="px-3 py-2 text-slate-600">{snapshot.qualityGrade ?? 'UNKNOWN'}</td>
                <td className="px-3 py-2 text-slate-600">{formatPercent(snapshot.successRate)}</td>
                <td className="px-3 py-2 text-slate-600">{formatPercent(snapshot.slaBreachRate)}</td>
                <td className="px-3 py-2 text-slate-600">{snapshot.recentFailureCount ?? 0}</td>
                <td className="px-3 py-2 text-slate-500">{snapshot.source ?? 'P3G'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {agentMetrics.length ? (
        <p className="mt-3 text-xs font-semibold text-emerald-700">Agent-specific rolling windows loaded: {agentMetrics.length}</p>
      ) : null}
    </section>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
      <p className="text-[11px] font-black uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-black text-slate-950">{value}</p>
    </div>
  );
}
